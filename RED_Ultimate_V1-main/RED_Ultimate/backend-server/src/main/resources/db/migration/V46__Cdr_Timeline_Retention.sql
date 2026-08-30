-- V46__Cdr_Timeline_Retention.sql
-- ═══════════════════════════════════════════════════════════════════════
-- `pstn_call_timeline` يفقد كل صفوفه لحظة انتهاء المكالمة — إصلاح المرجع.
-- ═══════════════════════════════════════════════════════════════════════
--
-- ## العطل
--
-- V44 عرّفت المرجع الخارجي:
--
--   FOREIGN KEY (call_id) REFERENCES pstn_active_calls(call_id) ON DELETE CASCADE
--
-- و`pstn_active_calls` جدول حالة **حيّة** لا سِجل: `PersistentReservationService`
-- ينظّفه كل دقيقة، والسجل يُثبته:
--
--   Cleaned up 0 expired port reservations and 1 active calls
--
-- فكل صفوف الخطّ الزمني تُمحى تتابعيًّا (`CASCADE`) في اللحظة التي تنتهي فيها
-- المكالمة — أي بالضبط عندما يبدأ التشخيص. جدول التشخيص لا يحتفظ بشيء إلا
-- للمكالمات الجارية، وهي التي لا تحتاج تشخيصًا بعد.
--
-- وأثرٌ ثانٍ: `recordStage` لمكالمة غير موجودة في `pstn_active_calls` يفشل
-- بخرق المرجع. المرحلة `ENDED` تُكتب بعد تحرير القيد أحيانًا، فتسقط.
--
-- ## الإصلاح
--
-- يُحذف المرجع الخارجي ويبقى الفهرس. `call_id` معرّف مكالمة عابر للجدولَين
-- (`pstn_active_calls` للحالة الحيّة، `dinstar_cdr` للسِجل، `call_history`
-- للتاريخ) ولا يملكه أحدها ملكيةً حصرية — فربط عمر التشخيص بعمر الحالة
-- الحيّة خطأ نموذجة لا خطأ ضبط.
--
-- التنظيف يتولّاه `RetentionScheduler` مع بقية الجداول التشغيلية بنافذة
-- زمنية صريحة، لا حذفٌ تتابعي غير مقصود.

ALTER TABLE pstn_call_timeline
    DROP CONSTRAINT IF EXISTS pstn_call_timeline_call_id_fkey;

COMMENT ON TABLE pstn_call_timeline IS
    'خطّ مراحل مكالمة PSTN للتشخيص. call_id معرّف عابر للجدولَين ولا يرتبط '
    'بمرجع خارجي: ربطه بـpstn_active_calls كان يمحو الخطّ تتابعيًّا لحظة '
    'انتهاء المكالمة. التنظيف بنافذة زمنية عبر RetentionScheduler.';

-- الاستعلام الشائع: «آخر مرحلة لهذه المكالمة» و«الخطّ مرتَّبًا».
-- الفهرس الموجود (call_id, started_at DESC) يخدم الأول؛ وهذا يخدم التنظيف
-- الزمني بلا مسح كامل.
CREATE INDEX IF NOT EXISTS idx_call_timeline_started_at
    ON pstn_call_timeline (started_at);
