# شرح Cloudflare Tunnel لمشروع RED Sovereign

## ما الذي يفعله Cloudflare Tunnel؟

Cloudflare Tunnel هو خدمة مجانية تسمح لخادمنا بـ **نشر تطبيقنا على الإنترنت** بدون الحاجة إلى عنوان IP عام أو فتح بورتات في الفيروال.

## لماذا نحتاجه؟

### المشكلة الحالية
- خادمنا موجود في **شبكة داخلية CGNAT** (Sanaa, Yemen — مزود AS30873)
- **لا يمكننا فتح بورتات** في الراوتر (لا نملك صلاحية)')
- **لا نملك عنوان IP عام** — الخادم وراء CGNAT
- التطبيق لا يصلح إلا على **الشبكة المحلية** حالياً

### الحل: Cloudflare Tunnel
- ينشئ **نفق آمن** من خادمنا إلى شبكة Cloudflare العالمية
- **لا حاجة لفتح أي بورت** — الاتصال من الداخل للخارج فقط
- **بدون عنوان IP عام** — كل شيء يمر عبر Cloudflare
- **حماية DDoS + WAF + SSL** تلقائياً

## كيف يعمل؟

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐
│  Android App │────▶│  Cloudflare  │────▶│  خادمنا       │
│  (التطبيق)   │     │  (الشبكة)    │     │  (192.168.11.104)│
└─────────────┘     └──────────────┘     └───────────────┘
     عبر الإنترنت      نفق آمن            داخل الشبكة
```

1. **cloudflared** (برنامج خفيف) يعمل على خادمنا
2. ينشئ **اتصال outbound** فقط من الخادم إلى Cloudflare
3. Cloudflare يرسل traffic من التطبيق إلى خادمنا عبر النفق
4. **بدون فتح أي بورت** في الفيروال

## ما الذي ننشره عبر Tunnel؟

### الخدمات المطلوبة:

| الخدمة | البروتوكول | الوصف |
|--------|-----------|-------|
| **Nginx (HTTP)** | HTTP/HTTPS | الباك اند الرئيسي + API |
| **Asterisk (WSS)** | WebSocket Secure | اتصال WebRTC للتطبيق |
| **Nginx (API)** | HTTP | واجهة برمجية للتطبيق |

### تكوين DNS المطلوب:

```
# السيرفر الرئيسي
your-domain.com          → http://nginx:80        (HTTP)

# WebSocket للـ SIP (Asterisk)
your-domain.com/ws/sip   → ws://pstn-gateway:8089 (WebSocket)
```

## الخطوات المطلوبة منك:

### 1. إنشاء حساب Cloudflare مجاني
- اذهب إلى https://dash.cloudflare.com
- أنشئ حساباً مجانياً

### 2. تسجيل نطاق (Domain)
- يمكنك استخدام:
  - **freedomain.one** — نطاقات `.com` مجانية مع DNS
  - **ifreedomains.com** — نطاق + استضافة مجانية
  - **أي مسجّل نطاقات آخر**

### 3. إضافة النطاق إلى Cloudflare
- في Cloudflare Dashboard: Domains → Add Domain
- اتبع التعليمات لتغيير Nameservers

### 4. إنشاء Tunnel
- في Cloudflare: Zero Trust → Networks → Tunnels → Create Tunnel
- اختر "Cloudflared" كطريقة الإنشاء
- ستحصل على **Tunnel Token** — ضعه في `.env`:

```bash
CLOUDFLARE_TUNNEL_TOKEN=your-token-here
```

### 5. تكوين Public Hostnames
- أضف Public Hostnames في Cloudflare Dashboard:
  - `your-domain.com` → `http://nginx:80`
  - `your-domain.com/ws/sip` → `ws://pstn-gateway:8089`

### 6. تشغيل النفق
```bash
docker compose --profile tunnel up -d cloudflared
```

## مثال على النتيجة النهائية

بمجرد الإعداد، سيكون:

```
# التطبيق يتصل بـ:
https://your-domain.com          → الباك اند (API + UI)
wss://your-domain.com/ws/sip     → Asterisk (WebRTC/SIP)
```

**التطبيق يعمل من أي مكان في العالم!**

## ملاحظات أمنية

- ✅ **لا فتح بورتات** — الاتصال outbound فقط
- ✅ **تشفير SSL** تلقائياً
- ✅ **حماية DDoS** من Cloudflare
- ✅ **WAF** (Web Application Firewall) مجاني
- ✅ **بدون عنوان IP عام** — لا يمكن اختراق الخادم مباشرة

---

**الملخص:** Cloudflare Tunnel يحل مشكلة "الخادم وراء CGNAT" عن طريق إنشاء نفق آمن من الخادم إلى Cloudflare، بحيث يمكن للتطبيق الوصول إلى جميع الخدمات عبر الإنترنت بدون فتح أي بورت أو الحاجة لعنوان IP عام.
