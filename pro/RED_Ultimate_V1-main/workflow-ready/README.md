# 🚀 تفعيل GitHub Actions (خطوة يدوية واحدة)

> **لماذا هذا المجلد؟** توكن GitHub App المستخدم في بيئة التطوير لا يملك صلاحية
> `workflows`، فلا يمكننا إنشاء/تعديل ملفات `.github/workflows/` من هنا.
> لذلك جهّزنا الـ workflows الجاهزة في هذا المجلد، وتفعيلها يستغرق **دقيقة واحدة**.

## الملفات الجاهزة

| الملف | الوظيفة |
|---|---|
| `red-ci.yml` | **RED CI الشامل**: 5 وظائف — بناء واختبار الخادم (Spring Boot + JUnit) + بناء لوحة الإدارة (TypeScript + عقد API) + فحوصات ثابتة (تطابق الكيانات + SFU + mock) + صحة Docker Compose + بناء APK أندرويد ورفعه كـ artifact |
| `repair-lfs.yml` | استخراج أصول LFS المكسورة (381 صورة) من مستودع Signal-Android المرجعي ورفعها كـ artifact |
| `build-red.yml` | (قديم) بناء Android يدوي بوظيفة واحدة — احتفظنا به كمرجع، يفضل استخدام `red-ci.yml` |

## طريقة التفعيل (اختر واحدة)

### الطريقة 1 — من GitHub (الأسهل، بدون سطر أوامر)
1. افتح `RED_Ultimate_V1-main/workflow-ready/red-ci.yml` في GitHub.
2. اضغط **Raw** ثم انسخ المحتوى كاملًا.
3. افتح **Actions → New workflow → set up a workflow yourself**.
4. الصق المحتوى، سمِّ الملف `red-ci.yml`، واضغط **Commit changes**.

### الطريقة 2 — من الجهاز المحلي (سطر أوامر)
```bash
cd <جذر المستودع>
mv RED_Ultimate_V1-main/workflow-ready/red-ci.yml .github/workflows/red-ci.yml
mv RED_Ultimate_V1-main/workflow-ready/repair-lfs.yml .github/workflows/repair-lfs.yml
git add .github/workflows/
git commit -m "تفعيل RED CI الشامل"
git push origin main
```

## ملاحظات مهمة
- GitHub **لا يرى** إلا ملفات `.github/workflows/` في **جذر** المستودع.
  الملفات داخل `RED_Ultimate_V1-main/.github/workflows/` كانت **ميتة** (لا تُنفَّذ) — لهذا نُقلت هنا.
- بعد التفعيل، سيعمل الـ CI تلقائيًا على كل push/PR نحو `main`، ويمكن تشغيله
  يدويًا من تبويب Actions (زر **Run workflow**) مع إدخال عنوان الخادم للبناء.
- فحوصات الـ CI **ليست إلزامية** للدمج (لا توجد Branch Protection مفروضة)، لكنها
  البوابة الاحترافية قبل أي إصدار.
