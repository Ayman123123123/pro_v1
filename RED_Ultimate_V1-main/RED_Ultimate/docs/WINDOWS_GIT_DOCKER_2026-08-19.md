# ثلاثة أعطال على جهاز ويندوز — الأسباب والحلول

جلسة 2026-08-19. الأعطال الثلاثة غير مترابطة، وكلٌّ له سبب مختلف تمامًا.

---

## ١ — `docker compose` يرفض قراءة `.env`

```
failed to read ...\.env: line 102: unexpected character "\x00"
in variable name "r\x00e\x00d\x00.\x00a\x00d\x00m\x00i\x00n\x00-..."
```

### السبب

البايت الصفري بين كل حرفين هو **بصمة UTF-16LE**. و`>` و`Out-File`
و`Set-Content` في PowerShell 5.1 تكتب بهذا الترميز افتراضيًّا، بينما
Docker Compose يقرأ `.env` كـUTF-8 حصرًا.

فالسطر `red.admin-cookie.secure=false` سليم تمامًا؛ العلّة في ترميز
الملف لا في محتواه. ولهذا لن ينفع تعديل السطر 102 ولا حذفه — الملف
كلّه بالترميز الخطأ، والسطر 102 مجرّد أول موضع اشتكى منه Docker.

### الحل

```powershell
cd C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate
python scripts\fix-env-encoding.py .env
docker compose config --services
```

السكربت ينسخ احتياطيًّا إلى `.env.utf16.bak` قبل أي كتابة، ولا يغيّر
أيّ قيمة ولا يحذف تعليقًا — التحويل ترميزيّ بحت. جُرّب على خمس حالات
(UTF-16LE بBOM وبلا، UTF-16BE، UTF-8 بBOM، UTF-8 سليم) فأعطت كلها
ناتجًا واحدًا متطابقًا.

للفحص دون تعديل: `python scripts\fix-env-encoding.py .env --check`

### المنع مستقبلًا

في PowerShell **لا تستعمل** `>` ولا `Out-File` مع `.env`:

```powershell
# ❌ يكتب UTF-16LE
"KEY=value" > .env

# ✅ يكتب UTF-8 بلا BOM
[System.IO.File]::WriteAllText("$PWD\.env", "KEY=value`n", `
    (New-Object System.Text.UTF8Encoding $false))
```

أو استعمل PowerShell 7 حيث الافتراضي UTF-8.

---

## ٢ — `merge: feat/admin-social-management - not something we can merge`

### السبب

الفرع موجود **على GitHub** لكن نسختك المحلية لا تعرفه: `git merge`
يبحث عن مرجع محلي، ونسخُك لم يجلب هذا الفرع قطّ.

### لكن الأهمّ: لا تدمجه

الفرع **مدموج في `main` مرّتين بالفعل** عبر PR #43 و#44. والأسوأ أنه
متأخّر: فيه 18 commit قديمة، و`main` سبقه بـ23 commit أحدث. دمجه اليوم
**يُرجِع** شيفرة قديمة فوق أحدث منها.

مقارنة الملفّات تؤكّد ذلك: الدمج يُظهر `917 حذفًا` مقابل `352 إضافة` —
أي أنه في معظمه تراجع لا تقدّم.

### الحل

```powershell
git fetch origin
git log --oneline origin/main -5     # تأكّد أن عملك موجود
```

ولا حاجة لدمج شيء. ولو أردت التحقّق بنفسك أن محتواه في `main`:

```powershell
git log --oneline origin/main --grep="admin-social"
gh pr list --state merged --head feat/admin-social-management
```

---

## ٣ — `git push origin main` مرفوض (non-fast-forward)

### السبب

`main` المحلي **متأخّر** عن `origin/main`. تراكمت على GitHub commits
ليست عندك (منها دمج PR #47)، فالدفع سيمحوها.

### الحل — والترتيب مهمّ

عندك تعديلات محلية غير ملتزمة (`M` أمام عشرة ملفات `.md` في مخرجاتك).
**احفظها أولًا** وإلا ضاعت:

```powershell
git stash push -u -m "تعديلات محلية قبل المزامنة"
git checkout main
git pull --ff-only origin main
git stash pop
```

`--ff-only` مقصودة: تفشل بوضوح إن تعذّر التقديم السريع بدل إنشاء دمج
لم تطلبه.

### ولا تدفع إلى `main` مباشرة

عملك يمرّ عبر PR. الفرع الحالي `arena/01a00f9e-pro-v1` مدفوع بالفعل
وله PR #46 مفتوح.

---

## ٤ — `gh auth login`

`gh` غير مصادَق على جهازك (وهو مصادَق في بيئة الوكيل، فلا تعارض):

```powershell
gh auth login --web
```

ملاحظة: **PR #47 مدموج بالفعل**، فلا معنى لـ`gh pr checkout 47`.

---

## تنبيه: `main` سبقك في إصلاح عقد القصص

`main` فيه الآن `9076487 feat(stories): align visibility and media
contracts`، وهو يعالج العطب نفسه الذي عالجتُه في `arena/01a00f9e`:
`visibleTo` ⇒ `visibility`.

**وصلنا إلى الاستنتاج نفسه استقلالًا** — وهذا تأكيد قويّ لصحّة
التشخيص. لكنّ نسخة `main` **أفضل في طرف الخادم**: أضافت
`mediaType` و`backgroundColor` و`durationMs` و`waveform` إلى
`CreateStoryRequest` و`StoryDocument`، فلم تعد تُهمَل صامتة.

ولذلك عند دمج PR #46 ستقع **9 تعارضات**، منها `StoryModels.kt`.
القاعدة المقترحة عند الحلّ:

| الملف | الأرجح |
|---|---|
| `stories/StoryModels.kt` (خادم) | خُذ `main` — أضاف حقولًا ناقصة |
| `stories/StoryModels.kt` (عميل) | ادمج: أسماء `main` + توثيق فرعي + `STORY_REACTIONS` |
| `FtsSearchManager.kt` | خُذ فرعي — `main` لم يُصلح صيغة FTS5 |
| `RedTheme.kt`, `RedExploreScreen.kt` | خُذ فرعي — إصلاحات تباين مقيسة |
| `FeedViewModel.kt`, `PostModels.kt` | راجع سطرًا سطرًا |

الحارس `scripts/check-api-contracts.py` سيمسك أي خطأ في حلّ التعارض.
