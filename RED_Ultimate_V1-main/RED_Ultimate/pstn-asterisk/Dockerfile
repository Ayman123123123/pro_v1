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
# إعدادات TLS / WSS لدعم اتصالات WebRTC/WSS من العملاء
# ═══════════════════════════════════════════════════════════════════════════
RUN mkdir -p /etc/asterisk/keys

#Generate self-signed certificate for TLS (valid 365 days) with SAN for current + legacy IPs
#CommonName set to server IP for internal testing - replace with your domain+certs in production
RUN openssl req -x509 -newkey rsa:2048 -keyout /etc/asterisk/keys/privkey.pem -out /etc/asterisk/keys/fullchain.pem -days 365 -nodes -subj '/CN=192.168.11.20/O=RED Sovereign/CN=Asterisk PSTN Gateway' -addext 'subjectAltName=IP:192.168.11.20,IP:192.168.11.210,IP:192.168.11.131,IP:127.0.0.1,DNS:localhost,DNS:red.local'

# Ensure proper permissions on key files (Asterisk requires key to be read by asterisk user)
RUN chown asterisk:asterisk /etc/asterisk/keys/privkey.pem /etc/asterisk/keys/fullchain.pem

RUN sed -i 's/\r$//' /usr/local/bin/red-asterisk-entrypoint /etc/asterisk/*.conf /usr/local/bin/pstn-incoming.sh \
    && chmod 0755 /usr/local/bin/red-asterisk-entrypoint /usr/local/bin/pstn-incoming.sh \
    && rm -f /etc/asterisk/manager.conf /etc/asterisk/pjsip.conf /etc/asterisk/dongle.conf \
    && rm -f /etc/asterisk/extensions.ael /etc/asterisk/extensions.lua /etc/asterisk/extensions_minivm.conf

EXPOSE 5060/udp 5038 8089 8090 10000-10100/udp

HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
    CMD asterisk -rx 'core show uptime' || exit 1

ENTRYPOINT ["/usr/local/bin/red-asterisk-entrypoint"]
