-- ═══════════════════════════════════════════════════════════════════
-- DINSTAR Enhancements — قوالب SMS + رسائل مجدوَلَة + تحكم بالمنافذ
-- ═══════════════════════════════════════════════════════════════════

-- ── قوالب الرسائل SMS ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sms_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    text TEXT NOT NULL,
    encoding VARCHAR(20) NOT NULL DEFAULT 'gsm-7bit',
    category VARCHAR(30) NOT NULL DEFAULT 'custom',
    variables_json TEXT NOT NULL DEFAULT '',
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sms_templates_category ON sms_templates(category);

-- ── رسائل SMS مجدوَلَة ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS scheduled_sms (
    id VARCHAR(36) PRIMARY KEY,
    template_id VARCHAR(36) REFERENCES sms_templates(id) ON DELETE SET NULL,
    recipients_json TEXT NOT NULL,
    gateway_host VARCHAR(255),
    variables_json TEXT NOT NULL DEFAULT '{}',
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SENT','DELIVERED','FAILED','CANCELLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_scheduled_sms_status ON scheduled_sms(status);
CREATE INDEX IF NOT EXISTS idx_scheduled_sms_scheduled_at ON scheduled_sms(scheduled_at);

-- ── حالة التحكم بالمنافذ (طاقة + تحويل) ─────────────────────────
CREATE TABLE IF NOT EXISTS port_control_state (
    gateway_id UUID NOT NULL REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER NOT NULL CHECK (port_index BETWEEN 0 AND 31),
    power_state BOOLEAN NOT NULL DEFAULT TRUE,
    call_forward_state VARCHAR(30) NOT NULL DEFAULT 'NONE',
    call_forward_number VARCHAR(20),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gateway_id, port_index)
);
