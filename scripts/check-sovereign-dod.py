#!/usr/bin/env python3
"""Ratchet the cancelled Firebase/telephony scopes without erasing historical code.

The previous DoD claimed zero banned words in shipped source, but failed on >2,000
pre-existing references (including comments). It never represented a passing CI gate.
This checks *new* source/dependencies against a Git base; legacy scope remains a
separate, explicitly reported retirement task. Never use a missing base as PASS.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PRODUCT = Path("RED_Ultimate_V1-main/RED_Ultimate")
SOURCE_ROOTS = (
    PRODUCT / "backend-server/src/main",
    PRODUCT / "red-app/src/main",
    PRODUCT / "admin_dashboard/src",
    PRODUCT / "media-sfu",
    PRODUCT / "shared-proto/src/main",
)
CANCELLED_NAME = re.compile(r"dinstar|pstn|yemen|asterisk|firebase|fcm", re.I)
# Comments/documentation mentioning legacy scope are not a new integration.
ADDED_RUNTIME = re.compile(
    r"^\s*(?:import\s+(?:com\.google\.firebase\b|org\.asteriskjava\b|com\.red\.[\w.]*\.(?:pstn|dinstar)\b)"
    r"|(?:implementation|api|runtimeOnly|id)\s*\(?\s*['\"](?:com\.google\.firebase|firebase-|com\.google\.gms\.google-services)"
    r"|@(?:RequestMapping|PostMapping|GetMapping|PutMapping|PatchMapping|DeleteMapping)\s*\([^\n]*?/(?:pstn|dinstar|ussd)\b)",
    re.I,
)
SDK = re.compile(
    r"^\s*(?:import\s+com\.google\.firebase\b|(?:implementation|api|runtimeOnly|id)\s*\(?\s*['\"](?:com\.google\.firebase|firebase-|com\.google\.gms\.google-services))",
    re.I | re.M,
)


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True, stderr=subprocess.PIPE)


def source(path: Path) -> bool:
    return any(path.is_relative_to(prefix) for prefix in SOURCE_ROOTS) and "_archive" not in path.parts


def main() -> int:
    base = os.getenv("DOD_BASE_SHA", "").strip()
    if not base or set(base) == {"0"}:
        # Local edits use origin/main; in a shallow CI dispatch, HEAD^ is the
        # latest reviewed commit. Push/PR always provide an explicit event SHA.
        for candidate in ("origin/main", "HEAD^"):
            try:
                base = git("rev-parse", "--verify", candidate).strip()
                break
            except subprocess.CalledProcessError:
                pass
    try:
        git("cat-file", "-e", f"{base}^{{commit}}")
    except subprocess.CalledProcessError:
        print(f"FAIL: review base {base or '<missing>'} is unavailable; fetch it before running DoD")
        return 2

    new = git("diff", "--name-only", "--diff-filter=ACR", base, "--", str(PRODUCT)).splitlines()
    violations: list[str] = []
    for name in new:
        path = Path(name)
        if not source(path) or not CANCELLED_NAME.search(path.name):
            continue
        violations.append(f"new cancelled-scope source file: {name}")

    # A newly added import/dependency/endpoint is dangerous even if placed in a
    # grandfathered file. Filter removed lines and diff metadata, not old debt.
    diff = git("diff", "--unified=0", base, "--", *(str(p) for p in SOURCE_ROOTS),
               str(PRODUCT / "red-app/build.gradle.kts"), str(PRODUCT / "backend-server/build.gradle.kts"))
    path = ""
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            path = line[6:]
        elif line.startswith("+") and not line.startswith("+++") and ADDED_RUNTIME.search(line[1:]):
            violations.append(f"new cancelled-scope integration in {path}: {line[1:].strip()}")

    # Irrespective of Git base, no real Firebase SDK is linked by shipped code.
    for root in SOURCE_ROOTS:
        if not (ROOT / root).is_dir():
            continue
        for file in (ROOT / root).rglob("*"):
            if file.suffix not in {".kt", ".java", ".ts", ".tsx", ".js", ".kts"} or not file.is_file():
                continue
            rel = file.relative_to(ROOT)
            if not source(rel) or "node_modules" in rel.parts or "build" in rel.parts:
                continue
            if SDK.search(file.read_text(encoding="utf-8", errors="replace")):
                violations.append(f"Firebase SDK in shipped code: {rel}")

    if violations:
        print("FAIL: sovereign scope regression(s):")
        for item in sorted(set(violations)):
            print("  -", item)
        return 1
    print(f"PASS: no new cancelled-scope integration relative to {base[:12]}; no Firebase SDK in shipped code")
    print("NOTE: pre-existing PSTN/DINSTAR sources and obsolete FCM claims still require separately staged retirement; this is a ratchet, NOT a zero-debt certificate")
    return 0


if __name__ == "__main__":
    sys.exit(main())
