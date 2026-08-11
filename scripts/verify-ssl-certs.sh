#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT/RED_Ultimate_V1-main/RED_Ultimate/scripts/verify-ssl-certs.sh" "$@"
