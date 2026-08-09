# 📡 DINSTAR UC2000-VE-8G — شريحة الاندرويد الكاملة

## نظرة عامة

شريحة DINSTAR في تطبيق يونس الاندرويد هي الواجهة الكاملة لإدارة ومراقبة بوابة GSM 
**Dinstar UC2000-VE-8G** المتصلة بالسيرفر الخلفي.

## البنية

```
┌──────────────────┐    HTTPS/Digest    ┌──────────────────┐    AMI/SIP    ┌──────────────┐
│  تطبيق يونس       │ ──────────────────→│  الباكند Spring   │ ────────────→│  Dinstar      │
│  (Android)       │ ←──────────────────│  Boot + OkHttp    │ ←────────────│  UC2000-VE-8G │
│                  │    REST + WS       │  Digest Auth      │              │  8 SIM Ports  │
└──────────────────┘                    └──────────────────┘              └──────────────┘
```

## الملفات الجديدة

| الملف | الوصف |
|---|---|
| `features/dinstar/DinstarModels.kt` | نماذج البيانات: DinstarPort, YemenOperator, DinstarGatewayStatus, CDR, Statistics |
| `features/dinstar/DinstarViewModel.kt` | العقل المركزي: API calls, Live Monitoring, Smart Port Selection |
| `features/dinstar/DinstarTab.kt` | شريحة DINSTAR الرئيسية في Dashboard |
| `features/dinstar/DinstarAdminScreen.kt` | لوحة الإدارة الكاملة (4 تبويبات) |
| `features/dinstar/PstnCallScreen.kt` | شاشة مكالمة PSTN مع تكامل Dinstar |

## التعديلات على ملفات موجودة

| الملف | التغيير |
|---|---|
| `MainAppNavigation.kt` | ربط مسار `dinstar_admin` بـ DinstarAdminScreen |
| `RedMainDashboard.kt` | تبويب "لوحة الاتصال" يعرض DinstarTab بدلاً من PstnDialerScreen |
| `MasterSystemOrchestrator.kt` | تهيئة DinstarViewModel + اكتشاف البوابة عند بدء النظام |
| `PstnDialerScreen.kt` | تكامل مع DinstarViewModel لعرض حالة البوابة الحقيقية |
| `PstnEngine.kt` | استخدام DinstarViewModel لاختيار المنفذ الذكي |
| `MasterDatabase.kt` | إضافة DinstarPortSnapshotEntity + Migration V5→V6 |
| `SovereignDaos.kt` | إضافة DinstarDao (8 functions) |
| `RedIcons.kt` | إضافة 30+ أيقونة Dinstar مخصصة |
| `RedNotificationService.kt` | توجيه رسائل WS من نوع DINSTAR_PORT_STATUS و DINSTAR_CDR |

## خوارزمية اختيار المنفذ الذكي

```
selectOptimalPort(targetNumber?):
  1. تصفية: ports.filter { isAvailable (مسجل + IDLE + إشارة ≥ 20%) }
  2. إذا targetNumber يمني:
     → preferredOperator = YemenOperator.fromNumber(targetNumber)
     → operatorBonus = +30 إذا port.simType == preferredOperator
  3. ترجيح: signalScore + operatorBonus + roundRobinBonus
  4. اختيار: maxBy(score)
  5. تحديث round-robin counter
```

### لماذا هذه الخوارزمية؟

- **ترجيح المشغل**: مكالمة سبأفون→سبأفون أرخص من سبأفون→MTN (inter-operator fees)
- **ترجيح الإشارة**: المنفذ ذو إشارة 85% أفضل من 30%
- **تنويع Round-Robin**: يمنع حظر SIM من运营商 بسبب مكالمات كثيرة من نفس المنفذ
- **حد الإشارة 20%**: إشارة أقل من 20% تعني مكالمة رديئة الجودة

## المشغلون اليمنيون

| المشغل | البادئة | اللون | رمز Hex |
|---|---|---|---|
| سبأفون | 770-779 | 🔴 أحمر | 0xFFE53935 |
| MTN اليمن | 710-719 | 🟡 أصفر | 0xFFFFB300 |
| يمن موبايل | 730-739 | 🟢 أخضر | 0xFF43A047 |
| هيتل | 700-709 | 🔵 أزرق | 0xFF1E88E5 |
| يو يمن | 77X (سبأفون سابقاً) | 🟣 بنفسجي | 0xFFAB47BC |

## المراقبة الحية (Live Monitoring)

- **الفترة**: كل 10 ثواني
- **Exponential Backoff**: عند الفشل: 10s → 20s → 40s → 80s (max 60s)
- **حد الفشل**: بعد 3 أعطال متتالية → ConnectionState = ERROR
- **Auto-recovery**: عند نجاح أي poll → إعادة تعيين عداد الفشل

## API Endpoints المستخدمة

| Endpoint | الطريقة | الاستخدام |
|---|---|---|
| `/api/admin/dinstar/discover` | GET | اكتشاف البوابة |
| `/api/admin/dinstar/status` | GET | جلب حالة المنافذ |
| `/api/admin/dinstar/cdr` | GET | جلب سجل المكالمات |
| `/api/admin/dinstar/capabilities` | GET | جلب قدرات الجهاز |
| `/api/admin/dinstar/ports/{port}/reset` | POST | إعادة تعيين منفذ |
| `/api/admin/dinstar/ports/{port}/ussd` | POST | إرسال كود USSD |
| `/api/admin/dinstar/ports/{port}` | GET | جلب معلومات منفذ |

## تدفق المكالمة PSTN

```
المستخدم يضغط "اتصال"
  → DinstarViewModel.selectOptimalPort(phoneNumber)
  → Best port chosen (e.g., Port 3 — سبأفون — 85%)
  → POST /api/pstn/dial { number, userId }
  → Backend: PstnCallService.dial()
    → DinstarLoadBalancer.getOptimalSlotBySignal()
    → PstnManager.dialGsm(number) → Asterisk AMI OriginateAction
    → Asterisk → PJSIP → DINSTAR SIM Port 3
    → DinstarEventListener tracks: NewState → Hangup
    → CallHistoryService records CDR
  → Android: PstnCallScreen shows live call state
  → WebSocket delivers state updates in real-time
```

## Room Database

### dinstar_port_snapshots (V6)

| العمود | النوع | الوصف |
|---|---|---|
| portIndex | Int PK | رقم المنفذ (0-7) |
| radioType | Text | GSM/UMTS/LTE |
| registrationState | Text | REGISTERED/UNREGISTERED |
| callState | Text | IDLE/RINGING/ACTIVE |
| signalPercent | Int | 0-100% |
| signalRaw | Int | 0-31 (from Dinstar API) |
| gprsState | Text | ATTACH/DETACH |
| operatorName | Text | اسم المشغل |
| numberMasked | Text? | ••••1234 |
| simType | Text | YemenOperator enum |
| isHealthy | Boolean | مسجل + IDLE + إشارة≥20% |
| observedAt | Long | آخر ملاحظة |

### DinstarDao — 8 functions

- `getAllPorts()`, `getAvailablePorts()`, `getHealthyPorts()`
- `getPort(port)`, `getAverageSignal()`, `getRegisteredCount()`, `getActiveCallCount()`
- `insertPort()`, `insertPorts()`, `clearAll()`

## تأمين الاتصال

- الباكند يستخدم **HTTP Digest Auth** للاتصال بـ Dinstar (ليس Basic Auth)
- الشهادة SSL موقعة ذاتياً — الباكند يتقبلها (X509TrustManager)
- التطبيق الاندرويد يتصل بالباكند فقط (وليس مباشرة بـ Dinstar)
- الباكند يفرض: `DINSTAR must use HTTP(S) on a private management address`
