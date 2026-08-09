#!/usr/bin/env python3
"""
إصلاح مؤشرات Git LFS بمحتواها الفعلي من مصدر Signal مفتوح المصدر
-----------------------------------------------------------------
هذه الصور (screenshots reference) أصلها من مشروع Signal-Android
(org.signal.registration / org.thoughtcrime.securesms). المؤشرات تحمل
oid sha256 للمحتوى الأصلي، وهو موجود في استنساخ Signal على GitHub.

الخطوات:
  1. تجميع خريطة sha256 -> مسار لكل ملف في استنساخ Signal (SIGNAL_DIR).
  2. لكل مؤشر LFS في المستودع: قراءة oid والمطابقة، وكتابة المحتوى الفعلي.
  3. إزالة قواعد LFS للصور من .gitattributes حتى تُلتزم الملفات كملفات عادية.

الاستخدام (من جذر المستودع، بعد استنساخ Signal مع LFS pull):
    python3 scripts/fetch-lfs-content.py /path/to/signal-android

إرجاع: 0 عند إصلاح كل شيء، 1 إذا بقي بعض المؤشرات بلا مصدر.
"""
import hashlib
import os
import subprocess
import sys

SIGNAL = sys.argv[1] if len(sys.argv) > 1 else "/tmp/signal-android"
ROOT = subprocess.check_output(
    ["git", "rev-parse", "--show-toplevel"], text=True
).strip()


def collect_hashes(root: str) -> dict:
    """sha256 -> [paths] لكل ملف صورة في استنساخ Signal."""
    table = {}
    count = 0
    for dp, dirs, files in os.walk(root):
        # تجاوز .git ووحدات الصور الضخمة غير المطلوبة
        dirs[:] = [d for d in dirs if d not in (".git", "node_modules", ".gradle", "build")]
        for name in files:
            if not name.lower().endswith((".png", ".jpg", ".jpeg", ".psd")):
                continue
            p = os.path.join(dp, name)
            try:
                if os.path.getsize(p) < 200:
                    continue  # مؤشر LFS أو ملف فارغ
            except OSError:
                continue
            try:
                h = hashlib.sha256(open(p, "rb").read()).hexdigest()
            except OSError:
                continue
            table.setdefault(h, []).append(p)
            count += 1
    print(f"تم فحص {count} ملف صورة في مصدر Signal")
    return table


def main() -> int:
    if not os.path.isdir(SIGNAL):
        print(f"❌ مصدر Signal غير موجود: {SIGNAL}", file=sys.stderr)
        return 2

    table = collect_hashes(SIGNAL)
    pointers = subprocess.check_output(
        ["git", "grep", "-l", "version https://git-lfs", "--", "."],
        text=True, cwd=ROOT,
    ).splitlines()

    repaired, missing = 0, []
    for rel in pointers:
        rel = rel.strip().lstrip(":")
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            continue
        try:
            text = open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        if "version https://git-lfs" not in text:
            continue
        oid = None
        for line in text.splitlines():
            if line.startswith("oid sha256:"):
                oid = line.split(":", 1)[1].strip()
                break
        if not oid:
            missing.append((rel, "no oid"))
            continue
        candidates = table.get(oid)
        if not candidates:
            missing.append((rel, oid[:12] + "… غير موجود في مصدر Signal"))
            continue
        src = candidates[0]
        data = open(src, "rb").read()
        if hashlib.sha256(data).hexdigest() != oid:
            missing.append((rel, "sha256 mismatch"))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as out:
            out.write(data)
        repaired += 1

    # إزالة قواعد LFS للصور حتى تُلتزم الملفات عادية
    attrs = os.path.join(ROOT, ".gitattributes")
    if os.path.exists(attrs):
        lines = [
            ln for ln in open(attrs, encoding="utf-8").read().splitlines()
            if not any(g in ln for g in ("*.png filter=lfs", "*.jpg filter=lfs", "*.psd filter=lfs"))
        ]
        with open(attrs, "w", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n")
        print("تمت إزالة قواعد LFS للصور من .gitattributes")

    print(f"\nإصلاح: {repaired} ملف ✅  |  بلا مصدر: {len(missing)}")
    for rel, why in missing[:20]:
        print(f"  ⚠️  {rel} — {why}")
    if missing:
        print(f"\n(إجمالي {len(missing)} مؤشرات لا مثيل لها في مصدر Signal — "
              "تبقى مؤشرات موثقة بلا محتوى)")
    return 0 if not missing else 1


if __name__ == "__main__":
    sys.exit(main())
