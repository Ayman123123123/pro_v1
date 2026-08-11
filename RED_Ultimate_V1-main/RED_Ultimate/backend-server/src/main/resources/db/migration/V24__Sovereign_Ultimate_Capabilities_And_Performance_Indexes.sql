-- 🚀 V23__Sovereign_Ultimate_Capabilities_And_Performance_Indexes.sql
-- التحديث الأسطوري لقواعد البيانات — إضافة الجداول المتقدمة، الفهارس المركبة، وتتبع الأداء والسيادة

-- 1. 📊 جدول جودة المكالمات الشامل (Call QoE & Telemetry)
CREATE TABLE IF NOT EXISTS call_qoe_telemetry (
    id UUID PRIMARY KEY,
    call_id VARCHAR(128) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    route VARCHAR(32) NOT NULL, -- WEBRTC_SFU, P2P_DIRECT, DINSTAR_PSTN
    duration_seconds INT NOT NULL DEFAULT 0,
    audio_bitrate_kbps INT,
    video_bitrate_kbps INT,
    packet_loss_percent NUMERIC(5,2) DEFAULT 0.00,
    jitter_ms INT DEFAULT 0,
    rtt_ms INT DEFAULT 0,
    mos_score NUMERIC(3,2) DEFAULT 4.50, -- Mean Opinion Score (1.00 - 5.00)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_qoe_call_id ON call_qoe_telemetry(call_id);
CREATE INDEX IF NOT EXISTS idx_qoe_user_created ON call_qoe_telemetry(user_id, created_at DESC);

-- 2. 🔗 جدول روابط دعوات المجموعات المشفرة (Group Invite Links)
CREATE TABLE IF NOT EXISTS group_invite_links (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    max_uses INT DEFAULT NULL, -- NULL = unlimited
    uses_count INT DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invite_group ON group_invite_links(group_id);
CREATE INDEX IF NOT EXISTS idx_invite_token ON group_invite_links(token_hash) WHERE is_revoked = FALSE;

-- 3. 📢 جدول تفضيلات واشتراكات القنوات والمجتمعات (Channel Subscriptions)
CREATE TABLE IF NOT EXISTS channel_subscriber_preferences (
    id UUID PRIMARY KEY,
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notifications_enabled BOOLEAN DEFAULT TRUE,
    is_muted BOOLEAN DEFAULT FALSE,
    last_read_post_id UUID DEFAULT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_channel_subscriber UNIQUE (community_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_sub_user ON channel_subscriber_preferences(user_id);

-- 4. 🛡️ جدول بصمات أجهزة وتتبع أمان الجلسات (Device Session Fingerprints)
CREATE TABLE IF NOT EXISTS device_session_fingerprints (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES user_devices(id) ON DELETE CASCADE,
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    location_country VARCHAR(64) DEFAULT 'YE',
    is_suspicious BOOLEAN DEFAULT FALSE,
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_fp_user ON device_session_fingerprints(user_id, last_active_at DESC);
CREATE INDEX IF NOT EXISTS idx_session_fp_suspicious ON device_session_fingerprints(is_suspicious) WHERE is_suspicious = TRUE;

-- 5. ⚡ فهارس الأداء الفائقة المركبة (Ultra-Performance Compound & Partial Indexes)
-- أ. فهرس الحسابات المعلقة للأدمن
CREATE INDEX IF NOT EXISTS idx_users_pending_approval ON users(created_at DESC) WHERE status = 'PENDING';

-- ب. فهرس تصفح سجلات المكالمات السريع
CREATE INDEX IF NOT EXISTS idx_cdr_user_date ON call_cdr_logs(user_id, created_at DESC);

-- ج. فهرس استعلامات البلاغات والإعلانات
CREATE INDEX IF NOT EXISTS idx_reports_status_cat ON user_reports(status, category, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON system_announcements(priority DESC, created_at DESC) WHERE is_published = TRUE;
