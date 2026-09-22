-- RED Sovereign VoiceBridge — Database Migration
-- V1__create_stasis_app_config.sql
-- Creates the runtime configuration table for VoiceBridge Stasis applications
-- This enables DB-driven runtime configuration per tenant/organization

CREATE TABLE IF NOT EXISTS stasis_app_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_name VARCHAR(100) NOT NULL UNIQUE,
    organization_id UUID, -- References organizations table if exists
    
    -- ARI Connection Settings
    ari_host VARCHAR(255) NOT NULL DEFAULT 'asterisk',
    ari_port INTEGER NOT NULL DEFAULT 8088,
    ari_username VARCHAR(100) NOT NULL DEFAULT 'voicebridge',
    ari_password VARCHAR(255) NOT NULL,
    ari_base_url VARCHAR(500) GENERATED ALWAYS AS ('http://' || ari_host || ':' || ari_port || '/ari') STORED,
    ari_websocket_path VARCHAR(200) DEFAULT '/ari/events?app=${app_name}',
    ari_connect_timeout_ms INTEGER DEFAULT 5000,
    ari_read_timeout_ms INTEGER DEFAULT 30000,
    ari_auto_reconnect BOOLEAN DEFAULT true,
    ari_reconnect_interval_ms INTEGER DEFAULT 5000,
    
    -- RTP Settings
    rtp_bind_port INTEGER DEFAULT 12100,
    rtp_port_range_start INTEGER DEFAULT 12100,
    rtp_port_range_end INTEGER DEFAULT 13100,
    rtp_frame_size_ms INTEGER DEFAULT 20,
    rtp_sample_rate INTEGER DEFAULT 8000,
    rtp_default_codec VARCHAR(20) DEFAULT 'PCMU',
    rtp_symmetric_rtp BOOLEAN DEFAULT true,
    rtp_learn_peer BOOLEAN DEFAULT true,
    rtp_jitter_buffer_size INTEGER DEFAULT 100,
    
    -- AI Bot Settings
    bot_provider VARCHAR(50) DEFAULT 'openai-realtime',
    bot_openai_api_key VARCHAR(500),
    bot_openai_realtime_url VARCHAR(500) DEFAULT 'wss://api.openai.com/v1/realtime',
    bot_openai_model VARCHAR(100) DEFAULT 'gpt-4o-realtime-preview-2024-12-17',
    bot_system_prompt TEXT DEFAULT 'You are a helpful AI assistant for RED Sovereign communications platform.',
    bot_temperature DECIMAL(3,2) DEFAULT 0.8,
    bot_max_tokens INTEGER DEFAULT 4096,
    bot_vad_enabled BOOLEAN DEFAULT true,
    bot_vad_threshold DECIMAL(3,2) DEFAULT 0.5,
    bot_vad_prefix_padding_ms INTEGER DEFAULT 300,
    bot_vad_silence_duration_ms INTEGER DEFAULT 500,
    bot_truncation_enabled BOOLEAN DEFAULT true,
    bot_external_ws_url VARCHAR(500),
    
    -- Call Handling
    max_concurrent_calls INTEGER DEFAULT 100,
    call_timeout_seconds INTEGER DEFAULT 180,
    barge_in_enabled BOOLEAN DEFAULT true,
    recording_enabled BOOLEAN DEFAULT false,
    recording_path VARCHAR(500) DEFAULT '/var/spool/asterisk/recordings',
    
    -- Feature Flags
    feature_dtmf_handling BOOLEAN DEFAULT true,
    feature_transfer BOOLEAN DEFAULT true,
    feature_recording BOOLEAN DEFAULT true,
    feature_analytics BOOLEAN DEFAULT true,
    
    -- Metadata
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_stasis_app_config_org ON stasis_app_config(organization_id);
CREATE INDEX IF NOT EXISTS idx_stasis_app_config_active ON stasis_app_config(is_active) WHERE is_active = true;

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_stasis_app_config_updated_at ON stasis_app_config;
CREATE TRIGGER update_stasis_app_config_updated_at
    BEFORE UPDATE ON stasis_app_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Default configuration for 'voicebridge' app
INSERT INTO stasis_app_config (
    app_name,
    organization_id,
    ari_host,
    ari_port,
    ari_username,
    ari_password,
    ari_websocket_path,
    rtp_bind_port,
    rtp_port_range_start,
    rtp_port_range_end,
    rtp_default_codec,
    bot_provider,
    bot_openai_model,
    bot_system_prompt,
    max_concurrent_calls,
    call_timeout_seconds,
    barge_in_enabled,
    recording_enabled,
    description,
    is_active
) VALUES (
    'voicebridge',
    NULL,
    'asterisk',
    8088,
    'voicebridge',
    'changeme',
    '/ari/events?app=voicebridge',
    12100,
    12100,
    13100,
    'PCMU',
    'openai-realtime',
    'gpt-4o-realtime-preview-2024-12-17',
    'You are a helpful AI assistant for RED Sovereign communications platform.',
    100,
    180,
    true,
    false,
    'Default VoiceBridge configuration for RED Sovereign',
    true
) ON CONFLICT (app_name) DO UPDATE SET
    ari_host = EXCLUDED.ari_host,
    ari_port = EXCLUDED.ari_port,
    ari_username = EXCLUDED.ari_username,
    ari_password = EXCLUDED.ari_password,
    rtp_bind_port = EXCLUDED.rtp_bind_port,
    rtp_port_range_start = EXCLUDED.rtp_port_range_start,
    rtp_port_range_end = EXCLUDED.rtp_port_range_end,
    bot_provider = EXCLUDED.bot_provider,
    bot_openai_model = EXCLUDED.bot_openai_model,
    bot_system_prompt = EXCLUDED.bot_system_prompt,
    max_concurrent_calls = EXCLUDED.max_concurrent_calls,
    call_timeout_seconds = EXCLUDED.call_timeout_seconds,
    barge_in_enabled = EXCLUDED.barge_in_enabled,
    recording_enabled = EXCLUDED.recording_enabled,
    updated_at = NOW();

-- Stasis app instructions table for dynamic behavior
CREATE TABLE IF NOT EXISTS stasis_app_instruction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_name VARCHAR(100) NOT NULL,
    instruction_key VARCHAR(100) NOT NULL,
    instruction_value JSONB NOT NULL,
    priority INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(app_name, instruction_key)
);

CREATE INDEX IF NOT EXISTS idx_stasis_app_instruction_app ON stasis_app_instruction(app_name) WHERE is_active = true;

DROP TRIGGER IF EXISTS update_stasis_app_instruction_updated_at ON stasis_app_instruction;
CREATE TRIGGER update_stasis_app_instruction_updated_at
    BEFORE UPDATE ON stasis_app_instruction
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Default instructions for 'voicebridge' app
INSERT INTO stasis_app_instruction (app_name, instruction_key, instruction_value, priority, is_active) VALUES
    ('voicebridge', 'greeting', '{"text": "Welcome to RED Sovereign. How can I help you today?", "language": "en"}', 10, true),
    ('voicebridge', 'fallback', '{"text": "I didn't catch that. Could you please repeat?", "language": "en"}', 20, true),
    ('voicebridge', 'transfer_human', '{"text": "Transferring you to a human agent. Please hold.", "target": "queue-support"}', 30, true),
    ('voicebridge', 'goodbye', '{"text": "Thank you for calling RED Sovereign. Goodbye!", "language": "en"}', 40, true)
ON CONFLICT (app_name, instruction_key) DO UPDATE SET
    instruction_value = EXCLUDED.instruction_value,
    priority = EXCLUDED.priority,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- Call session tracking table
CREATE TABLE IF NOT EXISTS voicebridge_call_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_name VARCHAR(100) NOT NULL,
    session_id UUID NOT NULL UNIQUE,
    ariadne_channel_id VARCHAR(100),
    external_media_channel_id VARCHAR(100),
    inbound_bridge_id VARCHAR(100),
    tap_bridge_id VARCHAR(100),
    snoop_channel_id VARCHAR(100),
    caller_id VARCHAR(50),
    dialed_number VARCHAR(50),
    direction VARCHAR(20) CHECK (direction IN ('inbound', 'outbound')),
    codec VARCHAR(20) DEFAULT 'PCMU',
    local_rtp_address INET,
    local_rtp_port INTEGER,
    remote_rtp_address INET,
    remote_rtp_port INTEGER,
    ssrc INTEGER,
    state VARCHAR(50) DEFAULT 'INITIALIZING',
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    recording_path VARCHAR(500),
    recording_enabled BOOLEAN DEFAULT false,
    barge_in_count INTEGER DEFAULT 0,
    dtmf_received TEXT[],
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_voicebridge_call_session_app ON voicebridge_call_session(app_name);
CREATE INDEX IF NOT EXISTS idx_voicebridge_call_session_state ON voicebridge_call_session(state);
CREATE INDEX IF NOT EXISTS idx_voicebridge_call_session_started ON voicebridge_call_session(started_at);
CREATE INDEX IF NOT EXISTS idx_voicebridge_call_session_caller ON voicebridge_call_session(caller_id);

DROP TRIGGER IF EXISTS update_voicebridge_call_session_updated_at ON voicebridge_call_session;
CREATE TRIGGER update_voicebridge_call_session_updated_at
    BEFORE UPDATE ON voicebridge_call_session
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Call analytics aggregate table
CREATE TABLE IF NOT EXISTS voicebridge_call_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_name VARCHAR(100) NOT NULL,
    date DATE NOT NULL,
    hour INTEGER NOT NULL CHECK (hour >= 0 AND hour <= 23),
    total_calls INTEGER DEFAULT 0,
    answered_calls INTEGER DEFAULT 0,
    failed_calls INTEGER DEFAULT 0,
    total_duration_seconds BIGINT DEFAULT 0,
    avg_duration_seconds DECIMAL(10,2) DEFAULT 0,
    barge_in_events INTEGER DEFAULT 0,
    dtmf_events INTEGER DEFAULT 0,
    recording_count INTEGER DEFAULT 0,
    avg_mos_score DECIMAL(3,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(app_name, date, hour)
);

CREATE INDEX IF NOT EXISTS idx_voicebridge_analytics_app_date ON voicebridge_call_analytics(app_name, date);

DROP TRIGGER IF EXISTS update_voicebridge_analytics_updated_at ON voicebridge_call_analytics;
CREATE TRIGGER update_voicebridge_analytics_updated_at
    BEFORE UPDATE ON voicebridge_call_analytics
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();