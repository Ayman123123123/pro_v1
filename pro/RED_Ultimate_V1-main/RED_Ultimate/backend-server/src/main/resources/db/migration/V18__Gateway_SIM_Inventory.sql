-- جرد شرائح SIM التشغيلي: تسميات وتحقق يدوي فقط (لا MSISDN/IMSI كامل).
CREATE TABLE IF NOT EXISTS gateway_sim_inventory (
    gateway_id UUID NOT NULL REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER NOT NULL CHECK (port_index BETWEEN 0 AND 31),
    operator_label VARCHAR(50),
    sim_label VARCHAR(80),
    verification_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (verification_state IN ('UNKNOWN','PENDING','VERIFIED','MISMATCH','NOT_PRESENT')),
    verification_method VARCHAR(20)
        CHECK (verification_method IS NULL OR verification_method IN ('MANUAL','USSD','SMS','CALL')),
    msisdn_masked VARCHAR(16),
    verified_at TIMESTAMP,
    verified_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gateway_id, port_index),
    CHECK (msisdn_masked IS NULL OR msisdn_masked ~ '^••••[0-9]{4}$')
);

CREATE INDEX IF NOT EXISTS idx_gateway_sim_inventory_state ON gateway_sim_inventory(gateway_id, verification_state);
