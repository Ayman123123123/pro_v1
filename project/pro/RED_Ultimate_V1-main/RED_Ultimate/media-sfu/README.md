# media-sfu/ — خادم وسائط RED

> **الحالة:** نشط — Node 22/mediasoup

## الوظيفة

SFU محلي للمكالمات الجماعية والبث والمساحات. يتحقق من JWT ويدير rooms/transports/producers/consumers؛ الإشارة الفردية الأساسية تمر عبر backend. يحتاج announced IP ومنافذ UDP وTURN للاختبار الحقيقي.

## المحتوى

`server.js` الخادم، `package-lock.json` تثبيت حتمي، `Dockerfile` runtime.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
