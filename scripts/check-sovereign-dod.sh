#!/usr/bin/env bash
# Scope *ratchet*, not a claim that cancelled scope is absent. The prior
# absolute grep failed on thousands of pre-existing lines, including prose.
# CI passes DOD_BASE_SHA for push/PR review; the Python guard fails closed when
# the base is unavailable. See docs/VERIFIED_AUDIT_2026-09-24_AR.md.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/scripts/check-sovereign-dod.py"
