#!/usr/bin/env bash
# المسار الوحيد: Docker Compose الحقيقي (Kotlin + Postgres + Mongo + Redis + Nginx).
set -euo pipefail

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

LOCAL_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || true)
if [ -z "${LOCAL_IP:-}" ] || [ "$LOCAL_IP" = "127.0.0.1" ]; then
  LOCAL_IP="192.168.1.50"
fi

echo -e "${CYAN}YOUNES — تشغيل المنصة الحقيقية${NC}"
echo -e "${YELLOW}IP:${NC} ${GREEN}${LOCAL_IP}${NC}"
echo "لا يوجد مسار Node/SQLite. اللوحة والتطبيق يتحدثان إلى Compose على 8088."

"$ROOT_DIR/scripts/local-first-run.sh" --server-ip "$LOCAL_IP"
echo -e "${GREEN}لوحة الإدارة: http://${LOCAL_IP}:8088/${NC}"
echo "كلمة المسؤول من RED_Ultimate/.env فقط."
