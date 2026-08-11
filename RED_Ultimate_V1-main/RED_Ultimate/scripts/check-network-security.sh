#!/usr/bin/env bash
# check-network-security.sh — Legendary Guard for TLS-only Enforcement
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MAIN="$REPO_ROOT/red-app/src/main/res/xml/network_security_config.xml"
DEBUG="$REPO_ROOT/red-app/src/debug/res/xml/network_security_config.xml"
MANIFEST="$REPO_ROOT/red-app/src/main/AndroidManifest.xml"
BUILD="$REPO_ROOT/red-app/build.gradle.kts"

echo "🔒 Checking Network Security Lock (TLS-only) ..."

fail=0
# 1) Release must be false
if grep -q 'cleartextTrafficPermitted="true"' "$MAIN"; then
  echo "❌ RELEASE config ($MAIN) must NOT contain cleartext true — TLS-only violated"
  fail=1
else
  echo "✅ RELEASE base-config is TLS-only (false) — sovereign"
fi
if ! grep -q 'cleartextTrafficPermitted="false"' "$MAIN"; then
  echo "❌ RELEASE config missing false flag"
  fail=1
fi
# 2) Debug must be true
if ! grep -q 'cleartextTrafficPermitted="true"' "$DEBUG"; then
  echo "❌ DEBUG config ($DEBUG) must allow cleartext for LAN dev"
  fail=1
else
  echo "✅ DEBUG overlay allows cleartext — dev friendly"
fi
# 3) Manifest must use placeholder, not hardcoded true
if grep -q 'usesCleartextTraffic="true"' "$MANIFEST"; then
  echo "❌ Manifest has hardcoded usesCleartext true — must use \${usesCleartext}"
  fail=1
else
  echo "✅ Manifest uses placeholder \${usesCleartext} — buildType controlled"
fi
if ! grep -q 'usesCleartext' "$MANIFEST"; then
  echo "❌ Manifest missing usesCleartext placeholder"
  fail=1
fi
# 4) build.gradle.kts must set true for debug, false for release
if ! grep -q 'manifestPlaceholders\["usesCleartext"\] = "true"' "$BUILD"; then
  echo "❌ build.gradle.kts debug must set true"
  fail=1
else
  echo "✅ buildTypes.debug → true"
fi
if ! grep -q 'manifestPlaceholders\["usesCleartext"\] = "false"' "$BUILD"; then
  echo "❌ build.gradle.kts release must set false"
  fail=1
else
  echo "✅ buildTypes.release → false"
fi

if [ $fail -ne 0 ]; then
  echo "🔴 Network Security Lock FAILED — fix configs before release"
  exit 1
fi
echo "🔒 Network Security Lock — LEGENDARY PASSED (TLS-only sovereign, debug LAN allowed)"
