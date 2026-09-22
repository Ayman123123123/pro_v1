# رفع المشروع إلى GitLab — دليل خطوة بخطوة

> التاريخ: 2026-09-15 — المشروع جاهز ومتكامل للرفع على GitLab.

## ما تم تجهيزه في هذا الفرع (`arena/01a0a2f4-pro-v1`)

1. **إصلاح `.gitlab-ci.yml`** (كان سيفشل حتمًا):
   - مهمة `android-test` كانت تنادي `:red-app:testDebugUnitTest` بينما اسم الموديول في
     `settings.gradle.kts` هو `:app` (مجلد `red-app/` مربوط كمشروع `:app`).
   - تم التصحيح إلى `:app:testDebugUnitTest :app:assembleDebug` وتم التحقق من YAML.
2. **تنظيف ملفات كانت متتبَّعة بالخطأ** (رغم أنها في `.gitignore`) — الملفات ما زالت
   على القرص محليًا، فقط أُزيلت من التتبع حتى لا تُرفع:
   - `RED_Ultimate_V1-main/RED_Ultimate/logcat.txt` (**26MB** سجل تشخيص)
   - `RED_Ultimate_V1-main/RED_Ultimate/build-logs/` (لقطات تشخيص + صور)
   - `RED_Ultimate_V1-main/RED_Ultimate/docs/diagnostics/` (تقارير فحص مؤرخة)
3. **فحص الأسرار**: لا توجد أسرار حقيقية متتبَّعة — كل كلمات المرور والمفاتيح في
   `application.yml` تُقرأ من متغيرات البيئة، و`keystore.properties` يحتوي فقط مفاتيح
   Debug الآمنة للفريق. ملفات `.env` الحقيقية غير متتبَّعة (فقط `.env.example`).

## خطوات الرفع (من جهازك — 5 دقائق)

### 1) أنشئ مشروعًا فارغًا على GitLab

- افتح: https://gitlab.com/projects/new
- الاسم المقترح: `pro_v1` (أو `red-ultimate`)
- **Visibility**: اختر `Private` (مستحسن)
- ⚠️ **لا** تفعّل `Initialize repository with a README` — اترك المشروع **فارغًا تمامًا**
- انسخ رابط HTTPS، سيكون شكله:
  `https://gitlab.com/USERNAME/pro_v1.git`

### 2) ادفع المشروع (اختر نظامك)

**Windows (PowerShell)** — من مجلد المشروع:

```powershell
git remote add gitlab https://gitlab.com/USERNAME/pro_v1.git
git push gitlab arena/01a0a2f4-pro-v1:main
```

أو نفّذ السكربت الجاهز:

```powershell
.\scripts\push-to-gitlab.ps1 -GitlabUrl "https://gitlab.com/USERNAME/pro_v1.git"
```

**Linux/macOS**:

```bash
git remote add gitlab https://gitlab.com/USERNAME/pro_v1.git
git push gitlab arena/01a0a2f4-pro-v1:main
```

أو:

```bash
bash scripts/push-to-gitlab.sh https://gitlab.com/USERNAME/pro_v1.git
```

> سيطلب GitLab اسم المستخدم وكلمة المرور: استخدم **Personal Access Token**
> (صلاحية `write_repository`) بدل كلمة المرور — GitLab لا يقبل كلمة مرور الحساب للدفع.

### 3) تحقق من نجاح الرفع

1. افتح صفحة المشروع على GitLab وتأكد من وجود كل المجلدات.
2. افتح **Build > Pipelines** — سيعمل تلقائيًا 3 مراحل:
   - `backend-test` (اختبارات Spring Boot)
   - `admin-checks` (فحص + بناء لوحة الإدارة)
   - `android-test` (اختبار + بناء APK تجريبي)
3. من **Settings > CI/CD > Variables** أضف أسرار الإنتاج عند الحاجة
   (`JWT_SECRET`, `DB_PASSWORD`, `TURN_SECRET`...) — لا تضعها في الكود أبدًا.

## ملاحظات مهمة

- **GitLab Shared Runners**: مهمة الأندرويد تستخدم صورة كبيرة (~عدة GB) وتستهلك من
  دقائق CI المجانية (400 دقيقة/شهر للحسابات المجانية). إذا نفدت الدقائق، عطّل مهمة
  `android-test` مؤقتًا بإضافة `rules: - when: manual` عليها.
- **حجم الدفع**: ~32MB — ضمن حدود GitLab تمامًا.
- **الفروع**: هذا الدليل يدفع فرع العمل الحالي كـ `main` على GitLab. إذا أردت كل
  الفروع: `git push gitlab --all`.
- ملف `RED_Ultimate_V1-main/.gitlab-ci.yml` الموجود داخل المجلد الفرعي هو **نسخة
  GitHub Actions قديمة بمسمى خاطئ** — GitLab يتجاهله (يقرأ فقط ملف الجذر)، وتُرك كما
  هو عمدًا للحفاظ على اكتمال الأرشيف.
