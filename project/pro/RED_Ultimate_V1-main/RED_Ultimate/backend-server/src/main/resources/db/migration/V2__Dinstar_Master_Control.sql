-- تخزين إعدادات الجهاز الرئيسية
CREATE TABLE IF NOT EXISTS dinstar_config (
    id SERIAL PRIMARY KEY,
    device_ip VARCHAR(50) NOT NULL,
    api_port INT DEFAULT 80,
    username VARCHAR(50),
    password_hash TEXT,
    sip_server_ip VARCHAR(50),
    last_sync TIMESTAMP
);

-- تفاصيل الـ 8 منافذ (8T)
CREATE TABLE IF NOT EXISTS dinstar_ports (
    port_index INT PRIMARY KEY, -- 0 to 7
    sim_number VARCHAR(20),
    operator_name VARCHAR(50),
    is_enabled BOOLEAN DEFAULT TRUE,
    total_calls BIGINT DEFAULT 0,
    total_minutes BIGINT DEFAULT 0,
    signal_threshold INT DEFAULT 20 -- الحد الأدنى للإشارة قبل التنبيه
);

-- سجل العمليات (Audit Log) لنظام Dinstar
CREATE TABLE IF NOT EXISTS dinstar_logs (
    id SERIAL PRIMARY KEY,
    event_type VARCHAR(50), -- RESTART, CONFIG_CHANGE, SMS_SENT
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
