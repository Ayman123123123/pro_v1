

## مراجعة الحالة الحالية (ما تم إنجازه)

4.  **Android Core**: دعم التشفير، الهوية الرقمية، الرنين في الخلفية عبر Foreground Services و FullScreen Intents.
5.  **Security**: حماية الشاشات من التصوير، وإدارة صلاحيات الشبكة المحلية لنظام Android 14+.

---

## التغييرات المقترحة (التطوير والإكمال)

### [Component] Android App (UI/UX & Architecture)

#### [MODIFY] [MainActivity.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/MainActivity.kt)
- استخراج منطق الصلاحيات وبدء الخدمات إلى `AppStartupCoordinator` لتخفيف الضغط عن الـ Activity.



---

### [Component] Backend Server (Resilience & Automation)

- نقل خريطة `channelToCallId` من الذاكرة المحلية إلى **Redis**. هذا يضمن استمرارية المكالمات الجارية حتى لو ريسترت السيرفر.



---

### [Component] DevOps & Maintenance

- توحيد كافة الأدلة المبعثرة في ملف واحد شامل يغطي (Config, Troubleshooting, Emergency Recovery).


---

## خطة التحقق

### التحقق الآلي

### التحقق اليدوي
- إعادة تشغيل حاوية الباك أند أثناء مكالمة رنين والتأكد من بقاء المكالمة نشطة (بفضل نقل الـ Map إلى Redis).

---

> [!IMPORTANT]
