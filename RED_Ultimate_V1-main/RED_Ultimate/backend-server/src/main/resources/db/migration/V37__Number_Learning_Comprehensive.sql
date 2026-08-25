-- Number Learning — SMS mode + pool intelligence (comprehensive upgrade)
ALTER TABLE number_learning_config
    ADD COLUMN IF NOT EXISTS sms_mode TEXT NOT NULL DEFAULT 'OFF' CHECK (sms_mode IN ('OFF','LEARN','MAINTAIN')),
    ADD COLUMN IF NOT EXISTS sms_daily_cap_per_port INT NOT NULL DEFAULT 4 CHECK (sms_daily_cap_per_port BETWEEN 1 AND 50),
    ADD COLUMN IF NOT EXISTS sms_min_interval_minutes INT NOT NULL DEFAULT 60 CHECK (sms_min_interval_minutes BETWEEN 1 AND 1440),
    ADD COLUMN IF NOT EXISTS sms_max_interval_minutes INT NOT NULL DEFAULT 240 CHECK (sms_max_interval_minutes BETWEEN 1 AND 1440),
    ADD COLUMN IF NOT EXISTS sms_template TEXT NOT NULL DEFAULT 'مرحبا — رسالة تعلم',
    ADD COLUMN IF NOT EXISTS auto_learn_from_cdr BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_learn_from_inbound BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE number_learning_pool
    ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS success_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fail_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE number_learning_calls
    ADD COLUMN IF NOT EXISTS direction TEXT NOT NULL DEFAULT 'OUTBOUND' CHECK (direction IN ('OUTBOUND','INBOUND'));

CREATE INDEX IF NOT EXISTS idx_nl_pool_active_last_used ON number_learning_pool(active, last_used_at DESC);
