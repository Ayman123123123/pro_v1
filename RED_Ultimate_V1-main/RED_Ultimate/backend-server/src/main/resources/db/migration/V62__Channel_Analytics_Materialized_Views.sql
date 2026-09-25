-- V62__Channel_Analytics_Materialized_Views.sql
-- ═══════════════════════════════════════════════════════════════════════
-- Materialized Views لتحليلات القنوات - 2026-09-17
-- V64-revised: محمية بالكامل - لا تشير لجداول غير موجودة
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) Materialized View: إحصائيات القنوات (بدون messages - في MongoDB) ──
-- guarded fresh-install: community_id/deleted_at/message_count تضاف في V64 — تُبنى هنا فقط إن وُجدت، وإلا تتولاها V65
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='channels')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channels' AND column_name='community_id')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channels' AND column_name='deleted_at')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channels' AND column_name='message_count')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_channel_stats') THEN
    CREATE MATERIALIZED VIEW mv_daily_channel_stats AS
    SELECT
        c.id AS channel_id,
        c.name AS channel_name,
        c.community_id,
        CURRENT_DATE AS stat_date,
        c.message_count::bigint AS message_count,
        c.subscriber_count::bigint AS unique_senders,
        0::bigint AS media_count,
        0::bigint AS reaction_count,
        0::bigint AS total_media_bytes,
        c.updated_at AS last_message_at
    FROM channels c
    WHERE c.deleted_at IS NULL;

    CREATE UNIQUE INDEX idx_mv_daily_channel_stats_pk ON mv_daily_channel_stats(channel_id, stat_date);
    CREATE INDEX idx_mv_daily_channel_stats_date ON mv_daily_channel_stats(stat_date DESC);
  END IF;
END $$;

-- ── 2) Materialized View: إحصائيات المجتمعات ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='communities')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_community_stats') THEN
    CREATE MATERIALIZED VIEW mv_daily_community_stats AS
    SELECT
        com.id AS community_id,
        com.name AS community_name,
        CURRENT_DATE AS stat_date,
        com.channel_count::bigint AS channel_count,
        0::bigint AS message_count,
        0::bigint AS active_members,
        com.member_count::bigint AS total_members,
        0::bigint AS total_media_bytes
    FROM communities com
    WHERE com.deleted_at IS NULL;

    CREATE UNIQUE INDEX idx_mv_daily_community_stats_pk ON mv_daily_community_stats(community_id, stat_date);
    CREATE INDEX idx_mv_daily_community_stats_date ON mv_daily_community_stats(stat_date DESC);
  END IF;
END $$;

-- ── 3) Materialized View: أعضاء القناة النشطين ──
-- guarded fresh-install: left_at يضاف في V64 — يُبنى هنا فقط إن وُجد، وإلا تتولاه V65
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='channel_members')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channel_members' AND column_name='joined_at')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channel_members' AND column_name='left_at')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_channel_active_members') THEN
    CREATE MATERIALIZED VIEW mv_channel_active_members AS
    SELECT
        cm.channel_id,
        cm.user_id,
        u.username,
        u.full_name AS display_name,
        u.avatar_url,
        cm.joined_at AS last_activity_at,
        0::bigint AS messages_7d,
        0::bigint AS messages_30d,
        0::bigint AS active_days_30d
    FROM channel_members cm
    JOIN users u ON u.id = cm.user_id
    WHERE cm.left_at IS NULL;

    CREATE UNIQUE INDEX idx_mv_channel_active_members_pk ON mv_channel_active_members(channel_id, user_id);
  END IF;
END $$;

-- ── 4) Materialized View: نمو القناة ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='channel_members')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channel_members' AND column_name='joined_at')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_channel_growth_daily') THEN
    CREATE MATERIALIZED VIEW mv_channel_growth_daily AS
    SELECT
        cm.channel_id,
        cm.joined_at::date AS stat_date,
        COUNT(*)::bigint AS joins_count,
        0::bigint AS leaves_count
    FROM channel_members cm
    WHERE cm.joined_at IS NOT NULL
    GROUP BY cm.channel_id, cm.joined_at::date;

    CREATE UNIQUE INDEX idx_mv_channel_growth_daily_pk ON mv_channel_growth_daily(channel_id, stat_date);
    CREATE INDEX idx_mv_channel_growth_daily_date ON mv_channel_growth_daily(stat_date DESC);
  END IF;
END $$;

-- ── 5) Materialized View: أكثر القنوات نشاطاً ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_channel_stats')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_top_channels') THEN
    CREATE MATERIALIZED VIEW mv_top_channels AS
    SELECT
        c.id AS channel_id,
        c.name AS channel_name,
        c.community_id,
        c.subscriber_count AS member_count,
        COALESCE(SUM(dcs.message_count), 0) AS total_messages_30d,
        COALESCE(SUM(dcs.unique_senders), 0) AS active_users_30d,
        CASE WHEN c.subscriber_count > 0
            THEN ROUND(COALESCE(SUM(dcs.unique_senders), 0)::NUMERIC / c.subscriber_count * 100, 2)
            ELSE 0 END AS engagement_rate_pct,
        MAX(dcs.last_message_at) AS last_activity_at
    FROM channels c
    LEFT JOIN mv_daily_channel_stats dcs ON dcs.channel_id = c.id
        AND dcs.stat_date >= CURRENT_DATE - INTERVAL '30 days'
    WHERE c.deleted_at IS NULL
    GROUP BY c.id, c.name, c.community_id, c.subscriber_count
    ORDER BY total_messages_30d DESC;

    CREATE UNIQUE INDEX idx_mv_top_channels_pk ON mv_top_channels(channel_id);
  END IF;
END $$;

-- ── 6) Live stream materialized views ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_streams')
     AND NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_live_stream_stats') THEN
    CREATE MATERIALIZED VIEW mv_live_stream_stats AS
    SELECT
        ls.id AS stream_id,
        ls.channel_id,
        ls.host_id,
        ls.title,
        ls.status,
        ls.started_at,
        ls.ended_at,
        EXTRACT(EPOCH FROM (COALESCE(ls.ended_at, NOW()) - ls.started_at))::BIGINT AS duration_seconds,
        COALESCE(ls.peak_viewers, 0) AS peak_viewers,
        COALESCE(ls.total_unique_viewers, 0) AS total_unique_viewers,
        COALESCE(ls.total_watch_time_seconds, 0) AS total_watch_time_seconds
    FROM live_streams ls
    WHERE ls.deleted_at IS NULL;

    CREATE UNIQUE INDEX idx_mv_live_stream_stats_pk ON mv_live_stream_stats(stream_id);
  END IF;
END $$;

-- ── 7) دالة تحديث الـ Materialized Views ──
CREATE OR REPLACE FUNCTION red_refresh_analytics_views() RETURNS VOID AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_channel_stats') THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_channel_stats;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_daily_community_stats') THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_community_stats;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_channel_active_members') THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY mv_channel_active_members;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_channel_growth_daily') THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY mv_channel_growth_daily;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_top_channels') THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY mv_top_channels;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'mv_live_stream_stats') THEN
        REFRESH MATERIALIZED VIEW CONCURRENTLY mv_live_stream_stats;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ── 8) BRIN indexes ──
CREATE INDEX IF NOT EXISTS idx_channels_brin_created ON channels USING BRIN(created_at);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='communities') THEN
    CREATE INDEX IF NOT EXISTS idx_communities_brin_created ON communities USING BRIN(created_at);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='channel_members' AND column_name='joined_at') THEN
    CREATE INDEX IF NOT EXISTS idx_channel_members_brin_joined ON channel_members USING BRIN(joined_at);
  END IF;
END $$;

COMMENT ON MATERIALIZED VIEW mv_daily_channel_stats IS 'إحصائيات يومية لكل قناة';
COMMENT ON MATERIALIZED VIEW mv_top_channels IS 'أكثر القنوات نشاطاً خلال 30 يوم';
COMMENT ON FUNCTION red_refresh_analytics_views IS 'تحديث كل Materialized Views';
