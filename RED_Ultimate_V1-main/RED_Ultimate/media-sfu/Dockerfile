# syntax=docker/dockerfile:1.7
# YOUNES Media SFU (mediasoup)
FROM node:22-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 build-essential pkg-config \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund \
    && npm cache clean --force

COPY --chown=node:node server.js ./server.js
RUN chown -R node:node /app

USER node
EXPOSE 4000
EXPOSE 40000-40100/udp

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=15s \
  CMD node -e "fetch('http://127.0.0.1:4000/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

ENTRYPOINT ["node", "/app/server.js"]
