#!/usr/bin/env bash
# فاحص مؤشرات Git LFS المكسورة
# ---------------------------------------------------------------------
# يكتشف ملفات LFS pointer (130 بايت) التي لا يملك المستودع محتواها الفعلي.
# تُنشأ هذه المؤشرات عندما يُلتزم ملف عبر .gitattributes (png/psd/jpg)
# دون رفع الملفات الفعلية إلى خادم LFS.
#
# التشغيل (من جذر المستودع):
#   bash scripts/check-lfs-pointers.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

count=0
total=0
while IFS= read -r file; do
    total=$((total + 1))
    size=$(stat -c%s "$file" 2>/dev/null || echo 0)
    if [ "$size" -lt 200 ]; then
        count=$((count + 1))
        echo "⚠️  pointer مكسور: $file (${size} بايت — المحتوى غير موجود)"
    fi
done < <(git grep -l "version https://git-lfs" -- . 2>/dev/null || true)

echo
if [ "$count" -eq 0 ]; then
    echo "✅ لا توجد مؤشرات LFS مكسورة ($total ملف سليم)"
    exit 0
else
    echo "❌ $count من أصل $total مؤشرات LFS بلا محتوى فعلي"
    echo "   (غير قابلة للاسترجاع: لم تُرفع ملفاتها إلى GitHub ولا توجد نسخة محلية)"
    exit 1
fi
