# 04 — تطبيقات Android: المنتج والمصادر التاريخية

## القرار المعماري الحالي

يوجد **منتج Android واحد فقط** في graph:

```kotlin
include(":app")
project(":app").projectDir = file("red-app")
```

وجود مجلدات Android أخرى لا يعني وجود ثلاثة APKs قابلة للإطلاق. هي مصادر تاريخية للاستخراج، موثقة كي لا يعيد مطور توصيلها بالخطأ.

## المقارنة

| المسار | الدور الحالي | يدخل البناء؟ | الاستخدام الصحيح |
|---|---|---:|---|
| `red-app/` | تطبيق RED القانوني | نعم، `:app` | التطوير والاختبار والإصدار |
| `app/` | ~~فورك Signal~~ حُذف في المرحلة 11 (2026-09-14) | — | الفرز اكتمل: كل الكود stubs/متفوَّق عليه/ملغي النطاق؛ نُقلت 5 نغمات فقط |

## `red-app/`

### الهوية والمصادقة

- username/password/display name بلا هاتف أو بريد أو OTP.
- RED ID مولد من الخادم.
- PENDING حتى موافقة الإدارة.
- Device ID وشهادة بصمة، JWT وrefresh rotation.
- recovery codes محلية أحادية الاستخدام.

### E2EE

- libsignal 0.86.5 المنشور والمتوافق.
- identity + signed EC + Kyber-1024 generated locally.
- one-time EC/Kyber pool وتجديد تلقائي.
- PQXDH session ثم Double Ratchet.
- protocol records مشفرة بـ Android Keystore AES-GCM.
- targeted per-device envelopes وSENT/DELIVERED/READ.

المتبقي: Safety Number/QR UX وKey Transparency وSender Keys distribution للمجموعات واختبار هاتفين فعلي.

### الواجهة

خمس وجهات: المنشورات، المحادثات، إنشاء، المكالمات الموحدة، هاتف DINSTAR. Feed/following/Yemen/posts والمجموعات والحالات ورفع الوسائط موجودة بمستويات متفاوتة. أي engine غير موصول يجب أن يبقى disabled.

### الاتصال المحلي

`RED_SERVER_URL` يحقن وقت البناء. Debug يسمح HTTP داخل LAN؛ release يمنع cleartext ويتطلب HTTPS. foreground WebSocket يعوض cloud push في local-first deployment.

## `app/` — حُذف في المرحلة 11 (2026-09-14)

فُرزت الشجرة ملفاً ملفاً (~7000 ملف): النماذج الأولية الـ55 تحت `com.red.sovereign` كانت stubs تطبع `println`
أو تحيل لأصناف وهمية (`MediasoupClient`/`CallResult`/`VoipState`/protos)، وكل ما له نظير في `red-app/` كان
متفوَّقاً عليه (SfuMediaClient ‏632 سطراً، NetworkChangeWatcher، SafetyViewModel…)؛ نطاق PSTN/Yemen ملغي
بقرار المستخدم. الاستخراج الوحيد: 5 أصوات تقدُّم مكالمات إلى `red-app/src/main/res/raw/` (ترخيص GPL-3.0
متوافق مع AGPL عبر §13 — الإسناد في `red-app/README.md`). لا تستعد شيئاً من Git history دون مراجعة الترخيص.

## `android/` و`app-android/` — أُزيلا في 2026-08-19

كانا شجرتين متوازيتين خارج build graph، ولم يستوردهما `red-app` بحرف
واحد. دُمج ما يفيد منهما في `red-app` وحُذفا. أبرز ما نُقل: 20 عملية
DINSTAR (USSD، إدارة المنافذ، الرسائل الواردة، تحويل المكالمات)،
وعامل تنظيف القصص المنتهية.

وقد كان بقاؤهما خطراً لا فوضى فحسب: `android/` حمل جدول بادئات مشغّلين
يمنيين مخالفاً للواقع وللخادم. التفاصيل الكاملة والقرارات المعمارية في
[`UNIFICATION_2026-08-19.md`](UNIFICATION_2026-08-19.md).

## كيفية إضافة ميزة Android بصورة صحيحة

1. أضفها في `red-app/src/main/java/com/red/sovereign/`.
2. استخدم `AuthorizedApiClient` وmodels قانونية.
3. للمراسلة عدّل `shared-proto` أولًا وحدث الطرفين.
4. لا ترسل private key/plaintext إلى backend.
5. اجعل UI معطلًا إذا لم يوجد runtime engine.
6. أضف unit/instrumentation test مناسبًا.
7. مرر `:app:assembleDebug --dependency-verification strict`.
8. اختبر على جهاز حقيقي عند الكاميرا/الصوت/WebRTC/Keystore.

## حالة الإصدار

APK Debug يُنتج في CI وصورة artifacts. هذا لا يساوي Release: ما تزال مفاتيح التوقيع، R8 release validation، TLS، سياسة الخصوصية، SBOM/licensing، واختبارات الأجهزة مطلوبة.
