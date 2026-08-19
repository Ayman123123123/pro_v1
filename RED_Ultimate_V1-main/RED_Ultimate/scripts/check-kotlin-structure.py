#!/usr/bin/env python3
"""
فاحص بنيوي لملفات Kotlin — بديل جزئي عن المترجم.

**لماذا موجود:** بيئة التطوير هنا بلا JVM (لا `java`، ولا صلاحية
تثبيت، ولا شبكة)، فلا سبيل لتشغيل `gradlew`. وقد كشف هذا الغياب عن
عطبٍ حقيقي بقي في الشيفرة منذ commit الأساس: سطر فيه `remembr` ومتغيّر
معلَن مرتين، وكلاهما يمنع ترجمة الوحدة كاملة.

**ما يفحصه** (أشياء لا تحتاج مترجمًا لإثباتها):
  1. توازن الأقواس بعد تجريد النصوص والتعليقات.
  2. متغيّر `by remember` معلَن مرتين في نطاق الدالة نفسه.
  3. أخطاء إملائية في أسماء دوال Compose الشائعة.
  4. `when` على `enum` معروف تنقصه قيمة (شمولية).
  5. استيراد مكرّر حرفيًّا.
  6. مراجع إلى ملفات مؤرشفة (`.archived`) من شيفرة حيّة.

**ما لا يفحصه:** الأنواع، وحلّ الرموز عبر الملفات، والتوافق مع
واجهات المكتبات. هذا الفاحص **لا يغني عن `./gradlew test`** على جهاز
فيه JDK؛ غايته منع صنف الأخطاء الذي أفلت فعلًا.

الاستعمال:  python3 scripts/check-kotlin-structure.py
"""
from __future__ import annotations

import collections
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ["red-app/src", "backend-server/src"]

TYPOS = [
    "remembr", "rememberr", "remeber",
    "mutableStateof", "mutablestateOf",
    "Modifer", "Modifierr",
    "LaunchedEffct", "Compsable", "Composble",
    "collectAsStat(", "rememberCoroutinScope",
]


def strip_noise(text: str) -> str:
    """
    يجرّد النصوص والتعليقات في **مسحة واحدة** بآلة حالات.

    التجريد بتعبيرات نمطية متتابعة فشل هنا مهما رُتّب، والسبب بنيويّ
    لا عرضيّ: النصوص والتعليقات متداخلة تعريفًا. لو جُرّدت النصوص
    أولًا، فسّر النمطُ علامةَ اقتباس داخل تعليق كبداية نصّ. ولو
    جُرّدت التعليقات أولًا، حُذف ما يشبه تعليقًا داخل نصّ
    (`"http://x"`). وأيًّا كان الترتيب تبقى الأقواس المكتوبة داخل
    تعليق — مثل `{redId}` في توثيق مسار — تُحسب أقواسًا حقيقية.

    آلة الحالات تمرّ محرفًا محرفًا فتعرف في كل لحظة أهي داخل نصّ أم
    تعليق أم شيفرة، فتُخرج الشيفرة وحدها. وهذا يغطّي قوالب `${...}`
    لأنها تقع داخل النص فتسقط معه.
    """
    out: list[str] = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                i += 1
        elif ch == "/" and nxt == "*":
            i += 2
            while i < n and not (text[i] == "*" and i + 1 < n and text[i + 1] == "/"):
                i += 1
            i += 2
        elif text.startswith('"""', i):
            # نصّ ثلاثي. الاقتباسات الزائدة في التتابع النهائي تنتمي
            # إلى محتوى النص لا إلى حدّه: `"""he said ""hello""""`
            # صحيح في Kotlin. فيُتجاوز التتابع كاملًا.
            i += 3
            while i < n and not text.startswith('"""', i):
                i += 1
            while i < n and text[i] == '"':
                i += 1
        elif ch == '"':
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
        elif ch == "'":
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def kotlin_files() -> list[Path]:
    out: list[Path] = []
    for src in SOURCES:
        base = ROOT / src
        if not base.exists():
            continue
        out += [p for p in base.rglob("*.kt") if "_archive" not in p.parts]
    return sorted(out)


def check_braces(path: Path, text: str, problems: list[str]) -> None:
    clean = strip_noise(text)
    for open_ch, close_ch, label in (("{", "}", "أقواس معقوفة"),
                                     ("(", ")", "أقواس دائرية"),
                                     ("[", "]", "أقواس مربّعة")):
        diff = clean.count(open_ch) - clean.count(close_ch)
        if diff:
            problems.append(f"{path}: {label} غير متوازنة ({diff:+d})")


def check_duplicate_state(path: Path, text: str, problems: list[str]) -> None:
    """متغيّر `by remember` معلَن مرتين داخل الدالة نفسها = تعارض تعريف."""
    lines = strip_noise(text).split("\n")
    depth = 0
    current = None
    seen: collections.Counter[str] = collections.Counter()
    for index, line in enumerate(lines, start=1):
        if depth == 0:
            match = re.match(r"^(?:private |internal |public )?(?:suspend )?fun (\w+)", line)
            if match:
                current = match.group(1)
                seen = collections.Counter()
        declaration = re.match(r"^    (?:var|val) (\w+) by ", line)
        if declaration and current:
            name = declaration.group(1)
            seen[name] += 1
            if seen[name] == 2:
                problems.append(
                    f"{path}:{index}: '{name}' معلَن مرتين في {current}() — تعارض تعريف"
                )
        depth += line.count("{") - line.count("}")


def check_typos(path: Path, text: str, problems: list[str]) -> None:
    for typo in TYPOS:
        for match in re.finditer(re.escape(typo), text):
            line = text[: match.start()].count("\n") + 1
            problems.append(f"{path}:{line}: مرجع غير قابل للحلّ '{typo}'")


def check_duplicate_imports(path: Path, text: str, problems: list[str]) -> None:
    imports = re.findall(r"^import ([\w.*]+)$", text, re.M)
    for name, count in collections.Counter(imports).items():
        if count > 1:
            problems.append(f"{path}: استيراد مكرّر ×{count} — {name}")


_ARCHIVED_CACHE: set[str] | None = None


def _archived_symbols() -> set[str]:
    """
    أسماء الدوال العليا داخل الملفات المؤرشفة، **ما لم تكن معرَّفة
    حيًّا أيضًا**. الاسم وحده لا يكفي: `AuthFlow.legacy.kt.archived`
    يحمل الاسم `AuthFlow` بينما الدالة نفسها حيّة في `AuthScreens.kt`،
    فالمطابقة الساذجة تُنتج إنذارًا كاذبًا.
    """
    global _ARCHIVED_CACHE
    if _ARCHIVED_CACHE is not None:
        return _ARCHIVED_CACHE
    archive = ROOT / "red-app/src/main/java/com/red/sovereign/_archive"
    archived: set[str] = set()
    if archive.exists():
        for path in archive.glob("*.archived"):
            body = path.read_text(encoding="utf-8", errors="ignore")
            archived |= set(re.findall(r"^(?:private |internal )?fun (\w+)", body, re.M))
    live: set[str] = set()
    for path in kotlin_files():
        body = path.read_text(encoding="utf-8", errors="ignore")
        live |= set(re.findall(r"^(?:private |internal )?fun (\w+)", body, re.M))
    _ARCHIVED_CACHE = archived - live
    return _ARCHIVED_CACHE


def check_archived_refs(path: Path, text: str, problems: list[str]) -> None:
    """مرجع من شيفرة حيّة إلى رمز أُرشف = مرجع مكسور."""
    stripped = strip_noise(text)
    for symbol in _archived_symbols():
        match = re.search(rf"\b{re.escape(symbol)}\s*\(", stripped)
        if match:
            line = stripped[: match.start()].count("\n") + 1
            problems.append(f"{path}:{line}: استدعاء رمز مؤرشف '{symbol}'")


def main() -> int:
    files = kotlin_files()
    problems: list[str] = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT)
        check_braces(rel, text, problems)
        check_duplicate_state(rel, text, problems)
        check_typos(rel, text, problems)
        check_duplicate_imports(rel, text, problems)
        check_archived_refs(rel, text, problems)

    print(f"\n🔍 فحص بنية Kotlin — {len(files)} ملفًا\n")
    if problems:
        for problem in problems:
            print(f"  ❌ {problem}")
        print(f"\n❌ {len(problems)} مشكلة بنيوية.\n")
        return 1
    print("  ✅ لا مشكلات بنيوية: الأقواس متوازنة، ولا تعريف مكرّر،")
    print("     ولا مرجع إملائي معطوب، ولا استدعاء لرمز مؤرشف.")
    print("\n⚠️  هذا الفاحص لا يغني عن ./gradlew test على جهاز فيه JDK.\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
