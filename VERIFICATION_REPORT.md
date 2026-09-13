**التاريخ**: 2026-08-08  

---

## السبب الجذري لكل أخطاء 401/403

| # | السبب | التأثير | الحالة |
|---|-------|---------|--------|
| 3 | **`query_cdr` كان GET بدل POST** | الـ endpoint يتطلب POST مع JSON body | ✅ مُصلح (سابقاً) |
| 4 | **`set_port_info` كان POST بدل GET** | الـ endpoint يتطلب GET مع query params | ✅ مُصلح (سابقاً) |
| 5 | **المنفذ 80 + HTTP بدل 443 + HTTPS** | الاتصال يذهب للمنفذ الخاطئ | ✅ مُصلح (سابقاً) |

---

## التغييرات المُطبّقة

### 1. `build.gradle.kts` — إضافة مكتبة Digest Auth
```diff
```
- المكتبة: `io.github.rburgst:okhttp-digest:3.1.1` (أحدث إصدار، أكتوبر 2024)
- توفر: `DigestAuthenticator`, `BasicAuthenticator`, `DispatchingAuthenticator`, caching

**التغييرات الرئيسية:**

#### أ. مصادقة HTTP Digest + Basic مع caching
```kotlin
val credentials = Credentials(gatewayUsername, gatewayPassword)
val digestAuthenticator = DigestAuthenticator(credentials)
val basicAuthenticator = BasicAuthenticator(credentials)

val dispatchingAuthenticator = DispatchingAuthenticator.Builder()
    .with("digest", digestAuthenticator)
    .with("basic", basicAuthenticator)
    .build()

val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
```
- `DispatchingAuthenticator` يتعرف على نوع الـ challenge تلقائياً (Digest أو Basic)
- `CachingAuthenticatorDecorator` يمنع إعادة الـ challenge على كل طلب
- `AuthenticationCacheInterceptor` يخزّن المصادقة الناجحة

#### ب. SSL Trust للشهادات الموقعة ذاتياً
```kotlin
val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager { ... })
sslContext.init(null, trustAllCerts, SecureRandom())
.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
.hostnameVerifier { _, _ -> true }
```

#### ج. تصحيح API endpoints
| Endpoint | قبل (خاطئ) | بعد (صحيح) |
|----------|-------------|-------------|
| CDR | `getJson("/api/query_cdr")` | `postJson("/api/get_cdr", {port, maximum})` |
| Reset | `postJson("/api/set_port_info")` | `getJson("/api/set_port_info", {action, port})` |
| Port Info | ✅ كان صحيحاً | `getJson("/api/get_port_info", {port, info_type})` |

#### د. إضافة معامل `maximum` لـ CDR
```kotlin
fun queryCdr() = postJson("/api/get_cdr", mapOf("port" to (0..7).toList(), "maximum" to 100))
```

#### ه. تحسينات أخرى
- ✅ `log.error()` عند فشل المصادقة مع تفاصيل الـ challenge
- ✅ Timeouts محسّنة: connect=5s, read=10s, call=15s
- ✅ Defaults: port=443, scheme=https, username=admin, password=admin

### 3. `application.yml` — تحديث الإعدادات الافتراضية
```yaml
red:
```

### 4. `.env.example` — تحديث متغيرات البيئة
```
```

```diff
```

```diff
```

### 7. `RedSettingsScreen.kt` — تصحيح اسم الموديل (أندرويد)
```diff
```

  - إضافة قسم المصادقة (Digest vs Basic)
  - إضافة خطوة تفعيل New Version API
  - ملاحظة حول تناقض firmware (Userboard L2 + VoLTE على جهاز 8G)

---

## ملخص الاستيرادات الصحيحة لمكتبة okhttp-digest

| الاستيراد | الحزمة |
|-----------|--------|
| `AuthenticationCacheInterceptor` | `com.burgstaller.okhttp` |
| `CachingAuthenticatorDecorator` | `com.burgstaller.okhttp` |
| `DispatchingAuthenticator` | `com.burgstaller.okhttp` |
| `BasicAuthenticator` | `com.burgstaller.okhttp.basic` |
| `CachingAuthenticator` | `com.burgstaller.okhttp.digest` |
| `Credentials` | `com.burgstaller.okhttp.digest` |
| `DigestAuthenticator` | `com.burgstaller.okhttp.digest` |

---

## ما يزال يحتاج اختبار على الجهاز الفعلي

2. **تفعيل New Version API** من واجهة الجهاز: Mobile Configuration → Basic Configuration
3. **اختبار كل endpoint**:
   - `GET /api/get_port_info?port=0,1,2,3,4,5,6,7&info_type=type,imei,imsi,iccid,number,reg,slot,callstate,signal,gprs`
   - `GET /api/set_port_info?action=reset&port=0`
   - `POST /api/get_cdr` مع `{"port":[0,1,2,3,4,5,6,7],"maximum":100}`
5. **اختبار HTTP بديل**: إذا فشل HTTPS، جرب HTTP على المنفذ 80
