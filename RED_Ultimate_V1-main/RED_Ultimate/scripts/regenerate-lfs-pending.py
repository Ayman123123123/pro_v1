#!/usr/bin/env python3
"""
🔁 إعادة توليد مجلد lfs-pending من تاريخ git — يعيد بناء مؤشرات LFS
المحفوظة (381 ملفًا) من الالتزام 7dca9d8 الذي يحويها كاملة، بمساراتها
النسبية الصحيحة، بحيث يعمل سكربت الاسترجاع restore-lfs-pending.py
على إعادتها إلى أماكنها الأصلية متى أراد المستخدم ذلك.
"""

import os
import subprocess
import sys

PENDING = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "lfs-pending"
)
SOURCE_COMMIT = "7dca9d8"  # الالتزام الذي أضاف ملفات LFS كاملة


def main() -> int:
    if not os.path.isdir(PENDING):
        os.makedirs(PENDING, exist_ok=True)

    # 1) كل الملفات في الالتزام المصدر
    try:
        files = subprocess.check_output(
            ["git", "ls-tree", "-r", "--name-only", SOURCE_COMMIT],
            text=True,
        ).splitlines()
    except subprocess.CalledProcessError as e:
        print(f"❌ تعذر قراءة الالتزام {SOURCE_COMMIT}: {e}")
        return 1

    root = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    ).strip()

    restored = 0
    skipped = 0
    for f in files:
        # نستهدف ملفات LFS فقط (مؤشرات git-lfs)
        try:
            content = subprocess.check_output(
                ["git", "show", f"{SOURCE_COMMIT}:{f}"], stderr=subprocess.DEVNULL
            )
        except subprocess.CalledProcessError:
            skipped += 1
            continue
        if not content.startswith(b"version https://git-lfs.github.com/spec/v1"):
            skipped += 1
            continue

        # المسار النسبي لمجلد المشروع (نزيل بادئة المستودع إن وُجدت)
        rel = f
        for prefix in ("RED_Ultimate_V1-main/RED_Ultimate/", "RED_Ultimate/"):
            if rel.startswith(prefix):
                rel = rel[len(prefix):]
                break

        dest = os.path.join(PENDING, rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as out:
            out.write(content)
        restored += 1

    print(f"✅ أُعيد توليد {restored} مؤشر LFS في {PENDING}")
    print(f"   (تخطّى {skipped} ملفًا غير LFS)")
    return 0 if restored > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
