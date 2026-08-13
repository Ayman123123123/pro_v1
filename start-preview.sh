#!/usr/bin/env bash
# المعاينة: لوحة Vite فقط. الـ API هو Compose على 8088 — لا SQLite.
set -u
ROOT="/home/user/pro_v1/RED_Ultimate_V1-main/RED_Ultimate"
cd "$ROOT/admin_dashboard"
RED_API_TARGET="${RED_API_TARGET:-http://127.0.0.1:8088}" npm run dev
