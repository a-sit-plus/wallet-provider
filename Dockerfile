# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server/build.gradle.kts server/build.gradle.kts
COPY server/src server/src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon :server:bootJar

RUN set -eux; \
    jar="$(find /workspace/server/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$jar"; \
    cp "$jar" /workspace/service.jar

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

ENV PORT=8080
ENV JAVA_OPTS=""
# Listen on all interfaces of the container, and load the configuration from the Spring Cloud Config server
# (set SPRING_CLOUD_CONFIG_URI, optionally SPRING_PROFILES_ACTIVE and SPRING_CLOUD_CONFIG_LABEL).
ENV SERVER_ADDRESS=0.0.0.0
ENV SPRING_APPLICATION_NAME=wallet-provider
ENV SPRING_CLOUD_CONFIG_ENABLED=true

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
COPY --from=build /workspace/service.jar /app/app.jar

RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/app/docker-entrypoint.sh"]
