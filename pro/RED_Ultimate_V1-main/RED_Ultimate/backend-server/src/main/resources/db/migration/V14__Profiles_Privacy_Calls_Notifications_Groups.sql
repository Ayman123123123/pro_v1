-- ══════════════════════════════════════════════════════════════════
-- V14: Call History, User Profiles, Privacy Settings, Notifications
-- PostgreSQL — البيانات العلائقية التي تحتاج JOINs و ACID
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ ملف المستخدم الشخصي ━━━━
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_media_key VARCHAR(200);
ALTER TABLE users ADD COLUMN IF NOT EXISTS about_text VARCHAR(300);
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_type VARCHAR(20) NOT NULL DEFAULT 'ONLINE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_custom_text VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_visible_to VARCHAR(20) NOT NULL DEFAULT 'EVERYONE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS theme_preference VARCHAR(30) NOT NULL DEFAULT 'SOVEREIGN_DARK';
ALTER TABLE users ADD COLUMN IF NOT EXISTS accent_color VARCHAR(20) NOT NULL DEFAULT 'CYAN';
ALTER TABLE users ADD COLUMN IF NOT EXISTS font_scale REAL NOT NULL DEFAULT 1.0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS chat_bubble_style VARCHAR(20) NOT NULL DEFAULT 'ROUNDED';
ALTER TABLE users ADD COLUMN IF NOT EXISTS language VARCHAR(10) NOT NULL DEFAULT 'ar';
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_rtl BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE users ADD CONSTRAINT users_status_type_check
    CHECK (status_type IN ('ONLINE','OFFLINE','BUSY','AWAY','DO_NOT_DISTURB','INVISIBLE'));
ALTER TABLE users ADD CONSTRAINT users_status_visible_to_check
    CHECK (status_visible_to IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY'));
ALTER TABLE users ADD CONSTRAINT users_theme_preference_check
    CHECK (theme_preference IN ('SOVEREIGN_DARK','SOVEREIGN_LIGHT','OLED_BLACK','AUTO','YEMENI_GOLD','OCEAN_BLUE','ROYAL_PURPLE','EMERALD'));
ALTER TABLE users ADD CONSTRAINT users_accent_color_check
    CHECK (accent_color IN ('CYAN','GOLD','RED','PURPLE','GREEN','ORANGE','PINK'));

-- ━━━━ إعدادات الخصوصية التفصيلية ━━━━
CREATE TABLE IF NOT EXISTS user_privacy_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_seen VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    online_status VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    profile_photo VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    about VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    status VARCHAR(20) NOT NULL DEFAULT 'CONTACTS',
    read_receipts VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    calls VARCHAR(20) NOT NULL DEFAULT 'CONTACTS',
    groups_add VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    live_location VARCHAR(20) NOT NULL DEFAULT 'NOBODY',
    -- أعمدة التحقق
    CONSTRAINT privacy_level_check CHECK (
        last_seen IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        online_status IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        profile_photo IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        about IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        status IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        read_receipts IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        calls IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        groups_add IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY') AND
        live_location IN ('EVERYONE','CONTACTS','CONTACTS_EXCEPT','ONLY_SHARE_WITH','NOBODY')
    )
);

-- استثناءات الخصوصية (CONTACTS_EXCEPT / ONLY_SHARE_WITH)
CREATE TABLE IF NOT EXISTS privacy_exceptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting VARCHAR(30) NOT NULL, -- last_seen, profile_photo, etc.
    exception_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_privacy_exception UNIQUE (user_id, setting, exception_user_id)
);
CREATE INDEX idx_privacy_exceptions_user_setting ON privacy_exceptions(user_id, setting);

-- ━━━━ سجل المكالمات الموحد ━━━━
CREATE TABLE IF NOT EXISTS call_history (
    id UUID PRIMARY KEY,
    caller_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    callee_id UUID REFERENCES users(id) ON DELETE CASCADE,
    -- لغير المسجلين (أرقام PSTN)
    callee_phone VARCHAR(30),
    call_type VARCHAR(20) NOT NULL, -- VOIP_AUDIO, VOIP_VIDEO, CONFERENCE, LIVE_BROADCAST, PSTN_DINSTAR, AUDIO_SPACE
    call_route VARCHAR(10) NOT NULL DEFAULT 'RED', -- RED = VoIP, DINSTAR = PSTN
    direction VARCHAR(10) NOT NULL, -- INCOMING, OUTGOING
    status VARCHAR(15) NOT NULL DEFAULT 'RINGING', -- RINGING, ACTIVE, ENDED, MISSED, FAILED, ON_HOLD
    duration_ms BIGINT NOT NULL DEFAULT 0,
    -- PSTN-specific
    dinstar_port INTEGER,
    signal_strength INTEGER CHECK (signal_strength BETWEEN 0 AND 100),
    -- Conference/Live-specific
    max_participants INTEGER,
    viewer_count INTEGER DEFAULT 0,
    -- Recording
    is_recorded BOOLEAN NOT NULL DEFAULT FALSE,
    recording_media_key VARCHAR(200),
    -- Timestamps
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Constraints
    CONSTRAINT call_type_check CHECK (call_type IN ('VOIP_AUDIO','VOIP_VIDEO','CONFERENCE','LIVE_BROADCAST','PSTN_DINSTAR','AUDIO_SPACE')),
    CONSTRAINT call_route_check CHECK (call_route IN ('RED','DINSTAR')),
    CONSTRAINT call_direction_check CHECK (direction IN ('INCOMING','OUTGOING')),
    CONSTRAINT call_status_check CHECK (status IN ('RINGING','CONNECTING','ACTIVE','ON_HOLD','ENDED','MISSED','FAILED'))
);
CREATE INDEX idx_call_history_caller ON call_history(caller_id, started_at DESC);
CREATE INDEX idx_call_history_callee ON call_history(callee_id, started_at DESC);
CREATE INDEX idx_call_history_type ON call_history(call_type, started_at DESC);
CREATE INDEX idx_call_history_status ON call_history(status, started_at DESC);

-- مشاركو المكالمات الجماعية
CREATE TABLE IF NOT EXISTS call_participants (
    call_id UUID NOT NULL REFERENCES call_history(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP,
    role VARCHAR(20) NOT NULL DEFAULT 'PARTICIPANT', -- HOST, SPEAKER, LISTENER, PARTICIPANT
    PRIMARY KEY (call_id, user_id)
);

-- ━━━━ إشعارات المستخدم ━━━━
CREATE TABLE IF NOT EXISTS user_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL, -- NEW_MESSAGE, INCOMING_CALL, MISSED_CALL, GROUP_INVITE, STORY_VIEW, SECURITY_ALERT, etc.
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    -- المرسل
    sender_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sender_name VARCHAR(100),
    -- الربط
    thread_id VARCHAR(100), -- معرف المحادثة/المكالمة/المجموعة
    group_id UUID,
    -- الحالة
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL', -- URGENT, HIGH, NORMAL, LOW
    -- الإجراءات
    action_label VARCHAR(50),
    action_data JSONB,
    secondary_action_label VARCHAR(50),
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    -- Constraints
    CONSTRAINT notif_type_check CHECK (type IN (
        'NEW_MESSAGE','GROUP_MESSAGE','MENTION',
        'INCOMING_CALL','MISSED_CALL','PSTN_CALL',
        'STORY_VIEW','STORY_REPLY',
        'GROUP_INVITE','GROUP_UPDATE','ROLE_CHANGE',
        'LIVE_STARTED','SPACE_STARTED',
        'SECURITY_ALERT','DEVICE_NEW','UPDATE_AVAILABLE',
        'DINSTAR_STATUS','DINSTAR_ALERT'
    )),
    CONSTRAINT notif_priority_check CHECK (priority IN ('URGENT','HIGH','NORMAL','LOW'))
);
CREATE INDEX idx_notifications_user_unread ON user_notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_user_type ON user_notifications(user_id, type, created_at DESC);
CREATE INDEX idx_notifications_created ON user_notifications(created_at DESC);

-- ━━━━ تفضيلات الإشعارات ━━━━
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    messages BOOLEAN NOT NULL DEFAULT TRUE,
    calls BOOLEAN NOT NULL DEFAULT TRUE,
    groups BOOLEAN NOT NULL DEFAULT TRUE,
    stories BOOLEAN NOT NULL DEFAULT TRUE,
    live BOOLEAN NOT NULL DEFAULT TRUE,
    system BOOLEAN NOT NULL DEFAULT TRUE,
    dinstar BOOLEAN NOT NULL DEFAULT TRUE,
    security BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start TIME, -- مثال: 22:00
    quiet_hours_end TIME    -- مثال: 08:00
);

-- ━━━━ المجموعات المتقدمة (SQL للعلاقات + MongoDB للمحتوى) ━━━━
ALTER TABLE groups ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE groups ADD COLUMN IF NOT EXISTS avatar_media_key VARCHAR(200);
ALTER TABLE groups ADD COLUMN IF NOT EXISTS privacy VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE groups ADD COLUMN IF NOT EXISTS created_by_red_id VARCHAR(32);
ALTER TABLE groups ADD COLUMN IF NOT EXISTS max_members INTEGER NOT NULL DEFAULT 256;
ALTER TABLE groups ADD COLUMN IF NOT EXISTS is_announcement BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE groups ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE groups ADD CONSTRAINT groups_privacy_check
    CHECK (privacy IN ('PUBLIC','PRIVATE','SECRET'));

-- تحديث أدوار المجموعات
ALTER TABLE group_members ADD COLUMN IF NOT EXISTS custom_title VARCHAR(50);
ALTER TABLE group_members ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE group_members ADD COLUMN IF NOT EXISTS is_muted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE group_members ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

-- Drop old constraint if exists and add new one
ALTER TABLE group_members DROP CONSTRAINT IF EXISTS group_members_role_check;
ALTER TABLE group_members ADD CONSTRAINT group_members_role_check
    CHECK (role IN ('OWNER','ADMIN','MODERATOR','MEMBER'));

-- مميزات المجموعة
CREATE TABLE IF NOT EXISTS group_features (
    group_id UUID PRIMARY KEY REFERENCES groups(id) ON DELETE CASCADE,
    messages BOOLEAN NOT NULL DEFAULT TRUE,
    media BOOLEAN NOT NULL DEFAULT TRUE,
    voice_notes BOOLEAN NOT NULL DEFAULT TRUE,
    polls BOOLEAN NOT NULL DEFAULT TRUE,
    calls BOOLEAN NOT NULL DEFAULT TRUE,
    live BOOLEAN NOT NULL DEFAULT FALSE,
    links BOOLEAN NOT NULL DEFAULT TRUE,
    files BOOLEAN NOT NULL DEFAULT TRUE
);

-- دعوات المجموعات
CREATE TABLE IF NOT EXISTS group_invites (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invitee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED, EXPIRED
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT group_invite_status_check CHECK (status IN ('PENDING','ACCEPTED','REJECTED','EXPIRED'))
);
CREATE INDEX idx_group_invites_invitee ON group_invites(invitee_id, status, created_at DESC);

-- ━━━━ المشاهدون للقصص ━━━━
CREATE TABLE IF NOT EXISTS story_viewers (
    story_id VARCHAR(40) NOT NULL, -- مرتبط بـ MongoDB StoryDocument
    viewer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reaction VARCHAR(10), -- ❤️ 🔥 😢 etc.
    PRIMARY KEY (story_id, viewer_id)
);
CREATE INDEX idx_story_viewers_story ON story_viewers(story_id, viewed_at DESC);

-- ━━━━ إحصائيات الاستخدام ━━━━
CREATE TABLE IF NOT EXISTS usage_stats (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stat_date DATE NOT NULL,
    messages_sent INTEGER NOT NULL DEFAULT 0,
    messages_received INTEGER NOT NULL DEFAULT 0,
    calls_outgoing INTEGER NOT NULL DEFAULT 0,
    calls_incoming INTEGER NOT NULL DEFAULT 0,
    calls_duration_seconds INTEGER NOT NULL DEFAULT 0,
    pstn_calls INTEGER NOT NULL DEFAULT 0,
    pstn_duration_seconds INTEGER NOT NULL DEFAULT 0,
    stories_posted INTEGER NOT NULL DEFAULT 0,
    stories_viewed INTEGER NOT NULL DEFAULT 0,
    media_uploaded INTEGER NOT NULL DEFAULT 0,
    media_bytes_uploaded BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, stat_date)
);
CREATE INDEX idx_usage_stats_user_date ON usage_stats(user_id, stat_date DESC);
