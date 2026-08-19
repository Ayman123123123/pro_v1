-- V33__Sms_Messages.sql
-- مخطط جداول الرسائل النصية (SMS) وعلامات القراءة للمحادثات.
-- يُستَخدم من قبَاطع Twilio/DINSTAR لتخزين الرسائل الواردة والصادرة
-- مع حالة التسليم (queued/sent/delivered/failed) وعدّاد المحاولات.
-- owner_id يشير إلى users(id) ويُمسح عند حذف المستخدم (ON DELETE CASCADE).

CREATE TABLE IF NOT EXISTS sms_messages (
    id UUID PRIMARY KEY,
    owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
    number VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    direction VARCHAR(4) NOT NULL,
    status VARCHAR(12) NOT NULL,
    port INTEGER,
    gateway_id UUID,
    sms_parts INTEGER NOT NULL DEFAULT 1,
    error_text VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_sms_owner_created ON sms_messages(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sms_number_created ON sms_messages(number, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sms_status ON sms_messages(status, created_at DESC);

-- علامة قراءة لكل محادثة (user_id, number) → آخر رسالة مقروءة فيها.
CREATE TABLE IF NOT EXISTS sms_conversation_read (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    number VARCHAR(20) NOT NULL,
    last_read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, number)
);
