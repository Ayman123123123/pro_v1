#!/usr/bin/env bash
# ==============================================================================
# 🚀 RED Ultimate — One-Click Master Local Runner (Linux / macOS / WSL)
# تشغيل المنصة كاملة محلياً بضغطة زر واحدة
# ==============================================================================

set -e

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}==============================================================${NC}"
echo -e "${GREEN}  🏛️  RED Ultimate V1 — تشغيل المنصة السيادية محلياً  ${NC}"
echo -e "${CYAN}==============================================================${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 1. كشف عنوان IP المحلي
LOCAL_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")
if [ -z "$LOCAL_IP" ] || [ "$LOCAL_IP" = "127.0.0.1" ]; then
  LOCAL_IP="192.168.1.50"
fi

echo -e "${YELLOW}📍 عنوان IP المحلي المكتشف:${NC} ${GREEN}${LOCAL_IP}${NC}"
echo -e "${YELLOW}🌐 عنوان بوابة DINSTAR:${NC} ${GREEN}192.168.11.1:443${NC}"

# 2. خيارات التشغيل
echo ""
echo -e "${CYAN}اختر طريقة التشغيل:${NC}"
echo "1) تشغيل كامل عبر Docker Compose (الخيار الموصى به - جميع الخدمات)"
echo "2) تشغيل سريع للتطوير (لوحة الإدارة + الـ API المحلي)"
echo "3) بناء تطبيق الأندروID (APK) وتثبيته على الهاتف"
echo "4) فحص الاتصال ببوابة DINSTAR UC2000-VE-8G"
echo "5) تهيئة وإصلاح شهادات ومفاتيح NGINX SSL (Self-Healing TLS)"
read -p "أدخل رقم الخيار [1]: " CHOICE
CHOICE=${CHOICE:-1}

case $CHOICE in
  1)
    echo -e "${GREEN}🚀 جاري التشغيل الآمن عبر مسار التهيئة والفحص الموحّد...${NC}"
    "$ROOT_DIR/scripts/local-first-run.sh" --server-ip "$LOCAL_IP"
    ;;

  2)
    echo -e "${GREEN}⚡ جاري تشغيل بيئة التطوير السريعة (خادم SQLite حقيقي)...${NC}"
    cd "$ROOT_DIR/admin_dashboard"
    npm install
    node dev-server/server.cjs &
    PID_BACKEND=$!
    RED_API_TARGET="http://127.0.0.1:8080" npm run dev &
    PID_FRONTEND=$!
    echo -e "${GREEN}✅ اللوحة تعمل الآن على: http://localhost:8088${NC}"
    echo -e "${YELLOW}اضغط Ctrl+C للإيقاف${NC}"
    wait $PID_FRONTEND $PID_BACKEND
    ;;

  3)
    echo -e "${GREEN}📱 جاري بناء تطبيق الأندرويد...${NC}"
    cd "$ROOT_DIR"
    if [ -f "./gradlew" ]; then
      ./gradlew :app:assembleDebug -PRED_SERVER_URL="http://$LOCAL_IP:8088" -PRED_SKIP_BUILD_LOGIC=true
      APK="$(find red-app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n 1)"
      [ -n "$APK" ] || { echo -e "${RED}❌ انتهى Gradle بلا ملف APK${NC}"; exit 1; }
      echo -e "${GREEN}✅ تم بناء الـ APK في: $APK${NC}"
      if command -v adb &>/dev/null && adb devices | grep -q "device$"; then
        echo -e "${GREEN}📲 جاري التثبيت على الهاتف عبر ADB...${NC}"
        adb install -r "$APK"
      fi
    else
      echo -e "${YELLOW}افتح مجلد android في Android Studio واضغط Run على هاتفك.${NC}"
    fi
    ;;

  4)
    echo -e "${CYAN}🔍 جاري فحص الاتصال ببوابة DINSTAR على 192.168.11.1:443...${NC}"
    curl -k -s --connect-timeout 3 https://192.168.11.1/ || echo -e "${RED}❌ تعذر الوصول لـ 192.168.11.1. تأكد من ضبط IP كرت الشبكة على 192.168.11.X.${NC}"
    ;;

  5)
    echo -e "${CYAN}🔒 جاري فحص وإصلاح شهادات NGINX Proxy و TLS...${NC}"
    "$ROOT_DIR/scripts/fix-red-proxy-certs.sh"
    ;;
esac
