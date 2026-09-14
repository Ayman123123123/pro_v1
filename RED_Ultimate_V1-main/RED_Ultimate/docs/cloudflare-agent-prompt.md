Hello! I need your help configuring Cloudflare Tunnel for my project.

## Project: RED Sovereign (WebRTC Calling App)

RED Sovereign is an encrypted messaging and calling app for Yemen. Calls are RED-to-RED over WebRTC:

- WebRTC signaling over WebSocket (`/ws/master`, `/ws/calls`)
- Audio/Video via mediasoup SFU (`red-media-sfu`)
- NAT traversal via coturn TURN (`coturn`)

## The Problem

My server is behind **CGNAT** in Sanaa, Yemen (ISP: AS30873). I cannot:
- Open any inbound ports (no port forwarding)
- Get a public static IP
- Use a VPS (no budget for one)

The Android app MUST reach the API and signaling WebSockets from anywhere in the world — this is non-negotiable for calling to work.

## Current Docker Services

```yaml
services:
  nginx:        # Reverse proxy — exposes port 8088 (HTTP) / 8443 (HTTPS)
  backend:      # Spring Boot API — internal port 8080
  media-sfu:    # mediasoup SFU — for RED-to-RED calls
  db-postgres:  # PostgreSQL
  db-mongo:     # MongoDB
  cache-redis:  # Redis
  storage:      # MinIO
  turn:         # coturn TURN server (for UDP media relay)
```

All services are on Docker network `red-net`. Only nginx is exposed to the host.

## What I Need

### 1. Cloudflare Tunnel YAML for docker-compose.yml
I need a `cloudflared` service that I can add to my existing docker-compose.yml. It should:
- Use the official `cloudflare/cloudflared` image
- Accept `CLOUDFLARE_TUNNEL_TOKEN` as an environment variable
- Be on the same `red-net` Docker network so it can reach all services

### 2. Public Hostname Routing
I need two routes:

| Hostname | Protocol | Target | Purpose |
|----------|----------|--------|---------|
| `my-domain.com` | HTTP | `http://nginx:80` | Backend API + Admin Dashboard |
| `my-domain.com/ws/*` | WebSocket | `http://nginx:80` | **Critical: signaling WebSockets (proxied to backend)** |

### 3. Questions
- Does Cloudflare Tunnel support WebSocket proxying for the `/ws/*` signaling routes?
- Do I need to configure anything special for long-lived WebSocket connections (signaling keeps a persistent connection)?
- Will the SSL certificate be auto-provisioned by Cloudflare for my domain?

## My docker-compose.yml snippet (what I have so far)

```yaml
  cloudflared:
    image: cloudflare/cloudflared:latest
    container_name: red-cloudflared
    restart: unless-stopped
    command: tunnel --no-autoupdate run
    environment:
      - TUNNEL_TOKEN=${CLOUDFLARE_TUNNEL_TOKEN}
    networks:
      - red-net
    profiles:
      - tunnel
```

## Environment
- Host OS: Windows (Docker Desktop)
- Server IP (LAN): 192.168.11.104
- ISP: AS30873 (PTC Yemen) — CGNAT, no inbound ports possible
- Domain: not yet registered (planning to use freedomain.one for a free .com domain)
- Cloudflare account: will create

Please provide the complete setup including the tunnel configuration, hostname routing, and any Cloudflare Dashboard steps I need to follow. Thank you!
