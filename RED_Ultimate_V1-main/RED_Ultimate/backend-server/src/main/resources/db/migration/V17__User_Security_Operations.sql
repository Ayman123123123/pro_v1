-- عمليات الأمان: إعادة تعيين كلمة مرور مؤقتة + المسح عن بُعد للأجهزة المُدارة.
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_issued_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS remote_wipe_status VARCHAR(20) NOT NULL DEFAULT 'NONE'
    CHECK (remote_wipe_status IN ('NONE','REQUESTED','ACKNOWLEDGED','COMPLETED','FAILED'));
ALTER TABLE users ADD COLUMN IF NOT EXISTS remote_wipe_requested_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS remote_wipe_completed_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS managed_device_wipe_allowed BOOLEAN NOT NULL DEFAULT FALSE;
