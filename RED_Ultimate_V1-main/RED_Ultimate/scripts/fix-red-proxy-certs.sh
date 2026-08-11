#!/usr/bin/env sh
# Revalidate/repair the Compose-managed TLS volume, then restart Nginx safely.
# The serving container mounts the private key read-only; only certs-init writes it.
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || fail 'Docker is required'
docker compose version >/dev/null 2>&1 || fail 'Docker Compose v2 is required'
docker info >/dev/null 2>&1 || fail 'Docker daemon is not running'
[ -f "$ENV_FILE" ] || fail "Missing $ENV_FILE. Run ./scripts/local-first-run.sh first."

cd "$ROOT"
printf '%s\n' '═══════════════════════════════════════════════════════════════════════'
printf '%s\n' ' YOUNES TLS volume validation and repair'
printf '%s\n' '═══════════════════════════════════════════════════════════════════════'

docker compose --env-file "$ENV_FILE" config --quiet

# Stop the reader before an atomic replacement. We do not delete the named
# volume: certs-init validates and preserves a healthy production certificate,
# and replaces only an invalid/expired/mismatched pair.
docker compose --env-file "$ENV_FILE" stop nginx >/dev/null 2>&1 || true
docker compose --env-file "$ENV_FILE" rm -f certs-init >/dev/null 2>&1 || true
printf '%s\n' '[tls] validating the persistent certificate pair...'
docker compose --env-file "$ENV_FILE" run --rm certs-init

printf '%s\n' '[nginx] starting proxy and required dependencies...'
docker compose --env-file "$ENV_FILE" up -d nginx

container_id="$(docker compose --env-file "$ENV_FILE" ps -q nginx)"
[ -n "$container_id" ] || fail 'Nginx container was not created'

attempt=0
while [ "$attempt" -lt 30 ]; do
  state="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"
  case "$state" in
    healthy)
      docker compose --env-file "$ENV_FILE" ps nginx
      printf '%s\n' 'TLS repair and Nginx readiness: PASS'
      exit 0
      ;;
    unhealthy|exited|dead)
      docker compose --env-file "$ENV_FILE" logs --tail=100 nginx >&2 || true
      fail "Nginx failed readiness: $state"
      ;;
  esac
  attempt=$((attempt + 1))
  sleep 2
done

docker compose --env-file "$ENV_FILE" logs --tail=100 nginx >&2 || true
fail 'Nginx did not become healthy within 60 seconds'
