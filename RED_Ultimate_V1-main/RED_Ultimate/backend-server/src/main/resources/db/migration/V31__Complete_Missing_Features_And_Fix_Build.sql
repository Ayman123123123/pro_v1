-- ══════════════════════════════════════════════════════════════════
-- V26: إكمال الميزات الناقصة وإصلاح مشاكل البناء
-- Complete Missing Features & Fix Build Issues
-- ══════════════════════════════════════════════════════════════════
-- يغطي كل الفجوات المكتشفة في الخريطة التنافسية والتقرير الشامل:
-- 1. تثبيت الرسائل (Pin) 2. سجل التعديلات (Edit History) 3. القنوات/البث 4. ملاحظة لنفسي 5. فهارس ناقصة 6. إصلاحات Hibernate validate
-- ══════════════════════════════════════════════════════════════════

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 1. تثبيت الرسائل — pinned_messages
-- يسمح بتثبيت رسالة في محادثة أو مجموعة أو قناة (مثل واتساب/تيليجرام)
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS pinned_messages (
    id UUID PRIMARY KEY,
    -- النطاق: محادثة خاصة أو مجموعة أو قناة
    conversation_id VARCHAR(128),          -- للرسائل الخاصة (Mongo conversationId)
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    channel_id UUID,                        -- يُربط بجدول channels أدناه
    -- الرسالة المثبتة
    message_uuid VARCHAR(40) NOT NULL,      -- مرجع إلى MongoDB messages.uuid / group_messages.uuid
    message_type VARCHAR(20) NOT NULL DEFAULT 'PRIVATE', -- PRIVATE, GROUP, CHANNEL
    -- من ثبتها
    pinned_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pinned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- انتهاء التثبيت (اختياري)
    expires_at TIMESTAMP,
    -- ترتيب التثبيت
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pinned_scope_check CHECK (
        (conversation_id IS NOT NULL AND group_id IS NULL AND channel_id IS NULL) OR
        (conversation_id IS NULL AND group_id IS NOT NULL AND channel_id IS NULL) OR
        (conversation_id IS NULL AND group_id IS NULL AND channel_id IS NOT NULL)
    ),
    CONSTRAINT pinned_type_check CHECK (message_type IN ('PRIVATE','GROUP','CHANNEL'))
);
CREATE INDEX IF NOT EXISTS idx_pinned_conversation ON pinned_messages(conversation_id, pinned_at DESC) WHERE conversation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pinned_group ON pinned_messages(group_id, pinned_at DESC) WHERE group_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pinned_channel ON pinned_messages(channel_id, pinned_at DESC) WHERE channel_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pinned_expires ON pinned_messages(expires_at) WHERE expires_at IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pinned_message ON pinned_messages(message_uuid, COALESCE(conversation_id, group_id::text, channel_id::text));

COMMENT ON TABLE pinned_messages IS 'الرسائل المثبتة في المحادثات والمجموعات والقنوات';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 2. سجل تعديلات الرسائل — message_edit_history
-- يحفظ كل نسخة قبل التعديل للتدقيق والتراجع (E2EE: النص المشفر فقط)
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS message_edit_history (
    id UUID PRIMARY KEY,
    message_uuid VARCHAR(40) NOT NULL,
    conversation_id VARCHAR(128),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    editor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- المحتوى المشفر قبل التعديل (payload المشفر، ليس plaintext)
    previous_payload BYTEA,
    previous_edited_at TIMESTAMP,
    edited_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edit_reason VARCHAR(200),
    -- ترتيب التعديل
    edit_version INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_edit_history_message ON message_edit_history(message_uuid, edited_at DESC);
CREATE INDEX IF NOT EXISTS idx_edit_history_editor ON message_edit_history(editor_id, edited_at DESC);
CREATE INDEX IF NOT EXISTS idx_edit_history_conversation ON message_edit_history(conversation_id, edited_at DESC) WHERE conversation_id IS NOT NULL;

COMMENT ON TABLE message_edit_history IS 'سجل تعديلات الرسائل — يحفظ الحمولة المشفرة قبل كل تعديل';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 3. القنوات / البث الأحادي — channels (مثل تيليجرام/واتساب)
-- قناة: ناشر واحد أو عدة ناشرين، جمهور كبير، رسائل أحادية الاتجاه
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS channels (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(40) UNIQUE,           -- @channel_username للبحث
    description TEXT,
    avatar_media_key VARCHAR(200),
    -- المالك والمشرفون
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- الخصوصية
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    -- الإحصائيات
    subscriber_count INTEGER NOT NULL DEFAULT 0,
    message_count INTEGER NOT NULL DEFAULT 0,
    -- الإعدادات
    allow_comments BOOLEAN NOT NULL DEFAULT FALSE,
    allow_reactions BOOLEAN NOT NULL DEFAULT TRUE,
    allow_forwarding BOOLEAN NOT NULL DEFAULT TRUE,
    slow_mode_seconds INTEGER NOT NULL DEFAULT 0,
    -- الحالة
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channels_name_check CHECK (LENGTH(name) BETWEEN 2 AND 100),
    CONSTRAINT channels_slowmode_check CHECK (slow_mode_seconds BETWEEN 0 AND 3600)
);
CREATE INDEX IF NOT EXISTS idx_channels_owner ON channels(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_channels_username ON channels(username) WHERE username IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_channels_public ON channels(is_public, subscriber_count DESC) WHERE is_public = TRUE AND is_archived = FALSE;
CREATE INDEX IF NOT EXISTS idx_channels_verified ON channels(is_verified, subscriber_count DESC) WHERE is_verified = TRUE;

CREATE TABLE IF NOT EXISTS channel_members (
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'SUBSCRIBER', -- OWNER, ADMIN, MODERATOR, SUBSCRIBER
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_message_id VARCHAR(40),
    PRIMARY KEY (channel_id, user_id),
    CONSTRAINT channel_role_check CHECK (role IN ('OWNER','ADMIN','MODERATOR','SUBSCRIBER'))
);
CREATE INDEX IF NOT EXISTS idx_channel_members_user ON channel_members(user_id, joined_at DESC);
CREATE INDEX IF NOT EXISTS idx_channel_members_role ON channel_members(channel_id, role);

CREATE TABLE IF NOT EXISTS channel_invites (
    id UUID PRIMARY KEY,
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invite_code VARCHAR(20) UNIQUE NOT NULL,
    max_uses INTEGER,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT channel_invite_uses_check CHECK (max_uses IS NULL OR max_uses > 0)
);
CREATE INDEX IF NOT EXISTS idx_channel_invites_code ON channel_invites(invite_code);
CREATE INDEX IF NOT EXISTS idx_channel_invites_channel ON channel_invites(channel_id, created_at DESC);

COMMENT ON TABLE channels IS 'القنوات — بث أحادي الاتجاه لجمهور كبير';
COMMENT ON TABLE channel_members IS 'أعضاء القنوات واشتراكاتهم';
COMMENT ON TABLE channel_invites IS 'دعوات القنوات';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 4. ملاحظة لنفسي — note_to_self
-- كل مستخدم له محادثة خاصة مع نفسه (conversationId = userId:self)
-- لا يحتاج جدول جديد، فقط السماح بذلك في التحقق + فهرس
-- لكن نضيف جدول تفضيلات سريع للوصول
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS user_note_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    conversation_id VARCHAR(128) NOT NULL UNIQUE,
    is_pinned BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE user_note_settings IS 'إعدادات محادثة ملاحظة لنفسي';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 5. إعدادات الاختفاء المرن — disappearing_messages
-- يسمح بتوقيتات مرنة: 30 ثانية، 1 دقيقة، 5 دقائق، 1 ساعة، 1 يوم، 1 أسبوع، بعد القراءة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS disappearing_settings (
    id UUID PRIMARY KEY,
    -- النطاق
    conversation_id VARCHAR(128),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    channel_id UUID REFERENCES channels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE, -- إعداد افتراضي للمستخدم
    -- التوقيت
    disappear_after_seconds INTEGER NOT NULL, -- 0 = معطل، 30، 60، 300، 3600، 86400، 604800، -1 = بعد القراءة
    mode VARCHAR(20) NOT NULL DEFAULT 'AFTER_SEND', -- AFTER_SEND, AFTER_READ
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT disappear_seconds_check CHECK (disappear_after_seconds IN (0, 30, 60, 300, 900, 1800, 3600, 14400, 86400, 604800, -1)),
    CONSTRAINT disappear_mode_check CHECK (mode IN ('AFTER_SEND','AFTER_READ'))
);
CREATE INDEX IF NOT EXISTS idx_disappearing_conversation ON disappearing_settings(conversation_id) WHERE conversation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_disappearing_group ON disappearing_settings(group_id) WHERE group_id IS NOT NULL;

COMMENT ON TABLE disappearing_settings IS 'إعدادات الرسائل ذاتية الاختفاء بتوقيتات مرنة';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 6. إصلاحات Hibernate validate — أعمدة ناقصة أو أنواع غير متطابقة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- إصلاح طول red_id في كيان UserAccount: الكيان كان length=32 بينما DB صار VARCHAR(5) في V24
-- الحل: نضمن أن DB يقبل 32 لكن القيد يفرض 5 أرقام — نوسع العمود مؤقتًا ثم نضيق مع الحفاظ على القيد
-- (لا نلمس القيد ck_users_red_id_format الموجود، فقط نضمن عدم فشل validate بسبب الطول)
-- ملاحظة: لا نغير القيد، فقط نضمن أن العمود VARCHAR(32) ليطابق الكيان، والقيد يفرض 5 أرقام
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='red_id' AND character_maximum_length=5) THEN
        ALTER TABLE users ALTER COLUMN red_id TYPE VARCHAR(32);
        -- إعادة القيد بعد التوسيع
        ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_red_id_format;
        ALTER TABLE users ADD CONSTRAINT ck_users_red_id_format CHECK (red_id ~ '^[1-9][0-9]{4}$');
    END IF;
END $$;

-- ضمان وجود عمود avatar_color (V19) و avatar_url/bio (V25) — قد تكون مفقودة في بيئات قديمة
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_color VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(280);
ALTER TABLE users ADD COLUMN IF NOT EXISTS theme_preference VARCHAR(30) NOT NULL DEFAULT 'SOVEREIGN_DARK';
ALTER TABLE users ADD COLUMN IF NOT EXISTS accent_color VARCHAR(20) NOT NULL DEFAULT 'CYAN';
ALTER TABLE users ADD COLUMN IF NOT EXISTS font_scale REAL NOT NULL DEFAULT 1.0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS chat_bubble_style VARCHAR(20) NOT NULL DEFAULT 'ROUNDED';
ALTER TABLE users ADD COLUMN IF NOT EXISTS language VARCHAR(10) NOT NULL DEFAULT 'ar';
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_rtl BOOLEAN NOT NULL DEFAULT TRUE;

-- إصلاح جدول saved_messages — قد يكون ناقصًا في بيئات قديمة (V20 أنشأه لكن IF NOT EXISTS قد فشل)
CREATE TABLE IF NOT EXISTS saved_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_id UUID NOT NULL,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    collection VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
    notes TEXT
);
CREATE INDEX IF NOT EXISTS idx_saved_messages_user ON saved_messages(user_id, saved_at DESC);

-- ضمان وجود جداول الملصقات (قد تفشل في V20 بسبب ترتيب الهجرات)
CREATE TABLE IF NOT EXISTS sticker_packs (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    creator_id UUID REFERENCES users(id) ON DELETE SET NULL,
    is_official BOOLEAN NOT NULL DEFAULT FALSE,
    cover_media_key VARCHAR(200) NOT NULL,
    preview_media_key VARCHAR(200),
    sticker_count INTEGER NOT NULL DEFAULT 0,
    total_downloads INTEGER NOT NULL DEFAULT 0,
    is_free BOOLEAN NOT NULL DEFAULT TRUE,
    price_cents INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS stickers (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES sticker_packs(id) ON DELETE CASCADE,
    name VARCHAR(100),
    media_key VARCHAR(200) NOT NULL,
    emoji_tags TEXT[] DEFAULT '{}',
    display_order INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS user_sticker_packs (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    pack_id UUID NOT NULL REFERENCES sticker_packs(id) ON DELETE CASCADE,
    installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, pack_id)
);

-- تفعيل امتداد pg_trgm للبحث التقريبي (يجب قبل فهارس gin_trgm_ops)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 7. فهارس أداء إضافية — تسريع الاستعلامات الحرجة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE INDEX IF NOT EXISTS idx_messages_delivery_receipts_message ON message_delivery_receipts(message_uuid);
CREATE INDEX IF NOT EXISTS idx_users_status_approved ON users(status, created_at DESC) WHERE status = 'APPROVED';
CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON users USING gin (username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_groups_owner ON groups(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_history_started_desc ON call_history(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_action_created ON audit_events(action, created_at DESC);

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 8. بيانات افتراضية — صفوف مطلوبة للتشغيل
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- إعدادات النظام الافتراضية (إن لم تكن موجودة)
INSERT INTO system_settings(setting_key, setting_value) VALUES
    ('pin.max_per_conversation', '5'),
    ('pin.max_per_group', '10'),
    ('pin.max_per_channel', '20'),
    ('edit.time_limit_seconds', '86400'),
    ('edit.max_versions', '10'),
    ('disappearing.default_seconds', '0'),
    ('channel.max_members', '200000'),
    ('channel.username_min_length', '5')
ON CONFLICT (setting_key) DO NOTHING;

-- قناة افتراضية عامة للاختبار (يونس الرسمية)
INSERT INTO channels(id, name, username, description, owner_id, is_public, is_verified, allow_comments, allow_reactions)
SELECT
    '00000000-0000-0000-0000-000000000010'::uuid,
    'قناة يونس الرسمية',
    'younes_official',
    'القناة الرسمية لمنصة يونس — أخبار وتحديثات',
    (SELECT id FROM users WHERE role='ADMIN' LIMIT 1),
    TRUE, TRUE, TRUE, TRUE
WHERE EXISTS (SELECT 1 FROM users WHERE role='ADMIN' LIMIT 1)
ON CONFLICT (id) DO NOTHING;

-- علم ميزة: تفعيل الميزات الجديدة افتراضيًا
INSERT INTO feature_flags(id, flag_name, description, enabled, rollout_percentage) VALUES
    ('26000000-0000-0000-0000-000000000001', 'pinned_messages', 'تثبيت الرسائل في المحادثات', TRUE, 100),
    ('26000000-0000-0000-0000-000000000002', 'message_edit_history', 'سجل تعديلات الرسائل', TRUE, 100),
    ('26000000-0000-0000-0000-000000000003', 'channels', 'القنوات والبث الأحادي', TRUE, 100),
    ('26000000-0000-0000-0000-000000000004', 'disappearing_flexible', 'اختفاء مرن بتوقيتات متعددة', TRUE, 100),
    ('26000000-0000-0000-0000-000000000005', 'note_to_self', 'محادثة ملاحظة لنفسي', TRUE, 100)
ON CONFLICT (flag_name) DO UPDATE SET enabled=EXCLUDED.enabled, rollout_percentage=EXCLUDED.rollout_percentage, updated_at=CURRENT_TIMESTAMP;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 9. دوال مساعدة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- دالة تنظيف الرسائل المثبتة المنتهية
CREATE OR REPLACE FUNCTION cleanup_expired_pins() RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM pinned_messages WHERE expires_at IS NOT NULL AND expires_at < NOW();
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_pins IS 'تنظيف الرسائل المثبتة المنتهية الصلاحية';

-- دالة إحصاء الرسائل المثبتة لكل محادثة
CREATE OR REPLACE FUNCTION count_pinned_for_conversation(p_conversation_id VARCHAR) RETURNS INTEGER AS $$
BEGIN
    RETURN (SELECT COUNT(*)::INTEGER FROM pinned_messages WHERE conversation_id = p_conversation_id);
END;
$$ LANGUAGE plpgsql;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 10. تعليقات توثيقية
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COMMENT ON TABLE pinned_messages IS 'V26: الرسائل المثبتة — يدعم تثبيت 5/10/20 رسالة حسب النطاق';
COMMENT ON TABLE message_edit_history IS 'V26: سجل تعديلات الرسائل — يحتفظ بالحمولة المشفرة قبل كل تعديل';
COMMENT ON TABLE channels IS 'V26: القنوات — بث أحادي لجمهور كبير (200k) مثل تيليجرام';
COMMENT ON TABLE disappearing_settings IS 'V26: إعدادات الاختفاء المرن — 30s إلى 1w + بعد القراءة';

