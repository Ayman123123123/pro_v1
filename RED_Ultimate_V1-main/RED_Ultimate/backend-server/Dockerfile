# Offline builds: the frontend line requires Docker Hub, which is blocked here. Removed.
# RUNTIME_BASE override allows local runtime images when Docker Hub is unreachable:
#   docker build --build-arg RUNTIME_BASE=red-backend:local ...
ARG RUNTIME_BASE=eclipse-temurin:21-jre-jammy
# Offline builds: this frontend line requires Docker Hub, which is blocked/unreachable here. Removed for local builds.
# â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
#  YOUNES Sovereign Backend â€” reproducible multi-stage BuildKit build
#  Stage 1: Gradle 8.14 + JDK 21 (non-root, persistent dependency cache)
#  Stage 2: Eclipse Temurin JRE 21 â€” no apt/ffmpeg (Windows Docker dies
#  for 30â€“90 minutes pulling 176 ffmpeg packages at tens of kB/s).
#  Video thumbnails already fall back to a Java placeholder.
# â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

FROM gradle:8.14.3-jdk21 AS builder

USER root
WORKDIR /build
RUN chown gradle:gradle /build

# Copy backend-server as the Gradle root (it has its own settings.gradle.kts)
COPY --chown=gradle:gradle shared-proto/ ./shared-proto/
COPY --chown=gradle:gradle backend-server/ .

USER gradle
WORKDIR /build

ENV GRADLE_OPTS="-Dorg.gradle.dependency.verification=lenient"
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000,mode=0755,sharing=locked \
    gradle :bootJar --no-daemon -x test --stacktrace

FROM ${RUNTIME_BASE} AS runtime

WORKDIR /app

# curl + tzdata already ship in eclipse-temurin:21-jre-jammy.
# Installing ffmpeg here pulled 112 MB / 176 packages and stalled local
# Windows builds for the better part of an hour. Do not put it back.
RUN if ! getent group redserver >/dev/null 2>&1; then groupadd --system --gid 10001 redserver; fi \
    && if ! id redserver >/dev/null 2>&1; then useradd --system --uid 10001 --gid redserver --home-dir /app --shell /usr/sbin/nologin redserver; fi \
    && mkdir -p /app/logs \
    && chown -R redserver:redserver /app

COPY --from=builder --chown=redserver:redserver /build/backend-server/build/libs/red-backend.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

USER redserver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=180s \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
