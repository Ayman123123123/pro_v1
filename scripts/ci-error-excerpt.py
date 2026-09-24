#!/usr/bin/env python3
"""Report compilation failures across *all* files, not just the first 80 lines.

GitHub check output is reachable even when Actions' blob log host is blocked.
Only diagnostics, not environment dumps, are written to the check summary.
"""

from __future__ import annotations

import collections
import re
import sys
from pathlib import Path


def excerpt(text: str) -> str:
    files: dict[str, list[str]] = collections.defaultdict(list)
    other: list[str] = []
    context: list[str] = []
    in_cause = False
    for line in text.splitlines():
        # Gradle's problem description often carries the useful reason (e.g.
        # version-catalog alias collisions) on the line AFTER the exception.
        if line.strip() == "* What went wrong:":
            in_cause = True
        elif line.startswith("* Try:") or line.startswith("* Exception is:"):
            in_cause = False
        elif in_cause and line.strip() and len(context) < 16:
            detail = line.strip()[:350]
            if detail not in context:
                context.append(detail)
        # Gradle Kotlin 2/AGP errors: e: file:///.../source.kt:line:col ...
        match = re.search(r"\be: file:///([^\s]+?\.(?:kt|java|kts)):(\d+):(\d+) (.+)", line)
        if match:
            path, row, _col, message = match.groups()
            path = path.split("/src/")[-1] if "/src/" in path else "/".join(path.split("/")[-3:])
            values = files[path]
            if len(values) < 3:
                values.append(f"L{row}: {message[:250]}")
            continue
        if re.search(r"(?:^|\s)(?:[> ] Task .*FAILED|FAILURE:|Execution failed for task|^error:|^Caused by: .*Exception)", line):
            if len(other) < 12:
                other.append(line.strip()[-350:])

    lines: list[str] = ["Compilation diagnostics by file (first 3 per file):"]
    for path, errors in files.items():
        lines.append(path)
        lines.extend("  " + error for error in errors)
    lines.extend(["Gradle failure context:", *context, "Other Gradle failures:", *other])
    return "\n".join(lines)[:19000]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: ci-error-excerpt.py /path/to/gradle.log")
    print(excerpt(Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")))
