# أدوات DINSTAR + كيف نتحكم بالبوابة — UC2000-VE @192.168.11.1

SN `dd45-0104-2109-0014` — PKG `02240221` — 8 منافذ LTE — 2026-09-04

## 1. كيف كنا نتحكم بالجهاز (5 قنوات — كلها في الكود)

| # | القناة | الكود | التفاصيل |
|---|---|---|---|
| 1 | **HTTP API Digest** (الأساسية) | `backend-server/.../services/DinstarConnectionFactory.kt:65` + `DinstarHardwareService.kt:141` | `DispatchingAuthenticator digest+basic` + `CachingAuthenticator` + `trustAll + SPKI pin` + ترميز `,`→`%2C` (`:259`) + `GET get_port_info` / `POST get_status ["performance"]` / `POST get_cdr` |
| 2 | **Web session devckie + goform** (احتياط) | `DinstarHardwareService.kt:746` `WebGetPortInfoAll` + `:321` `triggerNumberLearning → /goform/HBPhoneNumberRuleAdd` | دخول نموذج → كوكي `devckie` → قراءة `operator` (API لا تصدره) + قواعد تعلم الرقم |
| 3 | **SIP/PJSIP** (الصوت) | `pstn-asterisk/docker-entrypoint.sh:189` يولّد `dinstar-gw-IP` (`:5062` trunk) + `gw-port-N` (`5060+N`) + `extensions.conf` contexts | `IP-auth identify` — بدون باسورد SIP على LAN |
| 4 | **AMI** (التحكم بالمكالمات) | `pstn/EnhancedPstnManager.kt:37` pool + `PstnManager.kt:43` + `InternalPstnController.kt:48` + `pstn-incoming.sh:26` | `Originate/Redirect/Hangup` + `RED_GW/RED_PORT_INDEX/RED_CALL_ID` |
| 5 | **SMS/USSD/CDR** (الرسائل) | `DinstarHardwareService.kt:451` `sendSms/queryIncomingSms` + `:391` USSD + `:404` CDR POST + `GsmAlphabet.kt` ترميز AUTO | `GSM7BIT↔gsm-7bit` و `UCS2↔unicode` — العربية كانت `?????` قبل الاشتقاق |

## 2. دروس ميدانية مثبتة (2026-09-04)

- الفاصلة الخام في `info_type=a,b` ترد `401 Wrong Password` كاذبًا (MatrixSSL) — **لازم `%2C`**. الباسورد كان صحيحًا دائمًا (`admin123`).
- `--anyauth` وليس `--digest` (الأخير يرسل body فارغًا فيُحسب فشلًا ويؤدي للحظر).
- `GET get_status?maximum=10` ترد `403` — الصحيح `POST ["performance"]`. `get_cdr` POST حصرًا.
- الحظر `forbid to try again` على مستوى IP — الحل: IP جديد + إيقاف الباكند أولًا (كان يفيض 403 كل ثوانٍ).
- البوابة الحية `.1` موديل **8T** (LTE) — التصنيف التلقائي الجديد كشفها من `type=LTE`.

## 3. أقوى الأدوات (مراجع)

- **API Manual v202011 (PDF)** — حمّلناه هنا: `Dinstar-GSM-Gateway-HTTP-API-v202011.pdf` (المصدر dinstar.com/tools)
- **SMSBox Demo** — برنامج تجريبي للـ SMS/USSD من dinstar.com/tools (Windows)
- **dinstar-client (Java 17)** — `github.com/ZodicSlanser/dinstar-client` — عميل typed لكل endpoints + جدول Method→endpoint (مرجع التطابق مع عقدنا)
- **dinstar-uc-api (Python)** — `pip install dinstar-uc-api` — سكربتات `sms_send/query_ussd/get_cdr/get_status`
- **sashalenz/dinstar-api (Laravel)** — مرجع PHP + push webhooks
- **sngrep** — فاحص SIP حي: `apt install sngrep` ثم `sngrep -c` (تشخيص 183/200/ACK/RTP)
- **sipp** — اختبار حمل SIP: `sipp -sn uac 192.168.11.10:5060`

## 4. جدول Endpoints المعتمد (v202011 — مطابق لعقدنا)

| العملية | الطريقة |
|---|---|
| `send_sms` / `query_sms_result` / `query_sms_deliver_status` / `query_sms_in_queue` / `stop_sms` | POST,POST,POST,GET,GET |
| `query_incoming_sms?flag=all` (لا `unread` — يغيّر الحالة) | GET |
| `send_ussd` / `query_ussd_reply` | POST / GET |
| `get_port_info` (GET + `%2C`) / `set_port_info?action=reset/power/slot/CallForward` | GET |
| `get_cdr` / `get_status` | POST + JSON فقط |
| `GetSTKView` / `STKGo` / `GetSTKCurrFrameIndex` | خارج `/api/` |

## 5. ملاحظة MCP

لا يوجد مضيف MCP في هذه البيئة — البديل المعتمد هنا: هذا المجلد + `DinstarConnectionFactory` + سكربتات `scripts/` للفحص.
