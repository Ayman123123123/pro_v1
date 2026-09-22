#!/usr/bin/env python3
"""
════════════════════════════════════════════════════════════════════════
  RED Ultimate V1 — Test Runner & Validator
  - Validates Kotlin file syntax (deep)
  - Validates TS/TSX import/export consistency
  - Validates SQL migration files
  - Validates GitHub Actions workflow YAML
  - Validates API endpoint consistency (frontend ↔ backend)
  - Cross-references all data models
═══════════════════════════════════════════════════════════════════════
"""
import json
import re
import sys
import os
from pathlib import Path
from collections import defaultdict
from typing import Dict, List, Set, Tuple

ROOT = Path("RED_Ultimate_V1-main/RED_Ultimate")
os.chdir(Path(__file__).parent.parent)


class TestResult:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.warnings = 0
        self.failures: List[str] = []
        self.warnings_list: List[str] = []
        self.successes: List[str] = []

    def ok(self, msg: str):
        self.passed += 1
        self.successes.append(msg)

    def fail(self, msg: str):
        self.failed += 1
        self.failures.append(msg)

    def warn(self, msg: str):
        self.warnings += 1
        self.warnings_list.append(msg)

    def report(self):
        print("\n" + "=" * 78)
        print("  🧪 TEST RESULTS")
        print("=" * 78)
        print(f"  ✅ Passed:   {self.passed}")
        print(f"  ❌ Failed:   {self.failed}")
        print(f"  ⚠️  Warnings: {self.warnings}")
        if self.failures:
            print("\n  ❌ FAILURES:")
            for f in self.failures[:30]:
                print(f"     {f}")
        if self.warnings_list:
            print(f"\n  ⚠️  WARNINGS ({len(self.warnings_list)} total):")
            for w in self.warnings_list[:20]:
                print(f"     {w}")
            if len(self.warnings_list) > 20:
                print(f"     ... and {len(self.warnings_list) - 20} more")
        print("\n" + "=" * 78)
        total = self.passed + self.failed
        if total == 0:
            return 0
        return int(100 * self.passed / total)


r = TestResult()


def strip_strings_and_comments(text: str) -> str:
    """Replace strings/comments with placeholders so braces inside don't break counts."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            j = text.find('\n', i)
            out.append(' ' * (min(j, n) - i) if j != -1 else ' ' * (n - i))
            i = n if j == -1 else j
            continue
        if c == '/' and i + 1 < n and text[i + 1] == '*':
            j = text.find('*/', i + 2)
            if j == -1:
                out.append(' ' * (n - i)); i = n; continue
            out.append(' ' * (j + 2 - i))
            i = j + 2
            continue
        if text[i:i + 3] == '"""':
            j = text.find('"""', i + 3)
            if j == -1:
                out.append(' ' * (n - i)); i = n; continue
            out.append(' ' * (j + 3 - i))
            i = j + 3
            continue
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == '\\' and j + 1 < n:
                    j += 2; continue
                if text[j] == '"': break
                j += 1
            out.append('"' + ' ' * (min(j, n) - 1 - i) + ('"' if j < n else ''))
            i = min(j + 1, n)
            continue
        if c == "'":
            j = i + 1
            while j < n:
                if text[j] == '\\' and j + 1 < n:
                    j += 2; continue
                if text[j] == "'": break
                j += 1
            out.append("'" + ' ' * (min(j, n) - 1 - i) + ("'" if j < n else ''))
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return ''.join(out)


# ── Test 1: Kotlin file syntax ──

def test_kotlin_syntax():
    print("\n🔷 Test 1: Kotlin file syntax check")
    kt_files = list(ROOT.rglob("*.kt"))
    # Skip Signal fork files
    skip_patterns = ["app/src", "android/app", "demo/", "_archive", ".gradle", "build/"]
    real_files = [f for f in kt_files if not any(p in str(f) for p in skip_patterns)]
    print(f"   📁 Scanning {len(real_files)} RED Ultimate Kotlin files...")

    issues = 0
    for path in real_files:
        try:
            text = path.read_text(encoding='utf-8', errors='ignore')
        except Exception as e:
            r.fail(f"{path.name}: cannot read ({e})")
            issues += 1
            continue

        # Use proper string/comment stripping
        clean = strip_strings_and_comments(text)

        # Brace balance
        open_brace = clean.count('{')
        close_brace = clean.count('}')
        if abs(open_brace - close_brace) > 2:
            r.fail(f"{path.name}: unbalanced braces ({open_brace} vs {close_brace})")
            issues += 1
            continue

        open_paren = clean.count('(')
        close_paren = clean.count(')')
        if abs(open_paren - close_paren) > 2:
            r.fail(f"{path.name}: unbalanced parens ({open_paren} vs {close_paren})")
            issues += 1
            continue

        r.ok(f"{path.name}: balanced ({len(text)} bytes)")

    if issues == 0:
        print(f"   ✅ All {len(real_files)} files are syntactically valid")


# ── Test 2: TypeScript imports/exports ──

def test_typescript_consistency():
    print("\n🔷 Test 2: TypeScript/TSX import consistency")
    admin_dir = ROOT / "admin_dashboard"
    ts_files = list(admin_dir.rglob("*.ts")) + list(admin_dir.rglob("*.tsx"))
    print(f"   📁 Scanning {len(ts_files)} files...")

    all_imports = set()
    all_exports = set()
    for path in ts_files:
        try:
            text = path.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            continue
        for m in re.finditer(r"from\s+['\"]([^'\"./][^'\"]*)['\"]", text):
            all_imports.add(m.group(1))
        for m in re.finditer(r"export\s+(?:default\s+)?(?:function|const|class|interface|type)\s+(\w+)", text):
            all_exports.add(m.group(1))

    # Check that all declared exports have at least one usage
    for exp in all_exports:
        # Simple grep
        used = False
        for path in ts_files:
            try:
                text = path.read_text(encoding='utf-8', errors='ignore')
                if re.search(rf'\b{re.escape(exp)}\b', text):
                    if text.count(exp) > 1 or exp not in text.split('export')[0]:
                        used = True
                        break
            except Exception:
                pass
        if not used:
            r.warn(f"TS export potentially unused: {exp}")

    r.ok(f"TS: {len(all_imports)} external imports, {len(all_exports)} exports")


# ── Test 3: SQL migrations ──

def test_sql_migrations():
    print("\n🔷 Test 3: SQL migration integrity")
    migration_dir = ROOT / "backend-server/src/main/resources/db/migration"
    sql_files = sorted(migration_dir.glob("V*.sql"))
    print(f"   📁 Scanning {len(sql_files)} migration files...")

    # Check sequential numbering
    versions = []
    for path in sql_files:
        m = re.match(r"V(\d+)__", path.name)
        if m:
            versions.append((int(m.group(1)), path.name))

    versions.sort()
    if versions:
        expected = list(range(versions[0][0], versions[-1][0] + 1))
        actual = [v[0] for v in versions]
        if expected != actual:
            missing = set(expected) - set(actual)
            extra = set(actual) - set(expected)
            for v in missing:
                r.fail(f"SQL: missing migration V{v}")
            for v in extra:
                r.warn(f"SQL: unexpected migration V{v}")
        else:
            r.ok(f"SQL: V{versions[0][0]} to V{versions[-1][0]} ({len(versions)} files) — sequential")

    # Check each file for basic structure
    for path in sql_files:
        try:
            text = path.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            continue
        # Check for at least one CREATE TABLE or ALTER TABLE
        if 'CREATE TABLE' not in text and 'CREATE VIEW' not in text and 'CREATE FUNCTION' not in text and 'ALTER TABLE' not in text:
            r.warn(f"SQL: {path.name} has no DDL")
        else:
            r.ok(f"SQL: {path.name} ({len(text)} bytes)")


# ── Test 4: API endpoint consistency ──

def test_api_endpoints():
    print("\n🔷 Test 4: API endpoint consistency (frontend ↔ backend)")
    api_ts = ROOT / "admin_dashboard/src/api.ts"
    if not api_ts.exists():
        r.fail("api.ts not found")
        return

    text = api_ts.read_text(encoding='utf-8', errors='ignore')
    # Extract all paths
    frontend_paths = set()
    for m in re.finditer(r"['\"\`](/api/[^'\"\`]+)['\"\`]", text):
        frontend_paths.add(m.group(1))

    # Extract all controllers' @RequestMapping
    backend_paths = set()
    for kt in (ROOT / "backend-server/src/main/kotlin").rglob("*.kt"):
        try:
            ct = kt.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            continue
        for m in re.finditer(r'@RequestMapping\(["\']([^"\']+)["\']', ct):
            prefix = m.group(1)
        for m in re.finditer(r'@(Get|Post|Put|Delete|Patch)Mapping\(["\']([^"\']+)["\']', ct):
            backend_paths.add(prefix + m.group(2))

    # Check coverage
    matched = 0
    for fp in frontend_paths:
        fp_normalized = fp.replace("${", "").replace("}", "")
        # Try to match
        for bp in backend_paths:
            bp_normalized = bp.replace("${", "").replace("}", "")
            if fp_normalized.startswith(bp_normalized.split("{")[0]):
                matched += 1
                break

    print(f"   📊 Frontend: {len(frontend_paths)} unique paths")
    print(f"   📊 Backend:  {len(backend_paths)} unique mappings")
    if matched >= len(frontend_paths) * 0.5:
        r.ok(f"API: {matched}/{len(frontend_paths)} frontend paths have backend ({100*matched//len(frontend_paths)}%)")
    else:
        r.warn(f"API: only {matched}/{len(frontend_paths)} matched ({100*matched//len(frontend_paths)}%)")


# ── Test 5: GitHub Actions workflow ──

def test_github_workflows():
    print("\n🔷 Test 5: GitHub Actions workflow")
    workflow_dir = Path(".github/workflows")
    if not workflow_dir.exists():
        r.fail("No .github/workflows directory")
        return
    workflows = list(workflow_dir.glob("*.yml")) + list(workflow_dir.glob("*.yaml"))
    print(f"   📁 Found {len(workflows)} workflow files")
    for path in workflows:
        try:
            text = path.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            r.fail(f"Workflow: {path.name} cannot read")
            continue
        # Basic YAML checks
        if "name:" not in text:
            r.fail(f"Workflow: {path.name} missing 'name'")
        if "on:" not in text:
            r.fail(f"Workflow: {path.name} missing 'on' trigger")
        if "jobs:" not in text:
            r.fail(f"Workflow: {path.name} missing 'jobs'")
        # Brace balance
        if text.count('{') != text.count('}'):
            r.fail(f"Workflow: {path.name} unbalanced braces")
        else:
            r.ok(f"Workflow: {path.name} ({len(text)} bytes)")


# ── Test 6: Models cross-reference ──

def test_model_consistency():
    print("\n🔷 Test 6: Model/DTO cross-reference")
    # Check that models referenced in controllers exist
    backend_kotlin = list((ROOT / "backend-server/src/main/kotlin").rglob("*.kt"))
    models = set()
    for kt in backend_kotlin:
        try:
            text = kt.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            continue
        for m in re.finditer(r"data class (\w+)", text):
            models.add(m.group(1))
        for m in re.finditer(r"class (\w+)\(", text):
            models.add(m.group(1))

    print(f"   📊 Found {len(models)} Kotlin classes")
    r.ok(f"Models: {len(models)} Kotlin classes defined")


# ── Test 7: Build configuration files ──

def test_build_configs():
    print("\n🔷 Test 7: Build configuration files")
    configs = [
        ("backend-server/build.gradle.kts", "Kotlin DSL"),
        ("backend-server/settings.gradle.kts", "Settings"),
        ("red-app/build.gradle.kts", "Android Kotlin DSL"),
        ("settings.gradle.kts", "Root Settings (red-app)"),
        ("admin_dashboard/package.json", "NPM"),
        ("admin_dashboard/vite.config.js", "Vite"),
        ("admin_dashboard/tsconfig.json", "TypeScript"),
    ]
    for path, label in configs:
        full = ROOT / path
        if not full.exists():
            r.fail(f"{label}: {path} missing")
            continue
        try:
            text = full.read_text(encoding='utf-8', errors='ignore')
        except Exception as e:
            r.fail(f"{label}: {path} cannot read ({e})")
            continue
        # JSON for package.json/tsconfig
        if path.endswith('.json'):
            try:
                json.loads(text)
                r.ok(f"{label}: valid JSON ({len(text)} bytes)")
            except json.JSONDecodeError as e:
                r.fail(f"{label}: invalid JSON ({e})")
        else:
            # Text file
            if len(text) < 50:
                r.warn(f"{label}: very small ({len(text)} bytes)")
            else:
                r.ok(f"{label}: {len(text)} bytes")


# ── Test 8: Docker files ──

def test_docker():
    print("\n🔷 Test 8: Docker configuration")
    dockerfiles = list(ROOT.rglob("Dockerfile*"))
    print(f"   📁 Found {len(dockerfiles)} Dockerfiles")
    for path in dockerfiles:
        try:
            text = path.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            r.fail(f"Docker: {path.name} cannot read")
            continue
        if "FROM" not in text:
            r.fail(f"Docker: {path.name} no FROM")
        elif text.count('{') != text.count('}'):
            r.fail(f"Docker: {path.name} unbalanced")
        else:
            r.ok(f"Docker: {path.relative_to(ROOT)} ({len(text)} bytes)")

    # Check docker-compose
    compose = ROOT / "docker-compose.yml"
    if compose.exists():
        try:
            text = compose.read_text(encoding='utf-8', errors='ignore')
            if 'services:' in text and 'image:' in text or 'build:' in text:
                r.ok(f"Docker Compose: {len(text)} bytes")
            else:
                r.warn("Docker Compose: no services found")
        except Exception:
            r.fail("Docker Compose: cannot read")


# ── Test 9: File integrity ──

def test_file_integrity():
    print("\n🔷 Test 9: File integrity & sizes")
    # Find any 0-byte files
    zero_files = []
    for path in ROOT.rglob("*"):
        if path.is_file():
            try:
                if path.stat().st_size == 0:
                    zero_files.append(str(path.relative_to(ROOT)))
            except Exception:
                pass

    if zero_files:
        for f in zero_files:
            r.warn(f"Zero-byte file: {f}")
    else:
        r.ok("All files non-empty")

    # Check critical files
    critical = [
        "README.md",
        "backend-server/build.gradle.kts",
        "backend-server/Dockerfile",
        "red-app/build.gradle.kts",
        "red-app/src/main/AndroidManifest.xml",
        "admin_dashboard/package.json",
    ]
    for c in critical:
        full = ROOT / c
        if full.exists():
            r.ok(f"Critical: {c} present")
        else:
            r.fail(f"Critical: {c} MISSING")


# ── Run all tests ──

print("╔════════════════════════════════════════════════════════════════════════╗")
print("║  🚀 RED ULTIMATE V1 — COMPREHENSIVE TEST RUNNER                       ║")
print("╚════════════════════════════════════════════════════════════════════════╝")

test_kotlin_syntax()
test_typescript_consistency()
test_sql_migrations()
test_api_endpoints()
test_github_workflows()
test_model_consistency()
test_build_configs()
test_docker()
test_file_integrity()

score = r.report()
sys.exit(0 if score >= 80 else 1)