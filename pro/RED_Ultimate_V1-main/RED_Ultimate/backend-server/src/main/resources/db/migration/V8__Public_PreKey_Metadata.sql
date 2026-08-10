ALTER TABLE user_devices ADD COLUMN IF NOT EXISTS registration_id INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_devices ADD COLUMN IF NOT EXISTS protocol_device_id INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_devices ADD COLUMN IF NOT EXISTS signed_pre_key_id INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_devices ADD COLUMN IF NOT EXISTS kyber_pre_key_id INTEGER NOT NULL DEFAULT 0;

-- Give legacy rows distinct temporary protocol IDs before creating the uniqueness constraint.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at, id) AS device_number
    FROM user_devices
)
UPDATE user_devices d SET protocol_device_id = ranked.device_number
FROM ranked WHERE d.id = ranked.id AND d.protocol_device_id = 0;

-- Pre-V8 clients may have uploaded serialized private pre-key records. Revoke them defensively.
UPDATE user_devices
SET status = 'REVOKED', revoked_at = CURRENT_TIMESTAMP,
    authorization_certificate = NULL, certificate_expires_at = NULL
WHERE registration_id = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_protocol_device ON user_devices(user_id, protocol_device_id);
