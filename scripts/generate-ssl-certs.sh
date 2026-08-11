#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# 🔐 YOUNES Platform — Professional TLS Certificate Generator
# Generates high-security TLS certificates & private keys with full
# Subject Alternative Name (SAN) support, ECC/RSA algorithms, atomic
# file creation, and universal path compatibility (/etc/ssl/private,
# /etc/ssl/certs, /etc/ssl/red).
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

# ─── Default Parameters ───
DOMAIN="${DOMAIN:-red.local}"
DAYS="${DAYS:-3650}"
ALGO="${ALGO:-rsa}" # rsa or ecc
KEY_SIZE="${KEY_SIZE:-2048}"
ECC_CURVE="${ECC_CURVE:-prime256v1}" # or secp384r1
OUT_DIR="${OUT_DIR:-/etc/ssl/red}"
SERVER_IP="${SERVER_IP:-}"
FORCE="${FORCE:-0}"

# Parse CLI flags
while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain|-d) DOMAIN="$2"; shift 2 ;;
    --ip|-i) SERVER_IP="$2"; shift 2 ;;
    --days) DAYS="$2"; shift 2 ;;
    --algo|-a) ALGO="$2"; shift 2 ;;
    --out|-o) OUT_DIR="$2"; shift 2 ;;
    --force|-f) FORCE=1; shift ;;
    --help|-h)
      echo "Usage: $0 [OPTIONS]"
      echo "Options:"
      echo "  -d, --domain <name>   Primary domain name (default: red.local)"
      echo "  -i, --ip <ip>         Server IPv4 address (default: auto-detected)"
      echo "  -a, --algo <rsa|ecc>  Algorithm: rsa (2048/4096) or ecc (prime256v1) (default: rsa)"
      echo "  -o, --out <path>      Output directory (default: /etc/ssl/red)"
      echo "  -f, --force           Overwrite existing certificates"
      echo "  --days <days>         Certificate validity in days (default: 3650)"
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "═══════════════════════════════════════════════════════════════════════"
echo " 🔒 YOUNES Platform: TLS Certificate Generation Suite"
echo "═══════════════════════════════════════════════════════════════════════"

# Check openssl
if ! command -v openssl >/dev/null 2>&1; then
  echo "❌ Error: OpenSSL is required but not installed." >&2
  exit 1
fi

# Auto-detect local IP if not provided
if [[ -z "$SERVER_IP" ]]; then
  SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")
  [[ -z "$SERVER_IP" ]] && SERVER_IP="127.0.0.1"
fi

echo "📋 Configuration:"
echo "   Domain:       $DOMAIN"
echo "   Server IP:    $SERVER_IP"
echo "   Algorithm:    $ALGO ($([[ "$ALGO" == "ecc" ]] && echo "$ECC_CURVE" || echo "RSA $KEY_SIZE bit"))"
echo "   Validity:     $DAYS days"
echo "   Output Dir:   $OUT_DIR"
echo ""

# Ensure output directories exist
mkdir -p "$OUT_DIR"
mkdir -p /etc/ssl/certs /etc/ssl/private /var/www/certbot 2>/dev/null || true

PRIVKEY="$OUT_DIR/privkey.pem"
FULLCHAIN="$OUT_DIR/fullchain.pem"

# Check existing files
if [[ "$FORCE" -eq 0 && -s "$PRIVKEY" && -s "$FULLCHAIN" ]]; then
  echo "ℹ️ Valid certificates already exist in $OUT_DIR."
  echo "   Use --force to regenerate."
else
  # Purge any directories mistaken for files
  for p in "$PRIVKEY" "$FULLCHAIN" /etc/ssl/certs/fullchain.pem /etc/ssl/certs/privkey.pem /etc/ssl/private/privkey.pem; do
    if [[ -d "$p" ]]; then
      echo "⚠️ Removing invalid directory found at cert path: $p"
      rm -rf "$p"
    fi
  done

  # Build SAN (Subject Alternative Names)
  SAN_LIST="DNS:localhost,DNS:$DOMAIN,IP:127.0.0.1"
  if [[ "$SERVER_IP" != "127.0.0.1" && -n "$SERVER_IP" ]]; then
    SAN_LIST="$SAN_LIST,IP:$SERVER_IP"
  fi

  TMP_KEY="$PRIVKEY.tmp.$$"
  TMP_CERT="$FULLCHAIN.tmp.$$"

  echo "⏳ Generating TLS certificate & key atomically..."
  if [[ "$ALGO" == "ecc" ]]; then
    openssl req -x509 -newkey ec \
      -pkeyopt "ec_paramgen_curve:$ECC_CURVE" \
      -nodes -sha256 -days "$DAYS" \
      -subj "/CN=$DOMAIN/O=YOUNES Sovereign Platform/OU=Security" \
      -addext "subjectAltName=$SAN_LIST" \
      -keyout "$TMP_KEY" \
      -out "$TMP_CERT"
  else
    openssl req -x509 -newkey "rsa:$KEY_SIZE" \
      -nodes -sha256 -days "$DAYS" \
      -subj "/CN=$DOMAIN/O=YOUNES Sovereign Platform/OU=Security" \
      -addext "subjectAltName=$SAN_LIST" \
      -keyout "$TMP_KEY" \
      -out "$TMP_CERT"
  fi

  # Atomic Move
  mv -f "$TMP_KEY" "$PRIVKEY"
  mv -f "$TMP_CERT" "$FULLCHAIN"

  # Strict Permissions
  chmod 600 "$PRIVKEY"
  chmod 644 "$FULLCHAIN"
  echo "✅ Certificate and Private Key generated successfully!"
fi

# ─── Universal Compatibility Symlinks ───
echo "🔗 Setting up universal compatibility symlinks..."
if [[ -w /etc/ssl/certs ]] || [[ $EUID -eq 0 ]]; then
  mkdir -p /etc/ssl/certs /etc/ssl/private 2>/dev/null || true
  ln -sf "$FULLCHAIN" /etc/ssl/certs/fullchain.pem 2>/dev/null || true
  ln -sf "$PRIVKEY" /etc/ssl/certs/privkey.pem 2>/dev/null || true
  ln -sf "$PRIVKEY" /etc/ssl/private/privkey.pem 2>/dev/null || true
  chmod 755 /etc/ssl/private 2>/dev/null || true
  chmod 600 /etc/ssl/private/privkey.pem 2>/dev/null || true
  echo "   ✓ /etc/ssl/certs/fullchain.pem -> $FULLCHAIN"
  echo "   ✓ /etc/ssl/certs/privkey.pem   -> $PRIVKEY"
  echo "   ✓ /etc/ssl/private/privkey.pem -> $PRIVKEY"
fi

# ─── Cryptographic Verification ───
echo ""
echo "🔍 Verifying cryptographic modulus / public key match..."
PUB1=$(mktemp)
PUB2=$(mktemp)
openssl pkey -in "$PRIVKEY" -pubout > "$PUB1" 2>/dev/null
openssl x509 -in "$FULLCHAIN" -pubkey -noout > "$PUB2" 2>/dev/null

if cmp -s "$PUB1" "$PUB2"; then
  echo "✅ Cryptographic Match: Public key & Private key are 100% matched!"
else
  echo "❌ Error: Certificate does not match the Private Key!" >&2
  rm -f "$PUB1" "$PUB2"
  exit 1
fi
rm -f "$PUB1" "$PUB2"

echo ""
echo "🎉 TLS Configuration is 100% Ready and Hardened!"
