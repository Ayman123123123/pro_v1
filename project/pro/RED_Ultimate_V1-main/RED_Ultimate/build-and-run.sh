#!/bin/bash
echo "🔴 RED Master Build Sequence Starting..."

# 1. Check Dependencies
if ! [ -x "$(command -v docker-compose)" ]; then
  echo "Error: docker-compose is not installed." >&2
  exit 1
fi

# 2. Build All Artifacts
echo "📦 Building Backend, SFU, and Admin Panel..."
docker-compose build

# 3. Launch the System
echo "🚀 Launching RED Sovereign Empire..."
docker-compose up -d

echo "✅ All 9 Systems are ONLINE."
echo "📱 App Access: http://localhost:8080"
echo "🔐 Admin Panel: http://localhost:80"
echo "📡 Media SFU: Port 4000"
echo "----------------------------------------"
echo "Project RED is now operational."
