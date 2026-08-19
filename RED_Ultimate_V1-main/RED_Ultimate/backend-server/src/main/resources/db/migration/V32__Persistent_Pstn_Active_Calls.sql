-- علاقة مكالمة PSTN الصادرة بالمنفذ المحجوز ومالكها لا يجوز أن تبقى في JVM.
CREATE TABLE IF NOT EXISTS pstn_active_calls (
    call_id VARCHAR(80) PRIMARY KEY,
    owner_red_id VARCHAR(40) NOT NULL,
    gateway_key VARCHAR(80) NOT NULL,
    port_index INTEGER NOT NULL CHECK (port_index BETWEEN 0 AND 127),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pstn_active_calls_expiry
    ON pstn_active_calls(expires_at);
CREATE INDEX IF NOT EXISTS idx_pstn_active_calls_owner
    ON pstn_active_calls(owner_red_id, created_at DESC);
