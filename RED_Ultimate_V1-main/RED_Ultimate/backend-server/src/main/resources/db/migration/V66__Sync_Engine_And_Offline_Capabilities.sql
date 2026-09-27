-- ══════════════════════════════════════════════════════════════════
-- V66: Sync Engine and Offline-First Capabilities (WhatsApp-style)
-- ══════════════════════════════════════════════════════════════════

-- 1. Sync Engine Events
-- Stores all changes (messages, states, group updates) for offline devices to pull.
CREATE TABLE IF NOT EXISTS device_sync_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id INTEGER NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- MESSAGE_RECEIVE, MESSAGE_UPDATE, GROUP_STATE_CHANGE, PRESENCE_UPDATE, CALL_EVENT
    reference_id VARCHAR(100),       -- e.g. message_uuid, group_id
    payload JSONB NOT NULL,          -- Event details
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP           -- Null if pending
);

CREATE INDEX IF NOT EXISTS idx_sync_events_user_device_undelivered 
    ON device_sync_events(user_id, device_id, id) 
    WHERE delivered_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_sync_events_created_at 
    ON device_sync_events(created_at DESC);

-- 2. Group Message Receipts
-- Granular read receipts for groups, tracking exactly who delivered/read each message.
CREATE TABLE IF NOT EXISTS group_message_receipts (
    id BIGSERIAL PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    message_uuid VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL, -- SENT, DELIVERED, READ, PLAYED
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(message_uuid, user_id)
);

CREATE INDEX IF NOT EXISTS idx_group_receipts_message 
    ON group_message_receipts(message_uuid, status);

-- 3. Device Sync States
-- High-water marks for device synchronization to allow fast resume
CREATE TABLE IF NOT EXISTS device_sync_states (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id INTEGER NOT NULL,
    last_sync_seq BIGINT NOT NULL DEFAULT 0,
    last_sync_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);

-- 4. Group Mutations Log
-- Ensures offline members receive group structure changes chronologically
CREATE TABLE IF NOT EXISTS group_mutations_log (
    id BIGSERIAL PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mutation_type VARCHAR(50) NOT NULL, -- ADD_MEMBER, REMOVE_MEMBER, PROMOTE, DEMOTE, CHANGE_SUBJECT, CHANGE_ICON
    target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    previous_state JSONB,
    new_state JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_group_mutations_group 
    ON group_mutations_log(group_id, id DESC);

-- 5. User Chat States
-- Mute, archive, pin, and unread counts synchronized across all user devices
CREATE TABLE IF NOT EXISTS user_chat_states (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id VARCHAR(100) NOT NULL, -- conversation_id
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    mute_until TIMESTAMP,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    pin_order INTEGER DEFAULT 0,
    unread_count INTEGER NOT NULL DEFAULT 0,
    last_activity_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, chat_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_states_user_activity 
    ON user_chat_states(user_id, last_activity_time DESC);

