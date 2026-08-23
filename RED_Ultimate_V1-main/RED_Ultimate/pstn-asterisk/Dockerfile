# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# RED SOVEREIGN — PSTN Gateway (Asterisk 22 + PJSIP)
# Pure IP/SIP mode for DINSTAR UC2000-VE network gateway.
# No chan_dongle, no USB, no DAHDI — SIP trunk over Ethernet only.
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FROM andrius/asterisk

# Install only essential runtime utilities (no USB/serial dependencies)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    net-tools \
    && rm -rf /var/lib/apt/lists/*

# ════════════════════════════════════════════════════════════════════════════
# Application configuration
# ════════════════════════════════════════════════════════════════════════════
COPY extensions.conf /etc/asterisk/extensions.conf
COPY modules.conf /etc/asterisk/modules.conf
COPY http.conf /etc/asterisk/http.conf
COPY rtp.conf /etc/asterisk/rtp.conf
COPY pstn-incoming.sh /usr/local/bin/pstn-incoming.sh
COPY docker-entrypoint.sh /usr/local/bin/red-asterisk-entrypoint

# ═══════════════════════════════════════════════════════════════════════════
# TLS / WSS certificates
# ═══════════════════════════════════════════════════════════════════════════
# The upstream image declares /etc/asterisk as a VOLUME, so files created there
# during docker build are hidden and unavailable to later build layers. The
# entrypoint therefore creates a development-only certificate at runtime only
# when no managed certificate exists in the persistent key volume.

RUN sed -i 's/\r$//' /usr/local/bin/red-asterisk-entrypoint /etc/asterisk/*.conf /usr/local/bin/pstn-incoming.sh \
    && chmod 0755 /usr/local/bin/red-asterisk-entrypoint /usr/local/bin/pstn-incoming.sh \
    && rm -f /etc/asterisk/manager.conf /etc/asterisk/pjsip.conf /etc/asterisk/dongle.conf \
    && rm -f /etc/asterisk/extensions.ael /etc/asterisk/extensions.lua /etc/asterisk/extensions_minivm.conf

EXPOSE 5060/udp 5038 8089 8090 10000-10100/udp

HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
    CMD asterisk -rx 'core show uptime' || exit 1

ENTRYPOINT ["/usr/local/bin/red-asterisk-entrypoint"]
