# 🔍 تحليل موديل UC2000-VE-8G — الفرق الجوهري عن 8T

## ⚠️ هذا يغير كل شيء

| | **UC2000-VE-8G** (جهازك) | **UC2000-VE-8T** |
|---|---|---|
| **النوع** | **GSM فقط** (2G) | **LTE** (4G + fallback 3G/2G) |
| **الموديولات** | GSM modules ( quad-band 850/900/1800/1900MHz ) | LTE modules (EC25-EUX أو مشابه) |
| **VoLTE** | ❌ **غير مدعوم** — GSM لا يدعم VoLTE | ✅ مدعوم |
| **الـ Userboard** | نسخة GSM (بدون L2) | نسخة LTE (B4.11.19.14**L2**) |
| **API HTTP** | ✅ مدعوم (نفس الـ New API) | ✅ مدعوم |
| **SMPP** | ✅ | ✅ |
| **السعر** | ~$350-400 | ~$700+ |

---

## 🚨 التناقض الخطير في جهازك

جهازك مُعلَّب ومُسمَّى **UC2000-VE-8G** لكن البيانات الداخلية تقول شيء مختلف:

| البيان | القيمة | ما تدل عليه |
|---|---|---|
| **Device Model (firmware)** | `UC2000-VE Business` | تسمية عامة للسلسلة VE |
| **Hardware ID** | `7036-cf4b-3125` | يُفترض أنه = 8G لكن لم أجد mapping عام |
| **Userboard Version** | `B4.11.19.14L2` | **الـ L2 = LTE module** — هذا لا يتطابق مع 8G! |
| **VoLTE** | `ENABLE` | **لا يجب أن يكون موجود في 8G (GSM only)!** |
| **Mobile Type** | `GSM` | طبيعي لو الموديولات LTE في وضع fallback |

---

## 🧠 الاستنتاج الذكي

### الاحتمال #1 (الأكثر ترجيحاً — 70%):
**الجهاز فعلياً UC2000-VE-8T (LTE) لكن مُعلَّب/مُسمَّى خطأً كـ 8G**
- الدليل: Userboard `L2` + VoLTE ENABLE = هذا موديول LTE
- السبب: تسمية تسويقية إقليمية، أو خطأ في الملصق، أو firmware مشترك
- **التأثير على المشكلة**: لا فرق — API نفسه

### الاحتمال #2 (25%):
**الجهاز فعلياً 8G (GSM) لكن firmware الـ 04240302 مشترك بين 8G و 8T**
- Dinstar تستخدم نفس firmware base لكل سلسلة VE
- الـ Userboard `L2` يمكن أن تكون تسمية فقط
- **التأثير**: نفس API لكن سلوك الـ auth قد يختلف

### الاحتمال #3 (5%):
**موديولات هجينة** — بعض المنافذ GSM وبعضها LTE
- غير مرجح لكن ممكن في بعض الأسواق

---

## ✅ ما يتغير فعلاً في الحل

### لا يتغير شيء جوهري!

**الـ API هو نفسه لكل موديلات UC2000-VE** (8G, 8T, 4G, 4T):
- نفس endpoints: `/api/get_port_info`, `/api/send_sms`, `/api/get_cdr`, إلخ
- نفس المصادقة: HTTP Digest/Basic auth مع `admin:admin`
- نفس طريقة التفعيل: Mobile Configuration → Basic Configuration → New Version API

### لكن يتغير شيء واحد مهم:

**لو فعلاً 8G (GSM فقط):**
- الـ `VoLTE ENABLE` في واجهتك **anomaly** — GSM لا يدعم VoLTE
- هذا قد يعني أن الـ firmware **04240302** فيه bug — يعرض خيارات LTE حتى على جهاز GSM
- وهذا قد يفسّر لماذا الـ API معطل جزئياً! **Firmware مخصص لـ 8T شُغِّل على 8G**

---

## ⚡ خطوات إضافية مطلوبة

### 1. تأكيد نوع الموديولات فعلياً

افتح الجهاز في المتصفح واذهب إلى:
**System Information → Mobile Information**

وانظر إلى عمود **Type** لكل port:
- لو كله **GSM** → جهازك 8G فعلاً
- لو فيه **LTE** أو **WCDMA** → جهازك 8T

### 2. لو فعلاً 8G — الـ firmware قد يكون خاطئ!

هذا سيناريو خطير:
- Firmware `04240302` (2025-08-15) مُصمَّم لـ 8T
- شُغِّل على 8G بالخطأ
- الـ API handler موجود لكن الـ auth module لا يعمل بشكل صحيح لأنه يفترض LTE modules

**الحل**: تواصل مع Dinstar Support وقل:
> "جهازي UC2000-VE-8G لكن الـ Userboard Version يُظهر B4.11.19.14L2 (L2 = LTE). هل الـ firmware 04240302 صحيح لجهاز 8G؟ أحتاج firmware مخصص لـ HWID 7036-cf4b-3125."

### 3. جرّب الحلول السابقة أولاً

بغض النظر عن 8G أو 8T — الـ API هو نفسه. جرّب:

```powershell
# 1. Auth probe
curl.exe -sk -v --max-time 10 "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs" 2>&1 | Select-String -Pattern "WWW-Authenticate|HTTP/1|realm|nonce"

# 2. --anyauth (من الوثائق الرسمية)
curl.exe -sk --anyauth -u admin:admin --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"

# 3. POST لـ get_status
curl.exe -sk --anyauth -u admin:admin -d "[`"performance`"]" -H "Content-Type: application/json" --max-time 15 -w "`nHTTP=%{http_code}" "https://192.168.11.1/api/get_status"

# 4. HTTP بورت 80
curl.exe -sk --anyauth -u admin:admin --max-time 15 -w "`nHTTP=%{http_code}" "http://192.168.11.1/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs"
```

---

## 📋 ملخص

| السؤال | الجواب |
|---|---|
| هل بحثت عن UC2000-VE-8G بالتحديد؟ | ✅ نعم — ووجدت 10+ صفحات منتج ومواصفات |
| هل 8G يدعم New HTTP/JSON API؟ | ✅ **نعم** — كل موديلات UC2000-VE تدعمه |
| هل الـ API مختلف بين 8G و 8T؟ | ❌ **لا** — نفس endpoints ونفس المصادقة |
| هل firmware 04240302 صحيح لـ 8G؟ | ⚠️ **مشكوك** — Userboard L2 و VoLTE تشير لـ LTE |
| ما السؤال الحقيقي لدinstar؟ | "هل هذا firmware صحيح لـ HWID 7036-cf4b-3125 على 8G؟" |
