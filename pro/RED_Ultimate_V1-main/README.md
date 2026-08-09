# RED Ultimate V1

منصة RED محلية أولًا للمراسلة الاجتماعية والمكالمات. المشروع القانوني داخل [`RED_Ultimate/`](RED_Ultimate/README.md).

## ابدأ من هنا

1. [نظرة المشروع والمعمارية](RED_Ultimate/docs/01-PROJECT-OVERVIEW.md)
2. [قواعد البيانات والتخزين](RED_Ultimate/docs/02-DATABASES.md)
3. [السيرفر ولوحة الإدارة وتدفق البيانات](RED_Ultimate/docs/03-SERVER-ADMIN-PANEL.md)
4. [تطبيق Android والمصادر التاريخية](RED_Ultimate/docs/04-APPS.md)
5. [تشغيل Alpha محليًا](RED_Ultimate/LOCAL_FIRST_RUN_AR.md)
6. [حدود الوحدات القانونية](RED_Ultimate/W0_MODULE_BOUNDARIES.md)

## المكونات القانونية

- Android: `RED_Ultimate/red-app/` كـ Gradle `:app`.
- Backend: `RED_Ultimate/backend-server/`.
- Protocol: `RED_Ultimate/shared-proto/`.
- Admin: `RED_Ultimate/admin_dashboard/`.
- SFU: `RED_Ultimate/media-sfu/`.
- DINSTAR voice: `RED_Ultimate/pstn-asterisk/`.
- Runtime: `RED_Ultimate/docker-compose.yml`.

> `app/` و`android/` و`app-android/` مصادر تاريخية خارج البناء، وليست تطبيقات إطلاق إضافية.

## مبادئ غير قابلة للكسر

- لا هاتف/SIM/بريد/SMS/OTP للتسجيل.
- الحساب والجهاز يحتاجان موافقة إدارية.
- RED voice/video عبر WebRTC وبـ RED ID دون SIM.
- DINSTAR مسار صوت PSTN منفصل ويستهلك رصيد SIM وتتحكم به الإدارة.
- مفاتيح libsignal الخاصة لا تغادر Android.
- المحتوى الاجتماعي العام ليس E2EE.
- لا توصف ميزة بأنها مكتملة قبل البناء واختبار runtime/الجهاز المناسب.

## حالة التحقق

بوابة CI تبني وتختبر backend، تبني APK مع dependency verification صارم، تبني لوحة الإدارة، وتفحص SFU وAsterisk. PR يبقى Draft حتى تنجح تجربة Docker المحلية وهاتفين وعتاد DINSTAR حسب كل بوابة.

كل واحد من المجلدات العليا الأربعة والعشرين داخل `RED_Ultimate/` يحتوي `README.md` يوضح الوظيفة والحالة والعلاقة بباقي النظام.
