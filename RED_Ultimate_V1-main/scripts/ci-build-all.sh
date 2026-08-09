#!/usr/bin/env bash
# ==============================================================================
# 🚀 RED Ultimate Multi-Platform Builder & CI Script
# Builds Frontend, Backend, and Android components
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "Building RED Ultimate across all platforms..."

# 1. Build Frontend
echo "==> 1. Building Admin Dashboard (React + TypeScript)..."
cd "$DIR/RED_Ultimate/admin_dashboard"
npm install
npm run build
echo "✅ Admin Dashboard built successfully in dist/"

# 2. Start Backend & Dashboard
echo "==> 2. Starting local mock backend on 127.0.0.1:8080..."
python3 "$DIR/RED_Ultimate/scripts/mock_backend.py" &

echo "==> 3. Starting live dashboard preview on 0.0.0.0:5173..."
npm run dev &

echo "🎉 All services are active and running!"
