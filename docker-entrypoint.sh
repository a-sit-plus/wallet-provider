#!/bin/sh
set -eu

PORT="${PORT:-8080}"

exec java ${JAVA_OPTS:-} -Dserver.port="${PORT}" -jar /app/app.jar "$@"
