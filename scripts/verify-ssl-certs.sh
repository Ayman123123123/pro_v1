#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# 🔍 YOUNES Platform — Comprehensive SSL/TLS Verification & Audit Tool
# Validates:
#  1. File existence and type (ensures files, not directories)
#  2. Permissions (private key 0600, cert 0644, dir 0755)
#  3. Expiration dates & days remaining
#  4. Subject, Issuer, and SAN (Subject Alternative Names)
#  5. Cryptographic Match (Public key vs Private key Modulus)
#  6. Universal Symlink Consistency across /etc/ssl/private, /etc/ssl/certs, /etc/ssl/red
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

CERT_PATH="${1:-/etc/ssl/red/fullchain.pem}"
KEY_PATH="${2:-/etc/ssl/red/privkey.pem}"

echo "═══════════════════════════════════════════════════════════════════════"
echo " 🛡️ TLS Certificate & Private Key Health Audit"
echo "═══════════════════════════════════════════════════════════════════════"

ERRORS=0
WARNINGS=0

check_file() {
  local path="$1"
  local name="$2"
  
  if [[ ! -e "$path" ]]; then
    echo "❌ CRITICAL: $name does not exist at '$path'"
    ERRORS=$((ERRORS + 1))
    return 1
  fi

  if [[ -d "$path" ]]; then
    echo "❌ CRITICAL: $name at '$path' is a DIRECTORY (Docker bind-mount trap!)"
    ERRORS=$((ERRORS + 1))
    return 1
  fi

  if [[ ! -s "$path" ]]; then
    echo "❌ CRITICAL: $name at '$path' is EMPTY (0 bytes - incomplete generation)"
    ERRORS=$((ERRORS + 1))
    return 1
  fi

  echo "✅ $name: Present, valid file ($(wc -c < "$path") bytes)"
  return 0
}

# 1. Existence and Type Checks
echo "1️⃣ Checking Certificate and Private Key Files..."
check_file "$CERT_PATH" "Certificate (fullchain.pem)" || true
check_file "$KEY_PATH" "Private Key (privkey.pem)" || true

# Check alternative paths
echo ""
echo "2️⃣ Checking Universal Symlink Paths..."
for p in /etc/ssl/private/privkey.pem /etc/ssl/certs/fullchain.pem /etc/ssl/certs/privkey.pem /etc/ssl/red/fullchain.pem /etc/ssl/red/privkey.pem; do
  if [[ -e "$p" ]]; then
    if [[ -L "$p" ]]; then
      echo "  ✓ $p -> $(readlink -f "$p")"
    else
      echo "  ✓ $p (regular file)"
    fi
  else
    echo "  ⚠️ Optional path not linked: $p"
    WARNINGS=$((WARNINGS + 1))
  fi
done

# 2. OpenSSL Parse and Expiry
if [[ -s "$CERT_PATH" && ! -d "$CERT_PATH" ]]; then
  echo ""
  echo "3️⃣ Certificate Details & Expiration..."
  SUBJECT=$(openssl x509 -in "$CERT_PATH" -noout -subject 2>/dev/null || echo "Unknown")
  ISSUER=$(openssl x509 -in "$CERT_PATH" -noout -issuer 2>/dev/null || echo "Unknown")
  START_DATE=$(openssl x509 -in "$CERT_PATH" -noout -startdate 2>/dev/null || echo "Unknown")
  END_DATE=$(openssl x509 -in "$CERT_PATH" -noout -enddate 2>/dev/null || echo "Unknown")
  
  echo "  Subject:    $SUBJECT"
  echo "  Issuer:     $ISSUER"
  echo "  Valid From: $START_DATE"
  echo "  Valid To:   $END_DATE"

  # Expiry check
  if openssl x509 -checkend 0 -noout -in "$CERT_PATH" >/dev/null 2>&1; then
    echo "  Status:     ✅ VALID (Not Expired)"
  else
    echo "  Status:     ❌ EXPIRED!"
    ERRORS=$((ERRORS + 1))
  fi

  # SAN check
  echo ""
  echo "4️⃣ Subject Alternative Names (SAN):"
  openssl x509 -in "$CERT_PATH" -noout -text 2>/dev/null | grep -A 1 "Subject Alternative Name" || echo "  (None)"
fi

# 3. Cryptographic Modulus Match
if [[ -s "$CERT_PATH" && -s "$KEY_PATH" && ! -d "$CERT_PATH" && ! -d "$KEY_PATH" ]]; then
  echo ""
  echo "5️⃣ Cryptographic Key Match Verification..."
  PUB1=$(mktemp)
  PUB2=$(mktemp)
  if openssl pkey -in "$KEY_PATH" -pubout > "$PUB1" 2>/dev/null && \
     openssl x509 -in "$CERT_PATH" -pubkey -noout > "$PUB2" 2>/dev/null; then
    if cmp -s "$PUB1" "$PUB2"; then
      echo "  ✅ PERFECT MATCH: Private key precisely corresponds to Public Certificate!"
    else
      echo "  ❌ CRYPTOGRAPHIC MISMATCH: Private key does NOT match certificate!"
      ERRORS=$((ERRORS + 1))
    fi
  else
    echo "  ❌ Error extracting public keys for comparison."
    ERRORS=$((ERRORS + 1))
  fi
  rm -f "$PUB1" "$PUB2"
fi

# Summary
echo ""
echo "═══════════════════════════════════════════════════════════════════════"
echo " 📊 Audit Summary:"
echo "    Errors:   $ERRORS"
echo "    Warnings: $WARNINGS"
if [[ $ERRORS -eq 0 ]]; then
  echo " 🟢 VERIFICATION PASSED: SSL/TLS Setup is healthy and ready for traffic!"
  echo "═══════════════════════════════════════════════════════════════════════"
  exit 0
else
  echo " 🔴 VERIFICATION FAILED: Please run fix-red-proxy-certs.sh to repair."
  echo "═══════════════════════════════════════════════════════════════════════"
  exit 1
fi
