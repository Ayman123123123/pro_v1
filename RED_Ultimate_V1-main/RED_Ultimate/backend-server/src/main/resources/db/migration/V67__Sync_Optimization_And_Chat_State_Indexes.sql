-- ══════════════════════════════════════════════════════════════════
-- V67: Sync Optimization, Granular Receipts, and Advanced Chat States
-- High-throughput indexing and WhatsApp/Telegram architectural patterns
-- ══════════════════════════════════════════════════════════════════

-- 1. Index Optimizations for device_sync_events
-- Fast chronological range scans for device catchup / event pull (id > last_seq)
CREATE INDEX IF NOT EXISTS idx_sync_events_user_device_seq 
    ON device_sync_events(user_id, device_id, id ASC);

-- Efficient retention pruning for delivered sync events
CREATE INDEX IF NOT EXISTS idx_sync_events_delivered_cleanup 
    ON device_sync_events(delivered_at) 
    WHERE delivered_at IS NOT NULL;

-- Fast reference lookup (e.g. tracking sync events for a specific message_uuid or call_id)
CREATE INDEX IF NOT EXISTS idx_sync_events_reference 
    ON device_sync_events(reference_id) 
    WHERE reference_id IS NOT NULL;

-- Event type analytics and operational monitoring
CREATE INDEX IF NOT EXISTS idx_sync_events_type_created 
    ON device_sync_events(event_type, created_at DESC);


-- 2. Index Optimizations for device_sync_states
-- Multi-device status and recent sync activity per user
CREATE INDEX IF NOT EXISTS idx_sync_states_user_time 
    ON device_sync_states(user_id, last_sync_time DESC);


-- 3. Granular Group Message Receipts (WhatsApp Pattern)
-- Add explicit per-status timestamps to preserve the complete receipt lifecycle
ALTER TABLE group_message_receipts 
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS played_at TIMESTAMP;

-- Rapid lookup for group member receipt history
CREATE INDEX IF NOT EXISTS idx_group_receipts_group_user 
    ON group_message_receipts(group_id, user_id);

-- Lookup unread / un-delivered receipts per user across groups
CREATE INDEX IF NOT EXISTS idx_group_receipts_user_status 
    ON group_message_receipts(user_id, status);

-- Message info screen query: all receipts for a message ordered by latest status update
CREATE INDEX IF NOT EXISTS idx_group_receipts_msg_status 
    ON group_message_receipts(message_uuid, status, updated_at DESC);


-- 4. Advanced User Chat States (WhatsApp / Telegram Patterns)
-- Cross-device draft sync (Telegram pattern)
-- Manual mark as unread flag (WhatsApp pattern)
-- Disappearing messages timer per chat (WhatsApp pattern)
-- Last read sequence cursor for seamless cross-device unread synchronization
ALTER TABLE user_chat_states 
    ADD COLUMN IF NOT EXISTS draft_text TEXT,
    ADD COLUMN IF NOT EXISTS draft_updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS marked_unread BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS disappearing_timer_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS last_read_sequence BIGINT NOT NULL DEFAULT 0;

-- Primary active inbox query: Pinned chats on top (ordered by pin_order), followed by active chats (last_activity_time DESC)
CREATE INDEX IF NOT EXISTS idx_chat_states_user_inbox 
    ON user_chat_states(user_id, is_pinned DESC, pin_order ASC, last_activity_time DESC) 
    WHERE is_archived = FALSE;

-- Dedicated archived folder query
CREATE INDEX IF NOT EXISTS idx_chat_states_user_archived 
    ON user_chat_states(user_id, last_activity_time DESC) 
    WHERE is_archived = TRUE;

-- Pinned chats only query (fast retrieval of top pinned bar)
CREATE INDEX IF NOT EXISTS idx_chat_states_user_pinned 
    ON user_chat_states(user_id, pin_order ASC) 
    WHERE is_pinned = TRUE;

-- Fast aggregation for total unread badge counter
CREATE INDEX IF NOT EXISTS idx_chat_states_user_unread 
    ON user_chat_states(user_id, unread_count) 
    WHERE unread_count > 0;

-- Muted chats query for push notification suppression
CREATE INDEX IF NOT EXISTS idx_chat_states_user_muted 
    ON user_chat_states(user_id, mute_until) 
    WHERE is_muted = TRUE;
