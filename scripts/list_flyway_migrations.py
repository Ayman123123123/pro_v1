#!/usr/bin/env python3
"""List versioned Flyway SQL migrations in numeric order for fresh-DB CI.

`sort -V` puts V26_1 before V26 in some locales, unlike Flyway's ordering.
This helper also fails on version collisions, which a plain shell loop misses.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path


def ordered_migrations(directory: Path) -> list[Path]:
    seen: dict[tuple[int, ...], Path] = {}
    for file in directory.glob("V*.sql"):
        match = re.fullmatch(r"V(\d+(?:_\d+)*)__.+\.sql", file.name)
        if match is None:
            raise ValueError(f"Invalid Flyway migration name: {file.name}")
        version = tuple(int(part) for part in match[1].split("_"))
        if version in seen:
            raise ValueError(f"Duplicate version: {file.name}, {seen[version].name}")
        seen[version] = file
    if not seen:
        raise ValueError(f"No Flyway migrations in {directory}")
    return [seen[key] for key in sorted(seen)]


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: list_flyway_migrations.py db/migration")
    for path in ordered_migrations(Path(sys.argv[1])):
        print(path.name)
