-- ══════════════════════════════════════════════════════════════════
-- V15: Rate Limiting, Billing, Dinstar CDR, Encryption Sessions
-- PostgreSQL — البيانات المالية والتشغيلية التي تحتاج ACID
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ سجلات CDR من Dinstar ━━━━
CREATE TABLE IF NOT EXISTS dinstar_cdr (
    id UUID PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE SET NULL,
    port_index INTEGER NOT NULL,
    call_id VARCHAR(100), -- معرف المكالمة من Dinstar API
    caller_number VARCHAR(30) NOT NULL,
    callee_number VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL, -- inbound, outbound
    call_type VARCHAR(20) NOT NULL DEFAULT 'VOICE', -- VOICE, SMS
    status VARCHAR(20) NOT NULL, -- answered, no_answer, busy, failed
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    ring_duration_seconds INTEGER NOT NULL DEFAULT 0,
    start_time TIMESTAMP NOT NULL,
    answer_time TIMESTAMP,
    end_time TIMESTAMP,
    -- Signal quality during call
    avg_signal_strength INTEGER,
    min_signal_strength INTEGER,
    -- Cost ( Yemeni Riyal)
    cost_yer DECIMAL(10,4) NOT NULL DEFAULT 0,
    -- Linked to internal call
    internal_call_id UUID REFERENCES call_history(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT dinstar_cdr_direction_check CHECK (direction IN ('inbound','outbound')),
    CONSTRAINT dinstar_cdr_status_check CHECK (status IN ('answered','no_answer','busy','failed','cancelled'))
);
CREATE INDEX idx_dinstar_cdr_port_time ON dinstar_cdr(gateway_id, port_index, start_time DESC);
CREATE INDEX idx_dinstar_cdr_number ON dinstar_cdr(caller_number, start_time DESC);
CREATE INDEX idx_dinstar_cdr_callee ON dinstar_cdr(callee_number, start_time DESC);

-- ━━━━ بطاقات وتعرفة PSTN ━━━━
CREATE TABLE IF NOT EXISTS pstn_tariffs (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL, -- "سبأفون اليمن", "MTN اليمن"
    country_code VARCHAR(3) NOT NULL DEFAULT '967',
    prefix_pattern VARCHAR(20) NOT NULL, -- "77", "73", "71"
    rate_per_minute_yer DECIMAL(10,4) NOT NULL, -- سعر الريالي اليمني
    rate_per_sms_yer DECIMAL(10,4) NOT NULL DEFAULT 0,
    billing_increment_seconds INTEGER NOT NULL DEFAULT 60, -- وحدة الفوترة
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pstn_tariffs_prefix ON pstn_tariffs(prefix_pattern, is_active);

-- ━━━━ فواتير المستخدم ━━━━
CREATE TABLE IF NOT EXISTS user_bills (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    -- الأusage
    total_pstn_calls INTEGER NOT NULL DEFAULT 0,
    total_pstn_minutes DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_pstn_sms INTEGER NOT NULL DEFAULT 0,
    -- التكلفة
    calls_cost_yer DECIMAL(12,4) NOT NULL DEFAULT 0,
    sms_cost_yer DECIMAL(12,4) NOT NULL DEFAULT 0,
    total_cost_yer DECIMAL(12,4) NOT NULL DEFAULT 0,
    -- الحالة
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, CLOSED, PAID, OVERDUE
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, billing_period_start),
    CONSTRAINT bill_status_check CHECK (status IN ('OPEN','CLOSED','PAID','OVERDUE'))
);
CREATE INDEX idx_user_bills_user ON user_bills(user_id, billing_period_start DESC);

-- ━━━━ Rate Limiting ━━━━
CREATE TABLE IF NOT EXISTS rate_limit_rules (
    id UUID PRIMARY KEY,
    endpoint_pattern VARCHAR(200) NOT NULL, -- "/api/auth/login", "/api/calls/*"
    limit_per_minute INTEGER NOT NULL,
    limit_per_hour INTEGER NOT NULL,
    limit_per_day INTEGER NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'USER', -- USER, IP, GLOBAL
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT rate_limit_scope_check CHECK (scope IN ('USER','IP','GLOBAL'))
);
CREATE INDEX idx_rate_limit_endpoint ON rate_limit_rules(endpoint_pattern, is_active);

-- ━━━━ جلسات التشفير ━━━━
CREATE TABLE IF NOT EXISTS encryption_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    remote_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    remote_device_id INTEGER NOT NULL,
    session_state BYTEA NOT NULL, -- serialized Signal protocol session
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_encryption_session UNIQUE (user_id, remote_user_id, remote_device_id)
);
CREATE INDEX idx_encryption_sessions_user ON encryption_sessions(user_id, last_used_at DESC);

-- ━━━━ المفاتيح المرسلة (Sent PreKeys) ━━━━
CREATE TABLE IF NOT EXISTS sent_prekey_records (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL,
    key_type VARCHAR(10) NOT NULL, -- EC, KYBER
    public_key BYTEA NOT NULL,
    sent_to_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sent_to_device_id INTEGER,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at TIMESTAMP,
    CONSTRAINT sent_prekey_type_check CHECK (key_type IN ('EC','KYBER'))
);
CREATE INDEX idx_sent_prekeys_device ON sent_prekey_records(device_id, key_type, consumed_at);

-- ━━━━ تتبع تسليم الرسائل ━━━━
CREATE TABLE IF NOT EXISTS message_delivery_receipts (
    id UUID PRIMARY KEY,
    message_uuid VARCHAR(40) NOT NULL, -- مرتبط بـ MongoDB MessageDocument
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_device_id INTEGER NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, DELIVERED, READ, FAILED
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    failed_reason VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_status_check CHECK (delivery_status IN ('PENDING','DELIVERED','READ','FAILED')),
    CONSTRAINT uq_message_delivery UNIQUE (message_uuid, recipient_user_id, recipient_device_id)
);
CREATE INDEX idx_delivery_receipts_recipient ON message_delivery_receipts(recipient_user_id, delivery_status, created_at DESC);
CREATE INDEX idx_delivery_receipts_message ON message_delivery_receipts(message_uuid);

-- ━━━━ جداول مفاتيح البحث ━━━━
-- Full-text search index for users
CREATE INDEX IF NOT EXISTS idx_users_name_search ON users USING gin(to_tsvector('arabic', COALESCE(full_name,'') || ' ' || COALESCE(username,'')));
CREATE INDEX IF NOT EXISTS idx_users_red_id_prefix ON users(red_id varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_users_username_prefix ON users(LOWER(username) varchar_pattern_ops);

-- ━━━━ بيانات تعرفة PSTN اليمن الافتراضية ━━━━
INSERT INTO pstn_tariffs(id, name, country_code, prefix_pattern, rate_per_minute_yer, rate_per_sms_yer, billing_increment_seconds)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'سبأفون اليمن', '967', '77', 15.0000, 5.0000, 60),
    ('00000000-0000-0000-0000-000000000002', 'MTN اليمن', '967', '71', 15.0000, 5.0000, 60),
    ('00000000-0000-0000-0000-000000000003', 'Yemen Mobile (يموبايل)', '967', '73', 15.0000, 5.0000, 60),
    ('00000000-0000-0000-0000-000000000004', 'HiTel اليمن', '967', '70', 12.0000, 4.0000, 60)
ON CONFLICT (id) DO NOTHING;

-- ━━━━ قواعد Rate Limit الافتراضية ━━━━
INSERT INTO rate_limit_rules(id, endpoint_pattern, limit_per_minute, limit_per_hour, limit_per_day, scope)
VALUES
    ('10000000-0000-0000-0000-000000000001', '/api/auth/login', 5, 20, 100, 'IP'),
    ('10000000-0000-0000-0000-000000000002', '/api/auth/register', 3, 10, 30, 'IP'),
    ('10000000-0000-0000-0000-000000000003', '/api/messages/*', 60, 1000, 10000, 'USER'),
    ('10000000-0000-0000-0000-000000000004', '/api/calls/*', 10, 60, 200, 'USER'),
    ('10000000-0000-0000-0000-000000000005', '/api/pstn/*', 5, 30, 100, 'USER'),
    ('10000000-0000-0000-0000-000000000006', '/api/stories/*', 20, 200, 500, 'USER')
ON CONFLICT (id) DO NOTHING;
