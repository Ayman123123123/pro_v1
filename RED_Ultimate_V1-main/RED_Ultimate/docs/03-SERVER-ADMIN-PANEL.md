# 03 — السيرفر ولوحة الإدارة وتدفق البيانات

## Backend

المسار القانوني `backend-server/`، والحزم القانونية تحت `com.red.server`. تم حذف backendات وWebSocket handlers المكررة. البناء مستقل ويشارك `shared-proto` مع Android.

### حدود API الأساسية

| المجال | أمثلة المسارات |
|---|---|
| Auth | `/api/auth/register`, `/login`, `/refresh`, `/recover` |
| Devices/identity | `/api/devices`, `/api/identity/authority`, `/api/identity/directory/**` |
| PreKeys | `/api/devices/{id}/prekeys`, atomic consume من directory endpoint |
| Admin | `/api/admin/**`, `/api/master/admin/**` — `ROLE_ADMIN` |
| Social | `/api/feed/**` وواجهات follow/post/reaction بحسب controllers |
| Groups | `/api/groups/**` |
| Media/stories | `/api/media/**`, `/api/stories/**` |
| Calls/PSTN | call history وPSTN authorization/dial controllers |
| Messaging WS | `/ws/master` |
| Call signaling WS | `/ws/calls` |

المرجع النهائي للمسارات هو annotations في controllers، وليس ملفات README القديمة.

## Security chain

- endpoints العامة محدودة بالتسجيل/login/refresh/logout/recover وسلطة الهوية وhealth.
- `/api/**` يتطلب JWT.
- admin paths تتطلب `ROLE_ADMIN`.
- JWT filter يتحقق من حالة الحساب والجهاز المعتمد.
- WebSocket handshake يتحقق من JWT ويحقن هوية المرسل؛ لا يثق في sender ID من payload.
- Argon2id لكلمات المرور، recovery hashes فقط، refresh rotation/reuse detection.
- CORS من `ALLOWED_ORIGINS`، وليس wildcard.

قبل النشر العام ما تزال مطلوبة HttpOnly admin cookie وCSRF وإعادة مصادقة للإجراءات الخطرة وRBAC متعدد الأدوار.

## لوحة الإدارة

`admin_dashboard/` هي اللوحة الوحيدة القانونية. تُبنى بـ React/Vite/TypeScript وتقدم عبر Nginx. الوظائف المنفذة تشمل:

- login وتجديد access token.
- قائمة الحسابات المعلقة والموافقة/الرفض.
- عرض بصمات الأجهزة.
- PSTN enable/disable والحد اليومي.
- metrics/health حقيقية بدل أرقام demo.
- audit log وإجراءات أمان وبنية/SFU tabs.

اللوحة لا تدخل PostgreSQL/Mongo/Redis مباشرة؛ كل عملية تمر عبر backend authorization/audit.

## تدفق الموافقة

```text
Android register + public device material
 → users/user_devices = PENDING
 → Admin GET pending
 → Admin approve account/device
 → authority signs device fingerprint
 → Android login with device ID
 → access JWT + rotating refresh
 → REST/WebSocket authorized
```

رفض/تعليق/حظر الحساب يمنع المصادقة. إلغاء الجهاز يلغي refresh sessions الخاصة به.

## تدفق الرسالة

```text
Android libsignal encrypt
 → /ws/master binary protobuf
 → JWT-authenticated handler
 → authorization + UUID/payload validation
 → Mongo durable insert + conversation sequence
 → active target device sessions or offline queue
 → target ACK DELIVERED/READ
 → durable status update + sender notification
```

الخادم يرى routing metadata وciphertext فقط للمحادثات الخاصة.

## تدفق المنشور

```text
Android authenticated REST
 → FeedController validation/UUIDv7
 → Mongo post/reply/poll/reaction
 → cursor feed ALL/FOLLOWING/YEMEN
 → Android renders public content
```

هذه البيانات عامة/اجتماعية وليست libsignal E2EE.

## تدفق الوسائط والحالة

```text
Android ContentResolver streaming
 → authenticated multipart API
 → MIME/size/key validation
 → MinIO private object
 → Mongo story metadata + expiry
 → authenticated download/view
 → scheduled cleanup بعد 24 ساعة
```

عرض الصور/الفيديو النهائي، thumbnails وencrypted cache ما تزال بوابات مستقلة.

## تدفق DINSTAR

```text
Android gold phone
 → backend validates approved user + pstn_enabled + Yemen number
 → Redis atomic daily reservation
 → AMI Originate with UUID ActionID
 → Local/<number>@from-red-backend
 → restricted Asterisk dialplan
 → PJSIP DINSTAR gateway
```

فشل AMI يعيد عداد Redis. AMI داخل شبكة Compose فقط. لا تدّعي نتيجة GSM النهائية قبل أحداث AMI واختبار العتاد.

## Nginx وSFU

Nginx يمرر HTTP/WebSocket ويحافظ على upgrade headers. `/sfu` يذهب إلى mediasoup. SFU يتحقق من JWT ويدير الغرف والمنتجين والمستهلكين، لكنه يحتاج Android mediasoup client وTURN/announced-IP صحيحين لتجربة media كاملة.

## التشغيل والمراقبة

- health العام: `/health`.
- Spring actuator health داخلي/مقيد بحسب SecurityConfig.
- SFU health: `/sfu-health` عبر Nginx.
- السجلات: `docker compose logs`؛ لا تطبع secrets أو plaintext.
- `LOCAL_FIRST_RUN_AR.md` هو دليل Alpha المحلي.
