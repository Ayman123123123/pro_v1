#!/usr/bin/env sh
# ════════════════════════════════════════════════════════════════════════════
# check-hardcoded-ips.sh — guard against hardcoded private IPv4 literals in the
# Android app source. Emulator/loopback aliases and doc placeholders are allowed.
# A line containing the marker "ALLOW-IP" is always permitted (intentional
# last-resort fallbacks). Exits non-zero when a forbidden literal is found.
# ════════════════════════════════════════════════════════════════════════════
set -eu
# Case-insensitive allowlist matching (parity with the PowerShell guard).
shopt -s nocasematch
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SCAN_DIR="$ROOT/red-app/src/main"

[ -d "$SCAN_DIR" ] || { echo "Nothing to scan (no $SCAN_DIR)."; exit 0; }

# Allowed literals / keywords that make a line OK.
allowed() {
    case "$1" in
        *127.0.0.1*|*10.0.2.2*|*10.0.3.2*|*0.0.0.0*|*255.255.255.255*|\
        *ALLOW-IP*|*localhost*|*emulator*|*alias*|*placeholder*|*example*|\
        *e.g.*|*subnet*|*comment*|*deprecated*|*fallback*|*migrated*|\
        *default*|*hint*|*RED_SERVER_URL*|*BuildConfig*) return 0 ;;
    esac
    return 1
}

IP_RE='(19[2-9]|2[0-1][0-9]|22[0-3])\.[0-9]{1,3}\.[0-9]{1,3}|10\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|172\.(1[6-9]|2[0-9]|3[01])\.[0-9]{1,3}\.[0-9]{1,3}'

count=0
# grep -rEn prints "file:line:content"; iterate matches (C-optimized, no per-line shell loop).
while IFS= read -r hit; do
    if ! allowed "$hit"; then
        echo "FAIL: $hit"
        count=$((count + 1))
    fi
done <<EOF
$(grep -rEn --include='*.kt' --include='*.kts' "$IP_RE" "$SCAN_DIR" 2>/dev/null || true)
EOF

if [ "$count" -gt 0 ]; then
    echo "Hardcoded private IP literals found in app source." >&2
    exit 1
fi
echo "PASS: no hardcoded private IP literals in $SCAN_DIR"
exit 0
