# 🔴 الفهم الشامل الكامل لمشروع RED Ultimate — RED_Ultimate_V1

> **تاريخ الفحص السطري الكامل:** 2026-08-04
> **المرجع:** الفرع `arena/019fce61-red-ultimate-v1` — آخر دمج `d7024f7` ("AQYAL 5-tab UI, real Dinstar HTTP API, dual-engine dialer")
> **نطاق الفحص:** 10,071 ملفاً (~94MB) — كل ملف مخصص قُرئ سطراً سطراً، والبنية العامة وملفات البناء فُحصت بالكامل.
> **ملاحظة مهمة:** هذا التقرير يصف **الحالة الحالية الفعلية** للمستودع، ويصحح التقارير السابقة (`TECHNICAL_REPORT_AR.md` و`VERIFICATION_REPORT_AR.md` بتاريخ 2026-08-03) لأن **كثيراً من الأخطاء المذكورة فيها أُصلح في الدمج الأخير**، بينما بقي جوهر الاستنتاج (المشروع لا يُبنى ولا يعمل كمنظومة واحدة) قائماً بأخطاء جديدة/متبقية موثقة هنا.

---

## 1) ما هو المشروع؟ (الخلاصة التنفيذية)

**RED Ultimate** = محاولة بناء "منظومة مراسلة سيادية محلية" (بديل عن Signal) عبر:

1. **نسخ كامل لـ Signal-Android** (أحدث إصدار، AGPLv3) وإعادة تسميته شاملة:
   - الحزمة: `org.thoughtcrime.securesms` ← `com.red.sovereign` (0 مرجع متبقٍ في `src/main` — تحقق آلي)
   - الصفوف: `SignalExecutors`←`REDExecutors`, `SignalStore`←`REDStore`, `SignalServiceAttachment`←`REDServiceAttachment`, `SignalWebSocket`←`REDWebSocket`, `ConscryptProvider`←`ConscryptRED`, `SignalGlideCodecs`←`REDGlideCodecs`, `SignalTheme`←`REDTheme` (في `core/ui`), `SignalServiceNetworkAccess` (الملف بقي بنفس الاسم لكنه يقرأ من `BuildConfig`)
2. **طبقة RED مخصصة** تدّعي 3 أنظمة:
   - **النظام A:** مكالمات VoIP/بث عبر SFU محلي (mediasoup) — 1080p/AV1 (الادعاءات تتأرجح بين 4K و1080p)
   - **النظام B:** بوابة GSM/PSTN عبر جهاز **DINSTAR UC2000-VE-8T** + أستريكس
   - **النظام C:** رسائل بتوصيل مضمون (UUID v7 + ACK + مزامنة فجوات) عبر WebSocket وMongoDB/Redis
   - + تسجيل بموافقة إدارية، قصص 24 ساعة، لوحة تحكم، "تشفير مقاوم للحواسيب الكمومية"

**البنية الفعلية للمستودع (3 طبقات أندرويد + خادم + لوحة + بنية تحتية):**

```
RED_Ultimate_V1/
├── RED_Ultimate/                  # جذر المشروع الحقيقي
│   ├── app/                       # (1) تطبيق Signal الكامل + طبقة RED المخصصة ← الوحدة :app
│   ├── core/ lib/ feature/        # وحدات Signal الأساسية (سليمة)
│   ├── backend-server/            # خادم Spring Boot 3.4 (67 ملف Kotlin)
│   ├── admin_dashboard/           # لوحة تحكم React 19 + antd
│   ├── media-sfu/                 # SFU (mediasoup) — منفّذ فعلياً الآن
│   ├── pstn-asterisk/             # إعدادات أستريكس لبوابة DINSTAR
│   ├── shared-proto/              # تعريفات ProtoBuf
│   ├── android/                   # (2) نموذج أندرويد مستقل "AQYAL" — غير مدرج في البناء
│   ├── app-android/               # (3) نموذج أقدم (com.red / developedchat) — غير مدرج
│   ├── server/                    # خادم قديم (مرجعي)
│   ├── admin-dashboard/           # نسخة لوحة قديمة (صفحتان فقط)
│   ├── demo/ benchmark/ microbenchmark/ baseline-profile/  # وحدات Signal الأصلية
│   ├── build-logic/ fast-lint/ lintchecks/ wire-handler/ reproducible-builds/
│   ├── infrastructure/ gradle/
│   ├── docker-compose.yml nginx.conf build-and-run.sh ...
├── TECHNICAL_REPORT_AR.md / VERIFICATION_REPORT_AR.md   # تقارير سابقة (قديمة جزئياً)
├── declared_deps.txt / imports_list.txt / used_imports.txt   # نواتج فحص آلي سابقة
└── image-search/                 # صور أيقونات مقترحة
```

---

## 2) تطبيق أندرويد الرئيسي `app/` (الأهم)

### 2.1 الحجم والبنية
- `src/main`: **5,825 ملفاً** = 2,540 Kotlin + 1,262 Java + 1,850 XML موارد + JNI (ملفا C++ لـ FileUtils من Signal) + proguard (17 ملفاً) + lint-baseline.xml
- `src/androidTest`: 694 ملفاً (اختبارات Signal الأصلية) — `src/test`: 310 ملفاً
- `sampledata/contacts.json` + `.tx/config` (ترجمة Transifex) — من Signal

### 2.2 إعادة التسمية (منفّذة بدقة — نقطة قوة مؤكدة)
- 0 مرجع لـ `org.thoughtcrime` في `src/main`؛ 170 ملفاً في `database.helpers.migration`، 122 في `jobs`... كلها `com.red.sovereign.*`
- تعريفات REDStore (3 ملفات)، REDExecutors، REDServiceAttachment (4)، REDWebSocket (2)، ConscryptRED، REDGlideCodecs، REDServiceUrl — كلها متسقة مع الاستخدام
- `MainActivity` واحدة فقط الآن (في `org/thoughtcrime/securesms/MainActivity.kt` بحزمة `com.red.sovereign`) — **النسخة المكررة حُذفت** (كانت خطأ C4 في التقرير القديم)

### 2.3 ملفات البناء — الوضع الحالي

**`app/build.gradle.kts` (70 سطراً — مختزل جداً مقابل 47KB الأصلي):**
- ✅ أُصلح: `manifestPlaceholders["mapsKey"]` موجود، `buildConfigField` لكل العناوين (SIGNAL_URL, STORAGE_URL, CDN, SFU, GIPHY...), `buildFeatures { compose; buildConfig; viewBinding }`
- ✅ plugins: Hilt + kotlinx-serialization معلنة (ومعرّفة في الجذر الآن)
- ⚠️ `composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }` — متهالك مع Kotlin 2.2.20/AGP 9.2.1 (أسلوب Compose compiler القديم)، و**`org.jetbrains.kotlin.plugin.compose` غير مطبّق على وحدة :app** (مطبّق فقط في `core/ui` و`core/network` وغيرهما) ⇒ كود Compose في :app (MainActivity + كل شاشات RED + واجهات Signal) لن يُصرَّف بشكل صحيح
- ❌ **`apply(from = "dependencies.gradle.kts")` يشير إلى مفاتيح غير موجودة في الكتالوج**: `libs.androidx.room.runtime` و`libs.androidx.room.ktx` غير معرّفتين إطلاقاً في `gradle/libs.versions.toml` (لا يوجد أي إدخال room في الكتالوج) ⇒ **فشل ترجمة سكربت البناء نفسه (خطأ حتمي)**

**`app/dependencies.gradle.kts`:**
- ✅ `lib:libsignal-service`, `core:util`, `core:ui`, libsignal-android, sqlcipher, ringrtc, `bundles.media3` (موجود في الكتالوج: exoplayer+session+ui), asterisk-java, kotlinx-serialization, navigation-compose, activity-compose, material
- ❌ ناقص: hilt-android (لا مكتبة Hilt رغم plugin!)، compose BOM/Material3 (يأتي transitively من core:ui عبر api لكن الممارسة الصحيحة إضافته)، **accompanist-permissions** (مستخدم في `PermissionRequestScreen` و`RedPermissionGate` وليس في الاعتماديات)، Room compiler (kapt) — بدونه RoomDatabase لا يولّد تنفيذها (فشل وقت التشغيل)، okhttp (transitive من libsignal-service)، **shared-proto غير مربوط إطلاقاً** ⇒ `com.red.sovereign.proto.RedProtos` غير موجود على classpath

### 2.4 ملفات RED المخصصة (31 ملفاً في `com/red/sovereign`) — قراءة سطرية

| الملف | الحالة الفعلية (2026-08-04) |
|---|---|
| `RedSovereignApp.kt` | `@HiltAndroidApp` يمتد **`Application()` فقط** — لا يمدّد `ApplicationContext` (نواة Signal: DB، JobManager، الإشعارات، التشفير) ⇒ **لن تُهيّأ أبداً**. الـ Manifest يشير إليه (`android:name="com.red.sovereign.RedSovereignApp"`). |
| `core/auth/IdentityManager.kt` | ✅ موجود الآن (interface: getRedId, getUserHandle, getAuthToken, isLoggedIn, logout) |
| `core/auth/RedIdentityManager.kt` | ✅ موجود — لكن يخزّن AUTH_TOKEN/GSM/RED_ID في SharedPreferences **بدون تشفير**، و`getUserHandle()` ناقص التنفيذ (interface يطلبه) |
| `core/crypto/QuantumGuard.kt` | `wrapWithQuantum()` **يعيد البيانات كما هي** (محاكاة صريحة) — "التشفير الكمومي" غير موجود فعلياً |
| `core/database/MasterDao.kt` | ✅ موجود (insertMessage/insertGroup/insertCall/getMessages/getMessageStatus/updateMessageStatus/getGroups/getCallLogs) — يستخدم `MessageEntity` و`GroupEntity` و`CallLogEntity` من `RedMasterDatabase.kt` |
| `core/database/RedMasterDatabase.kt` | ✅ موجود (MessageEntity, GroupEntity, CallLogEntity + RedDao + قاعدة Room v1) |
| `core/delivery/MasterDeliveryEngine.kt` | ✅ موجود (UUID v7 صحيح + initialize) — **لكن لا `dispatchMessage` ولا `processIncomingRED`** ⇒ `ChatViewModel.sendMessage` و`NotificationBridge` **لا يترجمان** |
| `core/delivery/RedDeliveryEngine.kt` | ✅ موجود (dispatchMessage + إعادة محاولة) — **لكن يستورد `com.red.sovereign.proto.ChatProtos` غير الموجود** (البروتوكول يولّد `RedProtos` فقط) ⇒ خطأ ترجمة |
| `core/delivery/SyncEngine.kt` | ✅ موجود — **لكن يستدعي `redDao.getLastSequenceNumber()` غير المعرّفة في RedDao** ⇒ خطأ ترجمة |
| `core/di/RedMasterModule.kt` | ❌ `Room.databaseBuilder(...).addMigrations()` **بلا وسائط** ⇒ خطأ ترجمة (تحتاج Migration واحداً على الأقل) |
| `core/network/RedWebSocketClient.kt` | ✅ موجود (OkHttp WebSocket كامل مع listener وBearer token) |
| `core/utils/RedMediaTransporter.kt` | ❌ يستورد `com.red.sovereign.core.network.MinioUploader` **غير الموجود في وحدة :app** (موجود فقط في `android/` خارج البناء) + `File(uri.path!!)` ممارسة خاطئة ⇒ خطأ ترجمة |
| `core/MasterFeatureSet.kt` | ✅ data class نظيف (voip + identity) — لا verifyIntegrity وهمية |
| `features/auth/SovereignAuthScreens.kt` | RegisterScreen/LoginScreen — واجهة فقط، أزرار بلا اتصال شبكة |
| `features/auth/PermissionRequestScreen.kt` | ✅ موجود — يستخدم accompanist (ناقص من الاعتماديات) |
| `features/auth/RedPermissionManager.kt` | ✅ موجود |
| `features/calls/RedVoipMaster.kt` | ✅ موجود (إدارة جلسات في الذاكرة فقط — لا WebRTC فعلي في هذه النسخة) |
| `features/calls/CallOrchestrator.kt` | ❌ يستدعي `pstnViewModel.makePstnCall()` — **غير موجودة** (الموجودة `dialPstn`) ⇒ خطأ ترجمة |
| `features/calls/CallViewModel.kt` | ✅ يستدعي startSecureCall(target) — موجودة |
| `features/chat/ChatViewModel.kt` | ❌ `deliveryEngine.dispatchMessage(chatId, text)` على `MasterDeliveryEngine` — **غير موجودة** ⇒ خطأ ترجمة |
| `features/chat/GroupIDManager.kt` | ❌ يستخدم `RedWebSocketClient` **بدون import** + `getUserHandle()` موجودة الآن ✓ ⇒ خطأ ترجمة (import ناقص) |
| `features/chat/ChatDetailScreen.kt` | واجهة ببيانات ثابتة ("Hello Team!", "System B is now live.") — لا DB ولا WebSocket |
| `features/chat/RedChatDetail.kt` | ❌ دالة `ChatDetailScreen(chatId, viewModel)` بنفس اسم دالة الملف السابق (تحميل زائد متعارض) + `viewModel.messages` غير موجودة + `ChatInputBar(onSend=...)` (المعرّفة بلا وسائط) + `RedChatBubble` غير موجودة في :app ⇒ أخطاء ترجمة متعددة |
| `features/chat/RedChatScreen.kt` | ❌ `RedChatTopBar` و`RedMessageInput` غير معرّفتين ⇒ خطأ ترجمة |
| `features/chat/LuxuryChatBubble.kt` | ✅ فقاعة تدرّج أحمر/أسود |
| `features/pstn/DialPadScreen.kt` | ❌ يستورد `com.red.sovereign.features.calls.YemeniOperatorDetector` — **غير موجود** ⇒ خطأ ترجمة |
| `features/pstn/PstnViewModel.kt` | ✅ موجود (dialPstn/endGsmCall/getActiveCall) |
| `features/stories/StoryViewModel.kt` | ✅ موجود (قائمة في الذاكرة + انتهاء صلاحية) |
| `network/NotificationBridge.kt` | ❌ يستدعي `deliveryEngine.processIncomingRED()` — **غير موجودة في MasterDeliveryEngine** ⇒ خطأ ترجمة |
| `network/RedPushService.kt` | ❌ `R.drawable.ic_launcher_red` **غير موجود في الموارد** ⇒ خطأ ترجمة + **غير مسجّل في الـ Manifest** + بلا Notification Channel (قناة `"RED_PUSH"` تُستخدم بلا إنشاء) |
| `ui/RedMainHost.kt` | ❌ يستورد `com.red.sovereign.features.calls.DialPadScreen` — الحزمة الصحيحة `features.pstn` ⇒ خطأ ترجمة |

### 2.5 طبقة `developed/` (داخل org/thoughtcrime/securesms — 8 ملفات)
- `DevelopedChatCore.kt`: `checkApprovalStatus()` **تُرجع true ثابتة** — الموافقة الإدارية وهمية
- `DevelopedChatInitialization.java`: يضبط `signal.service.url` — خاصية **لا يقرؤها أحد** (Signal يقرأ من BuildConfig) والدالة لا تُستدعى
- `MasterIntegration.kt`: ❌ يستورد `com.red.core.delivery.DeliveryEngine` و`com.red.features.pstn.PstnEngine` **غير الموجودين في :app** ⇒ خطأ ترجمة؛ `checkAdminApproval()` تُرجع false
- `delivery/GuaranteedDelivery.kt`: `generateMsgId()` = `"${millis}-${UUID}"` — **ليست UUID v7**؛ `start()` يطبع فقط
- `voip/UltraHDCall.kt` + `DevelopedVoipController.java` + `QualityController.kt`: يطبعون فقط؛ خريطة المعاملات تُلقى (لا تمرير لأي واجهة)؛ الادعاء 4K مقابل 5Mbps (لا يكفي حتى 1080p جيداً)
- `pstn/DuminManager.kt`: `connect()` يطبع فقط

### 2.6 الـ Manifest (`AndroidManifest.xml` — 71KB)
- `android:name="com.red.sovereign.RedSovereignApp"` (Application فقط) + **`android:allowBackup="true"`** (ثغرة لتطبيق مشفر)
- MainActivity واحدة، `${mapsKey}` مستخدم مرة واحدة (معرّف في build.gradle الآن ✓)
- كل خدمات Signal الأصلية مسجلة (Services/Receivers/Providers) — لكن **RedPushService غير مسجل**

### 2.7 الشبكة — "السيادة" غير مكتملة
- `push/SignalServiceNetworkAccess.kt` يقرأ `BuildConfig.SIGNAL_URL` (= `https://chat.red.local`) — أي أن العناوين **معرّفة في build.gradle** (خطوة صحيحة) لكنها أسماء نطاقات محلية غير قابلة للحل فعلياً
- `static_ips.properties` غير موجود في res (بحث شامل) — كان التقرير القديم يذكر وجود IPs سيجنال
- لا `signal.org` متبقياً في `java/` ولا `res/` (تحقق آلي) — التقرير القديم عن بقاء خوادم Signal **لم يعد صحيحاً** في `src/main` (بقيت إشارات "fastly" المولّدة من أسماء النطاقات المعاد تسميتها `storage.red.local.global.prod.fastly.net`)

---

## 3) الوحدات المساندة `core/` `lib/` `feature/` (سليمة)

- **core/**: util, ui, models, models-jvm, util-jvm, serialization, network — **كلها بملفات build.gradle.kts** ✓
  - `core/ui/.../theme/SignalTheme.kt` يعرّف `REDTheme` (198 سطراً) — يُستورد من 73 ملفاً ✓
  - `core/network/.../SignalServiceConfiguration.kt` يعرّف `REDServiceConfiguration` ✓
- **lib/**: 18 مكتبة (libsignal-service، network، glide، archive، apng، contacts، blurhash، paging، photoview، qr، video، spinner، sticky-header-grid، debuglogs-viewer، device-transfer، image-editor، donations، billing) — كلها بملفات بناء ✓
- **feature/**: camera, media-send, registration فقط (لا chat/calls/pstn/stories — صحيح لأن settings لم تعد تضمها)
- **demo/**: وحدات Signal التجريبية (apng, camera, paging, qr, registration, spinner, video) — **غير مدرجة في settings** (مرجعية)
- **benchmark/ microbenchmark/ baseline-profile/**: مدرجة (benchmark, microbenchmark) — بنية Signal الأصلية ✓

---

## 4) الخادم الخلفي `backend-server/` (Spring Boot 3.4 — 67 ملفاً)

### 4.1 ما أُصلح منذ التقرير القديم ✅
- `settings.gradle.kts` + `build.gradle.kts` + `Dockerfile` + **`application.yml`** + Flyway migrations (V1, V2) + `master-schema.sql` + اختبار `MessageServiceTest.kt`
- `WebSocketConfig.kt` (WebSocketConfigurer يسجّل `/ws/master`, `/ws/chat`, `/ws/red`, `/ws/admin/logs`) ✅
- `RedApprovalService.kt` و`RedSecurityService.kt` و`MessageService.kt` و`SecurityConfig.kt` — **موجودة الآن** (كانت مفقودة)
- Dockerfile: `FROM gradle:8.12-jdk21` يبني `gradle build -x test` ثم JRE 21 + HEALTHCHECK على `/health`

### 4.2 أخطاء ترجمة مؤكدة (قراءة سطرية) ❌
1. **`RedApprovalService`** (auth) تنقصها 3 دوال تُستدعى من 3 أماكن: `getPendingList()` (AdminMasterController, RedMasterController, AdminController)، `processAction()` (RedMasterController)، `rejectUser()` (AdminController) ⇒ **3 أخطاء ترجمة**
2. **`MessageService.processIncoming`** (messaging) توقيعها `(senderId, receiverId, conversationId, payload, messageType)` بينما الـ websocket handlers الثلاثة (`RedMasterHandler`, `RedWebSocketHandler`, `ChatWebSocketHandler`) تستدعي `processIncoming(msg)` بوسيط واحد ⇒ **3 أخطاء ترجمة**
3. **`ChatProtos.MessageAck`** في `messages.proto` لا يحوي حقل `sequence_number` و`status` من نوع enum `AckStatus` — بينما `sendAck` في `RedWebSocketHandler`/`ChatWebSocketHandler` تستدعي `.setSequenceNumber(seq)` و`.setStatus("SENT")` ⇒ **خطأ ترجمة في الملفين**
4. **`CoreService`** يستورد `com.red.sovereign.features.chat.GroupEntity` و`com.red.sovereign.features.stories.StoryEntity` — غير موجودتين في الخادم (لا يوجد features في backend) ⇒ خطأ ترجمة
5. **`PstnManager`** يستخدم `@Value("${ASTERISK_AMI_USER}")` **بدون import** لـ `org.springframework.beans.factory.annotation.Value` ⇒ خطأ ترجمة (بينما `pstn/DinstarMasterService` يستوردها بشكل صحيح)
6. **`CallWebSocketHandler`** يستورد `com.red.sovereign.proto.CallProtos` — **غير معرّف في red_protocol.proto إطلاقاً** (لا CallRED ولا CallSignal) ⇒ خطأ ترجمة + **ليس Bean وغير مسجل في WebSocketConfig**
7. **حزمة `com.developedchat/`** (10 ملفات بحزم `com.red.admin`, `com.red.auth`, `com.red.core.*`): `com.red.core.models.User`/`UserStatus` غير موجودة في الخادم ⇒ خطأ ترجمة في AuthController؛ وحتى لو تُرجمت، **خارج نطاق component scan** (`com.red.server`) فلن تُدار
8. **`AdminMasterController.executeDinstarAction`** يعيد `"EXECUTED"` بلا تنفيذ (وهمي)

### 4.3 منطق يعمل فعلياً (نقاط إيجابية)
- **`MessageService`** (المسار الصحيح): Dedup → تسلسل Redis (`red:seq:`) → تخزين Mongo → presence → إشعار Redis pub/sub → ACK/قراءة — **منطق توصيل حقيقي مكتوب** (لكن لا يُستدعى بسبب خطأ التوقيع أعلاه)
- **`DinstarHardwareService`**: طلبات HTTP REST حقيقية لجهاز DINSTAR (`/api/status`, `/api/get_port_status`, `/api/set_sip`, `/api/reboot`, `/api/dial`) مع اكتشاف تلقائي (6 عناوين) وfallback — **أفضل مكوّن في الخادم**
- **`RedSecurityService`**: kill-switch/wipe عبر Redis pub/sub (قناة `security:wipe`) — بنية صحيحة لكن لا مستهلك
- **`HealthController`**: يفحص Mongo+Redis+Postgres فعلياً ويعيد `UP/DOWN`
- **`AdminMonitorController`**: إحصاءات حية (keys presence، عدّاد messages، JVM load، uptime)
- **`DuminTelemetryController`**: تيليمتري (بيانات شبه ثابتة لكنها منظمة)
- Flyway V1/V2 (Postgres: users, messages, dinstar...) موجودة

### 4.4 ثغرات وظيفية/أمنية قائمة
1. **لا مصادقة فعلية**: `SecurityConfig` يجعل كل `/api/**` permitAll مع TODO "Restrict in production"؛ JWT في AuthController غير موقّع (`"red-jwt-${UUID}"`)؛ قاعدة مستخدمين في الذاكرة (تمسح عند إعادة التشغيل)؛ بلا hashing لكلمات المرور
2. **WebSocket بلا مصادقة**: `session.attributes["userId"]` لا يضبطه أحد (لا HandshakeInterceptor) ⇒ انتحال كامل للهوية
3. **`handleSync` في RedMasterHandler فارغ** — مزامنة الفجوات غير منفذة؛ **`DeleteRED` غير معالج** في أي handler (رغم تعريفه في البروتوكول)
4. **الرسائل لغير المتصلين لا تُعاد** — التوجيه فقط للجلسات النشطة (التخزين موجود لكن لا إعادة تسليم)
5. **`redis.keys()`** (O(N)) في AdminMonitorController/MasterStatsService/MasterOrchestrationService/IronSyncService — محظورة في الإنتاج
6. نظاما تسلسل متوازيان: `RedisSequenceGenerator` (`seq:`) و`RedisManager` (`red:seq:`)
7. `SearchService` يبحث نصياً (`TextCriteria`) في رسائل مخزنة **كـ ByteArray مشفّر** — تناقض تصميمي مع E2EE
8. أسرار افتراضية مكشوفة: `password`, `red_secret_123`, `redturnsecret`, `redsecret123` في docker-compose/application.yml/manager.conf
9. `PstnManager` يتصل بأستريكس عند البدء — فشل الاتصال يطبع تحذيراً فقط (لا استرجاع)
10. `DinstarMasterClient.getPortsRealtimeStatus()` ما زال **بيانات عشوائية** (الطلب الحقيقي معلّق كتعليق) — بينما `DinstarHardwareService` حقيقي ⇒ تناقض بين المكوّنين، وترقيم المنافذ (0..7) مقابل (1..8) في `DinstarMasterService`/`DinstarLoadBalancer`

### 4.5 بقية الملفات
- `MasterLogicIntegrator`: ليس Bean وغير مستخدم (تركيب فقط)
- `LiveStreamService`: عدادات ذاكرة لا علاقة لها بـ mediasoup
- `AdminStoryService`: إحصاءات تقديرية (2MB لكل قصة) + purge
- `StorageMonitorService`: يفحص مسارات داخل الحاوية لا تطابق volumes الفعلية
- `IronSyncService`: State Vectors عبر Redis (30 يوماً) — فكرة صحيحة لكن `keys()`
- `DinstarEventListener`: يستمع أحداث أستريكس (AMI) لكنه لا يُسجَّل في ManagerConnection أبداً (لا addListener) — غير فعّال
- `DinstarLoadBalancer`: round-robin 1..8 بذرية AtomicInteger
- `TypingHandler`: بث typing عبر Redis
- `AdminLogHandler`: بث سجلات للوحة عبر `/ws/admin/logs` ✓ (sendMessage في afterConnectionEstablished بلا try/catch)
- اختبار واحد: `MessageServiceTest.kt`

---

## 5) لوحة التحكم `admin_dashboard/` (React 19 + antd + echarts)

### 5.1 ما أُصلح ✅
- `package.json` كامل (scripts: start/build/test + react-dom + @ant-design/icons + echarts-for-react + react-scripts)
- `src/index.js` + `src/App.jsx` (قائمة جانبية 6 صفحات) + `Dockerfile` + `nginx.conf` + `public/index.html`
- **5 تبويبات** كاملة في `pages/tabs/`: OverviewTab, AuthorityTab, DinstarTab, MessagingTab, SecurityTab + LogStreamerTab (كانت ناقصة)
- `pages/`: Dashboard.tsx, MasterOverview.tsx, UserApproval.tsx, DinstarControl.tsx, Diagnostics.js, MasterLayout.tsx (+ قديمة: Approvals.js, DinstarMonitor.js, DuminAdvanced.tsx, DuminMonitor.js, MasterControl.tsx)

### 5.2 أخطاء/مشاكل متبقية ❌
1. **`DuminAdvanced.tsx`**: `import { signalFilled } from '@ant-design/icons'` — خطأ إملائي (الصحيح `SignalFilled`) ⇒ فشل بناء CRA
2. **`MasterLayout.tsx`**: التبويبات السبعة في القائمة لكن `currentTab === '5'` (Media SFU) و`'7'` (Infrastructure) **بلا محتوى** — غير معروضين
3. **`LogStreamerTab.tsx`**: `ws://${hostname}:8080` — خلف nginx يجب `/ws/...` عبر البروكسي (المنفذ 8080 غير مكشوف في docker-compose للوحة) ⇒ لن يتصل
4. **`Dockerfile` معطوب وظيفياً**: `npm install --production` ثم `npx serve -s public` — **لا `npm run build`**؛ و`public/index.html` لا يحوي أي script (البناء لا يكتب إلى public) ⇒ الحاوية تعرض صفحة فارغة
5. **`nginx.conf` الخاص باللوحة غير مستخدم** (Dockerfile يستخدم serve بدلاً منه)
6. **endpoints لا تطابق الخادم**:
   - `Approvals.js` (قديمة): `/api/admin/pending-users` و`/api/admin/approve/:id` — غير موجودة (الموجود `/api/admin/users/pending`, `/api/admin/users/update-status`)
   - `MasterOverview.tsx` يتوقع `ws_active/pending_auth/gsm_signal/db_storage` بينما الخادم يعيد `active_users/messages_24h/system_load/db_health/pending_approvals` ⇒ بطاقات undefined
   - `AuthorityTab` يستدعي `/api/admin/users/approve/${userId}` (POST) — الخادم لديه `/api/admin/users/update-status?userId=&status=` و`/api/master/v1/auth/action` ⇒ خطأ 404/405
   - `SecurityTab` يستدعي `/api/master/v1/security/kill-switch` (POST body) و`/api/master/v1/security/wipe/${id}` — الخادم لديه `/api/master/v1/security/wipe?userId=` فقط ⇒ عدم تطابق
   - `MessagingTab`/`OverviewTab` يستدعيان `/api/master/v1/stats/realtime` ✓ موجودة
   - `Dashboard.tsx` يتوقع `weekly_messages/gsm_active/pending_users/cpu_load/ram_usage` — الخادم يعيد `active_users/total_messages/system_load/uptime_ms/cpu_cores/jvm_memory_mb` ⇒ undefined
7. **بيانات ثابتة**: `DuminMonitor.js` (simStatus/balance ثابتة)، `MasterControl.tsx` (`setStats({msgs: 8540, calls: 142, load: 22})` يدوياً)، MessagingTab جدول رسائل ثابت، SecurityTab أحداث ثابتة
8. `DinstarTab` يتوقع `slot.index` (من DinstarMasterClient 0..7 ✓) بينما `DinstarControl` يتوقع `port.index` من `/api/admin/dinstar/status` (DinstarHardwareService تعيد index ✓) — متسقان معاً، لكن `DinstarMasterService` القديم يعيد `slot` (1..8) — تباين إن استُخدم
9. `Diagnostics.js` يعرض "OPERATIONAL" دائماً (status READY ثابت) + يستورد أشياء بلا استخدام
10. لا `vite`/`webpack` config — يعتمد react-scripts (CRA) ✓ مقبول لكن نسخة 5 قديمة

---

## 6) وسيط الوسائط `media-sfu/` (Node.js + mediasoup) — **منفّذ فعلياً الآن** ✅

- **`server.js` (~300 سطراً)**: 2 workers mediasoup (RTC 40000–40100)، إنشاء rooms/routers (Opus + VP8/VP9/H264)، `join` → createWebRtcTransport، `connectTransport`، `produce` (يبث `newProducer` لبقية الأعضاء)، `consume` (ينشئ consumer transport + consume)، `leave`، تنظيف عند close/error، معالجة أخطاء try/catch لكل رسالة
- `package.json` (mediasoup ^3.12, ws ^8.13) + `Dockerfile` (node:22-bookworm + build-essential + python3) ✓
- **نقاط ضعف**: `consume` يستهدف منتجاً واحداً فقط (أول peer بمنتج) — لا تكرار لجميع المشاركين؛ `announcedIp: null` (قد يفشل خلف NAT/Docker)؛ لا TLS (wss)؛ لا مصادقة؛ لا إشعار `producerClosed`/`consumerClosed`؛ لا `pause/resume`

---

## 7) بوابة PSTN `pstn-asterisk/`

- `pjsip.conf`: transport-udp + endpoint `dinstar-gateway` (g729/alaw/ulaw/gsm، direct_media=no) + identify 192.168.1.100 + endpoint `webrtc-client` (opus/vp9/av1, webrtc=yes, DTLS ذاتي) + auth `webrtc-auth` + AOR
- `extensions.conf`: `from-internal` → `Dial(PJSIP/${EXTEN}@dumin-trunk)` و`from-pstn` → `Dial(PJSIP/webrtc-client)`
- **❌ خطأ حاسم**: `dumin-trunk` **غير معرّف في pjsip.conf** (المعرّف الفعلي `dinstar-gateway`) ⇒ Dial يرمي "No such endpoint" — نفس الملاحظة تنطبق على `PstnManager.dialGsm` في الخادم الذي يستخدم `dumin-trunk`
- `manager.conf`: `red_admin / red_secret_123` (read/write = all) — سر ثابت مكشوف
- `Dockerfile`: `FROM andrius/asterisk` + نسخ الإعدادات ✓ (يستبدل الإعدادات الافتراضية)
- `pjsip_dinstar.conf`: نسخة بديلة (context=from-dinstar) — غير مستخدمة في Dockerfile
- لا قناة إشعار للجوال الوارد (webrtc-client بلا تسجيل من التطبيق — لا SIP stack في التطبيق أصلاً)

---

## 8) البروتوكولات `shared-proto/`

- **`build.gradle.kts` موجود الآن** (java-library + com.google.protobuf 0.9.4 + protobuf-java 3.25.1) ✓
- `red_protocol.proto`: `RedRED` (oneof: message/ack/sync_req/typing/delete) + ChatMessage (حقول underscore) + MessageAck (message_id/sequence_number/status نصي) + SyncRequest + TypingRED + DeleteRED — حزمة `com.red.sovereign.proto` → يولّد `RedProtos`
- `messages.proto`: ChatMessage مختلف (type enum MessageType) + MessageAck مختلف (status enum AckStatus، **بلا sequence_number**) — حزمة `com.red.proto` → يولّد `ChatProtos`
- **❌ لا وحدة تستخدم shared-proto**: لا `:app` ولا `:backend-server` يضيفان dependency عليه ⇒ `RedProtos`/`ChatProtos` غير مولّدين في أي classpath؛ و`com.red.sovereign.proto.ChatProtos` (المستخدم في RedDeliveryEngine بالجهاز) **لا يولّده أي ملف proto**
- تناقض: نموذجان مختلفان لـ ACK

---

## 9) الطبقتان القديمتان (خارج البناء — ليست في settings.gradle.kts)

### 9.1 `android/` — نموذج "AQYAL" (49 ملفاً، حزم com.red.app / com.red.sovereign / com.red.core / com.red.features)
- **أفضل واجهة Compose في المشروع**: `RedMainDashboard` (5 تابات: المحادثات/المكالمات/لوحة الاتصال/الاستكشاف/الإعدادات)، `RedTheme.kt` (نظام تصميم "ملكي": ذهبي AqyalGold + أسود Obsidian + أزرق ملكي + سماوي)، `SovereignBackground` (تدرّج متحرك)، `AqyalEpicButton`
- `RedChatListScreen` (عربى كامل + زر اتصال Dinstar ذهبي لكل جهة اتصال)، `RedCallLogScreen` (سجل موحد VoIP/DINSTAR)، `RedExploreScreen` (بثوث + Spaces)، `RedSettingsScreen` (إدارة هاردوير + اكتشاف ذكي)، `RedCallForegroundService` (قناة إشعارات مكالمات)، `VideoCallScreen` (SurfaceViewRenderer WebRTC)
- `MasterDatabase` (Room v2 + ترحيل MIGRATION_1_2 لجدولي stories/story_views)، `BurnManager` (رسائل تحترق)، `MessageDeliveryManager` (إرسال + ACK)، `RedDeliveryEngine` (UUID v7 + backoff)، `SyncManager` (مزامنة فجوات)، `MinioUploader` (رفع OkHttp)، `MediaCompressor`/`VideoTrimmer` (Media3 Transformer)
- `RedVoipMaster` (WebRTC PeerConnection + mediasoup signaling)، `WebRtcSignaler` (CallProtos — غير موجود في البروتوكولات!)، `LiveBroadcastManager` (بث حي)
- **❌ مراجع مفقودة كثيرة**: `MediasoupClient`, `StoryDao`, `StoryEntity`, `StoryViewEntity`, `StoryRepository`, `ApprovalManager`, `VoipController`, `PstnEngine` (موجود لكن بمراجع ناقصة), `DeliveryEngine`, `RedLogger`, `CallProtos`, `ChatProtos(com.red.proto)`, `PstnViewModel.syncGatewayStatus`... ⇒ لا يترجم حتى كوحدة مستقلة
- `build.gradle.kts` مستقل (namespace com.red.sovereign, compileSdk 35, Hilt) لكنه غير مربوط بأي بناء

### 9.2 `app-android/` — النموذج الأقدم (29 ملفاً، حزم com.red / com.developedchat)
- `MainActivity` (BottomNav 5 تابات)، `AppNavigation`/`NavGraph`، شاشات Auth كاملة (Permissions→Welcome→Register→Login→Pending/Rejected/Banned مع AuthViewModel + AuthApi عبر Retrofit/Moshi إلى `http://192.168.1.50:8080/api/`)
- `PstnViewModel` (polling لحالة مكالمة Dumin + PstnDatabase Room)، `ChatDetailScreen` (فقاعات + DeliveryStatusIcon)، `ChatListScreen` (mock)، `StoryCleanupWorker` (WorkManager 15 دقيقة)
- **❌ مراجع مفقودة**: `DuminApi`, `REDDatabase`, `MessageDao`, `MessageEntity`, `MessageStatus`, `StoryDao`, `ChatProtos(com.red.proto)` غير مولّد... ⇒ لا يترجم

### 9.3 `server/` — الخادم القديم (5 ملفات)
- `RedMasterServer` (Spring Boot)، `RedApprovalService` (نفس نسخة in-memory)، `DeviceManager`، `SecurityController` (kill-switch يطبع فقط)، `application.properties` (منفذ 8443 + SSL p12 بكلمة `red-secret-password` — الملف p12 غير موجود) — مرجعي فقط

---

## 10) بنية Gradle الجذرية — الوضع الحالي

### `settings.gradle.kts` (تحسّن كبير ✅)
- يضم فقط الوحدات الموجودة: `:app`, `:core:*` (7), `:lib:*` (18), `:feature:*` (3), `:lintchecks`, `:fast-lint`, `:build-logic:tools`, `:benchmark`, `:microbenchmark`, `:shared-proto`
- ❌ **`include(":build-logic:tools")`** بينما `build-logic/settings.gradle.kts` موجود (build-logic جذر بناء مستقل) ⇒ تعارض بنية (يجب `includeBuild("build-logic")` مع حذف include أو العكس)
- dependencyResolutionManagement: google + mavenCentral + jitpack ✓

### `build.gradle.kts` (الجذر)
- plugins: android/kotlin/compose-compiler/ktlint/hilt/serialization/baselineprofile ✓ (كانت ناقصة)
- buildscript: AGP 9.2.1 + safe-args + protobuf 0.10.0 + wire 6.4.0 + benchmark + wire-handler-1.0.0.jar + KSP ✓
- ❌ **`tasks.register("buildQa")` و`tasks.register("format")` يستدعيان `gradle.includedBuild("build-logic")` — لا يوجد includeBuild بهذا الاسم** ⇒ أي تشغيل لـ `qa`/`format`/`buildQa`/`ci` يفشل (IllegalStateException)
- `projectsEvaluated`: يستخدم `tasks.findByPath(":app:testDebugUnitTest")` بأمان (بدون `!!`) ✓ — خطأ `:RED-Android` أُصلح
- `checkStopship` مكتوب (يفحص STOPSHIP عبر الملفات) ✓

### `gradle/`
- `libs.versions.toml` (AGP 9.2.1، Kotlin 2.2.20، Gradle wrapper 9.x، compileSdk 37، minSdk 23، compose BOM 2026.06.01، Hilt 2.52، media3 1.9.1، accompanist 0.28.0...) + benchmark/lint/test tomls + `verification-metadata.xml` (527KB) ✓
- **لا إدخال Room في الكتالوج** (المشكلة القاتلة لـ :app)

### أدوات الجودة (من Signal — سليمة)
- `build-logic/` (plugins: dependency-verification, licenses, signal-library, signal-sample-app, translations, ktlint + tools: SmartlingClient, StaticIpResolver) — **غير مربوط بالبناء الجذر**
- `fast-lint/` (لينتر AST مخصص: AlertDialog, DatabaseReference, ForegroundService, LogNotSignal...) + `lintchecks/` (Lint Registry) ✓
- `wire-handler/` (jar مبني 1.0.0 — استبدال countNonNull→countNonDefa للكود المولّد) ✓
- `reproducible-builds/` (Docker + apkdiff بالـ Python) ✓
- `lint.xml` (StopShip fatal، HardcodedText error...) ✓
- `lefthook.yml` (pre-push format) ✓

---

## 11) البنية التحتية (Docker/nginx/scripts)

- **`docker-compose.yml`**: 11 خدمة (backend, media-sfu, coturn, pstn-gateway, db-postgres:16, db-mongo:8, cache-redis:7, minio, nginx, admin-panel + volumes) مع healthchecks (backend: `/actuator/health` — actuator **مضاف الآن** في build.gradle.kts ✓ لكن نقطة البداية في Dockerfile تشغيل jar بـ healthcheck على `/health` — متسق تقريباً؛ compose يفحص `/actuator/health` بينما HealthController يعرّف `/health` فقط ⇒ **healthcheck سيفشل**)
- `nginx.conf` (الجذر): `/`→admin-panel:3000، `/api/`→backend:8080، `/ws/`→backend — **بلا TLS** (HTTP فقط)
- `admin_dashboard/nginx.conf`: proxy /api و/ws مع headers — غير مستخدم
- `build-and-run.sh`: يستخدم `docker-compose` القديم (وليس `docker compose`) ويطبع "All 9 Systems ONLINE" بلا تحقق
- `infrastructure/setup-env.sh`: mc/psql بلا فحص وجود؛ `mc policy set public` على bucket وسائط؛ كلمات مرور ثابتة
- `temp-dc.yml`: نسخة قديمة (server/mediasoup-sfu/asterisk/admin) — مرجعية
- `pkcs11.config`: OpenSC PKCS11 (للمفاتيح) — جاهز
- `.env.example` (DB_PASSWORD, AMI_PASSWORD, TURN_SECRET, REDIS_PASSWORD) ✓
- `.github/workflows/`: `deno.yml` و`docker-image.yml` — قوالب عامة لا علاقة لها بالمشروع فعلياً (لا تبني Android ولا backend)

---

## 12) التقارير والملفات الجذرية

- `FINAL_SUMMARY.md`: "Build errors: 0/12, Security gaps: 3" — **غير دقيق** مقابل الفحص الحالي (ما زالت أخطاء بناء متعددة مؤكدة)
- `TECHNICAL_REPORT_AR.md` (478 سطراً) / `VERIFICATION_REPORT_AR.md` (461 سطراً): تفصيلان ممتازان لحالة **2026-08-03** — أُصلح منذها: MainActivity المكررة، settings/features، :RED-Android NPE، plugins الجذر، manifestPlaceholders/buildConfigField، Dockerfiles (admin+sfu)، WebSocketConfig، application.yml، الفئات المفقودة (RedApprovalService/RedSecurityService/MessageService/IdentityManager/RedVoipMaster/PstnViewModel/StoryViewModel/MasterDao/RedWebSocketClient/RedChatBubble...)، media-sfu الكامل، نقطة دخول اللوحة. **ما زال قائماً**: أخطاء ترجمة عديدة (موثقة أعلاه)، stubs، ثغرات أمنية، media-sfu جزئي، لوحة جزئية
- `declared_deps.txt` / `imports_list.txt` / `used_imports.txt`: نواتج فحص آلي سابق (مرجعية)
- `MASTER_CHECKLIST.txt`: يدّعي "NO FEATURES MISSING. ZERO CLOUD DEPENDENCY" — **غير دقيق**
- `MASTER_GUIDE.md` (عربي): دليل التشغيل (Docker + Dinstar 192.168.1.100 + Android Studio + تبويبات اللوحة) — إرشادي
- `DEPLOY.md`: دليل نشر قديم (يشير لـ app-android وadmin-dashboard القديمين)
- `audit_check.py`: سكربت فحص سطحي (يبحث عن وجود ملفات فقط)
- `README.md`/`CONTRIBUTING.md`: من Signal مع استبدال الاسم (بقيت روابط Signal الرسمية)
- `LICENSE` (AGPLv3 661 سطراً) + `NOTICE` (إسناد Whisper Systems — جيد قانونياً)
- `image-search/`: 3 صور أيقونات (ذهب/أزرق ملكي)

---

## 13) الخلاصة النهائية — الحكم الفني (2026-08-04)

### ✅ نقاط القوة (مؤكدة)
1. قاعدة Signal-Android كاملة وسليمة (E2EE، Kyber، نسخ احتياطي v2، Compose، libsignal-service) مع إعادة تسمية **متقنة 100%**
2. الخادم أصبح ببنية صحيحة (Spring Boot + application.yml + Flyway + WebSocketConfig + Dockerfile) ومنطق MessageService حقيقي
3. **media-sfu أصبح SFU حقيقياً** (mediasoup كامل) — كان فارغاً
4. اللوحة أصبحت كاملة البنية (entry + tabs + Dockerfile) — كانت بلا نقطة دخول
5. DinstarHardwareService: HTTP REST حقيقي للجهاز
6. إصلاح كل أخطاء البنية الجذرية القديمة (settings, NPE, plugins, placeholders)
7. أداة الجودة كاملة من Signal (fast-lint, lintchecks, wire-handler, reproducible-builds, verification-metadata)

### ❌ لماذا ما زال لا يُبنى/لا يعمل (مؤكد سكونياً)
**أخطاء بناء حتمية:**
1. `app/dependencies.gradle.kts` يستخدم `libs.androidx.room.runtime` و`libs.androidx.room.ktx` — غير موجودتين في الكتالوج ⇒ فشل ترجمة سكربت :app
2. :app بلا compose compiler plugin (org.jetbrains.kotlin.plugin.compose) + composeOptions قديمة مع Kotlin 2.2.20
3. :app بلا shared-proto ⇒ `RedProtos`/`ChatProtos` غير مولّدة (MasterDeliveryEngine, RedDeliveryEngine, SyncEngine)
4. ~12 خطأ ترجمة في ملفات com/red/sovereign (قائمة 2.4)
5. backend: ~10 أخطاء ترجمة (قائمة 4.2) — أبرزها RedApprovalService الناقصة، توقيع processIncoming، MessageAck، CoreService، PstnManager @Value، CallProtos، com.red.core.models
6. لوحة: `signalFilled` خطأ إملائي يوقف بناء CRA
7. `include(":build-logic:tools")` + غياب `includeBuild("build-logic")` + استدعاءات `gradle.includedBuild("build-logic")` ⇒ مهمات qa/format/buildQa مكسورة
8. admin_dashboard Dockerfile لا يبني bundle (serve -s public لصفحة فارغة)
9. pstn-asterisk: `dumin-trunk` غير معرّف ⇒ اتصال PSTN وهمي

**أخطاء وظيفية:**
- RedSovereignApp لا يهيّئ ApplicationContext ⇒ نواة Signal معطلة حتى لو تُرجم
- لا مصادقة (REST + WebSocket)؛ kill-switch println؛ DinstarMasterClient عشوائي؛ لا إعادة تسليم للغير متصلين؛ لا معالجة DeleteRED؛ handleSync فارغ؛ Redis keys()؛ SearchService على مشفّر
- اللوحة والخادم endpoints غير متطابقة في عدة شاشات

**أمنياً:** أسرار ثابتة في كل الطبقات، allowBackup=true، SharedPreferences مكشوفة، nginx بلا TLS، bucket عام، JWT وهمي.

### 🎯 خارطة الطريق العملية (مرتبة)
1. **المرحلة 0 — البناء:** إصلاح dependencies.gradle.kts (إضافة Room للكتالوج أو استخدام إصدارات صريحة)؛ تطبيق compose plugin على :app؛ ربط shared-proto مع :app وbackend؛ إصلاح أخطاء com/red/sovereign الـ12 (إنشاء MasterDeliveryEngine.dispatchMessage، إضافة getLastSequenceNumber لـ RedDao، تصحيح addMigrations، إنشاء RedChatTopBar/RedMessageInput أو حذف الشاشات، تصحيح import DialPadScreen، إضافة YemeniOperatorDetector أو حذف مرجعه، إنشاء MinioUploader أو حذف RedMediaTransporter، إضافة ic_launcher_red، تصحيح GroupIDManager import، إصلاح PstnViewModel.makePstnCall)؛ إصلاح backend العشرة؛ تصحيح signalFilled؛ توحيد build-logic (includeBuild أو إزالة)
2. **المرحلة 1 — قرار معماري:** (أ) RED فوق Signal: RedSovereignApp يمتد ApplicationContext + حذف أو عزل شاشات RED المنافسة، أو (ب) تطبيق مستقل فوق libsignal-service
3. **المرحلة 2 — الوظائف:** تطابق اللوحة مع الخادم، إعادة تسليم الرسائل، معالجة DeleteRED/Sync، Dinstar حقيقي موحّد (0..7)، إصلاح dumin-trunk
4. **المرحلة 3 — الأمن:** JWT موقّع + HandshakeInterceptor + أسرار .env + allowBackup=false + TLS + EncryptedSharedPreferences
5. **المرحلة 4 — الجودة:** اختبارات لطبقة RED (صفر اختبار حالياً)، تنظيف android/ وapp-android/ وserver/ وadmin-dashboard/ وdemo/ وZIPs القديمة، تحديث FINAL_SUMMARY والتقارير

> **الحكم النهائي:** المشروع الآن **أفضل بكثير من حالة 2026-08-03** (أُصلح ~60% من أخطاء البنية والملفات الناقصة)، لكنه **ما زال غير قابل للبناء ككل** بسبب أخطاء ترجمة مؤكدة في الوحدات الثلاث (app، backend، لوحة) و**غير قابل للتشغيل كمنظومة** بسبب فصل RedSovereignApp عن نواة Signal وعدم تطابق البروتوكولات بين التطبيق والخادم. أقوى مكوّنات يمكن البناء عليها: قاعدة Signal السليمة، DinstarHardwareService الحقيقي، media-sfu الكامل، MessageService المنطقي، وواجهة AQYAL الفاخرة في android/ (يمكن نقلها إلى :app بعد إصلاح مراجعها).
