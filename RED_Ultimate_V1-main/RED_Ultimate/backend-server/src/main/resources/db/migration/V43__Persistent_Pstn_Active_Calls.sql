-- V43 — المكالمات النشطة الدائمة: ربط callId ↔ منفذ ↔ مستخدم
-- المشكلة: PstnActiveCallKeys كان Redis فقط — إعادة تشغيل Redis أو الباكند
-- تُفقد الربط فيُصبح المنفذ محجوزًا في الذاكرة لكن لا أحد يعرف لمن، فلا يُحرر
-- عند Hangup ويبقى المنفذ ميتًا حتى انتهاء TTL (30 دقيقة) بلا مكالمة.
-- الحل: جدول دائم يحفظ الربط مع فهرس فريد على call_id و TTL، و Redis كاش.

CREATE TABLE IF NOT EXISTS pstn_active_calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE SET NULL,
    port_index INT NOT NULL CHECK (port_index BETWEEN 0 AND 31),
    target_number VARCHAR(32),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_active_call_ttl CHECK (expires_at > started_at)
);

-- كل مكالمة لها سجل واحد فقط — يمنع التكرار حتى مع إعادة محاولة Asterisk
CREATE UNIQUE INDEX IF NOT EXISTS uq_pstn_active_call_id
    ON pstn_active_calls (call_id);

-- فهرس للبحث العكسي: من callId إلى userId (يُستخدم عند Hangup من AMI)
CREATE INDEX IF NOT EXISTS idx_pstn_active_call_lookup
    ON pstn_active_calls (call_id, user_id);

-- فهرس للمستخدم: call_id للمكالمات النشطة (التطبيق يحذف المنتهية قبل إدراج جديد)
CREATE INDEX IF NOT EXISTS idx_pstn_active_user_calls
    ON pstn_active_calls (user_id, expires_at);

-- فهرس للبحث عن المنافذ المشغولة لبوابة
CREATE INDEX IF NOT EXISTS idx_pstn_active_gateway_port
    ON pstn_active_calls (gateway_id, port_index, expires_at);

CREATE INDEX IF NOT EXISTS idx_pstn_active_expires
    ON pstn_active_calls (expires_at);

COMMENT ON TABLE pstn_active_calls IS 'ربط المكالمات النشطة الدائم — يضمن تحرير المنفذ حتى بعد فقدان Redis';