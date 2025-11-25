# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server/build.gradle.kts server/build.gradle.kts
COPY server/src server/src

RUN chmod +x gradlew
RUN ./gradlew build

RUN set -eux; \
    jar="$(find /workspace/server/build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$jar"; \
    cp "$jar" /workspace/service.jar

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

COPY --from=build /workspace/service.jar /app/app.jar
COPY keystore.p12 /app/keystore.p12

ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar"]
