# gradle/ — إعدادات واعتماديات Gradle

> **الحالة:** نشط وحساس أمنيًا

## الوظيفة

Version catalogs وGradle Wrapper و`verification-metadata.xml`. ملف التحقق يثبت SHA-256 للاعتماديات ويُستخدم مع `--dependency-verification strict`.

## المحتوى

`libs.versions.toml` الكتالوج الرئيسي، كتالوجات QA، `wrapper/`، metadata.

## العلاقة بباقي المشروع

- راجع [`../settings.gradle.kts`](../settings.gradle.kts) لمعرفة ما يدخل البناء فعلًا؛ وجود المصدر لا يعني أنه مفعّل.
- الحدود القانونية موثقة في [`../W0_MODULE_BOUNDARIES.md`](../W0_MODULE_BOUNDARIES.md).
- خريطة النظام الكاملة في [`../docs/01-PROJECT-OVERVIEW.md`](../docs/01-PROJECT-OVERVIEW.md).

## التحقق

لا تُعلن ميزة هذا المجلد مكتملة إلا إذا دخلت بوابة البناء المناسبة واختبار runtime/جهازها. أسرار `.env` و`secrets/` ومفاتيح Android الخاصة لا تُحفظ في Git.
