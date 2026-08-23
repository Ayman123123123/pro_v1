-- ═══════════════════════════════════════════════════════════════════════════
-- V34: الربط الدائم 1:1 — حساب ↔ شريحة GSM ثابتة
-- كل حساب يملك شريحة دائمة على بوابة/منفذ محدد (16 منفذ = 8G + 8T)
-- ═══════════════════════════════════════════════════════════════════════════

-- ربط الحساب ببوابة ومنفذ محدد (شريحة دائمة)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS pstn_gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS pstn_port_index INT,
    ADD COLUMN IF NOT EXISTS pstn_number VARCHAR(20);

-- قيد: المنفذ 0..31 (8G=0..7, 8T=0..7 لكن مع بوابتين = 16 منفذ فعلي)
ALTER TABLE users
    ADD CONSTRAINT chk_users_pstn_port_range
        CHECK (pstn_port_index IS NULL OR pstn_port_index BETWEEN 0 AND 31);

-- قيد: الاتساق — إما الكل NULL أو الكل NOT NULL
ALTER TABLE users
    ADD CONSTRAINT chk_users_pstn_binding_consistency
        CHECK (
            (pstn_gateway_id IS NULL AND pstn_port_index IS NULL AND pstn_number IS NULL)
         OR (pstn_gateway_id IS NOT NULL AND pstn_port_index IS NOT NULL AND pstn_number IS NOT NULL)
        );

-- كل شريحة تخدم حساباً واحداً فقط — منع ازدواج الربط
ALTER TABLE users
    ADD CONSTRAINT uq_users_pstn_port UNIQUE (pstn_gateway_id, pstn_port_index);

-- فهارس للبحث السريع (الوارد: number → owner, والصادر: gateway+port → owner)
CREATE INDEX IF NOT EXISTS idx_users_pstn_number ON users(pstn_number) WHERE pstn_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_pstn_gateway_port ON users(pstn_gateway_id, pstn_port_index) WHERE pstn_gateway_id IS NOT NULL;

-- تعليقات للتوثيق
COMMENT ON COLUMN users.pstn_gateway_id IS 'البوابة المالكة للشريحة الدائمة (FK telecom_gateways.id)';
COMMENT ON COLUMN users.pstn_port_index IS 'منفذ الشريحة 0..7 على البوابة (0=المنفذ الأول)';
COMMENT ON COLUMN users.pstn_number IS 'رقم الشريحة الحقيقي (مثل 774xxxxxx) يظهر للمستلم الخارجي';
