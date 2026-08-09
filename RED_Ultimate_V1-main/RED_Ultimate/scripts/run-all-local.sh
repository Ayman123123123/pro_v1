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
read -p "أدخل رقم الخيار [1]: " CHOICE
CHOICE=${CHOICE:-1}

case $CHOICE in
  1)
    echo -e "${GREEN}🚀 جاري تشغيل الحاويات عبر Docker Compose...${NC}"
    cd "$ROOT_DIR"
    
    # تجهيز ملف .env إن لم يكن موجوداً
    if [ ! -f .env ]; then
      echo -e "${YELLOW}⚙️ إنشاء ملف الإعدادات .env...${NC}"
      cp .env.example .env
      sed -i "s/192.168.1.50/$LOCAL_IP/g" .env
    fi

    # تشغيل Docker
    if command -v docker &>/dev/null && docker info &>/dev/null; then
      docker compose up -d --build
      echo -e "${GREEN}✅ تم تشغيل جميع الخدمات بنجاح!${NC}"
      echo -e "🔗 لوحة الإدارة: ${CYAN}http://$LOCAL_IP:8088${NC} أو ${CYAN}http://localhost:8088${NC}"
      echo -e "🔗 واجهة الباكند: ${CYAN}http://$LOCAL_IP:8080/health${NC}"
    else
      echo -e "${RED}⚠️ Docker غير مشغل أو غير مثبت. جاري التحويل للتشغيل المحلي السريع...${NC}"
      cd "$ROOT_DIR/admin_dashboard" && npm install && npm run dev &
      python3 "$ROOT_DIR/scripts/mock_backend.py" &
    fi
    ;;

  2)
    echo -e "${GREEN}⚡ جاري تشغيل بيئة التطوير السريعة...${NC}"
    python3 "$ROOT_DIR/scripts/mock_backend.py" &
    PID_BACKEND=$!
    cd "$ROOT_DIR/admin_dashboard"
    npm install
    npm run dev &
    PID_FRONTEND=$!
    echo -e "${GREEN}✅ اللوحة تعمل الآن على: http://localhost:5173${NC}"
    echo -e "${YELLOW}اضغط Ctrl+C للإيقاف${NC}"
    wait $PID_FRONTEND $PID_BACKEND
    ;;

  3)
    echo -e "${GREEN}📱 جاري بناء تطبيق الأندرويد...${NC}"
    cd "$ROOT_DIR"
    if [ -f "./gradlew" ]; then
      ./gradlew assembleDebug
      echo -e "${GREEN}✅ تم بناء الـ APK في: app/build/outputs/apk/debug/app-debug.apk${NC}"
      if command -v adb &>/dev/null && adb devices | grep -q "device$"; then
        echo -e "${GREEN}📲 جاري التثبيت على الهاتف عبر ADB...${NC}"
        adb install -r app/build/outputs/apk/debug/app-debug.apk
      fi
    else
      echo -e "${YELLOW}افتح مجلد android في Android Studio واضغط Run على هاتفك.${NC}"
    fi
    ;;

  4)
    echo -e "${CYAN}🔍 جاري فحص الاتصال ببوابة DINSTAR على 192.168.11.1:443...${NC}"
    curl -k -s --connect-timeout 3 https://192.168.11.1/ || echo -e "${RED}❌ تعذر الوصول لـ 192.168.11.1. تأكد من ضبط IP كرت الشبكة على 192.168.11.X.${NC}"
    ;;
esac
