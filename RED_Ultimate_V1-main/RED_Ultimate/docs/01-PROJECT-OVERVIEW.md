# 01 — نظرة شاملة على RED Ultimate V1

## الهدف

RED منصة محلية أولًا للمراسلة الاجتماعية والمكالمات، لا تستخدم رقم هاتف أو SIM أو بريد أو SMS/OTP لإنشاء الحساب. ينشئ المستخدم `username` وكلمة مرور واسم عرض، ويحصل على RED ID، ثم يبقى الحساب والجهاز `PENDING` حتى موافقة المسؤول.

هذه الوثيقة تصف **الفرع القانوني الحالي**، لا الادعاءات التاريخية في المصادر المرجعية.

## المكونات القانونية

| المكوّن | المسار | الحالة |
|---|---|---|
| Android | `red-app/` ويظهر كـ Gradle `:app` | يبني APK في CI |
| Backend | `backend-server/` | Spring Boot/Kotlin/JVM 21 |
| Protocol | `shared-proto/src/main/proto/red_protocol.proto` | المصدر الموحد |
| Admin | `admin_dashboard/` | React/Vite/Ant Design |
| SFU | `media-sfu/` | Node/mediasoup |
| PSTN | `pstn-asterisk/` | Asterisk/DINSTAR صوت فقط |
| Runtime | `docker-compose.yml` + `nginx.conf` | تشغيل محلي متعدد الخدمات |

`app/` وبقية وحدات Signal القديمة مصادر استخراج فقط وخارج graph الحالي. (`android/` و`app-android/` دُمجا في `red-app/` وحُذفا في 2026-08-19 — انظر `UNIFICATION_2026-08-19.md`.) المرجع الحاسم هو `settings.gradle.kts`.

## الفصل بين مساري المكالمات

```text
RED صوت/فيديو:
RED ID ↔ WebRTC ↔ backend signaling/SFU/TURN ↔ WebRTC ↔ RED ID
لا SIM، لا DINSTAR، ولا Asterisk.

DINSTAR صوت يمني:
Android ↔ backend authorization/limits ↔ AMI/Asterisk ↔ DINSTAR ↔ SIM ↔ الشبكة اليمنية
```

Asterisk لا يحتوي عميل RED WebRTC، ومنفذ AMI غير منشور للمضيف في Compose. الاتصال الوارد غير المربوط يُرفض بدل تحويله إلى وجهة وهمية.

## تدفق الحساب والهوية

1. Android يولد هوية libsignal وsigned pre-key وKyber محليًا.
2. تُحفظ المواد الخاصة مشفرة بمفتاح AES-GCM غير قابل للتصدير في Android Keystore.
3. يرسل التطبيق المواد العامة فقط مع التسجيل.
4. PostgreSQL يحفظ المستخدم والجهاز `PENDING`.
5. المسؤول يراجع البصمة ويوافق.
6. سلطة الهوية المحلية توقع شهادة جهاز ECDSA P-256.
7. تسجيل الدخول يصدر Access JWT وrefresh token دوارًا ومقيدًا بالجهاز.
8. أي reuse لعائلة refresh يلغي الجلسة.

لا يجوز أن تغادر مفاتيح الهوية أو pre-key الخاصة جهاز Android.

## تدفق الرسالة الخاصة

```text
Android sender
  → directory + certificate verification
  → atomic one-time EC/Kyber consumption عند إنشاء جلسة فقط
  → libsignal PQXDH + Double Ratchet
  → RedProtos.RedRED ciphertext
  → /ws/master
  → MongoDB durable sequence/offline queue
  → Android receiver device
  → decrypt locally
  → SENT / DELIVERED / READ ACK
```

الخادم لا يملك plaintext ولا يوفر بحثًا في محتوى المحادثة. المنشورات العامة ليست E2EE ويجب ألا تُوصف بأنها مشفرة طرفيًا.

## واجهة Android

خمس وجهات رئيسية:

1. المنشورات/نبض RED.
2. المحادثات والمجموعات.
3. إنشاء مركزي.
4. سجل مكالمات موحد.
5. هاتف DINSTAR ذهبي منفصل.

الوظائف التي لا تملك engine فعليًا تبقى معطلة وموضحة بـ«قيد الربط»؛ لا توجد نجاحات وهمية مقصودة.

## التشغيل المحلي

Nginx هو المدخل على المنفذ 80:

- `/api/` و`/health` → backend.
- `/ws/` → WebSockets في backend.
- `/sfu` و`/sfu-health` → mediasoup.
- `/` → لوحة الإدارة.

الخدمات المحلية: PostgreSQL وMongoDB وRedis وMinIO وbackend وadmin وSFU وTURN وAsterisk وNginx. راجع `LOCAL_FIRST_RUN_AR.md`.

## بوابات التحقق

بوابة CI الحالية تبني/تختبر backend، تبني Android مع dependency verification صارم، تبني لوحة الإدارة، تثبت SFU وتفحص JavaScript، وتولد إعداد Asterisk الآمن. هذه لا تستبدل:

- تشغيل Compose على جهاز حقيقي.
- اختبار هاتفين لـ E2EE/WebRTC.
- اختبار TURN بين شبكتين.
- اختبار DINSTAR/Yemen Mobile/Sabafon/YOU على العتاد.
- نسخ احتياطي واستعادة وضغط وأمن وRelease signing.

## وثائق مرتبطة

- [02-DATABASES.md](02-DATABASES.md)
- [03-SERVER-ADMIN-PANEL.md](03-SERVER-ADMIN-PANEL.md)
- [04-APPS.md](04-APPS.md)
- [../W0_MODULE_BOUNDARIES.md](../W0_MODULE_BOUNDARIES.md)
- [../LOCAL_FIRST_RUN_AR.md](../LOCAL_FIRST_RUN_AR.md)
