# LEGACY_REMOVAL_2026-09-16 — توثيق ما أُزيل/أُرشف من المجلدات القديمة

**تاريخ الإنشاء:** 2026-09-16  
**القرار المرجعي:** OLD_MODULES_DECISION_2026-09-16.md  
**الجلسة:** #3 (فهرسة وتنظيف)  
**التنفيذ:** Git commit bc8cbaa + نقل فعلي إلى D:\pro_archive_2026-09-16\old-modules\

---

## ملخص تنفيذي

تم إزالة 4 وحدات Gradle كاملة (`lib`، `core`، `feature`، `demo`) من شجرة المصدر الحية وأرشفتها كـ zip مع تحقق SHA256. كانت هذه الوحدات بقايا Fork Signal (~3730 ملف، ~21 MB كود، ~951 ملف .kt) **غير مشمولة في `settings.gradle.kts`** ولا تعتمد عليها أي وحدة حية (`red-app`، `backend-server`، `shared-proto`).

---

## الوحدات المزالة/المؤرشفة

| الوحدة | المسار الأصلي | ملفات .kt | إجمالي الملفات | حجم المصدر | ملف الأرشيف | حجم الأرشيف | SHA256 |
|---------|--------------|-----------|---------------|------------|-------------|-------------|--------|
| **lib** | `RED_Ultimate/lib/` | 338 | ~1,020 | 5.44 MB | `lib.zip` | 674,638 B | `B5904B835DA85B301D7BAD1BD67D5499972F376C0DBED63D658CF4BEC00D434E` |
| **core** | `RED_Ultimate/core/` | 239 | ~1,475 | 6.46 MB | `core.zip` | 1,989,081 B | `08ABF36D2B659AFB095521A10799A9D62C8EDD87402D3D43BBC196683BB8B7EE` |
| **feature** | `RED_Ultimate/feature/` | 282 | ~818 | 6.77 MB | `feature.zip` | 1,943,298 B | `30E1E8C1DCE07DC84976F2D7BD79BD96E6B3E93521975041EBA8F3A50DF56167` |
| **demo** | `RED_Ultimate/demo/` | 92 | ~417 | 2.31 MB | `demo.zip` | 2,691,321 B | `6A4FD9DEC5169513799A22D1FBC58ED08C62DFA1A583B7DBC1CDE7E52D656B96` |
| **المجموع** | | **951** | **~3,730** | **~20.98 MB** | **4 zip + SHA256SUMS.txt** | **7.3 MB** | — |

---

## سبب الإزالة (من OLD_MODULES_DECISION_2026-09-16.md)

### 1. غير مشمولة في Build Graph
```kotlin
// settings.gradle.kts — الأسطر 39-43 فقط
include(":app")
project(":app").projectDir = file("red-app")
include(":shared-proto")
// لا يوجد: include(":lib") / include(":core") / include(":feature") / include(":demo")
```
- **لا `include`** لأي من الوحدات الأربع
- **لا `project(":lib:...")`** في `red-app/build.gradle.kts` أو `backend-server/build.gradle.kts`
- **لا reference** في dependencyResolutionManagement

### 2. صفر اعتماديات فعلية
```powershell
# تحقق من استيراد org.whispersystems/org.thoughtcrime
Get-ChildItem -Recurse -File -Include *.kt,*.java -Path red-app\src,backend-server\src | Select-String -Pattern "org\.whispersystems|org\.thoughtcrime"
# COUNT = 0
```

### 3. Signal Fork Code = Dead Code
- `lib/archive/` — 14 ملف .kt (Backup/Binary serialization) — **غير مستخدم**
- `lib/libsignal-service/` — 18+ ملف (Archive API) — **غير مستخدم**
- `lib/network/ArchiveApi.kt` — 23,113 سطر — **غير مستخدم**
- `core/util/DeadlockDetector.kt` — **غير مستخدم**
- `feature/registration/restoreselection/` — 8 ملفات (Archive restore UI) — **غير مستخدم**
- `demo/` — 12 وحدة demo (apng، camera، contacts، إلخ) — **غير مستخدم**

### 4. لا وجود لـ Dead Code Markers
```powershell
# لا مجلدات dead-code/dead_code/deadcode
Get-ChildItem -Recurse -Directory -Filter "*dead*" RED_Ultimate\lib, RED_Ultimate\core, RED_Ultimate\feature, RED_Ultimate\demo
# COUNT = 0

# لا ملفات *.archived
Get-ChildItem -Recurse -File -Filter "*.archived" RED_Ultimate\lib, RED_Ultimate\core, RED_Ultimate\feature, RED_Ultimate\demo
# COUNT = 0
```

### 5. لا reference في Gradle (40 ملف build.gradle.kts/.gradle)
```powershell
Select-String -Pattern "project\(" RED_Ultimate\lib, RED_Ultimate\core, RED_Ultimate\feature, RED_Ultimate\demo -Include *.gradle.kts,*.gradle
# لا توجد references إلى هذه الوحدات
```

---

## محتويات الأرشيف بالتفصيل

### lib.zip (674 KB) — "Gold Mine" Archived
```
lib/
├── archive/                    # 14 ملفات — Backup serialization (EncryptedBackupReader/Writer، Backup.proto)
├── libsignal-service/          # 18+ ملفات — SignalService Archive API
├── network/                    # ArchiveApi.kt (23K lines)
├── apng/، blurhash/، contacts/، debuglogs-viewer/، device-transfer/، donations/، glide/، image-editor/، paging/، photoview/، qr/، spinner/، sticky-header-grid/، video/
└── build.gradle.kts (18 subprojects)
```
**سبب الأرشفة:** يحتوي على `archive` + `libsignal-service` + `network` — **قيمة عالية للاسترجاع المستقبلي** (Backup/Binary serialization، SignalService API). لكن **صفر استخدام حالي**.

### core.zip (1.9 MB) — Core Utilities
```
core/
├── models/، models-jvm/
├── network/                    # Network utilities
├── serialization/              # Serialization helpers
├── ui/، util/، util-jvm/
└── core/util/DeadlockDetector.kt  # destacado في التدقيق
```
**سبب الأرشفة:** `util/network/serialization` مفيدة نظرياً لكن **صفر استيراد** من الوحدات الحية.

### feature.zip (1.9 MB) — Feature Modules
```
feature/
├── camera/                     # Camera integration
├── media-send/                 # Media sending
└── registration/
    └── restoreselection/       # 8 ملفات — Archive restore UI (ArchiveRestoreSelectionScreen.kt، ViewModelTest.kt)
```
**سبب الأرشفة:** `registration/restoreselection` مرتبط بـ Signal archive — **غير مستخدم**. `camera/media-send` قد تفيد لكن **لا اعتماديات**.

### demo.zip (2.6 MB) — Demo Apps
```
demo/
├── apng/، camera/، contacts/، debuglogs-viewer/، device-transfer/
├── donations/، image-editor/، paging/، qr/، registration/
├── spinner/، video/
```
**سبب الأرشفة:** **Demo apps فقط** — تعتمد على `lib`/`feature`/`core` المؤرشفة. **لا قيمة إنتاجية**.

---

## التحقق من الأرشفة (SHA256SUMS.txt)

**ملف:** `D:\pro_archive_2026-09-16\old-modules\SHA256SUMS.txt`
```
B5904B835DA85B301D7BAD1BD67D5499972F376C0DBED63D658CF4BEC00D434E  core.zip
08ABF36D2B659AFB095521A10799A9D62C8EDD87402D3D43BBC196683BB8B7EE  demo.zip
30E1E8C1DCE07DC84976F2D7BD79BD96E6B3E93521975041EBA8F3A50DF56167  feature.zip
6A4FD9DEC5169513799A22D1FBC58ED08C62DFA1A583B7DBC1CDE7E52D656B96  lib.zip
```

**أمر التحقق:**
```powershell
cd D:\pro_archive_2026-09-16\old-modules
Get-FileHash -Algorithm SHA256 core.zip, demo.zip, feature.zip, lib.zip | Format-Table -AutoSize
# يجب أن يطابق SHA256SUMS.txt تماماً
```

---

## ما تم نقله أيضاً إلى الأرشيف (غير الوحدات الأربع)

| العنصر | المسار الأصلي | المسار في الأرشيف | السبب |
|---------|--------------|------------------|-------|
| `.git-temp/` | `RED_Ultimate/.git-temp/` | `D:\pro_archive_2026-09-16\.git-temp/` + `.git-temp.zip` (2.47 GB) | نسخة احتياطية .git كاملة |
| `red-sha1/` | `RED_Ultimate/red-sha1/` | `D:\pro_archive_2026-09-16\red-sha1/` + `red-sha1.zip` (191 MB) | أرشيف SHA1 قديم |
| `bundles/` | `RED_Ultimate/bundles/` | `D:\pro_archive_2026-09-16\bundles/` + `bundles.zip` (148 MB) | AAB bundles |
| `apks/` | `RED_Ultimate/apks/` | `D:\pro_archive_2026-09-16\apks/` (2.75 GB، 10 APKs) | APKs خام |
| `apks-stray/` | `RED_Ultimate/apks-stray/` | `D:\pro_archive_2026-09-16\apks-stray/` (818 MB، 3 APKs مكررة) | APKs مكررة |
| `dead-backend-shadow/` | `D:\pro_new\pro_new\backend-server/` | `D:\pro_archive_2026-09-16\dead-backend-shadow/` (فارغ) | Backold shadow repo |
| `nested-git-backup/` | متفرقة | `D:\pro_archive_2026-09-16\nested-git-backup/` (26 KB، 20 ملف) | .git backups |
| `gradle/` | `RED_Ultimate/gradle/verification-metadata.xml.disabled` | `D:\pro_archive_2026-09-16\gradle/` (699 KB) | Verification metadata معطل |
| `env-backup/` | 4 ملفات .env الحالية | `D:\pro_archive_2026-09-16\env-backup/` (22 KB) | نسخ احتياطية .env |
| `env-history/` | 9 ملفات .env.before-* | `D:\pro_archive_2026-09-16\env-history/` (89 KB) | تاريخ .env |
| `docs-duplicates/` | 1 ملف markdown مكرر | `D:\pro_archive_2026-09-16\docs-duplicates/` (5 KB) | مستند مكرر |

---

## ملفات README في الأرشيف (للاسترجاع المستقبلي)

كل وحدة مؤرشفة لها `README.md` في الأرشيف يصف:
- الغرض الأصلي
- سبب الإزالة
- محتويات البارزة
- كيفية الاسترجاع (unzip + إضافة `include` في settings.gradle.kts)

---

## قرارات بشرية مطلوبة (ما بقي)

| # | العنصر | التوصية |
|---|---------|----------|
| 1 | **lib/archive + libsignal-service** | **احتفظ في الأرشيف** — قيمة عالية لـ Backup/SignalService API المستقبلي. لا تسترجع إلا عند الحاجة الفعلية. |
| 2 | **core/util/network/serialization** | **احتفظ في الأرشيف** — قد يفيد shared-proto أو backend مستقبلاً. |
| 3 | **feature/registration/restoreselection** | **احذف نهائياً** — مرتبط بـ Signal archive المزول. لا قيمة بدون archive. |
| 4 | **demo/** | **احذف نهائياً** — demo apps فقط، لا قيمة إنتاجية. |
| 5 | **apks-stray/** (818 MB) | **احذف** — مكررة SHA256 مع `apks/`. |
| 6 | **.git-temp.zip** (2.47 GB) | **قرار مطلوب** — أرشيف .git كامل. هل يُحتفظ للتاريخ؟ أم يُحذف بعد التحقق؟ |
| 7 | **bundles.zip + red-sha1.zip** (348 MB) | **احتفظ مؤقتاً** — قد يحتاج للـ rollback. |

---

## سجل Git المرتبط

| Commit | الرسالة | التاريخ |
|--------|---------|---------|
| `bc8cbaa` | `chore(repo): untrack 50 junk files, tighten gitignore, document archive` | 2026-09-16 |
| `7cd2083` | `chore: remove DINSTAR/Yemen, clean CI workflows` | 2026-09-15 |
| `4b7313f` | `chore: rebase fix dashboard merge` | 2026-09-14 |
| (Phase 11) | حذف `app/` Signal fork (~7000 ملف) | 2026-09-14 |
| (Unification) | دمج `android/` + `app-android/` → `red-app/` | 2026-08-19 |

---

## أمر الاسترجاع (إذا لزم مستقبلاً)

```bash
# استرجاع وحدة واحدة (مثال: lib)
cd D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate
unzip D:\pro_archive_2026-09-16\old-modules\lib.zip -d .

# إضافة إلى settings.gradle.kts
# include(":lib:archive")
# include(":lib:libsignal-service")
# ... إلخ لكل subproject

# تحديث red-app/build.gradle.kts إذا لزم
# implementation(project(":lib:archive"))
```

**تحذير:** الاسترجاع يتطلب مراجعة شاملة للاعتماديات والتأكد من عدم تعارض مع `red-app` الحالي.

---

*تم إنشاؤه في 2026-09-16 — الوكيل #5 (توثيق شامل نهائي)*