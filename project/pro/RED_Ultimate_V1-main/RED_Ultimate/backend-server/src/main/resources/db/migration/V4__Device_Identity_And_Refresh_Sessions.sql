CREATE TABLE user_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(100) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    identity_key BYTEA NOT NULL,
    signed_pre_key BYTEA NOT NULL,
    kyber_pre_key BYTEA NOT NULL,
    signed_pre_key_signature BYTEA NOT NULL,
    kyber_pre_key_signature BYTEA NOT NULL,
    identity_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    authorization_certificate VARCHAR(4096),
    certificate_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    revoked_at TIMESTAMP,
    CONSTRAINT user_devices_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED')),
    CONSTRAINT uq_user_identity_fingerprint UNIQUE (user_id, identity_fingerprint)
);

CREATE INDEX idx_user_devices_user_status ON user_devices(user_id, status);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES user_devices(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    replaced_by UUID
);

CREATE INDEX idx_refresh_sessions_user_active ON refresh_sessions(user_id, revoked_at);
CREATE INDEX idx_refresh_sessions_device_active ON refresh_sessions(device_id, revoked_at);
