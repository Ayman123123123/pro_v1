#!/usr/bin/env python3
"""Ratchet cancelled-scope additions and protect the retired server boundary.

The prior zero-word grep was not an executable DoD: historical migrations,
client copy and comments include the words. Runtime PSTN/DINSTAR Kotlin was
selectively removed after its accidental 22 September restoration; guard that
specific boundary, and check *new* integrations against a real Git review base.
Never use a missing base as PASS. Historical migrations are kept for Flyway.
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

    # V52 dropped the gateway schema. A filename-level fence catches a wholesale
    # restoration even if it happens outside a Git diff (e.g. CI dispatch). The
    # only exception is the intentional 410 compatibility endpoint.
    backend = ROOT / PRODUCT / "backend-server/src/main/kotlin"
    legacy_stub = backend / "com/red/server/auth/PstnAuthorizationController.kt"
    for file in backend.rglob("*.kt"):
        if re.search(r"pstn|dinstar|sms|ussd|gatewaysim", file.name, re.I) and file != legacy_stub:
            violations.append(f"cancelled server runtime source: {file.relative_to(ROOT)}")
    if not legacy_stub.is_file() or "HttpStatus.GONE" not in legacy_stub.read_text(encoding="utf-8"):
        violations.append("retired admin PSTN endpoint must return HTTP 410")
    account = backend / "com/red/server/auth/model/UserAccount.kt"
    if "pstn_number" in account.read_text(encoding="utf-8"):
        violations.append("UserAccount maps a column dropped in V52: pstn_number")

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
    print(f"PASS: no new cancelled-scope integration relative to {base[:12]}; retired server boundary and Firebase SDK check pass")
    print("NOTE: historical migrations, dormant clients and unverified marketing claims remain outside this static gate; it is not a zero-debt certificate")
    return 0


if __name__ == "__main__":
    sys.exit(main())
