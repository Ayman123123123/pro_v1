#!/usr/bin/env bash
# ==============================================================================
# RED Ultimate Multi-Platform Builder & CI Script
# Builds Frontend, Backend, and Android components
# NOTE: the old reference to
#   RED_Ultimate_V1-main/RED_Ultimate/scripts/mock_backend.py
# never existed in the repo. The real stub now lives at scripts/mock_backend.py
# (repo root) and is used below.
# ==============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOCK_PORT="${MOCK_PORT:-8080}"
DASH_PORT="${DASH_PORT:-5173}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-60}"
echo "Building RED Ultimate across all platforms..."

cleanup() { kill "$(jobs -p)" 2>/dev/null || true; }
trap cleanup EXIT

wait_for_url() {
  local url="$1" label="$2" i=0
  echo "Waiting for $label at $url (timeout ${WAIT_TIMEOUT}s)..."
  while [ "$i" -lt "$WAIT_TIMEOUT" ]; do
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
      echo "OK: $label is healthy ($url)"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "ERROR: timed out waiting for $label at $url" >&2
  return 1
}

# 1. Build Frontend (fail fast; verify real artifact, not echo)
echo "==> 1. Building Admin Dashboard (React + TypeScript)..."
cd "$DIR/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard"
npm install
npm run build
test -d dist
echo "Admin Dashboard built successfully in dist/"

# 2. Start mock backend stub + health-gated wait (no false success)
echo "==> 2. Starting local mock backend on 127.0.0.1:${MOCK_PORT}..."
MOCK_PORT="$MOCK_PORT" python3 "$DIR/scripts/mock_backend.py" &
wait_for_url "http://127.0.0.1:${MOCK_PORT}/health" "mock backend"

# 3. Preview the built dashboard + health-gated wait
echo "==> 3. Starting dashboard preview on 0.0.0.0:${DASH_PORT}..."
npm run preview -- --host 0.0.0.0 --port "$DASH_PORT" &
wait_for_url "http://127.0.0.1:${DASH_PORT}/" "dashboard preview"

echo "All services verified healthy (mock backend :${MOCK_PORT}, preview :${DASH_PORT})."
