#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════
# 🔍 RED Ultimate — الفحص الشامل الآلي (كل شيء في أمر واحد)
# يشغّل: فاحص الكيانات + عقد API + بناء اللوحة + SFU + mock + YAML
# ══════════════════════════════════════════════════════════════════
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT="$(pwd)"
PASS=0; FAIL=0

check() {
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "  ✅ $name"
    PASS=$((PASS+1))
  else
    echo "  ❌ $name"
    FAIL=$((FAIL+1))
  fi
}

echo "════════════════════════════════════════════════"
echo "  🔍 RED Ultimate — الفحص الشامل"
echo "════════════════════════════════════════════════"

echo "[1/6] تطابق الكيانات مع قاعدة البيانات"
check "check-schema-consistency.py" python3 scripts/check-schema-consistency.py

echo "[2/6] عقد API (لوحة ↔ خادم)"
check "npm run check:api" bash -c "cd admin_dashboard && npm run check:api"

echo "[3/6] بناء لوحة الإدارة (TypeScript + Vite)"
check "npm run build" bash -c "cd admin_dashboard && npm run build"

echo "[4/6] صياغة SFU + mock_backend"
check "node --check server.js" bash -c "cd media-sfu && node --check server.js"
check "python3 mock_backend.py" python3 -c "import ast; ast.parse(open('scripts/mock_backend.py').read())"

echo "[5/6] صحة Docker Compose + nginx"
check "docker-compose YAML" python3 -c "import yaml; yaml.safe_load(open('docker-compose.yml'))"
check "nginx.conf متوازن" bash -c "grep -c '{' nginx.conf >/dev/null && grep -c '}' nginx.conf >/dev/null"

echo "[6/6] سكربتات bash"
check "bash -n run.sh" bash -n ../run.sh
check "bash -n ci-build-all.sh" bash -n ../scripts/ci-build-all.sh
check "bash -n run-all-local.sh" bash -n scripts/run-all-local.sh

echo ""
echo "════════════════════════════════════════════════"
echo "  النتيجة: $PASS نجحت | $FAIL فشلت"
echo "════════════════════════════════════════════════"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
