-- Public one-time pre-keys only. Private key material is generated and retained on Android.
CREATE TABLE one_time_ec_prekeys (
    device_id UUID NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL CHECK (key_id >= 0),
    public_key BYTEA NOT NULL CHECK (octet_length(public_key) BETWEEN 16 AND 4096),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at TIMESTAMP,
    PRIMARY KEY (device_id, key_id)
);

CREATE TABLE one_time_kyber_prekeys (
    device_id UUID NOT NULL REFERENCES user_devices(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL CHECK (key_id >= 0),
    public_key BYTEA NOT NULL CHECK (octet_length(public_key) BETWEEN 32 AND 16384),
    signature BYTEA NOT NULL CHECK (octet_length(signature) BETWEEN 32 AND 512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at TIMESTAMP,
    PRIMARY KEY (device_id, key_id)
);

CREATE INDEX idx_ec_prekeys_available ON one_time_ec_prekeys(device_id, created_at, key_id) WHERE consumed_at IS NULL;
CREATE INDEX idx_kyber_prekeys_available ON one_time_kyber_prekeys(device_id, created_at, key_id) WHERE consumed_at IS NULL;
