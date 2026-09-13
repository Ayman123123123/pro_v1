-- ══════════════════════════════════════════════════════════════════
-- V15: Rate Limiting, Encryption Sessions
-- PostgreSQL — البيانات المالية والتشغيلية التي تحتاج ACID
-- ══════════════════════════════════════════════════════════════════

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

-- ━━━━ قواعد Rate Limit الافتراضية ━━━━
INSERT INTO rate_limit_rules(id, endpoint_pattern, limit_per_minute, limit_per_hour, limit_per_day, scope)
VALUES
    ('10000000-0000-0000-0000-000000000001', '/api/auth/login', 5, 20, 100, 'IP'),
    ('10000000-0000-0000-0000-000000000002', '/api/auth/register', 3, 10, 30, 'IP'),
    ('10000000-0000-0000-0000-000000000003', '/api/messages/*', 60, 1000, 10000, 'USER'),
    ('10000000-0000-0000-0000-000000000004', '/api/calls/*', 10, 60, 200, 'USER'),
    ('10000000-0000-0000-0000-000000000006', '/api/stories/*', 20, 200, 500, 'USER')
ON CONFLICT (id) DO NOTHING;
