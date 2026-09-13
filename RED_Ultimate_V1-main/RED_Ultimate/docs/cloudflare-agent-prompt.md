Hello! I need your help configuring Cloudflare Tunnel for my project.

## Project: RED Sovereign (VoIP/PSTN Calling App)

RED Sovereign is an encrypted messaging and calling app for Yemen. It has two critical calling routes:

### 1. RED-to-RED Calls (App ↔ App)
- WebRTC signaling over WebSocket (`/ws/calls`)
- Audio/Video via mediasoup SFU (`red-media-sfu`)
- This works peer-to-peer via the media server

### 2. PSTN Calls (App ↔ Real Phone Numbers) ← **This is why I need Cloudflare Tunnel**
- The app needs to call real Yemeni mobile numbers (e.g., +967777123456)
- Architecture: Android App → **Asterisk (WebRTC/SIP via WSS)** → DINSTAR GSM Gateway → Yemeni Mobile Network
- The app connects to Asterisk via **WebSocket Secure (WSS)** on port 8089
- Asterisk bridges the call to a DINSTAR UC2000 GSM gateway on the local LAN
- **This is the critical path that must work over the internet**

## The Problem

My server is behind **CGNAT** in Sanaa, Yemen (ISP: AS30873). I cannot:
- Open any inbound ports (no port forwarding)
- Get a public static IP
- Use a VPS (no budget for one)

The Android app MUST connect to Asterisk via WSS from anywhere in the world — this is non-negotiable for PSTN calling to work.

## Current Docker Services

```yaml
services:
  nginx:        # Reverse proxy — exposes port 8088 (HTTP) / 8443 (HTTPS)
  backend:      # Spring Boot API — internal port 8080
  pstn-gateway: # Asterisk — SIP/UDP:5060, WSS:8089
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
| `my-domain.com/ws/sip` | WebSocket | `ws://pstn-gateway:8089` | **Critical: Asterisk WSS for PSTN calls** |

### 3. Questions
- Does Cloudflare Tunnel support WebSocket proxying for the `/ws/sip` route? (Asterisk uses WSS for SIP signaling)
- Do I need to configure anything special for long-lived WebSocket connections (SIP REGISTER keeps a persistent connection)?
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
