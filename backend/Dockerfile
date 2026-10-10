# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc AS build
WORKDIR /workspace
RUN --mount=type=cache,target=/var/cache/apt \
    --mount=type=cache,target=/var/lib/apt \
    apt-get update && apt-get install -y --no-install-recommends unzip
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# Testcontainers needs the host daemon: full ./mvnw verify is a separate release gate.
# The isolated image builder explicitly runs compilation/unit verification only.
RUN --mount=type=cache,target=/.cache/maven-repository \
    --mount=type=cache,target=/root/.m2/wrapper \
    ./mvnw -B -DskipITs verify

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61 AS runtime
RUN addgroup -S kelios && adduser -S -G kelios kelios
WORKDIR /app
COPY --from=build --chown=kelios:kelios /workspace/target/kelios-backend-0.0.1-SNAPSHOT.jar app.jar
USER kelios
EXPOSE 8080 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
