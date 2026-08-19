# syntax=docker/dockerfile:1.7
# YOUNES Media SFU — resilient mediasoup worker install.
# Prebuilt GitHub binary first; local compile without io_uring if the
# download is blocked (corporate TLS intercept / firewall).
FROM node:24-bookworm AS builder

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        python3 python3-pip python3-setuptools python3-wheel \
        build-essential pkg-config ninja-build meson \
        ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY package.json package-lock.json install-worker.sh ./
RUN chmod +x install-worker.sh \
    && PYTHON=python3 ./install-worker.sh \
    && npm cache clean --force

FROM node:24-bookworm-slim

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --chown=node:node server.js ./server.js
RUN chown -R node:node /app

USER node
EXPOSE 4000
EXPOSE 40000-40100/udp

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=20s \
  CMD node -e "fetch('http://127.0.0.1:4000/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

ENTRYPOINT ["node", "/app/server.js"]
