# RED Sovereign — Janus Gateway Dockerfile
# Based on janus-gateway official image with SIP plugin
# Includes: DTLS/SRTP, ICE, WebSocket, SIP plugin

FROM janus-gateway:1.2.3

# Install additional tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    openssl \
    curl \
    netcat-openbsd \
    iproute2 \
    && rm -rf /var/lib/apt/lists/*

# Create janus user and directories
RUN groupadd -r janus --gid=998 && \
    useradd -r -g janus --uid=998 --home-dir=/var/run/janus --shell=/bin/false janus && \
    mkdir -p /run/janus /etc/janus /etc/janus/certs /var/log/janus && \
    chown -R janus:janus /run/janus /etc/janus /var/log/janus

# Generate self-signed certificates for development
RUN mkdir -p /etc/janus/certs && \
    openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -keyout /etc/janus/certs/janus.key \
    -out /etc/janus/certs/janus.pem \
    -subj "/CN=janus.red-sovereign.local/O=RED Sovereign" \
    -addext "subjectAltName=DNS:janus.red-sovereign.local,IP:0.0.0.0" && \
    chown -R janus:janus /etc/janus/certs

# Copy configuration
COPY janus.cfg /etc/janus/janus.cfg
COPY janus.jcfg /etc/janus/janus.jcfg

# Expose ports
EXPOSE 8188/tcp 8189/tcp 8088/tcp 5060/udp 5061/tcp 20000-40000/udp

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8088/admin || exit 1

# Run as janus user
USER janus

ENTRYPOINT ["janus", "-F", "/etc/janus", "-L", "/var/log/janus", "-p", "/var/run/janus/janus.pid"]