# 🔬 تحليل عميق شامل — مشكلة Dinstar UC2000-VE-8T New HTTP/JSON API

**التاريخ:** 2026-08-08  
**الجهاز:** UC2000-VE-8T (LTE) — HWID 7036-cf4b-3125  
**الفيرموير:** 04240302 (2025-08-15)  
**IP:** 192.168.11.1  

---

## 🧩 لغز الـ 401/403 — ماذا يحدث فعلاً؟

### النتائج المُلاحظة

| Endpoint | admin:admin | red_api:red_api123 | red_api:hash | admin:red_api123 |
|---|---|---|---|---|
| `get_port_info?port=0` (بدون info_type) | **404** | 401 | 401 | 401 |
| `get_port_info?port=0&info_type=slot,...` | **401** | 401 | 401 | 401 |
| `get_port_info?port=0&info_type=imei,...` | **401** | 401 | 401 | **403** |
| `get_status?maximum=10` | **403** | **403** | **403** | **403** |
| `get_cdr?port=0` | **403** | **403** | **403** | **403** |
| `set_port_info?action=reset&port=0` | **403** | **403** | **403** | **403** |

### 🔑 الخلاصة الحرجة

**الـ API Handler الجديد موجود ونشط جزئياً** — لكن هناك **3 مشاكل متراكبة** تمنع العمل:

---

## 🚨 المشكلة #1: استخدام `--digest` بدلاً من `--anyauth`

### الأدلة من وثائق Dinstar الرسمية

**كل الأمثلة الرسمية في وثائق Dinstar تستخدم `--anyauth` وليس `--digest`:**

```bash
# ✅ من وثائق Dinstar الرسمية (v201910 و v202011):
curl -k --anyauth -u admin:admin -d '{"port":[2,3]}' -H "Content-Type: application/json" https://gateway_ip/api/get_cdr

curl -k --anyauth -u admin:admin -d '["performance"]' -H "Content-Type: application/json" https://gateway_ip/api/get_status

# ❌ ما كنت تستخدمه:
curl -sk --digest -u admin:admin "https://192.168.11.1:443/api/get_status?maximum=10"
```

### لماذا هذا مهم جداً؟

- **`--anyauth`**: يرسل الطلب أولاً بدون مصادقة، يقرأ `WWW-Authenticate` header من استجابة الـ 401، ثم يحدد نوع المصادقة تلقائياً (Basic أو Digest) ويعيد الإرسال بالطريقة الصحيحة
- **`--digest`**: يُجبر curl على استخدام Digest auth فقط — إذا كان السيرفر يتوقع Basic auth أو نوع مختلف، سيفشل

**الجهاز قد يكون يستجيب بـ `WWW-Authenticate: Basic` لبعض endpoints و `Digest` لأخرى.** استخدام `--digest` يمنع curl من التكيف مع نوع المصادقة الفعلي.

---

## 🚨 المشكلة #2: استخدام GET بدلاً من POST لبعض الـ endpoints

### الأدلة من وثائق Dinstar الرسمية

الوثائق تُظهر أن **بعض الـ endpoints تتطلب POST مع JSON body** وليس GET مع query parameters:

```bash
# ✅ get_status = POST مع JSON body (ليس GET):
curl -k --anyauth -u admin:admin -d '["performance"]' -H "Content-Type: application/json" https://gateway_ip/api/get_status

# ✅ get_cdr = POST مع JSON body:
curl -k --anyauth -u admin:admin -d '{"port":[2,3]}' -H "Content-Type: application/json" https://gateway_ip/api/get_cdr

# ✅ get_port_info = GET مع query parameters (هذا صح):
https://gateway_ip/api/get_port_info?port=1,2,3&info_type=imei,imsi,iccid,...

# ✅ set_port_info = GET مع query parameters:
https://gateway_ip/api/set_port_info?port=1&action=reset
```

**كنت ترسل `get_status` و `get_cdr` كـ GET — هذا قد يفسر الـ 403!**

| Endpoint | الطريقة الصحيحة | ما كنت تستخدمه |
|---|---|---|
| `get_port_info` | **GET** + query params | GET + query params ✅ |
| `get_status` | **POST** + JSON body | GET + query params ❌ |
| `get_cdr` | **POST** + JSON body | GET + query params ❌ |
| `set_port_info` | **GET** + query params | GET + query params ✅ |
| `send_sms` | **POST** + JSON body | — |
| `send_ussd` | **POST** + JSON body | — |

---

## 🚨 المشكلة #3: كلمة مرور الـ admin قد تكون تغيرت

### الدليل

- `admin:admin` على `get_port_info?port=0&info_type=slot,...` → **401 "Wrong Password"**
- إذا كانت كلمة المرور صحيحة، يجب أن نحصل على 200 أو 403، وليس 401

### الاحتمالات

1. **كلمة مرور الـ web تم تغييرها** — واجهة الويب تستخدم session cookies (لا تتحقق من Digest auth)، لكن الـ API يستخدم Digest auth مباشرة
2. **هناك Bug في الـ firmware** — الـ Digest auth nonce/realm handling معطل
3. **الـ API credentials منفصلة** عن الـ web credentials في هذا الـ firmware المحدد

### ملاحظة مهمة من الوثائق

> "The username and password used for API is the same with those for logging in the Web interface of the gateway. For instance, if the programming language is curl and username/password is admin/admin, the parameter format is -u admin:admin"

**هذا يؤكد أن الـ credentials يجب أن تكون نفس واجهة الويب** — لكن إذا تغيرت كلمة المرور ولم تتحدث في الـ Digest auth database الداخلي، سيفشل الـ API.

---

## 🛠️ الحلول العملية — بالترتيب من الأسهل للأصعب

### ✅ الخطوة 1: جرّب `--anyauth` بدل `--digest`

```powershell
# جرب هذا أولاً — نفس أمثلة Dinstar الرسمية:
curl.exe -sk --anyauth -u admin:admin --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"
```

---

### ✅ الخطوة 2: جرّب HTTP بدل HTTPS (بورت 80)

بعض أمثلة Dinstar الرسمية تستخدم `http://` وليس `https://`:

```powershell
# عبر HTTP بورت 80:
curl.exe -sk --anyauth -u admin:admin --max-time 15 -w "`nHTTP=%{http_code}" "http://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"
```

---

### ✅ الخطوة 3: أرسل get_status و get_cdr كـ POST مع JSON body

```powershell
# get_status — POST مع JSON (الطريقة الصحيحة حسب الوثائق):
curl.exe -sk --anyauth -u admin:admin -d "[\"performance\"]" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_status"

# get_cdr — POST مع JSON (الطريقة الصحيحة حسب الوثائق):
curl.exe -sk --anyauth -u admin:admin -d "{\"port\":[0]}" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_cdr"

# get_cdr مع port range:
curl.exe -sk --anyauth -u admin:admin -d "{\"port\":[0,1,2,3,4,5,6,7]}" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_cdr"
```

---

### ✅ الخطوة 4: تحقق من كلمة المرور الفعلية

اذهب إلى **System Configuration → Setting** في واجهة الويب وتحقق/غيّر كلمة مرور الـ admin:

1. افتح `https://192.168.11.1` في المتصفح
2. اذهب إلى **System → Setting** أو **System → Security**
3. تحقق من الـ Admin Password
4. إذا تغيرت — استخدم كلمة المرور الجديدة مع curl
5. إذا لم تتغير — جرّب إعادة تعيينها إلى `admin` ثم احفظ وأعد التشغيل

```powershell
# جرّب مع كلمة المرور الفعلية (إذا كانت مختلفة):
curl.exe -sk --anyauth -u admin:YOUR_ACTUAL_PASSWORD --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"
```

---

### ✅ الخطوة 5: تحقق من إعدادات Basic Configuration

اذهب إلى **Mobile Configuration → Basic Configuration** وتحقق من:

| الإعداد | القيمة المطلوبة | ملاحظة |
|---|---|---|
| **API Version** | New Version | يجب أن يكون محدداً |
| **Remote API Enable** | Yes (إذا موجود) | للـ Old API فقط |
| **API Server Port** | 0 أو فارغ (للـ New API) | الـ New API يعمل على بورت الويب (443/80) |
| **VoLTE** | ENABLE | ✅ مفعل عندك |

**مهم:** بعد أي تغيير في Basic Configuration — **أعد تشغيل الجهاز** (System → Reboot)

---

### ✅ الخطوة 6: فحص.verbose لفهم الـ auth challenge

```powershell
# أرسل بدون أي auth لترى WWW-Authenticate header:
curl.exe -sk -v --max-time 10 "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1 | Select-String -Pattern "WWW-Authenticate|HTTP/|401|403|realm|nonce"
```

**هذا سيكشف:**
- نوع المصادقة المطلوب (Basic? Digest? كلاهما؟)
- الـ realm و nonce
- هل السيرفر يرسل challenge صحيح؟

---

### ✅ الخطوة 7: جرّب على بورت 80 تحديداً (HTTP بدون تشفير)

```powershell
# أحياناً الـ Digest auth يعمل على HTTP لكن يفشل على HTTPS بسبب TLS issues:
curl.exe -sk --anyauth -u admin:admin --max-time 15 -w "`nHTTP=%{http_code}" "http://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"

# مع verbose:
curl.exe -sk -v --anyauth -u admin:admin --max-time 15 "http://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1 | Select-String -Pattern "WWW-Authenticate|Authorization|HTTP/|401|403|200"
```

---

## 🧪 سكريبت اختبار شامل

```powershell
# ═══════════════════════════════════════════
# 🔬 DINSTAR API COMPREHENSIVE TEST
# ═══════════════════════════════════════════

$ip = "192.168.11.1"
$creds = @(
    @{name="admin:admin"; user="admin"; pass="admin"},
    @{name="admin:ACTUAL_PASS"; user="admin"; pass="admin"}  # ← غيّر لكلمة المرور الفعلية
)

# ── Test 1: Auth probe (no credentials) ──
Write-Output "`n═══ TEST 1: Auth Probe (no credentials) ═══"
curl.exe -sk -v --max-time 10 "https://${ip}/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1 | Select-String -Pattern "WWW-Authenticate|HTTP/1|realm|nonce|qop|algorithm"

# ── Test 2: --anyauth instead of --digest ──
Write-Output "`n═══ TEST 2: --anyauth on HTTPS ═══"
foreach ($c in $creds) {
    $r = curl.exe -sk --anyauth -u "$($c.user):$($c.pass)" --max-time 15 -w "`nHTTP=%{http_code}" "https://${ip}/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1
    $http = ($r | Select-String -Pattern 'HTTP=' | Select-Object -First 1).ToString()
    $body = ($r | Where-Object { $_ -notmatch 'HTTP=' } | Select-Object -Last 1).ToString().Trim()
    Write-Output "[$($c.name)] HTTPS/anyauth => $http | $body"
}

# ── Test 3: HTTP (port 80) ──
Write-Output "`n═══ TEST 3: HTTP (port 80) ═══"
foreach ($c in $creds) {
    $r = curl.exe -sk --anyauth -u "$($c.user):$($c.pass)" --max-time 15 -w "`nHTTP=%{http_code}" "http://${ip}/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1
    $http = ($r | Select-String -Pattern 'HTTP=' | Select-Object -First 1).ToString()
    $body = ($r | Where-Object { $_ -notmatch 'HTTP=' } | Select-Object -Last 1).ToString().Trim()
    Write-Output "[$($c.name)] HTTP/anyauth => $http | $body"
}

# ── Test 4: POST for get_status ──
Write-Output "`n═══ TEST 4: POST for get_status ═══"
foreach ($c in $creds) {
    $r = curl.exe -sk --anyauth -u "$($c.user):$($c.pass)" -d "[`"performance`"]" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "https://${ip}/api/get_status" 2>&1
    $http = ($r | Select-String -Pattern 'HTTP=' | Select-Object -First 1).ToString()
    $body = ($r | Where-Object { $_ -notmatch 'HTTP=' } | Select-Object -Last 1).ToString().Trim()
    Write-Output "[$($c.name)] POST get_status => $http | $body"
}

# ── Test 5: POST for get_cdr ──
Write-Output "`n═══ TEST 5: POST for get_cdr ═══"
foreach ($c in $creds) {
    $r = curl.exe -sk --anyauth -u "$($c.user):$($c.pass)" -d "{`"port`":[0]}" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "https://${ip}/api/get_cdr" 2>&1
    $http = ($r | Select-String -Pattern 'HTTP=' | Select-Object -First 1).ToString()
    $body = ($r | Where-Object { $_ -notmatch 'HTTP=' } | Select-Object -Last 1).ToString().Trim()
    Write-Output "[$($c.name)] POST get_cdr => $http | $body"
}

# ── Test 6: HTTP + POST ──
Write-Output "`n═══ TEST 6: HTTP + POST ═══"
foreach ($c in $creds) {
    $r = curl.exe -sk --anyauth -u "$($c.user):$($c.pass)" -d "[`"performance`"]" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "http://${ip}/api/get_status" 2>&1
    $http = ($r | Select-String -Pattern 'HTTP=' | Select-Object -First 1).ToString()
    $body = ($r | Where-Object { $_ -notmatch 'HTTP=' } | Select-Object -Last 1).ToString().Trim()
    Write-Output "[$($c.name)] HTTP+POST get_status => $http | $body"
}

Write-Output "`n═══ DONE ═══"
```

---

## 📋 خريطة القرار

```
ابدأ
  │
  ├─→ جرّب --anyauth بدل --digest
  │     ├─→ نجح؟ ✅ حل! (المشكلة كانت نوع المصادقة)
  │     └─→ فشل؟ ↓
  │
  ├─→ جرّب HTTP (بورت 80) بدل HTTPS (بورت 443)
  │     ├─→ نجح؟ ✅ حل! (المشكلة كانت TLS/HTTPS)
  │     └─→ فشل؟ ↓
  │
  ├─→ أرسل POST مع JSON body لـ get_status و get_cdr
  │     ├─→ نجح؟ ✅ حل! (المشكلة كانت HTTP method)
  │     └─→ فشل؟ ↓
  │
  ├─→ تحقق من كلمة المرور في System → Setting
  │     ├─→ تغيرت؟ استخدم الجديدة ↓
  │     └─→ لم تتغير؟ ↓
  │
  ├─→ فحص verbose بدون credentials لقراءة WWW-Authenticate
  │     ├─→ يُظهر realm/nonce؟ → Digest auth يعمل، المشكلة كلمة مرور
  │     └─→ لا يُظهر؟ → الـ API handler معطل على هذا البورت
  │
  ├─→ أعد تشغيل الجهاز بعد التحقق من Basic Configuration
  │     ├─→ نجح بعد الريبوت؟ ✅ حل! (كان يحتاج إعادة تشغيل)
  │     └─→ فشل؟ ↓
  │
  └─→ ⚠️ الـ firmware لا يدعم الـ New API بشكل كامل
        → تواصل مع Dinstar Support بالمعلومات أدناه
```

---

## 📧 رسالة Dinstar Support المحدّثة

```
To: support@dinstar.com
Subject: API Authentication Issue — UC2000-VE-8T — HWID 7036-cf4b-3125

Device Information:
- Model: UC2000-VE-8T (labeled UC2000-VE-8G / UC2000-VE Business)
- Serial Number: dd45-1014-8440-0030
- Hardware ID: 7036-cf4b-3125
- MAC Address: F8-A0-3D-88-E6-B4
- Current Firmware: Package/Software Version 04240302 (2025-08-15)
- Userboard Version: B4.11.19.14L2
- Hardware Version: PCB 27

Issue:
We have enabled "New Version API" in Mobile Configuration → Basic Configuration,
but the HTTP/JSON API endpoints return authentication errors:

- /api/get_port_info with --digest returns 401 "Wrong Password" (using admin:admin)
- /api/get_status returns 403 for all credentials
- /api/get_cdr returns 403 for all credentials
- /api/set_port_info returns 403 for all credentials

The API handlers ARE active (we get 401/403 not 404 "api is disable"),
but authentication fails even with correct web interface credentials.

We have tried:
- HTTP Digest auth (--digest) with admin:admin → 401
- Multiple credential combinations → 401/403
- Port 8000 (API not listening)

Questions:
1. Should we use --anyauth instead of --digest? (Your docs show --anyauth)
2. Does firmware 04240302 fully support New HTTP/JSON API (version ≥1102)?
3. Is there a known authentication issue with this firmware version?
4. Do we need a firmware upgrade to enable the New API?

We have config backup ready and can upgrade via Web UI or TFTP.
```

---

## 💡 ملخص القرار الذكي

**لا ترسل لـ Dinstar Support الآن.** لدينا 3 أشياء ملموسة لنجربها أولاً:

1. 🔴 **`--anyauth` بدل `--digest`** — هذا هو الفرق الأوضح بين أوامرك وأمثلة Dinstar الرسمية
2. 🔴 **POST + JSON body** لـ `get_status` و `get_cdr` — الوثائق تُظهر POST وليس GET
3. 🟡 **HTTP بورت 80** بدل HTTPS بورت 443 — بعض أمثلة الوثائق تستخدم HTTP

**احتمال نجاح أي من هذه = 70%+** لأنها أخطاء ملموسة في طريقة الاستدعاء مقارنة بالوثائق الرسمية.

**جرّبها بالترتيب ثم أخبرني بالنتائج.**
