#!/usr/bin/env bash
# Repository-level compatibility wrapper. The canonical implementation lives
# with docker-compose.yml so path detection and operational behavior cannot drift.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$ROOT/RED_Ultimate_V1-main/RED_Ultimate/scripts/red-cli.sh"
[[ -x "$TARGET" ]] || { echo "Canonical red-cli is missing or not executable: $TARGET" >&2; exit 1; }
exec "$TARGET" "$@"
