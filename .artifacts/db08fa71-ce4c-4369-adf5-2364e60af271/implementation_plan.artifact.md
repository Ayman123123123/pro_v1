# خطة تنفيذ "الاكتمال الأسطوري" (Legendary Completeness) لـ RED Ultimate

تهدف هذه الخطة إلى دمج كافة الميزات السيادية (Sovereign) مع النواة الأساسية للتطبيق (Signal-based) لضمان الحصول على نسخة متكاملة وشاملة.

## التغييرات المقترحة

### 1. توحيد تطبيق الأندرويد (Android Consolidation)

- **[تعديل] [settings.gradle.kts](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/settings.gradle.kts)**: جعل مجلد `app/` هو الموديول الرئيسي `:app` بدلاً من `red-app/`.
- **[تعديل] [AndroidManifest.xml](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/app/src/main/AndroidManifest.xml)**: دمج كافة الخدمات والـ Receivers السيادية (PSTN, VoIP, NotificationRouter).
- **[تعديل] [MainActivity.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/app/src/main/java/org/thoughtcrime/securesms/MainActivity.kt)**:
    - استدعاء `RedConnector.autoBind()` عند التشغيل.
    - دمج منطق `AppLock` السيادي مع نظام قفل الشاشة الحالي.
    - توجيه إشعارات الـ VoIP السيادية إلى مصلحة المكالمات الصحيحة.

### 2. بروتوكول الرسائل المتقدم (Advanced Messaging Protocol)

- **[تم التنفيذ] [red_protocol.proto](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/shared-proto/src/main/proto/red_protocol.proto)**: تم إضافة دعم `StickerRED` و `PollRED` و `ReactionRED`.
- **[تعديل] [MessageService.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/messaging/MessageService.kt)**: تفعيل دعم أنواع الرسائل الجديدة في الخادم.

### 3. إدارة المحتوى (Content Management)

- **[تعديل] [ContentController.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/admin/controller/ContentController.kt)**: إضافة الـ Endpoints اللازمة لإدارة حزم الملصقات (Stickers) والاستطلاعات (Polls).

### 4. نظام Dinstar المتقدم

- **[تعديل] [NumberLearningService.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/dinstar/NumberLearningService.kt)**: التأكد من دمج منطق تعلم الأرقام المتقدم لضمان استقرار الخدمة في المناطق الضعيفة.

## خطة التحقق (Verification Plan)

### الاختبارات الآلية
- تشغيل `./gradlew :app:assembleDebug` للتأكد من سلامة دمج الأندرويد.
- تشغيل `npm run build` في `admin_dashboard` للتأكد من سلامة الواجهة.

### التحقق اليدوي
- فحص ظهور تبويبات الملصقات والاستطلاعات في لوحة التحكم.
- فحص عمل الـ AppLock عند تشغيل التطبيق.
- التأكد من قدرة التطبيق على استقبال إشعارات الـ VoIP.
