#!/usr/bin/env python3
"""
فاحص تطابق الكيانات (JPA/Hibernate) مع مخطط قاعدة البيانات (Flyway)
--------------------------------------------------------------------
يمنع انهيار الخادم وقت الإقلاع بسبب `ddl-auto: validate`.

النسخة السابقة كانت تجمع كل خصائص الملف لكل جدول، وهذا يعطي أخطاء كاذبة عندما
يحتوي ملف Kotlin واحد عدة كيانات. هذه النسخة تفحص كتلة كل data class المرتبطة
بـ @Table بشكل مستقل.
"""
import glob
import os
import re
import sys

BACKEND = "backend-server/src/main"
MIGRATION_DIR = os.path.join(BACKEND, "resources/db/migration")
MODEL_BASE = os.path.join(BACKEND, "kotlin/com/red/server")

SKIP_PROPERTIES = {
    "id",  # كل الكيانات لها id غالبًا من @Id؛ وجوده متحقق ضمن CREATE/ALTER أو مقبول تاريخيًا
}


def db_columns(table: str) -> set[str]:
    cols: set[str] = set()
    create_re = re.compile(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?' + re.escape(table) + r'\s*\(', re.I)
    for f in sorted(glob.glob(os.path.join(MIGRATION_DIR, "V*.sql"))):
        text = open(f, encoding="utf-8").read()
        for m in create_re.finditer(text):
            open_idx = text.find('(', m.start())
            close_idx = find_matching_paren(text, open_idx)
            body = text[open_idx + 1:close_idx]
            for line in body.splitlines():
                line = re.sub(r'--.*$', '', line).strip().rstrip(",")
                first = line.split()[0].upper() if line.split() else ""
                if re.match(r'^[a-z_]+\s+', line) and first not in {
                    "CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN", "KEY"
                }:
                    cols.add(line.split()[0])
        # ALTER TABLE ... ADD COLUMN — including the multi-column comma form:
        #   ALTER TABLE users
        #       ADD COLUMN IF NOT EXISTS a UUID ...,
        #       ADD COLUMN IF NOT EXISTS b INT,
        #       ADD COLUMN IF NOT EXISTS c VARCHAR(20);
        # Matching only one ADD per ALTER (the previous behaviour) silently missed
        # every column after the first, so V34's pstn_port_index / pstn_number
        # looked absent from the schema and failed this gate for a correct DB.
        for stmt in re.finditer(
            r'ALTER TABLE (?:IF EXISTS )?' + re.escape(table) + r'\b(.*?);',
            text,
            re.I | re.S,
        ):
            for am in re.finditer(
                r'ADD\s+(?:COLUMN\s+)?(?:IF NOT EXISTS\s+)?([a-z_]+)',
                stmt.group(1),
                re.I,
            ):
                name = am.group(1)
                # ADD CONSTRAINT / ADD PRIMARY KEY etc. are not columns.
                if name.upper() not in {
                    "CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN", "KEY", "COLUMN"
                }:
                    cols.add(name)
    return cols


def snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def find_matching_paren(text: str, open_index: int) -> int:
    depth = 0
    in_string = False
    escape = False
    for i in range(open_index, len(text)):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
    return len(text)


def entity_blocks(path: str):
    text = open(path, encoding="utf-8").read()
    table_re = re.compile(r'@Table\(name\s*=\s*"([a-z_]+)"[^)]*\)')
    for m in table_re.finditer(text):
        table = m.group(1)
        cls = re.search(r'(?:data\s+)?class\s+([A-Za-z0-9_]+)\s*\(', text[m.end():])
        if not cls:
            continue
        open_idx = m.end() + cls.end() - 1
        close_idx = find_matching_paren(text, open_idx)
        yield table, text[open_idx + 1:close_idx]


def entity_columns(block: str) -> set[str]:
    cols: set[str] = set()

    explicit_props: set[str] = set()
    relationship_props: set[str] = set()

    # @Column(name = "...") val/var prop:
    for m in re.finditer(r'@Column\([^)]*name\s*=\s*"([a-z_]+)"[^)]*\)(?:(?!@Column).)*?(?:var|val)\s+([A-Za-z0-9_]+)\s*:', block, re.S):
        cols.add(m.group(1))
        explicit_props.add(m.group(2))

    # @JoinColumn(name = "...") val/var prop:
    for m in re.finditer(r'@JoinColumn\([^)]*name\s*=\s*"([a-z_]+)"[^)]*\)(?:(?!@JoinColumn).)*?(?:var|val)\s+([A-Za-z0-9_]+)\s*:', block, re.S):
        cols.add(m.group(1))
        relationship_props.add(m.group(2))

    # Properties without explicit @Column. Skip transient/computed annotations.
    chunks = re.split(r'(?=\n\s*(?:@[A-Za-z]|(?:var|val)\s+))', block)
    for chunk in chunks:
        if '@Transient' in chunk or '@org.springframework.data.annotation.Transient' in chunk:
            continue
        pm = re.search(r'(?:var|val)\s+([A-Za-z0-9_]+)\s*:', chunk)
        if not pm:
            continue
        prop = pm.group(1)
        if prop in SKIP_PROPERTIES or prop in explicit_props or prop in relationship_props:
            continue
        # If explicit @Column existed, it has already been added under the DB name.
        if '@Column' in chunk and 'name' in chunk:
            continue
        if '@OneToMany' in chunk or '@ManyToMany' in chunk or '@ManyToOne' in chunk or '@OneToOne' in chunk:
            continue
        cols.add(snake(prop))
    return cols


def main() -> int:
    models: dict[str, list[tuple[str, set[str]]]] = {}
    for dp, _dn, fn in os.walk(MODEL_BASE):
        for f in fn:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(dp, f)
            rel = os.path.relpath(path, MODEL_BASE)
            for table, block in entity_blocks(path):
                models.setdefault(table, []).append((rel, entity_columns(block)))

    problems = 0
    for table, entries in sorted(models.items()):
        cols = db_columns(table)
        entity_cols: set[str] = set()
        for _rel, c in entries:
            entity_cols |= c
        missing = (entity_cols - cols) - SKIP_PROPERTIES
        if missing:
            problems += 1
            print(f"❌ {table}: أعمدة كيان غير موجودة في DB: {sorted(missing)}")
        else:
            print(f"✅ {table}: متطابق ({len(entries)} كيان)")

    print("\nالنتيجة:", "سليم ✅" if problems == 0 else f"فيه {problems} مشكلة ❌")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
