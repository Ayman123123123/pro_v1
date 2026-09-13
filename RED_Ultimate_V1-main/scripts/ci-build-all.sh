#!/usr/bin/env bash
# Build the canonical dashboard and start a local preview.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$DIR/RED_Ultimate"

cd "$PROJECT/admin_dashboard"
npm ci --no-audit --no-fund
npm run build
exec npm run dev -- --host 0.0.0.0 --port 5173
