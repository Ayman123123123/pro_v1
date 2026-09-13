# red-app/ — تطبيق Android القانوني

> **الحالة:** نشط — Gradle module `:app`

## الوظيفة

التطبيق الذي ينتج APK: حساب username، موافقة الإدارة، هوية جهاز محلية، libsignal PQXDH/Double Ratchet، WebSocket، feed/groups/stories وسجل المكالمات وهاتف DINSTAR المنفصل. الميزات غير المكتملة معطلة بصريًا.

## المحتوى

`src/main/java/com/red/sovereign/` المصدر، `AndroidManifest.xml`، `build.gradle.kts`، اختبارات الوحدة.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.

## FCM (مكالمات PSTN الواردة)

- الحالة الحالية: لا يوجد `google-services.json` حقيقي في `red-app/` — وهذا مقصود (ملف أسرار لكل بيئة، في `.gitignore`).
- للتشغيل المحلي/الإنتاج:
  1. انسخ `google-services.json.example` إلى `google-services.json` (بجانب `build.gradle.kts`) واملأ القيم من Firebase Console لنفس `applicationId` (`com.red.sovereign`).
  2. على الخادم: عبّئ `FCM_V1_SERVICE_ACCOUNT` (JSON حساب الخدمة) — بدونه لا تُرسل رسائل DATA من `NotificationService`.
  3. أعد البناء ونفّذ مكالمة PSTN واردة أثناء قفل التطبيق.
- التشخيص: `PstnFcmListenerService` لا يخرج بصمت — كل رسالة مُتجاهَلة تُسجَّل في logcat تحت وسم `PstnFcmListener` مع السبب (`empty data` / `missing type` / `non-VOIP` / `missing callId` / `no active coordinator`). راقب بـ:
  `adb logcat -s PstnFcmListener:* VoipPushRegistrar:*`
- بدون الملف: البناء سليم والتطبيق يعمل، لكن الإيقاظ عبر FCM معطّل (يبقى مسار WebSocket `/ws/pstn` أثناء فتح التطبيق).
