-- ══════════════════════════════════════════════════════════════════
-- V50: اكتمال المحادثات — قوائم البث، مرآة التفاعلات، دليل المتصلين
-- Chat completeness — broadcast lists, reaction mirror, caller directory
-- ══════════════════════════════════════════════════════════════════
-- آمنة تمامًا: إنشاء فقط مع IF NOT EXISTS، بلا تعديل لأي جدول قائم.
-- 1. broadcast_lists + broadcast_members — قوائم بث (توزيع E2EE من العميل)
-- 2. message_reactions — مرآة Postgres للتفاعلات المخزنة في Mongo
-- 3. caller_directory + phone_spam_reports — بحث عكسي عن الأرقام + بلاغات إزعاج
-- ══════════════════════════════════════════════════════════════════

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 1. قوائم البث — Broadcast Lists (مثل واتساب)
-- المالك يوزع من جهازه (E2EE fan-out)؛ الخادم يحفظ العضوية فقط.
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS broadcast_lists (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT broadcast_lists_name_check CHECK (char_length(name) BETWEEN 1 AND 100)
);
CREATE INDEX IF NOT EXISTS idx_broadcast_lists_owner ON broadcast_lists(owner_id, created_at DESC);

CREATE TABLE IF NOT EXISTS broadcast_members (
    list_id UUID NOT NULL REFERENCES broadcast_lists(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (list_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_broadcast_members_user ON broadcast_members(user_id);

COMMENT ON TABLE broadcast_lists IS 'V50: قوائم البث — العضوية فقط، التوزيع E2EE من جهاز المالك';
COMMENT ON TABLE broadcast_members IS 'V50: أعضاء قوائم البث';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 2. مرآة التفاعلات — Message Reactions (المصدر: Mongo reactions[])
-- تفاعل واحد لكل مستخدم على كل رسالة (PRIMARY KEY(message_uuid, user_id)).
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS message_reactions (
    message_uuid TEXT NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji TEXT NOT NULL,
    reacted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_uuid, user_id),
    CONSTRAINT message_reactions_emoji_check CHECK (char_length(emoji) BETWEEN 1 AND 16)
);
CREATE INDEX IF NOT EXISTS idx_message_reactions_message ON message_reactions(message_uuid);

COMMENT ON TABLE message_reactions IS 'V50: مرآة Postgres لتفاعلات الرسائل (المصدر MongoDB)';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 3. دليل المتصلين — Caller Directory + بلاغات الإزعاج
-- بحث عكسي عن أرقام GSM (يكمل passthrough الخام في DinstarEventListener).
-- phone بلا FK في البلاغات عمدًا: الإبلاغ عن رقم مجهول يجب أن ينجح
-- بلا قيود ترتيب (السطر يُنشأ في caller_directory أولًا من المتحكم).
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS caller_directory (
    phone TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    spam_score INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT caller_directory_spam_check CHECK (spam_score >= 0),
    CONSTRAINT caller_directory_name_check CHECK (char_length(display_name) BETWEEN 1 AND 100)
);

CREATE TABLE IF NOT EXISTS phone_spam_reports (
    id SERIAL PRIMARY KEY,
    phone TEXT NOT NULL,
    reporter UUID REFERENCES users(id) ON DELETE SET NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT phone_spam_reason_check CHECK (reason IS NULL OR char_length(reason) <= 500)
);
CREATE INDEX IF NOT EXISTS idx_spam_reports_phone ON phone_spam_reports(phone);
CREATE INDEX IF NOT EXISTS idx_spam_reports_reporter ON phone_spam_reports(reporter) WHERE reporter IS NOT NULL;

COMMENT ON TABLE caller_directory IS 'V50: دليل المتصلين — بحث عكسي عن أرقام GSM مع درجة إزعاج مجتمعية';
COMMENT ON TABLE phone_spam_reports IS 'V50: بلاغات الإزعاج عن أرقام الهاتف';
