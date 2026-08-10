# scripts/ — أتمتة التشغيل والأمان

> **الحالة:** نشط للمطور/المشغل

## الوظيفة

أدوات تشغيل Alpha محليًا على Windows وPOSIX وتوليد سلطة هوية P-256. تنشئ أسرارًا محلية ولا ترفعها.

## المحتوى

`local-first-run.ps1`، `local-first-run.sh`، `generate-local-identity-authority.sh`.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
