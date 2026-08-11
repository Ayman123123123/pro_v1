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
check "check-catalog-accessors.py" python3 scripts/check-catalog-accessors.py
check "check-android-integrity.py" python3 scripts/check-android-integrity.py

echo "[2/6] عقد API (لوحة ↔ خادم)"
check "npm run check:api" bash -c "cd admin_dashboard && npm run check:api"

echo "[3/6] بناء لوحة الإدارة (TypeScript + Vite)"
check "npm run build" bash -c "cd admin_dashboard && npm run build"

echo "[4/6] صياغة SFU + mock_backend"
check "node --check server.js" bash -c "cd media-sfu && node --check server.js"
check "python3 mock_backend.py" python3 -c "import ast; ast.parse(open('scripts/mock_backend.py').read())"

echo "[4b] اختبار حي لخادم mock (smoke test)"
check "mock smoke: health + inventory + overview + PUT + POST" bash -c "
  python3 scripts/mock_backend.py >/tmp/mock_smoke.log 2>&1 &
  MOCK_PID=\$!
  sleep 1.2
  ok=1
  curl -sf http://127.0.0.1:8080/health >/dev/null || ok=0
  curl -sf http://127.0.0.1:8080/api/admin/dinstar/inventory | grep -q 'portIndex' || ok=0
  curl -sf http://127.0.0.1:8080/api/admin/users/user_uuid_001/overview | grep -q 'messages24h' || ok=0
  curl -sf -X PUT http://127.0.0.1:8080/api/admin/dinstar/inventory/gateway_uuid_001/ports/0 -H 'Content-Type: application/json' -d '{}' | grep -q 'portIndex' || ok=0
  curl -sf -o /dev/null -w '%{http_code}' -X POST http://127.0.0.1:8080/api/admin/users/user_uuid_001/temporary-password | grep -q '204' || ok=0
  curl -sf -X POST http://127.0.0.1:8080/api/admin/users/user_uuid_001/remote-app-wipe | grep -q 'commandId' || ok=0
  kill \$MOCK_PID 2>/dev/null
  [ \"\$ok\" = 1 ]
"

echo "[5/6] صحة Docker Compose + nginx"
check "docker-compose YAML" bash -c "test -s docker-compose.yml && grep -q '^services:' docker-compose.yml && grep -q '^volumes:' docker-compose.yml && grep -q '^networks:' docker-compose.yml"
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
