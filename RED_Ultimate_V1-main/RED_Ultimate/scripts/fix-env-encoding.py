#!/usr/bin/env python3
"""
يُصلح ترميز ملف .env الذي كتبته PowerShell.

العطب
─────
    docker compose config --services
    failed to read ...\.env: line 102: unexpected character "\x00"
    in variable name "r\x00e\x00d\x00.\x00a\x00d\x00m\x00i\x00n\x00..."

الصفر بين كل حرفين هو بصمة **UTF-16LE**. و`>` و`Out-File` و
`Set-Content` في PowerShell 5.1 تكتب بهذا الترميز افتراضيًّا، بينما
Docker Compose يقرأ `.env` كـUTF-8 حصرًا فيرى بايتات صفرية داخل أسماء
المتغيّرات.

ما يفعله السكربت
────────────────
  • يكشف الترميز من BOM أو من نمط البايتات الصفرية (لا يخمّن)
  • يحوّل إلى UTF-8 بلا BOM وبنهايات أسطر LF
  • ينسخ احتياطيًّا قبل الكتابة
  • يتحقّق أن الناتج يُحلَّل فعلًا كـ`.env` سليم

لا يغيّر أي قيمة، ولا يعيد ترتيب سطر، ولا يحذف تعليقًا. التحويل
ترميزيّ بحت.

الاستعمال:
    python3 scripts/fix-env-encoding.py .env
    python3 scripts/fix-env-encoding.py .env --check   # فحص بلا كتابة
"""
import argparse
import os
import shutil
import sys

BOMS = [
    (b"\xff\xfe\x00\x00", "utf-32-le"),
    (b"\x00\x00\xfe\xff", "utf-32-be"),
    (b"\xff\xfe", "utf-16-le"),
    (b"\xfe\xff", "utf-16-be"),
    (b"\xef\xbb\xbf", "utf-8-sig"),
]


def detect_encoding(raw: bytes) -> str:
    """يُرجع اسم الترميز. يعتمد BOM أولًا ثم نمط البايتات الصفرية."""
    for bom, enc in BOMS:
        if raw.startswith(bom):
            return enc

    if not raw:
        return "utf-8"

    # بلا BOM: تُكشف UTF-16 من مواضع الأصفار. نصّ ASCII مرمَّز
    # UTF-16LE يضع صفرًا في كل بايت فردي، وUTF-16BE في كل زوجي.
    sample = raw[:4096]
    zeros = sample.count(0)
    if zeros > len(sample) // 4:
        odd = sum(1 for i in range(1, len(sample), 2) if sample[i] == 0)
        even = sum(1 for i in range(0, len(sample), 2) if sample[i] == 0)
        return "utf-16-le" if odd >= even else "utf-16-be"

    return "utf-8"


def validate(text: str):
    """يتحقّق أن النصّ يُقرأ كـ.env سليم. يُرجع قائمة المشكلات."""
    problems = []
    for number, line in enumerate(text.split("\n"), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "\x00" in line:
            problems.append(f"سطر {number}: ما زال فيه بايت صفري")
            continue
        if "=" not in stripped:
            problems.append(f"سطر {number}: بلا علامة = ⇒ {stripped[:40]!r}")
            continue
        name = stripped.split("=", 1)[0].strip()
        if not name:
            problems.append(f"سطر {number}: اسم متغيّر فارغ")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default=".env")
    parser.add_argument("--check", action="store_true",
                        help="فحص فقط بلا تعديل")
    args = parser.parse_args()

    if not os.path.isfile(args.path):
        print(f"❌ لا يوجد ملف: {args.path}", file=sys.stderr)
        return 2

    raw = open(args.path, "rb").read()
    encoding = detect_encoding(raw)
    print(f"الملف   : {args.path}")
    print(f"الحجم   : {len(raw)} بايت")
    print(f"الترميز : {encoding}")

    if encoding in ("utf-8",) and b"\x00" not in raw and b"\r" not in raw:
        print("\n✅ الملف سليم أصلًا (UTF-8، بلا بايتات صفرية، نهايات LF).")
        return 0

    try:
        text = raw.decode(encoding)
    except UnicodeDecodeError as error:
        print(f"\n❌ تعذّر فكّ الترميز: {error}", file=sys.stderr)
        return 1

    # وحّد نهايات الأسطر وأزل BOM متبقّيًا في الصدر
    text = text.replace("\r\n", "\n").replace("\r", "\n").lstrip("\ufeff")

    problems = validate(text)
    if problems:
        print(f"\n⚠️  {len(problems)} سطرًا مريبًا بعد التحويل:")
        for item in problems[:10]:
            print(f"     {item}")
        print("     (تُكتب كما هي؛ راجعها بنفسك)")

    variables = sum(
        1 for line in text.split("\n")
        if line.strip() and not line.strip().startswith("#") and "=" in line
    )
    print(f"\nالمتغيّرات المقروءة: {variables}")

    if args.check:
        print("\n(--check: لم يُكتب شيء)")
        return 1 if encoding != "utf-8" else 0

    backup = args.path + ".utf16.bak"
    shutil.copy2(args.path, backup)
    with open(args.path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)

    after = open(args.path, "rb").read()
    if b"\x00" in after:
        shutil.copy2(backup, args.path)
        print("\n❌ بقيت بايتات صفرية — أُعيد الأصل.", file=sys.stderr)
        return 1

    print(f"نسخة احتياطية: {backup}")
    print(f"✅ حُوّل إلى UTF-8 بلا BOM ({len(after)} بايت).")
    print("\nتحقّق الآن بـ: docker compose config --services")
    return 0


if __name__ == "__main__":
    sys.exit(main())
