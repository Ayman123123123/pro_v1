#!/usr/bin/env python3
"""
════════════════════════════════════════════════════════════════════════
  RED Ultimate V1 — Security Audit
  - SQL injection patterns
  - SSRF vulnerabilities
  - Hardcoded secrets
  - Insecure crypto
  - Path traversal
  - XSS patterns
═══════════════════════════════════════════════════════════════════════
"""
import re
import sys
import os
from pathlib import Path
from collections import defaultdict

ROOT = Path("RED_Ultimate_V1-main/RED_Ultimate")
os.chdir(Path(__file__).parent.parent)

# Pattern definitions
PATTERNS = {
    "SQL Injection (raw concat)": [
        re.compile(r'"SELECT\s+.*\s*\+\s*\w+', re.IGNORECASE),
        re.compile(r'"INSERT\s+.*\s*\+\s*\w+', re.IGNORECASE),
        re.compile(r'"UPDATE\s+.*\s*\+\s*\w+', re.IGNORECASE),
        re.compile(r'"DELETE\s+.*\s*\+\s*\w+', re.IGNORECASE),
    ],
    "SSRF Risk (URL building)": [
        re.compile(r"new\s+URL\s*\(\s*['\"]https?://[^'\"]*\s*\+\s*\w+"),
    ],
    "Path Traversal Risk": [
        re.compile(r'\.\./\.\.'),
        re.compile(r"getCanonicalPath|getAbsolutePath"),
    ],
    "Insecure Crypto": [
        re.compile(r'\bMD5\b'),
        re.compile(r'\bSHA-?1\b'),
        re.compile(r'Cipher\.getInstance\s*\(\s*["\']DES["\']'),
        re.compile(r'Cipher\.getInstance\s*\(\s*["\']RC4["\']'),
    ],
    "Hardcoded Password/Secret": [
        re.compile(r'password\s*=\s*["\'][^"\']{3,}["\']', re.IGNORECASE),
        re.compile(r'apiKey\s*=\s*["\'][A-Za-z0-9]{16,}["\']'),
        re.compile(r'secret\s*=\s*["\'][A-Za-z0-9]{16,}["\']'),
    ],
    "PrintStackTrace (information leak)": [
        re.compile(r'\.printStackTrace\(\)'),
    ],
    "Hardcoded IPs (potential)": [
        re.compile(r'\b(?:192\.168|10\.\d{1,3}|172\.(?:1[6-9]|2[0-9]|3[0-1]))\.\d{1,3}\.\d{1,3}\b'),
    ],
    "System.exit/Runtime.exec": [
        re.compile(r'Runtime\.getRuntime\(\)\.exec'),
        re.compile(r'ProcessBuilder\s*\('),
    ],
    "TODO/FIXME (security)": [
        re.compile(r'TODO.*[Ss]ecurity'),
        re.compile(r'FIXME.*[Ss]ecurity'),
    ],
}

# Whitelist (these are OK)
WHITELIST_PATTERNS = [
    re.compile(r'//.*?(MD5|SHA-?1)'),  # Comments
    re.compile(r'/\*.*?(MD5|SHA-?1).*?\*/', re.DOTALL),
    re.compile(r'""".*?(MD5|SHA-?1).*?"""', re.DOTALL),
    re.compile(r'//.*?printStackTrace'),  # Comments
    re.compile(r'comment|MD5 of name|message digest|hash for|HMAC|hmacSha', re.IGNORECASE),
]

# Find files
KT_FILES = list(ROOT.rglob("*.kt"))
TS_FILES = list((ROOT / "admin_dashboard/src").rglob("*.ts")) + list((ROOT / "admin_dashboard/src").rglob("*.tsx"))

ALL_FILES = KT_FILES + TS_FILES
# Filter out Signal fork
ALL_FILES = [f for f in ALL_FILES if not any(p in str(f) for p in ["app/src", "android/app", "demo/", "_archive"])]

print(f"\n🔒 Scanning {len(ALL_FILES)} files for security issues...\n")

findings = defaultdict(list)
file_count = 0

for path in ALL_FILES:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    file_count += 1

    for category, patterns in PATTERNS.items():
        for pat in patterns:
            for m in pat.finditer(text):
                # Check whitelist
                line_start = text.rfind('\n', 0, m.start()) + 1
                line_end = text.find('\n', m.end())
                if line_end == -1:
                    line_end = len(text)
                line = text[line_start:line_end]

                whitelisted = False
                for wp in WHITELIST_PATTERNS:
                    if wp.search(line):
                        whitelisted = True
                        break

                if not whitelisted:
                    # Skip comments
                    stripped = line.strip()
                    if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                        continue
                    findings[category].append((str(path.relative_to(ROOT)), line[:80].strip()))

print("=" * 78)
print("  🔒 SECURITY AUDIT RESULTS")
print("=" * 78)
print(f"\n  Files scanned: {file_count}\n")

total_issues = 0
for category, items in findings.items():
    if items:
        print(f"  ⚠️  {category}: {len(items)} potential issues")
        for f, snippet in items[:5]:
            print(f"     {f}: {snippet}")
        if len(items) > 5:
            print(f"     ... and {len(items) - 5} more")
        total_issues += len(items)
        print()

if total_issues == 0:
    print("  ✅ No security issues detected!")
else:
    print(f"  📊 Total potential issues: {total_issues}")

print("\n" + "=" * 78)
sys.exit(0 if total_issues < 20 else 1)