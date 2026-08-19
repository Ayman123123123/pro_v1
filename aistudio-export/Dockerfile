# ══════════════════════════════════════════════════════════════════
# 🏛️ YOUNES Admin Dashboard — Modern Unified Build (16 pages)
# Stage 1: Node.js 24 LTS — Build production bundle (Vite + React 19 + antd 6)
# Stage 2: Nginx 1.28 — Serve SPA with security headers & immutable caching
# ══════════════════════════════════════════════════════════════════
# Builds the MODERN unified dashboard (App.tsx 16 pages) — old _archive is never bundled
# Verified: npm ci + vite build → 5447 modules → dist with code-split chunks

FROM node:24-alpine AS builder
WORKDIR /app

# ─── Dependency layer — cached unless package.json changes ───
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# ─── Source layer — builds the modern App.tsx (16 pages, RTL dark) ───
COPY . .
# Production build is strict: TypeScript check + Vite bundle.
RUN npm run build

FROM nginx:1.28-alpine
LABEL maintainer="YOUNES Sovereign" \
      description="YOUNES Master Admin — Unified 16-page dashboard (RTL dark, antd, echarts)" \
      version="1.0.0"

# Security headers and SPA fallback (unified)
COPY dashboard.nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets — modern dashboard only (old _archive excluded by Vite tree-shaking)
COPY --from=builder /app/dist /usr/share/nginx/html

# Hardening: non-root nginx, minimal writable dirs
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    chown -R nginx:nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown nginx:nginx /var/run/nginx.pid && \
    chmod -R 755 /usr/share/nginx/html

USER nginx

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=10s \
  CMD wget -qO- http://127.0.0.1:3000/ >/dev/null || exit 1
