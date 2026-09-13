# نظام المكالمات في يونس / RED Calls Architecture

> **آخر تحديث:** 2026-08-09  
> **الحالة:** مكتمل (جميع الأنواع مدعومة)  
> **الكاتب:** Arena.ai Agent Mode

## نظرة عامة

يونس يدعم **4 أنواع** من المكالمات، كل منها بمسار (route) مختلف وطبقة تشفير مناسبة:

| النوع | المسار | التشفير | العدد | الاستخدام |
|---|---|---|---|---|
| **1-1 صوت/فيديو** | RED (WebRTC P2P) | DTLS-SRTP + Identity Pinned | 2 أشخاص | محادثات خاصة |
| **مؤتمر (Conference)** | RED (WebRTC mesh/SFU) | DTLS-SRTP | 2-4 (mesh) / 5+ (SFU) | اجتماعات مجموعات |
| **بث مباشر (Live)** | RED (WebRTC 1-to-N) | DTLS-SRTP | 1 مذيع + N مشاهدين | بث عام/خاص |
| **DINSTAR PSTN** | DINSTAR gateway | GSM/CDMA المشفّر | 1 يونس ↔ شبكة يمنية | هاتف ثابت يمني |

## المعمارية

```
┌────────────────────┐  WebSocket  ┌─────────────────────┐
│ Android Client     │  signaling  │ Spring Boot Backend │
│ (YounesCallService)│◄────────────►│ (CallWebSocket-     │
│                    │  ICE/STUN   │  Handler)            │
│  WebRtcEngine      │  /ws/calls  │                      │
└────────┬───────────┘             └────────┬─────────────┘
         │                                   │
         │  STUN/TURN                        │  ICE config
         │  candidates                       │  (time-limited HMAC)
         ▼                                   ▼
   ┌──────────┐                        ┌──────────────┐
   │  TURN    │  media (SRTP)         │  REST API    │
   │  Server  │◄───────────────────────│  /history    │
   └──────────┘                        └──────────────┘
```

## تفصيل الملفات

### Android (`com.red.sovereign.calls`)

| ملف | الدور |
|---|---|
| `YounesCallService.kt` | مكالمة 1-1 + HOLD + DTMF + Call Waiting |
| `ConferenceService.kt` | مؤتمرات متعددة + room join/leave |
| `LiveStreamService.kt` | بث مباشر + broadcaster/viewer roles |
| `WebRtcEngine.kt` | PeerConnection factory + NetworkStats |
| `TelecomBridge.kt` | Self-managed VoIP عبر `androidx.core.telecom` |
| `CallSignalingClient.kt` | WebSocket signaling للـ 1-1 |
| `ConferenceSignalingClient.kt` | WebSocket signaling للـ conferences |
| `LiveStreamSignalingClient.kt` | WebSocket signaling للـ live broadcasts |
| `CallOverlay.kt` | UI overlay (fullscreen) للمكالمة |
| `ConferenceOverlay.kt` | UI grid للمشاركين |
| `LiveStreamViewerOverlay.kt` | UI للمذيع/مشاهد |
| `UnifiedCallOverlays.kt` | يختار overlay واحد بناءً على الـ state |
| `CallLogCipher.kt` | تشفير peer IDs في call logs (Keystore) |
| `YemeniOperatorDetector.kt` | كشف مشغّل يمني (7X موبايل، 1-5 هاتف ثابت) |
| `CallHistoryViewModel.kt` | تحميل + تخزين + mapping صحيح |
| `CallHistoryModels.kt` | data class لـ CallHistoryItem |

### Backend (`com.red.server.calls`)

| ملف | الدور |
|---|---|
| `CallHistoryController.kt` | REST `GET /api/calls/history` |
| `CallHistoryService.kt` | MongoDB persistence + state machine |
| `CallHistoryModels.kt` | `CallHistoryDocument` + enums |
| `IceServerController.kt` | TURN/STUN credentials (HMAC, 1h expiry) |
| `SfuTicketController.kt` | Short-lived capability for mediasoup rooms |
| `LiveStreamService.kt` | Active streams registry + viewer counts |

### Backend WebSocket

| ملف | المسار | الدور |
|---|---|---|
| `CallWebSocketHandler.kt` | `/ws/calls` | 1-1 signaling مع cancel-broadcast |
| `ConferenceWebSocketHandler.kt` | `/ws/conference` | Room-based mesh signaling |
| `LiveStreamWebSocketHandler.kt` | `/ws/livestream` | Broadcaster ↔ viewer routing |

### Media SFU (`media-sfu/server.js`)

- Mediasoup-based selective forwarding unit
- 199 سطر
- يستمع على `/sfu` (WebSocket بعد authentication)
- WebRTC transport creation + produce/consume
- حالياً لا يُستخدم من Android (يفضل mesh لـ <4 مشاركين)

## الـ state machine للمكالمات

```
                    ┌─────────┐
                    │  Idle   │◄─────────────────────┐
                    └────┬────┘                      │
                         │ start/listen              │
                         ▼                           │
              ┌──────────────────────┐               │
              │     Incoming         │──────────────►│
              │ (callId, peer, mode) │  reject/end   │
              └──────────┬───────────┘               │
                         │ accept                    │
                         ▼                           │
              ┌──────────────────────┐               │
              │    Connecting        │──────────────►│
              └──────────┬───────────┘  error        │
                         │ connected                 │
                         ▼                           │
              ┌──────────────────────┐               │
              │     Active           │──────────────►│
              │  + can be HELD       │  end/timeout  │
              │  + can have waiting  │               │
              └──────────────────────┘               │
                                                      │
    ┌─────────────────────────────────────────┐        │
    │ ActiveWithIncoming(active, waiting)    │────────┘
    │ (call waiting)                         │
    └─────────────────────────────────────────┘
```

## ميزات متقدمة

### 1. HOLD/RESUME
- اضغط زر "تعليق" لتعليق المكالمة
- الـ peer connection يبقى حياً (إعادة اتصال فورية)
- المحرك يعطل local tracks
- النظام (Telecom) يعرض المكالمة كمعلّقة

### 2. CALL WAITING
- مكالمة نشطة + مكالمة واردة = `ActiveWithIncoming`
- شريط UI يعرض "مكالمة من X في الانتظار"
- خيارات: "تعليق الحالي وقبول" / "رفض"

### 3. DTMF
- Keypad يظهر في الـ overlay الصوتي
- يُرسل عبر `CallControlScope.sendDtmf`
- للملاحة في IVR والخدمات المصرفية

### 4. Network Quality Indicator
- Polling كل ثانيتين
- يعرض: excellent (4 bars أخضر) / good / fair / poor
- مع RTT بالميلي ثانية

### 5. E2EE Call Logs
- `CallLogCipher` يشفر `peerId` و `peerLabel` بـ `ProtocolRecordCipher` (Android Keystore)
- DB في `/data/data/com.red.sovereign/databases/red.db` تحوي hex مشفر فقط
- يُفك التشفير فقط عند العرض في الواجهة

## الاختبارات

| ملف | عدد الاختبارات |
|---|---|
| `YemeniOperatorDetectorTest.kt` | 12 |
| `CallHistoryMappingTest.kt` | 3 |
| `CallUiStateTest.kt` | 8 |
| `CallSignalSerializationTest.kt` | 3 |
| `NetworkStatsTest.kt` | 4 |
| `CallConstantsTest.kt` | 3 |
| `CallLogCipherTest.kt` | 4 |
| **Android total** | **37** |
| `ConferenceWebSocketHandlerTest.kt` | 5 |
| `LiveStreamWebSocketHandlerTest.kt` | 4 |
| `LiveStreamServiceTest.kt` (existing) | 6 |
| **Backend total** | **15** |

## الأداء

- **Startup**: `YounesCallService.listen` يُستدعى من `MainActivity` بعد login
- **WebSocket reconnect**: يتم عند `onFailure` (لكن لا automatic retry)
- **Network stats**: polling 2s (يمكن تعديله)
- **Ringtone**: يبدأ مع المكالمة الواردة، يوقف عند القبول/الرفض

## نقاط معروفة (Known limitations)

1. **BOOT_COMPLETED**: لا يوجد receiver — يجب فتح التطبيق مرة بعد reboot لتفعيل signaling
2. **E2EE لـ metadata**: الـ peerId في signaling مشفر (TLS) لكن مكشوف في MongoDB server-side
3. **Call recording**: غير مدعوم بعد (يحتاج MediaRecorder + WebRTC integration)
4. **Conference > 4**: mesh يصبح ثقيل، يُفضل الانتقال لـ media-sfu
5. **DTMF detection**: لا يكتشف DTMF من الـ remote (incoming)
6. **Network quality events**: لا يُرسل للـ backend (local-only UI)

## CI/CD

- `android-tests.yml`: يشغل `red-app:testDebugUnitTest`
- `backend-tests.yml`: يشغل `backend-server:test`
- `docker-image.yml`: يبني Docker image
