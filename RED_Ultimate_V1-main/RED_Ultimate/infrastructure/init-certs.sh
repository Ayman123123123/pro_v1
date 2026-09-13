#!/bin/sh
set -e
mkdir -p /etc/ssl/red
if [ ! -f /etc/ssl/red/fullchain.pem ]; then
    echo "[tls] Generating dev certificates..."
    # Include only local development names; production certificates are provisioned separately.
    openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 365 \
        -subj "/CN=red.local" \
        -addext "subjectAltName=IP:127.0.0.1,DNS:localhost,DNS:red.local" \
        -keyout /etc/ssl/red/privkey.pem -out /etc/ssl/red/fullchain.pem
fi
chmod 644 /etc/ssl/red/fullchain.pem
chmod 600 /etc/ssl/red/privkey.pem
echo "[tls] Done."
