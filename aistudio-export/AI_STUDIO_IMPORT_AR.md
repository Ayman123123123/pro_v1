# استيراد لوحة إدارة RED إلى Google AI Studio

هذا المجلد نسخة **كاملة ومطابقة 100%** من `RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard`،
منقولة إلى الجذر لأن Google AI Studio يبحث عن `package.json` و `index.html` في جذر المستودع.

تم التحقق: `diff -r` بين الأصل وهذه النسخة = **لا فرق، ولا حرف واحد ناقص**.
تم التحقق: `vite build` ينجح ✅ — 72 ملفاً، كل الصفحات تُبنى بلا أخطاء.

---

## لماذا فشل استيراد `pro_v1` بالخطأ `Internal error encountered`

| السبب | القياس في `pro_v1` | حد AI Studio |
|---|---|---|
| عدد الملفات | **11,011** | ~1,000 |
| حجم شجرة العمل | **151 MB** | ~10–20 MB |
| لا `package.json` في الجذر | مفقود | مطلوب |
| لا `index.html` في الجذر | مفقود | مطلوب |
| مؤشرات Git LFS مكسورة | **381 ملف** | LFS غير مدعوم |
| مسارات > 260 حرفاً | **60 مسار** (أطولها 372) | يكسر نظام الملفات |
| أسماء بمسافات | 347 ملف | يكسر بعض المحلّلات |

السبب القاتل الأول: الحجم والعدد. السبب القاتل الثاني: **381 مؤشر LFS مكسور**
— تم التأكد عبر واجهة LFS الرسمية لـ GitHub أن كل هذه الكائنات ترجع
`404 Object does not exist on the server`، أي أن محتواها الأصلي **مفقود نهائياً**
من خادم GitHub ولا يمكن استرجاعه. AI Studio يقرؤها كصور PNG فيجد نصاً
`version https://git-lfs.github.com/spec/v1` ← ينهار.

هذه الملفات المكسورة كلها لقطات شاشة اختبارية
(`screenshotTestDebug` / `screenshotTestPlayProdDebug`) — ليست جزءاً من كود
التطبيق ولا تؤثر على البناء إطلاقاً.

---

## طريقة الاستيراد

### 1) أنشئ مستودعاً جديداً
```bash
gh repo create red-admin-dashboard --public --source=. --remote=aistudio --push
```
أو يدوياً على github.com ثم:
```bash
cd aistudio-export
git init
git add -A
git commit -m "RED Admin Dashboard — AI Studio import"
git branch -M main
git remote add origin https://github.com/<اسمك>/red-admin-dashboard.git
git push -u origin main
```

### 2) في AI Studio
`Import code from GitHub` ← اختر `red-admin-dashboard` ← سينجح فوراً.

---

## تشغيل محلي

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # بناء الإنتاج
```

الواجهة تتصل بالخادم الخلفي عبر بروكسي Vite على `http://127.0.0.1:8088`
(قابل للتغيير عبر متغير البيئة `RED_API_TARGET`).

---

## ماذا عن بقية المشروع؟

**لم يُحذف ولم يتغير شيء.** المستودع الأصلي `pro_v1` كما هو بكامل
الـ11,011 ملفاً: تطبيق Android، الخادم الخلفي، كل التقارير العربية،
كل الأيقونات. هذا المجلد **إضافة** فقط.

تطبيق Android (`RED_Ultimate/app`, 6,963 ملف، Kotlin/Java) **لا يمكن**
استيراده إلى AI Studio بأي حال — المنصة تبني تطبيقات Android من الصفر
عبر Gemini، ولا تستورد مشاريع Gradle قائمة بهذا الحجم. استخدم
Android Studio له كالمعتاد.
