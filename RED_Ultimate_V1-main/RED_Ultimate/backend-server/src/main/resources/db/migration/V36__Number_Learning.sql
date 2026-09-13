-- Number Learning (Call mode) — محرك سلوك بشري لمكالمات تعلّم الأرقام عبر DINSTAR/Asterisk
CREATE TABLE IF NOT EXISTS number_learning_config (
    id                     INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    mode                   TEXT NOT NULL DEFAULT 'OFF' CHECK (mode IN ('OFF','LEARN','MAINTAIN')),
    window_start_minute    INT NOT NULL DEFAULT 480  CHECK (window_start_minute BETWEEN 0 AND 1439),
    window_end_minute      INT NOT NULL DEFAULT 1320 CHECK (window_end_minute BETWEEN 0 AND 1439),
    min_duration_seconds   INT NOT NULL DEFAULT 25   CHECK (min_duration_seconds BETWEEN 3 AND 600),
    max_duration_seconds   INT NOT NULL DEFAULT 90   CHECK (max_duration_seconds BETWEEN 3 AND 900),
    min_interval_minutes   INT NOT NULL DEFAULT 45   CHECK (min_interval_minutes BETWEEN 1 AND 1440),
    max_interval_minutes   INT NOT NULL DEFAULT 180  CHECK (max_interval_minutes BETWEEN 1 AND 1440),
    daily_cap_per_port     INT NOT NULL DEFAULT 6    CHECK (daily_cap_per_port BETWEEN 1 AND 100),
    enabled_ports          TEXT NOT NULL DEFAULT '', -- فارغ = كل المنافذ؛ وإلا CSV مثل "0,2,5"
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             UUID,
    CONSTRAINT nl_window_ok CHECK (window_start_minute <> window_end_minute),
    CONSTRAINT nl_duration_ok CHECK (max_duration_seconds >= min_duration_seconds),
    CONSTRAINT nl_interval_ok CHECK (max_interval_minutes >= min_interval_minutes)
);

CREATE TABLE IF NOT EXISTS number_learning_pool (
    id        UUID PRIMARY KEY,
    number    TEXT NOT NULL UNIQUE,
    label     TEXT,
    source    TEXT NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL','CDR','INBOUND')),
    active    BOOLEAN NOT NULL DEFAULT TRUE,
    added_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS number_learning_calls (
    id               UUID PRIMARY KEY,
    port             INT,
    number           TEXT NOT NULL,
    correlation_id   TEXT,
    mode             TEXT NOT NULL,
    status           TEXT NOT NULL CHECK (status IN ('ORIGINATED','COMPLETED','FAILED','CAPPED')),
    duration_seconds INT,
    error            TEXT,
    started_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_eligible_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nl_calls_started_at ON number_learning_calls(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_nl_calls_port_time ON number_learning_calls(port, started_at DESC);

INSERT INTO number_learning_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;
