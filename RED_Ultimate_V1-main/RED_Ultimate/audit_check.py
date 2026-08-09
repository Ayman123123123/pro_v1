#!/usr/bin/env python3
"""Fast structural audit for the active YOUNES product, not its archived prototypes."""
from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as element_tree
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ACTIVE_PATHS = {
    "Android product": ROOT / "red-app" / "src" / "main" / "AndroidManifest.xml",
    "Android build": ROOT / "red-app" / "build.gradle.kts",
    "Backend": ROOT / "backend-server" / "build.gradle.kts",
    "Admin dashboard": ROOT / "admin_dashboard" / "package.json",
    "Shared protocol": ROOT / "shared-proto" / "src" / "main" / "proto" / "red_protocol.proto",
    "SFU": ROOT / "media-sfu" / "server.js",
    "PSTN bridge": ROOT / "pstn-asterisk" / "extensions.conf",
    "Runtime compose": ROOT / "docker-compose.yml",
}


def check(condition: bool, label: str, failures: list[str]) -> None:
    mark = "PASS" if condition else "FAIL"
    print(f"[{mark}] {label}")
    if not condition:
        failures.append(label)


def parse_files(extension: str, parser) -> tuple[int, list[str]]:
    checked = 0
    bad: list[str] = []
    excluded = {"node_modules", "build", "dist", ".gradle"}
    for file in ROOT.rglob(f"*.{extension}"):
        if excluded.intersection(file.parts):
            continue
        checked += 1
        try:
            parser(file)
        except Exception as error:  # report every malformed config source
            bad.append(f"{file.relative_to(ROOT)}: {error}")
    return checked, bad


def parse_json(file: Path) -> None:
    json.loads(file.read_text(encoding="utf-8"))


def parse_xml(file: Path) -> None:
    element_tree.parse(file)


def main() -> int:
    print("--- YOUNES / RED ACTIVE PRODUCT STRUCTURAL AUDIT ---")
    failures: list[str] = []

    for label, path in ACTIVE_PATHS.items():
        check(path.is_file(), f"{label}: {path.relative_to(ROOT)}", failures)

    manifest = ACTIVE_PATHS["Android product"]
    if manifest.is_file():
        text = manifest.read_text(encoding="utf-8")
        check('android:label="يونس"' in text, "Android product is branded YOUNES", failures)
        check('android:name=".MainActivity"' in text, "Android has one declared launcher entry", failures)
        check("FOREGROUND_SERVICE_REMOTE_MESSAGING" in text, "Android declares local messaging service capability", failures)

    auth_source = ROOT / "red-app" / "src" / "main" / "java" / "com" / "red" / "sovereign" / "ui" / "AuthScreens.kt"
    main_activity = ROOT / "red-app" / "src" / "main" / "java" / "com" / "red" / "sovereign" / "MainActivity.kt"
    forbidden_otp_flow = ROOT / "red-app" / "src" / "main" / "java" / "com" / "red" / "sovereign" / "ui" / "AuthFlow.kt"
    check(auth_source.is_file() and "لا نطلب رقم هاتف" in auth_source.read_text(encoding="utf-8"), "Identity flow declares no phone requirement", failures)
    check(main_activity.is_file() and "AuthFlow(authViewModel)" in main_activity.read_text(encoding="utf-8"), "MainActivity uses the sovereign auth flow", failures)
    check(not forbidden_otp_flow.exists(), "Legacy phone/OTP onboarding is absent", failures)

    model_profile = ROOT / "backend-server" / "src" / "main" / "kotlin" / "com" / "red" / "server" / "services" / "DinstarModelProfile.kt"
    check(model_profile.is_file() and "UC2000-VE-8G" in model_profile.read_text(encoding="utf-8") and "UC2000-VE-8T" in model_profile.read_text(encoding="utf-8"), "Backend distinguishes 8G and 8T gateway profiles", failures)

    json_count, bad_json = parse_files("json", parse_json)
    xml_count, bad_xml = parse_files("xml", parse_xml)
    check(not bad_json, f"JSON parsing: {json_count} checked", failures)
    check(not bad_xml, f"XML parsing: {xml_count} checked", failures)
    for error in bad_json + bad_xml:
        print(f"  {error}")

    source_count = sum(1 for path in ROOT.rglob("*") if path.is_file() and path.suffix in {".kt", ".java", ".js", ".ts", ".tsx", ".jsx"} and "node_modules" not in path.parts and "build" not in path.parts)
    print(f"[INFO] Active repository source files scanned: {source_count}")

    if failures:
        print(f"--- RESULT: FAILED ({len(failures)} structural checks) ---")
        return 1
    print("--- RESULT: PASSED (structural checks only; build/runtime/device tests remain required) ---")
    return 0


if __name__ == "__main__":
    sys.exit(main())
