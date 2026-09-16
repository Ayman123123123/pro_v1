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

# The compose entrypoint override wins; this CMD is only for bare `docker run`.
# It generates certs for localhost, red.local, and provided SANs.
CMD ["/bin/sh", "-ec", "\
  TLS_SAN_DNS=${TLS_SAN_DNS:-localhost,red.local};\
  TLS_SAN_IP=${TLS_SAN_IP:-127.0.0.1};\
  mkcert -install;\
  mkcert -key-file /etc/ssl/red/privkey.pem -cert-file /etc/ssl/red/fullchain.pem \
    $${TLS_SAN_DNS} $${TLS_SAN_IP};\
  chmod 600 /etc/ssl/red/privkey.pem;\
  chmod 644 /etc/ssl/red/fullchain.pem;\
  echo '[tls] mkcert certificates generated for' $${TLS_SAN_DNS} $${TLS_SAN_IP}\
"]