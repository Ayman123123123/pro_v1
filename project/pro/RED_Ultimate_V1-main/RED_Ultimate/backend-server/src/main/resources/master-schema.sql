-- 1. PostgreSQL: إدارة الهوية والسلطة (Authority)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    red_id VARCHAR(50) UNIQUE, -- المعرف الذي يمنحه المدير
    gsm_number VARCHAR(20),    -- الرقم المرتبط من DINSTAR
    status VARCHAR(20) DEFAULT 'PENDING',
    last_seen BIGINT
);

-- 2. PostgreSQL: إدارة هاردوير DINSTAR (8 SIM Slots)
CREATE TABLE dinstar_slots (
    slot_id INT PRIMARY KEY, -- 1 to 8
    operator VARCHAR(50),    -- Yemen Mobile, YOU, Sabafon
    signal_strength INT,
    is_active BOOLEAN DEFAULT TRUE
);

-- 3. MongoDB: تخزين الرسائل (للسرعة الضخمة)
-- {
--   "_id": "uuid_v7",
--   "conversationId": "cid",
--   "payload": "encrypted_bytes",
--   "sequenceNumber": 1240,
--   "expiresAt": "24h_for_stories"
-- }
