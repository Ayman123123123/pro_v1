-- V62__Channel_Analytics_Materialized_Views.sql
-- ═══════════════════════════════════════════════════════════════════════
-- Materialized Views لتحليلات القنوات والمجتمعات - 2026-09-17
-- تحديث دوري (Refresh) عبر pg_cron أو تطبيق Scheduler
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) Materialized View: إحصائيات القنوات اليومية ──
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_channel_stats AS
SELECT 
  c.id AS channel_id,
  c.name AS channel_name,
  c.community_id,
  DATE_TRUNC('day', m.created_at)::date AS stat_date,
  COUNT(DISTINCT m.id) AS message_count,
  COUNT(DISTINCT m.sender_id) AS unique_senders,
  COUNT(DISTINCT CASE WHEN m.message_type IN ('IMAGE','VIDEO','FILE','AUDIO','VOICE') THEN m.id END) AS media_count,
  COUNT(DISTINCT CASE WHEN m.message_type = 'RICH_TEXT' AND m.payload::text LIKE '%"action":"REACTION"%' THEN m.id END) AS reaction_count,
  SUM(CASE WHEN m.message_type IN ('IMAGE','VIDEO','FILE','AUDIO','VOICE') THEN 
    COALESCE((m.payload->>'size')::BIGINT, 0) ELSE 0 END) AS total_media_bytes,
  MAX(m.created_at) AS last_message_at
FROM channels c
LEFT JOIN messages m ON m.conversation_id = c.id
WHERE c.deleted_at IS NULL
GROUP BY c.id, c.name, c.community_id, DATE_TRUNC('day', m.created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_daily_channel_stats_pk ON mv_daily_channel_stats(channel_id, stat_date);
CREATE INDEX IF NOT EXISTS idx_mv_daily_channel_stats_community_date ON mv_daily_channel_stats(community_id, stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_mv_daily_channel_stats_date ON mv_daily_channel_stats(stat_date DESC);

-- ── 2) Materialized View: إحصائيات المجتمعات اليومية ──
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_community_stats AS
SELECT 
  com.id AS community_id,
  com.name AS community_name,
  DATE_TRUNC('day', m.created_at)::date AS stat_date,
  COUNT(DISTINCT c.id) AS channel_count,
  COUNT(DISTINCT m.id) AS message_count,
  COUNT(DISTINCT m.sender_id) AS active_members,
  COUNT(DISTINCT cm.user_id) AS total_members,
  SUM(CASE WHEN m.message_type IN ('IMAGE','VIDEO','FILE','AUDIO','VOICE') THEN 
    COALESCE((m.payload->>'size')::BIGINT, 0) ELSE 0 END) AS total_media_bytes
FROM communities com
LEFT JOIN channels c ON c.community_id = com.id AND c.deleted_at IS NULL
LEFT JOIN messages m ON m.conversation_id = c.id
LEFT JOIN channel_members cm ON cm.channel_id = c.id
WHERE com.deleted_at IS NULL
GROUP BY com.id, com.name, DATE_TRUNC('day', m.created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_daily_community_stats_pk ON mv_daily_community_stats(community_id, stat_date);
CREATE INDEX IF NOT EXISTS idx_mv_daily_community_stats_date ON mv_daily_community_stats(stat_date DESC);

-- ── 3) Materialized View: أعضاء القناة النشطين (آخر 7/30 يوم) ──
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_channel_active_members AS
SELECT 
  cm.channel_id,
  cm.user_id,
  u.username,
  u.display_name,
  u.avatar_url,
  MAX(m.created_at) AS last_activity_at,
  COUNT(m.id) FILTER (WHERE m.created_at >= NOW() - INTERVAL '7 days') AS messages_7d,
  COUNT(m.id) FILTER (WHERE m.created_at >= NOW() - INTERVAL '30 days') AS messages_30d,
  COUNT(DISTINCT DATE_TRUNC('day', m.created_at)) FILTER (WHERE m.created_at >= NOW() - INTERVAL '30 days') AS active_days_30d
FROM channel_members cm
JOIN users u ON u.id = cm.user_id
LEFT JOIN messages m ON m.conversation_id = cm.channel_id AND m.sender_id = cm.user_id
WHERE cm.left_at IS NULL
GROUP BY cm.channel_id, cm.user_id, u.username, u.display_name, u.avatar_url;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_channel_active_members_pk ON mv_channel_active_members(channel_id, user_id);
CREATE INDEX IF NOT EXISTS idx_mv_channel_active_members_activity ON mv_channel_active_members(channel_id, last_activity_at DESC);

-- ── 4) Materialized View: نمو القناة (أعضاء جدد/مغادرون يومياً) ──
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_channel_growth_daily AS
SELECT 
  cm.channel_id,
  DATE_TRUNC('day', cm.joined_at)::date AS stat_date,
  COUNT(*) AS joins_count,
  0 AS leaves_count  -- نضيف المغادرين في استعلام منفصل
FROM channel_members cm
WHERE cm.joined_at IS NOT NULL
GROUP BY cm.channel_id, DATE_TRUNC('day', cm.joined_at)

UNION ALL

SELECT 
  cm.channel_id,
  DATE_TRUNC('day', cm.left_at)::date AS stat_date,
  0 AS joins_count,
  COUNT(*) AS leaves_count
FROM channel_members cm
WHERE cm.left_at IS NOT NULL
GROUP BY cm.channel_id, DATE_TRUNC('day', cm.left_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_channel_growth_daily_pk ON mv_channel_growth_daily(channel_id, stat_date);
CREATE INDEX IF NOT EXISTS idx_mv_channel_growth_daily_date ON mv_channel_growth_daily(stat_date DESC);

-- ── 5) Materialized View: أكثر القنوات نشاطاً (Top Channels) ──
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_top_channels AS
SELECT 
  c.id AS channel_id,
  c.name AS channel_name,
  c.community_id,
  c.member_count,
  COALESCE(SUM(dcs.message_count), 0) AS total_messages_30d,
  COALESCE(SUM(dcs.unique_senders), 0) AS active_users_30d,
  CASE WHEN c.member_count > 0 
    THEN ROUND(COALESCE(SUM(dcs.unique_senders), 0)::NUMERIC / c.member_count * 100, 2)
    ELSE 0 END AS engagement_rate_pct,
  MAX(dcs.last_message_at) AS last_activity_at
FROM channels c
LEFT JOIN mv_daily_channel_stats dcs ON dcs.channel_id = c.id 
  AND dcs.stat_date >= CURRENT_DATE - INTERVAL '30 days'
WHERE c.deleted_at IS NULL
GROUP BY c.id, c.name, c.community_id, c.member_count
ORDER BY total_messages_30d DESC;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_top_channels_pk ON mv_top_channels(channel_id);
CREATE INDEX IF NOT EXISTS idx_mv_top_channels_community ON mv_top_channels(community_id, total_messages_30d DESC);
CREATE INDEX IF NOT EXISTS idx_mv_top_channels_engagement ON mv_top_channels(engagement_rate_pct DESC);

-- ── 6) Materialized View: إحصائيات البث المباشر (Live Streams) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_streams') THEN
    CREATE MATERIALIZED VIEW IF NOT EXISTS mv_live_stream_stats AS
    SELECT 
      ls.id AS stream_id,
      ls.channel_id,
      ls.host_id,
      ls.title,
      ls.status,
      ls.started_at,
      ls.ended_at,
      EXTRACT(EPOCH FROM (ls.ended_at - ls.started_at))::BIGINT AS duration_seconds,
      COALESCE(ls.peak_viewers, 0) AS peak_viewers,
      COALESCE(ls.total_unique_viewers, 0) AS total_unique_viewers,
      COALESCE(ls.total_watch_time_seconds, 0) AS total_watch_time_seconds
    FROM live_streams ls
    WHERE ls.deleted_at IS NULL;
    
    CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_live_stream_stats_pk ON mv_live_stream_stats(stream_id);
    CREATE INDEX IF NOT EXISTS idx_mv_live_stream_stats_channel ON mv_live_stream_stats(channel_id, started_at DESC);
    CREATE INDEX IF NOT EXISTS idx_mv_live_stream_stats_host ON mv_live_stream_stats(host_id, started_at DESC);
    CREATE INDEX IF NOT EXISTS idx_mv_live_stream_stats_status ON mv_live_stream_stats(status, started_at DESC);
  END IF;
END $$;

-- ── 7) Materialized View: مشاهدات البث المباشر التفصيلية ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_viewers') THEN
    CREATE MATERIALIZED VIEW IF NOT EXISTS mv_live_stream_viewers_detail AS
    SELECT 
      lsv.stream_id,
      lsv.user_id,
      u.username,
      u.display_name,
      lsv.joined_at,
      lsv.left_at,
      EXTRACT(EPOCH FROM (COALESCE(lsv.left_at, NOW()) - lsv.joined_at))::BIGINT AS watch_duration_seconds
    FROM live_stream_viewers lsv
    JOIN users u ON u.id = lsv.user_id;
    
    CREATE INDEX IF NOT EXISTS idx_mv_live_stream_viewers_stream ON mv_live_stream_viewers_detail(stream_id, joined_at);
    CREATE INDEX IF NOT EXISTS idx_mv_live_stream_viewers_user ON mv_live_stream_viewers_detail(user_id, joined_at DESC);
  END IF;
END $$;

-- ── 8) دالة تحديث الـ Materialized Views (تُستدعى من Scheduler) ──
CREATE OR REPLACE FUNCTION red_refresh_analytics_views() RETURNS VOID AS $$
BEGIN
  -- التحديث المتزامن (CONCURRENTLY لا يحتاج لقفل حصري)
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_channel_stats;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_community_stats;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_channel_active_members;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_channel_growth_daily;
  REFRESH MATERIALIZED VIEW CONCURRENTLY mv_top_channels;
  
  IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_live_stream_stats') THEN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_live_stream_stats;
  END IF;
  
  IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_live_stream_viewers_detail') THEN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_live_stream_viewers_detail;
  END IF;
END;
$$ LANGUAGE plpgsql;

-- ── 9) دوال استعلام سريعة للوحة التحكم (Dashboard) ──
-- إحصائيات قناة واحدة
CREATE OR REPLACE FUNCTION red_get_channel_analytics(
  p_channel_id UUID,
  p_days INT DEFAULT 30
) RETURNS TABLE(
  stat_date DATE,
  message_count BIGINT,
  unique_senders BIGINT,
  media_count BIGINT,
  reaction_count BIGINT,
  total_media_bytes BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    dcs.stat_date,
    dcs.message_count,
    dcs.unique_senders,
    dcs.media_count,
    dcs.reaction_count,
    dcs.total_media_bytes
  FROM mv_daily_channel_stats dcs
  WHERE dcs.channel_id = p_channel_id
    AND dcs.stat_date >= CURRENT_DATE - p_days
  ORDER BY dcs.stat_date DESC;
END;
$$ LANGUAGE plpgsql STABLE;

-- إحصائيات مجتمع واحد
CREATE OR REPLACE FUNCTION red_get_community_analytics(
  p_community_id UUID,
  p_days INT DEFAULT 30
) RETURNS TABLE(
  stat_date DATE,
  channel_count BIGINT,
  message_count BIGINT,
  active_members BIGINT,
  total_members BIGINT,
  total_media_bytes BIGINT
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    dcs.stat_date,
    dcs.channel_count,
    dcs.message_count,
    dcs.active_members,
    dcs.total_members,
    dcs.total_media_bytes
  FROM mv_daily_community_stats dcs
  WHERE dcs.community_id = p_community_id
    AND dcs.stat_date >= CURRENT_DATE - p_days
  ORDER BY dcs.stat_date DESC;
END;
$$ LANGUAGE plpgsql STABLE;

-- أفضل القنوات في مجتمع
CREATE OR REPLACE FUNCTION red_get_top_channels_in_community(
  p_community_id UUID,
  p_limit INT DEFAULT 10
) RETURNS TABLE(
  channel_id UUID,
  channel_name TEXT,
  member_count INT,
  total_messages_30d BIGINT,
  active_users_30d BIGINT,
  engagement_rate_pct NUMERIC,
  last_activity_at TIMESTAMPTZ
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    tc.channel_id,
    tc.channel_name,
    tc.member_count,
    tc.total_messages_30d,
    tc.active_users_30d,
    tc.engagement_rate_pct,
    tc.last_activity_at
  FROM mv_top_channels tc
  WHERE tc.community_id = p_community_id
  ORDER BY tc.total_messages_30d DESC
  LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

-- ── 10) فهارس BRIN على جداول المصدر للتسريع ──
CREATE INDEX IF NOT EXISTS idx_messages_brin_created ON messages USING BRIN(created_at);
CREATE INDEX IF NOT EXISTS idx_channels_brin_created ON channels USING BRIN(created_at);
CREATE INDEX IF NOT EXISTS idx_channel_members_brin_joined ON channel_members USING BRIN(joined_at);
CREATE INDEX IF NOT EXISTS idx_communities_brin_created ON communities USING BRIN(created_at);

COMMENT ON MATERIALIZED VIEW mv_daily_channel_stats IS 'إحصائيات يومية لكل قناة - يتم تحديثها دورياً';
COMMENT ON MATERIALIZED VIEW mv_daily_community_stats IS 'إحصائيات يومية لكل مجتمع';
COMMENT ON MATERIALIZED VIEW mv_channel_active_members IS 'أعضاء القناة النشطين مع نشاط 7/30 يوم';
COMMENT ON MATERIALIZED VIEW mv_channel_growth_daily IS 'نمو القناة اليومي (انضمامات/مغادرات)';
COMMENT ON MATERIALIZED VIEW mv_top_channels IS 'أكثر القنوات نشاطاً خلال 30 يوم';
COMMENT ON MATERIALIZED VIEW mv_live_stream_stats IS 'إحصائيات البث المباشر';
COMMENT ON MATERIALIZED VIEW mv_live_stream_viewers_detail IS 'تفاصيل مشاهدي البث المباشر';