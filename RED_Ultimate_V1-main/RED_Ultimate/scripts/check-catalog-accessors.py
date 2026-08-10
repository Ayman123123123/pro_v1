#!/usr/bin/env python3
"""
🛡️ فاحص مراجع كتالوج الإصدارات (Version Catalog Accessors)

يكتشف خطأ شائعاً وقاتلاً: كتابة accessor بشرطة بدل نقطة.
في Kotlin DSL، الكتالوج `androidx-work-runtime-ktx` يولّد accessor
اسمه `libs.androidx.work.runtime.ktx` — الشرطات تتحول لنقاط.
كتابة `libs.androidx.work.runtime-ktx` تُقرأ نحوياً: runtime ناقص ktx
→ خطأ ترجمة "unresolved reference" → فشل بناء تطبيق Android بالكامل.

هذا الخطأ الفعلي وُجد في النسخة المكررة pro/ وأُصلح في النسخة
القانونية عبر commit 958d035c. هذا الفاحص يمنع رجوعه أبداً.

الاستخدام: python3 scripts/check-catalog-accessors.py
"""
import glob
import re
import sys

ROOT = "."

# المجلدات القانونية فقط — التي تدخل settings.gradle.kts/build الفعلي.
# app/, android/, app-android/, feature/, core/, lib/, demo/ = مراجع تاريخية خارج البناء.
CANONICAL_DIRS = ("red-app/", "backend-server/", "shared-proto/", "build-logic/", "media-sfu/")
CANONICAL_ROOT_FILES = ("build.gradle.kts", "settings.gradle.kts")

def is_canonical(path: str) -> bool:
    if path in CANONICAL_ROOT_FILES:
        return True
    return not any(path.startswith(skip) for skip in
                   ("app/", "android/", "app-android/", "feature/", "core/", "lib/", "demo/"))

FAILURES = []

# ─── 1) اقرأ كل أسماء التبعيات المعرّفة في الكتالوج ────────────────────────────
catalog_path = "gradle/libs.versions.toml"
defined_aliases = set()
try:
    catalog = open(catalog_path, encoding="utf-8").read()
except FileNotFoundError:
    print(f"❌ الكتالوج غير موجود: {catalog_path}")
    sys.exit(1)

for m in re.finditer(r'^\s*([a-zA-Z0-9_.-]+)\s*=\s*[{"]', catalog, re.M):
    defined_aliases.add(m.group(1))

def alias_to_accessor(alias: str) -> str:
    """androidx-work-runtime-ktx → libs.androidx.work.runtime.ktx"""
    return "libs." + alias.replace("-", ".").replace("_", ".")

# ─── 2) افحص كل ملفات gradle.kts بحثاً عن الشرطة داخل مرجع libs. ─────────────
# النمط: libs.<segments> حيث segment يحتوي شرطة (وهذا خطأ نحوي في Kotlin DSL)
bad_pattern = re.compile(r'\blibs\.[a-zA-Z0-9]*(?:\.[a-zA-Z0-9]*)*-[a-zA-Z]')

for kts in glob.glob("**/*.gradle.kts", recursive=True):
    if "/build/" in kts or "/.gradle/" in kts or not is_canonical(kts):
        continue
    try:
        src = open(kts, encoding="utf-8").read()
    except (OSError, UnicodeDecodeError):
        continue
    for i, line in enumerate(src.splitlines(), 1):
        m = bad_pattern.search(line)
        if m:
            FAILURES.append(
                f"❌ {kts}:{i}: مرجع معطوب `{m.group(0)}` — "
                "الشرطة غير صالحة في accessor (استبدلها بنقطة)"
            )

# ─── 3) افحص accessors مستخدمة لكنها غير معرّفة في الكتالوج ─────────────────
valid_accessors = {alias_to_accessor(a) for a in defined_aliases}
used_accessor = re.compile(r'\blibs\.([a-zA-Z0-9]+(?:\.[a-zA-Z0-9]+)+)')

for kts in glob.glob("**/*.gradle.kts", recursive=True):
    if "/build/" in kts or "/.gradle/" in kts or not is_canonical(kts):
        continue
    try:
        src = open(kts, encoding="utf-8").read()
    except (OSError, UnicodeDecodeError):
        continue
    for m in used_accessor.finditer(src):
        # خطأ متعمد شائع في build-logic: libs.javaClass... هو وصول لخاصية Java وليس accessor
        if m.group(1).startswith("javaClass"):
            continue
        full = f"libs.{m.group(1)}"
        # upsert: قد يكون مرجعاً جزئياً (مثل libs.plugins أو libs.versions.x.get)
        if full in valid_accessors:
            continue
        # نتحقق فقط من المراجع التي تبدو تبعيات (وليس .get/.toInt ولا plugins/versions/bundles)
        leaf = m.group(1).split(".")[-1]
        if leaf in {"get", "toInt", "plugins", "versions", "bundles"}:
            continue
        if any(full.startswith(v + ".") for v in valid_accessors):
            continue
        if not any(v.startswith(full + ".") for v in valid_accessors):
            if "plugins" in m.group(1).lower() or "versions" in m.group(1).lower():
                continue
            FAILURES.append(
                f"⚠️  {kts}: مرجع `{full}` غير معرّف في {catalog_path}"
            )

# ─── 4) النتيجة ───────────────────────────────────────────────────────────────
if FAILURES:
    print("🛡️ فاحص مراجع الكتالوج — اكتشف مشاكل:")
    for f in FAILURES:
        print(" ", f)
    sys.exit(1)

print(f"🛡️ فاحص مراجع الكتالوج: سليم ✅ ({len(defined_aliases)} alias معرّف، كل الاستخدامات صالحة)")
