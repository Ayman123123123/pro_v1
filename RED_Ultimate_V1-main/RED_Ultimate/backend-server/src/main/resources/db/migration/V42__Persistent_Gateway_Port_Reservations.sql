-- V42 — حجز المنافذ الدائم: Postgres مصدر الحقيقة، Redis كاش سريع
-- المشكلة: portUsage كان ConcurrentHashMap في الذاكرة فقط — إعادة تشغيل واحدة
-- تُفقد كل الحجوزات فيُخصص منفذان لمكالمتين في آن واحد.
-- الحل: جدول دائم بفهرس فريد يمنع الحجز المزدوج على مستوى قاعدة البيانات
-- حتى مع تعدد نسخ الباكند، و TTL تلقائي يحرر المنفذ دون تدخل.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS gateway_port_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INT NOT NULL CHECK (port_index BETWEEN 0 AND 31),
    reserved_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    call_id VARCHAR(64) NOT NULL,
    target_number VARCHAR(32),
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    -- يمنع حجز منفذ واحد مرتين في نفس اللحظة حتى مع تزامن كامل بين نسختين
    CONSTRAINT chk_reservation_ttl CHECK (expires_at > reserved_at)
);

-- فهرس فريد على (gateway_id, port_index, call_id): كل مكالمة لها حجز واحد.
-- الانتهاء يُدار في طبقة التطبيق: قبل إدراج حجز جديد، تُحذف المنتهية لنفس المنفذ.
CREATE UNIQUE INDEX IF NOT EXISTS uq_gateway_port_reservation_call
    ON gateway_port_reservations (gateway_id, port_index, call_id);

-- فهرس للتنظيف الدوري و للاستعلام السريع عن الحجوزات النشطة لبوابة
CREATE INDEX IF NOT EXISTS idx_gateway_reservations_expires
    ON gateway_port_reservations (expires_at);
CREATE INDEX IF NOT EXISTS idx_gateway_reservations_gateway_active
    ON gateway_port_reservations (gateway_id, port_index, expires_at);

-- فهرس للمستخدم: هل لديه حجز نشط؟ (يُستخدم للتحقق من منع مكالمتين متزامنتين)
CREATE INDEX IF NOT EXISTS idx_gateway_reservations_user_active
    ON gateway_port_reservations (reserved_by_user_id, expires_at);

COMMENT ON TABLE gateway_port_reservations IS 'حجوزات المنافذ الدائمة — Postgres مصدر الحقيقة، Redis كاش للقراءة السريعة. TTL يحرر المنفذ تلقائياً';
COMMENT ON INDEX uq_gateway_port_reservation_call IS 'كل مكالمة = حجز واحد؛ تكرار call_id على نفس المنفذ ممنوع';