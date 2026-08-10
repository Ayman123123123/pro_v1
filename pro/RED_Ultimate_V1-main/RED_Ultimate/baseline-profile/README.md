# baseline-profile/ — Baseline Profile تاريخي

> **الحالة:** غير مدرج حاليًا

## الوظيفة

مولد Profile من إرث Android لتحسين startup عند إعادة تفعيله مع التطبيق القانوني. لا يدخل APK الحالي لأن الوحدة غير موجودة في `settings.gradle.kts`.

## المحتوى

مصادر instrumentation و`build.gradle.kts`.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
