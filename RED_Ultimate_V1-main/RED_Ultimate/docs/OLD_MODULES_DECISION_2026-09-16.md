# قرار الوحدات القديمة lib / core / feature / demo — 2026-09-16

> **الوكيل:** إصلاح #3 — تحقق ثم عزل موثّق (لا حذف نهائي).
> **مساحة العمل:** `D:\pro_new\pro_new\RED_Ultimate_V1-main\RED_Ultimate`
> **الممنوعات المحترمة:** لم يُحذف `lib/core/feature/demo`، لم تُضَف إلى `settings.gradle.kts`، لم يُنفَّذ أي `commit`.

---

## 1) إثبات أنها خارج البناء

### 1.1 `settings.gradle.kts` — فقط `:app` + `:shared-proto`

المقتطف الحي (سطور 39 و 43):

```kotlin
include(":app")
project(":app").projectDir = file("red-app")
// ...
include(":shared-proto")
```

السطران الوحيدان من نوع `include(":...")` الدائم هما أعلاه. الاستثناءات المشروطة فقط:

- `if (!skipBuildLogic) includeBuild("build-logic")` — composite للـ QA فقط، لا يُدخِل `lib/core/feature/demo`.
- `if (buildLogicRequested) include(":fast-lint")` — فقط عندما تُطلب مهام `buildQa/qa/ci/qualityGate/format`.

لا يوجد أي `include(":lib...")` / `include(":core...")` / `include(":feature...")` / `include(":demo...")` في `settings.gradle.kts`.
التعليق أعلى الملف نفسه يصرّح أن `backend-server` مشروع Spring مستقل خارج build graph ويُبنى عبر `scripts/build-backend.sh`، وأن `red-app/` هو منتج الأندرويد الوحيد (بعد دمج `android/` و `app-android/` وحذفهما في 2026-08-19، وحذف `app/` Signal fork في Phase 11 بتاريخ 2026-09-14).

### 1.2 كل مجلد يحوي README يصرّح أنه خارج البناء

| المجلد | ملف README | نص الحالة |
|---|---|---|
| `lib/` | `lib/README.md` | `Gold mine مرجعي — غير مدرج` + «وجود المصدر لا يعني أنه مفعّل — راجع `settings.gradle.kts`» |
| `core/` | `core/README.md` | `مرجع — غير مدرج` + نفس الإحالة إلى `settings.gradle.kts` و `W0_MODULE_BOUNDARIES.md` |
| `feature/` | `feature/README.md` | `مرجع — غير مدرج` + «تسجيل RED القانوني في `red-app` و `backend-server` ولا يعتمد هذه الوحدة» |
| `demo/` | `demo/README.md` | `مرجع/عينات — غير مدرج` + «ليست تطبيقات RED ولا تُنشر» |

كلها تحيل إلى `W0_MODULE_BOUNDARIES.md` (بوابة البناء والملكية) وإلى `docs/01-PROJECT-OVERVIEW.md`.

### 1.3 لا كود حي يستورد منها

**أ) الحزم الإرثية `org.whispersystems` / `org.thoughtcrime` = صفر تمامًا في الكود الحي:**

```powershell
Get-ChildItem -Recurse -File -Include *.kt,*.java -Path red-app\src,backend-server\src |
  Select-String -Pattern "org\.whispersystems|org\.thoughtcrime"
# COUNT = 0
```

**ب) `org.signal.*` في الكود الحي = فقط Maven الخارجي `org.signal.libsignal.protocol` (44 سطرًا)، وليس الوحدات المحلية:**

- الملفات: `red-app/src/main/java/com/red/sovereign/auth/DeviceKeyManager.kt` (8)، `crypto/PersistentSignalProtocolStore.kt` (17)، `crypto/SignalSessionManager.kt` (10)، `features/lan/LanSignalCrypto.kt` (5)، `groups/GroupCryptoManager.kt` (4) — المجموع 44.
- كلها `import org.signal.libsignal.protocol.*` القادم من `libs.libsignal.android` (AAR مركزي 0.86.5 عبر `local-maven` → Google mirror → repo1 مع تثبيت SHA-256 في `gradle/verification-metadata.xml`)، وليست `project(":lib:...")`.
- الدليل: `red-app/build.gradle.kts` يحوي `implementation(libs.libsignal.android)` ولا يحوي أي `project(":lib/:core/:feature/:demo")`. السطر الوحيد من نوع `project(` فيه هو `implementation(project(":shared-proto"))`. وكذلك `backend-server/build.gradle.kts` سطره الوحيد `implementation(project(":shared-proto"))`.

**ج) المراجع `project(":lib/:core/:feature/:demo")` موجودة فقط *داخل* الشجرة القديمة نفسها (40 ملف `build.gradle.kts`/`build.gradle` تحت `lib/core/feature/demo/microbenchmark/build-logic`)، ولا يستهلكها أي build حي لأن Gradle لا يقرأ تلك الملفات أصلًا ما دامت غير مُضمَّنة في `settings.gradle.kts`.**

الخلاصة: الشجرة القديمة مغلقة على نفسها ومرجعية فقط.

---

## 2) إثبات أن حذفها من القرص الآن خطير — لذلك لم تُحذف

### 2.1 تاريخ Signal مرجعي حي داخلها (أمثلة مؤكدة على القرص)

- `lib/archive/src/main/java/org/signal/archive/` — ‏14 ملف `.kt` (منها `stream/EncryptedBackupReader.kt` ‏9088 بايت، `EncryptedBackupWriter.kt`، `Backup.proto`/`LocalArchive.proto`) — مرجع Backup/Binary الوحيد.
- `lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/archive/` — ‏18+ ملفًا (`ArchiveCredentialPresentation.kt` … `TransferArchiveResponse.kt`) — مرجع شكل API الأرشفة.
- `lib/network/src/main/java/org/signal/network/api/ArchiveApi.kt` ‏(23113 بايت).
- `core/util/src/main/java/org/signal/core/util/concurrent/DeadlockDetector.kt`.
- `feature/registration/src/main/java/org/signal/registration/screens/restoreselection/` — ‏8 ملفات (`ArchiveRestoreSelectionScreen.kt` … `ViewModelTest.kt`).
- تعليق `settings.gradle.kts` سطر 36–38 يشهد أن `app/` (فورك Signal ‏~7000 ملف) حُذف في Phase 11 بعد استخراج 5 أصوات، وأن `android/` و `app-android/` دُمجا في `red-app/` وحُذفا في 2026-08-19 — أي أن `lib/core/feature/demo` هي الآن **البقية المرجعية الوحيدة** لهذا التاريخ على القرص. حذفها يقطع سلسلة الاستخراج/التدقيق (W0 gate رقم 4: «لا يُحذف كود Signal إلا بعد سجل استخراج/اعتماد صريح لكل مكتبة/سكيمة مختارة»).

### 2.2 أصوات `res/raw` مستخرجة منها وتُستخدم في المنتج

`red-app/src/main/res/raw/` (6 ملفات حية):

| الملف | الحجم (بايت) |
|---|---|
| `notification_simple_01.ogg` | 11295 |
| `redphone_busy.opus` | 38214 |
| `redphone_outring.opus` | 20570 |
| `typing_dots.json` | 2867 |
| `webrtc_completed.mp3` | 12525 |
| `webrtc_disconnected.mp3` | 13884 |

حذف الشجرة المرجعية الآن يمنع أي مقارنة/إعادة استخراج/تدقيق ترخيص مستقبلي لهذه الأصول (نغمات تقدم المكالمة `redphone_*` / `webrtc_*`).

### 2.3 القرار: إبقاء مؤقت + أرشفة خارجية لاحقة (لا حذف قرص الآن)

| الوحدة | الحجم (ملفات / بايت / م.ب) | `.kt` | سبب الإبقاء المؤقت | خطة الأرشفة الخارجية المقترحة (خارج Git الحي) |
|---|---|---|---|---|
| `lib/` (‏18 مجلدًا: `apng/archive/billing/blurhash/contacts/debuglogs-viewer/device-transfer/donations/glide/image-editor/libsignal-service/network/paging/photoview/qr/spinner/sticky-header-grid/video`) | ‏1020 ملف / 5701413 بايت (5.44 م.ب) | 338 | Gold mine: ‏`archive` + `libsignal-service` + `network` مرجع Backup/API؛ أي استخراج مستقبلي يحتاج المقارنة | `D:\pro_archive_2026-09-16\old-modules\lib.zip` + `SHA256.txt` + نسخة `README.md`؛ يُحفظ خارج الريبو؛ لا `git rm` إلا بقرار لاحق بعد التحقق من الـ zip |
| `core/` (‏7: `models/models-jvm/network/serialization/ui/util/util-jvm`) | ‏1475 ملف / 6778936 بايت (6.46 م.ب) | 239 | طبقات `util/network/serialization` التاريخية؛ `core/util/DeadlockDetector.kt` وأمثاله مرجع تشخيص | `D:\pro_archive_2026-09-16\old-modules\core.zip` + SHA256؛ نفس البوابة |
| `feature/` (‏3: `camera/media-send/registration`) | ‏818 ملف / 7095279 بايت (6.77 م.ب) | 282 | `registration/restoreselection` مرجع شاشات الاستعادة؛ `camera/media-send` مرجع وسائط | `D:\pro_archive_2026-09-16\old-modules\feature.zip` + SHA256؛ نفس البوابة |
| `demo/` (‏12: `apng/camera/contacts/debuglogs-viewer/device-transfer/donations/image-editor/paging/qr/registration/spinner/video`) | ‏417 ملف / 2420812 بايت (2.31 م.ب) | 92 | عينات اختبار المكتبات فقط؛ تُبقي الشجرة القديمة قابلة للفهم ككل (مراجع `demo → lib/feature/core` الداخلية) | `D:\pro_archive_2026-09-16\old-modules\demo.zip` + SHA256؛ نفس البوابة |
| **المجموع** | **‏3730 ملفًا / ‏21997440 بايت (~20.98 م.ب)** | **951** | — | أرشفة رباعية منفصلة (لا أرشيف واحد) حتى يمكن استعادة وحدة واحدة دون البقية |

قاعدة W0 المطبقة: «Git history هو الأرشيف؛ لا تُكرر آلاف الملفات تحت مجلد `archive/`» — لذلك الأرشفة المقترحة **خارج الريبو** (`D:\pro_archive_*`) وليست مجلدًا جديدًا داخل المشروع.

---

## 3) الملفات الميتة التي تلوّث العد الجذري — ذُكرت دون نقل

- **داخل `lib/core/feature/demo` نفسها: صفر تلوث جذري.** لا يوجد أي `.kt` في المستوى المباشر لكل من `lib/` و `core/` و `feature/` و `demo/` (فحص `Get-ChildItem -File -Filter *.kt -Path <dir>` غير العميق = 0 لكل منها). كل `.kt` الـ 951 تقع في أعماق `src/...` وهي جزء من الوحدة المرجعية، لا ملفات يتيمة في الجذر.
- **لا يوجد مجلد `dead-code`/`dead_code`/`deadcode` داخل مساحة العمل** (بحث `Get-ChildItem -Recurse -Directory` = لا نتيجة).
- **لا يوجد أي `*.archived` على القرص حاليًا** (`COUNT = 0`) — مجلد `red-app/src/main/java/com/red/sovereign/_archive/` يحوي فقط `README.md` (‏11864 بايت) يوثق ملفات `.kt.archived` السابقة التي أُزيلت فعلًا، ولا يحوي أي `.kt` حي (فحص `Get-ChildItem -Recurse -File _archive` = ملف واحد فقط). لا يُنقل ولا يُحذف.
- **النسخ التاريخية في `docs/archive/` (مئات `.kt` في `merge-2026-09-12/*` و `verify-unique-2026-08-23/*` و `superseded-2026-09-12/PstnSipEngine.kt`) هي خارج البناء أصلًا** (لا يقرأها Gradle، ولا تُحتسب في عد `red-app/src` ‏464 ملفًا / `backend-server/src` ‏284 ملفًا)، وتُترك كما هي دون نقل عملًا بمبدأ «التحقق ثم العزل الموثق».
- **الـ 40 ملف `build.gradle(.kts)` تحت `lib/core/feature/demo` ميتة حكمًا** (لا يقرأها Gradle لعدم التضمين) لكنها جزء من سلامة الشجرة المرجعية — تُذكر هنا ولا تُنقل ولا تُحذف.

---

## 4) ظل `backend-server/` الميت في جذر `D:\pro_new\pro_new` — نُقل بدل حذفه

### الأدلة

- **فارغ من الملفات:** `(Get-ChildItem -Recurse -File "D:\pro_new\pro_new\backend-server" | Measure-Object).Count = 0`. البنية الوحيدة هي `build/libs/red-backend.jar` وهو **مجلد فارغ** (Attributes=Directory، صفر ملف تحته)، من بقايا بناء قديم بتاريخ 2026-08-17 — ظل ميت، وليس الباكند الحي.
- **الباكند الحي في مكان آخر ولم يُمس:** `RED_Ultimate/backend-server/` يحوي 27 مدخلًا (`src/` ‏284 ملفًا، `Dockerfile`، `build.gradle.kts`، `settings.gradle.kts` الخاص به …).
- **لا يشير إليه أي سكربت حي بالنمط المطلوب:**

```powershell
# في RED_Ultimate/scripts وفي D:\pro_new\pro_new\scripts معًا:
Get-ChildItem -Recurse -File -Path scripts | Select-String -Pattern "^\s*backend-server/"
# COUNT = 0 في كليهما
```

الإشارات الأوسع إلى `backend-server` في `RED_Ultimate/scripts/*` (مثل `build-backend.sh: cd "$ROOT/backend-server"` و `check-android-integrity.py: ROOT/"backend-server/src/..."`) كلها نسبية إلى جذر `RED_Ultimate` (الباكند الحي)، وليست إلى ظل `D:\pro_new\pro_new\backend-server`.

### القرار المنفذ

نُقل الظل الميت كاملًا (بدل حذفه) إلى:

```text
D:\pro_archive_2026-09-16\dead-backend-shadow\
```

(يحوي بداخله `build/libs/red-backend.jar/` الفارغ كما هو للتدقيق). النقل بـ `Move-Item` فقط، دون حذف نهائي، ودون أي `commit`.

---

## 5) أوامر التحقق المعادة (للتدقيق اللاحق)

```powershell
# 1) البناء: فقط :app + :shared-proto
Select-String -Pattern "include\(" -Path settings.gradle.kts
# → 39:include(":app") ، 43:include(":shared-proto") (+ سطر مشروط fast-lint للـ QA فقط)

# 2) لا استيراد إرثي حي
Get-ChildItem -Recurse -File -Include *.kt,*.java -Path red-app\src,backend-server\src |
  Select-String -Pattern "org\.whispersystems|org\.thoughtcrime" | Measure-Object
# → 0
Get-ChildItem -Recurse -File -Include *.kt,*.java -Path red-app\src,backend-server\src |
  Select-String -Pattern "org\.signal" | Measure-Object
# → 44 — كلها org.signal.libsignal.protocol (Maven) وليست الوحدات المحلية

# 3) لا اعتماد حي على الوحدات القديمة
Select-String -Pattern "project\(" -Path red-app\build.gradle.kts,backend-server\build.gradle.kts
# → فقط project(":shared-proto") في كل منهما

# 4) الظل الميت (بعد النقل: المصدر لم يعد موجودًا)
(Get-ChildItem -Recurse -File "D:\pro_new\pro_new\backend-server" -ErrorAction SilentlyContinue | Measure-Object).Count
Get-ChildItem -Recurse -File -Path scripts | Select-String -Pattern "^\s*backend-server/" | Measure-Object
```

---

*أُنشئ 2026-09-16 — وكيل إصلاح #3. التزم المنع: لا حذف لـ `lib/core/feature/demo`، لا إضافة إلى `settings.gradle.kts`، لا `commit`.*
