#!/usr/bin/env bash
# Unified preview launcher: mock backend + Vite admin dashboard
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/RED_Ultimate_V1-main/RED_Ultimate"

# 1) Mock backend API on 8080
cd "$ROOT"
python3 scripts/mock_backend.py &
MOCK_PID=$!
echo "Mock backend PID=$MOCK_PID"

# 2) Vite admin dashboard on 8088
cd "$ROOT/admin_dashboard"
RED_API_TARGET="http://127.0.0.1:8080" npm run dev