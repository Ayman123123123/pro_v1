-- ══════════════════════════════════════════════════════════════════
-- V29: Legendary Operational Hardening & Final Polish
-- PostgreSQL — تحسين الأداء، سياسات الاستبقاء، والتنظيف التلقائي
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ System Retention Config ━━━━
-- ضبط مدة الاحتفاظ بالبيانات التشغيلية لضمان استقرار الأداء
INSERT INTO system_settings(setting_key, setting_value) VALUES
    ('retention.audit_days', '90'),
    ('retention.cdr_days', '180'),
    ('retention.health_days', '7'),
    ('retention.deleted_messages_days', '30'),
    ('retention.stale_sessions_days', '7')
ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value;

-- ━━━━ Optimization Indexes ━━━━
-- تسريع البحث في السجلات الكبيرة
CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users(last_seen DESC) WHERE status = 'APPROVED';
CREATE INDEX IF NOT EXISTS idx_admin_sessions_cleanup ON admin_sessions(expires_at) WHERE is_active = TRUE;

-- ━━━━ Automatic Retention Cleanup Function ━━━━
-- دالة مركزية لتنظيف البيانات القديمة بناءً على الإعدادات
CREATE OR REPLACE FUNCTION legendary_retention_cleanup() RETURNS VOID AS $$
DECLARE
    v_audit_days INTERVAL;
    v_cdr_days INTERVAL;
    v_health_days INTERVAL;
BEGIN
    -- جلب القيم من الإعدادات مع fallback
    SELECT (COALESCE(setting_value, '90') || ' days')::INTERVAL INTO v_audit_days FROM system_settings WHERE setting_key='retention.audit_days';
    SELECT (COALESCE(setting_value, '180') || ' days')::INTERVAL INTO v_cdr_days FROM system_settings WHERE setting_key='retention.cdr_days';
    SELECT (COALESCE(setting_value, '7') || ' days')::INTERVAL INTO v_health_days FROM system_settings WHERE setting_key='retention.health_days';

    -- التنظيف الفعلي
    DELETE FROM admin_audit_log WHERE created_at < NOW() - v_audit_days;
    DELETE FROM dinstar_cdr WHERE start_time < NOW() - v_cdr_days;
    DELETE FROM system_health WHERE last_check_at < NOW() - v_health_days;

    -- تنظيف الجلسات المنتهية (soft-delete)
    UPDATE admin_sessions SET is_active = FALSE, terminated_at = NOW(), termination_reason = 'RETENTION_CLEANUP'
    WHERE is_active = TRUE AND expires_at < NOW() - INTERVAL '1 day';
END;
$$ LANGUAGE plpgsql;

-- ━━━━ Final Branding Polish ━━━━
-- التأكد من وجود الهوية السيادية في كل مكان
UPDATE system_settings SET setting_value = 'YOUNES Sovereign Platform - Legendary Version' WHERE setting_key = 'brand.name.full';
INSERT INTO system_settings(setting_key, setting_value) VALUES ('brand.version', '1.0.0-legendary') ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value;

COMMENT ON FUNCTION legendary_retention_cleanup IS 'Legendary: Automatic cleanup of operational data based on sovereign policy';
