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

printf '%s\n' '[1/8] Infrastructure invariants'
check 'BuildKit / Compose / Nginx / readiness / Flyway' python3 scripts/check-infrastructure.py

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  check 'docker compose config' docker compose --env-file .env.example config --quiet
else
  skip 'docker compose config' 'Docker Compose is unavailable on this host'
fi

printf '%s\n' '[2/8] Database/entity and Kotlin static contracts'
# Flyway naming/duplication gate runs first and needs no DB: a duplicate version
# aborts startup entirely, so catching it here is cheaper than a failed boot.
check 'Flyway migration naming' bash scripts/check-flyway-migrations.sh
check 'schema consistency' python3 scripts/check-schema-consistency.py
# Complements the textual entity/migration diff above by making PostgreSQL itself
# plan every hand-written JdbcTemplate statement. Skips itself when the DB
# container is not running, so it is safe to call unconditionally.
check 'hand-written SQL vs live schema' python3 scripts/check-sql-prepare.py
check 'version-catalog accessors' python3 scripts/check-catalog-accessors.py
check 'Android integrity' python3 scripts/check-android-integrity.py
check 'Kotlin static checks' python3 scripts/check-kotlin-static.py

printf '%s\n' '[3/8] Admin dashboard contracts and production build'
if command -v npm >/dev/null 2>&1 && [[ -d admin_dashboard/node_modules ]]; then
  check 'admin API/guard/role/type checks' bash -c 'cd admin_dashboard && npm run check'
  check 'admin production build' bash -c 'cd admin_dashboard && npm run build'
else
  skip 'admin checks' 'run npm ci in admin_dashboard first'
fi

printf '%s\n' '[4/8] SFU syntax'
if command -v node >/dev/null 2>&1; then
  check 'media-sfu server syntax' node --check media-sfu/server.js
else
  skip 'media-sfu syntax' 'Node.js is unavailable'
fi

printf '%s\n' '[5/8] Legendary Trap Guards — 5 traps from #26 (Sovereign hardening)'
check 'Trap 1: Android Home (prefs conflict)' bash -c 'grep -q "Android prefs resolved to" settings.gradle.kts && grep -q "ANDROID_SDK_HOME" settings.gradle.kts && echo "Android Home Trap hardened"'
check 'Trap 2: Sovereign Signal (local-maven chain)' bash -c 'grep -q "Sovereign Signal Artifact" settings.gradle.kts && grep -q "local-maven" settings.gradle.kts && echo "Signal chain OK"'
check 'Trap 3: Double @Composable' bash scripts/check-double-composable.sh
check 'Trap 4: Network Security Lock (TLS-only)' bash scripts/check-network-security.sh
check 'Trap 5: Icon Integrity (SVG merger)' bash scripts/check-icon-integrity.sh

printf '%s\n' '[6/8] Backend unit tests'
if command -v java >/dev/null 2>&1; then
  check 'Gradle backend tests' bash -c 'cd backend-server && ./gradlew test --no-daemon'
else
  skip 'Gradle backend tests' 'JDK 21 is unavailable'
fi

printf '%s\n' '[7/8] Shell syntax'
while IFS= read -r script; do
  check "bash -n $script" bash -n "$script"
done < <(find scripts -maxdepth 1 -type f -name '*.sh' -print | sort)

printf '%s\n' '[8/8] Live Compose stack (real Kotlin API on 8088)'
if curl -fsS --max-time 3 http://127.0.0.1:8088/health/live >/dev/null 2>&1; then
  check 'live /health/live is YOUNES' bash -c 'curl -fsS http://127.0.0.1:8088/health/live | grep -q YOUNES'
  check 'live /health reports bindings' bash -c 'curl -fsS http://127.0.0.1:8088/health | grep -q mongodbHost'
else
  skip 'live Compose /health' 'stack is not up — run scripts/compose-recover.ps1'
fi

printf '\n════════════════════════════════════════════════\n'
printf '  Result: %d passed | %d failed | %d skipped\n' "$PASS" "$FAIL" "$SKIP"
printf '%s\n' '════════════════════════════════════════════════'
[[ "$FAIL" -eq 0 ]]
