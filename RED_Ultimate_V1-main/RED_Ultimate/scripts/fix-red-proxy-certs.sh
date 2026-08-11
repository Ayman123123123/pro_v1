#!/bin/sh
# 🔧 إصلاح فوري لحاوية red-proxy المتوقفة بسبب الشهادة
# يصلح الخطأ: cannot load certificate "/etc/ssl/certs/fullchain.pem"

set -eu

echo "🔍 فحص حالة red-proxy..."
docker ps -a --filter name=red-proxy --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo ""

echo "📋 سجلات الخطأ الأخيرة:"
docker logs red-proxy --tail 20 2>&1 | tail -n 20
echo ""

# الحل 1: إذا كنت تستخدم docker-compose.yml الأصلي
if [ -f "docker-compose.yml" ]; then
  echo "🔧 محاولة الإصلاح عبر docker-compose..."
  # إزالة الـ volume الفاسد (إذا كان مجلد بدل ملف)
  if docker volume ls | grep -q red-certs; then
    echo "→ إعادة إنشاء volume red-certs..."
    docker compose down 2>/dev/null || true
    docker volume rm red-certs 2>/dev/null || true
    docker compose run --rm certs-init
    echo "✅ الشهادات أُعيد توليدها"
  fi
  echo "→ إعادة تشغيل red-proxy..."
  docker compose up -d nginx
  sleep 3
  docker logs red-proxy --tail 20 2>&1 | tail -n 20
  echo ""
  echo "✅ تحقق من http://localhost:8088 و https://localhost:8443"
  exit 0
fi

# الحل 2: إصلاح حاوية مستقلة (docker run)
echo "🔧 إصلاح حاوية مستقلة..."
CONTAINER=$(docker ps -a --filter name=red-proxy -q | head -n 1)
if [ -z "$CONTAINER" ]; then
  echo "❌ لم أجد حاوية باسم red-proxy"
  exit 1
fi

echo "→ إنشاء شهادات مؤقتة داخل الحاوية..."
docker exec red-proxy sh -c '
  if [ -d /etc/ssl/red/fullchain.pem ]; then rm -rf /etc/ssl/red/fullchain.pem /etc/ssl/red/privkey.pem; fi
  if [ ! -s /etc/ssl/red/fullchain.pem ]; then
    echo "Generating fallback cert..."
    apk add --no-cache openssl >/dev/null 2>&1
    mkdir -p /etc/ssl/red /etc/ssl/certs
    openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 -subj "/CN=red.local" -addext "subjectAltName=DNS:localhost,DNS:red.local,IP:127.0.0.1" -keyout /etc/ssl/red/privkey.pem -out /etc/ssl/red/fullchain.pem
  fi
  mkdir -p /etc/ssl/certs
  [ ! -e /etc/ssl/certs/fullchain.pem ] && ln -sf /etc/ssl/red/fullchain.pem /etc/ssl/certs/fullchain.pem || true
  [ ! -e /etc/ssl/certs/privkey.pem ] && ln -sf /etc/ssl/red/privkey.pem /etc/ssl/certs/privkey.pem || true
  nginx -t && nginx -s reload || nginx -g "daemon off;" &
  echo "Done"
' 2>&1

echo "→ إعادة تشغيل الحاوية..."
docker restart red-proxy
sleep 3
docker ps --filter name=red-proxy --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker logs red-proxy --tail 20 2>&1 | tail -n 20

echo ""
echo "✅ إذا استمر الخطأ، شغل الحاوية بدون HTTPS مؤقتاً:"
echo "   docker run -d --name red-proxy-fixed -p 8088:80 -v \$(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro nginx:1.27-alpine"
echo "   ثم افتح http://localhost:8088"
