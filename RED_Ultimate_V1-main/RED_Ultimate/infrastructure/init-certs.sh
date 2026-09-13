#!/bin/sh
# Legacy fallback only — compose certs-init inline script is canonical.
# Kept consistent: honors TLS_SAN_IP instead of hardcoded 192.168.11.20.
set -e
TLS_SAN_IP="${TLS_SAN_IP:-127.0.0.1}"
mkdir -p /etc/ssl/red
if [ ! -f /etc/ssl/red/fullchain.pem ]; then
    echo "[tls] Generating dev certificates (legacy fallback, SAN IP=$TLS_SAN_IP)..."
    openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 365 \
        -subj "/CN=red.local" \
        -addext "subjectAltName=DNS:localhost,DNS:red.local,IP:127.0.0.1,IP:$TLS_SAN_IP" \
        -keyout /etc/ssl/red/privkey.pem -out /etc/ssl/red/fullchain.pem
fi
chmod 644 /etc/ssl/red/fullchain.pem
chmod 600 /etc/ssl/red/privkey.pem
echo "[tls] Done."
