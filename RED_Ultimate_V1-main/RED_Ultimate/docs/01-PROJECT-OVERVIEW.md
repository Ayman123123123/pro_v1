# Project Overview

RED is a local-first messaging and calling platform built around a RED identity and administrator approval.

## Canonical components

| Component | Location | Role |
|---|---|---|
| Android application | `red-app/` | The canonical `:app` Gradle module |
| Backend | `backend-server/` | Spring Boot API, authentication, messaging and administration |
| Admin dashboard | `admin_dashboard/` | React/TypeScript operations UI |
| Media SFU | `media-sfu/` | WebRTC media routing |
| Shared protocol | `shared-proto/` | Protobuf contracts |
| Runtime | `docker-compose.yml` and `nginx.conf` | Local service orchestration and ingress |

## Runtime path

The browser and Android clients use Nginx for HTTP and WebSocket ingress. The backend owns identity, approval, tokens, device certificates, messaging, social content and call history. WebRTC signaling uses `/ws/calls`; media is routed through the SFU and TURN when required.

## Data stores

PostgreSQL stores durable relational state and approvals. MongoDB stores document-oriented application data. Redis provides short-lived state and rate limits. MinIO stores media objects.

## Build truth

`settings.gradle.kts`, `docker-compose.yml`, and the CI workflow define the active build graph. Legacy Signal sources remain reference material unless included by the canonical graph.
