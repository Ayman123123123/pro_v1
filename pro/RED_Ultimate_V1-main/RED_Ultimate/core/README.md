# core/ — مكتبات Signal الأساسية القديمة

> **الحالة:** مرجع — غير مدرج

## الوظيفة

وحدات models/network/serialization/ui/util من شجرة Signal الأصلية. ليست جزءًا من APK القانوني حاليًا؛ لا يُفترض أن تعني وجود ميزة في RED.

## المحتوى

مجلدات Gradle مستقلة تاريخية.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
