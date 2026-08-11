#!/usr/bin/env bash
# ==============================================================================
# 🚀 RED Ultimate V1 — تشغيل المنظومة على جهازك
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# عند وجود مجلد المشروع داخل الجذر (مثل RED_Ultimate_V1-main/run.sh)
if [ -d "$DIR/RED_Ultimate" ]; then
  cd "$DIR/RED_Ultimate"
elif [ -d "$DIR/RED_Ultimate_V1-main/RED_Ultimate" ]; then
  cd "$DIR/RED_Ultimate_V1-main/RED_Ultimate"
else
  cd "$DIR"
fi

echo "=============================================================="
echo "  🏛️  RED Ultimate V1 — تشغيل المنصة على جهازك"
echo "=============================================================="
echo "1) تشغيل سريع للتطوير (اللوحة + API)"
echo "2) تشغيل كامل عبر Docker Compose"
echo "3) فحص اتصال DINSTAR (192.168.11.1)"
echo "4) فحص وإصلاح شهادات NGINX SSL و HTTPS"
read -p "اختر رقم الخيار [1]: " OPT
OPT=${OPT:-1}

case $OPT in
  1)
    echo "⚡ تشغيل خادم الـ API واللوحة..."
    python3 scripts/mock_backend.py &
    cd admin_dashboard && npm install && npm run dev
    ;;
  2)
    echo "🐳 تشغيل Docker Compose..."
    [ ! -f .env ] && cp .env.example .env
    docker compose up -d --build
    echo "✅ اللوحة تعمل على: http://localhost:8088 (HTTPS: https://localhost:8443)"
    ;;
  3)
    echo "🔍 فحص الاتصال ببوابة DINSTAR..."
    ping -c 3 192.168.11.1 || true
    ;;
  4)
    echo "🔒 فحص وإصلاح شهادات NGINX Proxy و TLS..."
    ./scripts/fix-red-proxy-certs.sh
    ;;
esac
