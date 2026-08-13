# تشخيص جذري — مشكلة `INVALID_REQUEST` ومشاكل الاتصال/الـ IP

> التاريخ: 2026-08-13 — فحص حرفي للملفات الفعلية داخل `RED_Ultimate_V1-main/RED_Ultimate/`.

---

## 1) فهم عميق للمشروع

المشروع **RED Ultimate** (العلامة "يونس / YOUNES") هو منصة مراسلة ومكالمات **محلية-أولًا (self-hosted)**، بديل خاص لواتساب/تلغرام/سيغنال:

| المكوّن | المسار | الدور |
|---|---|---|
| تطبيق أندرويد | `red-app/` (`com.red.sovereign`) | Compose + libsignal + Room/SQLCipher + WebRTC |
| الخادم الخلفي | `backend-server/` | Spring Boot (Kotlin)، يستمع داخليًا على **8080** |
| البوابة | `nginx.conf` | المنفذ الخارجي **8088** (HTTP) / **8443** (HTTPS) |
| لوحة الإدارة | `admin_dashboard/` | على 3000 خلف nginx |
| قواعد البيانات | Postgres + Mongo + Redis | عبر `docker-compose.yml` |
| التخزين | MinIO | وسائط مشفّرة |
| الصوت PSTN | DINSTAR / Asterisk | مسار منفصل |

**التسجيل** لا يعتمد على رقم هاتف/OTP: اسم مستخدم + كلمة مرور + اسم ظاهر + **مفاتيح libsignal للجهاز**، ثم ينتظر موافقة الإدارة (`PENDING`).

---

## 2) سلسلة الخطأ الكاملة (لماذا تظهر علامة التعجب الحمراء)

تتبّع حرفي من الضغطة على "إنشاء حساب" حتى الشاشة الحمراء:

```
1. AuthViewModel.register()  →  POST /api/auth/register
2. AuthController.register() →  RegistrationService.register()
3. أي فشل داخل الخدمة:
   - require(...)  →  IllegalArgumentException
   - حقل null     →  NullPointerException
4. AuthExceptionHandler:
   - IllegalArgumentException (بلا رسالة) → 400 {"error":"INVALID_REQUEST"}
   - NullPointerException              → 400 {"error":"INVALID_REQUEST"}
5. التطبيق AuthApi.post() يقرأ جسم الخطأ → ApiResult.Error(400, "INVALID_REQUEST")
6. AuthViewModel.localize("INVALID_REQUEST") → لا يوجد تطابق → يعرض النص الخام كما هو
7. AuthScreens.ErrorStatusScreen → أيقونة Icons.Default.Error (دائرة حمراء + علامة تعجب)
   + عنوان «تعذر إكمال العملية» + الوصف «INVALID_REQUEST»
```

**الخلاصة الجوهرية:** `INVALID_REQUEST` ليس رسالة خطأ حقيقية — بل **كود عام يبتلع السبب الفعلي**. الخادم يرمي `IllegalArgumentException` (برسالة فارغة) أو `NullPointerException`، والمعالج يحوّلها إلى هذا الكود **دون تسجيل أي أثر**، والتطبيق بدوره يعرضه كما هو لأن دالة الترجمة `localize()` لا تعرفه.

---

## 3) الأسباب الجذرية

### السبب الجذري 1 — معالج أخطاء يُخفي السبب (الأخطر)
في `AuthExceptionHandler.kt`:
- `IllegalArgumentException` → يعيد `error.message ?: "INVALID_REQUEST"` **دون تسجيل log**.
- `NullPointerException` → يعيد `"INVALID_REQUEST"` دائمًا **دون أي stack trace**.

أي عطل فعلي يصل إلى هنا يتحوّل إلى لغز لا يمكن تشخيصه من طرفي الخادم ولا الهاتف.

### السبب الجذري 2 — حقل `null` يمرّ للخدمة فيسبب NPE
DTO الطلب في `AuthDtos.kt` كانت بلا أي تحقق (لا `@NotBlank` ولا `@NotNull`). لو وصل حقل فارغ/مفقود (JSON مثل `"username": null`)، فسطر:
```kotlin
val username = request.username.trim().lowercase()
```
يرمي `NullPointerException` → يتحول مباشرة إلى `INVALID_REQUEST` بدل رسالة واضحة.

### السبب الجذري 3 — التطبيق لا يترجم الكود
دالة `localize()` في `AuthViewModel.kt` كانت تفتقد أي حالة لـ `INVALID_REQUEST` (ولا لـ `MALFORMED_JSON` ولا `INTERNAL_ERROR`)، فيصل النص الخام للمستخدم.

### السبب الجذري 4 — مشاكل الـ IP والاتصال (منفصلة عن `INVALID_REQUEST`)
النظام **كاملًا** مُبرمج على عنوان ثابت `192.168.1.50` في أكثر من مكان:

| الملف | القيمة الثابتة |
|---|---|
| `red-app/build.gradle.kts` | `RED_SERVER_URL` افتراضيًا `http://192.168.1.50:8088` |
| `.env.example` | `TLS_SAN_IP=192.168.1.50` |
| `.env.example` | `ALLOWED_ORIGINS=…192.168.1.50:8088` |
| `.env.example` | `TURN_PUBLIC_HOST=192.168.1.50` |
| `.env.example` | `MEDIASOUP_ANNOUNCED_IP=192.168.1.50` |
| `LocalServerDiscovery.kt` | قائمة البذور `10.0.2.2` / `192.168.1.50` |

النتيجة:
1. أي جهاز يُبنى دون تمرير `-PRED_SERVER_URL` يضرب `192.168.1.50` — غالبًا **ليس عنوان خادمك**.
2. **اكتشاف الشبكة محدود**: يمسح فقط شبكة `/24` المحلية ويطابق توقيع "يونس". إن كان الخادم على شبكة فرعية أخرى، أو عبر نفق، أو mDNS محجوبًا → فشل برسالة «لم يُعثر على خادم يونس».
3. **تعارض HTTP/HTTPS في release**: البناء release يمنع `http://` بينما الافتراضي http → أي APK release بلا عنوان https يفشل في كل الاتصالات.
4. **لا مدخل يدوي للعنوان** (كان) — المستخدم رهينة الاكتشاف التلقائي بلا سبيل لإدخال IP خادمه.

> ملاحظة مهمة: وجود `INVALID_REQUEST` تحديدًا يعني أن الهاتف **وصل فعلًا لخادم يونس** (وإلا لظهرت رسالة «تعذر الاتصال»). فالمشكلتان مترابطتان: مشاكل IP كانت تعيق الاتصال، وبعد الوصول يظهر فشل التسجيل الغامض.

---

## 4) الحلول المطبّقة الآن (تعديلات فعلية في الكود)

### ✅ إصلاح 1 — كشف السبب الحقيقي في الخادم (`AuthExceptionHandler.kt`)
- أُضيف **تسجيل log** لكل استثناء (warn للـ 4xx، و`error` مع stack trace للـ NPE).
- أُضيف معالج `HttpMessageNotReadableException` → `MALFORMED_JSON` (جسم JSON تالف).
- أُضيف معالج `MethodArgumentNotValidException` → يعيد أول رسالة حقل بوضوح.
- أُضيف معالج عام `Exception` → `500 INTERNAL_ERROR` (بدل تسريب خطأ Spring الافتراضي).

### ✅ إصلاح 2 — منع NPE من الحقول الفارغة (`AuthDtos.kt` + `AuthController.kt`)
- أُضيف `@NotBlank`/`@NotNull` على حقول `RegisterRequest` و`LoginRequest` و`DeviceEnrollmentRequest`.
- أُضيف `@Valid` في `register()` و`login()`.
- الآن الحقل الفارغ يُرفض برسالة مفهومة قبل وصوله للخدمة، بدل `INVALID_REQUEST`.

### ✅ إصلاح 3 — رسالة عربية مفهومة للمستخدم (`AuthViewModel.kt`)
- أُضيف ترجمة `INVALID_REQUEST` / `MALFORMED_JSON` / `VALIDATION_FAILED` → «الطلب غير صالح أو ناقص البيانات…».
- أُضيف ترجمة `INTERNAL_ERROR` → «حدث خطأ داخلي في الخادم…».

### ✅ إصلاح 4 — حل مشكلة الـ IP (كود + تشغيل)
**أ. إدخال عنوان الخادم يدويًا من التطبيق (جديد):**
- أُضيف زر «تحديد عنوان الخادم يدويًا» في شاشة الترحيب.
- دالة `AuthViewModel.setServerUrl()` تتحقق من الصيغة وتحفظها وتفعّلها فورًا.
- الآن يستطيع المستخدم كتابة `http://IP_خادمه:8088` مباشرة دون إعادة بناء، ودون انتظار الاكتشاف التلقائي.

**ب. عند البناء مرّر عنوانك الصحيح:**
```bash
./gradlew :app:assembleDebug -PRED_SERVER_URL=http://IP_الخادم:8088
```
- المنفذ الصحيح هو **8088** (بوابة nginx) وليس 8080 (داخلي فقط).
- لنسخة release استخدم `https://` + المنفذ 8443 مع شهادة صالحة.

**ج. حدّث `.env` عند نشر الخادم** (غيّر كل `192.168.1.50` إلى IP خادمك الفعلي):
```env
TLS_SAN_IP=IP_الخادم
ALLOWED_ORIGINS=http://localhost:8088,http://127.0.0.1:8088,http://IP_الخادم:8088
TURN_PUBLIC_HOST=IP_الخادم
MEDIASOUP_ANNOUNCED_IP=IP_الخادم
```
- تأكد أن الهاتف والخادم على نفس الشبكة، وجدار الحماية يسمح بـ 8088 (و8443 و40000-40100/udp للمكالمات).

---

## 5) الخلاصة المباشرة

| العرض | السبب | الحل |
|---|---|---|
| علامة تعجب حمراء + `INVALID_REQUEST` | معالج أخطاء يبتلع السبب + NPE/استثناء داخلي غير مسجّل | ✅ أُصلح (تسجيل + أكواد واضحة + تحقق حقول) |
| لا تظهر رسالة مفهومة | `localize()` لا يترجم الكود | ✅ أُصلح |
| مشاكل IP والاتصال | عنوان افتراضي ثابت `192.168.1.50` + اكتشاف محدود + تعارض http/release | أعد البناء بـ `-PRED_SERVER_URL=http://IP_خادمك:8088` |

**بعد إعادة بناء الخادم** (`backend-server`) سترى السبب الحقيقي في سجلاته (`docker compose logs backend`) بدل الكود الغامض — وهذا هو أساس أي تشخيص لاحق.

---

## 6) كيف تتحقق بنفسك (خطوات عملية)

### على جهاز الخادم
```bash
# 1) هل الخادم يعمل؟
docker compose ps

# 2) هل البوابة تستجيب على 8088؟
curl -s http://localhost:8088/health/live

# 3) ما عنوان IP الخادم على الشبكة؟
ip addr show | grep "inet " | grep -v 127.0.0.1
```

### من هاتفك (على نفس الشبكة)
افتح المتصفح على هاتفك واذهب إلى:
```
http://IP_الخادم:8088/health/live
```
إن رأيت `{"brand":"YOUNES",...}` فالشبكة سليمة والبوابة تصل. ثم افتح التطبيق → «تحديد عنوان الخادم يدويًا» → أدخل نفس العنوان.

### تجربة التسجيل مباشرة (اختبار معزول)
```bash
curl -s -X POST http://localhost:8088/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","password":"a-strong-password","displayName":"تجربة","device":{"deviceName":"test","registrationId":42,"protocolDeviceId":1,"signedPreKeyId":7,"kyberPreKeyId":8,"identityKey":"'"$(head -c64 /dev/urandom | base64)"'","signedPreKey":"'"$(head -c64 /dev/urandom | base64)"'","kyberPreKey":"'"$(head -c1568 /dev/urandom | base64)"'","signedPreKeySignature":"'"$(head -c64 /dev/urandom | base64)"'","kyberPreKeySignature":"'"$(head -c64 /dev/urandom | base64)"'"}}'
```
- استجابة `201` + `PENDING` = التسجيل يعمل.
- أي `400` الآن يعيد **رسالة سبب حقيقية** (مثل `Username must be 3-32…`) وليس `INVALID_REQUEST` الغامض — وستظهر الرسالة أيضًا في `docker compose logs -f backend`.

