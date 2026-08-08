# ══════════════════════════════════════════════════════════════════
# 🏛️ YOUNES Sovereign Backend — Multi-stage Docker Build
# Stage 1: Build JAR using Gradle (with Maven mirrors & timeouts)
# Stage 2: Minimal Eclipse Temurin JRE 21 Runtime
# ══════════════════════════════════════════════════════════════════

FROM gradle:8.12-jdk21 AS builder
WORKDIR /build

# Copy Gradle configurations & properties first for layer caching
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY shared-proto/ shared-proto/
COPY backend-server/ backend-server/

WORKDIR /build/backend-server

# Build the Spring Boot JAR (skipping tests for fast container deployment)
RUN gradle bootJar --no-daemon -x test --stacktrace || \
    gradle :backend-server:bootJar --no-daemon -x test

# ─── Stage 2: Runtime ───
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Install curl for healthcheck & tzdata
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
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
