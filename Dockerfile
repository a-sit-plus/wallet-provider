# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server/build.gradle.kts server/build.gradle.kts
COPY server/src server/src
COPY docker-entrypoint.sh docker-entrypoint.sh

RUN chmod +x gradlew
RUN ./gradlew build

RUN set -eux; \
    jar="$(find /workspace/server/build/libs -maxdepth 1 -name '*-all.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$jar"; \
    cp "$jar" /workspace/service.jar

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/service.jar /app/app.jar
COPY --from=build /workspace/docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

ENV CONFIG_APPLICATION=wallet-provider
ENV CONFIG_PROFILE=default
ENV CONFIG_LABEL=main
ENV KTOR_CONFIG_FILE=/app/custom.yaml

ENTRYPOINT ["/app/docker-entrypoint.sh"]
