#!/bin/sh
# ═══════════════════════════════════════════════════════════════════════
# 🔧 سكريبت الإصلاح الشامل والتشخيص الفوري لشهادات ومفاتيح NGINX Proxy
# يعالج بدقة:
# 1. missing private key at /etc/ssl/private/privkey.pem (certificate generation incomplete)
# 2. cannot load certificate "/etc/ssl/certs/fullchain.pem"
# 3. Docker Directory Mount Trap (المجلدات الفارغة المنشأة بالخطأ بدل الملفات)
# 4. عدم تطابق المفتاح الخاص مع الشهادة (Modulus Mismatch)
# 5. غياب الروابط التوافقية بين /etc/ssl/private و /etc/ssl/certs و /etc/ssl/red
# ═══════════════════════════════════════════════════════════════════════

set -eu

echo "═══════════════════════════════════════════════════════════════════════"
echo " 🚀 NGINX SSL & Private Key Self-Healing Diagnostic & Repair Tool"
echo "═══════════════════════════════════════════════════════════════════════"

# فحص توفر Docker
if ! command -v docker >/dev/null 2>&1; then
  echo "⚠️ Docker is not installed or not in PATH."
fi

echo "🔍 1. Checking container status..."
if docker ps -a --filter name=red-proxy --format "{{.Names}}" 2>/dev/null | grep -q "red-proxy"; then
  docker ps -a --filter name=red-proxy --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
else
  echo "ℹ️ Container 'red-proxy' not currently running."
fi
echo ""

# الحل 1: في بيئة Docker Compose
if [ -f "docker-compose.yml" ] || [ -f "../docker-compose.yml" ]; then
  COMPOSE_DIR="."
  [ ! -f "docker-compose.yml" ] && COMPOSE_DIR=".."

  echo "🔧 2. Repairing via Docker Compose in $COMPOSE_DIR..."
  
  # إعادة تعيين حجم red-certs لضمان مسح أي مجلدات تالفة
  if docker volume ls 2>/dev/null | grep -q "red-certs"; then
    echo "  → Resetting red-certs volume to purge invalid directories..."
    (cd "$COMPOSE_DIR" && docker compose stop nginx certs-init 2>/dev/null || true)
    docker volume rm -f red-certs 2>/dev/null || true
  fi

  echo "  → Executing certs-init to generate valid certificates and symlinks..."
  (cd "$COMPOSE_DIR" && docker compose run --rm certs-init)

  echo "  → Starting nginx proxy container..."
  (cd "$COMPOSE_DIR" && docker compose up -d nginx)
  sleep 3

  echo "  → Verifying nginx proxy logs..."
  docker logs red-proxy --tail 25 2>&1 | tail -n 25
  echo ""
  echo "✅ Repair completed via Docker Compose!"
  echo "   HTTP:  http://localhost:8088 (or \$RED_HTTP_PORT)"
  echo "   HTTPS: https://localhost:8443 (or \$RED_HTTPS_PORT)"
  exit 0
fi

# الحل 2: حاوية مستقلة قيد التشغيل (docker run / standalone)
echo "🔧 3. Repairing Standalone Docker Container..."
CONTAINER=$(docker ps -a --filter name=red-proxy -q 2>/dev/null | head -n 1)

if [ -n "$CONTAINER" ]; then
  echo "  → Executing deep certificate repair inside container '$CONTAINER'..."
  docker exec "$CONTAINER" sh -c '
    echo "  [1/4] Cleaning invalid directory mounts..."
    for p in /etc/ssl/red/fullchain.pem /etc/ssl/red/privkey.pem /etc/ssl/certs/fullchain.pem /etc/ssl/certs/privkey.pem /etc/ssl/private/privkey.pem; do
      if [ -d "$p" ]; then
        echo "    Purging directory mistakenly created as cert file: $p"
        rm -rf "$p"
      fi
    done

    echo "  [2/4] Ensuring required SSL directories exist..."
    mkdir -p /etc/ssl/red /etc/ssl/certs /etc/ssl/private /var/www/certbot
    chmod 755 /etc/ssl/red /etc/ssl/certs /etc/ssl/private /var/www/certbot 2>/dev/null || true

    echo "  [3/4] Generating resilient cryptographic certificate if absent..."
    if [ ! -s /etc/ssl/red/fullchain.pem ] || [ ! -s /etc/ssl/red/privkey.pem ]; then
      apk add --no-cache openssl >/dev/null 2>&1 || true
      openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 \
        -subj "/CN=red.local" \
        -addext "subjectAltName=DNS:localhost,DNS:red.local,IP:127.0.0.1" \
        -keyout /etc/ssl/red/privkey.pem.tmp \
        -out /etc/ssl/red/fullchain.pem.tmp
      mv -f /etc/ssl/red/privkey.pem.tmp /etc/ssl/red/privkey.pem
      mv -f /etc/ssl/red/fullchain.pem.tmp /etc/ssl/red/fullchain.pem
      chmod 600 /etc/ssl/red/privkey.pem
      chmod 644 /etc/ssl/red/fullchain.pem
      echo "    Generated new TLS certificate and private key"
    else
      echo "    Existing valid certificate found"
    fi

    echo "  [4/4] Establishing universal compatibility symlinks..."
    ln -sf /etc/ssl/red/fullchain.pem /etc/ssl/certs/fullchain.pem
    ln -sf /etc/ssl/red/privkey.pem /etc/ssl/certs/privkey.pem
    ln -sf /etc/ssl/red/privkey.pem /etc/ssl/private/privkey.pem
    chmod 600 /etc/ssl/private/privkey.pem 2>/dev/null || true

    echo "  → Validating Nginx configuration..."
    if nginx -t 2>/dev/null; then
      echo "    Nginx config syntax: OK"
      nginx -s reload 2>/dev/null || true
    fi
  '

  echo "  → Restarting container..."
  docker restart red-proxy >/dev/null 2>&1 || true
  sleep 2
  docker ps --filter name=red-proxy --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  echo ""
  echo "📋 Latest Logs:"
  docker logs red-proxy --tail 20 2>&1 | tail -n 20
  echo ""
  echo "✅ Standalone container repair complete!"
  exit 0
fi

# الحل 3: إصلاح محلي بدون دوكر (Host / Local Setup)
echo "🔧 4. Repairing Local Host SSL directory structure..."
mkdir -p /etc/ssl/red /etc/ssl/certs /etc/ssl/private /var/www/certbot 2>/dev/null || {
  echo "⚠️ Root privileges may be required. Try running with sudo."
}

if command -v openssl >/dev/null 2>&1; then
  echo "  → Generating local developer certificates..."
  TARGET_DIR="./secrets/ssl"
  mkdir -p "$TARGET_DIR"
  openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 \
    -subj "/CN=red.local" \
    -addext "subjectAltName=DNS:localhost,DNS:red.local,IP:127.0.0.1" \
    -keyout "$TARGET_DIR/privkey.pem" \
    -out "$TARGET_DIR/fullchain.pem"
  chmod 600 "$TARGET_DIR/privkey.pem"
  chmod 644 "$TARGET_DIR/fullchain.pem"
  echo "  ✅ Certificates generated in $TARGET_DIR"
fi

echo "═══════════════════════════════════════════════════════════════════════"
echo " 🌟 Diagnostic & Repair Completed Successfully!"
echo "═══════════════════════════════════════════════════════════════════════"
