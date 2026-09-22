-- V64__Schema_Reconciliation_And_Missing_Tables.sql
-- ═══════════════════════════════════════════════════════════════════════
-- تسوية المخطط: إنشاء الجداول والأعمدة التي تفترضها V60-V63 لكنها غير موجودة.
--
-- المشاكل المُصلحة:
--   1) call_history: أعمدة quality_score, network_type غير موجودة (V60 تفترضها)
--   2) call_participants: أعمدة quality_score, packet_loss_percent, jitter_ms,
--      rtt_ms, codec غير موجودة (V60 تفترضها)
--   3) communities: جدول غير موجود (V62 يشير إليه)
--   4) channels: أعمدة community_id, deleted_at, member_count غير موجودة (V62)
--   5) channel_members: أعمدة left_at, joined_at غير موجودة (V62)
--   6) live_streams + الجداول الفرعية: غير موجودة إطلاقًا (V63)
--   7) users: display_name غير موجود (V62 يستخدمه — العمود الفعلي full_name)
--   8) messages: غير موجود في PG (V62/V61 يشير إليه — الرسائل في MongoDB)
--
-- كل عبارة idempotent (IF NOT EXISTS / IF EXISTS guards).
-- ═══════════════════════════════════════════════════════════════════════

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 1) call_history: إضافة أعمدة الجودة والشبكة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ALTER TABLE call_history ADD COLUMN IF NOT EXISTS quality_score NUMERIC(5,2);
ALTER TABLE call_history ADD COLUMN IF NOT EXISTS network_type VARCHAR(20);
ALTER TABLE call_history ADD COLUMN IF NOT EXISTS duration_seconds INTEGER GENERATED ALWAYS AS (duration_ms / 1000) STORED;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 2) call_participants: إضافة أعمدة الجودة التقنية
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ALTER TABLE call_participants ADD COLUMN IF NOT EXISTS quality_score NUMERIC(5,2);
ALTER TABLE call_participants ADD COLUMN IF NOT EXISTS packet_loss_percent NUMERIC(5,2) DEFAULT 0.00;
ALTER TABLE call_participants ADD COLUMN IF NOT EXISTS jitter_ms INTEGER DEFAULT 0;
ALTER TABLE call_participants ADD COLUMN IF NOT EXISTS rtt_ms INTEGER DEFAULT 0;
ALTER TABLE call_participants ADD COLUMN IF NOT EXISTS codec VARCHAR(30);

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 3) communities: جدول المجتمعات (يجمع عدة قنوات)
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS communities (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    avatar_media_key VARCHAR(200),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    member_count INTEGER NOT NULL DEFAULT 0,
    channel_count INTEGER NOT NULL DEFAULT 0,
    category VARCHAR(50),
    rules TEXT,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_communities_owner ON communities(owner_id);
CREATE INDEX IF NOT EXISTS idx_communities_public ON communities(is_public, member_count DESC) WHERE is_public = TRUE AND deleted_at IS NULL;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 4) channels: إضافة الأعمدة الناقصة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ALTER TABLE channels ADD COLUMN IF NOT EXISTS community_id UUID REFERENCES communities(id) ON DELETE SET NULL;
ALTER TABLE channels ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE channels ADD COLUMN IF NOT EXISTS member_count INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_channels_community ON channels(community_id) WHERE community_id IS NOT NULL;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 5) channel_members: إضافة الأعمدة الناقصة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='channel_members') THEN
    ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
    ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS left_at TIMESTAMPTZ;
    ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
  END IF;
END $$;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 6) live_streams: البث المباشر والجداول الفرعية
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CREATE TABLE IF NOT EXISTS live_streams (
    id UUID PRIMARY KEY,
    channel_id UUID REFERENCES channels(id) ON DELETE CASCADE,
    host_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    community_id UUID REFERENCES communities(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    description TEXT,
    thumbnail_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    scheduled_at TIMESTAMPTZ,
    peak_viewers INTEGER NOT NULL DEFAULT 0,
    viewer_count INTEGER NOT NULL DEFAULT 0,
    total_unique_viewers INTEGER NOT NULL DEFAULT 0,
    total_watch_time_seconds BIGINT NOT NULL DEFAULT 0,
    engagement NUMERIC(10,2) NOT NULL DEFAULT 0,
    gift_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_recording BOOLEAN NOT NULL DEFAULT FALSE,
    recording_url TEXT,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT live_streams_status_check CHECK (status IN ('SCHEDULED','LIVE','ENDED','CANCELLED','ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_live_streams_base_status ON live_streams(status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_streams_base_host ON live_streams(host_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_streams_base_channel ON live_streams(channel_id, started_at DESC);

CREATE TABLE IF NOT EXISTS live_stream_viewers (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMPTZ,
    watch_duration_seconds INTEGER,
    quality VARCHAR(20) DEFAULT 'AUTO',
    UNIQUE(stream_id, user_id)
);

CREATE TABLE IF NOT EXISTS live_stream_recordings (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    host_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    storage_path TEXT,
    hls_manifest_url TEXT,
    thumbnail_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ls_recordings_status_check CHECK (status IN ('PROCESSING','READY','FAILED'))
);

CREATE TABLE IF NOT EXISTS live_stream_chat (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    content TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ls_chat_type_check CHECK (message_type IN ('CHAT','REACTION','DONATION','SYSTEM'))
);

CREATE TABLE IF NOT EXISTS live_stream_reactions (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji VARCHAR(10) NOT NULL,
    count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS live_stream_gifts (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gift_type VARCHAR(50) NOT NULL,
    amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS live_stream_moderation (
    id UUID PRIMARY KEY,
    stream_id UUID NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    moderator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stream_ingest_servers (
    id UUID PRIMARY KEY,
    region VARCHAR(50) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL DEFAULT 1935,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    load_factor NUMERIC(5,2) NOT NULL DEFAULT 0,
    stream_id UUID REFERENCES live_streams(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ingest_status_check CHECK (status IN ('ACTIVE','DRAINING','OFFLINE'))
);

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 7) message_search_fts: إصلاح المرجع (messages في MongoDB وليس PG)
--    نحذف الـ FK ونجعل message_id standalone
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DO $$ BEGIN
  -- Drop table if it was created with broken FK in V61
  DROP TABLE IF EXISTS message_search_fts CASCADE;
END $$;

-- Recreate without FK to messages (messages are in MongoDB)
CREATE TABLE IF NOT EXISTS message_search_fts (
    message_id      UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id       UUID NOT NULL,
    content         TEXT NOT NULL,
    content_vector  tsvector NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message_type    TEXT NOT NULL DEFAULT 'TEXT'
);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_vector ON message_search_fts USING GIN(content_vector);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_conv ON message_search_fts(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_sender ON message_search_fts(sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_search_fts_type ON message_search_fts(message_type, created_at DESC);

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 8) إصلاح views/functions التي تشير لجداول غير موجودة
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- Drop broken materialized views from V62 (they reference messages/communities incorrectly)
DROP MATERIALIZED VIEW IF EXISTS mv_daily_channel_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_daily_community_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_channel_active_members CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_channel_growth_daily CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_top_channels CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_live_stream_stats CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_live_stream_viewers_detail CASCADE;

-- Drop broken views from V60/V63
DROP VIEW IF EXISTS v_daily_call_quality CASCADE;
DROP VIEW IF EXISTS v_active_live_streams CASCADE;
DROP VIEW IF EXISTS v_upcoming_live_streams CASCADE;

-- Recreate v_daily_call_quality with correct column references
CREATE OR REPLACE VIEW v_daily_call_quality AS
SELECT
    started_at::date AS call_date,
    call_type,
    network_type,
    COUNT(*) AS total_calls,
    AVG(quality_score)::NUMERIC(5,2) AS avg_quality,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY quality_score) AS median_quality,
    AVG(duration_ms)::NUMERIC(10,2) AS avg_duration_ms,
    COUNT(*) FILTER (WHERE quality_score < 60) AS poor_quality_count
FROM call_history
WHERE quality_score IS NOT NULL
GROUP BY started_at::date, call_type, network_type;

-- Recreate v_active_live_streams
CREATE OR REPLACE VIEW v_active_live_streams AS
SELECT
    ls.id, ls.channel_id, ls.host_id, ls.community_id, ls.title,
    ls.thumbnail_url, ls.started_at, ls.peak_viewers,
    (SELECT COUNT(*) FROM live_stream_viewers lsv WHERE lsv.stream_id = ls.id AND lsv.left_at IS NULL) AS current_viewers
FROM live_streams ls
WHERE ls.status = 'LIVE' AND ls.deleted_at IS NULL;

-- Recreate v_upcoming_live_streams
CREATE OR REPLACE VIEW v_upcoming_live_streams AS
SELECT ls.id, ls.channel_id, ls.host_id, ls.community_id, ls.title,
    ls.thumbnail_url, ls.scheduled_at, ls.description
FROM live_streams ls
WHERE ls.status = 'SCHEDULED' AND ls.scheduled_at > NOW() AND ls.deleted_at IS NULL
ORDER BY ls.scheduled_at ASC;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 9) Materialized Views آمنة (محاطة بفحوصات وجود الجداول)
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- Daily channel stats (without messages table - use channel metadata instead)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_channel_stats') THEN
    CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_channel_stats AS
    SELECT
        c.id AS channel_id,
        c.name AS channel_name,
        c.community_id,
        CURRENT_DATE AS stat_date,
        c.message_count::bigint AS message_count,
        0::bigint AS unique_senders,
        0::bigint AS media_count,
        0::bigint AS reaction_count,
        0::bigint AS total_media_bytes,
        c.updated_at AS last_message_at
    FROM channels c
    WHERE c.deleted_at IS NULL;

    CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_daily_channel_stats_pk ON mv_daily_channel_stats(channel_id, stat_date);
  END IF;
END $$;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 10) Drop broken triggers from V61
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DROP TRIGGER IF EXISTS trigger_sync_message_fts ON messages;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 11) Cleanup broken functions and recreate safely
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- Fix red_retention_delete to handle tables that may not have expected columns
CREATE OR REPLACE FUNCTION red_retention_delete(table_name TEXT, cutoff TIMESTAMPTZ, batch INT)
RETURNS BIGINT AS $$
DECLARE
    deleted BIGINT := 0;
    col TEXT;
    sql TEXT;
    safe_batch INT := GREATEST(100, LEAST(COALESCE(batch, 10000), 50000));
BEGIN
    CASE table_name
        WHEN 'admin_audit_log' THEN col := 'created_at';
        WHEN 'audit_events' THEN col := 'created_at';
        WHEN 'system_health' THEN col := 'last_check_at';
        WHEN 'usage_stats' THEN col := 'created_at';
        WHEN 'user_notifications' THEN col := 'created_at';
        WHEN 'call_history' THEN col := 'started_at';
        WHEN 'call_qoe_telemetry' THEN col := 'created_at';
        ELSE RAISE EXCEPTION 'red_retention_delete: table % not allowed', table_name;
    END CASE;
    sql := format('DELETE FROM %I WHERE id IN (SELECT id FROM %I WHERE %I < $1 ORDER BY %I ASC LIMIT %s)',
        table_name, table_name, col, col, safe_batch);
    EXECUTE sql USING cutoff;
    GET DIAGNOSTICS deleted = ROW_COUNT;
    RETURN deleted;
END;
$$ LANGUAGE plpgsql;

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 12) Drop orphan BRIN indexes from V62 on non-existent tables
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DROP INDEX IF EXISTS idx_messages_brin_created;
DROP INDEX IF EXISTS idx_communities_brin_created;

-- Create BRIN indexes only on tables that exist
CREATE INDEX IF NOT EXISTS idx_channels_brin_created ON channels USING BRIN(created_at);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='channel_members') THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channel_members' AND column_name='joined_at') THEN
      CREATE INDEX IF NOT EXISTS idx_channel_members_brin_joined ON channel_members USING BRIN(joined_at);
    END IF;
  END IF;
END $$;

COMMENT ON TABLE communities IS 'المجتمعات: تجمع عدة قنوات تحت مظلة واحدة';
COMMENT ON TABLE live_streams IS 'البث المباشر - حالات: SCHEDULED, LIVE, ENDED, CANCELLED, ARCHIVED';
COMMENT ON TABLE live_stream_viewers IS 'مشاهدو البث المباشر مع تتبع وقت الانضمام/المغادرة';
COMMENT ON TABLE message_search_fts IS 'فهرس البحث النصي للرسائل (message_id يشير لـ MongoDB)';

-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- 13) FRESH-INSTALL INDEX RECONCILIATION
-- V60/V63 guards skipped their indexes on fresh installs (columns/tables
-- didn't exist yet). Now that V64 created them, create the full index set.
-- All IF NOT EXISTS — safe on upgraded installs where V60/V63 already ran.
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- call_history quality indexes (from V60)
CREATE INDEX IF NOT EXISTS idx_call_history_quality_caller_time ON call_history(caller_id, started_at DESC)
  INCLUDE (callee_id, ended_at, quality_score);
CREATE INDEX IF NOT EXISTS idx_call_history_type_quality ON call_history(call_type, quality_score DESC, started_at DESC)
  WHERE quality_score IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_call_history_network_quality ON call_history(network_type, quality_score DESC, started_at DESC)
  WHERE quality_score IS NOT NULL AND network_type IS NOT NULL;

-- call_participants quality indexes (from V60 — exact names/defs for IF NOT EXISTS dedupe)
CREATE INDEX IF NOT EXISTS idx_call_participants_call_quality ON call_participants(call_id, joined_at, left_at)
  INCLUDE (user_id, quality_score);
CREATE INDEX IF NOT EXISTS idx_call_participants_user_time ON call_participants(user_id, joined_at DESC)
  INCLUDE (call_id, quality_score);

-- live_streams indexes (from V63 — exact names/defs for IF NOT EXISTS dedupe)
CREATE INDEX IF NOT EXISTS idx_live_streams_community ON live_streams(community_id, status, started_at DESC)
  WHERE community_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_live_streams_scheduled ON live_streams(scheduled_at, status)
  WHERE scheduled_at IS NOT NULL AND status IN ('SCHEDULED', 'LIVE');
CREATE INDEX IF NOT EXISTS idx_live_streams_brin_started ON live_streams USING BRIN(started_at);
CREATE INDEX IF NOT EXISTS idx_live_streams_live_only ON live_streams(channel_id, started_at DESC)
  WHERE status = 'LIVE' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_live_streams_ended_ready ON live_streams(host_id, ended_at DESC)
  WHERE status = 'ENDED' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_stream_joined ON live_stream_viewers(stream_id, joined_at);
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_user_joined ON live_stream_viewers(user_id, joined_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_active ON live_stream_viewers(stream_id, user_id)
  WHERE left_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_live_stream_chat_stream_time ON live_stream_chat(stream_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_live_stream_chat_user_time ON live_stream_chat(user_id, sent_at DESC);
