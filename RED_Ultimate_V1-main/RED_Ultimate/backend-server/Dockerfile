# ══════════════════════════════════════════════════════════════════
# 🏛️ YOUNES Sovereign Backend — Multi-stage Docker Build
# Stage 1: Gradle build with JDK 21
# Stage 2: Minimal JRE runtime
# ══════════════════════════════════════════════════════════════════

FROM gradle:8.12-jdk21 AS builder
WORKDIR /build
COPY backend-server/ backend-server/
COPY shared-proto/ shared-proto/
WORKDIR /build/backend-server
RUN gradle clean bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install curl for healthcheck, timezone data
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r redserver && useradd -r -g redserver -d /app -s /sbin/nologin redserver

COPY --from=builder /build/backend-server/build/libs/*.jar app.jar

# Create directories
RUN mkdir -p /app/logs && chown -R redserver:redserver /app

USER redserver

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-Xms256m -Xmx512m} -jar app.jar"]
