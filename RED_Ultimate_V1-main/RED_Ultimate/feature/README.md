# feature/ — ميزات Signal المنفصلة

> **الحالة:** مرجع — غير مدرج

## الوظيفة

وحدات camera وmedia-send وregistration من المصدر التاريخي. تسجيل RED القانوني موجود في `red-app` و`backend-server` ولا يعتمد هذه الوحدة.

## المحتوى

`camera/` و`media-send/` و`registration/`.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
