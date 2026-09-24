#!/usr/bin/env bash
# Verify Flyway's fresh-install selection and SQL against disposable PostgreSQL 17.
# B64 must be selected instead of the broken historical V1..V64 upgrade chain.
# This does NOT validate upgrading existing databases or their applied checksums.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/resources/db/migration"
: "${PGPASSWORD:?Set the disposable CI database password}"
: "${PGDATABASE:?Set the disposable CI database name}"
: "${PGUSER:?Set the disposable CI database user}"
python3 "$ROOT/scripts/generate-flyway-baseline.py" --check

flyway() {
  docker run --rm --network host \
    -e FLYWAY_URL="jdbc:postgresql://127.0.0.1:5432/${PGDATABASE}" \
    -e FLYWAY_USER="$PGUSER" -e FLYWAY_PASSWORD="$PGPASSWORD" \
    -e FLYWAY_VALIDATE_ON_MIGRATE=true \
    -v "$MIGRATIONS:/flyway/sql:ro" flyway/flyway:11-alpine "$@"
}
psql_ci() {
  docker run --rm --network host \
    -e PGPASSWORD -e PGDATABASE -e PGUSER postgres:17-alpine \
    psql -X -h 127.0.0.1 -v ON_ERROR_STOP=1 "$@"
}

flyway migrate
# Check the single migration Flyway ran, not merely whether psql accepts SQL.
version=$(psql_ci -tAc "SELECT COUNT(*) FROM flyway_schema_history WHERE version='64' AND script='B64__Sovereign_Fresh_Install.sql' AND success")
if [ "$version" != 1 ]; then
  echo "FAIL: Flyway did not record B64 as the sole fresh-install migration" >&2
  exit 1
fi
applied=$(psql_ci -tAc "SELECT COUNT(*) FROM flyway_schema_history WHERE success")
if [ "$applied" != 1 ]; then
  echo "FAIL: fresh install unexpectedly ran historical migrations ($applied recorded)" >&2
  exit 1
fi
count=$(psql_ci -tAc "SELECT COUNT(*) FROM pg_tables WHERE schemaname='public' AND tablename != 'flyway_schema_history'")
if [ "$count" -lt 60 ]; then
  echo "FAIL: unexpectedly few application tables after baseline ($count)" >&2
  exit 1
fi
retired=$(psql_ci -tAc "SELECT COUNT(*) FROM pg_tables WHERE schemaname='public' AND (tablename ILIKE '%pstn%' OR tablename ILIKE '%dinstar%')")
if [ "$retired" != 0 ]; then
  echo "FAIL: cancelled PSTN/Dinstar tables left in fresh schema ($retired)" >&2
  exit 1
fi
flyway migrate  # idempotent and checksum-valid for the fresh installation
printf 'PASS: Flyway B64 installs %s application tables on PostgreSQL 17; no retired PSTN tables; second migrate validates\n' "$count"
echo 'NOTE: This is a fresh-install test; existing history and partial upgrades require separate evidence.'
