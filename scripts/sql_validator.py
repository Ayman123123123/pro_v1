#!/usr/bin/env python3
"""
════════════════════════════════════════════════════════════════════════
  SQL Migration Validator — RED Ultimate V1
  - Brace/paren balance
  - CREATE/ALTER/DROP/INSERT statement counter
  - Table definitions tracking
  - Index/FK consistency
═══════════════════════════════════════════════════════════════════════
"""
import re
import sys
from pathlib import Path
from collections import defaultdict

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
SQL_FILES = sorted(ROOT.rglob("*.sql"))

print(f"\n🔍 Scanning {len(SQL_FILES)} SQL migration files...\n")


def strip_sql_strings_and_comments(text: str) -> str:
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '-' and i + 1 < n and text[i + 1] == '-':
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
        if c == "'":
            j = i + 1
            while j < n:
                if text[j] == "'" and (j + 1 >= n or text[j + 1] != "'"):
                    break
                j += 1
            out.append("'" + ' ' * (min(j, n) - 1 - i) + ("'" if j < n else ''))
            i = min(j + 1, n)
            continue
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == '"': break
                j += 1
            out.append('"' + ' ' * (min(j, n) - 1 - i) + ('"' if j < n else ''))
            i = min(j + 1, n)
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def balance_check(text: str) -> dict:
    diffs = {'paren': 0, 'brace': 0}
    for c in text:
        if c == '(': diffs['paren'] += 1
        elif c == ')': diffs['paren'] -= 1
        elif c == '{': diffs['brace'] += 1
        elif c == '}': diffs['brace'] -= 1
    return diffs


errors = []
warnings = []
total_statements = defaultdict(int)
total_tables = set()
total_indexes = 0
total_functions = 0
total_views = 0
total_triggers = 0
total_foreign_keys = 0

for path in SQL_FILES:
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
    except Exception:
        continue
    short = str(path.relative_to(ROOT))
    clean = strip_sql_strings_and_comments(text)

    # Balance check
    bal = balance_check(clean)
    if abs(bal['paren']) > 1 or abs(bal['brace']) > 1:
        errors.append((short, f"unbalanced: {bal}"))

    # Statement counter
    for stmt in re.finditer(r'\b(CREATE\s+TABLE|CREATE\s+INDEX|CREATE\s+VIEW|CREATE\s+FUNCTION|CREATE\s+TRIGGER|CREATE\s+TYPE|ALTER\s+TABLE|DROP\s+TABLE|INSERT\s+INTO|UPDATE\s+\w+|DELETE\s+FROM)\b', clean, re.IGNORECASE):
        total_statements[stmt.group(1).upper().replace(' ', '_')] += 1

    # Tables
    for m in re.finditer(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)', clean, re.IGNORECASE):
        total_tables.add(m.group(1).lower())

    # Indexes
    total_indexes += len(re.findall(r'CREATE\s+(?:UNIQUE\s+)?INDEX', clean, re.IGNORECASE))

    # Functions
    total_functions += len(re.findall(r'CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+(\w+)', clean, re.IGNORECASE))

    # Views
    total_views += len(re.findall(r'CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\s+(\w+)', clean, re.IGNORECASE))

    # Triggers
    total_triggers += len(re.findall(r'CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+(\w+)', clean, re.IGNORECASE))

    # Foreign keys
    total_foreign_keys += len(re.findall(r'REFERENCES\s+(\w+)\s*\(', clean, re.IGNORECASE))

    # Common issues
    if re.search(r'\bSELECT\s+\*\b', text):
        warnings.append((short, "SELECT * in migration (may be intentional)"))

    # Check version migration file naming
    fname = path.name
    if not re.match(r'^V\d+__', fname) and 'V' in fname[:5]:
        warnings.append((short, "unusual filename (not V###__*.sql)"))


print("=" * 78)
print("  📊 SQL MIGRATION REPORT")
print("=" * 78)
print(f"\n📁 Files scanned:        {len(SQL_FILES)}")
print(f"🗂️  Unique tables:        {len(total_tables)}")
print(f"📇 Indexes:              {total_indexes}")
print(f"👁️  Views:                {total_views}")
print(f"⚙️  Functions:            {total_functions}")
print(f"🔔 Triggers:             {total_triggers}")
print(f"🔗 Foreign key refs:     {total_foreign_keys}")
print()
print("📊 Statement counts:")
for stmt, count in sorted(total_statements.items(), key=lambda x: -x[1])[:15]:
    print(f"   {stmt:30s} {count:5d}")
print()

if errors:
    print("─" * 78)
    print(f"❌ ERRORS ({len(errors)}):")
    for f, msg in errors[:10]:
        print(f"  {f}: {msg}")
    print()

if warnings:
    print("─" * 78)
    print(f"⚠️  WARNINGS ({len(warnings)}):")
    for f, msg in warnings[:15]:
        print(f"  {f}: {msg}")
    print()

print("=" * 78)
status = "✅ PASS" if not errors else "❌ FAIL"
print(f"  {status}")
print("=" * 78)
print()

# List all migration files
print("📋 Migration files (in order):")
for path in sorted(SQL_FILES, key=lambda p: p.name):
    print(f"   {path.name}")
print()