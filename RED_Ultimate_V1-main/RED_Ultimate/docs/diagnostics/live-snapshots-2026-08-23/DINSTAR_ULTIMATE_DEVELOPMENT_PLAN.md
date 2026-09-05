# 🚀 خطة التطوير الشاملة — مشروع RED Ultimate + DINSTAR

## 📋 ملخص المشروع

**المشروع:** RED Ultimate — تطبيق اتصالات متكامل (VoIP/GSM/Chat/Groups)  
**الجهاز:** DINSTAR UC2000-VE-8G (مُعلَّب كـ 8G لكن Userboard L2 يشير لـ LTE)  
**الـ IP:** 192.168.11.1  
**الفيرموير:** 04240302 (2025-08-15)  
**الـ Backend:** Kotlin/Spring Boot  
**الـ Android App:** Kotlin/Jetpack Compose  
**الـ Admin Dashboard:** React/TypeScript  

---

## 🎯 الأهداف الرئيسية

1. **إصلاح API DINSTAR** — حل مشكلة المصادقة 401/403
2. **تنظيف المشاريع المعزولة** — دمج/إصلاح ملفات `app/` و `android/`
3. **تفعيل WebSocket** — تسجيل `/ws/dinstar` في Backend
4. **تفعيل مسار dial()** — ربطه بالواجهة لاستخدام الحدود اليومية
5. **ربط الشاشات المعزولة** — SMS والمكالمات الواردة
6. **إصلاح الترميز** — تعليقات VoiceMessageViewModel
7. **إنشاء بيئة تشغيل** — docker-compose + .env

---

## 🔴 الأولوية القصوى — إصلاح API DINSTAR

### المشكلة: 401/403 على جميع endpoints

```
get_port_info → 401 "Wrong Password"
get_status    → 403
get_cdr       → 403
set_port_info → 403
```

### 🔍 الأسباب المحتملة (3 مشاكل متراكبة):

| # | المشكلة | الدليل | الاحتمال |
|---|---------|--------|----------|
| 1 | استخدام `--digest` بدل `--anyauth` | وثائق Dinstar الرسمية تستخدم `--anyauth` | 70% |
| 2 | إرسال GET بدل POST لبعض endpoints | `get_status` و `get_cdr` يحتاجان POST+JSON | 60% |
| 3 | كلمة مرور admin تغيرت أو firmware bug | 401 على admin:admin | 50% |
| 4 | firmware 04240302 مخصص لـ 8T لا 8G | Userboard L2 + VoLTE على جهاز GSM | 40% |

### ✅ خطوات الإصلاح بالترتيب:

#### الخطوة 1: اختبار `--anyauth` (الأرجح)

```powershell
# اختبار المصادقة الصحيحة
curl.exe -sk --anyauth -u admin:admin --max-time 15 `
  -w "`nHTTP=%{http_code}" `
  "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"

# إذا فشل — جرب HTTP بدل HTTPS
curl.exe -sk --anyauth -u admin:admin --max-time 15 `
  -w "`nHTTP=%{http_code}" `
  "http://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"
```

#### الخطوة 2: اختبار POST للـ endpoints

```powershell
# get_status — POST مع JSON body
curl.exe -sk --anyauth -u admin:admin `
  -d '["performance"]' `
  -H "Content-Type: application/json" `
  --max-time 15 -w "`nHTTP=%{http_code}" `
  "https://192.168.11.1/api/get_status"

# get_cdr — POST مع JSON body  
curl.exe -sk --anyauth -u admin:admin `
  -d '{"port":[0,1,2,3,4,5,6,7]}' `
  -H "Content-Type: application/json" `
  --max-time 15 -w "`nHTTP=%{http_code}" `
  "https://192.168.11.1/api/get_cdr"
```

#### الخطوة 3: فحص نوع المصادقة الفعلي

```powershell
# إرسال بدون credentials لرؤية WWW-Authenticate header
curl.exe -sk -v --max-time 10 `
  "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1 |
  Select-String -Pattern "WWW-Authenticate|HTTP/|realm|nonce|qop|algorithm"
```

#### الخطوة 4: التحقق من كلمة المرور في الويب

1. افتح `https://192.168.11.1` في المتصفح
2. سجل الدخول بـ admin:admin
3. إذا فشل → كلمة المرور تغيرت
4. اذهب إلى **System → Setting** وتحقق/غيّر كلمة المرور

#### الخطوة 5: التحقق من إعدادات Basic Configuration

1. اذهب إلى **Mobile Configuration → Basic Configuration**
2. تأكد من:
   - **API Version** = "New Version" ✅
   - **Remote API Enable** = Yes (للـ Old API فقط)
   - **API Server Port** = 0 أو فارغ (للـ New API)
3. أعد تشغيل الجهاز بعد أي تغيير

#### الخطوة 6: إذا فشلت كل الخطوات

قد يكون firmware خاطئ (04240302 مخصص لـ 8T لا 8G):

```
جهازي UC2000-VE-8G لكن Userboard Version = B4.11.19.14L2 (LTE).
هل firmware 04240302 صحيح لـ HWID 7036-cf4b-3125؟
```

اتصل بـ Dinstar Support: support@dinstar.com

---

## 🟠 الأولوية الثانية — تنظيف المشاريع المعزولة

### الملفات الميتة (لا تُترجم ولا تُختبر):

| الملف | المشكلة | الحل |
|-------|---------|------|
| `app/.../DinstarDashboardUI.kt` | يستورد `com.red.features.dinstar` غير الموجود + علامات اقتباس مفردة | **حذف أو إصلاح** |
| `app/.../DinstarLiveMonitor.kt` | يستورد نفس الحزمة المفقودة + IP وcredentials مثبتة | **حذف أو إصلاح** |
| `android/.../PstnSipEngine.kt` | خارج البناء + credentials مكتوبة | **حذف أو دمج** |

### ✅ الخطوات:

1. **إنشاء `settings.gradle.kts` مُحدَّث**:

```kotlin
rootProject.name = "RED_Ultimate"

// المشروع الرئيسي
include(":app")
project(":app").projectDir = file("red-app")

// Backend
include(":backend-server")

// ⚠️ حذف المشاريع المعزولة أو دمجها
// include(":android")  // ← معزول، يحتاج إصلاح
// include(":legacy-app")  // ← معزول
```

2. **إصلاح أو حذف الملفات الميتة**:

```bash
# اختيار 1: حذف (إذا كانت نسخ قديمة)
rm -rf android/features/pstn/PstnSipEngine.kt
rm -rf app/src/.../DinstarDashboardUI.kt
rm -rf app/src/.../DinstarLiveMonitor.kt

# اختيار 2: دمج (إذا كانت مفيدة)
# نقل DinstarDashboardUI.kt → red-app/src/.../features/dinstar/
# تصحيح imports وإزالة الاعتماد على الحزمة المفقودة
```

---

## 🟡 الأولوية الثالثة — تفعيل WebSocket

### المشكلة:

```kotlin
// WebSocketConfig.kt — لا يوجد /ws/dinstar!
override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
    registry.addHandler(masterWebSocketHandler, "/ws/master")
    registry.addHandler(callWebSocketHandler, "/ws/calls")
    // ❌ MISSING: /ws/dinstar
}
```

### ✅ الحل:

```kotlin
// في WebSocketConfig.kt — إضافة:
@Value("\${dinstar.ws.enabled:true}")
private val dinstarWsEnabled: Boolean = true

override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
    registry.addHandler(masterWebSocketHandler, "/ws/master")
    registry.addHandler(callWebSocketHandler, "/ws/calls")
    
    // ✅ NEW: WebSocket للـ DINSTAR
    if (dinstarWsEnabled) {
        registry.addHandler(dinstarWebSocketHandler, "/ws/dinstar")
            .setAllowedOrigins("*")
    }
}
```

### إنشاء DinstarWebSocketHandler:

```kotlin
@Component
class DinstarWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val dinstarFleetService: DinstarFleetService
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        // إرسال الحالة الأولية
        val status = dinstarFleetService.getFleetStatus()
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(status)))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val command = objectMapper.readTree(message.payload)
        when (command["type"].asText()) {
            "refresh" -> {
                val status = dinstarFleetService.getFleetStatus()
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(status)))
            }
            "resetPort" -> {
                val portIndex = command["portIndex"].asInt()
                dinstarFleetService.resetPort(portIndex)
            }
            "sendUssd" -> {
                val portIndex = command["portIndex"].asInt()
                val code = command["code"].asText()
                dinstarFleetService.sendUssd(portIndex, code)
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.id)
    }

    // Broadcast للجميع
    fun broadcastStatus(status: DinstarFleetStatus) {
        val message = TextMessage(objectMapper.writeValueAsString(status))
        sessions.values.forEach { session ->
            if (session.isOpen) {
                session.sendMessage(message)
            }
        }
    }
}
```

---

## 🟢 الأولوية الرابعة — تفعيل مسار dial()

### المشكلة:

```kotlin
// AuthViewModel.kt — يستخدم bridge مباشرة، يتجاهل dial()
fun dialPstn(number: String) {
    pstnState = PstnState.Bridging
    viewModelScope.launch {
        val bridge = pstnApi.bridge(number)  // ← يستخدم bridge فقط
        // ❌ NEVER uses: pstnApi.dial(number, slotIndex)
    }
}
```

### ✅ الحل — إضافة خيار الاتصال المباشر:

```kotlin
// في PstnApi.kt — موجود بالفعل:
suspend fun dial(number: String, slotIndex: Int? = null): PstnCallResponse
suspend fun bridge(number: String): BridgeResponse
suspend fun hangup(callId: String, port: Int = -1): Boolean

// في AuthViewModel.kt — إضافة دالة dialPstnDirect:
fun dialPstnDirect(number: String, slotIndex: Int? = null) {
    pstnState = PstnState.Dialing
    viewModelScope.launch {
        try {
            val response = pstnApi.dial(number, slotIndex)
            if (response.status == "SUCCESS") {
                pstnState = PstnState.Started(
                    callType = "gsm",
                    callId = response.callId.hashCode(),
                    slot = response.slot
                )
                // تحديث الحدود اليومية
                _dailyPstnUsage.value = response.usedToday
                _dailyPstnLimit.value = response.dailyLimit
            } else {
                pstnState = PstnState.Error(response.status)
            }
        } catch (e: Exception) {
            pstnState = PstnState.Error(e.message ?: "Unknown error")
        }
    }
}

// تعديل UI ليعرض خيارين:
// 1. "اتصال WebRTC" (bridge) — للجودة العالية
// 2. "اتصال مباشر" (dial) — للسرعة + الحدود اليومية
```

### تعديل PstnCallScreen لعرض معلومات dial:

```kotlin
@Composable
fun PstnCallScreen(
    state: PstnState.Started,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    Column {
        // ... existing UI ...
        
        // إضافة معلومات المنفذ والحدود:
        if (state.slot >= 0) {
            Text(
                text = "منفذ DINSTAR: ${state.slot + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = AqyalGold
            )
        }
        
        // عرض الحدود اليومية:
        val usage = viewModel.dailyPstnUsage.collectAsState()
        val limit = viewModel.dailyPstnLimit.collectAsState()
        LinearProgressIndicator(
            progress = { usage.value.toFloat() / limit.value.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${usage.value} / ${limit.value} مكالمة يومية")
    }
}
```

---

## 🔵 الأولوية الخامسة — ربط الشاشات المعزولة

### 1. DinstarSmsScreen (غير مربوطة):

```kotlin
// في RedDashboard.kt — إضافة زر SMS:
// داخل قائمة الإجراءات أو شاشة DinstarAdminScreen

// خيار 1: زر داخل DinstarAdminScreen
IconButton(onClick = { navController.navigate("dinstar_sms") }) {
    Icon(Icons.Default.Sms, contentDescription = "SMS")
}

// خيار 2: Tab داخل شاشة الإدارة
TabRow(selectedTabIndex = selectedTab) {
    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
        Text("المنافذ")
    }
    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
        Text("SMS")
    }
    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
        Text("CDR")
    }
}
```

### 2. IncomingPstnCallScreen (غير مربوطة):

```kotlin
// إنشاء BroadcastReceiver للمكالمات الواردة
// أو استخدام FCM (Firebase Cloud Messaging)

class IncomingPstnCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        
        // إظهار شاشة المكالمة الواردة
        val activityIntent = Intent(context, IncomingPstnCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("number", number)
        }
        context.startActivity(activityIntent)
    }
}

// في AndroidManifest.xml:
<receiver android:name=".pstn.IncomingPstnCallReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.red.sovereign.INCOMING_PSTN_CALL" />
    </intent-filter>
</receiver>

// في Backend — إرسال broadcast عند مكالمة واردة:
@RestController
class PstnIncomingController {
    @PostMapping("/api/pstn/incoming")
    fun handleIncoming(
        @RequestParam number: String,
        @RequestParam port: Int
    ) {
        // إرسال FCM أو Broadcast للجهاز المستهدف
        fcmService.sendIncomingCallNotification(number, port)
    }
}
```

---

## 🟣 الأولوية السادسة — إصلاح ترميز التعليقات

### المشكلة:

```kotlin
// VoiceMessageViewModel.kt — تعليقات تالفة:
// 🎙️� التسجيل الصوتي المشفر
// التسجيل الصوتي
```

### ✅ الحل:

```kotlin
// حذف التعليقات التالفة وإعادة كتابتها:
/**
 * تسجيل صوتي مشفر بمعيار AES-256-GCM
 * - مفتاح عشوائي لكل رسالة
 * - نونس (Nonce) فريد لكل عملية تشفير
 * - SHA-256 لاشتقاق المفاتيح
 */

// أو استخدام ASCII فقط لتجنب مشاكل الترميز:
// Recording with AES-256-GCM encryption
// Random key per message, unique nonce per operation
```

---

## 📁 ملفات الإعدادات المطلوبة

### 1. `.env` — إعدادات البيئة:

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/red_ultimate
DATABASE_USER=red_user
DATABASE_PASS=secure_password

# JWT
JWT_SECRET=your_256_bit_secret_key_here
JWT_EXPIRATION=86400000

# DINSTAR
DINSTAR_ENABLED=true
DINSTAR_DEFAULT_IP=192.168.11.1
DINSTAR_DEFAULT_USER=admin
DINSTAR_DEFAULT_PASS=admin
DINSTAR_API_VERSION=new
DINSTAR_WS_ENABLED=true

# Asterisk / SIP
ASTERISK_WSS_URL=ws://pstn-gateway:8089/ws
ASTERISK_WSS_PORT=8089
SIP_LOCAL_PORT=5060

# TURN/STUN Servers
TURN_SERVER_URL=turn:red-turn.example.com:3478
TURN_USERNAME=red_turn_user
TURN_PASSWORD=red_turn_pass

# WebSocket
WS_DINSTAR_ENABLED=true
WS_MASTER_ENABLED=true
WS_CALLS_ENABLED=true

# Rate Limiting
PSTN_DAILY_LIMIT=50
PSTN_RATE_LIMIT_PER_MINUTE=5

# Logging
LOG_LEVEL=INFO
LOG_DINSTAR_DEBUG=true
```

### 2. `docker-compose.yml` — البنية التحتية:

```yaml
version: '3.8'

services:
  # Database
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: red_ultimate
      POSTGRES_USER: red_user
      POSTGRES_PASSWORD: ${DATABASE_PASS}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  # Redis (for sessions/caching)
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  # Backend API
  backend:
    build: ./backend-server
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DATABASE_URL=jdbc:postgresql://postgres:5432/red_ultimate
      - REDIS_URL=redis://redis:6379
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis

  # Asterisk / PSTN Gateway
  asterisk:
    build: ./asterisk
    environment:
      - ASTERISK_WSS_PORT=8089
      - DINSTAR_IP=${DINSTAR_DEFAULT_IP}
    ports:
      - "5060:5060/udp"
      - "5061:5061/tcp"
      - "8088:8088"
      - "8089:8089"
      - "10000-20000:10000-20000/udp"
    volumes:
      - ./asterisk/config:/etc/asterisk

  # TURN Server (coturn)
  turn:
    image: coturn/coturn:latest
    ports:
      - "3478:3478"
      - "3478:3478/udp"
      - "5349:5349"
      - "5349:5349/udp"
    environment:
      - TURN_SECRET=${TURN_PASSWORD}

  # Nginx (Reverse Proxy)
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf
      - ./nginx/ssl:/etc/nginx/ssl
    depends_on:
      - backend
      - asterisk

volumes:
  postgres_data:
```

---

## 📊 خريطة المشروع المُحدَّثة

```
RED_Ultimate/
├── red-app/                    ← Android App (Kotlin/Compose)
│   ├── src/main/java/com/red/sovereign/
│   │   ├── features/
│   │   │   ├── dinstar/
│   │   │   │   ├── DinstarAdminScreen.kt     ✅ موجود
│   │   │   │   ├── DinstarSmsScreen.kt       ⚠️ معزول — يحتاج ربط
│   │   │   │   ├── IncomingPstnCallScreen.kt ⚠️ معزول — يحتاج ربط
│   │   │   │   ├── DinstarViewModel.kt       ✅ موجود
│   │   │   │   └── DinstarModels.kt          ✅ موجود
│   │   │   ├── pstn/
│   │   │   │   ├── PstnCallScreen.kt         ✅ موجود
│   │   │   │   └── PstnWebRtcManager.kt      ✅ موجود
│   │   │   └── ...
│   │   └── ...
│   └── build.gradle.kts
│
├── backend-server/             ← Backend (Kotlin/Spring Boot)
│   ├── src/main/kotlin/com/red/server/
│   │   ├── controllers/
│   │   │   ├── DinstarController.kt          ✅ موجود
│   │   │   ├── DinstarFleetController.kt     ✅ موجود
│   │   │   ├── PstnCallController.kt         ✅ موجود
│   │   │   └── ...
│   │   ├── services/
│   │   │   ├── DinstarHardwareService.kt     ✅ موجود
│   │   │   ├── DinstarLoadBalancer.kt        ✅ موجود
│   │   │   ├── DinstarFleetService.kt        ✅ موجود
│   │   │   └── ...
│   │   ├── websocket/
│   │   │   ├── DinstarWebSocketHandler.kt    ⚠️ موجود لكن غير مسجل!
│   │   │   └── WebSocketConfig.kt            ❌ يحتاج إضافة /ws/dinstar
│   │   └── ...
│   └── ...
│
├── admin_dashboard/            ← React Admin Dashboard
│   └── src/pages/
│       ├── PstnManagement.tsx    ✅ موجود
│       └── DinstarControl.tsx    ✅ موجود
│
└── docker-compose.yml          ← ⚠️ يحتاج تحديث
```

---

## ⏱️ الجدول الزمني المقترح

| اليوم | المهمة | الوقت |
|-------|--------|-------|
| 1 | إصلاح API DINSTAR (6 خطوات) | 2-3 ساعات |
| 2 | تفعيل WebSocket + اختبار | 2 ساعات |
| 3 | تنظيف المشاريع المعزولة | 1-2 ساعات |
| 4 | تفعيل dial() + ربط UI | 2-3 ساعات |
| 5 | ربط SMS + المكالمات الواردة | 2 ساعات |
| 6 | إصلاح الترميز + docker-compose | 1-2 ساعات |
| 7 | اختبار شامل + توثيق | 2 ساعات |

**المجموع: 12-15 ساعة عمل**

---

## 📞 اتصال Dinstar Support

إذا فشلت كل الحلول:

```
To: support@dinstar.com
Subject: API Authentication Issue — UC2000-VE-8G — HWID 7036-cf4b-3125

Device Information:
- Model: UC2000-VE-8G (labeled) / UC2000-VE Business (firmware)
- Serial Number: dd45-1014-8440-0030
- Hardware ID: 7036-cf4b-3125
- MAC Address: F8-A0-3D-88-E6-B4
- Current Firmware: 04240302 (2025-08-15)
- Userboard Version: B4.11.19.14L2
- Hardware Version: PCB 27

Issue:
We have enabled "New Version API" in Mobile Configuration → Basic Configuration,
but the HTTP/JSON API endpoints return authentication errors:
- /api/get_port_info → 401 "Wrong Password" (using admin:admin)
- /api/get_status → 403
- /api/get_cdr → 403
- /api/set_port_info → 403

We have tried:
- --anyauth and --digest
- HTTP and HTTPS
- POST and GET
- Multiple reboots

Questions:
1. Is firmware 04240302 correct for HWID 7036-cf4b-3125 on 8G?
2. Does Userboard L2 indicate LTE module on GSM device?
3. Is there a known authentication bug in this firmware?
```
