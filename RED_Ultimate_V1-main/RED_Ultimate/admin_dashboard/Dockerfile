# ══════════════════════════════════════════════════════════════════
# 🏛️ YOUNES Admin Dashboard — Modern Unified Build (39 pages, TanStack Router)
# Stage 1: Node.js 24 — Build production bundle (Vite + React 19 + antd 6)
# Stage 2: Nginx 1.29 — Serve SPA with security headers & immutable caching
# ══════════════════════════════════════════════════════════════════
# Builds the MODERN unified dashboard (src/routes/* via TanStack Router) — App.tsx retired 2026-09-24
# Verified: npm ci + npm run lint + npm run build (Vite 5) → dist with code-split chunks

FROM node:24-alpine AS builder
WORKDIR /app

# ─── Dependency layer — cached unless package.json changes ───
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# ─── Source layer — builds the modern router pages (39 pages, RTL dark) ───
COPY . .
# Strict gates: ESLint 9 (flat) + TypeScript check + Vite 5 bundle.
RUN npm run lint
RUN npm run build

FROM nginx:1.29-alpine
LABEL maintainer="YOUNES Sovereign" \
      description="YOUNES Master Admin — Unified 39-page dashboard (RTL dark, TanStack Router)" \
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
