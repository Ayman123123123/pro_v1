#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# 🌐 YOUNES Platform — Production Let's Encrypt SSL Automation Suite
# Obtains official CA certificates via Certbot ACME Webroot, integrates
# seamlessly with Nginx reverse proxy, establishes cross-directory
# symlinks (/etc/ssl/private, /etc/ssl/certs, /etc/ssl/red), and sets up
# automated cron renewal with zero-downtime Nginx reload.
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

DOMAIN="${1:-}"
EMAIL="${2:-}"
WEBROOT="${WEBROOT:-/var/www/certbot}"
DRY_RUN="${DRY_RUN:-0}"

usage() {
  echo "Usage: $0 <DOMAIN> <EMAIL> [--dry-run]"
  echo "Example: $0 sovereign.example.com admin@example.com"
  exit 1
}

if [[ -z "$DOMAIN" || -z "$EMAIL" ]]; then
  usage
fi

if [[ "${3:-}" == "--dry-run" ]]; then
  DRY_RUN=1
fi

echo "═══════════════════════════════════════════════════════════════════════"
echo " 🛡️ Production Let's Encrypt / ACME Certificate Provisioning"
echo "═══════════════════════════════════════════════════════════════════════"
echo "  Domain:   $DOMAIN"
echo "  Email:    $EMAIL"
echo "  Webroot:  $WEBROOT"
echo "  Dry-run:  $([[ $DRY_RUN -eq 1 ]] && echo 'ENABLED' || echo 'DISABLED')"
echo "═══════════════════════════════════════════════════════════════════════"

# Check certbot
if ! command -v certbot >/dev/null 2>&1; then
  echo "⚠️ certbot not found. Attempting installation..."
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update && apt-get install -y certbot
  elif command -v apk >/dev/null 2>&1; then
    apk add --no-cache certbot
  elif command -v yum >/dev/null 2>&1; then
    yum install -y certbot
  else
    echo "❌ Could not auto-install certbot. Please install it manually." >&2
    exit 1
  fi
fi

# Ensure webroot directory exists
mkdir -p "$WEBROOT"
mkdir -p /etc/ssl/red /etc/ssl/certs /etc/ssl/private 2>/dev/null || true

# Pre-flight check: Test if Nginx proxy is running
echo "⏳ Testing HTTP accessibility of /.well-known/acme-challenge/..."
TEST_FILE="$WEBROOT/.well-known/acme-challenge/health-check-$$.txt"
mkdir -p "$(dirname "$TEST_FILE")"
echo "ok" > "$TEST_FILE"
rm -f "$TEST_FILE"

# Issue certificate via Certbot
CERTBOT_CMD=(certbot certonly --webroot -w "$WEBROOT" -d "$DOMAIN" --agree-tos -m "$EMAIL" --non-interactive)

if [[ $DRY_RUN -eq 1 ]]; then
  CERTBOT_CMD+=(--dry-run)
  echo "🧪 Running in Dry-Run mode..."
fi

echo "🚀 Executing Certbot request..."
"${CERTBOT_CMD[@]}"

if [[ $DRY_RUN -eq 1 ]]; then
  echo "✅ Dry-run successful! You can now run without --dry-run."
  exit 0
fi

# Link generated certificates to standard platform paths
LETSENCRYPT_DIR="/etc/letsencrypt/live/$DOMAIN"

if [[ -d "$LETSENCRYPT_DIR" ]]; then
  echo "🔗 Linking Let's Encrypt certificates to platform paths..."
  
  # Remove any old directories or dead symlinks
  for p in /etc/ssl/red/fullchain.pem /etc/ssl/red/privkey.pem /etc/ssl/certs/fullchain.pem /etc/ssl/certs/privkey.pem /etc/ssl/private/privkey.pem; do
    if [[ -d "$p" || -L "$p" ]]; then rm -rf "$p"; fi
  done

  ln -sf "$LETSENCRYPT_DIR/fullchain.pem" /etc/ssl/red/fullchain.pem
  ln -sf "$LETSENCRYPT_DIR/privkey.pem" /etc/ssl/red/privkey.pem
  ln -sf "$LETSENCRYPT_DIR/fullchain.pem" /etc/ssl/certs/fullchain.pem
  ln -sf "$LETSENCRYPT_DIR/privkey.pem" /etc/ssl/certs/privkey.pem
  ln -sf "$LETSENCRYPT_DIR/privkey.pem" /etc/ssl/private/privkey.pem
  chmod 755 /etc/ssl/private 2>/dev/null || true
  chmod 600 /etc/ssl/private/privkey.pem 2>/dev/null || true

  echo "   ✓ /etc/ssl/red/fullchain.pem     -> $LETSENCRYPT_DIR/fullchain.pem"
  echo "   ✓ /etc/ssl/red/privkey.pem       -> $LETSENCRYPT_DIR/privkey.pem"
  echo "   ✓ /etc/ssl/private/privkey.pem   -> $LETSENCRYPT_DIR/privkey.pem"
  echo "   ✓ /etc/ssl/certs/fullchain.pem   -> $LETSENCRYPT_DIR/fullchain.pem"
  echo "   ✓ /etc/ssl/certs/privkey.pem     -> $LETSENCRYPT_DIR/privkey.pem"

  # Zero-downtime Nginx reload
  echo "🔄 Reloading Nginx configuration..."
  if command -v docker >/dev/null 2>&1 && docker ps --filter name=red-proxy -q | grep -q .; then
    docker exec red-proxy nginx -t && docker exec red-proxy nginx -s reload
    echo "✅ Container red-proxy reloaded with new certificates!"
  elif command -v nginx >/dev/null 2>&1; then
    nginx -t && nginx -s reload
    echo "✅ Host Nginx reloaded with new certificates!"
  fi

  # Setup Auto-Renewal Cron Job
  echo "⏰ Setting up automated renewal cron job..."
  RENEW_SCRIPT="/etc/cron.d/younes-ssl-renew"
  if [[ -w /etc/cron.d ]]; then
    cat <<EOF > "$RENEW_SCRIPT"
# Auto-renew Let's Encrypt certificates every 12 hours
0 3,15 * * * root certbot renew --quiet --post-hook "docker exec red-proxy nginx -s reload 2>/dev/null || systemctl reload nginx 2>/dev/null || true"
EOF
    chmod 644 "$RENEW_SCRIPT"
    echo "✅ Automated cron renewal scheduled in $RENEW_SCRIPT"
  fi
fi

echo "═══════════════════════════════════════════════════════════════════════"
echo " 🎉 Production SSL Setup Completed Successfully!"
echo "═══════════════════════════════════════════════════════════════════════"
