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
    echo "⚡ تشغيل خادم الـ API (SQLite حقيقية) واللوحة..."
    cd admin_dashboard
    npm install
    node dev-server/server.cjs &
    RED_API_TARGET="http://127.0.0.1:8080" npm run dev
    ;;
  2)
    echo "🐳 تشغيل Docker Compose عبر التهيئة الآمنة وفحوص الجاهزية..."
    ./scripts/local-first-run.sh "${SERVER_IP:-}"
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
