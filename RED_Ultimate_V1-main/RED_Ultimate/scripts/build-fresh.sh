#!/bin/bash
# ════════════════════════════════════════════════════════════════════════
#  RED Ultimate — Fresh Build (no cache)
#  Forces a complete rebuild with all caches cleared
# ════════════════════════════════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "═══════════════════════════════════════════════════════════════════════"
echo "  RED Ultimate — Fresh Build (no cache)"
echo "═══════════════════════════════════════════════════════════════════════"

# Stop containers
echo "[1/5] Stopping containers..."
docker compose down 2>&1 || true

# Remove old images
echo "[2/5] Removing old images..."
for img in red-sovereign-backend red-sovereign-admin-panel red-sovereign-pstn-gateway red-sovereign-media-sfu; do
    if docker images -q "$img" 2>/dev/null; then
        docker rmi -f "$img" 2>&1 || true
    fi
done

# Clear Gradle cache
echo "[3/5] Clearing Gradle cache..."
rm -rf ~/.gradle/caches/build-cache-1
rm -rf ~/.gradle/caches/kotlin-build
rm -rf backend-server/build
rm -rf backend-server/.gradle

# Prune Docker build cache
echo "[4/5] Pruning Docker build cache..."
docker builder prune -f

# Build fresh
echo "[5/5] Starting fresh build..."
SERVER_IP="${SERVER_IP:-192.168.137.19}"
echo "  Server IP: $SERVER_IP"
echo "  This will take 5-10 minutes..."
echo ""

./scripts/local-first-run.sh --server-ip "$SERVER_IP" --build-android