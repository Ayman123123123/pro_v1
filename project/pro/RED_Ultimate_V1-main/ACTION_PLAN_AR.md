# 🚨 خطة العمل العاجلة — مشروع RED Ultimate
### مقترحات + أولويات يجب تنفيذها بأسرع وقت (2026-08-04)

> **المبدأ:** لا فائدة من أي ميزة جديدة قبل أن **يُبنى المشروع ويُشغَّل**. الترتيب أدناه هو "مسار النجاة": إصلاح البناء → تشغيل الخادم واللوحة → توصيل التطبيق بالخادم → الأمن → الميزات الحقيقية.

---

## 🟥 المرحلة 0 — إيقاف النزيف (أول 24 ساعة) — "اجعلها تُبنى"

### 0.1 إصلاح `app/dependencies.gradle.kts` + الكتالوج (خطأ قاتل)
**المشكلة:** يستخدم مفاتيح غير موجودة: `libs.androidx.room.runtime` و`libs.androidx.room.ktx` — لا يوجد أي إدخال Room في `gradle/libs.versions.toml` ⇒ فشل ترجمة سكربت :app فوراً.
**الحل:**
- إضافة إلى `[versions]`: `androidx-room = "2.6.1"` (أو 2.7.x)
- إضافة إلى `[libraries]`:
  ```toml
  androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
  androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "androidx-room" }
  androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }
  ```
- في `dependencies.gradle.kts`: إضافة `kapt(libs.androidx.room.compiler)` (ضروري لتوليد كود Room)
- إضافة `implementation(libs.accompanist.permissions)` (مستخدم في PermissionRequestScreen وRedPermissionGate)
- إضافة `implementation(platform(libs.androidx.compose.bom))` + `implementation(libs.androidx.compose.material3)` + `implementation(libs.androidx.compose.material.icons.extended)` (الواجهات تستخدمها)
- إضافة `implementation(project(":shared-proto"))` (لتوليد `RedProtos`)

### 0.2 إصلاح Compose في :app (خطأ قاتل)
**المشكلة:** لا يوجد `id("org.jetbrains.kotlin.plugin.compose")` في `app/build.gradle.kts` ⇒ كل كود Compose (MainActivity + الشاشات) يُترجم كدوال عادية وتنهار وقت التشغيل. و`composeOptions.kotlinCompilerExtensionVersion = "1.5.15"` متهالك مع Kotlin 2.2.20.
**الحل:**
- إضافة `alias(libs.plugins.compose.compiler)` إلى plugins في `app/build.gradle.kts`
- حذف كتلة `composeOptions { }` كاملة

### 0.3 إصلاح أخطاء ترجمة ملفات `com/red/sovereign` (12 خطأ مؤكد)
| الملف | الإصلاح |
|---|---|
| `features/chat/ChatViewModel.kt` | أضف `dispatchMessage(conversationId, text)` إلى `MasterDeliveryEngine` (أو اجعله يستخدم `RedDeliveryEngine` الموجود) |
| `core/delivery/MasterDeliveryEngine.kt` | أضف `suspend fun dispatchMessage(...)` + `fun processIncomingRED(payload: ByteArray)` |
| `core/delivery/RedDeliveryEngine.kt` | غيّر `import com.red.sovereign.proto.ChatProtos` ← `com.red.sovereign.proto.RedProtos` + `RedProtos.ChatMessage` |
| `core/delivery/SyncEngine.kt` | أضف `@Query("SELECT MAX(sequenceNumber) FROM messages WHERE conversationId = :cId") suspend fun getLastSequenceNumber(cId: String): Long?` إلى `RedDao` |
| `core/di/RedMasterModule.kt` | احذف `.addMigrations()` (أو مرّر `MasterDatabase.MIGRATION_1_2` من نسخة android/) |
| `core/utils/RedMediaTransporter.kt` | أنشئ `MinioUploader` في :app (انسخ من `android/core/network/MinioUploader.kt`) أو احذف الملف إن لم يُستخدم |
| `features/chat/RedChatDetail.kt` | احذفه (مكرر متعارض مع ChatDetailScreen) — أو أعد كتابته باستخدام RedChatBubble الموجودة في android/ |
| `features/chat/RedChatScreen.kt` | أنشئ `RedChatTopBar` + `RedMessageInput` أو استبدل الاستدعاءات بشاشة ChatDetailScreen الموجودة |
| `features/pstn/DialPadScreen.kt` | أنشئ `YemeniOperatorDetector` (كشف مشغّل يمني: 77/73/71/70 → يمن موبايل/سبأفون/يمن) — ملف صغير مفيد فعلاً |
| `features/chat/GroupIDManager.kt` | أضف `import com.red.sovereign.core.network.RedWebSocketClient` |
| `network/RedPushService.kt` | استبدل `R.drawable.ic_launcher_red` بأيقونة موجودة + أنشئ Notification Channel + سجّله في Manifest |
| `features/calls/CallOrchestrator.kt` | غيّر `makePstnCall(target)` ← `dialPstn(target)` |
| `ui/RedMainHost.kt` | صحح import DialPadScreen إلى `com.red.sovereign.features.pstn` |

### 0.4 إصلاح الخادم `backend-server` (10 أخطاء مؤكدة) — **الأسرع ربحاً**
| الملف | الإصلاح |
|---|---|
| `auth/RedApprovalService.kt` | أضف: `getPendingList()`, `processAction(userId, action)`, `rejectUser(userId)` (خريطة ConcurrentHashMap + قائمة pending) |
| `websocket/RedMasterHandler.kt` + `RedWebSocketHandler.kt` + `ChatWebSocketHandler.kt` | عدّل النداء إلى `messageService.processIncoming(msg.senderId, msg.receiverId, msg.conversationId, msg.payload.toByteArray(), msg.type)` (أو أضف overload يقبل ChatMessage) |
| `RedWebSocketHandler` + `ChatWebSocketHandler` | `MessageAck` في `messages.proto` بلا `sequence_number` — إما أضف الحقل للبروتوكول، أو احذف `.setSequenceNumber(seq)` وعدّل `.setStatus("SENT")` إلى `.setStatus(ChatProtos.AckStatus.SENT)` |
| `services/CoreService.kt` | عرّف `GroupEntity`/`StoryEntity` محلياً في الخادم (أو استخدم MongoEntities) |
| `pstn/PstnManager.kt` | أضف `import org.springframework.beans.factory.annotation.Value` |
| `websocket/CallWebSocketHandler.kt` | أضف `CallProtos` إلى `red_protocol.proto` (message CallSignal + enum SignalType) أو احذف الملف واجعل WebRTC signaling عبر TextMessage JSON |
| `developedchat/*` (حزمة com.red.admin/com.red.auth) | احذف المجلد كاملاً (خارج نطاق المسح الضوئي ويسبب فوضى) |
| `AdminMasterController.executeDinstarAction` | وجّهه إلى `DinstarHardwareService` الحقيقي (reboot/sip/dial) بدل "EXECUTED" |

**التحقق:** `cd backend-server && gradle build -x test` (متوفر) ثم تشغيله واختبار `/health` و`/api/master/v1/stats/realtime`.

### 0.5 إصلاح لوحة التحكم (خطآن يمنعان البناء)
- `DuminAdvanced.tsx`: `signalFilled` ← `SignalFilled` (يوقف بناء CRA بالكامل)
- `Dockerfile` اللوحة: أضف `RUN npm run build` وانسخ `build/` إلى nginx (أو serve على `build` بدل `public`)

### 0.6 توحيد build-logic
- `settings.gradle.kts`: استبدل `include(":build-logic:tools")` بـ `includeBuild("build-logic")` (لأن الجذر يستدعي `gradle.includedBuild("build-logic")` في مهمات buildQa/format) — أو احذف السطرين معاً إن لم نحتجهما الآن.

### 0.7 إصلاح أستريكس (خطأ واحد يجعل PSTN وهمياً)
- `pstn-asterisk/extensions.conf` + `backend-server/pstn/PstnManager.kt`: غيّر `dumin-trunk` ← `dinstar-gateway` (المعرّف الفعلي في pjsip.conf).

---

## 🟧 المرحلة 1 — القرار المعماري (يوم واحد) — **لا تتخطَّ هذه الخطوة**

**السؤال المصيري:** هل نُبقي "RED فوق Signal" أم "RED مستقل"؟
- **(أ) RED فوق Signal (موصى به):** `RedSovereignApp` يجب أن يمدّد `ApplicationContext` (نواة Signal: قاعدة البيانات، JobManager، التشفير، الإشعارات) — ثم نضيف شاشات RED كنقطة دخول اختيارية. الأقل تكلفة لأن كل البنية موجودة.
- **(ب) RED مستقل:** نبني تطبيقاً خفيفاً فوق `libsignal-service` فقط ونستورد واجهة AQYAL من `android/` — أكثر تحكماً لكنه يرمي 95% من الكود.
- **قرار سريع يجب اتخاذه اليوم:** أي واجهة سترى للمستخدم؟ (واجهة Signal الكاملة؟ واجهة AQYAL العربية الفاخرة؟ كلتاهما؟)
- **إجراء فوري بلا جدال:** نقل أفضل ملفات `android/` (واجهة AQYAL: RedTheme, RedMainDashboard, RedChatListScreen, RedCallLogScreen, RedExploreScreen, RedSettingsScreen, MasterDatabase مع MIGRATION_1_2, MinioUploader, StoryRepositoryImpl) إلى `:app` — فهي أفضل واجهات المشروع.

---

## 🟨 المرحلة 2 — التشغيل الفعلي (2-3 أيام)

### 2.1 توصيل التطبيق بالخادم (أهم خطوة وظيفية)
- التطبيق يتحدث `RedProtos` عبر WebSocket (`/ws/master`) — الخادم جاهز لذلك بعد إصلاح 0.4
- ربط `RedWebSocketClient` بالعنوان `ws://<server-ip>:8080/ws/master` (قابلة للضبط في buildConfigField)
- تنفيذ `handleSync` في الخادم: جلب الرسائل المفقودة من MongoDB (`getMissedMessages`) وإرسالها
- معالجة `DeleteRED` في `RedMasterHandler` (بث حذف لكل الجلسات)

### 2.2 مطابقة اللوحة مع الخادم (جدول endpoints)
| الشاشة | تستدعي حالياً | الصحيح |
|---|---|---|
| AuthorityTab | `/api/admin/users/approve/:id` | `/api/admin/users/update-status?userId=&status=APPROVED` |
| SecurityTab | `/api/master/v1/security/kill-switch` + `/wipe/:id` | `/api/master/v1/security/wipe?userId=` (وأضف endpoint kill-switch في الخادم) |
| MasterOverview | `ws_active/pending_auth/gsm_signal/db_storage` | `active_users/messages_24h/system_load/db_health/pending_approvals` |
| Dashboard.tsx | `weekly_messages/gsm_active/pending_users/cpu_load` | مفتاح الخادم الفعلي |
| LogStreamerTab | `ws://host:8080` | `/ws/admin/logs` عبر nginx (نفس المنفذ 80) |
| MasterLayout | تبويبات 5 و7 بلا محتوى | أضف محتوى (Media SFU → إحصائيات من الخادم، Infrastructure → /health) |

### 2.3 إعادة تسليم الرسائل للغير متصلين
- `MessageService`: عند وصول رسالة والمستلم غير متصل — تُخزَّن (موجود ✓) وتُرسل عند `afterConnectionEstablished` (اسحب آخر الرسائل من Mongo لكل مستخدم عند دخوله)
- ACK حقيقي من المستقبِل (DELIVERED/READ) بدل "SENT" فقط — البروتوكول يدعم `status`

---

## 🟩 المرحلة 3 — الأمن (3-5 أيام — قبل أي نشر حقيقي)

1. **JWT حقيقي** في `SecurityConfig` + `AuthController` (jjwt موجود في الاعتماديات ✓) مع bcrypt لكلمات المرور وتخزين PostgreSQL (Flyway موجود)
2. **HandshakeInterceptor** لـ WebSocket: تحقق من التوكن وضبط `session.attributes["userId"]` — يغلق ثغرة الانتحال
3. **أسرار من .env فقط**: حذف `password`/`red_secret_123`/`redturnsecret`/`redsecret123` من docker-compose وapplication.yml وmanager.conf (`.env.example` جاهز)
4. **`allowBackup="false"`** في Manifest + `EncryptedSharedPreferences` لـ RedIdentityManager
5. **استبدال `redis.keys()`** بـ `SCAN` أو مفاتيح فهرس منفصلة
6. **TLS** في nginx (شهادة ذاتية على الأقل) — كل شيء الآن HTTP نصي
7. إغلاق `/api/admin/**` خلف دور ADMIN حقيقي

---

## 🟦 المرحلة 4 — ميزات حقيقية بدل المحاكاة (أسبوع+)

| الادعاء | الواقع الحالي | الإصلاح المقترح |
|---|---|---|
| نظام C: توصيل مضمون | منطق موجود لكن معطل بخطأ التوقيع | أصلحه (0.4) ثم فعّل ACK/إعادة تسليم (2.3) |
| نظام A: مكالمات 1080p | SFU كامل + WebRTC جزئي في android/ | انقل `RedVoipMaster`+`VoipEngine`+`WebRtcSignaler` من android/ واربط signaling عبر CallSignal |
| نظام B: Dinstar | DinstarHardwareService حقيقي لكن DinstarMasterClient عشوائي | وحد الاستخدام على DinstarHardwareService + أصلح dumin-trunk |
| الموافقة الإدارية | `checkApprovalStatus() = true` وهمية | اربطها بـ `/api/auth/register` → PENDING → موافقة اللوحة → APPROVED → التطبيق يفتح |
| التشفير الكمومي | يعيد البيانات كما هي | استخدم Kyber من libsignal (موجود فعلاً) أو احذف الادعاء |
| قصص 24 ساعة | ViewModel + Room + تنظيف Mongo موجود | اربط الرفع عبر `/api/media/upload` إلى MinIO |

---

## 🟪 المرحلة 5 — تنظيف ووثائق (ساعة واحدة — يمكن فعلها الآن)

- ❌ **حذف/عزل**: `android/` و`app-android/` و`server/` و`admin-dashboard/` و`demo/` و`temp-dc.yml` و`pjsip_dinstar.conf` (بعد نقل ما يلزم) — 3 نسخ متداخلة = كارثة إصدارات
- ❌ **حذف `imports_list.txt` و`used_imports.txt`** (نواتج فحص مشوهة بلا قيمة) — أو أعد توليدها نظيفة
- ✅ **تحديث `FINAL_SUMMARY.md`** (أرقامه غير دقيقة الآن) و`MASTER_CHECKLIST.txt` (يدّعي "NO FEATURES MISSING" وهذا غير صحيح)
- ✅ إصلاح `build-and-run.sh` (استخدم `docker compose` بدل `docker-compose`)
- ✅ `docker-compose.yml`: غيّر healthcheck backend إلى `/health` (المسار الفعلي)
- ✅ إضافة `Dockerfile` جذر (لأن workflow `docker-image.yml` سيفشل بدونه) أو حذف الـ workflow
- ✅ إصلاح `media-sfu` `consume`: دعم استهلاك منتجات عدة (أكثر من متحدث) + `announcedIp` قابل للضبط

---

## ⚡ الملخص السريع — "ماذا نعمل هذا الأسبوع؟"

| اليوم | المهمة | الناتج |
|---|---|---|
| **اليوم 1** | 0.1 + 0.2 + 0.3 + 0.4 (إصلاح البناء app + backend) | `gradle build` ينجح + الخادم يعمل و`/health` = UP |
| **اليوم 2** | 0.5 + 0.6 + 0.7 + المرحلة 1 (قرار معماري + نقل AQYAL) | اللوحة تعمل وتتصل بالخادم + APK يُبنى |
| **اليوم 3** | المرحلة 2 (توصيل WebSocket + مطابقة endpoints + sync) | رسالة من هاتف إلى هاتف عبر خادمك المحلي 🎉 |
| **اليوم 4-5** | المرحلة 3 (أمن أساسي) | لا انتحال ولا أسرار مكشوفة |
| **الأسبوع 2+** | المرحلة 4 (VoIP + PSTN + قصص + Kyber) | الأنظمة A/B/C حقيقية |

---

## 🎯 أهم 3 أشياء "لازم نعملها باسرع وقت" (لو لا وقت إلا لها)

1. **إصلاح backend-server والتحقق منه** (0.4) — الخادم هو قلب المنظومة، إصلاحه سريع (ساعات)، ونستطيع اختباره فوراً بدون Android SDK: `gradle build` → تشغيل → `curl /health` → اختبار WebSocket بإرسال رسالة
2. **إصلاح بناء تطبيق أندرويد** (0.1 + 0.2 + 0.3) — Room + compose plugin + الأخطاء الـ12 — لأن بدون APK لا يوجد منتج
3. **القرار المعماري** (المرحلة 1) — تحديد الواجهة الواحدة (Signal أم AQYAL) قبل أي تطوير إضافي، حتى لا نبني فوق نظامين متوازيين مرة أخرى
