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
| `app/` | فورك Signal تاريخي كبير | لا | Gold mine لتقنيات منتقاة ومراجعة الترخيص |
| `android/` | نموذج AQYAL سابق | لا | أفكار UI/قصص/مكالمات تُنقل يدويًا |
| `app-android/` | نموذج DevelopedChat أقدم | لا | مرجع لتدفق الموافقة وبعض Compose |

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

## `app/` — Signal Gold Mine

هذا ليس التطبيق الرئيسي الآن. الاحتفاظ به يتيح دراسة تطبيقات Signal الأصلية، لكنه يحمل افتراضات وبنية واعتماديات ضخمة لا تناسب الخادم المحلي تلقائيًا. يمنع:

- اعتباره دليلًا أن ميزة ما مفعلة.
- استعادة endpoints سحابية.
- إعادة الحزم/الخدمات المكررة إلى graph.
- نسخ كود دون مراجعة AGPL/التراخيص والأمان.

## `android/` — AQYAL reference

يحتوي أفكارًا بصرية وتنظيمًا قديمًا للميزات. تُقارن واجهاته مع `red-app` ثم تُعاد كتابة القطعة المتوافقة؛ لا يعتمد `red-app` عليه ولا تُبنى مواده.

## `app-android/` — prototype reference

نموذج مبكر أصغر. قد يفيد لفهم شاشات pending/rejected، لكنه يستخدم نماذج وURLs قديمة ولا يحدد flow الحالي.

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
