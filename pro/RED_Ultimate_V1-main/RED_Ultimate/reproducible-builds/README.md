# reproducible-builds/ — أدوات مقارنة البناء

> **الحالة:** مرجع يحتاج مواءمة

## الوظيفة

أدوات Docker و`apkdiff` الموروثة لمقارنة APK. الوثائق القديمة لا تعني أن إصدار RED الحالي منشور reproducibly؛ يلزم خط إصدار وتوقيع ونتيجة مقارنة فعلية.

## المحتوى

`Dockerfile` و`apkdiff/`.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
