# RED Sovereign Backend — production multi-stage BuildKit build
# Stage 1: Gradle 9.7 + JDK 21 (non-root, persistent dependency cache via BuildKit)
# Stage 2: Eclipse Temurin JRE 21 Alpine — ZGC, minimal attack surface

ARG RUNTIME_BASE=eclipse-temurin:21-jre-alpine

# Use BuildKit for cache mounts; fallback works without BuildKit
# syntax=docker/dockerfile:1.7

FROM gradle:9.7.0-jdk21 AS builder

USER root
WORKDIR /build
RUN chown gradle:gradle /build

# Copy gradle wrapper and settings first for better cache
COPY --chown=gradle:gradle gradle/ gradle/
COPY --chown=gradle:gradle gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle shared-proto/ ../shared-proto/
COPY --chown=gradle:gradle backend-server/ .

USER gradle
WORKDIR /build

ENV GRADLE_OPTS="-Dorg.gradle.dependency.verification=lenient"

# Build with BuildKit cache mount for gradle dependencies
RUN --mount=type=cache,target=/home/gradle/.gradle,cache_id=gradle-cache,uid=1000,gid=1000 \
    gradle :backend-server:bootJar --no-daemon -x test --stacktrace

FROM ${RUNTIME_BASE} AS runtime

WORKDIR /app

# Install curl for healthcheck, tzdata for timezone
RUN apk add --no-cache curl tzdata \
    && addgroup -g 10001 -S redserver \
    && adduser -u 10001 -S -G redserver -h /app -s /sbin/nologin redserver \
    && mkdir -p /app/logs \
    && chown -R redserver:redserver /app

COPY --from=builder --chown=redserver:redserver /build/backend-server/build/libs/red-backend.jar /app/app.jar
COPY --chown=redserver:redserver entrypoint.sh /app/entrypoint.sh

# ZGC for low latency, container-aware, exit on OOM
ENV JAVA_TOOL_OPTIONS="-Xms512m -Xmx1g -XX:+UseZGC -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"

USER redserver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=180s \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/liveness >/dev/null || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]

