

```
backend-server/src/main/resources/application.yml
.env.example
```

---

## 🚨 الخطأ #1 (القاتل): HTTP Basic Auth بدل Digest Auth


```kotlin
// ❌ الكود الحالي:
val request = unsigned.newBuilder()
    .header("Authorization", Credentials.basic(gatewayUsername, gatewayPassword))
    .header("Accept", "application/json")
    .build()
```

### المشكلة:
`Credentials.basic()` يرسل **HTTP Basic Auth** (`Authorization: Basic YWRtaW46YWRtaW4=`)



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


```kotlin
// ❌ الكود الحالي:
fun queryCdr(): Map<String, Any?> = getJson("/api/query_cdr", emptyMap())
```

### المشكلة:
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


```kotlin
// ❌ الكود الحالي:
val response = postJson("/api/set_port_info", mapOf("action" to "reset", "port" to listOf(port)))
```

### المشكلة:
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
```

### المشكلة:
- لكن قد يعمل على HTTP أيضاً — يجب اختبار كلاهما

### ✅ الإصلاح في `.env`:
```env
```

---

## 🚨 الخطأ #5: Model hardcoded كـ 8T

### الموقع: عدة أماكن

```kotlin
// ❌ الكود الحالي:
```

```tsx
// ❌ الكود الحالي:
```

### المشكلة:

### ✅ الإصلاح:

---

## 📋 ملخص كل الأخطاء

| # | الملف | السطر | الخطأ | الأثر | الإصلاح |
|---|---|---|---|---|---|
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
```
