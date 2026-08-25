# خطة التوحيد الأسطوري الشامل (Ultimate Consolidation Plan)

الهدف هو دمج أفضل العناصر من كافة النسخ المتوفرة (المجلد الرئيسي، نسخة التحقق `_red_ultimate_verify` والنسخ التاريخية) لإنشاء النسخة الأكثر اكتمالاً وحداثة وشمولية لمشروع RED Ultimate.

## Proposed Changes

### [Android App - red-app]

تحديث المكونات الأساسية لضمان استقرار المكالمات وشمولية الاختبارات.

#### [MODIFY] [MeshRtcSession.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/calls/MeshRtcSession.kt)
استبدال النسخة الحالية بالنسخة "الأسطورية" الموجودة في `_red_ultimate_verify` والتي تدعم:
- نظام محاولات ذكي (Retries) لخوادم ICE.
- دقات فيديو تكيفية (Multiple Resolutions).
- إصلاحات مشاركة الشاشة المتقدمة.

#### [NEW] [MediaLogicTest.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/red-app/src/test/java/com/red/sovereign/features/media/MediaLogicTest.kt)
استعادة ملف الاختبارات الشامل للوسائط الموجود في نسخة التحقق وغير الموجود في النسخة الحالية.

### [Backend Server]

التأكد من أن الباكند يحتوي على كافة مسارات الـ API (أكثر من 210 مسار) وأحدث منطق لـ PSTN.

#### [MODIFY] [PstnManager.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/pstn/PstnManager.kt)
دمج تحسينات إدارة القنوات من نسخة التحقق، مع الحفاظ على ميزات "Number Learning" المتقدمة الموجودة في النسخة الحالية.

#### [MODIFY] [DinstarFleetController.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/controllers/DinstarFleetController.kt)
تحديث مسارات التحكم لتتوافق مع الواجهة البرمجية الأسطورية الموحدة.

### [Admin Dashboard - red-admin-dashboard]

تحويل لوحة التحكم إلى النسخة الأكثر احترافية مع دعم كامل لخصائص الـ PSTN المتقدمة.

#### [MODIFY] [DinstarControl.tsx](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/pages/DinstarControl.tsx)
دمج الميزات من كلا النسختين:
- الرسوم البيانية التفاعلية لحالة المنافذ (من نسخة التحقق).
- نظام "Number Learning" والتحكم العميق (من النسخة الحالية).

#### [NEW] [SecurityDashboard.tsx](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/admin_dashboard/src/pages/SecurityDashboard.tsx)
إضافة شاشة مراقبة الأمان والشهادات الموجودة في نسخة التحقق.

### [Infrastructure & Proto]

#### [MODIFY] [nginx.conf](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/nginx.conf)
تحديث إعدادات البروكسي لتدعم الـ WebSockets بشكل أكثر استقراراً (Keep-alive timeouts).

#### [SYNC] [red_protocol.proto](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/shared-proto/src/main/proto/red_protocol.proto)
التأكد من تطابق البروتوكول تماماً بين كافة الأطراف لتجنب أخطاء التسلسل (Serialization).

### [Documentation & Diagnostics]

استعادة كافة ملفات السجلات والتوثيق "التافهة" والصغيرة لضمان اكتمال الأرشيف.

#### [NEW] [diagnostics/](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/docs/diagnostics/)
نسخ كافة سجلات البناء والاختبارات (logs) من نسخة التحقق إلى المجلد الرئيسي.

## User Review Required

> [!IMPORTANT]
> دمج ملف `DinstarControl.tsx` يتطلب حذراً كبيراً لأن النسخة الحالية تحتوي على منطق "Number Learning" معقد قد لا يكون موجوداً في نسخة التحقق. سأقوم بالدمج يدوياً لضمان عدم ضياع أي ميزة.

> [!WARNING]
> تحديث `nginx.conf` قد يتطلب إعادة تشغيل حاويات Docker، مما قد يسبب انقطاعاً مؤقتاً في الخدمة أثناء التجربة.

## Open Questions

- هل هناك أي ملفات "Secret" أو مفاتيح تشفير (Keys) في مجلد `_red_ultimate_verify` يجب نقلها أيضاً، أم نكتفي بالكود المصدري؟
- هل تفضل تشغيل عملية الدمج بشكل تلقائي (Scripted) أم أقوم بكل خطوة يدوياً مع التحقق؟

## Verification Plan

### Automated Tests
- تشغيل `./gradlew :app:compileDebugKotlin` للتأكد من سلامة كود الأندرويد بعد الدمج.
- تشغيل اختبارات الباكند `./gradlew :backend-server:test`.
- تشغيل `npm run build` في مجلد `admin_dashboard` للتأكد من سلامة كود الـ React.

### Manual Verification
- الدخول إلى لوحة التحكم والتأكد من ظهور كافة القوائم (بما في ذلك Dinstar و Security).
- إجراء مكالمة تجريبية للتأكد من استقرار `MeshRtcSession`.
- فحص سجلات NGINX للتأكد من استقرار اتصالات الـ WebSocket.
