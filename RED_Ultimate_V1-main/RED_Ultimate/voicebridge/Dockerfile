# RED Sovereign — VoiceBridge Dockerfile
# Multi-stage build for Spring Boot VoiceBridge application
# Includes: JRE, native libraries for audio processing

# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install required native libraries for audio processing
RUN apk add --no-cache \
    alsa-lib \
    libsndfile \
    flac \
    opus \
    opus-tools \
    sox \
    ffmpeg \
    curl \
    bash \
    && rm -rf /var/cache/apk/*

# Create voicebridge user
RUN addgroup -g 1000 -S voicebridge && \
    adduser -u 1000 -S voicebridge -G voicebridge

# Create directories
RUN mkdir -p /app /var/spool/asterisk/recordings /var/log/voicebridge && \
    chown -R voicebridge:voicebridge /app /var/spool/asterisk/recordings /var/log/voicebridge

WORKDIR /app

# Copy built jar from builder
COPY --from=builder /app/target/voicebridge-*.jar /app/voicebridge.jar

# Copy configuration
COPY src/main/resources/application.yml /app/config/application.yml

# Health check script
COPY <<'EOF' /app/healthcheck.sh
#!/bin/bash
curl -f http://localhost:8090/voicebridge/actuator/health/liveness || exit 1
EOF
RUN chmod +x /app/healthcheck.sh

# Switch to non-root user
USER voicebridge

# Expose ports
EXPOSE 8090 12100-13100/udp

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD /app/healthcheck.sh

# JVM options for production
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/voicebridge.jar --spring.config.location=file:/app/config/application.yml"]