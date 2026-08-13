-- ═══════════════════════════════════════════════════════════════════
-- DINSTAR Enhanced - جداول إضافية للوظائف المتقدمة
-- V26 - 2026-08-13
-- ═══════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════
-- 1. سجل حالة الجهاز (Device Status History)
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_device_status (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    cpu_used VARCHAR(20),
    memory_total VARCHAR(20),
    memory_used VARCHAR(20),
    memory_free VARCHAR(20),
    flash_total VARCHAR(20),
    flash_used VARCHAR(20),
    flash_free VARCHAR(20),
    temperature VARCHAR(20),
    uptime VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(gateway_id)
);

CREATE INDEX IF NOT EXISTS idx_device_status_gateway ON dinstar_device_status(gateway_id);
CREATE INDEX IF NOT EXISTS idx_device_status_updated ON dinstar_device_status(updated_at DESC);

-- ═══════════════════════════════════════════════════════════════════
-- 2. سجل المكالمات التفصيلي (CDR)
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_cdr (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER,
    start_time TIMESTAMP,
    answer_time TIMESTAMP,
    end_time TIMESTAMP,
    duration INTEGER, -- بالثواني
    caller_number VARCHAR(50),
    callee_number VARCHAR(50),
    direction VARCHAR(20), -- INBOUND, OUTBOUND
    call_type VARCHAR(20), -- VOICE, SMS, USSD
    codec VARCHAR(20),
    hangup_cause VARCHAR(50),
    sip_call_id VARCHAR(100),
    asterisk_channel VARCHAR(100),
    raw_data JSONB, -- بيانات خام من البوابة
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cdr_gateway ON dinstar_cdr(gateway_id);
CREATE INDEX IF NOT EXISTS idx_cdr_port ON dinstar_cdr(port_index);
CREATE INDEX IF NOT EXISTS idx_cdr_start_time ON dinstar_cdr(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_cdr_caller ON dinstar_cdr(caller_number);
CREATE INDEX IF NOT EXISTS idx_cdr_callee ON dinstar_cdr(callee_number);
CREATE INDEX IF NOT EXISTS idx_cdr_direction ON dinstar_cdr(direction);

-- ═══════════════════════════════════════════════════════════════════
-- 3. سجل رسائل SMS
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_sms_log (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER,
    message_type VARCHAR(20), -- SENT, RECEIVED
    phone_number VARCHAR(50),
    message_text TEXT,
    encoding VARCHAR(20), -- GSM7, UNICODE
    status VARCHAR(20), -- PENDING, SENT, DELIVERED, FAILED
    task_id VARCHAR(50),
    error_code INTEGER,
    error_message VARCHAR(200),
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sms_gateway ON dinstar_sms_log(gateway_id);
CREATE INDEX IF NOT EXISTS idx_sms_port ON dinstar_sms_log(port_index);
CREATE INDEX IF NOT EXISTS idx_sms_phone ON dinstar_sms_log(phone_number);
CREATE INDEX IF NOT EXISTS idx_sms_status ON dinstar_sms_log(status);
CREATE INDEX IF NOT EXISTS idx_sms_created ON dinstar_sms_log(created_at DESC);

-- ═══════════════════════════════════════════════════════════════════
-- 4. سجل أوامر USSD
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_ussd_log (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER,
    ussd_code VARCHAR(50),
    response_text TEXT,
    status VARCHAR(20), -- SUCCESS, FAILED, TIMEOUT
    error_message VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ussd_gateway ON dinstar_ussd_log(gateway_id);
CREATE INDEX IF NOT EXISTS idx_ussd_port ON dinstar_ussd_log(port_index);
CREATE INDEX IF NOT EXISTS idx_ussd_created ON dinstar_ussd_log(created_at DESC);

-- ═══════════════════════════════════════════════════════════════════
-- 5. قوالب الرسائل SMS
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_sms_templates (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    template_text TEXT NOT NULL,
    description VARCHAR(500),
    variables JSONB, -- ["name", "code", "amount"]
    encoding VARCHAR(20) DEFAULT 'GSM7',
    usage_count INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sms_templates_name ON dinstar_sms_templates(name);
CREATE INDEX IF NOT EXISTS idx_sms_templates_active ON dinstar_sms_templates(is_active);

-- ═══════════════════════════════════════════════════════════════════
-- 6. رسائل SMS مجدولة
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_sms_scheduled (
    id SERIAL PRIMARY KEY,
    template_id INTEGER REFERENCES dinstar_sms_templates(id) ON DELETE SET NULL,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE SET NULL,
    port_index INTEGER,
    recipient_number VARCHAR(50) NOT NULL,
    message_text TEXT NOT NULL,
    variables JSONB,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, SENT, FAILED, CANCELLED
    sent_at TIMESTAMP,
    error_message VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sms_scheduled_status ON dinstar_sms_scheduled(status);
CREATE INDEX IF NOT EXISTS idx_sms_scheduled_at ON dinstar_sms_scheduled(scheduled_at);

-- ═══════════════════════════════════════════════════════════════════
-- 7. حالة التحكم بالمنافذ
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_port_control (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER NOT NULL,
    power_state BOOLEAN DEFAULT TRUE,
    call_forward_enabled BOOLEAN DEFAULT FALSE,
    call_forward_number VARCHAR(50),
    call_forward_condition VARCHAR(20), -- ALWAYS, BUSY, NO_ANSWER
    do_not_disturb BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(gateway_id, port_index)
);

CREATE INDEX IF NOT EXISTS idx_port_control_gateway ON dinstar_port_control(gateway_id);
CREATE INDEX IF NOT EXISTS idx_port_control_port ON dinstar_port_control(port_index);

-- ═══════════════════════════════════════════════════════════════════
-- 8. إحصائيات يومية
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_daily_stats (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    stat_date DATE NOT NULL,
    total_calls INTEGER DEFAULT 0,
    inbound_calls INTEGER DEFAULT 0,
    outbound_calls INTEGER DEFAULT 0,
    total_call_duration INTEGER DEFAULT 0, -- بالثواني
    total_sms_sent INTEGER DEFAULT 0,
    total_sms_received INTEGER DEFAULT 0,
    total_ussd INTEGER DEFAULT 0,
    avg_signal_strength INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(gateway_id, stat_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_stats_date ON dinstar_daily_stats(stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_daily_stats_gateway ON dinstar_daily_stats(gateway_id);

-- ═══════════════════════════════════════════════════════════════════
-- 9. تنبيهات وأحداث
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_alerts (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL, -- LOW_SIGNAL, PORT_OFFLINE, HIGH_TEMPERATURE, etc.
    severity VARCHAR(20) NOT NULL, -- INFO, WARNING, CRITICAL
    message TEXT NOT NULL,
    port_index INTEGER,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_at TIMESTAMP,
    acknowledged_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alerts_gateway ON dinstar_alerts(gateway_id);
CREATE INDEX IF NOT EXISTS idx_alerts_type ON dinstar_alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON dinstar_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_acknowledged ON dinstar_alerts(acknowledged);
CREATE INDEX IF NOT EXISTS idx_alerts_created ON dinstar_alerts(created_at DESC);

-- ═══════════════════════════════════════════════════════════════════
-- 10. سجل تغييرات التكوين
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS dinstar_config_changes (
    id SERIAL PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    changed_by UUID,
    change_type VARCHAR(50) NOT NULL, -- PORT_RESET, CALL_FORWARD, POWER_OFF, etc.
    port_index INTEGER,
    old_value TEXT,
    new_value TEXT,
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_changes_gateway ON dinstar_config_changes(gateway_id);
CREATE INDEX IF NOT EXISTS idx_config_changes_type ON dinstar_config_changes(change_type);
CREATE INDEX IF NOT EXISTS idx_config_changes_created ON dinstar_config_changes(created_at DESC);

-- ═══════════════════════════════════════════════════════════════════
-- Views للتقارير
-- ═══════════════════════════════════════════════════════════════════

-- عرض المكالمات الأخيرة
CREATE OR REPLACE VIEW v_dinstar_recent_calls AS
SELECT 
    c.id,
    g.name as gateway_name,
    g.host as gateway_host,
    c.port_index,
    c.start_time,
    c.duration,
    c.caller_number,
    c.callee_number,
    c.direction,
    c.call_type,
    c.hangup_cause,
    CASE 
        WHEN c.direction = 'INBOUND' THEN c.caller_number
        ELSE c.callee_number
    END as contact_number
FROM dinstar_cdr c
JOIN telecom_gateways g ON g.id = c.gateway_id
ORDER BY c.start_time DESC
LIMIT 100;

-- عرض إحصائيات البوابات
CREATE OR REPLACE VIEW v_dinstar_gateway_stats AS
SELECT 
    g.id,
    g.name,
    g.host,
    g.model,
    g.enabled,
    g.health_state,
    COUNT(DISTINCT c.id) as total_calls,
    COUNT(DISTINCT s.id) as total_sms,
    COUNT(DISTINCT u.id) as total_ussd,
    AVG(CASE WHEN c.duration IS NOT NULL THEN c.duration ELSE NULL END) as avg_call_duration,
    MAX(c.start_time) as last_call_at
FROM telecom_gateways g
LEFT JOIN dinstar_cdr c ON c.gateway_id = g.id
LEFT JOIN dinstar_sms_log s ON s.gateway_id = g.id
LEFT JOIN dinstar_ussd_log u ON u.gateway_id = g.id
WHERE g.vendor = 'DINSTAR'
GROUP BY g.id, g.name, g.host, g.model, g.enabled, g.health_state;

-- عرض التنبيهات غير المعترف بها
CREATE OR REPLACE VIEW v_dinstar_active_alerts AS
SELECT 
    a.id,
    g.name as gateway_name,
    a.alert_type,
    a.severity,
    a.message,
    a.port_index,
    a.created_at,
    EXTRACT(EPOCH FROM (NOW() - a.created_at))/3600 as hours_since_alert
FROM dinstar_alerts a
JOIN telecom_gateways g ON g.id = a.gateway_id
WHERE a.acknowledged = FALSE
ORDER BY 
    CASE a.severity 
        WHEN 'CRITICAL' THEN 1 
        WHEN 'WARNING' THEN 2 
        ELSE 3 
    END,
    a.created_at DESC;
