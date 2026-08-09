# shared-proto/ — بروتوكول RED الموحد

> **الحالة:** نشط — مشترك Android/backend

## الوظيفة

المصدر الوحيد لرسائل Protobuf القانونية، ويُبنى كوحدة JVM مشتركة. endpoint الرسائل هو `/ws/master`؛ أي proto تاريخي خارج `src/main/proto` غير قانوني.

## المحتوى

`src/main/proto/red_protocol.proto` و`build.gradle.kts`.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
