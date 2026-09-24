# 📡 DINSTAR UC2000-VE-8G — شريحة الاندرويد V2 (النسخة الكاملة)

## ما الجديد في V2

### 🔥 التقنيات المتقدمة المضافة

| التقنية | الوصف | المكان |
|---|---|---|
| **Circuit Breaker** | حماية من الانهيار المتتابع — 5 أعطال → OPEN → 30ث → HALF-OPEN | DinstarViewModel |
| **Sliding Window** | تتبع نسبة النجاح عبر آخر 20 مكالمة | DinstarViewModel |
| **Weighted Fair Queuing** | اختيار منفذ عادل: w_signal + w_operator + w_usage + w_roundRobin + w_successRate | DinstarViewModel |
| **SharedFlow** | أحداث Dinstar الحية (multicast, hot stream, DROP_OLDEST) | DinstarViewModel |
| **SupervisorJob** | عزل أعطال الفرعية — عطل في SMS لا يوقف المراقبة | DinstarViewModel |
| **Atomic Variables** | خيط آمن بدون أقفال (circuitBreaker, portUsageCounter, roundRobin) | DinstarViewModel |
| **OkHttp ConnectionPool** | 5 اتصالات HTTP متاحة مع 30ث keep-alive | DinstarViewModel |
| **OkHttp PING** | WebSocket heartbeat كل 30ث (ping/pong تلقائي) | DinstarWebSocketBridge |
| **Exponential Backoff** | WS إعادة اتصال: 1s→2s→4s→8s...max 60s | DinstarWebSocketBridge |
| **SMS Gateway** | إرسال/استقبال SMS عبر Dinstar API (GSM7BIT + UCS2) | DinstarSmsScreen |

### 📱 ميزة SMS الجديدة

| Endpoint | الطريقة | الوصف |
|---|---|---|
| `/api/send_sms` | POST | إرسال SMS فردي/مجمّع |
| `/api/query_sms_result` | POST | جلب نتائج الإرسال |
| `/api/query_sms_deliver_status` | POST | جلب حالة التسليم |
| `/api/query_incoming_sms` | GET | جلب SMS الواردة |
| `/api/query_sms_count` | GET | عدد SMS في الطابور |
| `/api/stop_sms` | GET | إيقاف مهمة إرسال |

### 📞 ميزات المنافذ المتقدمة

| Endpoint | الوصف |
|---|---|
| CallForward | تحويل مكالمات (Unconditional/NoReply/Busy/Not_Reachable/CancelAll) |
| Port Power | تشغيل/إيقاف منفذ (action=power&param=on/off) |
| Device Status | جلب حالة الجهاز الكاملة (POST /api/get_status) |

### 📡 WebSocket Bridge

```
الباكند ←ws→ DinstarWebSocketBridge ←SharedFlow→ DinstarTab/UI
```

الأحداث المستقبلة:
- `DINSTAR_PORT_STATUS` → تحديث فوري لحالة منفذ
- `DINSTAR_CDR` → سجل مكالمة جديد
- `DINSTAR_SMS` → SMS وارد
- `DINSTAR_USSD` → رد USSD
- `DINSTAR_EXCEPTION` → حدث استثناء (call_fail, sim_removed)
- `HEARTBEAT` → نبض

## خوارزمية Circuit Breaker

```
CLOSED (عادي) → كل الطلبات تمر
  ↓ 5 أعطال متتالية
OPEN (مفتوح) → كل الطلبات تُرفض فوراً
  ↓ بعد 30 ثانية
HALF_OPEN (نصف مفتوح) → طلب واحد يمر (probe)
  ↓ نجاح → CLOSED
  ↓ فشل → OPEN
```

## خوارزمية Weighted Fair Queuing

```
totalScore = w_signal     (0-100, حسب قوة الإشارة)
           + w_operator   (+35 إذا نفس مشغل الرقم اليمني)
           + w_usage      (-usageCount × 5, عقاب المنافذ المستخدمة كثيراً)
           + w_roundRobin (+8 للمنفذ التالي في الدور)
           + w_successRate (successRate × 10, تفصيل المنافذ الناجحة)

selectedPort = argmax(totalScore)
```

## الملفات (7 جديدة + 10 معدّلة)

### جديدة
| الملف | الأسطر | الوصف |
|---|---|---|
| `DinstarModels.kt` | 252 | نماذج البيانات |
| `DinstarViewModel.kt` | ~700 | العقل المركزي V2 (Circuit Breaker + WFQ + SMS) |
| `DinstarTab.kt` | ~870 | شريحة DINSTAR الرئيسية |
| `DinstarAdminScreen.kt` | ~560 | لوحة الإدارة (4 تبويبات) |
| `PstnCallScreen.kt` | ~274 | شاشة مكالمة PSTN |
| `DinstarSmsScreen.kt` | ~350 | شاشة SMS كاملة |
| `DinstarWebSocketBridge.kt` | ~200 | اتصال WebSocket حي |
| `DinstarModelsTest.kt` | ~200 | اختبارات الوحدة |

### معدّلة
| الملف | التغيير |
|---|---|
| `MainAppNavigation.kt` | مساران: `dinstar_admin` + `dinstar_sms` |
| `RedMainDashboard.kt` | DinstarTab + SMS callback |
| `MasterSystemOrchestrator.kt` | تهيئة + اكتشاف Dinstar |
| `PstnDialerScreen.kt` | تكامل DinstarViewModel حقيقي |
| `PstnEngine.kt` | اختيار منفذ ذكي |
| `MasterDatabase.kt` | DinstarPortSnapshotEntity + V6 |
| `SovereignDaos.kt` | DinstarDao |
| `RedIcons.kt` | 30+ أيقونة Dinstar |
| `RedNotificationService.kt` | توجيه DINSTAR WS events |
| `DinstarHardwareService.kt` | SMS + CallForward + PortPower + DeviceStatus |
| `DinstarSmsController.kt` | 6 SMS endpoints |
| `DinstarController.kt` | CallForward + PortPower + DeviceStatus |
| `SecurityConfig.kt` | SMS endpoint authorization |
