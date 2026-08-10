#!/usr/bin/env bash
# المشغّل الموحد للمعاينة: mock backend + Vite admin dashboard
set -u
ROOT="/home/user/pro_v1/RED_Ultimate_V1-main/RED_Ultimate"

# 1) Mock backend API على 8080
cd "$ROOT"
python3 scripts/mock_backend.py &
MOCK_PID=$!
echo "Mock backend PID=$MOCK_PID"

# 2) لوحة الإدارة Vite على 8088
cd "$ROOT/admin_dashboard"
RED_API_TARGET="http://127.0.0.1:8080" npm run dev
