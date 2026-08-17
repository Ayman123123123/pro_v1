# دليل إعداد Cloudflare Tunnel لمشروع RED Sovereign

## الخطوة 1: إنشاء حساب Cloudflare مجاني
1. اذهب إلى https://dash.cloudflare.com
2. أنشئ حساباً مجانياً
3. أضف نطاقك (أو سجّل نطاق مجاني أولاً)

## الخطوة 2: تسجيل نطاق مجاني (إذا لم يكن لديك)
### الخيار الأفضل: freedomain.one
- اذهب إلى https://freedomain.one
- سجّل حساباً مجانياً
- اختر نطاق `.com` أو `.net` مجاني
- سيُعطيك nameservers — أضفها في Cloudflare

## الخطوة 3: إنشاء Tunnel في Cloudflare
1. في Cloudflare Dashboard: Zero Trust → Networks → Tunnels
2. اضغط "Create a tunnel"
3. اختر "Cloudflare Tunnel (cloudflared)"
4. اسم Tunnel: `red-sovereign`
5. اختر "Docker" كطريقة التثبيت
6. ستحصل على **Tunnel Token** — انسخه

## الخطوة 4: إضافة Public Hostnames
في صفحة إعداد الـ Tunnel، أضف:

### Route 1 — الباك اند الرئيسي:
| Field | Value |
|-------|-------|
| Subdomain | `api` (أو اتركه فارغاً) |
| Domain | `your-domain.com` |
| Path | (اتركه فارغاً) |
| Service Type | `HTTP` |
| URL | `nginx:80` |

### Route 2 — Asterisk WSS للمكالمات:
| Field | Value |
|-------|-------|
| Subdomain | `api` (نفس الأول) |
| Domain | `your-domain.com` |
| Path | `/ws/sip` |
| Service Type | `HTTPS` |
| URL | `pstn-gateway:8089` |
| No TLS Verify | ✅ مفعّل (Asterisk يستخدم شهادة ذاتية) |

## الخطوة 5: تفعيل WebSockets
في Cloudflare Dashboard:
- اذهب إلى domainك → Network → WebSockets
- تأكد أنها مفعّلة (مفعّلة افتراضياً)

## الخطوة 6: إعداد SSL/TLS
- اذهب إلى SSL/TLS → Overview
- اختر encryption mode: **Full**

## الخطوة 7: تشغيل Tunnel
أضف الـ token في `.env`:
```bash
CLOUDFLARE_TUNNEL_TOKEN=eyJhIjoi...your-token...
```

شغّل Tunnel:
```bash
docker compose --profile tunnel up -d cloudflared
```

تحقق من الاتصال:
```bash
docker logs red-cloudflared
# يجب أن ترى: "Registered tunnel connection" (4 اتصالات)
```

## الخطوة 8: اختبار الاتصال
من أي جهاز متصل بالإنترنت:
```bash
# اختبار API
curl https://your-domain.com/api/pstn/status

# اختبار WebSocket (Asterisk WSS)
# استخدم wscat أو متصفح:
# wscat -c wss://your-domain.com/ws/sip
```

## ملاحظات مهمة

### ⚠️ مشكلة RTP Media (الصوت)
Cloudflare Tunnel **لا يدعم UDP** — فقط HTTP/HTTPS/WebSocket这意味着:
- ✅ SIP Signaling (WSS) — يمر عبر Tunnel
- ❌ RTP Audio/Video (UDP) — لا يمر عبر Tunnel

**الحل**: تحتاج TURN server على سحابة مجانية:
- **Oracle Cloud Always Free** — VPS مجاني دائماً مع 10TB شهرياً
- **Google Cloud Free Tier** — $300 كريديت لمدة 90 يوماً

### ⚠️ قطع الاتصال في CGNAT
إذا لاحظت انقطاعات عشوائية، أزل التعليق عن `TUNNEL_TRANSPORT_PROTOCOL=http2` في `docker-compose.yml`.

### ⚠️ SIP Keepalives
يجب على التطبيق Android إرسال ping كل فترة لمنع قطع اتصال WebSocket.

## النتيجة النهائية
بمجرد الإعداد:
```
https://your-domain.com          → الباك اند (API + UI)
wss://your-domain.com/ws/sip     → Asterisk (WebRTC/SIP)
```

**التطبيق يعمل من أي مكان في العالم!**
