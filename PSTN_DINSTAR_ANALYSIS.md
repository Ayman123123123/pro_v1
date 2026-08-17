# تقرير تحليلي شامل — جزء PSTN / DINSTAR والواجهات
**المستودع:** `C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate`
**نطاق التحليل:** 14 ملفاً (10 في `red-app` + 3 في مشاريع معزولة + 1 لوحة ويب) + ملف الديف الكامل `calls_diff_full.txt` (5138 سطراً / 37 ملفاً) + الملفات الجديدة `calls_new_files.txt` (4316 سطراً)
**الأساس:** قراءة كاملة سطراً-بسطر للملفات التالية، والتحقق منها مقابل الـ backend الحالي (mappings/SecurityConfig/WebSocketConfig/settings.gradle).

---

## 1) وظيفة كل ملف من الـ 14 ملفاً

### 1.1 `red-app\src\main\java\com\red\sovereign\auth\PstnApi.kt` (63 سطراً)
عميل API الرسمي للمكالمات اليمنية عبر DINSTAR:
- **DTOs** (أسطر 7-30): `PstnCallRequest(number, slotIndex: Int? = null)`، `PstnCallResponse(callId, status, number, usedToday, dailyLimit, slot = -1)`، `BridgeResponse(sipServer, sipUsername, sipPassword, sipTransport, targetNumber, iceServers: BridgeIceConfig, expiresAt, usedToday, dailyLimit, turnServerUrl?/turnUsername?/turnPassword?)`، `BridgeIceConfig(expiresAt, iceServers)`، `BridgeIceServerDto(urls, username?, credential?)`.
- **العمليات** (41-62): `dial(number, slotIndex)`، `hangup(callId, port = -1)`، `bridge(number)` — كلها عبر `AuthorizedApiClient(tokens)` مع `Json { ignoreUnknownKeys = true }`.

### 1.2 `red-app\...\features\dinstar\DinstarModels.kt` (225 سطراً)
نموذج بيانات DINSTAR الكامل:
- `DinstarPort` (9-62): index/radioType/registrationState/callState/**signalPercent: Int? = null**/signalDbm/signalRaw/signalUsable/gprsState/operatorName/أرقام مقنّعة/simType؛ **`isAvailable = REGISTERED && IDLE && signalUsable`** (41)، `isRegisteredButUnusable` (45)، `statusDescriptionAr`، `signalLabelAr` (لا يختلق أرقاماً).
- `YemenOperator` (82-127): البادئات الصحيحة 71=سبأفون، 73=يو (MTN سابقاً)، 77/78=يمن موبايل، 70=واي، مع `fromNumber` (100-109) و`fromApiOperatorName` (111-125) و`color`.
- `DinstarGatewayStatus` (129-165): gatewayId/name/isOnline/ip/model/firmware/ports + `registeredButUnusableCount` و`averageSignalDbm` و`bestPortForCall`.
- `DinstarFleetStatus` (173-187): عدة بوابات + `summaryAr`.
- `DinstarCdr` (189-200)، `DinstarStatistics` (202-210)، `DinstarCommandResult` (212-216)، **`DinstarSms` (218-224)** — جديد.

### 1.3 `red-app\...\features\dinstar\DinstarViewModel.kt` (228 سطراً)
- `refreshStatus()` (59-103): **`GET /api/admin/dinstar/fleet/ports`**، يفسّر `{gateways:[{gateway:{...healthState...}, ports:[...], error}]}` — مطابق تماماً لاستجابة `DinstarFleetController.allPorts()`، ويخزّن الأسطول في `_fleetStatus` والبوابة الأولى في `_gatewayStatus` (توافق مع الشاشات القديمة).
- `parsePort()` (113-131): نقل القيم كما هي؛ `signalDbm/signalUsable` يحسبها **الخادم** (لا يعيد الحساب).
- `sendSms(text, numbers)` (133-150): `POST /api/admin/dinstar/sms/send` + إضافة `DinstarSms` إلى `_smsHistory` عند النجاح.
- `connectWebSocket()` (152-163): عبر `DinstarWebSocketBridge` + `PortStatusChanged → refreshStatus()`، `CdrReceived → queryCdr()`.
- `resetPort` (165-176)، `sendUssd` (178-191، مع `delay(2500)` ثم `pollUssdResult`)، `pollUssdResult` (193-208)، `clearCommandResult` (210-212).
- **`queryCdr()` (214-218): يستدعي `GET /api/admin/dinstar/cdr` لكنه **لا يخزّن النتيجة** — `_cdrRecords` يبقى فارغاً دائماً.**

### 1.4 `red-app\...\features\dinstar\DinstarAdminScreen.kt` (447 سطراً)
شاشة إدارة البوابات: TopBar مع تحديث (80-88)، بانر نتيجة الأوامر (92-165)، قائمة بوابات (167-198)، `GatewayCard` (284-362: إحصائيات منافذ/مسجلة/مكالمات + قائمة المنافذ)، `PortRowItem` (365-438: يعرض `signalPercent` كنسبة مئوية — انظر قسم المشاكل)، حوار USSD (202-280) بأزرار *555# و*123#.

### 1.5 `red-app\...\features\dinstar\DinstarSmsScreen.kt` (198 سطراً)
شاشة SMS: قائمة عكسية `reverseLayout = true` من `smsHistory`، حقل رقم + حقل نص + زر إرسال (146-194). **غير مربوط بأي ملاحة** (انظر المشاكل).

### 1.6 `red-app\...\features\pstn\PstnCallScreen.kt` (257 سطراً)
شاشة المكالمة الصادرة: مؤقّت (59-66)، شارة "بوابة DINSTAR المركزية" (92-96)، حلقة Avatar بحدود `SovereignGradients.dinstar` (102-116)، رقم مقسّم (121)، `SovereignOperatorBadge` (130)، حالات النص (134-149)، `SovereignWaveVisualizer` (151-158)، أزرار كتم/تسجيل/مكبر (163-193، التسجيل يتطلب `callId` من `PstnState.Started`)، زر إنهاء (198-206).

### 1.7 `red-app\...\features\pstn\IncomingPstnCallScreen.kt` (161 سطراً)
شاشة المكالمة الواردة: نبض 1f→1.15f (tween 1000 / FastOutSlowInEasing / Reverse) (36-44)، شارة "مكالمة هاتفية واردة" بألوان AqyalGold (61-74)، أفاتار + رقم + **`YemeniOperatorDetector.getOperatorInfo`** مع `brandColor` (105-113)، زرا رفض/قبول بألوان YounesRose/YounesEmerald (126-147). **لا يُستدعى من أي مكان في التطبيق.**

### 1.8 `red-app\...\media\VoiceMessageViewModel.kt` (563 سطراً)
تسجيل صوتي مشفر: MediaRecorder AAC بمعدلات الجودة (97، 523-528)، press-to-record/release-to-send + lock (224-230) + drag-to-cancel بعتبة 0.6 (236-242)، preview (248-274)، إرسال فردي/جماعي (276-287)، **AES-256-GCM** بمفتاح/نونس عشوائيين (426-483) مع SHA-256 و`VoiceManifest` (531-546)، منح الوصول للمستلمين **بالتوازي** عبر `coroutineScope + async + awaitAll` (447-453) — إن لم يُمنح أحد يحذف الملف (456-459). **التعليقات العربية كلها تالفة الترميز (mojibake) + أسطر فارغة زائدة 557-563.**

### 1.9 `red-app\...\media\VoiceNotePlayer.kt` (377 سطراً)
مشغّل صوتي: ExoPlayer (media3) مع `AudioAttributes SPEECH` (74-103)، **AudioFocusRequest** كامل (106-153: duck عند CAN_DUCK / pause عند LOSS / volume 1f عند GAIN)، شريط سرعات 0.5×-2× (245-281)، waveform مع drag-to-seek + tap-to-seek (287-324)، fallback LinearProgressIndicator (327-336).

### 1.10 `red-app\...\ui\CallsHubActions.kt` (821 سطراً)
مركز المكالمات (Bento Grid): `CallsHubLaunchers` (43-159) — بطاقات: بث مباشر، مكالمات جماعية، مؤتمرات، مساحات، **الهاتف اليمني DINSTAR (117-127)**، مكالمة E2EE، استكشاف؛ `CallBentoCard` (166-241) مع ضغط 0.96f وspring (180-187)؛ `GroupCallPickerDialog` (260-381)؛ `ConferenceHubDialog` (388-542)؛ `LiveStreamHubDialog` (549-783)؛ `rememberCallPermissionLauncher` (790-807)؛ `callTypeGlyph` (813-821) — DINSTAR→PhoneInTalk/AqyalGold.

### 1.11 `android\features\pstn\PstnSipEngine.kt` (مشروع معزول)
محرك SIP منفصل: اتصال `ws://[host]:8088/ws` مع `Sec-WebSocket-Protocol: sip`، `sipUser = "red-webrtc-client"` / `sipPass = "red-secret-token"` **مكتوبتان صراحة**، `readTimeout(0)`. **خارج بناء الـ root (settings.gradle لا يضمّه).**

### 1.12 `app\src\main\java\com\red\sovereign\features\pstn\DinstarDashboardUI.kt` (مشروع معزول)
لوحة تحكم: يستورد **`com.red.features.dinstar.DinstarGatewayStatus/DinstarPort` — حزمة غير موجودة إطلاقاً في المستودع (glob صفر نتائج)**؛ سطرا 14-15 بـ**علامات اقتباس مفردة** (`'androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar'`، `'...PhoneInTalk'`) — خطأ Kotlin صرف؛ يستخدم `port.simType.colorHex` و`isHealthy` غير الموجودين في النموذج الفعلي (`color: Color` و`isAvailable`). **لا يُترجم.**

### 1.13 `app\...\features\pstn\DinstarLiveMonitor.kt` (مشروع معزول)
مراقب حي منفصل: `@Singleton @Inject`، Gson، `gatewayIp = "192.168.11.1"` **مثبتة**، `https://$gatewayIp`، **admin/admin مكتوبة**، trust-all SSL. **معزول عن البناء، ومرتبط بالحزمة المفقودة نفسها.**

### 1.14 `admin_dashboard\src\pages\PstnManagement.tsx` (لوحة ويب)
لوحة إدارة مستخدمي PSTN: `PstnUser` (userId, redId, username, displayName, pstnEnabled, pstnDailyLimit, usedToday, accountStatus, role)، `STATUS_BADGE` (APPROVED/PENDING/REJECTED/SUSPENDED/BANNED)، `ROLE_BADGE` (ADMIN/USER)، GET `/api/master/v1/pstn/users`، PATCH `/api/master/v1/pstn/users/{userId}`، POST `/api/master/v1/pstn/users/{userId}/toggle`، حد يومي 0..10000، `apiFetch` + `usePolling`، جولات `phoneUtil.parse`.

---

## 2) التغييرات الدقيقة في الديف (`calls_diff_full.txt` — 5138 سطراً، 37 ملفاً)

### Backend (13 ملفاً)
| الملف (سطر البداية) | التغييرات |
|---|---|
| `.env.example` (1) | إضافة `ASTERISK_WSS_URL` وملاحظات الربط العام `domain/ws/sip → pstn-gateway:8089`. |
| `DinstarAdminController` (23) | **حذف `GET /fleet/ports`** (المنطق القديم كان يجمع gateways بلا هيكل أسطول). لا يؤثر على التطبيق: `/fleet/ports` موجود في `DinstarFleetController:138`. |
| `CallHistoryService` (59) | تعديلات طفيفة (احتفاظ بالميدان `type`). |
| `IceServerController` (72) | دعم coturn محلي (STUN/TURN 3478، TURNS 5349، TURN 443) + Open Relay (TURN 80، TURNS 443)؛ RFC 7635 HMAC زمني؛ خصائص `red.turn.*`. |
| `DinstarController` (151) | توسعة endpoints الإدارة. |
| `DinstarEventListener` (346) | أحداث Asterisk جديدة. |
| `DinstarLoadBalancer` (431) | **`forcedPort: Int? = null`** مع `if (forcedPort != null && index != forcedPort) continue`؛ إصلاحات: استبعاد `signalUsable=false`، فكّ قيد 8 منافذ (`AtomicIntegerArray(8)` و`%8` حُذفا)، إصلاح عدّاد `releasePort`، مطابقة المشغل بالاسم المطبوع (MTN=YOU بعد 2021)، تعليقات توثيقية مفصلة. |
| `PstnCallController` (1108) | `dial(...)` يمرّر `request.slotIndex`؛ `PstnCallRequest(number, slotIndex: Int? = null)`. |
| `PstnCallService` (1131) | **إضافة بادئات `78x`** للـ YEMEN_MOBILE_PREFIXES؛ `PstnRetryEvent(callId, userId, userRedId, targetNumber, failedPort)`؛ `dial(userId, number, slotIndex)` مع رسالة خطأ خاصة بالمنفذ المطلوب؛ `@EventListener handleRetry` — **إعادة محاولة تلقائية** على منفذ آخر بعد فشل مكالمة. |
| `PstnManager` (1224) | `dialGsm(number, endpoint, portIndex = -1)` + **`setVariable("RED_PORT_INDEX", ...)`**. |
| `DinstarHardwareService` (1248) | **إعادة كتابة جذرية للمصادقة**: حذف مكتبة `burgstaller` (Digest auth) → **جلسة Web UI**: `POST /goform/IADIdentityAuth` (302 + كعكة `devckie`) ثم `GET /WebGetPortInfoAll` (مصفوفة خام بلا غلاف `info`)؛ `parsePortInfoResponse` يستبعد صف `Total`؛ الافتراضيات صارت **`http`/`80`** بدل https/443. |
| `CallWebSocketHandler` (1408) | إضافة `ApprovedDeviceSessionGuard` — إعادة تحقق من الموافقة في **كل إطار** وإلا `CloseStatus.POLICY_VIOLATION`. |
| `ConferenceWebSocketHandler` (1437) | نفس حارس الجلسة. |
| `LiveStreamWebSocketHandler` (1476) | حارس الجلسة + تعديلات بث. |

### البنية التحتية (6 ملفات)
- `docker-compose.yml` (1956): `ASTERISK_WSS_URL=ws://pstn-gateway:8089/ws` (2474)؛ `ASTERISK_WSS_PORT=8089` و`"8089:8088"` (2598-2605) مع تعليق عربي يشرح أن `bindport=8088` في http.conf يُلزم نشر منفذ مضيف مختلف 8089 للحفاظ على WSS ثابت؛ تعليقات نجد جديدة للوصول العام (2798-2805).
- `nginx.conf` (2847): **`upstream asterisk-ws-pool`** (3116-3118) مع `zone` و`resolve`؛ `location /ws/sip` → `proxy_pass http://asterisk-ws-pool` مع تعليق "SIP over WebSocket" (3237-3240)؛ CSP محدّثة بـ `connect-src ws: wss:`.
- `Dockerfile` (3291): `EXPOSE ... 8088 8089 ...`.
- `docker-entrypoint.sh` (3334): `WSS_PORT="${ASTERISK_WSS_PORT:-8089}"`، `bind=0.0.0.0:${WSS_PORT}` على transport `websockets` (3354-3360) مع `bindport=8088`؛ ملاحظة أن `transport-wss` حُذف عمداً (لا TLS داخل الحاوية).
- `extensions.conf` (3428): تنسيق القائمة؛ **`ExecIf` يضيف `PJSIP_HEADER(add,X-Port)` و`X-Dinstar-Port` من `${RED_PORT_INDEX}`** + NoOp؛ **سياق جديد `[from-red-client-webrtc]`** لجسر WebRTC→DINSTAR.
- `IceServerController` مطابق لشكل `BridgeIceConfig(expiresAt, iceServers)`.

### red-app (14 ملفاً)
- `PstnApi.kt` (3466): +`slotIndex` +DTOs الجسر/ICE.
- `CallHistoryViewModel` (3539): **`CallFilterType`** (ALL/MISSED/INCOMING/OUTGOING/VIDEO/DINSTAR="GSM يمني") + **`CallStatsSummary`** (totalCalls, answeredCalls, missedCalls, totalDurationSeconds, videoCallsCount, voiceCallsCount, dinstarCallsCount, successRate, topPeer, peakHour) + فلاتر تُطبَّق.
- `CallRecordingManager` (3738)، `ConferenceOverlay` (3758)، `ConferenceService` (3798)، `GroupCallService` (3815)، `LiveStreamViewerOverlay` (3830)، `SfuMediaClient` (3869)، `VoipPushRegistrar` (3898)، `YounesCallService` (3928) — تحسينات/إصلاحات غير محورية.
- `VoiceRecorder.kt` ×2 (3985 core/utils، 4045 features/chat).
- `DinstarAdminScreen.kt` (4061): توسعة 59 → 279+ سطراً (شاشة الأسطول الحالية).
- `DinstarModels.kt` (4575): **+`DinstarSms`** (218-224).
- `DinstarViewModel.kt` (4592): +`_smsHistory`، تحديث `sendSms` لتعبئة السجل، +`sendSms(number, text)`، +`resetPort`، +`sendUssd`/`pollUssdResult`، +`clearCommandResult`.
- `VoiceMessageViewModel.kt` (4684): **+`awaitAll` للـ grants** + **سطر BOM `﻿package`** + **كل التعليقات العربية تحوّلت إلى mojibake** (`ðŸŽ™ï¸`، `Ø§Ù„ØªØ³Ø¬ÙŠÙ„`) + 7 أسطر فارغة زائدة في نهاية الملف.
- `VoiceNotePlayer.kt` (4918): +AudioFocus كامل + حذف التعليقات + إعادة هيكلة المتغيرات.
- `CallsHubActions.kt` (5050): `MutableInteractionSource` + ضغط 0.96f (spring بouncy) + ظل مزدوج 6.dp + حدود متدرجة + `indication = null`.

---

## 3) Endpoints الخاصة بـ `PstnApi` (بمعاملاتها)

| العملية | الطلب | البودي | الرد |
|---|---|---|---|
| `dial(number, slotIndex=null)` (41) | `POST /api/pstn/calls` | `{number, slotIndex?}` | `PstnCallResponse(callId, status, number, usedToday, dailyLimit, slot=-1)` |
| `hangup(callId, port=-1)` (49) | `POST /api/pstn/calls/{callId}/hangup` | `{"port":N}` | `Boolean` |
| `bridge(number)` (56) | `POST /api/pstn/bridge` | `{number}` | `BridgeResponse` (sipServer/sipUsername/sipPassword/sipTransport/targetNumber/iceServers/expiresAt/usedToday/dailyLimit/turnServerUrl?/turnUsername?/turnPassword?) |

- `slotIndex = null` = الاختيار الذكي عبر `DinstarLoadBalancer` (إشارة dBm + مشغل داخل الشبكة + عدّادات استخدام). `slotIndex ≠ null` = حصر الترشيح في ذلك المنفذ مع بقاء شروط الصلاحية (REGISTERED + IDLE + signalUsable).
- الأمان (SecurityConfig:133): `/api/pstn/**` → `authenticated` (أي حساب APPROVED مفعّل PSTN). `/api/admin/dinstar/**` و`/api/master/v1/**` و`/api/admin/dinstar/sms/**` → `hasRole("ADMIN")` (115، 134).
- **الملاحظة الحرجة:** `dial()` و`hangup()` **لا يُستدعيان في أي مكان** — `AuthViewModel.dialPstn` (119-136) يستخدم `PstnWebRtcManager` مباشرةً، والوحيد المستخدَم فعلاً هو `bridge()` (PstnWebRtcManager.kt:99).

---

## 4) آلية شاشات Dinstar (التدفق)

```
RedDashboard.kt:385-386 → DinstarViewModel(application) → DinstarAdminScreen(dm)
      │
      ├─ init (47-50): refreshStatus() + connectWebSocket()
      │      ├─ GET /api/admin/dinstar/fleet/ports (61)  ← DinstarFleetController:138 (ADMIN فقط)
      │      │      └─ parsePort لكل منفذ (113) → fleetStatus (87-90) + gatewayStatus = أول بوابة (92)
      │      └─ DinstarWebSocketBridge → ws://…/ws/dinstar   ← ⚠ غير مسجّل في WebSocketConfig
      │             (المسجّل فقط: /ws/master و/ws/calls — WebSocketConfig:34,44)
      │
      ├─ أزرار الشاشة:
      │      ├─ Refresh (81) → refreshStatus()
      │      ├─ إعادة تشغيل منفذ (429) → resetPort(index) → POST /api/admin/dinstar/ports/{i}/reset (168)
      │      ├─ USSD (423) → Dialog (202-280) → sendUssd → POST /api/admin/dinstar/ports/{i}/ussd (182)
      │      │      └─ بعد 2.5 ثانية: pollUssdResult → GET /api/admin/dinstar/ports/{i}/ussd (195)
      │      └─ SMS: DinstarSmsScreen (معزولة عن الملاحة) → sendSms → POST /api/admin/dinstar/sms/send (137)
      └─ أحداث WebSocket: PortStatusChanged → refreshStatus()، CdrReceived → queryCdr() (نتيجته مهملة!)
```

الملاحظات:
- `_gatewayStatus` مجرد نسخة من أول بوابة "توافقاً مع الشاشات القديمة" (91-92).
- `DinstarAdminScreen` تُعرض فقط لحسابات ADMIN (قاعدة SecurityConfig:115).
- **`DinstarSmsScreen` لا تُستدعى من `RedDashboard` إطلاقاً** (لا يوجد مرجع لها).

---

## 5) آلية شاشات PSTN (التدفق)

```
CallsHubLaunchers (CallsHubActions.kt:117-127 "الهاتف اليمني") → onPstn
   ↓
RedDashboard:
   ├─ زر الاتصال (3164): if (account.pstnEnabled) dialPstn(number)
   ├─ زر "اتصال صوتي عبر DINSTAR" (3213): dialPstn(number)
   └─ overlay (3189-3192): pstnState ∈ {Started, Bridging, Registering, Ringing, Dialing} → PstnCallScreen
   ↓
AuthViewModel.dialPstn (119-136): pstnState=Bridging → PstnWebRtcManager.call(number)
   ↓
PstnWebRtcManager.call (91): POST /api/pstn/bridge (99) → BridgeResponse
   ├─ iceServers.iceServers → PeerConnection ICE (111)
   ├─ SIP WSS → bridge.sipServer (nginx: /ws/sip → upstream asterisk-ws-pool → pstn-gateway:8089)
   ├─ تسجيل SIP (red-webrtc-client) → onConnected → pstnState=Registering
   ├─ invite(bridge.targetNumber) (154) → onRinging → pstnState=Ringing
   ├─ Asterisk [from-red-client-webrtc] → Dial DINSTAR مع PJSIP_HEADER X-Port/X-Dinstar-Port
   └─ onAnswered → pstnState=Started("webrtc", 0, 100) (126)
   ↓
PstnCallScreen (257): مؤقّت + أزرار كتم/تسجيل/مكبر + إنهاء → onHangup → hangupPstn (138-143)
   └─ WebRTCSipClient.hangup + release + pstnState=Idle

المكالمة الواردة: IncomingPstnCallScreen غير مربوطة بأي دفق إشعارات (لا مراجع لها في red-app).
```

الملاحظات:
- `PstnState` يُعرَّف في `AuthViewModel.kt` (327-340): Idle/Dialing/Bridging/Registering/Ringing/Started/Error.
- `pstnState` يتلاشى تلقائياً (146 `clearPstnState`) و`pstnWebRtc` يُحرَّر عند onHangup (129-130).
- **`PstnApi.dial` (مع slotIndex وusedToday/dailyLimit) معطّل من الواجهة** — المسار الفعلي هو الجسر SIP-WebRTC فقط.

---

## 6) المشاكل المكتشفة (مرتبة بالخطورة)

### حرجة (تكسر البناء أو تشل الوظيفة)
1. **حزمة `com.red.features.dinstar` غير موجودة** — `DinstarDashboardUI.kt` و`DinstarLiveMonitor.kt` يستوردانها (glob مؤكد: صفر نتائج). كما يستخدمان `simType.colorHex` و`isHealthy` غير الموجودين في `DinstarModels.kt` (الموجود: `color: Color` و`isAvailable`). **هذان الملفان لن يترجما أبداً.**
2. **Imports بعلامات اقتباس مفردة** في `DinstarDashboardUI.kt:14-15` (`'androidx.compose.material.icons.rounded.SignalCellularConnectedNoInternet0Bar'`...) — خطأ نحوي في Kotlin.
3. **`/ws/dinstar` غير مسجّل في الـ backend** (WebSocketConfig يسجّل `/ws/master` و`/ws/calls` فقط) → اتصال `DinstarWebSocketBridge` سيفشل (404)؛ الشاشات تعتمد تحديثاً يدوياً فقط.
4. **`DinstarViewModel.queryCdr()` (214-218) يرمي النتيجة** — `_cdrRecords` يبقى فارغاً دائماً؛ شاشة السجل (لو وُجدت) بلا بيانات.
5. **`PstnApi.dial`/`hangup` مهملان** — مسار `POST /api/pstn/calls` (مع slotIndex والاختيار الذكي والقيود اليومية `usedToday/dailyLimit`) غير مستخدَم من أي كود؛ `AuthViewModel` يجسر عبر `/bridge` فقط، فيُفقد التقرير بالحدود اليومية وربط callId.

### وظيفية (لا تُكسر البناء)
6. **`DinstarSmsScreen` و`IncomingPstnCallScreen` غير مربوطتين بأي ملاحة** — لا مراجع لهما في `RedDashboard.kt` أو أي مكان آخر (grep: صفر). واجهة كاملة بلا مدخل.
7. **`DinstarAdminScreen.PortRowItem:415-419` يعرض `signalPercent` كنسبة %** بينما النموذج (DinstarModels:14-26) يؤكد أن النسبة مغلوطة قديماً والقياس الصحيح dBm (`signalDbm`/`signalLabelAr`). تناقض داخل نفس الميزة.
8. **بيانات اعتماد مكتوبة في الكود**: `PstnSipEngine` (`red-webrtc-client`/`red-secret-token`)، `DinstarLiveMonitor` (`admin`/`admin` + IP `192.168.11.1` ثابت)، وافتراضيات `DinstarHardwareService` (admin/admin، trust-all).
9. **تلف ترميز واسع**: تعليقات `VoiceMessageViewModel.kt` كلها mojibake (`ðŸŽ™ï¸`، `Ø§Ù„ØªØ³Ø¬ÙŠÙ„`...) وسطر BOM في أول السطر، + 7 أسطر فارغة زائدة في نهاية الملف (557-563)؛ مسافة بادئة شاذة عند سطر 446-447 (`val grantResults` بمسافة 59).

### بنيوية (مشاريع معزولة)
10. **`app/` و`android/` خارج البناء**: `settings.gradle.kts` يضم `:app` (projectDir=`red-app`) و`:backend-server` فقط — أي أن `DinstarDashboardUI.kt` و`DinstarLiveMonitor.kt` و`PstnSipEngine.kt` ملفات ميتة لا تُبنى ولا تُختبر.
11. `DinstarLiveMonitor` و`DinstarHardwareService` يلتفّان على شهادات TLS الذاتية (trust-all) — مخاطرة أمنية معروفة في شبكات الإدارة الخاصة، لكنها مكتوبة بلا قيود شبكة.

### لم تكن مشاكل (تصحيحات للافتراضات السابقة)
12. **`GET /api/admin/dinstar/fleet/ports` موجود فعلاً** في `DinstarFleetController:138` — حذف `DinstarAdminController` كان لنسخة مكررة؛ وViewModel (61) مطابق تماماً لشكل الاستجابة (`gateway` + `ports` + `error` + `healthState`).
13. `PstnManagement.tsx` (GET/PATCH/toggle) مطابق لـ `RedMasterController` (133/197/229) وقاعدة ADMIN (115).
14. مكونات UI المطلوبة كلها موجودة: `SovereignOperatorBadge/StatusBadge/WaveVisualizer` (SovereignUiComponents.kt:321/153/206)، `SovereignGradients.dinstar` (SovereignThemeSystem.kt:78)، `CairoFamily` (RedTheme.kt:41)، `AqyalGold/YounesEmerald/YounesRose` (RedTheme.kt:69/95/107)، `VoiceColors/VoiceWaveformCanvas` (media/voice)، `MediaApi.uploadEncrypted/grant/delete` (MediaApi.kt:71/79/82)، `YemeniOperatorDetector` (calls/YemeniOperatorDetector.kt:27).

---

## 7) تقييم الاكتمال والثغرات

**الاكتمال:**
- الـ backend مكتمل تقريباً: موزّع أحمال مُصلَح (بدون قيد 8 منافذ، قياس dBm حقيقي، مطابقة مشغل، `forcedPort`)، إعادة محاولة تلقائية (`PstnRetryEvent`)، إدارة أسطول حقيقية متعددة البوابات، جسر ICE (coturn + Open Relay + RFC 7635)، وأمان صارم (ADMIN فقط لأدوات الإدارة + إعادة تحقق WebSocket لكل إطار).
- البنية التحتية مكتملة ومترابطة: `nginx /ws/sip` → `asterisk-ws-pool` (8089) → `transport websockets` → سياق `from-red-client-webrtc` → `X-Port/X-Dinstar-Port` → DINSTAR. أرقام المنافذ متسقة (8088 داخل الحاوية / 8089 خارجها).
- واجهة `red-app` بصرياً مكتملة (شاشات مكالمات صادرة/واردة، إدارة بوابات، SMS، مؤتمرات/مساحات/بث، رسائل صوتية مشفرة) وكل مراجعها تحلّ (تحقّق فعلي).

**الثغرات الوظيفية:**
1. WebSocket DINSTAR ميت في الممارسة (unregistered) — التحديث الحي لا يعمل؛
2. مسار `dial()` (حدود يومية/فواتير/اختيار منفذ) غير مفعّل في التطبيق؛
3. `IncomingPstnCallScreen` + إشعارات المكالمات الواردة + تدفق الرد غائبة تماماً (لا push ولا UI hook)؛
4. `DinstarSmsScreen` بلا مدخل ملاحي؛
5. سجل CDR يُجلب ولا يُخزَّن؛
6. ملفات `app/` و`android/` معزولة وتشير لحزمة غير موجودة (يجب حذفها أو دمجها مع تصحيحات colorHex/isHealthy والإيقونات وعلامات الاقتباس)؛
7. ترميز التعليقات العربية في `VoiceMessageViewModel` تالف (مشكلة تحويل ترميز عند الحفظ — UTF-8 ↔ ANSI/UTF-16)؛
8. الإشارة المئوية في `PortRowItem` تتعارض مع فلسفة النموذج الجديد (dBm فقط).

**الخلاصة:** العمود الفقري (backend + infra + نواة red-app) متين ومدروس بدقة (توثيق عربي ممتاز في `DinstarLoadBalancer` و`DinstarModels` و`docker-compose`)؛ أما الحواف فتتطلب: ربط `/ws/dinstar`، تفعيل `dial`، ربط شاشة الوارد/الـ SMS، تصحيح `queryCdr`، وتنظيف/دمج المشاريع المعزولة الثلاثة وإصلاح ترميز `VoiceMessageViewModel`.
