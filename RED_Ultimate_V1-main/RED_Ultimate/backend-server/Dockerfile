# Build context must be RED_Ultimate/ so backend and shared-proto use one protocol source.
FROM gradle:8.12-jdk21 AS builder

WORKDIR /build
COPY backend-server/ backend-server/
COPY shared-proto/ shared-proto/
WORKDIR /build/backend-server
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/backend-server/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
