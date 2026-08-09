# pstn-asterisk/ — جسر DINSTAR الصوتي

> **الحالة:** نشط اختياري — منفصل عن RED WebRTC

## الوظيفة

Asterisk مخصص لمسار Android → backend authorization → AMI/Asterisk → DINSTAR → SIM. لا يحتوي عميل RED WebRTC. الإعدادات الحساسة تُولد وقت التشغيل، والوارد غير المربوط يُرفض.

## المحتوى

`Dockerfile`، `docker-entrypoint.sh` لتوليد AMI/PJSIP، `extensions.conf` dialplan مقيد.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
