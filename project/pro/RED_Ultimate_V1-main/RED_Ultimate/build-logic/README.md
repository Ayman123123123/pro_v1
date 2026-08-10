# build-logic/ — منطق بناء Gradle

> **الحالة:** نشط كـ included build

## الوظيفة

Plugins وأدوات QA مشتركة يستدعيها بناء الجذر. علاقته مباشرة بـ `build.gradle.kts` و`settings.gradle.kts` ولا يحتوي ميزات المنتج.

## المحتوى

`plugins/` اتفاقيات Gradle، `tools/` أدوات وفحوص، `settings.gradle.kts` للبناء المركب.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
