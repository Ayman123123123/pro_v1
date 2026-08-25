#!/usr/bin/env bash
# المعاينة: لوحة Vite فقط. الـ API هو Compose على 8088 — لا mock ولا SQLite.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="${SCRIPT_DIR}/RED_Ultimate_V1-main/RED_Ultimate"

cd "$ROOT/admin_dashboard"
RED_API_TARGET="${RED_API_TARGET:-http://127.0.0.1:8088}" npm run dev
