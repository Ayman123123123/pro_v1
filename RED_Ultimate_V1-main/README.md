# RED Ultimate V1

منصة RED المحلية للمراسلة الاجتماعية والمكالمات. المشروع القانوني داخل [`RED_Ultimate/`](RED_Ultimate/README.md).

## ابدأ من هنا

1. [نظرة المشروع والمعمارية](RED_Ultimate/docs/01-PROJECT-OVERVIEW.md)
2. [قواعد البيانات والتخزين](RED_Ultimate/docs/02-DATABASES.md)
3. [السيرفر ولوحة الإدارة](RED_Ultimate/docs/03-SERVER-ADMIN-PANEL.md)
4. [تطبيق Android](RED_Ultimate/docs/04-APPS.md)
5. [تشغيل Alpha محليًا](RED_Ultimate/LOCAL_FIRST_RUN_AR.md)
6. [حدود الوحدات القانونية](RED_Ultimate/W0_MODULE_BOUNDARIES.md)

## المكونات القانونية

- Android: `RED_Ultimate/red-app/` كـ Gradle `:app`.
- Backend: `RED_Ultimate/backend-server/`.
- Protocol: `RED_Ultimate/shared-proto/`.
- Admin: `RED_Ultimate/admin_dashboard/`.
- SFU: `RED_Ultimate/media-sfu/`.
- Runtime: `RED_Ultimate/docker-compose.yml`.

> `app/` و`android/` و`app-android/` مصادر تاريخية خارج البناء، وليست تطبيقات إطلاق إضافية.

## مبادئ المشروع

- التسجيل يعتمد على هوية RED وموافقة الإدارة، وليس على هاتف أو بريد أو OTP.
- المكالمات الصوتية والمرئية داخل المنصة تستخدم WebRTC وRED ID.
- مفاتيح libsignal الخاصة لا تغادر Android.
- المحتوى الاجتماعي العام ليس E2EE.
- لا توصف ميزة بأنها مكتملة قبل البناء واختبار runtime والجهاز المناسب.

## التحقق

بوابة CI تبني backend وAPK ولوحة الإدارة وتفحص SFU. استخدم `docker-compose.yml` وملفات التشغيل العامة من أجل التحقق المحلي.

كل مجلد تشغيلي داخل `RED_Ultimate/` يحتوي `README.md` يوضح وظيفته وحالته وعلاقته بباقي النظام.
