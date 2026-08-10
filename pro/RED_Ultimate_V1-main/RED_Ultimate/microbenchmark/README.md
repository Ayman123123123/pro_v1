# microbenchmark/ — Microbenchmark تاريخي

> **الحالة:** غير مدرج حاليًا

## الوظيفة

قياسات دقيقة موروثة لخوارزميات/بروتوكولات. ليست دليل أداء للنسخة الحالية قبل إعادة ربطها بـ `red-app`.

## المحتوى

`src/androidTest` وملف ProGuard للاختبارات.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
