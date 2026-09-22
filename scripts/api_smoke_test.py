#!/usr/bin/env python3
"""
════════════════════════════════════════════════════════════════════════
  RED Ultimate V1 — API Smoke Test (offline)
  - Parses all @RequestMapping, @GetMapping, etc.
  - Verifies path consistency
  - Generates curl commands for manual testing
  - Cross-references with frontend api.ts
═══════════════════════════════════════════════════════════════════════
"""
import re
import sys
import os
import json
from pathlib import Path
from collections import defaultdict, OrderedDict
from typing import Dict, List, Set, Tuple

ROOT = Path("RED_Ultimate_V1-main/RED_Ultimate")
os.chdir(Path(__file__).parent.parent)


def parse_backend_endpoints() -> Dict[str, List[Dict]]:
    """Parse all Spring Boot endpoints from Kotlin controllers."""
    backend_kotlin = list((ROOT / "backend-server/src/main/kotlin").rglob("*.kt"))
    controllers = {}

    for kt in backend_kotlin:
        try:
            text = kt.read_text(encoding='utf-8', errors='ignore')
        except Exception:
            continue

        # Find class with @RequestMapping
        class_match = re.search(
            r'@(RestController|Controller)\s*\n\s*@RequestMapping\(["\']([^"\']+)["\']',
            text
        )
        if not class_match:
            continue

        prefix = class_match.group(2)
        class_name_match = re.search(r'class\s+(\w+)', text[class_match.end():text.find('{', class_match.end()) + class_match.end()])
        if not class_name_match:
            continue
        class_name = class_name_match.group(1)

        endpoints = []
        # Find all @*Mapping methods (with or without path / parens)
        method_pattern = re.compile(
            r'@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(\s*(?:value\s*=\s*)?["\']?([^"\')\s]*)["\']?\s*\))?',
            re.MULTILINE
        )
        for m in method_pattern.finditer(text):
            method = m.group(1).upper()
            path = m.group(2) or ""
            full_path = prefix + path
            endpoints.append({"method": method, "path": full_path})

        if endpoints:
            controllers[class_name] = endpoints

    return controllers


def parse_frontend_endpoints() -> List[Dict]:
    """Parse all api calls from api.ts."""
    api_ts = ROOT / "admin_dashboard/src/api.ts"
    if not api_ts.exists():
        return []
    text = api_ts.read_text(encoding='utf-8', errors='ignore')

    calls = []
    # Find function calls like apiFetch('/api/...')
    for m in re.finditer(
        r"apiFetch\s*\(\s*['\"\`](/[^'\"\`]+)['\"\`]\s*(?:,\s*\{[^}]*method\s*:\s*['\"\`]([A-Z]+)['\"\`])?",
        text
    ):
        calls.append({
            "path": m.group(1),
            "method": m.group(2) or "GET"
        })
    return calls


print("\n╔════════════════════════════════════════════════════════════════════════╗")
print("║  🔌 RED ULTIMATE V1 — API ENDPOINT ANALYZER                          ║")
print("╚════════════════════════════════════════════════════════════════════════╝\n")

print("📂 Parsing backend controllers...")
controllers = parse_backend_endpoints()

print(f"   Found {len(controllers)} controllers")
total_endpoints = sum(len(e) for e in controllers.values())
print(f"   Total endpoints: {total_endpoints}\n")

# Group by method
method_counts = defaultdict(int)
for cls, endpoints in controllers.items():
    for e in endpoints:
        method_counts[e["method"]] += 1

print("📊 Endpoints by HTTP method:")
for m, c in sorted(method_counts.items(), key=lambda x: -x[1]):
    print(f"   {m:7s} {c:4d}")
print()

# Print all controllers
print("📋 Controllers:")
for cls, endpoints in sorted(controllers.items()):
    print(f"   {cls}: {len(endpoints)} endpoints")
print()

# Cross-reference with frontend
print("🌐 Parsing frontend api.ts...")
frontend_calls = parse_frontend_endpoints()
print(f"   Found {len(frontend_calls)} api calls\n")

# Build set of backend paths
backend_paths = defaultdict(set)  # path -> set of methods
for cls, endpoints in controllers.items():
    for e in endpoints:
        # Normalize path (remove {id} placeholders for matching)
        normalized = re.sub(r'\{[^}]+\}', '{id}', e["path"])
        backend_paths[normalized].add(e["method"])

# Check frontend coverage
print("🔍 Frontend → Backend coverage:")
matched = 0
unmatched = []
for call in frontend_calls:
    path = call["path"]
    method = call["method"]
    # Normalize
    # Replace ${...} interpolations
    path_clean = re.sub(r'\$\{[^}]+\}', 'X', path)
    path_clean = re.sub(r'\$\w+', 'X', path_clean)
    # Try to match
    found = False
    for bp, methods in backend_paths.items():
        bp_clean = re.sub(r'\{id\}', 'X', bp)
        path_base = path_clean.split('?')[0]
        bp_base = bp_clean.split('?')[0]
        if path_base == bp_base or path_base.startswith(bp_base) or path_clean.startswith(bp_base):
            if method in methods or "ANY" in methods:
                found = True
                matched += 1
                break
    if not found:
        unmatched.append((method, path))

print(f"   ✅ Matched: {matched}/{len(frontend_calls)} ({100*matched//max(len(frontend_calls),1)}%)")
if unmatched:
    print(f"   ⚠️  Unmatched: {len(unmatched)}")
    for m, p in unmatched[:15]:
        print(f"      {m:7s} {p}")
    if len(unmatched) > 15:
        print(f"      ... and {len(unmatched) - 15} more")
print()

# List admin endpoints specifically
print("🛡️  Admin V2 Controller endpoints:")
if "AdminV2Controller" in controllers:
    for e in controllers["AdminV2Controller"]:
        print(f"   {e['method']:7s} {e['path']}")
print()

print("📊 Content Controller endpoints:")
if "ContentController" in controllers:
    for e in controllers["ContentController"]:
        print(f"   {e['method']:7s} {e['path']}")
print()

# Health check
print("🏥 Health endpoints:")
for cls, endpoints in controllers.items():
    if 'health' in cls.lower() or 'monitor' in cls.lower():
        for e in endpoints:
            print(f"   {e['method']:7s} {e['path']}")
print()

print("=" * 78)
if not unmatched:
    print("  ✅ All frontend API calls have matching backend endpoints")
else:
    print(f"  📊 {matched}/{len(frontend_calls)} frontend calls matched ({100*matched//max(len(frontend_calls),1)}%)")
print("=" * 78)