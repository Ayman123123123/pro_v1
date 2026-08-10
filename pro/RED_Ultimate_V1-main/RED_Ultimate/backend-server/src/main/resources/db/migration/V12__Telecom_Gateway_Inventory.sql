CREATE TABLE telecom_gateways (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    vendor VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    scheme VARCHAR(10) NOT NULL CHECK (scheme IN ('http','https')),
    api_port INTEGER NOT NULL CHECK (api_port BETWEEN 1 AND 65535),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    capabilities_json TEXT NOT NULL DEFAULT '{}',
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(host, api_port)
);

CREATE TABLE gateway_port_snapshots (
    gateway_id UUID NOT NULL REFERENCES telecom_gateways(id) ON DELETE CASCADE,
    port_index INTEGER NOT NULL CHECK (port_index BETWEEN 0 AND 31),
    radio_type VARCHAR(20),
    registration_state VARCHAR(40),
    call_state VARCHAR(40),
    signal_raw INTEGER,
    signal_percent INTEGER CHECK (signal_percent BETWEEN 0 AND 100),
    gprs_state VARCHAR(30),
    sim_number_masked VARCHAR(40),
    imsi_masked VARCHAR(40),
    iccid_masked VARCHAR(40),
    observed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(gateway_id, port_index)
);

CREATE TABLE gateway_operations (
    id UUID PRIMARY KEY,
    gateway_id UUID REFERENCES telecom_gateways(id) ON DELETE SET NULL,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    operation VARCHAR(80) NOT NULL,
    target_port INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED','SUCCEEDED','FAILED','REJECTED')),
    details_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_gateway_operations_created ON gateway_operations(created_at DESC);
