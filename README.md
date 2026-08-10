# YOUNES / RED Sovereign

تطبيق أندرويد محلي أولًا للمراسلة الآمنة والمكالمات والإدارة، مبني حول هوية مستقلة بدون رقم هاتف وبدون SIM للتسجيل. يحتوي المستودع على تطبيق Android، خادم Backend، لوحة إدارة، بروتوكول مشترك، وخدمات مساعدة للتشغيل المحلي.

> المدخل الفني الرئيسي للمشروع موجود داخل: [`RED_Ultimate_V1-main/`](RED_Ultimate_V1-main/README.md)

## ماذا نعمل الآن؟

الأولوية العملية بعد رفع المشروع هي تحويله من مشروع مرفوع إلى منتج قابل للاختبار والإطلاق المرحلي:

1. **تثبيت البناء والتحقق**: تشغيل اختبارات الخادم، بناء تطبيق Android، وبناء لوحة الإدارة.
2. **تشغيل بيئة Docker المحلية**: PostgreSQL + MongoDB + Redis + MinIO + SFU + Backend + Admin.
3. **اختبار جهازين Android فعليًا**: تسجيل، موافقة إدارية، محادثة خاصة، مجموعة، صوت، فيديو، وسائط.
4. **تحسين الواجهة تدريجيًا**: شاشة الترحيب، إنشاء الحساب، الدردشات، المنشورات، الحالات، المكالمات، الإعدادات.
5. **تحويل كل ميزة إلى بوابة تحقق**: لا تُعتبر الميزة مكتملة حتى تُبنى وتُختبر وتُوثق.

## المكونات الأساسية

| المكون | المسار | الدور |
|---|---|---|
| Android App | `RED_Ultimate_V1-main/RED_Ultimate/red-app/` | تطبيق يونس الأساسي |
| Backend Server | `RED_Ultimate_V1-main/RED_Ultimate/backend-server/` | المصادقة، الأجهزة، الإدارة، APIs |
| Admin Dashboard | `RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/` | لوحة الإدارة والمراقبة |
| Shared Protocol | `RED_Ultimate_V1-main/RED_Ultimate/shared-proto/` | رسائل Protobuf المشتركة |
| Media SFU | `RED_Ultimate_V1-main/RED_Ultimate/media-sfu/` | مؤتمرات ومكالمات WebRTC |
| PSTN/DINSTAR | `RED_Ultimate_V1-main/RED_Ultimate/pstn-asterisk/` | مسار صوت PSTN منفصل |
| Runtime | `RED_Ultimate_V1-main/RED_Ultimate/docker-compose.yml` | تشغيل الخدمات محليًا |

## أوامر التحقق السريعة

من داخل `RED_Ultimate_V1-main/RED_Ultimate/`:

```bash
# بناء/اختبار الخادم حسب إعداد المشروع
./gradlew :backend-server:test

# ترجمة تطبيق Android
./gradlew :app:compileDebugKotlin

# اختبارات وحدة Android
./gradlew :app:testDebugUnitTest

# بناء APK تجريبي
./gradlew :app:assembleDebug

# بناء لوحة الإدارة
npm --prefix admin_dashboard install
npm --prefix admin_dashboard run build
```

> إن كانت بيئتك Windows فاستخدم `gradlew.bat` بدل `./gradlew`.

## قواعد المشروع المهمة

- التسجيل لا يعتمد على رقم هاتف أو SMS أو OTP.
- الحساب والجهاز يحتاجان موافقة الإدارة.
- مفاتيح التشفير الخاصة تبقى على جهاز Android ولا تغادره.
- الرسائل الخاصة والمجموعات يجب أن تحافظ على E2EE.
- المحتوى الاجتماعي العام ليس E2EE إلا إذا ذُكر ذلك صراحة.
- لوحة الإدارة لا يجب أن تعرض نصوص الرسائل الخاصة أو أسرار التشفير.
- أي ميزة جديدة يجب أن تأتي معها: كود + اختبار + توثيق + طريقة تحقق.

## خارطة تطوير مختصرة

### المرحلة 1: الاستقرار
- إصلاح أي أخطاء بناء أو اختبارات.
- تنظيف تحذيرات Gradle وTypeScript.
- توثيق متغيرات البيئة المطلوبة.

### المرحلة 2: الواجهة الاحترافية
- أيقونة موحدة للتطبيق ولوحة الإدارة.
- نظام ألوان وخطوط واحد.
- تحسين إنشاء الحساب وتسجيل الدخول.
- تحسين الدردشات والحالات والمنشورات والمكالمات والإعدادات.

### المرحلة 3: الميزات الكاملة
- دردشات خاصة متقدمة: رد، تعديل، حذف، تثبيت، بحث، وسائط.
- مجموعات: أدوار، دعوات، إدارة أعضاء، صلاحيات.
- منشورات وحالات: تفاعلات، تعليقات، استطلاعات، وسائط.
- مكالمات: واجهة اتصال، سجل، مؤتمرات، جودة شبكة.

### المرحلة 4: الإطلاق المحلي
- تشغيل Docker كامل.
- اختبار جهازين أو أكثر.
- APK تجريبي موقع.
- تقرير قبول نهائي.

## الروابط الداخلية المهمة

- [نظرة المشروع](RED_Ultimate_V1-main/RED_Ultimate/docs/01-PROJECT-OVERVIEW.md)
- [قواعد البيانات](RED_Ultimate_V1-main/RED_Ultimate/docs/02-DATABASES.md)
- [السيرفر ولوحة الإدارة](RED_Ultimate_V1-main/RED_Ultimate/docs/03-SERVER-ADMIN-PANEL.md)
- [تطبيق Android](RED_Ultimate_V1-main/RED_Ultimate/docs/04-APPS.md)
- [تشغيل Alpha محليًا](RED_Ultimate_V1-main/RED_Ultimate/LOCAL_FIRST_RUN_AR.md)
