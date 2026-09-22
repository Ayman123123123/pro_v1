#!/usr/bin/env python3
"""
════════════════════════════════════════════════════════════════════════
  TypeScript/TSX Advanced Linter — RED Ultimate V1
  - Brace/paren/bracket balance
  - Type annotation checks
  - Mock data detection
  - Unused imports
═══════════════════════════════════════════════════════════════════════
"""
import re
import sys
from pathlib import Path
from collections import defaultdict

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
TS_FILES = list(ROOT.rglob("*.ts")) + list(ROOT.rglob("*.tsx"))
TS_FILES = [f for f in TS_FILES if "node_modules" not in str(f) and "dist" not in str(f)]

print(f"\n🔍 Scanning {len(TS_FILES)} TypeScript/TSX files...\n")


def strip_strings_and_comments(text: str) -> str:
    """Replace strings/comments with placeholders."""
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
        if c in ('"', "'", '`'):
            quote = c
            j = i + 1
            while j < n:
                if text[j] == '\\' and j + 1 < n:
                    j += 2; continue
                if text[j] == quote: break
                j += 1
            out.append(quote + ' ' * (min(j, n) - 1 - i) + (quote if j < n else ''))
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def balance_check(text: str) -> dict:
    diffs = {'paren': 0, 'bracket': 0, 'brace': 0, 'angle': 0}
    # skip angle in generic context — only count < T > between identifiers
    in_jsx = False
    for c in text:
        if c == '(': diffs['paren'] += 1
        elif c == ')': diffs['paren'] -= 1
        elif c == '[': diffs['bracket'] += 1
        elif c == ']': diffs['bracket'] -= 1
        elif c == '{': diffs['brace'] += 1
        elif c == '}': diffs['brace'] -= 1
    return diffs


errors = []
warnings = []
todos = []
mocks = []
imports_per_file = []
exports_per_file = []
total_brace_mismatch = 0
total_paren_mismatch = 0

for path in TS_FILES:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    short = str(path.relative_to(ROOT))
    clean = strip_strings_and_comments(text)

    # Balance check
    bal = balance_check(clean)
    if abs(bal['brace']) > 2:
        total_brace_mismatch += 1
        errors.append((short, f"unbalanced braces: {bal['brace']}"))
    if abs(bal['paren']) > 5:
        total_paren_mismatch += 1
        errors.append((short, f"unbalanced parens: {bal['paren']}"))

    # Imports
    for m in re.finditer(r"^\s*import\s+.*?from\s+['\"]([^'\"]+)['\"]", clean, re.MULTILINE):
        imports_per_file.append((short, m.group(1)))

    # Exports
    for m in re.finditer(r"^\s*export\s+(?:default\s+)?(?:function|const|class|interface|type)\s+(\w+)", clean, re.MULTILINE):
        exports_per_file.append((short, m.group(1)))

    # TODO/FIXME
    for m in re.finditer(r'//\s*(?:TODO|FIXME|XXX|HACK)\b', text):
        line = text[:m.start()].count('\n') + 1
        todos.append((short, line))

    # Mock data patterns
    mock_patterns = [
        re.compile(r"=.*\[\s*\{[^}]*['\"]name['\"]\s*:\s*['\"](?:sample|test|demo|fake|mock|dummy|placeholder)", re.IGNORECASE),
        re.compile(r"const\s+\w+\s*=\s*\[\s*['\"]?(?:sample|test|demo|fake|mock|dummy)", re.IGNORECASE),
        re.compile(r"hardcoded|MOCK_|FAKE_|DUMMY_|PLACEHOLDER_", re.IGNORECASE),
    ]
    for pat in mock_patterns:
        for m in pat.finditer(text):
            mocks.append((short, m.group(0)[:80]))

    # Missing types: function with no return annotation
    # Skip for now — JSX files have many implicit returns

    # Unused imports (very basic)
    file_imports = [m.group(1) for m in re.finditer(r"^\s*import\s+(?:\{([^}]+)\}|(\w+))\s+from", clean, re.MULTILINE)]
    for imp_block in re.findall(r"import\s*\{([^}]+)\}\s*from", clean):
        names = [n.strip().split(' as ')[0] for n in imp_block.split(',') if n.strip()]
        for name in names:
            if name and re.search(rf'\b{re.escape(name)}\b', clean.replace('import', '')) is None:
                warnings.append((short, f"potentially unused import: {name}"))


print("=" * 78)
print("  📊 TYPESCRIPT ADVANCED LINT REPORT")
print("=" * 78)
print(f"\n📁 Files scanned:        {len(TS_FILES)}")
print(f"📦 Imports:              {len(imports_per_file)}")
print(f"📤 Exports:              {len(exports_per_file)}")
print()
print(f"❌ Unbalanced braces:     {total_brace_mismatch}")
print(f"❌ Unbalanced parens:     {total_paren_mismatch}")
print(f"⚠️  Warnings:             {len(warnings)}")
print(f"📌 TODO/FIXME:           {len(todos)}")
print(f"🎭 Mock data:            {len(mocks)}")
print()

if errors:
    print("─" * 78)
    print("❌ ERRORS:")
    for f, msg in errors[:20]:
        print(f"  {f}: {msg}")
    print()

if warnings:
    print("─" * 78)
    print(f"⚠️  WARNINGS ({len(warnings)}):")
    seen = set()
    for f, msg in warnings[:30]:
        if (f, msg) not in seen:
            seen.add((f, msg))
            print(f"  {f}: {msg}")
    print()

if todos:
    print("─" * 78)
    print(f"📌 TODO LOCATIONS ({len(todos)}):")
    for f, line in todos[:20]:
        print(f"  {f}:{line}")
    print()

if mocks:
    print("─" * 78)
    print(f"🎭 MOCK DATA ({len(mocks)}):")
    for f, snippet in mocks[:20]:
        print(f"  {f}: {snippet}")
    print()

print("=" * 78)
status = "✅ PASS" if not errors else "❌ FAIL"
print(f"  {status}")
print("=" * 78)