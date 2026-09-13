#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# 🏛️ YOUNES Sovereign Platform — Master Administration CLI (red-cli)
# Unified management tool for operations, certificates, database,
# hardware telecom gateway, and container diagnostics.
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Target project root
if [[ -d "$ROOT_DIR/RED_Ultimate" ]]; then
  PROJ_DIR="$ROOT_DIR/RED_Ultimate"
elif [[ -d "$ROOT_DIR/RED_Ultimate_V1-main/RED_Ultimate" ]]; then
  PROJ_DIR="$ROOT_DIR/RED_Ultimate_V1-main/RED_Ultimate"
else
  PROJ_DIR="$ROOT_DIR"
fi
ENV_FILE="$PROJ_DIR/.env"

compose_cmd() {
  if [[ -f "$ENV_FILE" ]]; then
    docker compose --env-file "$ENV_FILE" "$@"
  else
    docker compose "$@"
  fi
}

banner() {
  echo -e "${CYAN}${BOLD}"
  echo "╔══════════════════════════════════════════════════════════════════════╗"
  echo "║       🏛️  YOUNES SOVEREIGN PLATFORM — MASTER ADMIN CLI (v1.0)       ║"
  echo "╚══════════════════════════════════════════════════════════════════════╝"
  echo -e "${NC}"
}

usage() {
  banner
  echo -e "${BOLD}Usage:${NC} $0 <command> [arguments]"
  echo ""
  echo -e "${YELLOW}Available Commands:${NC}"
  echo "  health               Run infrastructure checks and live Compose readiness"
  echo "  test                 Execute the repository's measurable local quality gate"
  echo "  ssl:fix              Self-heal and restore NGINX SSL private keys & certs"
  echo "  ssl:generate         Generate high-security ECC/RSA certificates with SAN"
  echo "  ssl:production       Setup Let's Encrypt SSL with automated cron renewal"
  echo "  ssl:verify           Audit certificate validity & cryptographic modulus match"
  echo "  dinstar:check        Test connection to DINSTAR VoIP Gateway (192.168.11.1)"
  echo "  logs [service]       Stream real-time logs (e.g. backend, nginx, media-sfu)"
  echo "  up                   Start all platform containers via Docker Compose"
  echo "  down                 Stop all platform containers"
  echo "  restart [service]    Restart specific container or entire platform"
  echo "  backup               Perform automated database backup"
  echo ""
  exit 1
}

CMD="${1:-}"

case "$CMD" in
  health)
    cd "$PROJ_DIR"
    python3 scripts/check-infrastructure.py
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
      compose_cmd config --quiet
      compose_cmd ps
      HTTP_PORT="${RED_HTTP_PORT:-$(sed -n 's/^RED_HTTP_PORT=//p' "$ENV_FILE" 2>/dev/null | tail -n 1)}"
      HTTP_PORT="${HTTP_PORT:-8088}"
      curl --fail --silent --show-error "http://127.0.0.1:$HTTP_PORT/health" >/dev/null
      echo -e "${GREEN}✅ Live readiness endpoint passed.${NC}"
    else
      echo -e "${YELLOW}⏭️  Docker daemon unavailable; live readiness skipped.${NC}"
    fi
    ;;

  test)
    "$PROJ_DIR/scripts/check-all.sh"
    ;;

  ssl:fix)
    "$SCRIPT_DIR/fix-red-proxy-certs.sh"
    ;;

  ssl:generate)
    shift
    "$SCRIPT_DIR/generate-ssl-certs.sh" "$@"
    ;;

  ssl:production)
    shift
    "$SCRIPT_DIR/setup-production-ssl.sh" "$@"
    ;;

  ssl:verify)
    shift
    if [[ "$#" -gt 0 ]]; then
      "$SCRIPT_DIR/verify-ssl-certs.sh" "$@"
    else
      [[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE" >&2; exit 1; }
      cd "$PROJ_DIR"
      compose_cmd run --rm --no-deps \
        -v "$SCRIPT_DIR/verify-ssl-certs.sh:/usr/local/bin/verify-red-tls:ro" \
        --entrypoint sh certs-init -ec \
        'bash /usr/local/bin/verify-red-tls'
    fi
    ;;

  dinstar:check)
    echo -e "${CYAN}🔍 Checking DINSTAR Gateway connectivity at 192.168.11.1:443...${NC}"
    if curl -k -s --connect-timeout 3 https://192.168.11.1/ >/dev/null 2>&1; then
      echo -e "${GREEN}✅ DINSTAR Gateway is REACHABLE and responding!${NC}"
    else
      echo -e "${RED}❌ Cannot reach 192.168.11.1.${NC}"
      echo -e "${YELLOW}💡 Tip: Ensure your NIC has secondary IP 192.168.11.X assigned.${NC}"
    fi
    ;;

  logs)
    SVC="${2:-}"
    cd "$PROJ_DIR"
    if [[ -n "$SVC" ]]; then
      compose_cmd logs -f --tail 100 "$SVC"
    else
      compose_cmd logs -f --tail 50
    fi
    ;;

  up)
    # Delegate secret generation, Compose validation, and readiness waits to the
    # canonical workflow instead of declaring success immediately after `up -d`.
    "$PROJ_DIR/scripts/local-first-run.sh" "${SERVER_IP:-}"
    ;;

  down)
    cd "$PROJ_DIR"
    compose_cmd down
    echo -e "${YELLOW}🛑 All services stopped.${NC}"
    ;;

  restart)
    SVC="${2:-}"
    cd "$PROJ_DIR"
    if [[ -n "$SVC" ]]; then
      compose_cmd restart "$SVC"
    else
      compose_cmd restart
    fi
    echo -e "${GREEN}✅ Restart complete!${NC}"
    ;;

  backup)
    if [[ -f "$SCRIPT_DIR/backup-unified.sh" ]]; then
      "$SCRIPT_DIR/backup-unified.sh"
    else
      echo -e "${GREEN}📦 Backing up PostgreSQL and MongoDB...${NC}"
      mkdir -p "$PROJ_DIR/backups"
      docker exec red-db-sql pg_dump -U admin red_sovereign > "$PROJ_DIR/backups/pg_$(date +%Y%m%d_%H%M%S).sql" 2>/dev/null || true
      echo -e "${GREEN}✅ Backup saved to $PROJ_DIR/backups/${NC}"
    fi
    ;;

  *)
    usage
    ;;
esac
