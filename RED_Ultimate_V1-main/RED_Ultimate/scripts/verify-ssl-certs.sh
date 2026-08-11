#!/usr/bin/env bash
# Verify a TLS certificate/private-key pair without modifying either file.
set -uo pipefail

CERT_PATH="${1:-/etc/ssl/red/fullchain.pem}"
KEY_PATH="${2:-/etc/ssl/red/privkey.pem}"
EXPIRY_WARNING_SECONDS="${TLS_EXPIRY_WARNING_SECONDS:-2592000}" # 30 days
ERRORS=0
WARNINGS=0
TMP_DIR=""

error() { printf '❌ %s\n' "$*"; ERRORS=$((ERRORS + 1)); }
warn() { printf '⚠️  %s\n' "$*"; WARNINGS=$((WARNINGS + 1)); }
ok() { printf '✅ %s\n' "$*"; }
cleanup() { [[ -n "$TMP_DIR" ]] && rm -rf "$TMP_DIR"; }
trap cleanup EXIT INT TERM

printf '%s\n' '═══════════════════════════════════════════════════════════════════════'
printf '%s\n' ' TLS certificate/private-key audit (read-only)'
printf ' Certificate: %s\n Private key: %s\n' "$CERT_PATH" "$KEY_PATH"
printf '%s\n' '═══════════════════════════════════════════════════════════════════════'

command -v openssl >/dev/null 2>&1 || { error 'openssl is required'; exit 1; }
command -v stat >/dev/null 2>&1 || { error 'stat is required'; exit 1; }

check_regular_file() {
  local path="$1" label="$2"
  if [[ ! -e "$path" && ! -L "$path" ]]; then
    error "$label does not exist: $path"
    return 1
  fi
  if [[ -d "$path" ]]; then
    error "$label is a directory (likely a Docker bind-mount trap): $path"
    return 1
  fi
  if [[ ! -f "$path" ]]; then
    error "$label is not a regular file: $path"
    return 1
  fi
  if [[ ! -s "$path" ]]; then
    error "$label is empty: $path"
    return 1
  fi
  if [[ ! -r "$path" ]]; then
    error "$label is not readable by the current user: $path"
    return 1
  fi
  ok "$label exists ($(wc -c < "$path") bytes)"
}

CERT_OK=1
KEY_OK=1
check_regular_file "$CERT_PATH" 'Certificate' || CERT_OK=0
check_regular_file "$KEY_PATH" 'Private key' || KEY_OK=0

if (( CERT_OK )); then
  if openssl x509 -in "$CERT_PATH" -noout >/dev/null 2>&1; then
    ok 'Certificate parses as X.509'
  else
    error 'Certificate is not valid X.509/PEM'
    CERT_OK=0
  fi
fi

if (( KEY_OK )); then
  if openssl pkey -in "$KEY_PATH" -noout -check >/dev/null 2>&1; then
    ok 'Private key parses and passes its integrity check'
  else
    error 'Private key is invalid, encrypted without an available passphrase, or corrupt'
    KEY_OK=0
  fi
fi

# Permission checks follow symlinks and inspect the actual target.
if (( KEY_OK )); then
  key_mode="$(stat -L -c '%a' "$KEY_PATH" 2>/dev/null || true)"
  if [[ "$key_mode" =~ ^[0-7]{3,4}$ ]]; then
    key_perm=$((8#$key_mode))
    if (( key_perm & 0077 )); then
      error "Private key permissions are too broad ($key_mode); expected 600 or 400"
    else
      ok "Private key permissions are restrictive ($key_mode)"
    fi
  else
    warn 'Could not determine private-key permissions'
  fi
fi

if (( CERT_OK )); then
  cert_mode="$(stat -L -c '%a' "$CERT_PATH" 2>/dev/null || true)"
  if [[ "$cert_mode" =~ ^[0-7]{3,4}$ ]]; then
    cert_perm=$((8#$cert_mode))
    if (( cert_perm & 0022 )); then
      error "Certificate is writable by group/others ($cert_mode)"
    else
      ok "Certificate is not group/other-writable ($cert_mode)"
    fi
  else
    warn 'Could not determine certificate permissions'
  fi
fi

if (( CERT_OK )); then
  printf '\nSubject:    %s\n' "$(openssl x509 -in "$CERT_PATH" -noout -subject)"
  printf 'Issuer:     %s\n' "$(openssl x509 -in "$CERT_PATH" -noout -issuer)"
  printf 'Valid from: %s\n' "$(openssl x509 -in "$CERT_PATH" -noout -startdate | cut -d= -f2-)"
  printf 'Valid to:   %s\n' "$(openssl x509 -in "$CERT_PATH" -noout -enddate | cut -d= -f2-)"

  if ! openssl x509 -in "$CERT_PATH" -noout -checkend 0 >/dev/null 2>&1; then
    error 'Certificate is expired or not currently valid'
  elif ! openssl x509 -in "$CERT_PATH" -noout -checkend "$EXPIRY_WARNING_SECONDS" >/dev/null 2>&1; then
    warn 'Certificate expires within the configured warning window (default: 30 days)'
  else
    ok 'Certificate is valid beyond the warning window'
  fi

  san_output="$(openssl x509 -in "$CERT_PATH" -noout -ext subjectAltName 2>/dev/null || true)"
  if [[ -n "$san_output" ]]; then
    printf '%s\n' "$san_output" | sed 's/^/  /'
  else
    warn 'Certificate has no Subject Alternative Name extension'
  fi
fi

if (( CERT_OK && KEY_OK )); then
  TMP_DIR="$(mktemp -d)"
  if openssl x509 -in "$CERT_PATH" -pubkey -noout \
       | openssl pkey -pubin -outform DER >"$TMP_DIR/cert.der" 2>/dev/null \
    && openssl pkey -in "$KEY_PATH" -pubout -outform DER >"$TMP_DIR/key.der" 2>/dev/null; then
    if cmp -s "$TMP_DIR/cert.der" "$TMP_DIR/key.der"; then
      ok 'Certificate public key matches the private key'
    else
      error 'Certificate/private-key cryptographic mismatch'
    fi
  else
    error 'Could not extract public keys for pair verification'
  fi
fi

printf '\nErrors: %d | Warnings: %d\n' "$ERRORS" "$WARNINGS"
if (( ERRORS == 0 )); then
  ok 'TLS verification passed'
  exit 0
fi
printf '❌ TLS verification failed\n'
exit 1
