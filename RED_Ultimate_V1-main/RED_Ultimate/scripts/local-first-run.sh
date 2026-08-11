#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
REPO_ROOT="$(dirname "$ROOT")"
ENV_FILE="$ROOT/.env"
SERVER_IP=""
BUILD_ANDROID="${BUILD_ANDROID:-0}"
HTTP_PORT="${RED_HTTP_PORT:-}"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "$1 is required"; }
usage() {
  printf 'Usage: %s [IPv4 | --server-ip IPv4] [--build-android]\n' "$0"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --server-ip)
      [ "$#" -ge 2 ] || fail '--server-ip requires an IPv4 value'
      SERVER_IP="$2"; shift 2
      ;;
    --build-android)
      BUILD_ANDROID=1; shift
      ;;
    -h|--help)
      usage; exit 0
      ;;
    -* )
      fail "Unknown option: $1"
      ;;
    *)
      [ -z "$SERVER_IP" ] || fail 'Server IP was supplied more than once'
      SERVER_IP="$1"; shift
      ;;
  esac
done

if [ -z "$HTTP_PORT" ] && [ -f "$ENV_FILE" ]; then
  HTTP_PORT="$(sed -n 's/^RED_HTTP_PORT=//p' "$ENV_FILE" | tail -n 1)"
fi
HTTP_PORT="${HTTP_PORT:-8088}"
wait_container_ready() {
  name="$1"; attempt=0
  while [ "$attempt" -lt 30 ]; do
    state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name" 2>/dev/null || true)"
    runtime="${state%%|*}"; health="${state#*|}"
    if [ "$runtime" = running ] && { [ "$health" = healthy ] || [ "$health" = none ]; }; then
      printf '%s readiness: PASS (%s/%s)\n' "$name" "$runtime" "$health"; return 0
    fi
    case "$runtime" in exited|dead|restarting) docker logs --tail 80 "$name" >&2 || true; fail "$name failed readiness: $runtime/$health" ;; esac
    attempt=$((attempt + 1)); sleep 3
  done
  docker inspect "$name" --format '{{json .State}}' >&2 || true
  fail "$name did not become ready"
}

if [ -z "$SERVER_IP" ]; then
  SERVER_IP="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
fi
[ -n "$SERVER_IP" ] || fail "Pass the local server IPv4 address: ./scripts/local-first-run.sh 192.168.1.50"
printf '%s\n' "$SERVER_IP" | awk -F. '
  NF != 4 { exit 1 }
  { for (i=1; i<=4; i++) if ($i !~ /^[0-9]+$/ || $i < 0 || $i > 255) exit 1 }
' || fail "Server IP must be a valid IPv4 address"

need docker
need openssl
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is required (docker compose)"
docker info >/dev/null 2>&1 || fail "Docker daemon is not running"

if [ ! -f "$ENV_FILE" ]; then
  rand_hex() { openssl rand -hex "$1"; }
  umask 077
  sed \
    -e "s|replace_with_a_long_random_database_password|$(rand_hex 32)|" \
    -e "s|replace_with_a_long_random_mongodb_password|$(rand_hex 32)|" \
    -e "s|replace_with_a_long_random_minio_password|$(rand_hex 32)|" \
    -e "s|replace_with_a_long_random_redis_password|$(rand_hex 32)|" \
    -e "s|replace_with_a_long_random_asterisk_password|$(rand_hex 32)|" \
    -e "s|replace_with_a_long_random_turn_secret|$(rand_hex 32)|" \
    -e "s|replace_with_at_least_32_random_characters|$(rand_hex 48)|" \
    -e "s|replace_with_at_least_14_random_characters|$(rand_hex 20)|" \
    -e "s|replace_with_the_gateway_password|$(rand_hex 24)|" \
    -e "s|192\.168\.1\.50|$SERVER_IP|g" \
    "$ROOT/.env.example" > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  printf 'Created private local configuration: %s\n' "$ENV_FILE"
else
  printf 'Using existing %s (secrets are not overwritten).\n' "$ENV_FILE"
fi

case "$HTTP_PORT" in *[!0-9]*|'') fail "RED_HTTP_PORT must be numeric" ;; esac
[ "$HTTP_PORT" -ge 1024 ] && [ "$HTTP_PORT" -le 65535 ] || fail "RED_HTTP_PORT must be between 1024 and 65535"
existing_origins="$(sed -n 's/^ALLOWED_ORIGINS=//p' "$ENV_FILE" | tail -n 1)"
required_origins="http://localhost:$HTTP_PORT,http://127.0.0.1:$HTTP_PORT,http://$SERVER_IP:$HTTP_PORT"
all_origins="$(printf '%s\n' "${existing_origins}${existing_origins:+,}${required_origins}" | awk -F, '
  {
    out=""
    for (i=1; i<=NF; i++) if ($i != "" && !seen[$i]++) out=out (out ? "," : "") $i
    print out
  }
')"
tmp_env="$ENV_FILE.tmp"
trap 'rm -f "$tmp_env"' EXIT INT TERM
grep -v -E '^(RED_HTTP_PORT|TLS_SAN_IP|ALLOWED_ORIGINS)=' "$ENV_FILE" > "$tmp_env"
printf 'RED_HTTP_PORT=%s\nTLS_SAN_IP=%s\nALLOWED_ORIGINS=%s\n' "$HTTP_PORT" "$SERVER_IP" "$all_origins" >> "$tmp_env"
mv "$tmp_env" "$ENV_FILE"
trap - EXIT INT TERM
chmod 600 "$ENV_FILE"
printf 'Local HTTP endpoint: http://%s:%s\n' "$SERVER_IP" "$HTTP_PORT"

if [ ! -f "$ROOT/secrets/red_identity_private_key.pem" ]; then
  "$ROOT/scripts/generate-local-identity-authority.sh"
else
  printf 'Using existing RED identity authority keys (not overwritten).\n'
fi

cd "$ROOT"
docker compose --env-file "$ENV_FILE" config --quiet
printf 'Docker Compose configuration: PASS\n'

docker compose --env-file "$ENV_FILE" build
docker compose --env-file "$ENV_FILE" up -d
# Nginx 1.27 resolves upstream replacements through Docker DNS dynamically.
sleep 3

printf 'Waiting for RED backend health'
i=0
until curl -fsS "http://127.0.0.1:$HTTP_PORT/health" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    printf '\nBackend did not become healthy. Recent logs:\n' >&2
    docker compose --env-file "$ENV_FILE" ps >&2
    docker compose --env-file "$ENV_FILE" logs --tail=120 backend >&2
    exit 1
  fi
  printf '.'
  sleep 3
done
printf ' PASS\n'

printf 'Waiting for SFU'
i=0
until curl -fsS "http://127.0.0.1:$HTTP_PORT/sfu-health" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    printf '\nSFU did not become healthy. Recent logs:\n' >&2
    docker logs --tail 100 red-media-sfu >&2 || true
    exit 1
  fi
  printf '.'; sleep 3
done
printf ' PASS\n'
wait_container_ready red-admin-ui
wait_container_ready red-pstn-gateway

if [ "$BUILD_ANDROID" = "1" ]; then
  printf 'Building verified backend + Android artifact image (this downloads the Android SDK image)...\n'
  cd "$REPO_ROOT"
  mkdir -p "$REPO_ROOT/local-artifacts"
  TLS_PINS_VALUE="${RED_TLS_PINS:-$(sed -n 's/^RED_TLS_PINS=//p' "$ENV_FILE" | tail -n 1)}"
  docker build --file Dockerfile --target android-artifact \
    --build-arg "RED_SERVER_URL=http://$SERVER_IP:$HTTP_PORT" \
    --build-arg "RED_TLS_PINS=$TLS_PINS_VALUE" \
    --output "type=local,dest=$REPO_ROOT/local-artifacts" .
  [ -f "$REPO_ROOT/local-artifacts/red-app-debug.apk" ] || fail "Android build finished without an APK"
  printf 'Verified APK saved under %s/local-artifacts/red-app-debug.apk\n' "$REPO_ROOT"
fi

printf '\nRED local first run is ready.\n'
printf 'Admin dashboard: http://%s:%s/\n' "$SERVER_IP" "$HTTP_PORT"
printf 'Health:          http://%s:%s/health\n' "$SERVER_IP" "$HTTP_PORT"
printf 'The generated admin password remains only in RED_Ultimate/.env.\n'
printf 'Install local-artifacts/red-app-debug.apk only when BUILD_ANDROID=1 was used.\n'
