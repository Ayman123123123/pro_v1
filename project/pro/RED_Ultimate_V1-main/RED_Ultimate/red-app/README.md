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
