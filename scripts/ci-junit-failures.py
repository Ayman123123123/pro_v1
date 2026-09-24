#!/usr/bin/env python3
"""Summarize failing JUnit XML test cases for GitHub check output.

Actions artifacts and raw logs may be unavailable through the API even when the
check summary is accessible. Never print system-out, test properties, or environment
values: those can contain credentials. Print only failed case identifiers and short
failure messages, with an upper bound for the check summary.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def summarize(directory: Path, limit: int = 30) -> str:
    reports = sorted(directory.glob("TEST-*.xml"))
    if not reports:
        return "JUnit XML: no test reports were generated (compilation/setup may have failed)."
    failures: list[str] = []
    suites = 0
    tests = 0
    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            failures.append(f"Invalid JUnit XML: {report.name}")
            continue
        suites += 1
        tests += int(root.get("tests", "0"))
        for case in root.iter("testcase"):
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            if failure is None:
                continue
            # Avoid dumping full stack traces; line 1 is generally the assertion.
            message = " ".join((failure.get("message") or "").split())[:400]
            if not message:
                message = " ".join((failure.text or "").splitlines()[:1])[:400]
            name = f"{case.get('classname', report.stem)}#{case.get('name', 'unnamed')}"
            failures.append(f"{name}: {message}")
    header = f"JUnit: {suites} suite(s), {tests} test(s), {len(failures)} failure(s)"
    if not failures:
        return header
    return "\n".join([header, *failures[:limit],
        *( [f"... and {len(failures) - limit} more failure(s)"] if len(failures) > limit else [] )])[:18000]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: ci-junit-failures.py path/to/build/test-results/test")
    print(summarize(Path(sys.argv[1])))
