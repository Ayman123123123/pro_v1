#!/bin/sh
set -e
mkdir -p /etc/ssl/red
if [ ! -f /etc/ssl/red/fullchain.pem ]; then
    echo "[tls] Generating dev certificates..."
    # Include SAN for current host (192.168.11.20) + legacy + localhost for WSS/TURN validation
    openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 365 \
        -subj "/CN=red.local" \
        -addext "subjectAltName=IP:192.168.11.20,IP:192.168.11.210,IP:192.168.11.131,IP:127.0.0.1,DNS:localhost,DNS:red.local" \
        -keyout /etc/ssl/red/privkey.pem -out /etc/ssl/red/fullchain.pem
fi
chmod 644 /etc/ssl/red/fullchain.pem
chmod 600 /etc/ssl/red/privkey.pem
echo "[tls] Done."
