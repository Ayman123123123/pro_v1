#!/usr/bin/env python3
"""Offline regression check for numeric Flyway migration ordering."""
import importlib.util
import tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("list_flyway", Path(__file__).with_name("list_flyway_migrations.py"))
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
with tempfile.TemporaryDirectory() as temp:
    directory = Path(temp)
    for name in ("V26_1__Next.sql", "V27__Later.sql", "V26__First.sql", "V9__Old.sql"):
        (directory / name).write_text("-- test\n")
    assert [p.name for p in module.ordered_migrations(directory)] == [
        "V9__Old.sql", "V26__First.sql", "V26_1__Next.sql", "V27__Later.sql"
    ]
    (directory / "V026__Collision.sql").write_text("-- test\n")
    try:
        module.ordered_migrations(directory)
    except ValueError:
        pass
    else:
        raise AssertionError("duplicate numeric Flyway versions must fail")
print("PASS: migration ordering agrees with Flyway, duplicate versions rejected")
