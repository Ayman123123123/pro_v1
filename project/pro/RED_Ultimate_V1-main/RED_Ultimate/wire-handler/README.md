# wire-handler/ — أداة Wire build-time

> **الحالة:** نشط كـ JAR بناء موروث

## الوظيفة

معالج Wire مخصص محمل من `wire-handler-1.0.0.jar` في buildscript. ليس خدمة runtime ولا بروتوكول الرسائل نفسه.

## المحتوى

`lib/` المصدر، JAR المثبت، `settings.gradle.kts`.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
