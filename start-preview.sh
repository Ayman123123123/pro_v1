#!/usr/bin/env bash
# المشغّل الموحد للمعاينة: خادم تطوير SQLite حقيقي + Vite admin dashboard
set -u
ROOT="/home/user/pro_v1/RED_Ultimate_V1-main/RED_Ultimate"

# 1) خادم التطوير الحقيقي (SQLite دائمة) على 8080 — ينفّذ عقد backend-server الكامل
cd "$ROOT/admin_dashboard"
node dev-server/server.cjs &
API_PID=$!
echo "Dev API (SQLite) PID=$API_PID"

# 2) لوحة الإدارة Vite على 8088 (توكّل /api إلى 8080)
RED_API_TARGET="http://127.0.0.1:8080" npm run dev
