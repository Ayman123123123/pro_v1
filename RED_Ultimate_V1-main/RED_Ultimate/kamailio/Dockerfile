# RED Sovereign — Kamailio SBC Dockerfile
# Official image moved off Docker Hub to GHCR (kamailio.org: "Due to Docker Hub
# restrictions we migrated to Github Packages Registry").
# Includes: TLS, WebSocket, RTPengine, carrier routing, topology hiding

FROM ghcr.io/kamailio/kamailio:5.8.7-bookworm

# Install additional modules and tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    kamailio-tls-module \
    kamailio-websocket-module \
    kamailio-rtpengine-module \
    kamailio-carrierroute-module \
    kamailio-dispatcher-module \
    kamailio-topoh-module \
    kamailio-sipcapture-module \
    kamailio-pike-module \
    kamailio-htable-module \
    kamailio-dialog-module \
    kamailio-nathelper-module \
    kamailio-crypto-module \
    kamailio-auth-db-module \
    rtpengine \
    curl \
    netcat-openbsd \
    iproute2 \
    && rm -rf /var/lib/apt/lists/*

# Create kamailio user and directories
RUN groupadd -r kamailio --gid=999 && \
    useradd -r -g kamailio --uid=999 --home-dir=/var/run/kamailio --shell=/bin/false kamailio && \
    mkdir -p /run/kamailio /etc/kamailio /var/log/kamailio && \
    chown -R kamailio:kamailio /run/kamailio /etc/kamailio /var/log/kamailio

# Copy entrypoint
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Copy dispatcher list template (populated at runtime)
COPY dispatcher.list.template /etc/kamailio/dispatcher.list.template

# Expose ports
EXPOSE 5060/udp 5060/tcp 5061/tcp

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD kamailio -f /etc/kamailio/kamailio.cfg -c || exit 1

# Run as kamailio user
USER kamailio

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]