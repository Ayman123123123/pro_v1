# 🔴 تحليل كود المشروع — الأخطاء الموجودة في DinstarHardwareService.kt

## 📂 الملفات المرتبطة بـ Dinstar

```
backend-server/src/main/kotlin/com/red/server/services/DinstarHardwareService.kt  ← 🔴 الملف الرئيسي
backend-server/src/main/kotlin/com/red/server/controllers/DinstarController.kt
backend-server/src/main/kotlin/com/red/server/infrastructure/dinstar/DinstarMasterClient.kt
backend-server/src/main/kotlin/com/red/server/pstn/DinstarEventListener.kt
backend-server/src/main/kotlin/com/red/server/pstn/DinstarLoadBalancer.kt
admin_dashboard/src/pages/tabs/DinstarTab.tsx
backend-server/src/main/resources/application.yml
.env.example
```

---

## 🚨 الخطأ #1 (القاتل): HTTP Basic Auth بدل Digest Auth

### الموقع: `DinstarHardwareService.kt` سطر 176

```kotlin
// ❌ الكود الحالي:
val request = unsigned.newBuilder()
    .header("Authorization", Credentials.basic(gatewayUsername, gatewayPassword))
    .header("Accept", "application/json")
    .build()
```

### المشكلة:
`Credentials.basic()` يرسل **HTTP Basic Auth** (`Authorization: Basic YWRtaW46YWRtaW4=`)

لكن Dinstar UC2000-VE يستخدم **HTTP Digest Auth** — هذا يفسر لماذا تحصل على **401 "Wrong Password"**!

الـ Basic auth يرسل كلمة المرور مشفرة base64 (قابلة لفك التشفير بسهولة) — Dinstar يتوقع Digest auth حيث كلمة المرور تُرسل كـ MD5 hash مع nonce.

### ✅ الإصلاح:

```kotlin
// ✅ يجب استخدام OkHttp Digest Auth:
// إضافة الـ dependency في build.gradle.kts:
// implementation("com.burgstaller:okhttp-digest:1.3")

// أو الأفضل — استخدام okhttp-auth interceptor:
val client = OkHttpClient.Builder()
    .authenticator(DigestAuthenticator(gatewayUsername, gatewayPassword))
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .callTimeout(12, TimeUnit.SECONDS)
    .build()
```

---

## 🚨 الخطأ #2: get_cdr و query_cdr تستخدم GET بدل POST

### الموقع: `DinstarHardwareService.kt` سطر 85

```kotlin
// ❌ الكود الحالي:
fun queryCdr(): Map<String, Any?> = getJson("/api/query_cdr", emptyMap())
```

### المشكلة:
وثائق Dinstar الرسمية تُظهر أن **get_cdr = POST مع JSON body**:
```bash
curl -k --anyauth -u admin:admin -d '{"port":[2,3]}' -H "Content-Type: application/json" https://gateway_ip/api/get_cdr
```

استخدام GET بدل POST يعطي **403 Forbidden** لأن الـ endpoint يتوقع POST.

### ✅ الإصلاح:
```kotlin
// ✅ الصحيح:
fun queryCdr(): Map<String, Any?> = postJson("/api/query_cdr", mapOf("port" to (0..7).toList()))
```

---

## 🚨 الخطأ #3: set_port_info يستخدم POST بدل GET

### الموقع: `DinstarHardwareService.kt` سطر 67

```kotlin
// ❌ الكود الحالي:
val response = postJson("/api/set_port_info", mapOf("action" to "reset", "port" to listOf(port)))
```

### المشكلة:
وثائق Dinstar تُظهر أن **set_port_info = GET مع query parameters**:
```
https://gateway_ip/api/set_port_info?port=1&action=reset
```

### ✅ الإصلاح:
```kotlin
// ✅ الصحيح:
val response = getJson("/api/set_port_info", mapOf("action" to "reset", "port" to port.toString()))
```

---

## 🚨 الخطأ #4: Configuration خاطئ في application.yml

### الموقع: `application.yml` سطور 32-36 و `.env.example`

```yaml
# ❌ الكود الحالي:
red:
  dinstar:
    ip: ${DINSTAR_IP:192.168.11.1}
    port: ${DINSTAR_PORT:80}        # ← بورت 80!
    scheme: ${DINSTAR_SCHEME:http}   # ← HTTP!
    username: ${DINSTAR_USERNAME:}
    password: ${DINSTAR_PASSWORD:}
```

### المشكلة:
- **بورت 80**: Dinstar API يعمل على **بورت 443 (HTTPS)** أو على نفس بورت الويب
- **scheme = http**: الـ Web UI يستخدم **HTTPS** (أنت تتصل بـ `https://192.168.11.1:443`)
- لكن قد يعمل على HTTP أيضاً — يجب اختبار كلاهما

### ✅ الإصلاح في `.env`:
```env
DINSTAR_IP=192.168.11.1
DINSTAR_PORT=443
DINSTAR_SCHEME=https
DINSTAR_USERNAME=admin
DINSTAR_PASSWORD=admin   # ← أو كلمة المرور الفعلية
```

---

## 🚨 الخطأ #5: Model hardcoded كـ 8T

### الموقع: عدة أماكن

```kotlin
// ❌ الكود الحالي:
"model" to "UC2000-VE-8T"   // سطر 50
"UC2000-VE-8T port must be 0-7"  // سطر 63
"UC2000-VE-8T", activeHost, configuredScheme  // سطر 130 في registerGateway
```

وفي `DinstarTab.tsx`:
```tsx
// ❌ الكود الحالي:
<h2>🔴 DINSTAR UC2000-VE-8T (GSM Gateway)</h2>
```

### المشكلة:
الجهاز فعلياً **UC2000-VE-8G** (GSM فقط) — التسمية خاطئة في الكود.

### ✅ الإصلاح:
استبدل كل `"UC2000-VE-8T"` بـ `"UC2000-VE-8G"` أو اجعله configurable.

---

## 📋 ملخص كل الأخطاء

| # | الملف | السطر | الخطأ | الأثر | الإصلاح |
|---|---|---|---|---|---|
| **1** | DinstarHardwareService.kt | 176 | **Basic Auth بدل Digest Auth** | 🔴 **401 على كل endpoint** | استخدام DigestAuthenticator |
| **2** | DinstarHardwareService.kt | 85 | **GET بدل POST لـ get_cdr** | 🟠 **403 على get_cdr** | تحويل لـ postJson |
| **3** | DinstarHardwareService.kt | 67 | **POST بدل GET لـ set_port_info** | 🟠 سلوك غير متوقع | تحويل لـ getJson |
| **4** | application.yml + .env | 32-36 | **بورت 80 + HTTP** | 🟡 فشل الاتصال | بورت 443 + HTTPS |
| **5** | عدة ملفات | — | **Model hardcoded 8T** | 🟢 عرض فقط | تغيير لـ 8G |

---

## ⚡ الخطأ #1 هو سبب كل شيء

**HTTP Basic Auth بدل Digest Auth** يفسر **كل** النتائج التي حصلت عليها:

| النتيجة | التفسير |
|---|---|
| `admin:admin` → **401 "Wrong Password"** | Basic auth header ≠ Digest auth المتوقع |
| كل الـ credentials → **401/403** | نفس المشكلة — نوع مصادقة خاطئ |
| `set_port_info?action=reset` → **"api is disable!!"** | الـ Old API handler يرد (لا يتحقق من نوع auth) |
| `get_port_info` مع `info_type` → **401** | الـ New API handler يتحقق ويرفض الـ Basic auth |

---

## 🛠️ الإصلاح العملي — خطوة بخطوة

### خطوة 1: إضافة OkHttp Digest Auth support

في `build.gradle.kts` أضف:
```kotlin
implementation("io.github.rburgst:okhttp-digest:1.3")
```

### خطوة 2: تعديل DinstarHardwareService.kt

استبدل الـ `execute` method بـ:
```kotlin
private val client = OkHttpClient.Builder()
    .authenticator(object : Authenticator {
        override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
            val challenge = response.challenges().find { it.scheme().equals("Digest", ignoreCase = true) }
            if (challenge != null) {
                // Use Digest auth
                return response.request.newBuilder()
                    .header("Authorization", DigestAuthHeader.compute(
                        gatewayUsername, gatewayPassword,
                        challenge.realm() ?: "",
                        challenge.scheme(),
                        response.request.method,
                        response.request.url.encodedPath,
                        challenge.nonce() ?: ""
                    ))
                    .build()
            }
            // Fallback to Basic auth
            return response.request.newBuilder()
                .header("Authorization", Credentials.basic(gatewayUsername, gatewayPassword))
                .build()
        }
    })
    .connectTimeout(3, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .callTimeout(12, TimeUnit.SECONDS)
    .build()
```

### خطوة 3: إصلاح queryCdr

```kotlin
// قبل:
fun queryCdr(): Map<String, Any?> = getJson("/api/query_cdr", emptyMap())

// بعد:
fun queryCdr(): Map<String, Any?> = postJson("/api/query_cdr", mapOf("port" to (0..7).toList()))
```

### خطوة 4: إصلاح resetPort

```kotlin
// قبل:
val response = postJson("/api/set_port_info", mapOf("action" to "reset", "port" to listOf(port)))

// بعد:
val response = getJson("/api/set_port_info", mapOf("action" to "reset", "port" to port.toString()))
```

### خطوة 5: تحديث .env

```env
DINSTAR_IP=192.168.11.1
DINSTAR_PORT=443
DINSTAR_SCHEME=https
DINSTAR_USERNAME=admin
DINSTAR_PASSWORD=admin
```
