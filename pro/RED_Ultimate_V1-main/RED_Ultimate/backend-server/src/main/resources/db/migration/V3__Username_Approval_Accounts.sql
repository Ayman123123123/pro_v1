-- RED accounts do not require a phone number or SIM card.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS red_id VARCHAR(32);
ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE users ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen BIGINT;

UPDATE users
SET red_id = COALESCE(red_id, 'RED-LEGACY-' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12))),
    username = COALESCE(username, 'legacy_' || LOWER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12))),
    full_name = COALESCE(full_name, 'RED User')
WHERE red_id IS NULL OR username IS NULL OR full_name IS NULL;

ALTER TABLE users ALTER COLUMN red_id SET NOT NULL;
ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_red_id ON users (red_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_lower ON users (LOWER(username));
CREATE INDEX IF NOT EXISTS idx_users_status_created ON users (status, created_at);

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'BANNED'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'ADMIN'));
