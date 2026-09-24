#!/usr/bin/env bash
# Exercise the committed Flyway SQL against an *empty* disposable PostgreSQL.
# This validates SQL/dependency order, not applied-checksum compatibility or
# upgraded databases. Never point this command at production: use the CI DB.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS="$ROOT/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/resources/db/migration"
: "${PGPASSWORD:?Set the disposable CI database password}"
: "${PGDATABASE:?Set the disposable CI database name}"
: "${PGUSER:?Set the disposable CI database user}"
ordered=$(python3 "$ROOT/scripts/list_flyway_migrations.py" "$MIGRATIONS")
mapfile -t files <<< "$ordered"
docker run --rm --network host \
  -e PGPASSWORD -e PGDATABASE -e PGUSER \
  -v "$MIGRATIONS:/migrations:ro" postgres:17-alpine \
  sh -eu -c '
    for migration do
      printf "Applying %s\n" "$migration"
      psql -X -q -h 127.0.0.1 -v ON_ERROR_STOP=1 -f "/migrations/$migration" >/dev/null
    done
  ' -- "${files[@]}"
echo "PASS: ${#files[@]} versioned SQL migrations apply on empty PostgreSQL 17"
