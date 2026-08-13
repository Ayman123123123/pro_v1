#!/usr/bin/env bash
# Recover the YOUNES Docker stack after an engine crash or a host :8080 fight.
# Production truth is Docker (Kotlin + Postgres + Nginx on 8088).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
REBUILD="${1:-}"

echo "=== YOUNES Docker recover ==="
echo "Root: $ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not on PATH." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker engine is not responding. Start Docker Desktop and retry." >&2
  exit 1
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE — copy .env.example to .env first." >&2
  exit 1
fi

free_port() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "$pids" ]; then
      echo "Stopping host listener on :$port ($pids)"
      # shellcheck disable=SC2086
      kill $pids 2>/dev/null || true
    fi
  fi
}

free_port 8080

cd "$ROOT"
docker compose --env-file "$ENV_FILE" config --quiet

if [ "$REBUILD" = "--rebuild" ] || [ "$REBUILD" = "-RebuildBackend" ]; then
  echo "Rebuilding backend image..."
  docker compose --env-file "$ENV_FILE" build backend
fi

docker compose --env-file "$ENV_FILE" up -d

echo -n "Waiting for http://127.0.0.1:8088/health"
for _ in $(seq 1 60); do
  if curl -fsS --max-time 3 "http://127.0.0.1:8088/health" >/dev/null 2>&1; then
    echo
    echo "PASS  http://127.0.0.1:8088/health"
    echo "Admin panel: http://127.0.0.1:8088/"
    echo "Do not run npm run dev:server while this stack is up."
    exit 0
  fi
  echo -n "."
  sleep 3
done

echo
docker compose --env-file "$ENV_FILE" ps
docker compose --env-file "$ENV_FILE" logs --tail=120 backend
echo "Nginx /health on 8088 did not become ready." >&2
exit 1
