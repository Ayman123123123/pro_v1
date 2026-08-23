# خطة التطوير والتحسين الشاملة لمنصة RED Sovereign (PSTN & Core)

بناءً على الفحص الدقيق للمشروع (Android, Backend, Asterisk, Dinstar) خلال الساعات الماضية، قمت بتحديد كافة الميزات المكتملة والمجالات التي تتطلب تحسيناً أو إكمالاً لضمان "الاكتمال الأسطوري" للمشروع.

## مراجعة الحالة الحالية (ما تم إنجازه)

1.  **DINSTAR Gateway**: تكامل ذكي لموزع الأحمال (Load Balancer) يراعي الإشارة، المشغل، والعدل في التوزيع.
2.  **Sabafon Number Learning**: حل مشكلة الرقم المفقود عبر نمط "Call mode" مع دليل كامل وخطوات مثبتة.
3.  **Asterisk Dialplan v3**: دعم نغمات المشغلين اليمنيين، وتمرير Early Media، وحل مشكلة سقوط أحداث UserEvent عبر جسر HTTP داخلي.
4.  **Android Core**: دعم التشفير، الهوية الرقمية، الرنين في الخلفية عبر Foreground Services و FullScreen Intents.
5.  **Security**: حماية الشاشات من التصوير، وإدارة صلاحيات الشبكة المحلية لنظام Android 14+.

---

## التغييرات المقترحة (التطوير والإكمال)

### [Component] Android App (UI/UX & Architecture)

#### [MODIFY] [MainActivity.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/MainActivity.kt)
- استخراج منطق الصلاحيات وبدء الخدمات إلى `AppStartupCoordinator` لتخفيف الضغط عن الـ Activity.
- تحسين إدارة `PstnIncomingCallCoordinator` ليكون أكثر مرونة مع دورة حياة التطبيق.

#### [NEW] [PstnStatusIndicator.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/ui/components/PstnStatusIndicator.kt)
- إضافة مؤشر في لوحة الاتصال يعرض حالة الاتصال ببوابة DINSTAR واسم المشغل الحالي المختار للمكالمة.

#### [MODIFY] [YemeniOperatorDetector.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/calls/YemeniOperatorDetector.kt)
- تحديث ألوان المشغلين لتكون أكثر دقة وتوافقاً مع الهوية البصرية الرسمية (Sabafon Gold, YOU Yellow, etc).

---

### [Component] Backend Server (Resilience & Automation)

#### [MODIFY] [DinstarEventListener.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/pstn/DinstarEventListener.kt)
- نقل خريطة `channelToCallId` من الذاكرة المحلية إلى **Redis**. هذا يضمن استمرارية المكالمات الجارية حتى لو ريسترت السيرفر.

#### [NEW] [AutoNumberLearningService.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/pstn/AutoNumberLearningService.kt)
- خدمة خلفية تقوم تلقائياً باكتشاف المنافذ التي لا تظهر أرقامها (خاصة Sabafon) وتطلق عملية "تعلّم الرقم" برمجياً دون تدخل يدوي.

#### [MODIFY] [InternalPstnController.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/pstn/InternalPstnController.kt)
- تقوية حماية الـ API الداخلي عبر التحقق من IP المصدر (Asterisk Container IP) بالإضافة إلى الـ Secret.

---

### [Component] DevOps & Maintenance

#### [NEW] [PSTN_MASTER_OPERATIONS.md](file:///C:/Users/hpc01/Pictures/pro_new/docs/PSTN_MASTER_OPERATIONS.md)
- توحيد كافة الأدلة المبعثرة في ملف واحد شامل يغطي (Config, Troubleshooting, Emergency Recovery).

#### [MODIFY] [scripts/test-dinstar-number-matcher.ps1](file:///C:/Users/hpc01/Pictures/pro_new/scripts/test-dinstar-number-matcher.ps1)
- تحسين السكربت ليشمل اختبار كافة البادئات اليمنية الجديدة (Yemen 4G, Fixed lines).

---

## خطة التحقق

### التحقق الآلي
- تشغيل `test-dinstar-number-matcher.ps1` للتأكد من دقة تصنيف الأرقام.
- فحص سجلات `InternalPstnController` للتأكد من وصول أحداث المكالمات الواردة من Asterisk.

### التحقق اليدوي
- إجراء مكالمة Sabafon واردة والتأكد من ظهور اسم المشغل واللون الصحيح على شاشة الأندرويد.
- إعادة تشغيل حاوية الباك أند أثناء مكالمة رنين والتأكد من بقاء المكالمة نشطة (بفضل نقل الـ Map إلى Redis).

---

> [!IMPORTANT]
> هذا التحديث سيجعل نظام PSTN في RED Sovereign الأكثر استقراراً ودقة في السوق اليمني، مع أتمتة كاملة لإدارة الشرائح.
