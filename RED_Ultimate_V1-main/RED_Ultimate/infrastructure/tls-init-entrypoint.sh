#!/bin/sh
# RED TLS Init Entrypoint — Generate trusted local certificates with mkcert
# Supports dynamic SANs via TLS_SAN_DNS and TLS_SAN_IP environment variables

set -eu

TLS_SAN_DNS=${TLS_SAN_DNS:-localhost,red.local}
TLS_SAN_IP=${TLS_SAN_IP:-127.0.0.1}

mkdir -p /etc/ssl/red

# Install CA in system trust store
mkcert -install

# Generate certificate with dynamic SANs
mkcert -key-file /etc/ssl/red/privkey.pem -cert-file /etc/ssl/red/fullchain.pem \
    ${TLS_SAN_DNS} ${TLS_SAN_IP}

chmod 600 /etc/ssl/red/privkey.pem
chmod 644 /etc/ssl/red/fullchain.pem

echo "[tls] mkcert certificates generated for ${TLS_SAN_DNS} ${TLS_SAN_IP}"

# Keep container running if needed (for debugging)
if [ "${TLS_KEEP_ALIVE:-false}" = "true" ]; then
    tail -f /dev/null
fi