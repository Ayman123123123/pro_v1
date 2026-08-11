# syntax=docker/dockerfile:1.7
# ══════════════════════════════════════════════════════════════════════
#  YOUNES Sovereign Backend — reproducible multi-stage BuildKit build
#  Stage 1: Gradle 8.12 + JDK 21 (non-root, persistent dependency cache)
#  Stage 2: Minimal Eclipse Temurin JRE 21 runtime (non-root)
# ══════════════════════════════════════════════════════════════════════

FROM gradle:8.14.3-jdk21 AS builder

USER root
WORKDIR /build
RUN chown gradle:gradle /build

# Copy build descriptors separately. Source edits then keep the dependency/build
# configuration layer reusable, while BuildKit keeps downloaded artifacts outside
# image layers in the cache mount below.
COPY --chown=gradle:gradle backend-server/settings.gradle.kts backend-server/build.gradle.kts ./backend-server/
COPY --chown=gradle:gradle shared-proto/build.gradle.kts ./shared-proto/
COPY --chown=gradle:gradle shared-proto/src/ ./shared-proto/src/
COPY --chown=gradle:gradle backend-server/src/ ./backend-server/src/

USER gradle
WORKDIR /build/backend-server

# The official Gradle image runs builds as `gradle` (uid/gid 1000), therefore
# /home/gradle — not /root — is the correct cache home. `sharing=locked` also
# prevents concurrent BuildKit jobs from corrupting Gradle's mutable cache.
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000,mode=0755,sharing=locked \
    gradle bootJar --no-daemon -x test --stacktrace

# ══════════════════════════════════════════════════════════════════════
#  Stage 2: Runtime
# ══════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# curl: container health check; ffmpeg: media thumbnails; tzdata: timestamps.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 redserver \
    && useradd --system --uid 10001 --gid redserver --home-dir /app --shell /usr/sbin/nologin redserver \
    && mkdir -p /app/logs \
    && chown -R redserver:redserver /app

COPY --from=builder --chown=redserver:redserver /build/backend-server/build/libs/red-backend.jar /app/app.jar

# JAVA_TOOL_OPTIONS is consumed directly by the JVM, allowing an exec-form
# entrypoint (correct signal handling; no shell interpolation/injection).
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

USER redserver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
