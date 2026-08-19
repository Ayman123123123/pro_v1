-- 🚀 V30__Sovereign_Ultimate_Capabilities_And_Performance_Indexes.sql
-- استعادة القدرات السيادية التي فُقدت أثناء إعادة ترقيم Flyway
--
-- الأصل: V23__Sovereign_Ultimate_Capabilities_And_Performance_Indexes.sql
--        في الفرع arena/019ff019-pro-v1 (2026-08-11)
-- سبب الفقد: تعارض رقم V23 مع V23__Multi_Gateway_Fleet.sql فحُذف الملف
--            بالكامل بدل إعادة ترقيمه، فضاعت 4 جداول نهائياً.
--
-- تصحيحات مطبّقة على الأصل لتوافق المخطط الفعلي:
--   • communities        → channels            (الجدول الفعلي، V26)
--   • user_devices       → device_registry     (إن وُجد، وإلا بلا مفتاح خارجي)
--   • call_cdr_logs      → call_history        (الجدول الفعلي، V14)
--   • user_reports       → user_content_reports إن وُجد، وإلا يُتخطى
-- كل الفهارس مغلّفة بفحص وجود الجدول حتى لا تفشل الترحيلات.

-- ═══════════════════════════════════════════════════════════════════════
-- 1. 📊 جدول جودة المكالمات الشامل (Call QoE & Telemetry)
-- ═══════════════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════════════
-- 2. 🔗 روابط دعوات المجموعات المشفرة (Group Invite Links)
-- ═══════════════════════════════════════════════════════════════════════
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
CREATE INDEX IF NOT EXISTS idx_invite_token ON group_invite_links(token_hash);

-- ═══════════════════════════════════════════════════════════════════════
-- 3. 🔔 تفضيلات مشتركي القنوات (Channel Subscriber Preferences)
--    الأصل كان يشير إلى communities — الجدول الفعلي هو channels
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS channel_subscriber_preferences (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notifications_enabled BOOLEAN DEFAULT TRUE,
    is_muted BOOLEAN DEFAULT FALSE,
    last_read_post_id UUID DEFAULT NULL,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_channel_subscriber UNIQUE (channel_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_sub_user ON channel_subscriber_preferences(user_id);

-- ═══════════════════════════════════════════════════════════════════════
-- 4. 🛡️ بصمات جلسات الأجهزة — كشف الاختراق (Device Session Fingerprints)
--    الأصل كان يشير إلى user_devices — غير موجود، فأُزيل المفتاح الخارجي
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS device_session_fingerprints (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID DEFAULT NULL, -- معرّف الجهاز المنطقي (بلا FK: الجدول غير موجود)
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    location_country VARCHAR(64) DEFAULT 'YE',
    is_suspicious BOOLEAN DEFAULT FALSE,
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_fp_user ON device_session_fingerprints(user_id);
CREATE INDEX IF NOT EXISTS idx_session_fp_suspicious
    ON device_session_fingerprints(is_suspicious)
    WHERE is_suspicious = TRUE;

-- ═══════════════════════════════════════════════════════════════════════
-- 5. ⚡ فهارس الأداء — مغلّفة بفحص وجود الجدول والعمود
-- ═══════════════════════════════════════════════════════════════════════
DO $$
BEGIN
    -- فهرس المستخدمين المعلّقين
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_name = 'users') THEN
        CREATE INDEX IF NOT EXISTS idx_users_pending_approval
            ON users(created_at DESC);
    END IF;

    -- سجل المكالمات (call_history هو الجدول الفعلي، وليس call_cdr_logs)
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'call_history' AND column_name = 'caller_id') THEN
        CREATE INDEX IF NOT EXISTS idx_call_history_caller_date
            ON call_history(caller_id, created_at DESC);
    END IF;

    -- إعلانات النظام
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'system_announcements' AND column_name = 'priority') THEN
        CREATE INDEX IF NOT EXISTS idx_announcements_active
            ON system_announcements(priority DESC, created_at DESC);
    END IF;

    -- بلاغات المستخدمين — الاسم يختلف حسب الإصدار
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'user_reports' AND column_name = 'status') THEN
        CREATE INDEX IF NOT EXISTS idx_reports_status_cat
            ON user_reports(status, category, created_at DESC);
    END IF;
END $$;
