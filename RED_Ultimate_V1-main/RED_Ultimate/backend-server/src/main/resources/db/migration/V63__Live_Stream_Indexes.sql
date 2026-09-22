-- V63__Live_Stream_Indexes.sql
-- ═══════════════════════════════════════════════════════════════════════
-- فهارس للبث المباشر - 2026-09-17
-- V64-revised: كل الفهارس محمية بفحص وجود الجداول/الأعمدة
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) live_streams ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_streams') THEN
    CREATE INDEX IF NOT EXISTS idx_live_streams_status_started ON live_streams(status, started_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_streams_channel_status ON live_streams(channel_id, status, started_at DESC)
      WHERE deleted_at IS NULL;
    CREATE INDEX IF NOT EXISTS idx_live_streams_host_status ON live_streams(host_id, status, started_at DESC)
      WHERE deleted_at IS NULL;
    CREATE INDEX IF NOT EXISTS idx_live_streams_scheduled ON live_streams(scheduled_at, status)
      WHERE scheduled_at IS NOT NULL AND status IN ('SCHEDULED', 'LIVE');
    CREATE INDEX IF NOT EXISTS idx_live_streams_community ON live_streams(community_id, status, started_at DESC)
      WHERE community_id IS NOT NULL AND deleted_at IS NULL;
    CREATE INDEX IF NOT EXISTS idx_live_streams_brin_started ON live_streams USING BRIN(started_at);
    CREATE INDEX IF NOT EXISTS idx_live_streams_live_only ON live_streams(channel_id, started_at DESC)
      WHERE status = 'LIVE' AND deleted_at IS NULL;
    CREATE INDEX IF NOT EXISTS idx_live_streams_ended_ready ON live_streams(host_id, ended_at DESC)
      WHERE status = 'ENDED' AND deleted_at IS NULL;
  END IF;
END $$;

-- ── 2) live_stream_viewers ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_viewers') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_stream_joined ON live_stream_viewers(stream_id, joined_at);
    CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_user_joined ON live_stream_viewers(user_id, joined_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_active ON live_stream_viewers(stream_id, user_id)
      WHERE left_at IS NULL;
  END IF;
END $$;

-- ── 3) live_stream_recordings ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_recordings') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_stream ON live_stream_recordings(stream_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_status ON live_stream_recordings(status, created_at DESC)
      WHERE status IN ('PROCESSING', 'READY', 'FAILED');
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_host ON live_stream_recordings(host_id, created_at DESC);
  END IF;
END $$;

-- ── 4) live_stream_chat ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_chat') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_stream_time ON live_stream_chat(stream_id, sent_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_user_time ON live_stream_chat(user_id, sent_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_type ON live_stream_chat(stream_id, message_type, sent_at DESC)
      WHERE message_type IN ('CHAT', 'REACTION', 'DONATION', 'SYSTEM');
  END IF;
END $$;

-- ── 5) live_stream_reactions ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_reactions') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_reactions_stream_time ON live_stream_reactions(stream_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_reactions_user_stream ON live_stream_reactions(user_id, stream_id);
  END IF;
END $$;

-- ── 6) live_stream_gifts ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_gifts') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_gifts_stream_time ON live_stream_gifts(stream_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_gifts_sender ON live_stream_gifts(sender_id, created_at DESC);
  END IF;
END $$;

-- ── 7) live_stream_moderation ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_moderation') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_moderation_stream_time ON live_stream_moderation(stream_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_moderation_target ON live_stream_moderation(target_user_id, created_at DESC);
  END IF;
END $$;

-- ── 8) stream_ingest_servers ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='stream_ingest_servers') THEN
    CREATE INDEX IF NOT EXISTS idx_stream_ingest_servers_region ON stream_ingest_servers(region, status, load_factor);
    CREATE INDEX IF NOT EXISTS idx_stream_ingest_servers_stream ON stream_ingest_servers(stream_id, status);
  END IF;
END $$;

-- ── 9) Views ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_streams')
     AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_viewers') THEN
    DROP VIEW IF EXISTS v_active_live_streams;
    CREATE VIEW v_active_live_streams AS
    SELECT
        ls.id, ls.channel_id, ls.host_id, ls.community_id, ls.title,
        ls.thumbnail_url, ls.started_at, ls.peak_viewers,
        (SELECT COUNT(*) FROM live_stream_viewers lsv WHERE lsv.stream_id = ls.id AND lsv.left_at IS NULL) AS current_viewers
    FROM live_streams ls
    WHERE ls.status = 'LIVE' AND ls.deleted_at IS NULL;

    DROP VIEW IF EXISTS v_upcoming_live_streams;
    CREATE VIEW v_upcoming_live_streams AS
    SELECT ls.id, ls.channel_id, ls.host_id, ls.community_id, ls.title,
        ls.thumbnail_url, ls.scheduled_at, ls.description
    FROM live_streams ls
    WHERE ls.status = 'SCHEDULED' AND ls.scheduled_at > NOW() AND ls.deleted_at IS NULL
    ORDER BY ls.scheduled_at ASC;
  END IF;
END $$;

-- ── 10) Helper functions ──
CREATE OR REPLACE FUNCTION red_get_active_stream_in_channel(p_channel_id UUID)
RETURNS TABLE(id UUID, channel_id UUID, host_id UUID, title TEXT, started_at TIMESTAMPTZ, peak_viewers INT, current_viewers BIGINT) AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_streams') THEN
    RETURN;
  END IF;
  RETURN QUERY EXECUTE format('
    SELECT ls.id, ls.channel_id, ls.host_id, ls.title, ls.started_at, ls.peak_viewers,
      (SELECT COUNT(*) FROM live_stream_viewers lsv WHERE lsv.stream_id = ls.id AND lsv.left_at IS NULL)
    FROM live_streams ls
    WHERE ls.channel_id = $1 AND ls.status = ''LIVE'' AND ls.deleted_at IS NULL
    ORDER BY ls.started_at DESC LIMIT 1
  ') USING p_channel_id;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON TABLE live_streams IS 'البث المباشر - حالات: SCHEDULED, LIVE, ENDED, CANCELLED, ARCHIVED';
