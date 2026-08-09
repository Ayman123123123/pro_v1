CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_settings(setting_key, setting_value) VALUES
    ('brand.name.ar', 'يونس'),
    ('brand.name.en', 'YOUNES'),
    ('public_id.prefix', 'YNS')
ON CONFLICT (setting_key) DO UPDATE SET setting_value=EXCLUDED.setting_value, updated_at=CURRENT_TIMESTAMP;

UPDATE users
SET full_name='YOUNES Administrator', updated_at=CURRENT_TIMESTAMP
WHERE role='ADMIN' AND full_name='RED Administrator';

-- Existing RED-* IDs remain valid permanently because they are embedded in device certificates,
-- ciphertext routing metadata and audit trails. New identities use YNS-*.
