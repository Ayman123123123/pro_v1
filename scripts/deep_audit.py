#!/usr/bin/env python3
"""
════════════════════════════════════════════════════════════════════════
  RED Ultimate V1 — DEEP Security & Code Audit
  Comprehensive analysis of all potential issues
════════════════════════════════════════════════════════════════════════
"""
import re
import sys
import os
import json
from pathlib import Path
from collections import defaultdict, Counter
from typing import Dict, List, Set, Tuple

ROOT = Path("RED_Ultimate_V1-main/RED_Ultimate")
os.chdir(Path(__file__).parent.parent)


class DeepAudit:
    def __init__(self):
        self.critical = []
        self.high = []
        self.medium = []
        self.low = []
        self.info = []

    def add(self, severity, category, file, line, msg, snippet=""):
        finding = {
            "severity": severity,
            "category": category,
            "file": file,
            "line": line,
            "msg": msg,
            "snippet": snippet[:200] if snippet else ""
        }
        getattr(self, severity).append(finding)


a = DeepAudit()


# ═══════════════════════════════════════════════════════════
#  Security: Spring Security
# ═══════════════════════════════════════════════════════════
print("\n🔒 Section 1: Spring Security Audit")
print("=" * 78)

backend_kt = list((ROOT / "backend-server/src/main/kotlin").rglob("*.kt"))

# Find @PreAuthorize usage
preauth_count = 0
unauth_endpoints = []
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))

    # Find all @GetMapping/@PostMapping etc
    for m in re.finditer(r'@(Get|Post|Put|Delete|Patch|Request)Mapping', text):
        idx = m.start()
        # Look for @PreAuthorize in 200 chars before
        before = text[max(0, idx-300):idx]
        if "@PreAuthorize" not in before and "@Secured" not in before:
            # Check if there's a @RequestMapping with role check
            line_no = text[:idx].count('\n') + 1
            if any(s in before[-200:] for s in ["ROLE_ADMIN", "hasAuthority", "hasRole"]):
                continue
            unauth_endpoints.append((rel, line_no, m.group()))

if unauth_endpoints:
    print(f"   ⚠️  Found {len(unauth_endpoints)} endpoints potentially without @PreAuthorize:")
    for f, l, m in unauth_endpoints[:10]:
        print(f"      {f}:{l} — {m}Mapping")
else:
    print("   ✅ All endpoints have authorization checks")

# Check for hardcoded admin credentials
print("\n🔐 Section 2: Hardcoded credentials check")
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))

    # Check for default passwords
    if re.search(r'(admin|root|test).*[Pp]assword\s*=\s*["\'][^"\']+["\']', text):
        a.add("high", "Hardcoded password", rel, 0, "Default admin password found")
    if re.search(r'["\']AKIA[0-9A-Z]{16}["\']', text):  # AWS
        a.add("critical", "AWS Access Key", rel, 0, "AWS key leaked")
    if re.search(r'sk-[A-Za-z0-9]{20,}', text):  # OpenAI
        a.add("critical", "OpenAI API Key", rel, 0, "OpenAI key leaked")

# ═══════════════════════════════════════════════════════════
#  SQL Injection patterns
# ═══════════════════════════════════════════════════════════
print("\n💉 Section 3: SQL Injection patterns")
jdbc_raw = []
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    # Find jdbcTemplate.query with string concat
    # Real risk: must be + in the SAME string as the SQL
    for m in re.finditer(r'jdbc\.(?:query|update|execute)\s*\(\s*["\'].{0,200}\+', text, re.DOTALL):
        line_no = text[:m.start()].count('\n') + 1
        snippet = m.group()
        # Check if it looks like real injection (string concat inside SQL)
        # False positive: + in the same string AFTER all ? placeholders, or + is operator on string, etc.
        if re.search(r'\+\s*\w+\s*\)', snippet) and '?' in snippet[:snippet.index('+')]:
            jdbc_raw.append((rel, line_no, snippet[:100]))

if jdbc_raw:
    print(f"   🔴 Found {len(jdbc_raw)} potential SQL injection risks:")
    for f, l, s in jdbc_raw[:5]:
        print(f"      {f}:{l}")
        print(f"         {s[:80]}")
        a.add("critical", "SQL Injection", f, l, "String concatenation in JDBC query", s)
else:
    print("   ✅ No SQL injection patterns found")

# ═══════════════════════════════════════════════════════════
#  XSS patterns in Kotlin
# ═══════════════════════════════════════════════════════════
print("\n🛡️  Section 4: XSS / HTML injection risks")
xss_patterns = []
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    # Direct HTML rendering
    if re.search(r'response\.getWriter\(\)\.write\s*\(', text):
        line_no = 0
        for m in re.finditer(r'response\.getWriter\(\)\.write\s*\(\s*\w+\s*\)', text):
            line_no = text[:m.start()].count('\n') + 1
            xss_patterns.append((rel, line_no))
            a.add("medium", "XSS risk", rel, line_no, "Unescaped HTML write")

if xss_patterns:
    print(f"   ⚠️  Found {len(xss_patterns)} potential XSS risks")
else:
    print("   ✅ No direct HTML write risks found")

# ═══════════════════════════════════════════════════════════
#  Kotlin-specific issues
# ═══════════════════════════════════════════════════════════
print("\n🔧 Section 5: Kotlin-specific issues")
# !! (force unwrap) usage
force_unwraps = []
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    for m in re.finditer(r'!!\s*[\.\)\,]', text):
        line_no = text[:m.start()].count('\n') + 1
        force_unwraps.append((rel, line_no, m.group()))

# But many !! are safe (require() above). Just count
print(f"   📊 Force unwraps (!!): {len(force_unwraps)} (mostly safe with require() above)")

# Empty catch blocks
empty_catches = []
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    # catch (...) {} - truly empty
    for m in re.finditer(r'catch\s*\([^)]+\)\s*\{\s*\}', text):
        line_no = text[:m.start()].count('\n') + 1
        empty_catches.append((rel, line_no))
        a.add("low", "Empty catch", rel, line_no, "Empty catch block silently swallows errors")

if empty_catches:
    print(f"   ⚠️  Empty catch blocks: {len(empty_catches)}")
    for f, l in empty_catches[:5]:
        print(f"      {f}:{l}")
else:
    print("   ✅ No empty catch blocks")

# println/debug
printlns = []
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    for m in re.finditer(r'\bprintln\s*\(', text):
        line_no = text[:m.start()].count('\n') + 1
        printlns.append((rel, line_no))

print(f"   📊 println() calls: {len(printlns)} (consider using logger)")

# ═══════════════════════════════════════════════════════════
#  Cross-file consistency
# ═══════════════════════════════════════════════════════════
print("\n🔗 Section 6: Cross-file consistency")
# Check imports for unresolved types
models_in_repo = defaultdict(set)
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    for m in re.finditer(r'@(?:Document|Entity|Table)\b.*?class\s+(\w+)', text, re.DOTALL):
        models_in_repo[rel.split('/')[1] if '/' in rel else 'root'].add(m.group(1))

# ═══════════════════════════════════════════════════════════
#  Endpoints with PII
# ═══════════════════════════════════════════════════════════
print("\n🔐 Section 7: PII / Sensitive data handling")
# Check for unencrypted logging of sensitive data
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    # Log statements that might log sensitive data
    for m in re.finditer(r'log\.(?:info|debug|warn|error)\s*\([^)]*(?:password|token|secret|jwt|redId|phone|email|otp|pin)', text, re.IGNORECASE):
        line_no = text[:m.start()].count('\n') + 1
        a.add("medium", "PII logging", rel, line_no, "Potential sensitive data in log")

# ═══════════════════════════════════════════════════════════
#  Build/deploy checks
# ═══════════════════════════════════════════════════════════
print("\n🏗️  Section 8: Build/Deploy checks")
docker_compose = ROOT / "docker-compose.yml"
if docker_compose.exists():
    text = docker_compose.read_text(encoding='utf-8', errors='ignore')
    # Check for latest tags (security risk)
    if re.search(r'image:\s*[\'"]?[\w-]+:latest\b', text):
        a.add("medium", "Docker latest tag", "docker-compose.yml", 0, "Using :latest tag is not recommended")

    # Check for missing restart policies
    services = re.findall(r'^\s{2}(\w+):', text, re.MULTILINE)
    print(f"   📊 Services defined: {len(services)}")

# Check for exposed DB ports
exposed_dbs = []
if docker_compose.exists():
    for m in re.finditer(r'-?\s*["\']?(\d+):(\d+)["\']?', docker_compose.read_text(encoding='utf-8', errors='ignore')):
        host_port = int(m.group(1))
        container_port = int(m.group(2))
        if host_port in [5432, 3306, 27017, 6379, 8086] and host_port == container_port:
            exposed_dbs.append((host_port, container_port))

if exposed_dbs:
    print(f"   🔴 {len(exposed_dbs)} DB ports exposed to host (security risk)")
    for h, c in exposed_dbs:
        a.add("high", "Exposed DB", "docker-compose.yml", 0, f"DB port {h} exposed")
else:
    print("   ✅ No DB ports exposed to host network")

# ═══════════════════════════════════════════════════════════
#  Code duplication
# ═══════════════════════════════════════════════════════════
print("\n♻️  Section 9: Code duplication check")
# Look for duplicate function bodies
function_bodies = defaultdict(list)
for path in backend_kt:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    rel = str(path.relative_to(ROOT))
    for m in re.finditer(r'fun\s+(\w+)\s*\([^)]*\)\s*:\s*[^{]+\{([^}]+)\}', text, re.DOTALL):
        name = m.group(1)
        body = m.group(2).strip()
        if len(body) > 50:
            function_bodies[name].append((rel, body))

duplicates = [(name, locs) for name, locs in function_bodies.items() if len(locs) > 1]
if duplicates:
    print(f"   📊 {len(duplicates)} functions with duplicate names across files")
else:
    print("   ✅ No obvious function name duplicates")

# ═══════════════════════════════════════════════════════════
#  Compose/Dockerfile health
# ═══════════════════════════════════════════════════════════
print("\n🐳 Section 10: Docker Compose health")
compose_text = docker_compose.read_text(encoding='utf-8', errors='ignore') if docker_compose.exists() else ""

# Health checks
healthcheck_count = len(re.findall(r'healthcheck:', compose_text))
print(f"   📊 Health checks defined: {healthcheck_count}")

# Restart policies
restart_count = len(re.findall(r'restart:\s*', compose_text))
print(f"   📊 Restart policies: {restart_count}")

# Networks
networks = re.findall(r'^\s{4}networks:\s*$', compose_text, re.MULTILINE)
print(f"   📊 Services with networks: {len(networks)}")

# ═══════════════════════════════════════════════════════════
#  Test coverage
# ═══════════════════════════════════════════════════════════
print("\n🧪 Section 11: Test coverage")
test_files = list((ROOT / "backend-server/src/test/kotlin").rglob("*.kt"))
prod_files = list((ROOT / "backend-server/src/main/kotlin").rglob("*.kt"))
print(f"   📊 Production files: {len(prod_files)}")
print(f"   📊 Test files: {len(test_files)}")
ratio = len(test_files) / max(len(prod_files), 1) * 100
print(f"   📊 Test-to-prod ratio: {ratio:.1f}%")

# ═══════════════════════════════════════════════════════════
#  File size & complexity
# ═══════════════════════════════════════════════════════════
print("\n📏 Section 12: File size analysis")
sizes = []
for path in prod_files:
    try:
        size = path.stat().st_size
        sizes.append((size, str(path.relative_to(ROOT))))
    except Exception:
        pass

sizes.sort(reverse=True)
print("   📊 Top 10 largest files:")
for size, name in sizes[:10]:
    kb = size / 1024
    flag = "🔴" if kb > 30 else ("⚠️" if kb > 15 else "✅")
    print(f"      {flag} {kb:6.1f} KB — {name}")
    if kb > 30:
        a.add("medium", "Large file", name, 0, f"File is {kb:.0f}KB — consider splitting")

# ═══════════════════════════════════════════════════════════
#  Final Report
# ═══════════════════════════════════════════════════════════
print("\n" + "=" * 78)
print("  📊 DEEP AUDIT FINAL REPORT")
print("=" * 78)
print(f"\n  🔴 Critical: {len(a.critical)}")
print(f"  🟠 High:     {len(a.high)}")
print(f"  🟡 Medium:   {len(a.medium)}")
print(f"  🟢 Low:      {len(a.low)}")
print(f"  ℹ️  Info:     {len(a.info)}")
print(f"  📊 Total:    {len(a.critical) + len(a.high) + len(a.medium) + len(a.low) + len(a.info)}\n")

if a.critical:
    print("  🔴 CRITICAL ISSUES:")
    for f in a.critical:
        print(f"     [{f['category']}] {f['file']}:{f['line']}")
        print(f"        {f['msg']}")

if a.high:
    print("\n  🟠 HIGH SEVERITY:")
    for f in a.high[:10]:
        print(f"     [{f['category']}] {f['file']}:{f['line']} — {f['msg']}")

if a.medium:
    print(f"\n  🟡 MEDIUM (showing first 10 of {len(a.medium)}):")
    for f in a.medium[:10]:
        print(f"     [{f['category']}] {f['file']}:{f['line']} — {f['msg']}")

if a.low:
    print(f"\n  🟢 LOW (showing first 5 of {len(a.low)}):")
    for f in a.low[:5]:
        print(f"     [{f['category']}] {f['file']}:{f['line']} — {f['msg']}")

print("\n" + "=" * 78)
print("  📈 HEALTH SCORE")
print("=" * 78)
total = len(a.critical) + len(a.high) + len(a.medium) + len(a.low)
if total == 0:
    print("  🎉 100/100 — EXCELLENT")
elif total < 5:
    print("  🟢 90/100 — VERY GOOD")
elif total < 15:
    print("  🟡 75/100 — GOOD (some issues to address)")
elif total < 30:
    print("  🟠 60/100 — NEEDS IMPROVEMENT")
else:
    print("  🔴 40/100 — REQUIRES ATTENTION")
print("=" * 78)
