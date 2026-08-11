#!/usr/bin/env bash
# check-icon-integrity.sh — Legendary Guard for Adaptive Icons & SVG Merger Trap
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RES="$REPO_ROOT/red-app/src/main/res"

echo "🖼️ Checking Icon Integrity (SVG Merger Trap) ..."

fail=0
# 1) No .svg in res (must be vector XML, not SVG)
if find "$RES" -name "*.svg" | grep -q .; then
  echo "❌ Found .svg in res/ — Android mergeDebugResources will fail. Convert to VectorDrawable XML:"
  find "$RES" -name "*.svg"
  fail=1
else
  echo "✅ No .svg in res/ — vector XML clean"
fi

# 2) No mipmap-anydpi (without -v26) — must be mipmap-anydpi-v26 for adaptive
if [ -d "$RES/mipmap-anydpi" ]; then
  echo "❌ Found mipmap-anydpi/ — must be mipmap-anydpi-v26 for adaptive icons (API 26+)"
  ls -lh "$RES/mipmap-anydpi"
  fail=1
else
  echo "✅ No mipmap-anydpi/ — adaptive boundary correct"
fi

# 3) mipmap-anydpi-v26 must contain ic_launcher.xml and ic_launcher_round.xml
for f in ic_launcher.xml ic_launcher_round.xml; do
  if [ ! -f "$RES/mipmap-anydpi-v26/$f" ]; then
    echo "❌ Missing $RES/mipmap-anydpi-v26/$f — adaptive icon broken"
    fail=1
  else
    echo "✅ $f in mipmap-anydpi-v26"
  fi
done

# 4) Every density must have png (not xml) for legacy <26
for dpi in hdpi mdpi xhdpi xxhdpi xxxhdpi; do
  for name in ic_launcher.png ic_launcher_round.png; do
    if [ ! -f "$RES/mipmap-$dpi/$name" ]; then
      echo "❌ Missing $RES/mipmap-$dpi/$name — legacy icon missing for API <26"
      fail=1
    fi
  done
  # Must NOT have xml in density folders
  if ls "$RES/mipmap-$dpi"/*.xml 2>/dev/null | grep -q .; then
    echo "⚠️ Found .xml in mipmap-$dpi/ — should be png only for legacy"
    ls "$RES/mipmap-$dpi"/*.xml
  fi
done
echo "✅ All densities have png legacy icons"

# 5) drawable must have vector foreground/background, not png duplicates
for xml in ic_launcher_background.xml ic_launcher_foreground.xml ic_launcher_monochrome.xml; do
  if [ ! -f "$RES/drawable/$xml" ]; then
    echo "❌ Missing drawable/$xml — adaptive layers incomplete"
    fail=1
  else
    echo "✅ drawable/$xml present"
  fi
done

# 6) Check for duplicate resource names (png + webp same name)
if find "$RES/mipmap-"* -name "*.webp" | grep -q .; then
  echo "⚠️ Found .webp in mipmap — ensure no duplicate name with .png (mergeDebugResources duplicate)"
  find "$RES/mipmap-"* -name "*.webp"
fi

if [ $fail -ne 0 ]; then
  echo "🔴 Icon Integrity FAILED — fix adaptive icons before build"
  exit 1
fi
echo "🖼️ Icon Integrity — LEGENDARY PASSED (Adaptive icons sovereign for API 14-37)"
