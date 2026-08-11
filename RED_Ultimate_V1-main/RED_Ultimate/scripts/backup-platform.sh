#!/usr/bin/env bash
# YOUNES platform backup — Docker Compose production operator tool.
# Creates a CHECKSUMMED, encrypted archive of PostgreSQL, MongoDB, Redis,
# MinIO media and identity-authority keys. It is intentionally host-operated:
# the application backend never receives Docker socket access.
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
OUT_DIR="${BACKUP_DIR:-$ROOT/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/younes-backup.${STAMP}.XXXXXX")"
ARCHIVE="$OUT_DIR/younes-platform-${STAMP}.tar.gz"

cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT
fail() { echo "[backup] ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"; }

need docker; need tar; need sha256sum
[[ -f "$ENV_FILE" ]] || fail "missing environment file: $ENV_FILE"
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
[[ -n "${MONGO_PASSWORD:-}" && -n "${REDIS_PASSWORD:-}" ]] || fail "MONGO_PASSWORD and REDIS_PASSWORD are required"
[[ -n "${BACKUP_GPG_RECIPIENT:-}" ]] || fail "set BACKUP_GPG_RECIPIENT to an imported GPG encryption recipient; plaintext backups are forbidden"
need gpg

docker compose ps --status running --services | grep -qx backend || fail "backend is not running; start the production Compose stack first"
mkdir -p "$OUT_DIR" "$WORK"/{postgres,mongo,redis,minio,identity,metadata}

echo "[backup] PostgreSQL logical dump"
docker exec red-db-sql pg_dump --format=custom --no-owner --no-privileges -U admin red_sovereign > "$WORK/postgres/red_sovereign.dump"

echo "[backup] MongoDB dump"
docker exec red-db-nosql mongodump --archive --gzip --username red_user --password "$MONGO_PASSWORD" --authenticationDatabase admin --db red_sovereign > "$WORK/mongo/red_sovereign.archive.gz"

echo "[backup] Redis RDB snapshot"
docker exec red-cache redis-cli --no-auth-warning -a "$REDIS_PASSWORD" --rdb /tmp/younes-redis.rdb >/dev/null
docker cp red-cache:/tmp/younes-redis.rdb "$WORK/redis/redis.rdb"
docker exec red-cache rm -f /tmp/younes-redis.rdb

echo "[backup] MinIO object snapshot"
docker cp red-storage:/data/. "$WORK/minio/"

echo "[backup] Identity authority keys"
docker cp red-backend:/run/secrets/. "$WORK/identity/"
chmod -R go-rwx "$WORK/identity"

cat > "$WORK/metadata/manifest.env" <<EOF
FORMAT_VERSION=1
CREATED_AT_UTC=$STAMP
POSTGRES_FORMAT=pg_dump-custom
MONGODB_FORMAT=mongodump-archive-gzip
REDIS_FORMAT=rdb
MINIO_FORMAT=container-data-snapshot
IDENTITY_KEYS_INCLUDED=true
CONSISTENCY=online-best-effort
EOF
(
  cd "$WORK"
  find postgres mongo redis minio identity metadata -type f -print0 | sort -z | xargs -0 sha256sum > metadata/SHA256SUMS
)

echo "[backup] encrypting archive for configured GPG recipient"
tar -C "$WORK" -czf - . | gpg --batch --yes --trust-model always --encrypt --recipient "$BACKUP_GPG_RECIPIENT" --output "${ARCHIVE}.gpg"
sha256sum "${ARCHIVE}.gpg" > "${ARCHIVE}.gpg.sha256"
chmod 600 "${ARCHIVE}.gpg" "${ARCHIVE}.gpg.sha256"

echo "[backup] complete: ${ARCHIVE}.gpg"
echo "[backup] checksum: ${ARCHIVE}.gpg.sha256"
