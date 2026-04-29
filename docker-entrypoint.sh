#!/bin/sh
set -eu

: "${CONFIG_SERVER_URL:?CONFIG_SERVER_URL is required}"
: "${CONFIG_APPLICATION:?CONFIG_APPLICATION is required}"
: "${CONFIG_PROFILE:=default}"
: "${CONFIG_LABEL:=main}"
: "${KTOR_CONFIG_FILE:=/app/custom.yaml}"
: "${HOST:=0.0.0.0}"
: "${PORT:=8080}"
: "${CONFIG_RETRY_ATTEMPTS:=30}"
: "${CONFIG_RETRY_SLEEP_SECONDS:=2}"

CONFIG_URL="${CONFIG_SERVER_URL}/${CONFIG_LABEL}/${CONFIG_APPLICATION}-${CONFIG_PROFILE}.yaml"

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

attempt=1

while [ "$attempt" -le "$CONFIG_RETRY_ATTEMPTS" ]; do
  echo "Fetching config from ${CONFIG_URL}, attempt ${attempt}/${CONFIG_RETRY_ATTEMPTS}"

  if http_code="$(curl \
      --silent \
      --show-error \
      --location \
      --write-out "%{http_code}" \
      --output "$tmp_file" \
      "$CONFIG_URL")"; then

    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
      cp "$tmp_file" "$KTOR_CONFIG_FILE"
      echo "Config written to ${KTOR_CONFIG_FILE}"

      exec java \
        ${JAVA_OPTS:-} \
        -jar /app/app.jar \
        -config="$KTOR_CONFIG_FILE" \
        -host="$HOST" \
        -port="$PORT"
    fi

    echo "Config server returned HTTP ${http_code}" >&2
  else
    echo "Could not connect to config server" >&2
  fi

  attempt=$((attempt + 1))
  sleep "$CONFIG_RETRY_SLEEP_SECONDS"
done

echo "Failed to fetch config from ${CONFIG_URL}" >&2
exit 1
