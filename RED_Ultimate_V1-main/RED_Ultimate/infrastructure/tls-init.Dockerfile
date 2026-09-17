# syntax=docker/dockerfile:1.7
# RED TLS Init — Generate trusted local certificates with mkcert
# Supports dynamic SANs via TLS_SAN_DNS and TLS_SAN_IP build args

FROM alpine:3.21

# Install mkcert for trusted local certificates and openssl for validation
RUN apk add --no-cache bash openssl go \
    && go install filippo.io/mkcert@latest \
    && mv /root/go/bin/mkcert /usr/local/bin/mkcert \
    && apk del go \
    && rm -rf /root/go /var/cache/apk/*

WORKDIR /etc/ssl/red

# Entrypoint script for dynamic SAN generation
COPY tls-init-entrypoint.sh /usr/local/bin/tls-init-entrypoint.sh
RUN chmod +x /usr/local/bin/tls-init-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/tls-init-entrypoint.sh"]