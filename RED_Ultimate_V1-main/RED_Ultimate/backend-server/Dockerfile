# syntax=docker/dockerfile:1.7
# ══════════════════════════════════════════════════════════════════════
#  YOUNES Sovereign Backend — reproducible multi-stage BuildKit build
#  Stage 1: Gradle 8.14 + JDK 21 (non-root, persistent dependency cache)
#  Stage 2: Eclipse Temurin JRE 21 — no apt/ffmpeg (Windows Docker dies
#  for 30–90 minutes pulling 176 ffmpeg packages at tens of kB/s).
#  Video thumbnails already fall back to a Java placeholder.
# ══════════════════════════════════════════════════════════════════════

FROM gradle:8.14.3-jdk21 AS builder

USER root
WORKDIR /build
RUN chown gradle:gradle /build

COPY --chown=gradle:gradle backend-server/settings.gradle.kts backend-server/build.gradle.kts ./backend-server/
COPY --chown=gradle:gradle shared-proto/build.gradle.kts ./shared-proto/
COPY --chown=gradle:gradle shared-proto/src/ ./shared-proto/src/
COPY --chown=gradle:gradle backend-server/src/ ./backend-server/src/

USER gradle
WORKDIR /build/backend-server

RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000,mode=0755,sharing=locked \
    gradle bootJar --no-daemon -x test --stacktrace

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# curl + tzdata already ship in eclipse-temurin:21-jre-jammy.
# Installing ffmpeg here pulled 112 MB / 176 packages and stalled local
# Windows builds for the better part of an hour. Do not put it back.
RUN groupadd --system --gid 10001 redserver \
    && useradd --system --uid 10001 --gid redserver --home-dir /app --shell /usr/sbin/nologin redserver \
    && mkdir -p /app/logs \
    && chown -R redserver:redserver /app

COPY --from=builder --chown=redserver:redserver /build/backend-server/build/libs/red-backend.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

USER redserver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=180s \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
