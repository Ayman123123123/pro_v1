#!/usr/bin/env bash
# ==============================================================================
# RED Ultimate V1 - المشغل الموحد لبيئات Linux/macOS
# ==============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# تحديد مجلد المشروع الرئيسي (نسخة RED_Ultimate_V1-main/run.sh)
if [ -d "$DIR/RED_Ultimate" ]; then
  cd "$DIR/RED_Ultimate"
elif [ -d "$DIR/RED_Ultimate_V1-main/RED_Ultimate" ]; then
  cd "$DIR/RED_Ultimate_V1-main/RED_Ultimate"
else
  cd "$DIR"
fi

echo "=============================================================="
echo "  RED Ultimate V1 - المشغل الموحد للنظام"
echo "=============================================================="
echo "1) تشغيل خادم التطوير (الخادم الوهمي + API)"
echo "2) تشغيل المنصة الكاملة Docker Compose"
echo "3) فحص بوابة DINSTAR (192.168.11.1)"
echo "4) فحص وإصلاح NGINX SSL و HTTPS"
read -p "اختر خيار التشغيل [1]: " OPT
OPT=${OPT:-1}

case $OPT in
  1)
    echo "جاري تشغيل الخادم الوهمي للـ API..."
    python3 scripts/mock_backend.py &
    cd admin_dashboard && npm install && npm run dev
    ;;
  2)
    echo "جاري تشغيل Docker Compose وتجهيز البيئة والمتابعة..."
    ./scripts/local-first-run.sh "${SERVER_IP:-}"
    ;;
  3)
    echo "فحص بوابات DINSTAR..."
    ping -c 3 192.168.11.1 || true
    ;;
  4)
    echo "فحص وإصلاح NGINX Proxy و TLS..."
    ./scripts/fix-red-proxy-certs.sh
    ;;
esac
