# الأرشيف — كود ميت/وهمي حُذف من البناء — 2026-09-15

> سياسة المشروع: الكود الميت يُؤرشف هنا (خارج شجرة المصدر) بدل تركه يعطي انطباعاً
> كاذباً بأن ميزة تعمل. السجل محفوظ عبر `git mv` ويمكن استرجاع أي ملف متى احتاج
> بناؤه الحقيقي.

| الملف | لماذا حُذف | الدليل |
|---|---|---|
| `CallTransferManager.kt` | "تحويل المكالمة" كان **وهمياً**: `success = Math.random() > 0.1` — يحذف المستخدم نجاحاً/فشلاً عشوائياً بلا أي إشارة نقل حقيقية (لا REFER ولا endpoint على الخادم). | السطر 78 من الملف المؤرشف |
| `CallTransferScreen.kt` | شاشة الميزة الوهمية أعلاه — بلا مستدعٍ واحد بعد فرز الجلسة. | `grep CallTransfer` = الملفان فقط |
| `CallSystemIntegration.kt` | "منسّق تهيئة المكالمات" **لم يكن مستدعى من أي مكان**. كل ما فيه القيمة فعلاً يعيش لدى مالكه الحقيقي: القنوات في `YounesApplication.createNotificationChannels`، `WebRtcBootstrap.ensure` عند كل محرك، تسجيل الدفع في `AppStartupCoordinator`/`CallBootReceiver`. بقي فقط كوهم توحيد. | تعليق `CallNotificationManager` نفسه يؤكد أنه غير مستدعى |
| `VirtualBackgroundManager.kt` | "الخلفية الافتراضية" لم تكن تفعل شيئاً في الفيديو: ضباب Compose على المعاينة فقط (وليس إطارات WebRTC المُرسلة للطرف الآخر) — وحتى ذلك لم يكن موصولاً: لا أحد يستدعي `setEffect`. ميزة نصية ثابتة تضلل المستخدم. | `grep setEffect` = الملف ذاته فقط |
| `UnifiedModernCallScreen.kt` | شاشة مكالمة بديلة **صفر مستدعين** — الواجهة الحية هي `CallScreens → UnifiedCallOverlays → YounesCallOverlay (CallOverlay.kt)`. كانت أيضاً العرض الوحيد لشريحة "الخلفية" الزائفة أعلاه. | `grep UnifiedModernCallScreen(` بلا نتائج |

## ما يتعين على من يريد إحياء أي منها

1. **تحويل المكالمة الحقيقي**: إشارة `TRANSFER` عبر `/ws/calls` + تحقق الخادم من الطرفين + UI جديد.
2. **خلفية افتراضية حقيقية**: معالجة إطارات WebRTC (`VideoSource` + `EglBase`custom frame processing)
   قبل النشر — إطارًا بإطار على مسار الإرسال، لا على المعاينة.
3. **تهيئة موحدة**: استعن بـ`AppStartupCoordinator` (هو نقطة التهيئة الفعلية المعيشة).
