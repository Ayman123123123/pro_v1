#!/usr/bin/env bash
# YOUNES local quality gate. Runs executable checks and labels unavailable tools
# as SKIP; it never reports a grep-only approximation as a real Docker test.
set -u
cd "$(dirname "$0")/.." || exit 1

PASS=0
FAIL=0
SKIP=0
LOG_FILE="$(mktemp)"
trap 'rm -f "$LOG_FILE"' EXIT

check() {
  local name="$1"; shift
  if "$@" >"$LOG_FILE" 2>&1; then
    printf '  ✅ %s\n' "$name"
    PASS=$((PASS + 1))
  else
    printf '  ❌ %s\n' "$name"
    sed 's/^/       /' "$LOG_FILE" | tail -n 120
    FAIL=$((FAIL + 1))
  fi
}

skip() {
  printf '  ⏭️  %s — %s\n' "$1" "$2"
  SKIP=$((SKIP + 1))
}

printf '%s\n' '════════════════════════════════════════════════'
printf '%s\n' '  YOUNES — comprehensive local quality gate'
printf '%s\n' '════════════════════════════════════════════════'

printf '%s\n' '[1/7] Infrastructure invariants'
check 'BuildKit / Compose / Nginx / readiness / Flyway' python3 scripts/check-infrastructure.py

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  check 'docker compose config' docker compose --env-file .env.example config --quiet
else
  skip 'docker compose config' 'Docker Compose is unavailable on this host'
fi

printf '%s\n' '[2/7] Database/entity and Kotlin static contracts'
check 'schema consistency' python3 scripts/check-schema-consistency.py
check 'version-catalog accessors' python3 scripts/check-catalog-accessors.py
check 'Android integrity' python3 scripts/check-android-integrity.py
check 'Kotlin static checks' python3 scripts/check-kotlin-static.py

printf '%s\n' '[3/7] Admin dashboard contracts and production build'
if command -v npm >/dev/null 2>&1 && [[ -d admin_dashboard/node_modules ]]; then
  check 'admin API/guard/role/type checks' bash -c 'cd admin_dashboard && npm run check'
  check 'admin production build' bash -c 'cd admin_dashboard && npm run build'
else
  skip 'admin checks' 'run npm ci in admin_dashboard first'
fi

printf '%s\n' '[4/7] SFU syntax'
if command -v node >/dev/null 2>&1; then
  check 'media-sfu server syntax' node --check media-sfu/server.js
else
  skip 'media-sfu syntax' 'Node.js is unavailable'
fi

printf '%s\n' '[5/7] Backend unit tests'
if command -v java >/dev/null 2>&1; then
  check 'Gradle backend tests' bash -c 'cd backend-server && ./gradlew test --no-daemon'
else
  skip 'Gradle backend tests' 'JDK 21 is unavailable'
fi

printf '%s\n' '[6/7] Shell syntax'
while IFS= read -r script; do
  check "bash -n $script" bash -n "$script"
done < <(find scripts -maxdepth 1 -type f -name '*.sh' -print | sort)

printf '%s\n' '[7/7] Mock API smoke test'
check 'health + inventory + user operations' bash -c '
  python3 scripts/mock_backend.py >/tmp/red-mock-smoke.log 2>&1 &
  pid=$!
  trap "kill $pid 2>/dev/null || true; wait $pid 2>/dev/null || true" EXIT
  sleep 1.2
  curl -sf http://127.0.0.1:8080/health >/dev/null
  curl -sf http://127.0.0.1:8080/api/admin/dinstar/inventory | grep -q portIndex
  curl -sf http://127.0.0.1:8080/api/admin/users/user_uuid_001/overview | grep -q messages24h
  test "$(curl -sfo /dev/null -w "%{http_code}" -X POST http://127.0.0.1:8080/api/admin/users/user_uuid_001/temporary-password)" = 204
'

printf '\n════════════════════════════════════════════════\n'
printf '  Result: %d passed | %d failed | %d skipped\n' "$PASS" "$FAIL" "$SKIP"
printf '%s\n' '════════════════════════════════════════════════'
[[ "$FAIL" -eq 0 ]]
