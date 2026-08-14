-- ══════════════════════════════════════════════════════════════════
-- V32: Comprehensive Schema Corrections & Hardening
-- PostgreSQL — تصحيحات شاملة لعيوب مكتشفة في التدقيق (DB audit)
-- V1..V31 لم تُلمس إطلاقاً (checksums محفوظة) — كل الإصلاحات هنا
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ 1. saved_messages.message_id: UUID → VARCHAR(40) ━━━━
-- الخطأ: V20 عرّف message_id كـ UUID بينما معرفات الرسائل الحقيقية نصوص
-- (UuidV7 من MongoDB، مثل message_delivery_receipts.message_uuid VARCHAR(40)).
-- الإدراج الفعلي من كود الحفظ كان سيفشل أو يخزّن قيماً لا تطابق أي رسالة.
ALTER TABLE saved_messages ALTER COLUMN message_id TYPE VARCHAR(40);
COMMENT ON COLUMN saved_messages.message_id IS
    'معرف الرسالة النصي (UuidV7 من MongoDB) — يطابق message_delivery_receipts.message_uuid';
-- فهرس جزئي على المعرف النصي للمسح السريع عند إلغاء الحفظ
CREATE INDEX IF NOT EXISTS idx_saved_messages_message_id ON saved_messages(message_id);

-- ━━━━ 2. contact_requests: UNIQUE كامل → فهرس جزئي PENDING ━━━━
-- الخطأ: uq_contact_request_direction UNIQUE(requester_id, recipient_id) كان
-- يمنع إعادة إرسال طلب الصداقة للأبد بعد REJECTED/ACCEPTED.
-- الحل: التفرد يطبَّق على الطلبات النشطة فقط (PENDING).
ALTER TABLE contact_requests DROP CONSTRAINT IF EXISTS uq_contact_request_direction;
CREATE UNIQUE INDEX IF NOT EXISTS uq_contact_request_pending
    ON contact_requests(requester_id, recipient_id) WHERE status = 'PENDING';
COMMENT ON INDEX uq_contact_request_pending IS
    'يمنع ازدواج طلب صداقة نشط بين زوج واحد فقط — يسمح بإعادة الطلب بعد الحل';

-- ━━━━ 3. فهرس مكرر: إزالة idx_messages_delivery_receipts_message ━━━━
-- V15 أنشأ idx_delivery_receipts_message(message_uuid) و V26 كرّره باسم آخر.
-- الإبقاء على الاسم الأصلي فقط.
DROP INDEX IF EXISTS idx_messages_delivery_receipts_message;

-- ━━━━ 4. تحصين دالة الاستبقاء ضد NULL (عيب V31) ━━━━
-- الخطأ: SELECT (COALESCE(setting_value,'90') || ' days') INTO ... — عند غياب
-- صف الإعداد يبقى المتغير NULL (COALESCE لا يعمل بلا صف) فيصبح
-- DELETE ... WHERE created_at < NOW() - NULL → حذف كل شيء.
-- الحل: COALESCE على استعلام فرعي يضمن صفاً دائماً.
CREATE OR REPLACE FUNCTION legendary_retention_cleanup() RETURNS VOID AS $$
DECLARE
    v_audit_days INTERVAL;
    v_cdr_days INTERVAL;
    v_health_days INTERVAL;
BEGIN
    SELECT (COALESCE((SELECT setting_value FROM system_settings WHERE setting_key='retention.audit_days'), '90') || ' days')::INTERVAL INTO v_audit_days;
    SELECT (COALESCE((SELECT setting_value FROM system_settings WHERE setting_key='retention.cdr_days'), '180') || ' days')::INTERVAL INTO v_cdr_days;
    SELECT (COALESCE((SELECT setting_value FROM system_settings WHERE setting_key='retention.health_days'), '7') || ' days')::INTERVAL INTO v_health_days;

    -- حارس إضافي: لا تنظيف أبداً بفاصل NULL أو سالب
    IF v_audit_days IS NULL OR v_audit_days < INTERVAL '1 day' THEN v_audit_days := INTERVAL '90 days'; END IF;
    IF v_cdr_days IS NULL OR v_cdr_days < INTERVAL '1 day' THEN v_cdr_days := INTERVAL '180 days'; END IF;
    IF v_health_days IS NULL OR v_health_days < INTERVAL '1 day' THEN v_health_days := INTERVAL '7 days'; END IF;

    DELETE FROM admin_audit_log WHERE created_at < NOW() - v_audit_days;
    DELETE FROM dinstar_cdr WHERE start_time < NOW() - v_cdr_days;
    DELETE FROM system_health WHERE last_check_at < NOW() - v_health_days;

    UPDATE admin_sessions SET is_active = FALSE, terminated_at = NOW(), termination_reason = 'RETENTION_CLEANUP'
    WHERE is_active = TRUE AND expires_at < NOW() - INTERVAL '1 day';
END;
$$ LANGUAGE plpgsql;

-- ━━━━ 5. brand.name.full: ضمان الوجود (عيب V31: UPDATE بلا INSERT) ━━━━
INSERT INTO system_settings(setting_key, setting_value)
VALUES ('brand.name.full', 'YOUNES Sovereign Platform - Legendary Version')
ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value;

-- ━━━━ 6. توثيق قرارات مُدرَسة (بلا تغيير) ━━━━
-- • call_history (V14): ON DELETE CASCADE على caller_id/callee_id — قرار خصوصية
--   مقصود: حذف الحساب يمسح سجل مكالماته. لا تغيير.
-- • user_reports: يحتوي reported_id (V10) وtarget_user_id (V19) — entity الحالية
--   (UserReport) تستخدم target_user_id فقط؛ reported_id عمود قديم غير مستخدم.
--   محفوظ لتوافق البيانات التاريخية؛ يُحذف في ترقية لاحقة بعد تأكيد عدم استخدامه.
-- • stories (Mongo): expiresAt ليست TTL عمداً — StoryService.cleanupExpired()
--   المجدولة كل 5 دقائق تمسح الميديا من MinIO قبل حذف المستندات؛ TTL كانت
--   تحذف المستند أولاً فتسرّب الملفات. قرار مقصود.
