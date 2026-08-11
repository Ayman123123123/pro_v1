#!/usr/bin/env sh
# Compatibility entrypoint. Delegate to the hardened first-run workflow so this
# script cannot bypass secret generation, Compose validation, or readiness tests.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$ROOT/scripts/local-first-run.sh" "$@"
