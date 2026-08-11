# syntax=docker/dockerfile:1.7
# Runtime TLS repair must not depend on downloading packages during container
# startup. Build this tiny tool image once with the rest of the platform.
FROM alpine:3.21
RUN apk add --no-cache bash openssl
ENTRYPOINT ["sh"]
