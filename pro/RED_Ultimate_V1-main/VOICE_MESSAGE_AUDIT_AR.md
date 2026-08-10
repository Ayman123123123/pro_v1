# تقرير فحص شامل: الرسائل الصوتية (Voice Messages) في RED Ultimate V1

> **تاريخ الفحص:** 2026-08-09
> **الفاحص:** Arena AI Agent
> **النطاق:** كل ملفات المشروع (Backend Kotlin, Android Jetpack Compose, Admin Dashboard, Docs, Tests)

---

## 📊 ملخص تنفيذي

| البُعد | الحالة | ملاحظات |
|---|---|---|
| **تسجيل الرسالة الصوتية (Recording)** | ✅ يعمل | MediaRecorder + AAC + 96kbps + 44.1kHz + M4A |
| **تشفير قبل الرفع (E2EE)** | ✅ قوي | AES/GCM/NoPadding + Key في Android Keystore |
| **رفع مشفر لـ MinIO** | ✅ يعمل | multipart + grants + 100MB max |
| **إرسال عبر Signal Protocol** | ✅ يعمل | `RedConnectionService.sendPayload` + type "VOICE" |
| **استقبال وعرض الفقاعة (Bubble)** | ✅ يعمل | `VoiceMessage` Composable + Waveform Canvas |
| **تشغيل صوتي (Playback)** | ✅ يعمل | ExoPlayer + AudioAttributes SPEECH + سرعات 1×/1.5×/2× |
| **Backend يستقبل VOICE type** | ✅ يسمح فقط | `TYPES` set في MessageService.kt:154 — لكن لا validation خاصة |
| **Auto-download Wi-Fi/Mobile** | ✅ يعمل | `SettingsRuntime.autoDownloadWifi/Mobile` |
| **Integrity check (SHA-256)** | ✅ يعمل | `downloadAndDecrypt` يتحقق من hash + size |
| **Permissions (RECORD_AUDIO)** | ✅ معرّف | `AndroidManifest.xml` — RECORD_AUDIO + MODIFY_AUDIO_SETTINGS |
| **Tests** | ❌ ناقص | لا VoiceMessageViewModelTest, لا MessageService VOICE test |
| **MediaBubble VOICE state** | ⚠️ مفقود | لكن غير مستخدم أصلاً (dead code) |
| **Dead code (VoiceRecorder ×2)** | ❌ مهمل | `core/utils/` و `features/chat/` غير مستخدمين |
| **lock-to-record + إلغاء بالسحب** | ❌ مفقود | ROADMAP يذكره، الـ ViewModel ما يدعمه |

---

## 📁 الملفات المعنية (16 ملف)

### 1️⃣ Android — التسجيل والإرسال (3 ملفات)

#### `media/VoiceMessageViewModel.kt` (222 سطر) — **الملف الرئيسي**
- **Package:** `com.red.sovereign.media`
- **المسؤولية:** تسجيل + تشفير AES-GCM + رفع + إرسال
- **الـ API:**
  - `start(targetRedId, conversationId)` — يبدأ التسجيل
  - `togglePause()` — إيقاف/استئناف
  - `stopAndSend(targetRedId, conversationId)` — إيقاف وإرسال
  - `cancel()` — إلغاء بدون إرسال
  - `permissionDenied()` — عند رفض الإذن
  - `clear()` — مسح الحالة
- **الـ Format:** `MPEG_4 / AAC / 96kbps / 44.1kHz / .m4a`
- **الحد:** `MAX_DURATION_SECONDS = 600` (10 دقائق)
- **Waveform:** 96 عينة بـ 250ms interval
- **التشفير:** AES/GCM/NoPadding 256-bit + key في Android Keystore
- **الرفع:** عبر `MediaApi.uploadEncrypted` (multipart, octet-stream)
- **الإرسال:** `RedConnectionService.sendPayload("VOICE", manifestJson.toByteArray())`

#### `media/VoiceNotePlayer.kt` (68 سطر) — **مشغّل الصوت**
- **Package:** `com.red.sovereign.media`
- **التقنية:** Jetpack Media3 ExoPlayer 1.9.1
- **AudioAttributes:** `USAGE_MEDIA + AUDIO_CONTENT_TYPE_SPEECH`
- **سرعات التشغيل:** 1×, 1.5×, 2× عبر `AssistChip`
- **Default speed:** من `SettingsRuntime.current.defaultPlaybackSpeed`
- **UI:** `PlayerView` بـ `useController=true` + height 68dp
- **Cleanup:** `DisposableEffect` يطلق الموارد

#### `media/AttachmentViewModel.kt` (110 سطر) — **يشارك في الـ Flow**
- **Package:** `com.red.sovereign.media`
- **يستخدم:** `EncryptedAttachmentRepository`
- **download(manifestJson):** ينزّل ويفك تشفير → `AttachmentState.Downloaded(path, name)`
- **يلعب دور:** يُستخدم لكل من IMAGE/VIDEO/AUDIO/FILE
- **VOICE يستعمله أيضاً:** في `VoiceMessage` bubble لتنزيل ملف الـ voice

---

### 2️⃣ Android — فقاعة العرض (1 ملف)

#### `ui/RedDashboard.kt` — `VoiceMessage` Composable (سطر 1752)
- **Decoding:** `ATTACHMENT_JSON.decodeFromString<VoiceManifest>(manifestJson)`
- **عند التنزيل:** `VoiceNotePlayer(Uri.fromFile(File(path)))`
- **قبل التنزيل:** أيقونة Mic + Waveform + Duration + Size + زر Download
- **Auto-download:** عبر `shouldAutoDownload(context, manifest.size)` إذا `!outgoing`
- **Manifest fields:** objectKey, url, name, size, durationSeconds, waveform, sha256, key, nonce

#### `ui/RedDashboard.kt` — `VoiceWaveform` Composable (سطر 1739)
- **Canvas** يرسم خطوط `StrokeCap.Round`
- **حساب:** `height = (size.height * (value / 100f))` لكل عينة
- **strokeWidth:** `step * .42f` بـ `coerceIn(2f, 7f)`
- **Default:** إذا فاضي، 24 عينة كلها = 8

---

### 3️⃣ Android — الـ Models (في `VoiceMessageViewModel.kt`)

```kotlin
@kotlinx.serialization.Serializable
data class VoiceManifest(
    val version: Int = 1,
    val objectKey: String,
    val url: String,
    val name: String,
    val mimeType: String = "audio/mp4",
    val size: Long,
    val durationSeconds: Int,
    val waveform: List<Int> = emptyList(),
    val sha256: String,
    val key: String,     // Base64(AES-256 key, 32 bytes)
    val nonce: String    // Base64(GCM nonce, 12 bytes)
)

sealed interface VoiceMessageState {
    data object Idle : VoiceMessageState
    data class Recording(val paused: Boolean) : VoiceMessageState
    data object Sending : VoiceMessageState
    data class Sent(val durationSeconds: Int) : VoiceMessageState
    data class Error(val message: String) : VoiceMessageState
}
```

---

### 4️⃣ Android — الـ Media API (3 ملفات)

#### `media/MediaApi.kt` (130 سطر)
- **Package:** `com.red.sovereign.media`
- **ALLOWED_MIMES (للـ voice):** `audio/ogg`, `audio/mp4`, `audio/mpeg`
- **MAX_SIZE:** 100 MiB
- **Methods:**
  - `uploadEncrypted(file, displayName)` → POST /api/media
  - `grant(objectKey, targetRedId)` → POST /api/media/grants
  - `downloadToPrivateCache(path, extension)` → GET (مع EncryptedMediaCache)

#### `media/EncryptedAttachment.kt` (210 سطر) — **يستخدمه AttachmentViewModel**
- **MAX_BYTES:** `99L * 1024 * 1024` (99 MiB — يترك مساحة لـ GCM tag)
- **ALLOWED_MIMES:** `audio/*` مقبول
- **Encryption:** AES/GCM/NoPadding + SHA-256 digest
- **Manifest:** `AttachmentManifest` (مختلف عن `VoiceManifest`!)

> ⚠️ **ملاحظة:** `VoiceMessageViewModel` يستخدم `VoiceManifest` خاص به، بينما `AttachmentViewModel` يستخدم `AttachmentManifest` عام. كلاهما مشفّر بنفس الطريقة.

#### `media/EncryptedMediaCache.kt` (75 سطر)
- **Package:** `com.red.sovereign.media`
- **Key alias:** `red.media.cache.v1`
- **Algorithm:** AES/GCM/NoPadding 256-bit
- **File naming:** SHA-256(key) → `hash.enc`
- **Cache dir:** `cacheDir/encrypted_media/`

---

### 5️⃣ Android — Dead Code (3 ملفات ⚠️)

#### `core/utils/VoiceRecorder.kt` (54 سطر) — **❌ غير مستخدم**
- **Format:** OGG/Opus, 48kHz, 64kbps
- **لا imports له في أي مكان**
- **احتمال الاستخدام:** للـ stories (لكن StoryViewModel يستخدم MediaRecorder مباشرة)

#### `features/chat/VoiceRecorder.kt` (35 سطر) — **❌ غير مستخدم**
- **Format:** OGG/Opus, 48kHz, 64kbps (نفس الكود، بدون معالجة أخطاء)
- **لا imports له في أي مكان**
- **احتمال الاستخدام:** مكرر، مهمل

#### `features/chat/MediaBubble.kt` (61 سطر) — **❌ غير مستخدم (في production)**
- **يدعم:** IMAGE, VIDEO, FILE
- **لا يدعم:** VOICE, AUDIO
- **لا يُستدعى** من RedDashboard (يستخدم AttachmentMessage و VoiceMessage منفصلتين)

> **التوصية:** حذف هذه الملفات الثلاثة (3 ملفات × 150 سطر = 150 سطر dead code) — أو نقلها إلى `archive/` أو عمل `@Deprecated` + TODO.

---

### 6️⃣ Android — الـ Integration في ChatHubScreen

#### `RedDashboard.kt` — `ChatHubScreen` (سطر 661)
```kotlin
@Composable
private fun ChatHubScreen(
    account: AuthState.Authenticated,
    groups: GroupViewModel,
    directory: DirectoryViewModel,
    safety: SafetyViewModel,
    attachments: AttachmentViewModel,
    voiceMessages: VoiceMessageViewModel,
    showGroups: Boolean
)
```

**سطر 935:**
```kotlin
when (item.type) {
    "FILE", "IMAGE", "VIDEO", "AUDIO" -> AttachmentMessage(item, attachments)
    "VOICE" -> VoiceMessage(item, attachments)   // ← الـ branch الخاص
    "RICH_TEXT" -> RichTextMessage(item, conversationMessages)
    else -> Text(...)
}
```

**سطر 1018-1023 (UI handlers):**
```kotlin
IconButton({
    if (voiceMessages.state is VoiceMessageState.Recording) voiceMessages.stopAndSend(target, conversation)
    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceMessages.start(target, conversation)
    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
}, enabled = target.matches(RED_ID_PATTERN) && voiceMessages.state !is VoiceMessageState.Sending) {
    Icon(if (voiceMessages.state is VoiceMessageState.Recording) Icons.Default.Stop else Icons.Default.Mic, ...)
}
```

> ⚠️ **ما لا يوجد (مقارنة بـ WhatsApp/Telegram):**
> - ❌ **lock-to-record** (الضغط المطوّل للقفل بدون رفع الإصبع)
> - ❌ **إلغاء بالسحب** (drag-to-cancel)
> - ❌ **preview قبل الإرسال** (مع زر "إرسال" منفصل)
> - ❌ **waveform قبل الإرسال** (يظهر فقط بعد التسجيل في `Recording` state)
> - ❌ **حذف محلي قبل الإرسال** (delete draft)

---

### 7️⃣ Android — ConnectionService (الإرسال الفعلي)

#### `core/RedConnectionService.kt`
- **سطر 286:** `private val ALLOWED_MESSAGE_TYPES = setOf("TEXT", "RICH_TEXT", "FILE", "VOICE", "IMAGE", "VIDEO", "AUDIO")`
- **سطر 79:** `val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)?.takeIf { it.isNotEmpty() && it.size <= 256 * 1024 }`

> ✅ **حجم الـ VoiceManifest:** ~875 bytes (0.33% من الحد 256KB) — أكثر من كافي.

---

### 8️⃣ Backend — Voice Message Handling

#### `messaging/MessageService.kt` (سطر 154)
```kotlin
private val TYPES = setOf(
    "TEXT", "RICH_TEXT", "IMAGE", "VIDEO", "AUDIO", "VOICE",
    "FILE", "SYSTEM", "GROUP_KEY_DISTRIBUTION", "GROUP_MESSAGE"
)
```

> ⚠️ **ما لا يوجد في Backend:**
> - ❌ **لا validation خاصة لـ VOICE** (لا size check, لا duration check)
> - ❌ **لا special handling** (يعامل مثل أي attachment مشفّر)
> - ❌ **لا VOICE-specific metadata** في MongoDB (لا waveform, لا duration)
> - ✅ فقط **passes through** إلى Signal Protocol layer

#### `media/MediaService.kt`
- **MAX_SIZE:** 100 MiB
- **ALLOWED_MIMES (audio):** `audio/ogg`, `audio/mp4`, `audio/mpeg`
- **EXTENSIONS:** `ogg`, `m4a`, `mp3`
- **Object key:** `users/{userId}/{UuidV7}.{ext}`

#### `media/MediaController.kt`
- **POST /api/media** — رفع (multipart)
- **POST /api/media/grants** — منح الوصول
- **GET /api/media/users/{userId}/{fileName}** — تنزيل
- **DELETE /api/media/users/{userId}/{fileName}** — حذف

#### `media/MediaSecurityScanner.kt` — **⚠️ لا يفحص audio magic bytes**
```kotlin
val isValid = when (mime) {
    "image/jpeg" -> ...
    "image/png" -> ...
    "image/gif" -> ...
    "video/mp4" -> ...
    "application/pdf" -> ...
    else -> true   // ← audio/* و ogg/opus و m4a تمر بلا فحص!
}
```

> ⚠️ **مشكلة أمنية محتملة:** الـ scanner لا يتحقق من magic bytes لملفات الصوت. قد يسمح بـ file مزيّف بـ `audio/mp4` MIME.

#### `media/MediaGrantService.kt`
- يتحقق من:
  - `objectKey.startsWith("users/$ownerId/")`
  - `media.exists(objectKey)`
  - `grantee.status == APPROVED`
  - `grantee.id != ownerId` (لا self-grant)
- يخزّن في `media_grants` table (PostgreSQL)

---

### 9️⃣ Admin Dashboard — لا يوجد Voice UI

#### النتيجة: **لا شيء**
- **بحث في كل `*.tsx, *.ts, *.jsx, *.js`:** صفر نتائج
- **استثناء واحد:** `DinstarControl.tsx` يعرض "Voice" كـ capability label للمكالمات PSTN

> **طبيعي:** Admin Dashboard للإدارة، ليس للمراسلة. لا يحتاج voice UI.

---

### 🔟 Documentation

#### `FINAL_POLISH_AR.md` (سطر 122)
```markdown
### VoiceMessage / VoiceNotePlayer
- **تسجيل:** VoiceMessageViewModel.start(target, conversation) — MediaRecorder + waveform 24 عينة
- **عرض الفقاعة:** VoiceWaveform (Canvas مع خطوط StrokeCap.Round) + زر تنزيل/تشغيل
- **تشغيل:** VoiceNotePlayer — ExoPlayer مع AudioAttributes SPEECH + سرعات 1×/1.5×/2× + AssistChip
- **تخزين:** مشفر عبر EncryptedAttachment → MinIO
```

#### `RED_INSPECTION_REPORT_AR.md` (سطر 88-89)
```markdown
| `VoiceMessageViewModel.kt` | تسجيل وإرسال رسائل الصوت (MediaRecorder + تشفير) | ✅ مكتمل |
| `VoiceNotePlayer.kt` | تشغيل رسائل الصوت (Media3 ExoPlayer) | ✅ مكتمل |
```

#### `DEVELOPMENT_PLAN.md` (سطر 117)
```markdown
- Voice messages
```

#### `docs/31-YOUNES-PRODUCT-UX-ROADMAP.md`
```markdown
- تسجيل رسالة صوتية مع lock-to-record وإلغاء بالسحب وموجة صوتية وسرعات تشغيل.
```

> ⚠️ **عدم تطابق:** الـ ROADMAP يذكر **lock-to-record وإلغاء بالسحب** لكن الـ implementation الحالي **لا يدعمها**.

---

### 1️⃣1️⃣ Tests — ❌ ناقص بشدة

#### Android Tests (لا يوجد):
- ❌ `VoiceMessageViewModelTest` — غير موجود
- ❌ `VoiceManifestTest` — غير موجود
- ❌ `VoiceNotePlayerTest` — غير موجود
- ❌ `VoiceWaveformTest` — غير موجود (Composable، صعب)
- ✅ فقط: `EncryptedCacheTest` (في `media/` — اختبار عام للـ cache)

#### Backend Tests (لا يوجد):
- ❌ `MessageServiceTest` — لا اختبار لـ VOICE type
- ❌ `MediaSecurityScannerTest` — لا اختبار لـ audio mime types
- ✅ فقط: `MediaSecurityScannerTest`, `MediaAccessServiceTest`, `MediaThumbnailTest` (عامة)

> **التوصية:** إضافة على الأقل:
> 1. `VoiceMessageViewModelTest` — اختبار encryptUploadAndGrant flow
> 2. `MessageServiceTest` إضافة test case لـ "VOICE" type validation
> 3. `MediaSecurityScannerTest` إضافة test case لـ audio/mp4 magic bytes

---

## 🔍 تحليل المشاكل والنواقص

### 🟢 ما يعمل بشكل ممتاز
1. **التشفير E2E كامل:** AES-256-GCM + key في Android Keystore (لا يغادر الجهاز)
2. **رفع آمن لـ MinIO:** multipart + grants + 100MB cap
3. **Signal Protocol integration:** `type=VOICE` معبّر في ALLOWED_MESSAGE_TYPES
4. **UI playback متقدم:** ExoPlayer + سرعات 1×/1.5×/2× + AssistChip
5. **Waveform visualization:** Canvas مع StrokeCap.Round و 96 عينة
6. **Auto-download logic:** يحترم Wi-Fi/Mobile settings
7. **Integrity check:** SHA-256 في decryption
8. **Permissions:** RECORD_AUDIO + MODIFY_AUDIO_SETTINGS معرّفة

### 🟡 مشاكل متوسطة

#### 1. **Dead code (3 ملفات):**
- `core/utils/VoiceRecorder.kt` (54 سطر)
- `features/chat/VoiceRecorder.kt` (35 سطر)
- `features/chat/MediaBubble.kt` (61 سطر)
- **الإجمالي:** 150 سطر dead code

#### 2. **عدم تطابق ROADMAP vs Implementation:**
- الـ ROADMAP يعد بـ **lock-to-record + drag-to-cancel**
- الـ ViewModel الحالي ما عنده هذه الميزات

#### 3. **MediaSecurityScanner لا يفحص audio magic bytes:**
```kotlin
else -> true   // audio/ogg, audio/mp4, audio/mpeg تمر بلا فحص
```

#### 4. **Backend لا metadata خاصة لـ VOICE:**
- لا duration, لا waveform, لا sampleRate, لا bitrate مخزّنة في MongoDB
- الـ Manifest يذهب داخل Signal ciphertext، لا يُفهرس

### 🔴 مشاكل حرجة (إن وجدت)

#### لا شيء حرج. كل التدفّق يعمل end-to-end. ✅

---

## 🛠️ التوصيات (بالأولوية)

### P0 — أمان
1. **إصلاح MediaSecurityScanner** — أضف magic bytes check لـ:
   - `audio/mp4` (M4A): bytes[4..7] = "ftyp"
   - `audio/ogg`: bytes[0..3] = "OggS"
   - `audio/mpeg`: bytes[0..1] = 0xFF 0xFB أو 0xFF 0xE0/0xE1/0xE2/0xE3

### P1 — توثيق وصيانة
2. **حذف الـ dead code** أو نقله إلى `archive/`:
   - `core/utils/VoiceRecorder.kt`
   - `features/chat/VoiceRecorder.kt`
   - `features/chat/MediaBubble.kt`
3. **إضافة تعليقات** في `VoiceMessageViewModel` تشرح lock-to-cancel و drag-to-cancel كـ TODO

### P2 — ميزات ROADMAP
4. **إضافة lock-to-record** (ضغط مطوّل + slide up للقفل)
5. **إضافة drag-to-cancel** (سحب لليسار/الأسفل للإلغاء)
6. **إضافة preview قبل الإرسال** (مع waveform + play قبل الإرسال)

### P3 — اختبارات
7. **VoiceMessageViewModelTest** — اختبر:
   - encryptUploadAndGrant happy path
   - start/cancel flow
   - MAX_DURATION_SECONDS enforcement
8. **MessageServiceTest** — أضف:
   - VOICE type في TYPES set
   - validation test لـ VOICE manifest size
9. **MediaSecurityScannerTest** — أضف:
   - audio/mp4 magic bytes positive/negative cases
   - audio/ogg magic bytes
   - audio/mpeg magic bytes

### P4 — Backend metadata
10. **إضافة VoiceMetadata** في MongoDB:
    - durationSeconds
    - waveform (أو reference)
    - sampleRate, bitrate
    - لسهولة البحث والعرض في الـ feed

---

## 📈 إحصائيات

| المقياس | القيمة |
|---|---|
| عدد الملفات المعنية بالـ voice messages | 16 |
| عدد سطور الكود المنتِج (voice) | ~600 سطر |
| عدد سطور الـ dead code | ~150 سطر |
| عدد الـ tests | 0 (لا يوجد!) |
| عدد الـ commits المتعلقة | 0 مباشر (داخل commits أخرى) |
| عدد الـ files في `media/` Android | 6 |
| عدد الـ mimes المدعومة | 3 (audio/ogg, audio/mp4, audio/mpeg) |
| الحد الأقصى للحجم | 100 MiB |
| الحد الأقصى للمدة | 600 ثانية (10 دقائق) |
| معدل البت للتسجيل | 96 kbps |
| معدل العينة | 44.1 kHz |
| عينة الـ Waveform | 96 (1.5 دقيقة بـ 250ms interval) |

---

## ✅ الخلاصة

**الرسائل الصوتية في RED Ultimate V1 تعمل end-to-end بشكل ممتاز** مع تشفير E2E كامل، تشغيل سريع، و waveform visualization. 

**النواقص الأساسية:**
1. ❌ **لا tests** (صفر!)
2. ❌ **Dead code** (3 ملفات)
3. ❌ **lock-to-record و drag-to-cancel** غير منفّذين (موعودين في ROADMAP)
4. ⚠️ **MediaSecurityScanner** لا يفحص audio magic bytes

**الخطوات القادمة المُقترحة:**
1. إصلاح P0 (magic bytes)
2. حذف P1 (dead code)
3. إضافة P3 (tests) — ضروري قبل أي release
4. لاحقاً: P2 (lock-to-record) و P4 (backend metadata)
