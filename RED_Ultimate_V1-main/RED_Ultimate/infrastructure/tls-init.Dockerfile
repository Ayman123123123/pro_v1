# syntax=docker/dockerfile:1.7
FROM alpine:3.21
RUN apk add --no-cache bash openssl
COPY infrastructure/init-certs.sh /init-certs.sh
RUN chmod +x /init-certs.sh
# CMD (not ENTRYPOINT) so compose `entrypoint: [sh, -ec]` + inline command
# wins. Bare `docker run` still generates legacy dev certs. The compose
# inline script is canonical: validates TLS_SAN_IP, checks expiry/key-match/
# SAN, and regenerates atomically. init-certs.sh is legacy fallback only.
CMD ["/bin/sh", "/init-certs.sh"]
