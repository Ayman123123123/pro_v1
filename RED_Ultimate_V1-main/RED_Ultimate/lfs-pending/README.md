# lfs-pending — مؤشرات Git LFS محفوظة (بلا حذف)

هذا المجلد يحفظ **كل** مؤشرات Git LFS الموجودة في تاريخ المستودع (381 ملفًا،
مطابقة تامة مع `git ls-tree` في الالتزام قبل أي تنظيف) — لم يُحذف أي شيء.

## ما هي هذه الملفات؟
صور PNG (screenshots reference لاختبارات Compose/ScreenshotTest) أصلها من
مشروع Signal-Android مفتوح المصدر (`org.signal.registration` و
`org.thoughtcrime.securesms`). الالتزام الأصلي `7dca9d8 "Add large files via
Git LFS"` أضاف **مؤشرات LFS فقط** (130 بايت لكل ملف) دون رفع الملفات الفعلية
إلى خادم LFS — لذلك أعدّها GitHub مكسورة (`Object does not exist on the server`).

## لماذا لا تُرفع إلى Git؟
GitHub يرفض أي push يحتوي مؤشرات LFS بلا محتواها (خطأ `GH008` في
pre-receive hook)، لذا لا يمكن لهذه المؤشرات أن تعيش داخل الفرع المرفوع.
هنا محفوظة بأمان كاملة (381/381 مطابقة).

## استرجاعها وإصلاحها (أمران)
```bash
# 1) أعِد الملفات إلى أماكنها الأصلية
python3 scripts/restore-lfs-pending.py

# 2) في بيئة بإنترنت مفتوح (أو حيث يكون github-cloud متاحًا): استنسخ المصدر
#    الأصلي واحصل على المحتوى الفعلي (يتطابق عبر oid sha256)
git clone https://github.com/signalapp/Signal-Android.git /tmp/signal
cd /tmp/signal && git lfs install && git lfs pull
cd ../.. && python3 scripts/restore-lfs-pending.py /tmp/signal
# → يُستبدل كل مؤشر بمحتواه الفعلي (sha256 مطابق للـ oid)،
#   وتُزال قواعد LFS من .gitattributes فتلتزم الملفات عادية.
```

## إعادة التوليد (إن فُقد المجلد محليًا)
المؤشرات قابلة لإعادة البناء في أي وقت من تاريخ git نفسه — الالتزام
`7dca9d8` يحوي كل المؤشرات (381 ملفًا). الأمر:
```bash
python3 scripts/regenerate-lfs-pending.py
# → يعيد بناء lfs-pending/ بكل المؤشرات بمساراتها النسبية الصحيحة
```

## ملاحظة
عند نجاح الإصلاح، أزل هذا المجلد (لم يعد مطلوبًا) ثم أضف الملفات المُصلحة
إلى Git كملفات عادية. كل المحتوى الفعلي قائم في `signalapp/Signal-Android`
(راجع `scripts/fetch-lfs-content.py` لتفاصيل المطابقة).
لا تُرفع المؤشرات إلى GitHub إطلاقًا (يرفضها hook GH008) — المجلد مستثنى
محليًا عبر `.git/info/exclude` والـ README فقط متعقب.
