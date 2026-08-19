-- تخصيصات منافذ DINSTAR/PSTN الدائمة عبر عقد الخادم.
-- المفتاح النصي يميّز بوابات الأسطول بالـUUID، ويغطي أيضًا وضع البوابة المفردة
-- القديم دون الاعتماد على NULL في فهرس فريد.
CREATE TABLE IF NOT EXISTS gateway_port_reservations (
    gateway_key VARCHAR(80) NOT NULL,
    port_index INTEGER NOT NULL CHECK (port_index BETWEEN 0 AND 127),
    reservation_id UUID NOT NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (gateway_key, port_index)
);

CREATE INDEX IF NOT EXISTS idx_gateway_port_reservations_expiry
    ON gateway_port_reservations(expires_at);
