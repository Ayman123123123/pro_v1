#!/usr/bin/env python3
"""
استرجاع مؤشرات LFS المحفوظة إلى أماكنها الأصلية + إصلاح اختياري
-----------------------------------------------------------------
1) يعيد كل ملف من RED_Ultimate/lfs-pending/ إلى موقعه الأصلي
   (المسار النسبي محفوظ داخل lfs-pending).
2) إن وُجد مصدر Signal (استنساخ signalapp/Signal-Android مع LFS pull)
   شغّل إصلاح المحتوى تلقائيًا عبر fetch-lfs-content.py.

الاستخدام:
    python3 scripts/restore-lfs-pending.py                 # استرجاع فقط
    python3 scripts/restore-lfs-pending.py /tmp/signal     # استرجاع + إصلاح

ملاحظة: lfs-pending/ مستثنى من Git (في .git/info/exclude محليًا) ولا يُرفع؛
الملفات محفوظة فيه للاسترجاع عند توفر بيئة بوصول كامل للإنترنت.
"""
import os
import shutil
import subprocess
import sys

ROOT = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip()
PENDING = os.path.join(ROOT, "RED_Ultimate_V1-main", "RED_Ultimate", "lfs-pending")
PROJECT = os.path.join(ROOT, "RED_Ultimate_V1-main", "RED_Ultimate")


def main() -> int:
    if not os.path.isdir(PENDING):
        print("لا يوجد مجلد lfs-pending — لا شيء لاسترجاعه")
        return 0

    restored = 0
    for dp, _dn, fn in os.walk(PENDING):
        for name in fn:
            src = os.path.join(dp, name)
            rel = os.path.relpath(src, PENDING)
            dst = os.path.join(PROJECT, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            restored += 1

    print(f"تم استرجاع {restored} ملفًا إلى أماكنها الأصلية")

    if len(sys.argv) > 1:
        print("تشغيل إصلاح المحتوى من مصدر Signal…")
        script = os.path.join(PROJECT, "scripts", "fetch-lfs-content.py")
        return subprocess.call([sys.executable, script, sys.argv[1]])
    print("(لإصلاح المحتوى أيضًا: مرّر مسار استنساخ Signal-Android كوسيط)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
