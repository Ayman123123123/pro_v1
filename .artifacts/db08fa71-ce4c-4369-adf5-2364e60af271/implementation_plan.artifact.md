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

التأكد من أن الباكند يحتوي على كافة مسارات الـ API (210 مسار) وأحدث منطق لـ PSTN.

#### [MODIFY] [PstnManager.kt](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/pstn/PstnManager.kt)
النسخة الحالية هي الأحدث، لكن سأقوم بمطابقتها مع نسخة التحقق للتأكد من عدم ضياع أي "Trivial fixes" تتعلق بالـ Heartbeat أو إدارة القنوات.

### [Documentation & Diagnostics]

استعادة كافة ملفات السجلات والتوثيق "التافهة" والصغيرة لضمان اكتمال الأرشيف.

#### [NEW] [diagnostics/](file:///C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/docs/diagnostics/)
نسخ كافة سجلات البناء والاختبارات (logs) من نسخة التحقق إلى المجلد الرئيسي.

## Verification Plan

### Automated Tests
- تشغيل `./gradlew :app:compileDebugKotlin` للتأكد من سلامة كود الأندرويد بعد الدمج.
- تشغيل اختبارات الباكند `./gradlew :backend-server:test`.

### Manual Verification
- التحقق من ظهور الميزات الجديدة في كود `MeshRtcSession.kt`.
- التأكد من وجود كافة ملفات الـ `docs` الجديدة.
