#!/usr/bin/env python3
"""
فاحص SQL المكتوب يدويًا مقابل المخطَّط الحيّ — تخطيطٌ بلا تنفيذ
---------------------------------------------------------------
يستخرج كل جملة SQL مغروسة في مصادر Kotlin ويطلب من PostgreSQL أن يُخطِّط لها
(`EXPLAIN (GENERIC_PLAN)`) داخل معاملة تُلغى دائمًا. لا صفَّ يُقرأ ولا صفَّ
يُكتب: `EXPLAIN` بلا `ANALYZE` لا ينفّذ الجملة.

لماذا هذا الفاحص موجود
=======================
`check-schema-consistency.py` يقارن كيانات JPA بملفات الهجرة نصيًا، فلا يرى
شيئًا من الـSQL المكتوب بـ`JdbcTemplate` — وهو معظم منطق DINSTAR. اسمٌ خاطئ في
جملة كهذه لا يفشل عند الإقلاع بل عند مرور مسار التنفيذ فقط، وغالبًا داخل
`catch` يُبتلَع فيه الاستثناء. عيوب حقيقية أمسكها هذا الفاحص:

* عرضٌ يقرأ `c.duration` و`c.hangup_cause` من جدول يحمل `duration_seconds`
  و`status` (هجرات DINSTAR).
* `INSERT INTO dinstar_cdr ... ON CONFLICT (...) DO NOTHING` بلا إعادة شرط
  الفهرس الجزئي `uq_dinstar_cdr_natural_key`: PostgreSQL يرفض فهرسًا جزئيًا
  حَكَمًا للتعارض إلا إذا أعادت الجملة شرطَه حرفيًا، فكانت **كل** دورة ابتلاع
  CDR تسقط بأكملها ويبقى الجدول فارغًا.
* `SELECT nextval(''dinstar_sms_user_id_seq'')` باقتباس مُضعَّف: خطأ نحوي دائم
  كان يُبتلَع في `runCatching` فيسقط التنفيذ إلى عدّاد ذاكرة يبدأ من 1 عند كل
  إقلاع، فتتكرّر `user_id` وتُنسَب تقارير التسليم إلى رسائل خاطئة.

`EXPLAIN (GENERIC_PLAN)` لا `PREPARE`
======================================
`PREPARE` يُعرِب ويحلّل فقط. استنتاج حَكَم `ON CONFLICT` يقع في **المُخطِّط**
(`infer_arbiter_indexes`، plancat.c)، فكان `PREPARE` يقبل الجملة المعطوبة أعلاه
دون شكوى — أي أن الفاحص كان يفوته العيب الذي بُني لأجله. ثبت الفرق عمليًا على
نفس الجملة: `PREPARE` صامت، و`EXPLAIN (GENERIC_PLAN)` يعطي 42P10.
`GENERIC_PLAN` (من PostgreSQL 16) يُخطِّط بمحارف `$n` بلا قيم.

الحكم بالـSQLSTATE لا بنصّ الرسالة
===================================
النسخة الأولى صنّفت الأخطاء بالبحث عن «does not exist» في نص الرسالة، فأفلت
42P10 (وهو بلا هذه العبارة) إلى خانة «غير قابل للتحقق» ومرّ العيب. لذلك يُشغَّل
psql بـ`\\set VERBOSITY verbose` ليطبع الرمز، ويُصنَّف عليه:

عيوب حقيقية:
  42P01 جدول غير معروف        42703 عمود غير معروف
  42883 دالة غير معروفة       42704 كائن غير معروف
  42P10 مرجع عمود غير صالح (منه حالة ON CONFLICT أعلاه)
  42601 خطأ نحوي             42P02 معامل غير معروف
  42804 عدم تطابق أنواع

`42P18` تعني فقط أن نوع محرف الاستبدال لا يُستنتج بلا سياق — تُذكر منفصلة.

ما يُستبعَد عن قصد
==================
* ما فيه استقراء Kotlin (`${'$'}{...}`): قيمته غير معروفة قبل التشغيل.
* JPQL في `@Query`: يخاطب كياناتٍ لا جداول ويتحقّق منه Hibernate عند الإقلاع.

يتطلّب حاوية قاعدة عاملة؛ وإلا طبع SKIP وأعاد 0 كبقية فحوص Docker في
`check-all.sh`.
"""
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
KOTLIN = ROOT / "backend-server" / "src" / "main" / "kotlin"

CONTAINER = os.environ.get("RED_DB_CONTAINER", "red-db-sql")
DB_USER = os.environ.get("RED_DB_USER", "admin")
DB_NAME = os.environ.get("RED_DB_NAME", "red_sovereign")

# رموز تعني أن الجملة تشير إلى شيء غير موجود أو تُخالف المخطَّط: عيب حقيقي.
DEFECT_CODES = {
    "42P01",  # undefined_table
    "42703",  # undefined_column
    "42883",  # undefined_function
    "42704",  # undefined_object
    "42P10",  # invalid_column_reference (ON CONFLICT بلا شرط الفهرس الجزئي)
    "42601",  # syntax_error
    "42P02",  # undefined_parameter
    "42804",  # datatype_mismatch
}

# رمز واحد لا يدلّ على عيب: نوع المحرف لا يُستنتج بلا سياق.
UNVERIFIABLE_CODES = {"42P18"}

SQL_START = re.compile(r"^\s*(SELECT|INSERT|UPDATE|DELETE|WITH)\b", re.I)
# psql مع VERBOSITY verbose يطبع: ERROR:  42P01: relation "x" does not exist
ERROR_LINE = re.compile(r"^(?:psql:[^:]*:\d+:\s*)?ERROR:\s+([0-9A-Z]{5}):\s*(.*)$")

# JPQL ليس SQL: `SELECT u FROM UserAccount u` يخاطب كياناتٍ لا جداول. تحضيره في
# PostgreSQL يفشل حتمًا (42P01/42601) وهو سليم تمامًا — يتحقّق منه Hibernate عند
# الإقلاع لا القاعدة. تمييزه من محتواه لا من سياقه: كل جداول هذا المستودع
# snake_case، فاسم علاقة فيه حرف كبير هو كيان JPA بالضرورة.
CAMEL_RELATION = re.compile(
    r"\b(?:FROM|JOIN|UPDATE|INTO)\s+([A-Za-z_][A-Za-z0-9_]*)", re.I
)
NAMED_PARAM = re.compile(r"(?<![:\w]):([a-zA-Z][a-zA-Z0-9_]*)")


def is_jpql(sql: str) -> bool:
    for m in CAMEL_RELATION.finditer(sql):
        name = m.group(1)
        # `SELECT ... FROM UserAccount` كيان؛ `FROM dinstar_cdr` جدول.
        if any(c.isupper() for c in name) and name.upper() != name:
            return True
    return False


def extract_sql(text: str) -> list[tuple[int, str]]:
    """كل نصّ ثلاثي أو مفرد يبدو جملة SQL كاملة، مع رقم سطره."""
    out: list[tuple[int, str]] = []
    for m in re.finditer(r'"""(.*?)"""', text, re.S):
        body = m.group(1)
        if SQL_START.match(body):
            out.append((text[: m.start()].count("\n") + 1, body))
    for m in re.finditer(r'"((?:[^"\\\n]|\\.){20,})"', text):
        body = m.group(1)
        if SQL_START.match(body):
            out.append((text[: m.start()].count("\n") + 1, body))
    return out


def normalize(sql: str) -> str | None:
    """`?` الخاصة بـJDBC إلى `$n`. ما فيه استقراء Kotlin يُستبعَد: قيمته غير
    معروفة قبل التشغيل فلا معنى لتحضيره. وJPQL يُستبعَد لأنه ليس SQL."""
    if "${" in sql or re.search(r"\$[A-Za-z_]", sql):
        return None
    if is_jpql(sql):
        return None
    n = 0
    res = []
    for ch in sql:
        if ch == "?":
            n += 1
            res.append(f"${n}")
        else:
            res.append(ch)
    return re.sub(r"\s+", " ", "".join(res).strip().rstrip(";"))


def docker_available() -> bool:
    try:
        p = subprocess.run(
            ["docker", "inspect", "-f", "{{.State.Running}}", CONTAINER],
            capture_output=True, text=True, timeout=30,
        )
        return p.returncode == 0 and p.stdout.strip() == "true"
    except (OSError, subprocess.SubprocessError):
        return False


def main() -> int:
    if not docker_available():
        print(f"SKIP: الحاوية {CONTAINER} غير عاملة — هذا الفاحص يحتاج مخطَّطًا حيًّا")
        return 0

    stmts: list[tuple[str, int, str]] = []
    for kt in sorted(KOTLIN.rglob("*.kt")):
        text = kt.read_text(encoding="utf-8", errors="replace")
        for line, sql in extract_sql(text):
            norm = normalize(sql)
            if norm:
                stmts.append((kt.relative_to(ROOT).as_posix(), line, norm))

    if not stmts:
        print("❌ لم تُستخرج أي جملة SQL — الاستخراج معطوب لا المصادر نظيفة")
        return 1

    # كل جملة في SAVEPOINT خاص بها حتى لا يُجهض فشلٌ واحدٌ ما بعده،
    # وعلامة \echo قبلها لنسبة كل خطأ إلى جملته.
    #
    # `EXPLAIN (GENERIC_PLAN)` لا `PREPARE`: الأخير يحلّل ويُعرِب فقط، أما
    # استنتاج حَكَم `ON CONFLICT` فيقع في المُخطِّط (infer_arbiter_indexes في
    # plancat.c). ثبت هذا عمليًا: `PREPARE` قبل جملةً بلا شرط الفهرس الجزئي
    # دون شكوى، بينما `EXPLAIN (GENERIC_PLAN)` أعطى 42P10 — أي أن الفاحص
    # بـPREPARE كان يفوته العيب الذي بُني لأجله. و`GENERIC_PLAN` (من PG16)
    # يُخطِّط بمحارف `$n` بلا قيم، و`EXPLAIN` بلا `ANALYZE` لا ينفّذ شيئًا.
    script = ["\\set VERBOSITY verbose", "BEGIN;"]
    for i, (_path, _line, sql) in enumerate(stmts):
        script.append(f"\\echo @@{i}")
        script.append(f"SAVEPOINT s{i};")
        script.append(f"EXPLAIN (GENERIC_PLAN) {sql};")
        script.append(f"ROLLBACK TO SAVEPOINT s{i};")
    script.append("ROLLBACK;")

    # الملف يُمرَّر على stdin: لا كتابة داخل الحاوية ولا docker cp.
    # دمج stderr داخل الحاوية لا خارجها: psql يكتب العلامات على stdout
    # والأخطاء على stderr، فدمجهما من هنا يُفقد التزامن ويُلحق كل خطأ
    # بالجملة الأخيرة.
    proc = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "sh", "-c",
         f"psql -U {DB_USER} -d {DB_NAME} -q -f - 2>&1"],
        input="\n".join(script),
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )

    current: int | None = None
    found: dict[int, tuple[str, str]] = {}
    for raw in proc.stdout.splitlines():
        line = raw.strip()
        if line.startswith("@@"):
            current = int(line[2:])
            continue
        m = ERROR_LINE.match(line)
        if m and current is not None and current not in found:
            found[current] = (m.group(1), m.group(2))

    defects, unverifiable, unknown = [], [], []
    for idx, (code, msg) in sorted(found.items()):
        path, line, sql = stmts[idx]
        entry = (path, line, code, msg, sql)
        if code in DEFECT_CODES:
            defects.append(entry)
        elif code in UNVERIFIABLE_CODES:
            unverifiable.append(entry)
        else:
            unknown.append(entry)

    ok = len(stmts) - len(found)
    print(f"جمل SQL خطّطتها القاعدة بنجاح: {ok}/{len(stmts)}")

    if unverifiable:
        print(f"\nغير قابل للتحقق ثابتًا ({len(unverifiable)}) — نوع محرف الاستبدال بلا سياق:")
        for path, line, code, _msg, _sql in unverifiable:
            print(f"  {path}:{line}  [{code}]")

    if unknown:
        print(f"\nرموز غير مصنَّفة ({len(unknown)}) — تُراجع يدويًا:")
        for path, line, code, msg, _sql in unknown:
            print(f"  {path}:{line}  [{code}] {msg[:120]}")

    if defects:
        print(f"\n❌ عيوب: SQL يشير إلى ما ليس في المخطَّط ({len(defects)})")
        for path, line, code, msg, sql in defects:
            print(f"\n  {path}:{line}  [{code}]")
            print(f"    {msg[:300]}")
            print(f"    SQL: {sql[:240]}")
        return 1

    print("\nالنتيجة: سليم ✅ — كل جملة تُخطِّطها القاعدة مقابل المخطَّط الحيّ")
    return 0


if __name__ == "__main__":
    sys.exit(main())
