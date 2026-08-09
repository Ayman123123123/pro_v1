# 🔴 التقرير الفني الشامل — مشروع RED Ultimate (RED_Sovereign_Final)

> **تاريخ الفحص:** 2026-08-03
> **نطاق الفحص:** المستودع `RED_Ultimate_V1` بما فيه من نسخ ZIP الثلاث، مع فحص سطري دقيق لجميع المكونات: تطبيق أندرويد، الخادم الخلفي، لوحة التحكم، وسيط الوسائط، بوابة PSTN، البروتوكولات، البنية التحتية، وأدوات البناء.
> **حجم المشروع:** ~9481 ملفاً، ~88MB، ~795 ألف سطر (Kotlin/Java + XML).

---

## 1) الملخص التنفيذي

المشروع هو محاولة لبناء "منظومة مراسلة سيادية" محلية (RED) عن طريق **نسخ كود سيجنال (Signal-Android) مفتوح المصدر (AGPLv3)** وإعادة تسميته (`org.thoughtcrime.securesms` ← `com.red.sovereign`)، مع إضافة طبقة مخصصة تدّعي ثلاث أنظمة:

- **النظام A:** مكالمات VoIP بجودة 4K/AV1 عبر خادم SFU محلي (mediasoup).
- **النظام B:** بوابة اتصالات خلوية (GSM/PSTN) عبر جهاز DINSTAR UC2000 وأستريكس.
- **النظام C:** رسائل بتوصيل مضمون (UUID v7 + ACK + مزامنة فجوات) عبر WebSocket وMongoDB/Redis.
- بالإضافة إلى: تسجيل بموافقة إدارية (Admin Approval)، قصص تحذف خلال 24 ساعة، لوحة تحكم مدير، و"تشفير مقاوم للحواسيب الكمومية".

### الحكم الفني النهائي

**المشروع في حالته الحالية لا يُبنى ولا يعمل إطلاقاً.** ينهار في مرحلة إعداد Gradle، وتوجد أخطاء ترجمة متعددة في تطبيق أندرويد والخادم الخلفي، ولوحة التحكم ووسيط الوسائط غير مكتملين، وملفات Docker للبناء ناقصة. وحتى لو تم إصلاح كل أخطاء الترجمة، فإن **معظم الميزات "المخصصة" المعلن عنها هي محاكاة (Stub)** تطبع رسائل فقط ولا تنفذ أي منطق حقيقي، وبعض الادعاءات متناقضة (4K مقابل 1080p)، والادعاء "بقطع الاتصال عن سحابة سيجنال" غير صحيح — فإعدادات الشبكة ما زالت تحتوي عناوين خوادم سيجنال الفعلية.

**التقييم الإجمالي (من 10): 1.5/10** — كفكرة طموحة جداً، وكناتج برمجي قابل للتشغيل فهو غير مكتمل في جميع طبقاته.

---

## 2) منهجية الفحص والتحقق

اعتمد الفحص على مزيج من:

1. **فك ضغط النسخ الثلاث** (`RED_Sovereign_Final (1).zip`، `RED_Sovereign_Final_Production_Build.zip`، `workspace-019fc4ca-...zip`) ومقارنتها (وجدنا النسخة الأخيرة هي نفس كود نسخة "Production Build" مع اختلافات توثيقية فقط).
2. **قراءة سطرية كاملة** لكل ملفات المشروع المخصصة (RED): 34 ملفاً في الخادم الخلفي، 26 ملفاً في `com.red.sovereign`، 8 ملفات في `developed`، كل ملفات لوحة التحكم (16)، وسيط الوسائط، البروتوكولات، وملفات البنية التحتية.
3. **فحص آلي للاتساق**: تتبع المراجع (`grep`) للفئات المذكورة في الاستيرادات مقابل تعريفاتها، عدّ الفئات المكررة، فحص إعادة التسمية الشاملة.
4. **تحقق من صحة البنية**: فحص ملفات Gradle/Docker/YAML/JS والنقطة المرجعية للأوامر.
5. **محاولة إنشاء قائمة أخطاء ترجمة حتمية** (compile-time) مقابل أخطاء تشغيل (runtime) مقابل أخطاء تصميم/أمن.

> ملاحظة منهجية: لم تُنفَّذ عملية بناء فعلية لـ Gradle/Android داخل بيئة الفحص لتعذّر توفير Android SDK وNDK والشبكة المطلوبة لتنزيل التوزيعة، لكن كل النتائج المذكورة في هذا التقرير مؤكدة سكونياً من الكود نفسه (مراجع غير محلولة، فئات مكررة، ملفات ناقصة، إلخ).

---

## 3) نظرة عامة على المستودع والنسخ الثلاث

| الملف | الحجم | عدد الملفات | الملاحظة |
|---|---|---|---|
| `RED_Sovereign_Final (1).zip` | 23MB | 9949 | النسخة الأولى: Signal 8.21.3 باسم `org.thoughtcrime.securesms` مع تغيير عناوين السيرفر إلى `chat.red.local`، وتضم مجلدات قديمة (`android/`, `app-android/`, `server/`, `admin-dashboard/`, `demo/`, `benchmark/`, `microbenchmark/`). |
| `RED_Sovereign_Final_Production_Build.zip` | 21MB | 9472 | النسخة "الإنتاجية": إعادة تسمية كاملة إلى `com.red.sovereign` + ملفات RED الجديدة. |
| `workspace-019fc4ca-...zip` | 68MB | 9484 | النسخة الأخيرة (المرفقة): تطابق كودياً نسخة الإنتاج تماماً مع إضافة `MASTER_GUIDE.md` وملفات `declared_deps.txt`, `imports_list.txt`, `used_imports.txt`. **هذه هي النسخة محل التحليل.** |

الخلاصة: **توجد 3 نسخ متداخلة من نفس المشروع، أحدها فقط (الثالثة) هو "النهائي"، والباقي نسخ قديمة مكررة** — مشكلة إدارة إصدارات بحد ذاتها.

---

## 4) البنية العامة للمشروع (النسخة النهائية)

```
RED_Ultimate/
├── app/                     # تطبيق أندرويد (Signal معاد تسميته + طبقة RED)
│   ├── src/main/java/com/red/sovereign/        # ملفات RED المخصصة (26 ملفاً)
│   ├── src/main/java/org/thoughtcrime/securesms/ # كود Signal بعد إعادة التسمية (3797 ملف)
│   ├── src/androidTest/ + src/test/            # اختبارات Signal الأصلية
│   └── jni/ + proguard/ + lint-baseline.xml
├── core/                    # وحدات أساسية (util, ui, models, network, serialization)
├── lib/                     # مكتبات (libsignal-service, network, glide, video, ...)
├── feature/                 # ميزات (camera, media-send, registration فقط)
├── backend-server/          # خادم Spring Boot 3.4 (34 ملف Kotlin — 1120 سطر)
├── admin_dashboard/         # لوحة تحكم React 19 (16 ملفاً — ~800 سطر)
├── media-sfu/               # وسيط ميديا Node.js/mediasoup (ملفان)
├── pstn-asterisk/           # إعدادات أستريكس لبوابة DINSTAR (5 ملفات)
├── shared-proto/            # تعريفات ProtoBuf (ملفان فقط — غير مربوطة بالبناء)
├── build-logic/, fast-lint/, lintchecks/, wire-handler/, reproducible-builds/
├── infrastructure/setup-env.sh
├── docker-compose.yml, nginx.conf, build-and-run.sh
└── build.gradle.kts, settings.gradle.kts, gradle.properties
```

إحصاءات سريعة:
- Kotlin: ~136 ألف سطر — Java: ~35 ألف سطر — XML: ~157 ألف سطر — Gradle: ~2.7 ألف — JS/TS: ~1.4 ألف.
- تطبيق أندرويد وحده: 3797 ملف مصدر رئيسي + 1850 ملف XML موارد + 391 ملف اختبار.
- الخادم الخلفي: 1120 سطراً فقط لكل "منطق السيادة" المزعوم.
- لوحة التحكم: 800 سطر فقط، بدون نقطة دخول.

---

## 5) التحليل التفصيلي لكل مكوّن

### 5.1 تطبيق أندرويد (app)

#### 5.1.1 إعادة التسمية الشاملة — إيجابية التنفيذ

- أُعيدت تسمية الحزمة `org.thoughtcrime.securesms` ← `com.red.sovereign` عبر كل الملفات (3768 ملفاً) بدقة عالية: **0 مرجع متبقٍ** للحزمة القديمة في كود `src/main`.
- أُعيدت تسمية صفوف داخل الحزم الأصلية بشكل متسق: `SignalExecutors` ← `REDExecutors`، `SignalStore` ← `REDStore`، `SignalServiceAttachment` ← `REDServiceAttachment`، `SignalWebSocket` ← `REDWebSocket`، `ConscryptProvider` ← `ConscryptRED`، `SignalGlideCodecs` ← `REDGlideCodecs`... وكلها تُعرَّف بنفس الاسم الجديد حيث تُستدعى.
- ملفات الموارد والـ Manifest ومفاتيح النمط (styles) موجودة وصحيحة (`ic_launcher`, `TextSecure.LightTheme`, `automotive_app_desc`).

> هذا الجزء — لوحده — منفَّذ بكفاءة. لكنه لا يكفي لسلامة المشروع كما سيظهر.

#### 5.1.2 ملفات RED المخصصة (com.red.sovereign) — قراءة وتحليل

| الملف | الوظيفة المزعومة | الواقع المكتشف |
|---|---|---|
| `RedSovereignApp.kt` | تطبيق رئيسي يشغّل المحركات | **لا يمدّد `ApplicationContext`** (فئة Signal الأساسية) ⇒ كل نظام Signal (قاعدة البيانات، JobManager، الإشعارات، التشفير...) لن يُهيّأ أبداً. ويستورد فئتين غير موجودتين أصلاً: `RedVoipMaster`, `IdentityManager` ⇒ **خطأ ترجمة**. |
| `MainActivity.kt` (في com/red/sovereign) | شاشة Compose بسيطة (splash → auth → main) | يستورد `RedSplashScreen`, `REDTheme`, `SovereignAuthScreensKt` — **الفئتان الأوليان غير موجودتين في المشروع إطلاقاً** ⇒ خطأ ترجمة. |
| `MainActivity.kt` (في org/thoughtcrime/securesms — نفس الحزمة!) | كود Signal الرئيسي الكامل | **تعريف مكرر**: الفئة `com.red.sovereign.MainActivity` معرّفة في ملفين بنفس الحزمة ⇒ خطأ "Duplicate class" يمنع الترجمة قطعياً. |
| `core/auth/RedIdentityManager.kt` | ربط الهوية السيادية بعد موافقة المدير | منطق بسيط على SharedPreferences **بدون تشفير**؛ يخزّن `AUTH_TOKEN`, `GSM_NUMBER`, `RED_ID` نصاً صريحاً. |
| `core/crypto/QuantumGuard.kt` | "تشفير مقاوم للكم" | `wrapWithQuantum()` **يعيد النص كما هو دون أي تغليف** (محاكاة صريحة). "البذرة الكمومية" مجرد `SecureRandom`. |
| `core/database/RedMasterDatabase.kt` | جداول الرسائل/المجموعات/السجلات | يحتاج Room لكن **Room غير مضاف للاعتماديات** في `app/build.gradle.kts` ⇒ خطأ ترجمة. و`MasterDao` المذكور في `RedDeliveryEngine` **غير موجود** (الموجود `RedDao`). |
| `core/di/RedMasterModule.kt` | حقن Hilt | **`addMigrations()` بلا وسائط ⇒ خطأ ترجمة** (تحتاج وسيطاً واحداً على الأقل). يستورد `RedVoipMaster` و`PstnViewModel` غير الموجودين. |
| `core/delivery/MasterDeliveryEngine.kt` | محرك التوصيل المضمون UUID v7 | توليد UUID v7 موجود فعلاً (صيغة سليمة)، لكن `ChatViewModel` يستدعي `dispatchMessage()` وهي **غير معرّفة في هذه الفئة** (معرّفة في `RedDeliveryEngine` المنافسة) ⇒ خطأ ترجمة. ويستورد `RedWebSocketClient` غير الموجود. |
| `core/delivery/RedDeliveryEngine.kt` | محرك موازٍ مكرر | يستخدم `MasterDao`, `getMessageStatus()`, `updateMessageStatus()` — **كلها غير موجودة** ⇒ خطأ ترجمة. |
| `core/delivery/SyncEngine.kt` | إصلاح فجوات التسلسل | منطق سليم نظرياً، لكن `RedWebSocketClient` غير موجود ⇒ خطأ ترجمة. |
| `core/utils/RedMediaTransporter.kt` | رفع الملفات إلى MinIO | يستورد `MinioUploader` غير الموجود ⇒ خطأ ترجمة. و`File(uri.path!!)` ممارسة خاطئة على Android (الـ URI ليس مساراً دائماً). |
| `features/auth/SovereignAuthScreens.kt` | شاشات تسجيل/دخول | واجهة Compose فقط بلا أي اتصال بالشبكة أو الخادم؛ أزرار لا تفعل شيئاً سوى استدعاء callback محلي. |
| `features/calls/CallOrchestrator.kt` + `CallViewModel.kt` | اختيار WebRTC أو GSM | كلاهما يستخدم `RedVoipMaster` غير الموجود ⇒ خطأ ترجمة. |
| `features/chat/ChatViewModel.kt` | إرسال رسالة | يستدعي `deliveryEngine.dispatchMessage` غير الموجودة في `MasterDeliveryEngine` ⇒ خطأ ترجمة. |
| `features/chat/GroupIDManager.kt` | دعوة أعضاء بمعرّف سيادي | يستخدم `IdentityManager` و`RedWebSocketClient` غير الموجودين؛ `identityManager.getUserHandle()` غير موجودة (الموجودة `getRedId()`) ⇒ خطأ ترجمة. |
| `features/chat/ChatDetailScreen.kt` | شاشة محادثة | واجهة ببيانات **ثابتة مكتوبة يدوياً** ("Hello Team!", "System B is now live.") — لا اتصال بقاعدة بيانات ولا WebSocket. |
| `features/chat/RedChatDetail.kt` | شاشة محادثة (نسخة ثانية!) | **يعرّف دالة `ChatDetailScreen` بنفس الاسم** في نفس الحزمة (تحميل زائد متعارض) ويستدعي `viewModel.messages` (غير موجودة), `RedChatBubble` (غير موجودة), `ChatInputBar(onSend=...)` (لا تقبل وسائط) ⇒ أخطاء ترجمة متعددة. |
| `features/chat/RedChatScreen.kt` | شاشة ثالثة | يستخدم `RedChatTopBar`, `RedMessageInput` غير الموجودين ⇒ خطأ ترجمة. |
| `features/pstn/DialPadScreen.kt` | لوحة اتصال GSM | تستدعي `YemeniOperatorDetector.getOperatorInfo()` — **الفئة غير موجودة** ⇒ خطأ ترجمة. والدالة تقبل وسيطاً (`onNavigateToCall`) بينما تُستدعى بلا وسائط في `RedMainHost` ⇒ خطأ ترجمة. |
| `network/NotificationBridge.kt` | جسر إشعارات | يستدعي `deliveryEngine.processIncomingRED()` — **غير موجودة** ⇒ خطأ ترجمة. |
| `network/RedPushService.kt` | خدمة دفع دائمة | تستخدم `R.drawable.ic_launcher_red` — **الموارد غير موجودة** ⇒ خطأ ترجمة؛ ولا تُسجَّل في الـ Manifest إطلاقاً؛ ولا تنشئ قناة إشعارات (Notification Channel) لنظام Android 8+. |
| `ui/RedMainHost.kt` | المضيف الرئيسي للواجهة | يستورد 8 فئات **غير موجودة** (`RedDashboard`, `ChatListScreen`, `CallLogScreen`, `StoryListSection`, `SettingsScreen`, `StatusListView`, `NavController` بلا import) ⇒ ~8 أخطاء ترجمة في ملف واحد. |
| `core/MasterFeatureSet.kt` | "ضمان عدم حذف الميزات" | فئة تركيب فقط، يستدعي `verifyIntegrity()` تطبع نصاً ولا تتحقق من شيء. |

**الخلاصة:** من 26 ملفاً مخصصاً في تطبيق أندرويد، **أغلبها لا يُترجم** بسبب مراجع مفقودة، ولا يوجد اختبار واحد لها، ولا اتصال شبكي فعلي بأي خادم RED.

#### 5.1.3 طبقة "developed" (في org/thoughtcrime/securesms)

| الملف | الواقع |
|---|---|
| `developed/DevelopedChatCore.kt` | `REDCore.initializeEverything()` يستدعي `checkApprovalStatus()` التي **تُرجع `true` دائماً** — "فرض موافقة المدير" وهمي. |
| `developed/DevelopedChatInitialization.java` | `REDInitialization.initialize()` يضبط خاصية نظام `signal.service.url` — **لا تُستخدم في أي مكان** (سيجتال يقرأ العناوين من `BuildConfig`)، والدالة **لا تُستدعى أبداً**. |
| `developed/MasterIntegration.kt` | يستورد `com.red.core.delivery.DeliveryEngine` و`com.red.features.pstn.PstnEngine` — **غير موجودين** ⇒ خطأ ترجمة. و`checkAdminApproval()` تُرجع `false` دائماً. |
| `developed/delivery/GuaranteedDelivery.kt` | `generateMsgId()` يركّب سلسلة `${millis}-${UUID}` — **ليست UUID v7**؛ و`start()` يطبع فقط. |
| `developed/voip/UltraHDCall.kt` + `DevelopedVoipController.java` | يطبعان فقط؛ لا يوجد أي كود WebRTC/mediasoup. |
| `developed/voip/QualityController.kt` | يبني `mutableMapOf` من معاملات ثم **يلقيها** — لا يمررها لأي واجهة؛ والتعليق يدّعي 4K بينما `video.maxBitrate = 5000000` (5Mbps) وهو رقم لا يكفي حتى 1080p جيداً. |
| `developed/pstn/DuminManager.kt` | `connect()` يطبع فقط. |
| `dependencies/DevelopedServerConfig.java` | عناوين محلية (`192.168.1.50`) لكنها **لا تُستخدم في أي كود** — لا أحد يقرأ هذه الثوابت. |

#### 5.1.4 ملف بناء التطبيق (app/build.gradle.kts — 1302 بايت فقط)

هذا الملف استُبدل بملف Signal الأصلي (47841 بايت) بملف مختزل جداً يعاني من مشاكل قاتلة:

1. **plugins غير متوفرة في الجذر**: `id("com.google.dagger.hilt.android")` و`id("org.jetbrains.kotlin.plugin.serialization")` غير معلنين في `build.gradle.kts` الجذر (الموجود: android, kotlin, compose, ktlint, benchmark, dependency-verification) ⇒ فشل تحليل plugin.
2. **لا compose compiler plugin** ⇒ كود Compose (وكل شاشات RED) يُترجم كدوال عادية بلا تحويل وتنهار وقت التشغيل (إن تُرجِم أصلاً).
3. **لا `buildConfigField`**: `REDServiceNetworkAccess` يستخدم `BuildConfig.SIGNAL_URL`, `SIGNAL_SERVICE_IPS`, `STORAGE_URL`... وكلها **غير معرّفة** ⇒ خطأ ترجمة.
4. **لا `manifestPlaceholders`**: الـ Manifest يستخدم `${mapsKey}` بلا تعريف ⇒ خطأ AGP "placeholder substitution required".
5. **ناقص الاعتماديات**: Room, Compose Material3/Foundation/Icons, accompanist-permissions (مستخدم في `PermissionRequestScreen`) — كلها غير مضمنة.
6. **`composeOptions.kotlinCompilerExtensionVersion = "1.5.15"`** مع Kotlin 2.2.20 — متهالك (deprecated) ومتضارب مع أسلوب Compose compiler الجديد.
7. `compileSdk 35` مقابل `37` في الكتالوج و`minSdk 26` مقابل `23` — تناقضات إصدارات.
8. `app/dependencies.gradle.kts` (ملف إضافي كامل للاعتماديات) **لا يُستدعى من أي مكان** — ملف ميت.

#### 5.1.5 ملفات البناء الجذرية (حتمية الفشل)

- `settings.gradle.kts`: يضم `:features:chat`, `:features:calls`, `:features:pstn`, `:features:stories`, `:features:auth`, `:features:profile` — **لا توجد أي من هذه المجلدات** (الموجود: camera, media-send, registration) ⇒ فشل إعداد المشروع.
- `build.gradle.kts` (الجذر): داخل `gradle.projectsEvaluated`:
  - `tasks.findByPath(":RED-Android:testPlayProdDebugUnitTest")!!` — **لا يوجد مشروع باسم `:RED-Android`** (الاسم `:app`) ⇒ **NullPointerException حتمية عند الإعداد** ⇒ أي أمر Gradle يفشل فوراً.
  - `id("dependency-verification")` — plugin غير معرّف في أي مكان في المشروع ⇒ فشل تحليل plugin.
- `gradle.properties` يستخدم إعدادات Signal الأصلية (`-Xmx12g`, R8 flags) — مقبولة لكنها تخص آلة بناء ضخمة.

### 5.2 الوحدات المساندة (core / lib / feature)

- `core/`, `lib/` هي وحدات Signal الأصلية بعد إعادة التسمية المتسقة (تحققنا من تعريف كل صفوف RED* المذكورة في الاستيرادات). وهي الجزء السليم تقنياً.
- `feature/` يحتوي فقط على `camera`, `media-send`, `registration` — **لا توجد features: chat/calls/pstn/stories/auth/profile** رغم تضمينها في `settings.gradle.kts`.
- `core/network/src/main/java/org/signal/network/config/SignalServiceConfiguration.kt` يعلن `data class REDServiceConfiguration` — متسق مع الاستخدام.
- `wire-handler/`: آلية استبدال `countNonNull` ← `countNonDefa` في الكود المولد من Wire (نفس حيلة Signal الرسمية). الجرة موجودة ومبنية. ملاحظة تقنية صغيرة: الاستبدال الثنائي يطابق أي ظهور للمتتالية حتى داخل كلمات أطول أو تعليقات — خطر نظري منخفض.
- `reproducible-builds/`: بنية جاهزة لإعادة بناء Signal — جيدة.
- `fast-lint/`, `lintchecks/`, `build-logic/`: هياكل صحيحة شكلياً.

### 5.3 الخادم الخلفي (backend-server) — Spring Boot 3.4

#### 5.3.1 أخطاء ترجمة حتمية

| الملف | الخطأ |
|---|---|
| `api/AdminMasterController.kt`, `controllers/AdminController.kt`, `api/admin/RedMasterController.kt` | يستوردون `RedApprovalService` — **غير موجودة في المشروع** (كانت موجودة في نسخة `server/` القديمة ثم حُذفت!) |
| `api/admin/RedMasterController.kt` | يستورد `RedSecurityService` — **غير موجودة** |
| `websocket/RedMasterHandler.kt`, `websocket/ChatWebSocketHandler.kt`, `websocket/RedWebSocketHandler.kt` | يستوردون `com.red.server.messaging.MessageService` — **غير موجودة** (الموجود `AdvancedMessageService`, `DeleteService`) |
| `messaging/AdvancedMessageService.kt`, `messaging/DeleteService.kt`, `websocket/ChatWebSocketHandler.kt`, `websocket/RedWebSocketHandler.kt` | يستوردون `com.red.sovereign.proto.ChatProtos` — **لا يُولَّد هذا الصنف أصلاً**: `messages.proto` يولّد `com.red.proto.ChatProtos`، و`red_protocol.proto` يولّد `com.red.sovereign.proto.RedProtos` فقط |
| `services/CoreService.kt` | يستورد `com.red.sovereign.features.chat.GroupEntity` و`com.red.sovereign.features.stories.StoryEntity` — **كلاهما غير موجود** (GroupEntity موجود في `core/database` بمسار مختلف) |
| `pstn/PstnManager.kt` | يستخدم `@Value("${ASTERISK_AMI_USER}")` **بدون استيراد** `Value` ⇒ خطأ ترجمة |
| `core/MasterLogicIntegrator.kt` | فئة عادية غير مسجلة كـ Bean وتستقبل `RedMasterController` في المنشئ — لن تُدار أبداً (ولن تُستخدم أصلاً) |

#### 5.3.2 مشاكل البناء والنشر

- **لا يوجد `settings.gradle.kts` ولا `gradlew` في مجلد `backend-server`** ⇒ أمر `./gradlew build` في الـ Dockerfile **يفشل حتماً**.
- **لا يوجد إعداد لتوليد البروتوكولات**: `shared-proto` بلا ملف بناء وبلا ربط بأي وحدة؛ ولا يوجد تطبيق لـ protobuf/wire plugin في `backend-server/build.gradle.kts` ⇒ استيرادات `ChatProtos`/`RedProtos` غير محلولة أصلاً.
- **لا يوجد `application.properties` أو `application.yml`** إطلاقاً ⇒ Spring Boot يبدأ بإعدادات افتراضية: قاعدة بيانات `localhost` غير موجودة، لا عنوان Mongo/Redis صحيح، و`spring-boot-starter-security` بلا أي إعداد يولّد مستخدماً عشوائياً بكلمة مرور مطبوعة على الشاشة ⇒ **كل الـ endpoints مغلقة بكلمة مرور مجهولة** (فشل وظيفي) أو مفتوحة إذا عُطّلت.
- لا توجد `@Configuration` WebSocket: رغم `@EnableWebSocket` على التطبيق، **لا يوجد WebSocketConfigurer أو registerWebSocketHandlers** ⇒ **لا يوجد أي مسار WebSocket مسجَّل فعلياً** رغم وجود الـ handlers كـ Components.
- الـ healthcheck في docker-compose يستدعي `/actuator/health` بينما **actuator غير مضاف للاعتماديات** ⇒ healthcheck يفشل دائماً.

#### 5.3.3 ثغرات ومنطق وهمي

1. **لا مصادقة ولا تفويض على أي endpoint**: `/api/admin/users/approve`, `/api/master/v1/security/wipe`, `/api/admin/security/kill-switch`, `/api/admin/dinstar/reboot`... كلها بلا حماية — أي شخص يستطيع الموافقة على مستخدمين أو "مسح" أجهزة.
2. **`AuthController`**: قاعدة مستخدمين في الذاكرة (`ConcurrentHashMap`) تُمحى عند إعادة التشغيل؛ لا تخزين كلمات مرور (لا hash أصلاً)؛ رمز الدخول `"red-jwt-${UUID}"` **غير موقّع وغير قابل للتحقق**؛ لا يقرأ ولا يكتب في PostgreSQL رغم وجود سكربتات Flyway.
3. **`AdminController.activateKillSwitch`**: يطبع رسالة فقط — لا إشارة WebSocket ولا مسح فعلي؛ الرد `"WIPE_SIGNAL_SENT"` كاذب.
4. **بيانات DINSTAR كلها مزيفة**: `DinstarMasterClient.getPortsRealtimeStatus()` يعيد `(70..95).random()`؛ `DinstarHardwareService.getHardwareStatus()` يعيد `"READY"` و`signal=85` ثابتين؛ الطلبات الحقيقية للجهاز **معلَّقة كتعليقات**؛ حتى `IMEI` مركّب `"8642210455${i}123"`. لوحة التحكم تعرض بيانات وهمية.
5. **`AdminMasterController.executeDinstarAction`**: يعيد `"EXECUTED"` دون تنفيذ أي شيء.
6. **الانتحال عبر WebSocket**: معرّف المستخدم يُقرأ من `session.attributes["userId"]` الذي **لا يضبطه أحد** (لا HandshakeInterceptor)؛ الرسالة تحمل `senderId`/`receiverId` من العميل نفسه ⇒ أي عميل يستطيع إرسال رسائل باسم أي مستخدم.
7. **التوصيل المضمون غير موجود**: الرسالة تُمرَّر للمستقبِل إن كان متصلاً فقط؛ **لا تخزين للرسائل الموجهة لغير المتصلين** ولا إعادة تسليم لاحقة؛ `handleSync` في `RedMasterHandler` **فارغ** (تعليق فقط).
8. **`DeleteRED` غير معالج**: البروتوكول يعرّف حزمة حذف لكن لا يوجد `when` لها في أي Handler؛ ادعاء "الحذف للجميع" من `DeleteService` يكتفي بحذف MongoDB ولا يبث شيئاً.
9. **تناقض ترقيم المنافذ**: `DinstarMasterClient` يستخدم (0..7) بينما `DinstarMasterService` يستخدم (1..8) و`DinstarLoadBalancer` يستخدم (1..8) — ولوحة `DinstarTab` تتوقع `slot.index` ⇒ بيانات متعارضة.
10. **`redis.keys()`** في `IronSyncService`, `MasterStatsService`, `MasterOrchestrationService` — أوامر حظر O(N) محظورة في الإنتاج.
11. **نظامان متوازيان للتسلسل**: `RedisSequenceGenerator` (`seq:`) و`RedisManager` (`red:seq:`) — لا يتصلان ببعض.
12. **`SearchService`**: بحث نصي (`TextCriteria`) على رسائل مخزّنة **كـ `ByteArray` مشفّر** — تناقض تصميمي: الخادم لا يستطيع البحث في محتوى مشفر دون كسر التشفير من الطرف إلى الطرف (وهذا سيجعل التطبيق غير آمن أصلاً لو طُبّق).
13. **`PstnManager`** يتصل بأستريكس عند بدء التشغيل ويسجّل دخوله — إذا فشل، يطبع تحذيراً ويستمر (لا استرجاع).
14. **`LiveStreamService`** عدادات في الذاكرة لا علاقة لها بـ mediasoup.
15. **`StorageMonitorService`** يفحص مسارات `/app/minio-data` و`/var/lib/postgresql/data` — موجودة داخل حاوية backend فقط جزئياً، فالمسارات مختلفة عن docker-compose الفعلي (volumes باسم `minio-data`).
16. **كلمات مرور افتراضية مكشوفة** في docker-compose: `DB_PASSWORD=password`, `AMI_PASSWORD=red_secret_123`, `TURN_SECRET=redturnsecret`, `MINIO_PASSWORD=redsecret123`، وفي `pstn-asterisk/manager.conf`: `secret = red_secret_123`، وفي `pjsip.conf`: `password=red_secure_pass`.

### 5.4 لوحة التحكم (admin_dashboard)

المشاكل قاتلة ومتعددة:

1. **لا توجد نقطة دخول إطلاقاً**: المجلد `src/` يحتوي فقط `components/` و`pages/` — **لا `index.js`, لا `App.jsx`, لا Router, لا `main`**؛ و`public/index.html` يعرّف `<div id="root">` دون أن يحمّل أي سكربت.
2. **`package.json` ناقص**: لا `scripts` (لا build/start/dev)، الاعتماديات هي `react, antd, echarts` فقط بينما الكود يستورد **`@ant-design/icons` و`echarts-for-react` و`react-dom`** (غير مضمنة) ⇒ `npm install` ثم أي بناء يفشل.
3. **لا Dockerfile** رغم أن docker-compose يبني `./admin_dashboard` ⇒ فشل docker build.
4. **استيرادات ملفات غير موجودة**: `MasterLayout.tsx` يستورد `./tabs/OverviewTab`, `AuthorityTab`, `MessagingTab`, `SecurityTab` — لا وجود لها (الموجود `DinstarTab`, `LogStreamerTab` فقط).
5. **أيقونة غير موجودة**: `import { signalFilled } from '@ant-design/icons'` (الصحيح `SignalFilled`).
6. **endpoints لا تطابق الخادم**:
   - `LiveMonitor.js` و`Dashboard.tsx` يستدعيان `/api/admin/monitor/stats` — **غير موجود في الخادم**.
   - `Approvals.js` يستدعي `/api/admin/pending-users` و`POST /api/admin/approve/:id` — **غير موجودين** (الموجود `/api/admin/users/pending`, `/api/admin/users/update-status`).
   - `DuminAdvanced.tsx` يستدعي `/api/admin/dumin/telemetry` — **غير موجود**.
   - `LogStreamerTab.tsx` يفتح `ws://host:8080/ws/admin/logs` — **لا endpoint WebSocket مسجّلاً أصلاً** (راجع 5.3).
   - `MasterOverview.tsx` يتوقع مفاتيح `ws_active/pending_auth/gsm_signal/db_storage` بينما الخادم يعيد `active_users/messages_24h/system_load/db_health/pending_approvals` ⇒ كل البطاقات تظهر "undefined".
7. **بيانات ثابتة/مزيفة**: `DuminMonitor.js` يعرض حالة وهمية ثابتة (`simStatus: 'Active'`, `balance: '120.50'`)؛ `MasterControl.tsx` يثبّت `setStats({ msgs: 8540, calls: 142, load: 22 })` يدوياً؛ `DinstarMonitor.js` لا يجلب أي شيء.
8. `Diagnostics.js` يستورد `Tag` من antd دون استخدامه (خطأ lint) ويعرض "OPERATIONAL" دائماً.
9. في `DinstarControl.tsx`, مفتاح `key={port.index}` بينما الخادم `DinstarHardwareService` يعيد `index`؛ أما `DinstarMasterClient` فيعرض `index` — لكن `DinstarMasterService` يعيد `slot` — تباين.

### 5.5 وسيط الوسائط (media-sfu)

- ملفان فقط: `package.json` + `server.js` (~60 سطراً).
- `join` و`signal` **فارغان** — لا إنشاء `WebRtcTransport`, لا `produce`/`consume`, لا تمرير وسائط ⇒ "SFU" لا يمرر أي شيء.
- `createRoom` ينشئ Router لكن لا يخزّن transports ولا يربط المستخدمين.
- **لا Dockerfile** رغم `build: ./media-sfu` في docker-compose ⇒ فشل البناء.
- لا معالجة أخطاء: `JSON.parse(message)` قد يرمي استثناء يكسر الخادم (لا try/catch).
- فتح المنافذ 40000–40100 كـ UDP في docker-compose صحيح لفكرة mediasoup، لكن بلا تنفيذ.

### 5.6 بوابة PSTN (pstn-asterisk)

- إعدادات أستريكس (pjsip/manager/extensions) **سليمة شكلياً** ومنطق الاتصال `Dial(PJSIP/${EXTEN}@dumin-trunk)` مقبول كخطوة أولى.
- لكن: لا يوجد سجل `[dumin-trunk]` endpoint في `pjsip.conf` — المسار يُحال إلى `dumin-trunk` بينما المعرّف هو `dinstar-gateway` ⇒ **اتصال وهمي لن يعمل** (`Dial(PJSIP/${EXTEN}@dumin-trunk)` ↔ المعرّف الفعلي `dinstar-gateway`).
- `extensions.conf` يوجّه المكالمات الواردة إلى `PJSIP/webrtc-client` دون وسيط WebSocket/SIP للمستخدمين الفعليين.
- أسرار ثابتة (`red_secret_123`, `red_secure_pass`).
- يوجد Dockerfile هنا (ناقص `COPY pjsip_dinstar.conf` غير مستخدم — لا بأس).

### 5.7 البروتوكولات (shared-proto)

- ملفان فقط: `messages.proto` (حزمة `com.red.proto` / صنف `ChatProtos`) و`red_protocol.proto` (حزمة `com.red.sovereign.proto` / صنف `RedProtos`).
- **لا build.gradle ولا ربط بأي وحدة** — لا شيء يولّد هذه الفئات في أي مشروع.
- **تناقض الحزم**: الكود الخلفي يستورد `com.red.sovereign.proto.ChatProtos` بينما `messages.proto` يولّد `com.red.proto.ChatProtos` ⇒ استيرادات مكسورة.
- `messages.proto` يحتوي `MessageAck` بحقول مختلفة عن `red_protocol.proto` — نموذجان متناقضان لـ ACK.

### 5.8 البنية التحتية (docker-compose / nginx / scripts)

- `docker-compose.yml`: 9 خدمات (backend, media-sfu, coturn, pstn-gateway, db-postgres, db-mongo, cache-redis, minio, nginx + admin-panel). الفكرة جيدة.
- **فشل البناء**: `admin-panel` و`media-sfu` بلا Dockerfile؛ `backend-server/Dockerfile` يستدعي `./gradlew` (غير موجود) و`chmod /app/infrastructure/setup-env.sh` (خارج سياق النسخ) و`CMD java -jar build/libs/backend-1.0.0.jar` (اسم الجرة غير مضبوط في build.gradle).
- `backend` healthcheck يستدعي actuator غير الموجود.
- `pstn-gateway` يستخدم صورة `andrius/asterisk` مع volume يغطي `/etc/asterisk` — سيكتب إعداداته فوقها (السلوك قد يختلف حسب الصورة).
- **nginx بلا TLS**: كل شيء على HTTP عبر المنفذ 80؛ المسار `/` يوجّه إلى admin-panel:3000 لكن admin-panel لا يعمل أصلاً.
- `build-and-run.sh`: يعتمد `docker-compose` (قديم) بدل `docker compose`؛ يقول "All 9 Systems are ONLINE" دون أي تحقق.
- `infrastructure/setup-env.sh`: يستخدم `mc` و`psql` بلا تثبيت ولا فحص وجود؛ ينشئ bucket عاماً (`mc policy set public`) لبيانات حساسة؛ كلمة مرور MinIO ثابتة "password".

### 5.9 النسخ القديمة داخل الملف الأول (للتذكير)

- `android/` و`app-android/`: محاولات RED سابقة (حزمة `com.developedchat`/`com.red.app`) — **مصدر الفئات المفقودة في النهائي** مثل `RedDashboard` الذي يستورده `RedMainHost` لكنه حُذف من النسخة النهائية!
- `server/`: خادم سابق يحتوي `RedApprovalService.kt` و`SecurityController.kt` و`application.properties` — **حُذف في النهائي بينما بقيت مراجعه في backend-server** ⇒ هذا يفسّر أخطاء الترجمة: رجوع (regression) من النسخة القديمة إلى الجديدة.
- `demo/`, `benchmark/`, `microbenchmark/`, `baseline-profile/`: وحدات Signal الأصلية.

---

## 6) جدول الأخطاء والعيوب (مصنفة حسب الخطورة)

### 🔴 حرجة (تمنع البناء/التشغيل قطعياً)

| # | الوصف | الموقع |
|---|---|---|
| C1 | NPE عند إعداد Gradle: مهمة `:RED-Android:...` غير موجودة مع `!!` | `build.gradle.kts` (الجذر) |
| C2 | وحدات `:features:*` مضمّنة غير موجودة | `settings.gradle.kts` |
| C3 | Plugin `dependency-verification` غير معرّف | `build.gradle.kts` (الجذر) |
| C4 | فئة مكررة `com.red.sovereign.MainActivity` | `com/red/sovereign/MainActivity.kt` + `org/thoughtcrime/securesms/MainActivity.kt` |
| C5 | 11+ اسم فئة مكرر في الحزمة نفسها (banner, bootreceiver, editprofilerepository, editprofileviewmodel, mystoriesitem, recipientviewholder, restorelocalbackup*, restorestate, utils...) | ملفات `org/thoughtcrime/securesms/...` المعلنة حزمة `com.red.sovereign` |
| C6 | فئات RED مفقودة مرجعية: `RedVoipMaster`, `IdentityManager`, `RedSplashScreen`, `REDTheme`, `PstnViewModel`, `StoryViewModel`, `MasterDao`, `RedWebSocketClient`, `MinioUploader`, `YemeniOperatorDetector`, `RedDashboard`, `ChatListScreen`, `CallLogScreen`, `StoryListSection`, `SettingsScreen`, `StatusListView`, `RedChatBubble`, `RedChatTopBar`, `RedMessageInput` | ملفات `com/red/sovereign/**` |
| C7 | Plugin Hilt وSerialization غير متوفرين في classpath الجذر؛ لا compose compiler | `app/build.gradle.kts` |
| C8 | `BuildConfig.SIGNAL_URL/IPS...` غير معرّفة (لا buildConfigField) | `app/build.gradle.kts` + `push/REDServiceNetworkAccess.kt` |
| C9 | placeholder `${mapsKey}` بلا تعريف | `app/build.gradle.kts` + `AndroidManifest.xml` |
| C10 | ناقص Room/Compose M3/Accompanist من الاعتماديات | `app/build.gradle.kts` |
| C11 | `addMigrations()` بلا وسائط | `core/di/RedMasterModule.kt` |
| C12 | استدعاءات دوال غير موجودة (dispatchMessage, processIncomingRED, getUserHandle, ChatInputBar(onSend), viewModel.messages, DialPadScreen بلا وسيط, R.drawable.ic_launcher_red) | ملفات `com/red/sovereign/**` |
| C13 | الخادم: `RedApprovalService`, `RedSecurityService`, `MessageService`, `ChatProtos(com.red.sovereign.proto)`, `GroupEntity/StoryEntity` غير موجودة | `backend-server/**` |
| C14 | `PstnManager` بلا import لـ `@Value` | `backend-server/pstn/PstnManager.kt` |
| C15 | لا إعداد لتوليد protos؛ لا وحدة لـ `shared-proto` | الجذر + `backend-server` |
| C16 | backend-server بلا `gradlew`/`settings.gradle.kts` ⇒ Dockerfile يفشل | `backend-server/Dockerfile` |
| C17 | admin_dashboard وmedia-sfu بلا Dockerfile ⇒ docker-compose يفشل | الجذر |
| C18 | لوحة التحكم بلا نقطة دخول ولا scripts ولا dependencies كاملة | `admin_dashboard/package.json` |
| C19 | استيرادات تابات/أيقونات غير موجودة في اللوحة | `admin_dashboard/src/pages/MasterLayout.tsx`, `DuminAdvanced.tsx` |
| C20 | اسم الجرة في CMD لا يطابق البناء؛ actuator مفقود للـ healthcheck | `backend-server/Dockerfile` + `docker-compose.yml` |

### 🟠 عالية (كسر وظيفي/أمني عند التشغيل)

| # | الوصف | الموقع |
|---|---|---|
| H1 | لا مصادقة/تفويض على كل endpoints الإدارية (approve, wipe, kill-switch, reboot...) | `backend-server/**Controllers` |
| H2 | Token وهمي غير موقّع؛ كلمات مرور بلا hash؛ DB في الذاكرة | `auth/AuthController.kt` |
| H3 | WebSocket بلا مصادقة ومعرّف مستخدم من خصائص الجلسة غير المضبوطة ⇒ انتحال كامل | `websocket/*Handler.kt` |
| H4 | لا WebSocketConfigurer ⇒ لا endpoints WebSocket مسجلة | `RedSovereignApplication.kt` |
| H5 | "Kill switch" وهمي (println فقط) | `controllers/AdminController.kt` |
| H6 | بيانات DINSTAR مزيفة بالكامل (random/ثوابت) | `infrastructure/dinstar/DinstarMasterClient.kt`, `services/Dinstar*Service.kt` |
| H7 | لا تخزين رسائل للغير متصلين؛ handleSync فارغ؛ DeleteRED غير معالج | `websocket/RedMasterHandler.kt` وغيرها |
| H8 | أسرار افتراضية مكشوفة في كل الطبقات | `docker-compose.yml`, `pstn-asterisk/*`, `infrastructure/setup-env.sh` |
| H9 | `android:allowBackup="true"` لتطبيق مراسلة مشفّر + SharedPreferences مكشوفة للـ token | `AndroidManifest.xml`, `RedIdentityManager.kt` |
| H10 | Redis `keys()` المحظورة O(N) | `services/IronSyncService.kt`, `MasterStatsService.kt`, `MasterOrchestrationService.kt` |
| H11 | مسار SIP `dumin-trunk` غير معرّف في pjsip.conf | `pstn-asterisk/extensions.conf` |
| H12 | SearchService يبحث في رسائل مشفّرة (تناقض مع E2EE) | `services/SearchService.kt` |
| H13 | نزاع ترقيم منافذ Dinstar (0..7 مقابل 1..8) | `DinstarMasterClient` vs `DinstarMasterService` |
| H14 | nginx بلا TLS؛ كل حركة المرور نصية | `nginx.conf` |

### 🟡 متوسطة

| # | الوصف |
|---|---|
| M1 | `RedSovereignApp` لا يهيّئ `ApplicationContext` (نواة Signal معطلة) |
| M2 | "السيادة" غير حقيقية: `static-ips.properties` يحوي IPs خوادم Signal الرسمية و`reflector-...run.app` مكتوبة في الكود؛ `DevelopedServerConfig` غير مستخدم |
| M3 | ادعاءات متناقضة: 4K في checklist مقابل 1080p في التعليقات؛ 5Mbps لا تكفي 4K |
| M4 | `checkApprovalStatus()` تُرجع true و`checkAdminApproval()` تُرجع false — موافقة المدير وهمية |
| M5 | `QuantumGuard` يعيد النص كما هو — "التشفير الكمومي" غير موجود |
| M6 | `GuaranteedDelivery.generateMsgId` ليست UUID v7 حقيقية (لا version/variant) |
| M7 | `IronSyncService`/`RedisSequenceGenerator` نظاما تسلسل متوازيان |
| M8 | `LiveStreamService`/`AdminStoryService` إحصاءات تخمينية (2MB لكل قصة) |
| M9 | `LogStreamerTab`/`Dashboard`/`Approvals`/`DuminAdvanced` endpoints غير موجودة ⇒ لوحة مكسورة وظيفياً |
| M10 | سباق في `DinstarLoadBalancer.getOptimalSlot` |
| M11 | `RedisManager.setPresence` (5 دقائق) مقابل `RedisSequenceGenerator.setUserOnline` (60 ثانية) — تضارب |
| M12 | `CallWebSocketHandler` غير مسجل وليس Bean |
| M13 | `MasterLogicIntegrator` ليس Bean وغير مستخدم |
| M14 | ملفات قديمة متبقية: `app/dependencies.gradle.kts` ميت؛ نسخ ZIP القديمة داخل المستودع |
| M15 | `AdminLogHandler.sendMessage` في `afterConnectionEstablished` بلا try/catch |

### 🟢 منخفضة / ملاحظات

| # | الوصف |
|---|---|
| L1 | `MasterOverview.tsx` مفاتيح إحصاءات لا تطابق استجابة الخادم |
| L2 | `Diagnostics.js` يستورد `Tag` بلا استخدام؛ `DuminMonitor` بيانات ثابتة |
| L3 | `MediaTransporter` يستخدم `File(uri.path!!)` — غير صالح لأنواع URI عديدة |
| L4 | `RedPushService` بلا قناة إشعارات وبدون تسجيل في Manifest |
| L5 | `wire-handler` استبدال ثنائي قد يطابق تواجدات غير مقصودة |
| L6 | `include(":core")` بلا ملف بناء — مشروع فارغ |
| L7 | ملفات `lib/contacts/build.gradle` و`lib/image-editor/build.gradle` بـ Groovy بينما البقية KTS |
| L8 | README يحتفظ بروابط Signal الرسمية (Play Store/iOS/Desktop/Forum) — إعادة تسمية سطحية غير مكتملة |
| L9 | `NOTICE`/`LICENSE`: "Copyright 2013 RED Messenger, LLC" بينما الكود كله من Signal — مسألة إسناد/ترخيص يجب مراجعتها قانونياً |

---

## 7) الثغرات الأمنية (قائمة مركزة)

1. **انعدام تام للتحكم بالوصول** على خادم الإدارة (بدون JWT/Spring Security Config/CSRF/rate-limit).
2. **انتحال الرسائل** عبر WebSocket (senderId/receiverId من العميل).
3. **كلمات مرور وأسرار افتراضية ثابتة** في compose/Asterisk/nginx/MinIO.
4. **"مسح عن بُعد" وهمي** — لا يوجد تنفيذ، والواجهة تعرض نجاحاً كاذباً.
5. **بحث الخادم في الرسائل المشفّرة** — سيتطلب كسر E2EE لو طُبّق.
6. **allowBackup مفتوح** + تخزين `AUTH_TOKEN` في SharedPreferences غير مشفّرة.
7. **لا TLS إطلاقاً** (nginx HTTP، Dinstar HTTP، AMI بلا تشفير).
8. **`bucket` عام** في `setup-env.sh` لبيانات الوسائط.
9. **`redis.keys()`** تكشف بنية المفاتيح وتحجب الخادم.
10. **لا تحقق من هوية الأجهزة** عند المصادقة — أي شخص يملك "رمزاً" من أي نوع يعمل.

---

## 8) المشاكل المعمارية والمنطقية الكبرى

1. **نسخ بديلة متعددة داخل مستودع واحد** (3 ZIP + مجلدات قديمة) ⇒ خطر تشغيل نسخة خاطئة وضياع التحديثات.
2. **تطبيق "نظامين" منفصلين**: واجهة Compose RED (شبه ميتة) مقابل واجهة Signal الكاملة (غير مرتبطة) — تعارض ملكية الـ MainActivity وبدء التشغيل.
3. **طبقة RED لا تتصل بطبقة Signal**: لا `ApplicationContext`، لا `AppDependencies`، لا WebSocket حقيقي — نظامان متوازيان لا يلتقيان.
4. **الخادم لا يطابق التطبيق**: التطبيق (لو عمل) يتحدث بروتوكول Signal الأصلي عبر WebSocket الخاص بـ libsignal-service، بينما الخادم يتوقع `ChatProtos` الخاصة بـ RED — **بروتوكولان مختلفان تماماً بلا جسر**.
5. **"التوصيل المضمون" ادعاء غير مدعوم**: لا تخزين للغير متصلين، لا إعادة إرسال، لا فحص ازدواج فعلي على الخادم.
6. **منطق Dinstar كله محاكاة** حتى مستوى "التحكم بالهاردوير" الذي يطبعه رسائل.
7. **لا CORS، لا TLS، لا معالجة أخطاء** في أي طبقة خادم.
8. **لا اختبارات** لأي من كود RED الجديد (كل الاختبارات الموجودة 391 ملفاً هي اختبارات Signal الأصلية).

---

## 9) "الميزات الوهمية" (Stubs/Simulation) — قائمة تحقق

| الادعاء | الواقع |
|---|---|
| نظام A: مكالمات 4K/AV1 | لا كود WebRTC/mediasoup في التطبيق؛ SFU فارغ؛ 5Mbps ≠ 4K |
| نظام B: بوابة DINSTAR الخلوية | قيم عشوائية + println؛ مسار SIP غير معرّف |
| نظام C: توصيل مضمون UUID v7 | UUID v7 سليم في محرك واحد، لكن لا إرسال فعلي ولا إعادة تسليم؛ محرك آخر يولّد UUID عشوائياً |
| تشفير مقاوم للكم | `wrapWithQuantum` يعيد البيانات كما هي |
| موافقة إدارية إجبارية | `checkApprovalStatus() = true` ثابتة؛ AuthController في الذاكرة |
| قصص تحذف خلال 24 ساعة | `cleanupStories` يعمل كل دقيقة على Mongo (فكرة سليمة) لكن لا شيء يكتب "القصص" أصلاً من التطبيق |
| Kill Switch / مسح عن بُعد | println فقط |
| قطع الاعتماد على سحابة Signal | خوادم Signal الفعلية في `static-ips.properties` والكود |
| لوحة تحكم "تحكم كامل" | endpoints وهمية/غير مطابقة/غير محمية؛ بيانات ثابتة |
| "NO FEATURES MISSING" (الـ checklist) | ناقص كل ما سبق + الميزات غير المبنية أصلاً |

---

## 10) نقاط القوة (التي يجب البناء عليها)

1. **قاعدة Signal-Android سليمة وكاملة** (تشفير E2EE، Kyber، نسخ احتياطي v2، Compose، وحدة libsignal-service) — أقوى أساس مجاني متاح لهذا الغرض.
2. **إعادة التسمية الشاملة نُفّذت بدقة واتساق** (0 مرجع متبقٍ) — دليل على عملية آلية مدروسة.
3. **بنية multi-module جيدة** (core/lib/feature) — مناسبة للتوسع.
4. **shared-proto فكرة صحيحة** (بروتوكول ثنائي موحد) لكن تحتاج ربطاً حقيقياً.
5. **docker-compose يغطي كل الخدمات اللازمة** بفكرة متكاملة (DB، Redis، MinIO، SFU، TURN، Asterisk).
6. `lint.xml` صارم (StopShip fatal) وقواعد جودة موجودة (fast-lint, build-logic, wire-handler المبني).
7. `reproducible-builds/` جاهزة — التزام جيد بجودة البناء.

---

## 11) خطة الإصلاح المقترحة (Roadmap)

### المرحلة 0 — التوثيق والتنظيف
- حذف النسخ القديمة (`android/`, `app-android/`, `server/`, `admin-dashboard/`, `demo/`, `benchmark/`, `microbenchmark/`) من أي إصدار قادم.
- توحيد النسخة النهائية في مستودع Git واحد نظيف مع `.gitignore` شامل وإزالة ملفات ZIP من المستودع.

### المرحلة 1 — إصلاح البنية (قبل أي تطوير)
1. `settings.gradle.kts`: تضمين الوحدات الموجودة فعلاً فقط (`:app`, `:core:*`, `:lib:*`, `:feature:camera/media-send/registration`) أو إنشاء مجلدات features المفقودة.
2. إزالة مرجع `:RED-Android` من الجذر (استخدام `:app`) وإزالة `!!`.
3. تعريف plugin `dependency-verification` في build-logic أو حذف السطر.
4. إضافة Hilt/Serialization/compose-compiler plugins إلى الجذر، وإصلاح `app/build.gradle.kts`: `buildConfigField` لكل عناوين الخادم، `manifestPlaceholders["mapsKey"]`, اعتماديات Compose BOM + Material3 + Icons + Accompanist + Room، حذف `composeOptions` المتهالك.
5. توحيد نسخة واحدة من `MainActivity` وحل كل الفئات المكررة (قرار: إبقاء واجهة Signal أو واجهة RED — وليس كليهما).

### المرحلة 2 — قرار معماري مصيري
**اختر أحد المسارين ولا تجمعهما:**
- **(أ) "RED فوق Signal":** اجعل `RedSovereignApp` يمدّد `ApplicationContext`، وشغّل واجهة Signal الحقيقية، وأضف ميزات RED كامتدادات (الشاشات المخصصة، بوابة Dinstar كوحدة اتصال).
- **(ب) "RED مستقل":** ابدأ من الصفر بواجهة Compose خفيفة فوق `libsignal-service` فقط (أصغر وأكثر تحكماً) — ولا تستورد آلاف ملفات Signal.

### المرحلة 3 — الخادم الخلفي
- إعادة `RedApprovalService` (من النسخة القديمة `server/`) وربطه بـ PostgreSQL عبر Flyway (موجود) مع bcrypt + JWT موقّع.
- تكوين `application.yml` (datasources, mongo, redis, actuator, CORS).
- `WebSocketConfigurer` + `HandshakeInterceptor` لمصادقة WebSocket وتعيين `userId` من التوكن.
- تخزين الرسائل للغير متصلين + جدولة إعادة التسليم + معالجة `DeleteRED` + ACK من طرف المستقبِل (وليس "SENT" فقط).
- إزالة `redis.keys()` واستبدالها بـ SCAN أو فهارس.
- تطبيق أمني: CSRF (للـ cookies) أو توكن، rate limiting، HTTPS، تدوير الأسرار إلى `.env`.

### المرحلة 4 — البروتوكولات
- تحويل `shared-proto` إلى وحدة Gradle مولّدة (protobuf-gradle أو Wire) مشتركة بين `app` و`backend-server`.
- توحيد الحزم: اختيار `com.red.sovereign.proto` للجميع، وإزالة `ChatProtos` المزدوجة، وإضافة حقول `device_id`, `delivery_status`, `edited_at`, `deleted_for_everyone`.

### المرحلة 5 — لوحة التحكم
- إضافة Vite/React Router مع نقطة دخول (`src/main.tsx`), `react-dom`, `@ant-design/icons`, `echarts-for-react`، و`Dockerfile` (nginx multi-stage).
- مطابقة كل fetch مع endpoints حقيقية، أو إعادة كتابة الخادم لتوفير `GET /api/admin/monitor/stats` بشكل موحد.
- تسجيل الدخول الإداري الحقيقي (JWT + role ADMIN من قاعدة البيانات).

### المرحلة 6 — SFU وAsterisk
- `media-sfu`: Dockerfile + تنفيذ `createWebRtcTransport`/`produce`/`consume` + إدارة rooms/participants + TLS (wss).
- `pstn-asterisk`: تعريف endpoint `dumin-trunk` فعلياً ومطابقة المسارات؛ إزالة الأسرار الثابتة.

### المرحلة 7 — التطبيق
- إكمال الفئات الناقصة أو إزالة كل المراجع العالقة (قرار المرحلة 2).
- ربط `MasterDeliveryEngine` بـ `RedWebSocketClient` حقيقي (OkHttp WebSocket) نحو خادم RED، أو رفضه واستخدام libsignal-service.
- `QuantumGuard`: استخدام libsignal Kyber (المتوفر فعلاً في libsignal) بدل المحاكاة.
- `allowBackup=false` + `EncryptedSharedPreferences` للمفاتيح.
- تسجيل `RedPushService` + قناة الإشعارات.

### المرحلة 8 — الجودة
- إضافة اختبارات وحدة/تكامل لطبقة RED (لا يوجد اختبار واحد حالياً).
- تشغيل `./gradlew qa` على نسخة تبنى فعلاً، وإصلاح كل lint/ktlint.
- مراجعة قانونية للترخيص (AGPL) والإسناد قبل أي نشر عام.

---

## 12) الخلاصة النهائية

**ما هو المشروع فعلاً؟** شيفرة Signal-Android كاملة ومعاد تسميتها بدقة، فوقها "طبقة RED" من ~70 ملفاً مخصصاً معظمها:
- **لا يُترجم** (مراجع مفقودة، فئات مكررة، ملفات بناء مكسورة)؛
- **أو يطبع فقط** (محاكاة لأنظمة A/B/C والتشفير الكمومي والمسح عن بُعد)؛
- **ولا يُختبَر** (صفر اختبارات للكود الجديد).

**ما الذي ينقصه ليكون مشروعاً حقيقياً؟**
1. ملفات بناء سليمة تنتج APK وخادماً يعملان فعلاً.
2. قرار معماري واضح (RED فوق Signal أم RED مستقل).
3. تنفيذ حقيقي — ولو مبسّطاً — لأحد الأنظمة المعلنة قبل الادعاء بالباقي (الرسائل أولاً، ثم VoIP، ثم PSTN).
4. أمن حقيقي (مصادقة، تشفير للأسرار، TLS) لأن طبيعة "السيادة" المحلية لا تعني الأمان.
5. تنظيف شامل للنسخ القديمة والملفات الميتة.

**التوصية العملية:** ابدأ من `server/` القديمة (التي تحتوي `RedApprovalService` حقيقياً) + `backend-server`، أصلح الـ Gradle أولاً حتى ترى `./gradlew assembleDebug` ينجح، ثم ركّب بروتوكول RED عبر `shared-proto` في مسار واحد متكامل من التطبيق إلى الخادم إلى اللوحة. عندها فقط — وليس قبلها — يمكن الحديث عن "منظومة سيادية".

> *أُعدّ هذا التقرير بعد فحص سطري مباشر لجميع الملفات المخصصة والبنية العامة، مع تحقق آلي من كل مرجع وكل ادعاء. جميع المواقع المذكورة صحيحة بالنسبة للنسخة النهائية `workspace-019fc4ca-...zip`.*
