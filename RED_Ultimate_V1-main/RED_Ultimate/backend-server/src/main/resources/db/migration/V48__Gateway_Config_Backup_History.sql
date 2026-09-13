-- ═══════════════════════════════════════════════════════════════════════════
-- V48 — إضافة جداول النسخ الاحتياطي والسجل الصحي للبوابات
-- ═══════════════════════════════════════════════════════════════════════════
-- المشكلة التي يعالجها هذا الترحيل:
--
-- DinstarHardwareService يحتاج الآن إلى تخزين نسخ احتياطية
-- من تكوينات البوابات وسجلات صحية، لكن لا توجد جداول مخصصة
-- لذلك. أضفنا الجداول التالية لدعم:
--   • backupConfig / restoreConfig / listBackups
--   • healthCheck / fleetHealthSummary
--
-- هذه الجداول تُضاف للبيئة الإنتاجية فقط ولا تؤثر
-- على النشرات ذات الجهاز الواحد.

-- 1) جدول النسخ الاحتياطي لتكوينات البوابات
CREATE TABLE IF NOT EXISTS gateway_config_backups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway_id      UUID NOT NULL REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    config_json     JSONB NOT NULL,
    actor_id        UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gw_config_backup_gateway
    ON gateway_config_backups (gateway_id, created_at DESC);

COMMENT ON TABLE gateway_config_backups IS
    'نسخ احتياطية لتكوينات البوابات. يُستخدم لـ backupConfig/restoreConfig.';

-- 2) جدول السجل الصحي للبوابات (health check history)
CREATE TABLE IF NOT EXISTS gateway_health_history (
    id              BIGSERIAL PRIMARY KEY,
    gateway_id      UUID NOT NULL REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    reachable       BOOLEAN NOT NULL,
    model           VARCHAR(64),
    port_total      INT DEFAULT 0,
    port_registered INT DEFAULT 0,
    port_healthy    INT DEFAULT 0,
    error_message   TEXT,
    checked_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gw_health_gateway_time
    ON gateway_health_history (gateway_id, checked_at DESC);

ALTER TABLE gateway_health_history
    ADD CONSTRAINT chk_gw_health_ports CHECK (
        port_total >= port_registered AND port_registered >= port_healthy
    );

COMMENT ON TABLE gateway_health_history IS
    'سجل الفحوصات الصحية للبوابات. يُستخدم لـ healthCheck/fleetHealthSummary.';

-- 3) جدول عمليات البوابات (للتسجيل التدقيقي)
--    يتم إنشاؤه بالفعل عبر V12 لكن نُضيف الأعمدة المفقودة
ALTER TABLE gateway_operations
    ADD COLUMN IF NOT EXISTS duration_ms INT;

ALTER TABLE gateway_operations
    ADD COLUMN IF NOT EXISTS call_id UUID REFERENCES pstn_active_calls(id);

CREATE INDEX IF NOT EXISTS idx_gw_ops_gateway_time
    ON gateway_operations (gateway_id, completed_at DESC);

COMMENT ON TABLE gateway_operations IS
    'سجل عمليات البوابات مع مدة التنفيذ ومعرّف المكالمة المرتبطة.';

-- 4) تأكيد جدول telecom_gateways يحتوي على الأعمدة المطلوبة
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'telecom_gateways' AND column_name = 'model'
    ) THEN
        ALTER TABLE telecom_gateways ADD COLUMN model VARCHAR(64) DEFAULT 'UNKNOWN';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'telecom_gateways' AND column_name = 'vendor'
    ) THEN
        ALTER TABLE telecom_gateways ADD COLUMN vendor VARCHAR(64) DEFAULT 'DINSTAR';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'telecom_gateways' AND column_name = 'api_port'
    ) THEN
        ALTER TABLE telecom_gateways ADD COLUMN api_port INT DEFAULT 443;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'telecom_gateways' AND column_name = 'scheme'
    ) THEN
        ALTER TABLE telecom_gateways ADD COLUMN scheme VARCHAR(10) DEFAULT 'https';
    END IF;
END $$;