#!/usr/bin/env python3
"""Cheap offline self-test for check-run diagnostic extraction."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("ci_error_excerpt", Path(__file__).with_name("ci-error-excerpt.py"))
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

log = "\n".join(
    [f"e: file:///runner/app/src/main/kotlin/First.kt:{i}:1 cascading error" for i in range(100)]
    + ["e: file:///runner/app/src/main/kotlin/Second.kt:8:1 actual root cause", "> Task :app:compileDebugKotlin FAILED"]
)
excerpt = module.excerpt(log)
assert excerpt.count("cascading error") == 3
assert "Second.kt" in excerpt and "actual root cause" in excerpt
assert "compileDebugKotlin FAILED" in excerpt
print("PASS: diagnostics include every file without flooding check output")
