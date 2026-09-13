#!/usr/bin/env bash
# YOUNES platform restore — deliberately guarded operator tool.
# Default mode verifies and extracts only. Apply requires explicit acknowledgement.
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
ARCHIVE="${1:-}"
MODE="${2:---verify-only}"
[[ -n "$ARCHIVE" && -f "$ARCHIVE" ]] || { echo "Usage: $0 /secure/path/younes-platform-*.tar.gz.gpg [--apply]" >&2; exit 2; }
[[ "$MODE" == "--verify-only" || "$MODE" == "--apply" ]] || { echo "Unknown mode: $MODE" >&2; exit 2; }
command -v gpg >/dev/null || { echo "gpg is required" >&2; exit 1; }
command -v docker >/dev/null || { echo "docker is required" >&2; exit 1; }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/younes-restore.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
gpg --decrypt "$ARCHIVE" | tar -C "$WORK" -xzf -
(cd "$WORK" && sha256sum -c metadata/SHA256SUMS)
echo "[restore] archive checksum manifest verified"
cat "$WORK/metadata/manifest.env"

if [[ "$MODE" == "--verify-only" ]]; then
  echo "[restore] verification only: no production data was changed"
  exit 0
fi

[[ "${I_UNDERSTAND_THIS_DESTROYS_CURRENT_DATA:-}" == "RESTORE_YOUNES_PLATFORM" ]] || {
  echo "Refusing apply. Set I_UNDERSTAND_THIS_DESTROYS_CURRENT_DATA=RESTORE_YOUNES_PLATFORM after a successful isolated restore drill." >&2
  exit 1
}
[[ -f .env ]] || { echo "missing .env" >&2; exit 1; }
# shellcheck disable=SC1091
set -a; source .env; set +a

echo "[restore] stopping application writers"
docker compose stop backend media-sfu pstn-gateway admin

echo "[restore] restoring PostgreSQL"
docker exec -i red-db-sql pg_restore --clean --if-exists --no-owner --no-privileges -U admin -d red_sovereign < "$WORK/postgres/red_sovereign.dump"

echo "[restore] restoring MongoDB"
cat "$WORK/mongo/red_sovereign.archive.gz" | docker exec -i red-db-nosql mongorestore --drop --archive --gzip --username red_user --password "$MONGO_PASSWORD" --authenticationDatabase admin

echo "[restore] restoring Redis"
docker cp "$WORK/redis/redis.rdb" red-cache:/data/dump.rdb

echo "[restore] restoring MinIO and identity volumes"
docker cp "$WORK/minio/." red-storage:/data/
docker cp "$WORK/identity/." red-backend:/run/secrets/

echo "[restore] starting services; inspect health before accepting traffic"
docker compose start backend media-sfu pstn-gateway admin
