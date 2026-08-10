# ══════════════════════════════════════════════════════════════════════
#  YOUNES Sovereign Backend — Multi-stage Docker Build
#  Stage 1: Gradle build with JDK 21
#  Stage 2: Minimal Eclipse Temurin JRE 21 Runtime
# ══════════════════════════════════════════════════════════════════════

FROM gradle:8.12-jdk21 AS builder
WORKDIR /build

# ⚠️ Fix DNS resolution issues inside Docker build (esp. on Windows)
RUN echo "nameserver 8.8.8.8" > /etc/resolv.conf && \
    echo "nameserver 1.1.1.1" >> /etc/resolv.conf

# Copy Gradle configurations & properties first for layer caching
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY shared-proto/ shared-proto/
COPY backend-server/ backend-server/

WORKDIR /build/backend-server

# Dependencies are cached across builds via a BuildKit cache mount.
# NOTE: a previous revision used `COPY .gradle-cache /home/gradle/.gradle 2>/dev/null || true`
# which is invalid — COPY is not a shell command, so BuildKit failed on every build with
# "/||": not found. It also targeted /home/gradle while the build runs as root.
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon -x test --stacktrace || \
    (echo "First build attempt failed, retrying with fresh deps..." && \
     gradle bootJar --no-daemon -x test --refresh-dependencies)

# ══════════════════════════════════════════════════════════════════════
#  Stage 2: Runtime
# ══════════════════════════════════════════════════════════════════════

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install curl for healthcheck, tzdata and ffmpeg for video thumbnails
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Security: create non-root user
RUN groupadd -r redserver && useradd -r -g redserver -d /app -s /sbin/nologin redserver
RUN mkdir -p /app/logs && chown -R redserver:redserver /app

# Copy JAR from builder stage
COPY --from=builder /build/backend-server/build/libs/*.jar app.jar

USER redserver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-Xms256m -Xmx512m} -jar app.jar"]
