-- V65__Fts_Vector_Trigger_And_Deferred_Views.sql
-- ═══════════════════════════════════════════════════════════════════════
-- تسوية guarded فقط (لا حذف، لا تعديل لهجرات مطبقة):
--   1) FTS: صيانة تلقائية لـ content_vector + تعبئة رجعية
--      (V61 يترك العمود للمُدخِل يدويًا — أي INSERT بلا vector يكسر البحث)
--   2) Views المؤجلة من V62 (تُبنى هنا بعد أعمدة V64، محمية بالكامل)
--   3) فهارس retention/orphan الناقصة (IF NOT EXISTS + guards)
-- كل عبارة idempotent — آمنة على الجديد والمُرقّى.
-- ═══════════════════════════════════════════════════════════════════════

-- ━━ 1) FTS vector trigger: BEFORE INSERT OR UPDATE يشتق vector من content ━━
CREATE OR REPLACE FUNCTION red_message_fts_fill_vector() RETURNS trigger AS $$
BEGIN
  IF NEW.content IS NULL OR NEW.content = '' THEN
    NEW.content_vector := ''::tsvector;
  ELSE
    NEW.content_vector := red_message_to_tsvector(NEW.content);
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='message_search_fts')
     AND EXISTS (SELECT 1 FROM pg_proc WHERE proname='red_message_to_tsvector') THEN
    DROP TRIGGER IF EXISTS trg_message_search_fts_vector ON message_search_fts;
    CREATE TRIGGER trg_message_search_fts_vector
      BEFORE INSERT OR UPDATE OF content ON message_search_fts
      FOR EACH ROW EXECUTE FUNCTION red_message_fts_fill_vector();
    -- تعبئة رجعية: صفوف بلا vector (أُدخلت قبل الزناد)
    UPDATE message_search_fts SET content = content
      WHERE content_vector IS NULL OR content_vector = ''::tsvector;
  END IF;
END $$;

-- ━━ 2) Deferred MVs من V62 (بعد أعمدة V64) ━━
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channels' AND column_name='community_id')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channels' AND column_name='deleted_at')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channels' AND column_name='message_count')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname='mv_daily_channel_stats') THEN
    CREATE MATERIALIZED VIEW mv_daily_channel_stats AS
    SELECT c.id AS channel_id, c.name AS channel_name, c.community_id,
        CURRENT_DATE AS stat_date, c.message_count::bigint AS message_count,
        c.subscriber_count::bigint AS unique_senders, 0::bigint AS media_count,
        0::bigint AS reaction_count, 0::bigint AS total_media_bytes, c.updated_at AS last_message_at
    FROM channels c WHERE c.deleted_at IS NULL;
    CREATE UNIQUE INDEX idx_mv_daily_channel_stats_pk ON mv_daily_channel_stats(channel_id, stat_date);
    CREATE INDEX idx_mv_daily_channel_stats_date ON mv_daily_channel_stats(stat_date DESC);
  END IF;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='communities')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname='mv_daily_community_stats') THEN
    CREATE MATERIALIZED VIEW mv_daily_community_stats AS
    SELECT com.id AS community_id, com.name AS community_name, CURRENT_DATE AS stat_date,
        com.channel_count::bigint AS channel_count, 0::bigint AS message_count,
        0::bigint AS active_members, com.member_count::bigint AS total_members, 0::bigint AS total_media_bytes
    FROM communities com WHERE com.deleted_at IS NULL;
    CREATE UNIQUE INDEX idx_mv_daily_community_stats_pk ON mv_daily_community_stats(community_id, stat_date);
    CREATE INDEX idx_mv_daily_community_stats_date ON mv_daily_community_stats(stat_date DESC);
  END IF;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channel_members' AND column_name='left_at')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='username')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname='mv_channel_active_members') THEN
    CREATE MATERIALIZED VIEW mv_channel_active_members AS
    SELECT cm.channel_id, cm.user_id, u.username, u.full_name AS display_name, u.avatar_url,
        cm.joined_at AS last_activity_at, 0::bigint AS messages_7d, 0::bigint AS messages_30d, 0::bigint AS active_days_30d
    FROM channel_members cm JOIN users u ON u.id = cm.user_id WHERE cm.left_at IS NULL;
    CREATE UNIQUE INDEX idx_mv_channel_active_members_pk ON mv_channel_active_members(channel_id, user_id);
  END IF;
END $$;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_streams')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname='mv_live_stream_stats') THEN
    CREATE MATERIALIZED VIEW mv_live_stream_stats AS
    SELECT ls.id AS stream_id, ls.channel_id, ls.host_id, ls.title, ls.status, ls.started_at, ls.ended_at,
        EXTRACT(EPOCH FROM (COALESCE(ls.ended_at, NOW()) - ls.started_at))::BIGINT AS duration_seconds,
        COALESCE(ls.peak_viewers, 0) AS peak_viewers,
        COALESCE(ls.total_unique_viewers, 0) AS total_unique_viewers,
        COALESCE(ls.total_watch_time_seconds, 0) AS total_watch_time_seconds
    FROM live_streams ls WHERE ls.deleted_at IS NULL;
    CREATE UNIQUE INDEX idx_mv_live_stream_stats_pk ON mv_live_stream_stats(stream_id);
  END IF;
END $$;

-- ━━ 3) فهارس retention/orphan الناقصة (guarded) ━━
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='system_health' AND column_name='last_check_at') THEN
    CREATE INDEX IF NOT EXISTS idx_system_health_retention ON system_health(last_check_at ASC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='user_notifications' AND column_name='created_at') THEN
    CREATE INDEX IF NOT EXISTS idx_user_notifications_retention ON user_notifications(created_at ASC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='call_history' AND column_name='started_at') THEN
    CREATE INDEX IF NOT EXISTS idx_call_history_retention ON call_history(started_at ASC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='message_search_fts' AND column_name='created_at') THEN
    CREATE INDEX IF NOT EXISTS idx_message_search_fts_created ON message_search_fts(created_at DESC);
  END IF;
END $$;

-- ━━ 4) red_search_messages: تنظيف (إزالة متغير ميت) + حد آمن ━━
CREATE OR REPLACE FUNCTION red_search_messages(
    p_query TEXT, p_conversation_id UUID DEFAULT NULL, p_limit INT DEFAULT 50
) RETURNS TABLE(message_id UUID, conversation_id UUID, sender_id UUID, snippet TEXT, rank REAL, created_at TIMESTAMPTZ) AS $$
BEGIN
  RETURN QUERY
  SELECT msf.message_id, msf.conversation_id, msf.sender_id,
      LEFT(msf.content, 200) AS snippet,
      ts_rank(msf.content_vector, plainto_tsquery('arabic_custom', red_normalize_arabic(p_query))) AS rank,
      msf.created_at
  FROM message_search_fts msf
  WHERE msf.content_vector @@ plainto_tsquery('arabic_custom', red_normalize_arabic(p_query))
    AND (p_conversation_id IS NULL OR msf.conversation_id = p_conversation_id)
  ORDER BY rank DESC
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 50), 200));
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION red_message_fts_fill_vector IS 'V65: اشتقاق content_vector تلقائيًا — يمنع صفوف FTS بلا vector';
COMMENT ON FUNCTION red_search_messages IS 'V65: بحث FTS عربي + حد 1..200';
