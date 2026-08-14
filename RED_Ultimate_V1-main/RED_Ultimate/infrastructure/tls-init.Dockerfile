# syntax=docker/dockerfile:1.7
FROM alpine:3.21
RUN apk add --no-cache bash openssl
COPY infrastructure/init-certs.sh /init-certs.sh
RUN chmod +x /init-certs.sh
ENTRYPOINT ["/bin/sh", "/init-certs.sh"]
