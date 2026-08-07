CREATE TABLE media_grants (
    object_key VARCHAR(180) NOT NULL,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grantee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    PRIMARY KEY (object_key, grantee_id),
    CHECK (owner_id <> grantee_id)
);
CREATE INDEX idx_media_grants_grantee ON media_grants(grantee_id, created_at DESC);
CREATE INDEX idx_media_grants_expiry ON media_grants(expires_at) WHERE expires_at IS NOT NULL;
