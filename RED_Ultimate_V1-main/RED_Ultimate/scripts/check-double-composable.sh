#!/usr/bin/env bash
# check-double-composable.sh — Legendary Guard for @Composable Duplication Trap
# Fails CI if any Kotlin file has stacked @Composable annotations (merge conflict residue)
# Zero tolerance: one function must have exactly one @Composable
set -euo pipefail
ROOT="${1:-red-app/src/main/java}"
echo "🔍 Scanning for double @Composable in $ROOT ..."

# Pattern 1: two @Composable on consecutive lines (no code between) — the real trap
# Example that FAILS:
#   @Composable
#   @Composable
#   fun Foo()
# Example that PASSES (two separate functions on adjacent lines):
#   @Composable fun Foo() = ...
#   @Composable fun Bar() = ...   ← this is OK, our Python checker handles it

python3 << 'PY'
import re, pathlib, sys
root = pathlib.Path(sys.argv[1] if len(sys.argv)>1 else "red-app/src/main/java")
if not root.exists():
    # try relative to repo root
    root = pathlib.Path("RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java")
if not root.exists():
    root = pathlib.Path(sys.argv[1])

issues = []
for f in root.rglob("*.kt"):
    lines = f.read_text(encoding="utf-8", errors="ignore").splitlines()
    for i in range(len(lines)-1):
        a = lines[i].strip()
        b = lines[i+1].strip()
        # Only flag if BOTH lines are PURE annotation (no fun/val/var on same line)
        # and the next non-empty line is fun/val
        if a == "@Composable" and b == "@Composable":
            issues.append((str(f), i+1))
        # Same line double
        if lines[i].count("@Composable") >= 2 and "fun " in lines[i]:
            # @Composable @Composable fun Foo() on same line
            issues.append((str(f), i+1, lines[i].strip()))

# Additional check: raw consecutive pattern with optional whitespace
raw_issues = []
for f in root.rglob("*.kt"):
    t = f.read_text(encoding="utf-8", errors="ignore")
    if re.search(r'@Composable\s*\n\s*@Composable\s*\n\s*fun', t):
        raw_issues.append(str(f))

if issues or raw_issues:
    print("❌ DOUBLE @Composable TRAP DETECTED!")
    for entry in issues[:20]:
        print(f"  {entry}")
    for p in raw_issues[:20]:
        print(f"  RAW_PATTERN: {p}")
    print(f"Total stacked issues: {len(issues)} | raw files: {len(raw_issues)}")
    print("Fix: remove duplicate @Composable, keep one per function. See CallOverlay.kt / RedDashboard.kt history.")
    sys.exit(1)
else:
    print("✅ No double @Composable found — Sovereign Compose integrity OK")
    # Also report stats for visibility
    total_files = len(list(root.rglob("*.kt")))
    composable_count = sum(1 for f in root.rglob("*.kt") for _ in re.finditer(r'@Composable', f.read_text(encoding="utf-8", errors="ignore")))
    print(f"  Scanned {total_files} Kotlin files, {composable_count} @Composable usages — all clean")
PY

echo "✅ Double-Composable guard passed"
