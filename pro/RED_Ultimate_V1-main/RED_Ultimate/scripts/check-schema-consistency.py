#!/usr/bin/env python3
"""
فاحص تطابق الكيانات (JPA/Hibernate) مع مخطط قاعدة البيانات (Flyway)
--------------------------------------------------------------------
يمنع انهيار الخادم وقت الإقلاع بسبب `ddl-auto: validate`:
يقارن أعمدة @Table/@Column/@JoinColumn في Kotlin entities مع
CREATE TABLE / ALTER TABLE ADD COLUMN في backend-server/src/main/resources/db/migration.

التشغيل (من جذر RED_Ultimate):
    python3 scripts/check-schema-consistency.py
"""
import re
import glob
import os
import sys

BACKEND = "backend-server/src/main"
MIGRATION_DIR = os.path.join(BACKEND, "resources/db/migration")
MODEL_BASE = os.path.join(BACKEND, "kotlin/com/red/server")


def db_columns(table: str) -> set:
    cols = set()
    for f in sorted(glob.glob(os.path.join(MIGRATION_DIR, "V*.sql"))):
        text = open(f, encoding="utf-8").read()
        m = re.search(r'CREATE TABLE (?:IF NOT EXISTS )?' + table + r'\s*\((.*?)\)\s*;', text, re.S)
        if m:
            for line in m.group(1).splitlines():
                line = line.strip().rstrip(",")
                if re.match(r'^[a-z_]+ ', line) and not line.upper().startswith(
                    ("CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN")
                ):
                    cols.add(line.split()[0])
        for am in re.finditer(
            r'ALTER TABLE (?:IF EXISTS )?' + table + r'\s+ADD\s+(?:COLUMN\s+)?(?:IF NOT EXISTS\s+)?([a-z_]+)',
            text, re.I,
        ):
            cols.add(am.group(1))
    return cols


def snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def entity_columns(path: str) -> set:
    text = open(path, encoding="utf-8").read()
    cols = set()
    text = re.sub(
        r'@Column\(name = "([a-z_]+)"[^)]*\)\s*var [a-zA-Z]+:',
        lambda m: "COL:" + m.group(1), text,
    )
    for m in re.finditer(r"COL:([a-z_]+)", text):
        cols.add(m.group(1))
    text = re.sub(
        r'@(?:ManyToOne|OneToOne|ManyToMany)[^)]*\)\s*@JoinColumn\(name = "([a-z_]+)"[^)]*\)\s*var [a-zA-Z]+:',
        lambda m: "REL:" + m.group(1), text,
    )
    for m in re.finditer(r"REL:([a-z_]+)", text):
        cols.add(m.group(1))
    for m in re.finditer(r"var ([a-zA-Z]+):", text):
        cols.add(snake(m.group(1)))
    return cols


def main() -> int:
    models = {}
    for dp, _dn, fn in os.walk(MODEL_BASE):
        for f in fn:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(dp, f)
            content = open(path, encoding="utf-8").read()
            for m in re.finditer(r'@Table\(name = "([a-z_]+)"\)', content):
                models.setdefault(m.group(1), []).append(os.path.relpath(path, MODEL_BASE))

    problems = 0
    for table, files in sorted(models.items()):
        cols = db_columns(table)
        entity_cols = set()
        for f in files:
            entity_cols |= entity_columns(os.path.join(MODEL_BASE, f))
        missing = (entity_cols - cols) - {"id"}
        if missing:
            problems += 1
            print(f"❌ {table}: أعمدة كيان غير موجودة في DB: {sorted(missing)}")
        else:
            print(f"✅ {table}: متطابق ({len(files)} كيان)")

    print("\nالنتيجة:", "سليم ✅" if problems == 0 else f"فيه {problems} مشكلة ❌")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
