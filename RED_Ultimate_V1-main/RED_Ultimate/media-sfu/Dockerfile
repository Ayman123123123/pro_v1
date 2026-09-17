# syntax=docker/dockerfile:1.7
# RED Media SFU — production multi-stage build with mediasoup
# Node 24 Alpine, non-root, BuildKit cache for npm

FROM node:24-alpine AS builder

RUN apk add --no-cache \
    python3 py3-pip py3-setuptools py3-wheel \
    build-base pkgconfig ninja meson \
    ca-certificates curl \
    linux-headers

WORKDIR /app

# Copy package files first for better cache
COPY package.json package-lock.json install-worker.sh ./

# BuildKit cache mount for npm
RUN --mount=type=cache,target=/root/.npm,cache_id=media-sfu-npm \
    chmod +x install-worker.sh \
    && PYTHON=python3 ./install-worker.sh \
    && npm cache clean --force

FROM node:24-alpine AS runtime

RUN apk add --no-cache ca-certificates \
    && addgroup -g 1000 -S node \
    && adduser -u 1000 -S -G node -h /app -s /sbin/nologin node

WORKDIR /app

COPY --from=builder --chown=node:node /app/node_modules ./node_modules
COPY --from=builder --chown=node:node /app/package.json ./
COPY --chown=node:node server.js protocol.js ./

USER node
EXPOSE 4000
EXPOSE 40000-40100/udp
EXPOSE 40000-40100/tcp

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=20s \
  CMD node -e "fetch('http://127.0.0.1:4000/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

ENTRYPOINT ["node", "/app/server.js"]
