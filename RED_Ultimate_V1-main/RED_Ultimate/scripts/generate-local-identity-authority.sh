#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
OUT="$ROOT/secrets"
PRIVATE="$OUT/red_identity_private_key.pem"
PUBLIC="$OUT/red_identity_public_key.pem"

command -v openssl >/dev/null 2>&1 || {
  echo "openssl is required" >&2
  exit 1
}

mkdir -p "$OUT"
umask 077
if [ -e "$PRIVATE" ] || [ -e "$PUBLIC" ]; then
  echo "Identity authority keys already exist; refusing to overwrite them." >&2
  exit 1
fi

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$PRIVATE"
openssl pkey -in "$PRIVATE" -pubout -out "$PUBLIC"
chmod 600 "$PRIVATE"
chmod 644 "$PUBLIC"
printf 'Created local RED identity authority keys in %s\n' "$OUT"
printf 'Back up the private key securely; never commit or send it to a client.\n'
