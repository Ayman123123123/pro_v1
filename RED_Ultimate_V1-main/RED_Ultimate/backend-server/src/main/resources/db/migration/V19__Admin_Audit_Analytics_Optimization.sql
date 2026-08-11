-- ══════════════════════════════════════════════════════════════════
-- V19: Admin Audit Trail, Analytics & Performance Optimization
-- PostgreSQL — تحسينات لإدارة النظام والـ Analytics
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ Audit Log محسّن (Admin Actions) ━━━━
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id UUID PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    admin_username VARCHAR(100),  -- denormalized for fast lookup
    action VARCHAR(50) NOT NULL,  -- USER_APPROVED, USER_BANNED, CONFIG_CHANGED, MEDIA_DELETED, etc.
    category VARCHAR(30) NOT NULL, -- USER, SYSTEM, MEDIA, BILLING, SECURITY, DINSTAR
    target_type VARCHAR(30),       -- USER, GROUP, MEDIA, CONFIG, etc.
    target_id VARCHAR(100),        -- reference to the affected entity
    description TEXT,
    metadata JSONB,                 -- before/after values, context
    ip_address INET,
    user_agent TEXT,
    severity VARCHAR(10) NOT NULL DEFAULT 'INFO', -- INFO, WARNING, CRITICAL
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_audit_action_check CHECK (action IN (
        'USER_APPROVED','USER_BANNED','USER_REJECTED','USER_UNBANNED','USER_PROMOTED','USER_DEMOTED',
        'MEDIA_DELETED','MEDIA_GRANTED','MEDIA_REVOKED',
        'GROUP_CREATED','GROUP_DELETED','GROUP_MEMBER_ADDED','GROUP_MEMBER_REMOVED',
        'CONFIG_CHANGED','DINSTAR_PORT_TOGGLED','DINSTAR_SIM_SWAPPED','DINSTAR_BALANCE_RESET',
        'BILLING_RATE_CHANGED','BILLING_CDR_EXPORTED',
        'SECURITY_ALERT','LOGIN_FAILED','PERMISSION_GRANTED','PERMISSION_REVOKED',
        'BACKUP_CREATED','BACKUP_RESTORED',
        'STORY_DELETED','POST_DELETED','CALL_TERMINATED'
    )),
    CONSTRAINT admin_audit_category_check CHECK (category IN (
        'USER','SYSTEM','MEDIA','BILLING','SECURITY','DINSTAR','CONTENT','CALL'
    )),
    CONSTRAINT admin_audit_severity_check CHECK (severity IN ('INFO','WARNING','CRITICAL'))
);
CREATE INDEX IF NOT EXISTS idx_admin_audit_admin ON admin_audit_log(admin_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_action ON admin_audit_log(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_category ON admin_audit_log(category, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_severity ON admin_audit_log(severity, created_at DESC) WHERE severity = 'CRITICAL';
CREATE INDEX IF NOT EXISTS idx_admin_audit_created ON admin_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_target ON admin_audit_log(target_type, target_id);

-- ━━━━ System Analytics (Dashboard Stats) ━━━━
CREATE TABLE IF NOT EXISTS system_analytics (
    id UUID PRIMARY KEY,
    stat_date DATE NOT NULL,
    -- المستخدمين
    total_users INTEGER NOT NULL DEFAULT 0,
    new_users INTEGER NOT NULL DEFAULT 0,
    active_users_dau INTEGER NOT NULL DEFAULT 0,  -- Daily Active Users
    active_users_mau INTEGER NOT NULL DEFAULT 0,  -- Monthly Active Users
    pending_approvals INTEGER NOT NULL DEFAULT 0,
    banned_users INTEGER NOT NULL DEFAULT 0,
    -- الرسائل
    messages_sent INTEGER NOT NULL DEFAULT 0,
    messages_delivered INTEGER NOT NULL DEFAULT 0,
    messages_read INTEGER NOT NULL DEFAULT 0,
    voice_messages INTEGER NOT NULL DEFAULT 0,
    media_uploads INTEGER NOT NULL DEFAULT 0,
    media_bytes_uploaded BIGINT NOT NULL DEFAULT 0,
    -- المكالمات
    calls_total INTEGER NOT NULL DEFAULT 0,
    calls_audio INTEGER NOT NULL DEFAULT 0,
    calls_video INTEGER NOT NULL DEFAULT 0,
    calls_conference INTEGER NOT NULL DEFAULT 0,
    calls_live INTEGER NOT NULL DEFAULT 0,
    calls_pstn INTEGER NOT NULL DEFAULT 0,
    calls_duration_seconds BIGINT NOT NULL DEFAULT 0,
    -- المكالمات الفاشلة
    calls_failed INTEGER NOT NULL DEFAULT 0,
    calls_missed INTEGER NOT NULL DEFAULT 0,
    -- DINSTAR
    dinstar_active_ports INTEGER NOT NULL DEFAULT 0,
    dinstar_total_calls INTEGER NOT NULL DEFAULT 0,
    dinstar_total_duration_seconds BIGINT NOT NULL DEFAULT 0,
    dinstar_balance_remaining DECIMAL(12,2) NOT NULL DEFAULT 0,
    -- المجموعات
    groups_created INTEGER NOT NULL DEFAULT 0,
    groups_active INTEGER NOT NULL DEFAULT 0,
    -- القصص والمنشورات
    stories_posted INTEGER NOT NULL DEFAULT 0,
    stories_viewed INTEGER NOT NULL DEFAULT 0,
    posts_created INTEGER NOT NULL DEFAULT 0,
    posts_reactions INTEGER NOT NULL DEFAULT 0,
    -- التخزين
    storage_used_bytes BIGINT NOT NULL DEFAULT 0,
    media_objects_count INTEGER NOT NULL DEFAULT 0,
    -- أمان
    security_alerts INTEGER NOT NULL DEFAULT 0,
    blocked_attempts INTEGER NOT NULL DEFAULT 0,
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(stat_date)
);
CREATE INDEX IF NOT EXISTS idx_system_analytics_date ON system_analytics(stat_date DESC);

-- ━━━━ System Health (Real-time metrics) ━━━━
CREATE TABLE IF NOT EXISTS system_health (
    id UUID PRIMARY KEY,
    component VARCHAR(50) NOT NULL, -- DATABASE, REDIS, MINIO, SIGNAL, RABBITMQ, etc.
    status VARCHAR(20) NOT NULL DEFAULT 'HEALTHY', -- HEALTHY, DEGRADED, DOWN
    -- Metrics
    cpu_usage REAL,            -- 0-100
    memory_usage REAL,         -- 0-100
    disk_usage REAL,           -- 0-100
    active_connections INTEGER,
    requests_per_second REAL,
    average_response_ms REAL,
    error_rate REAL,            -- 0-100 (%)
    -- Details
    details JSONB,              -- component-specific metrics
    -- Timestamps
    last_check_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT system_health_status_check CHECK (status IN ('HEALTHY','DEGRADED','DOWN'))
);
CREATE INDEX IF NOT EXISTS idx_system_health_component ON system_health(component, last_check_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_health_status ON system_health(status, last_check_at DESC) WHERE status != 'HEALTHY';

-- ━━━━ Admin Sessions (Active admin tracking) ━━━━
CREATE TABLE IF NOT EXISTS admin_sessions (
    id UUID PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token_hash VARCHAR(128) NOT NULL, -- hashed for security
    ip_address INET,
    user_agent TEXT,
    location VARCHAR(100),     -- city/country if available
    device_info JSONB,         -- browser, OS, screen
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    terminated_at TIMESTAMP,
    termination_reason VARCHAR(50) -- LOGOUT, EXPIRED, FORCE_TERMINATED, SECURITY
);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_admin ON admin_sessions(admin_id, is_active, last_active_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_token ON admin_sessions(session_token_hash);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at) WHERE is_active = TRUE;

-- ━━━━ Feature Flags (Enable/disable features per user) ━━━━
CREATE TABLE IF NOT EXISTS feature_flags (
    id UUID PRIMARY KEY,
    flag_name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- Rollout
    rollout_percentage INTEGER NOT NULL DEFAULT 0 CHECK (rollout_percentage BETWEEN 0 AND 100),
    target_user_ids UUID[] DEFAULT '{}',
    target_groups TEXT[] DEFAULT '{}', -- e.g. {'BETA_TESTERS', 'ADMINS'}
    -- Configuration
    config JSONB,                -- flag-specific configuration
    -- Metadata
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP         -- optional expiration
);
CREATE INDEX IF NOT EXISTS idx_feature_flags_name ON feature_flags(flag_name);
CREATE INDEX IF NOT EXISTS idx_feature_flags_enabled ON feature_flags(enabled, expires_at);

-- ━━━━ User Reports (content moderation) — ترقية تراكمية لجدول V10 الموجود مسبقاً ━━━━
-- V10 أنشأ user_reports بمخطط أقدم (reported_id/details)؛ لذلك CREATE TABLE IF NOT EXISTS
-- هنا كان يتخطى الإنشاء بصمت ثم تفشل الفهارس على الأعمدة الجديدة (خطأ الإقلاع target_user_id).
-- الحل: تطوير الجدول القائم بإضافة أعمدة كيان UserReport فقط (validate يتسامح مع الأعمدة الزائدة).
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS target_user_id UUID REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS target_content_type VARCHAR(30); -- MESSAGE, POST, STORY, PROFILE, GROUP
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS target_content_id VARCHAR(100);  -- ID of the reported content
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS evidence JSONB;                  -- screenshots, message IDs, etc.
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS assigned_admin_id UUID REFERENCES users(id);
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS resolution VARCHAR(30);          -- WARNING_ISSUED, USER_BANNED, CONTENT_REMOVED, NO_ACTION
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS admin_notes TEXT;
ALTER TABLE user_reports ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
-- قيد V10 القديم على status يسمح بـ OPEN كحالة افتتاحية، بينما الكيان يبدأ بـ PENDING → نوحّد القائمتين
ALTER TABLE user_reports DROP CONSTRAINT IF EXISTS user_reports_status_check;
ALTER TABLE user_reports ADD CONSTRAINT user_reports_status_check
    CHECK (status IN ('OPEN','PENDING','REVIEWING','RESOLVED','DISMISSED'));
CREATE INDEX IF NOT EXISTS idx_user_reports_status ON user_reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_reports_reporter ON user_reports(reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_reports_target_user ON user_reports(target_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_reports_assigned ON user_reports(assigned_admin_id, status) WHERE status IN ('PENDING','REVIEWING');

-- ━━━━ System Announcements ━━━━
CREATE TABLE IF NOT EXISTS system_announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'INFO', -- INFO, WARNING, MAINTENANCE, FEATURE
    target_audience VARCHAR(20) NOT NULL DEFAULT 'ALL', -- ALL, ADMINS, USERS, SPECIFIC
    target_user_ids UUID[] DEFAULT '{}',
    -- Display
    priority INTEGER NOT NULL DEFAULT 0,  -- 0=normal, 1=high, 2=critical
    is_dismissible BOOLEAN NOT NULL DEFAULT TRUE,
    show_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    show_until TIMESTAMP,
    -- Status
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    published_by UUID REFERENCES users(id),
    published_at TIMESTAMP,
    -- Timestamps
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT announcements_type_check CHECK (type IN ('INFO','WARNING','MAINTENANCE','FEATURE','PROMO')),
    CONSTRAINT announcements_audience_check CHECK (target_audience IN ('ALL','ADMINS','USERS','SPECIFIC'))
);
CREATE INDEX IF NOT EXISTS idx_announcements_published ON system_announcements(is_published, priority DESC, show_from);
CREATE INDEX IF NOT EXISTS idx_announcements_active ON system_announcements(is_published, show_until) WHERE is_published = TRUE;

-- ━━━━ Backup History ━━━━
CREATE TABLE IF NOT EXISTS backup_history (
    id UUID PRIMARY KEY,
    backup_type VARCHAR(20) NOT NULL, -- FULL, INCREMENTAL, CONFIG_ONLY, USER_DATA
    -- Storage
    storage_location VARCHAR(500) NOT NULL, -- path/URL to backup file
    size_bytes BIGINT NOT NULL,
    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, FAILED, VERIFIED
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    -- Verification
    checksum VARCHAR(128),         -- SHA-256 of the backup file
    verified_at TIMESTAMP,
    verified_by UUID REFERENCES users(id),
    -- Restore info
    last_restored_at TIMESTAMP,
    restore_count INTEGER NOT NULL DEFAULT 0,
    -- Metadata
    triggered_by VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, MANUAL, API
    initiated_by UUID REFERENCES users(id),
    notes TEXT,
    CONSTRAINT backup_type_check CHECK (backup_type IN ('FULL','INCREMENTAL','CONFIG_ONLY','USER_DATA','MEDIA')),
    CONSTRAINT backup_status_check CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED','VERIFIED'))
);
CREATE INDEX IF NOT EXISTS idx_backup_history_type ON backup_history(backup_type, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_backup_history_status ON backup_history(status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_backup_history_completed ON backup_history(completed_at DESC) WHERE status IN ('COMPLETED','VERIFIED');

-- ━━━━ Views for Dashboard Quick Stats ━━━━
CREATE OR REPLACE VIEW v_user_stats AS
SELECT
    COUNT(*) FILTER (WHERE TRUE) AS total_users,
    COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_users,
    COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved_users,
    COUNT(*) FILTER (WHERE status = 'BANNED') AS banned_users,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours') AS new_users_24h,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '7 days') AS new_users_7d,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '30 days') AS new_users_30d
FROM users;

-- الرسائل تعيش في MongoDB (MessageDocument) ولا يوجد لها جدول PostgreSQL إطلاقاً —
-- إنشاء العرض بـ FROM messages كان سيفشل حتماً (relation does not exist).
-- عرض توافقي بقيم صفرية حتى تُغذّى إحصاءات الرسائل من طبقة التطبيق (لا مستدعي له حالياً).
CREATE OR REPLACE VIEW v_message_stats AS
SELECT
    0::BIGINT AS total_messages,
    0::BIGINT AS text_messages,
    0::BIGINT AS voice_messages,
    0::BIGINT AS image_messages,
    0::BIGINT AS video_messages,
    0::BIGINT AS file_messages,
    0::BIGINT AS messages_24h,
    0::BIGINT AS messages_7d,
    0::BIGINT AS sent_count,
    0::BIGINT AS delivered_count,
    0::BIGINT AS read_count;

CREATE OR REPLACE VIEW v_call_stats AS
SELECT
    COUNT(*) AS total_calls,
    COUNT(*) FILTER (WHERE call_type = 'VOIP_AUDIO') AS audio_calls,
    COUNT(*) FILTER (WHERE call_type = 'VOIP_VIDEO') AS video_calls,
    COUNT(*) FILTER (WHERE call_type = 'CONFERENCE') AS conference_calls,
    COUNT(*) FILTER (WHERE call_type = 'LIVE_BROADCAST') AS live_calls,
    COUNT(*) FILTER (WHERE call_type = 'PSTN_DINSTAR') AS pstn_calls,
    COUNT(*) FILTER (WHERE call_route = 'DINSTAR') AS dinstar_calls,
    COUNT(*) FILTER (WHERE call_route = 'RED') AS red_calls,
    COUNT(*) FILTER (WHERE status = 'MISSED') AS missed_calls,
    COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_calls,
    SUM(duration_ms) AS total_duration_ms,
    AVG(duration_ms) AS avg_duration_ms
FROM call_history;

-- ━━━━ Function for Auto-cleanup of Expired Records ━━━━
CREATE OR REPLACE FUNCTION cleanup_expired_admin_sessions()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    UPDATE admin_sessions
    SET is_active = FALSE, terminated_at = NOW(), termination_reason = 'EXPIRED'
    WHERE is_active = TRUE AND expires_at < NOW();
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- ━━━━ Function for Daily Analytics Rollup ━━━━
CREATE OR REPLACE FUNCTION rollup_daily_analytics(target_date DATE DEFAULT CURRENT_DATE)
RETURNS VOID AS $$
BEGIN
    INSERT INTO system_analytics (
        id, stat_date,
        total_users, new_users, messages_sent, voice_messages,
        calls_total, calls_audio, calls_video, calls_pstn,
        groups_active, storage_used_bytes
    )
    SELECT
        gen_random_uuid(), target_date,
        (SELECT COUNT(*) FROM users),
        (SELECT COUNT(*) FROM users WHERE DATE(created_at) = target_date),
        0, -- messages: تُحسب من MongoDB (لا جدول في PG)
        0, -- voice_messages: تُحسب من MongoDB
        (SELECT COUNT(*) FROM call_history WHERE DATE(started_at) = target_date),
        (SELECT COUNT(*) FROM call_history WHERE call_type = 'VOIP_AUDIO' AND DATE(started_at) = target_date),
        (SELECT COUNT(*) FROM call_history WHERE call_type = 'VOIP_VIDEO' AND DATE(started_at) = target_date),
        (SELECT COUNT(*) FROM call_history WHERE call_type = 'PSTN_DINSTAR' AND DATE(started_at) = target_date),
        (SELECT COUNT(*) FROM groups),
        0 -- storage_used_bytes: تُحسب من MinIO/MongoDB (لا جدول media_objects في PG)
    ON CONFLICT (stat_date) DO UPDATE SET
        total_users = EXCLUDED.total_users,
        new_users = EXCLUDED.new_users,
        messages_sent = EXCLUDED.messages_sent,
        voice_messages = EXCLUDED.voice_messages,
        calls_total = EXCLUDED.calls_total,
        calls_audio = EXCLUDED.calls_audio,
        calls_video = EXCLUDED.calls_video,
        calls_pstn = EXCLUDED.calls_pstn,
        groups_active = EXCLUDED.groups_active,
        storage_used_bytes = EXCLUDED.storage_used_bytes,
        updated_at = NOW();
END;
$$ LANGUAGE plpgsql;

-- ━━━━ Function to Get Admin Dashboard Summary ━━━━
CREATE OR REPLACE FUNCTION get_admin_dashboard_summary()
RETURNS TABLE (
    metric_name VARCHAR(50),
    metric_value BIGINT,
    metric_change_24h BIGINT,
    metric_change_pct NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 'total_users'::VARCHAR(50), COUNT(*)::BIGINT,
        COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours')::BIGINT,
        CASE WHEN COUNT(*) > 0
            THEN ROUND(COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours') * 100.0 / COUNT(*), 2)
            ELSE 0 END
    FROM users
    UNION ALL
    SELECT 'pending_approvals'::VARCHAR(50), COUNT(*) FILTER (WHERE status = 'PENDING')::BIGINT,
        COUNT(*) FILTER (WHERE status = 'PENDING' AND created_at > NOW() - INTERVAL '24 hours')::BIGINT,
        0
    FROM users
    UNION ALL
    SELECT 'messages_24h'::VARCHAR(50), 0::BIGINT, 0::BIGINT, 100.0 -- تُحسب من MongoDB لاحقاً
    UNION ALL
    SELECT 'calls_24h'::VARCHAR(50), COUNT(*)::BIGINT, COUNT(*)::BIGINT, 100.0
    FROM call_history WHERE started_at > NOW() - INTERVAL '24 hours'
    UNION ALL
    SELECT 'pending_reports'::VARCHAR(50), COUNT(*)::BIGINT,
        COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '24 hours')::BIGINT, 0
    FROM user_reports WHERE status = 'PENDING';
END;
$$ LANGUAGE plpgsql;

-- ━━━━ Comments for Documentation ━━━━
COMMENT ON TABLE admin_audit_log IS 'سجل شامل لكل عمليات الإدارة';
COMMENT ON TABLE system_analytics IS 'إحصائيات يومية للنظام';
COMMENT ON TABLE system_health IS 'صحة مكونات النظام في الوقت الفعلي';
COMMENT ON TABLE admin_sessions IS 'جلسات الإدارة النشطة';
COMMENT ON TABLE feature_flags IS 'أعلام الميزات للتفعيل التدريجي';
COMMENT ON TABLE user_reports IS 'بلاغات المستخدمين (مراقبة المحتوى)';
COMMENT ON TABLE system_announcements IS 'إعلانات النظام';
COMMENT ON TABLE backup_history IS 'سجل النسخ الاحتياطية';

COMMENT ON FUNCTION cleanup_expired_admin_sessions IS 'تنظيف جلسات الإدارة المنتهية';
COMMENT ON FUNCTION rollup_daily_analytics IS 'تجميع الإحصائيات اليومية';
COMMENT ON FUNCTION get_admin_dashboard_summary IS 'ملخص لوحة الإدارة';
