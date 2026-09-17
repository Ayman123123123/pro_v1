-- V63__Live_Stream_Indexes.sql
-- ═══════════════════════════════════════════════════════════════════════
-- فهارس للبث المباشر (Live Streams) - 2026-09-17
-- دعم: البث المباشر، التسجيلات، المشاهدين، الدردشة المصاحبة
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) فهارس جدول live_streams (الأساسي) ──
-- استعلامات: بث مباشر نشط، بث حسب المضيف، بث في قناة، بث مجدول
CREATE INDEX IF NOT EXISTS idx_live_streams_status_started ON live_streams(status, started_at DESC)
  INCLUDE (channel_id, host_id, title, thumbnail_url, scheduled_at, ended_at, peak_viewers);
CREATE INDEX IF NOT EXISTS idx_live_streams_channel_status ON live_streams(channel_id, status, started_at DESC)
  WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_live_streams_host_status ON live_streams(host_id, status, started_at DESC)
  WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_live_streams_scheduled ON live_streams(scheduled_at, status)
  WHERE scheduled_at IS NOT NULL AND status IN ('SCHEDULED', 'LIVE');
CREATE INDEX IF NOT EXISTS idx_live_streams_community ON live_streams(community_id, status, started_at DESC)
  WHERE community_id IS NOT NULL AND deleted_at IS NULL;

-- فهرس BRIN للبيانات الزمنية الكبيرة
CREATE INDEX IF NOT EXISTS idx_live_streams_brin_started ON live_streams USING BRIN(started_at);
CREATE INDEX IF NOT EXISTS idx_live_streams_brin_scheduled ON live_streams USING BRIN(scheduled_at) WHERE scheduled_at IS NOT NULL;

-- ── 2) فهارس جدول live_stream_viewers (المشاهدين) ──
-- استعلامات: مشاهدون نشطون، تاريخ المشاهدة، إحصائيات المشاهد
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_stream_joined ON live_stream_viewers(stream_id, joined_at)
  INCLUDE (user_id, left_at, watch_duration_seconds, quality);
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_user_joined ON live_stream_viewers(user_id, joined_at DESC)
  INCLUDE (stream_id, left_at, watch_duration_seconds);
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_active ON live_stream_viewers(stream_id, user_id)
  WHERE left_at IS NULL;  -- المشاهدون النشطون حالياً
CREATE INDEX IF NOT EXISTS idx_live_stream_viewers_duration ON live_stream_viewers(stream_id, watch_duration_seconds DESC)
  WHERE watch_duration_seconds IS NOT NULL;

-- ── 3) فهارس جدول live_stream_recordings (التسجيلات) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_recordings') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_stream ON live_stream_recordings(stream_id, created_at DESC)
      INCLUDE (status, duration_seconds, file_size_bytes, storage_path, hls_manifest_url);
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_status ON live_stream_recordings(status, created_at DESC)
      WHERE status IN ('PROCESSING', 'READY', 'FAILED');
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_host ON live_stream_recordings(host_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_recordings_brin ON live_stream_recordings USING BRIN(created_at);
  END IF;
END $$;

-- ── 4) فهارس جدول live_stream_chat (دردشة البث المباشر) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_chat') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_stream_time ON live_stream_chat(stream_id, sent_at DESC)
      INCLUDE (user_id, message_type, content);
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_user_time ON live_stream_chat(user_id, sent_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_type ON live_stream_chat(stream_id, message_type, sent_at DESC)
      WHERE message_type IN ('CHAT', 'REACTION', 'DONATION', 'SYSTEM');
    CREATE INDEX IF NOT EXISTS idx_live_stream_chat_brin ON live_stream_chat USING BRIN(sent_at);
  END IF;
END $$;

-- ── 5) فهارس جدول live_stream_reactions (تفاعلات البث) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_reactions') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_reactions_stream_time ON live_stream_reactions(stream_id, created_at DESC)
      INCLUDE (user_id, emoji, count);
    CREATE INDEX IF NOT EXISTS idx_live_stream_reactions_user_stream ON live_stream_reactions(user_id, stream_id);
    CREATE INDEX IF NOT EXISTS idx_live_stream_reactions_brin ON live_stream_reactions USING BRIN(created_at);
  END IF;
END $$;

-- ── 6) فهارس جدول live_stream_gifts (الهدايا/التبرعات) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_gifts') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_gifts_stream_time ON live_stream_gifts(stream_id, created_at DESC)
      INCLUDE (sender_id, gift_type, amount, currency);
    CREATE INDEX IF NOT EXISTS idx_live_stream_gifts_sender ON live_stream_gifts(sender_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_gifts_stream_amount ON live_stream_gifts(stream_id, amount DESC, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_gifts_brin ON live_stream_gifts USING BRIN(created_at);
  END IF;
END $$;

-- ── 7) فهارس جدول live_stream_moderation (الإشراف) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='live_stream_moderation') THEN
    CREATE INDEX IF NOT EXISTS idx_live_stream_moderation_stream_time ON live_stream_moderation(stream_id, created_at DESC)
      INCLUDE (moderator_id, action, target_user_id, reason);
    CREATE INDEX IF NOT EXISTS idx_live_stream_moderation_target ON live_stream_moderation(target_user_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_live_stream_moderation_action ON live_stream_moderation(action, created_at DESC);
  END IF;
END $$;

-- ── 8) فهارس جدول stream_ingest_servers (خوادم الاستقبال - SFU) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='stream_ingest_servers') THEN
    CREATE INDEX IF NOT EXISTS idx_stream_ingest_servers_region ON stream_ingest_servers(region, status, load_factor);
    CREATE INDEX IF NOT EXISTS idx_stream_ingest_servers_stream ON stream_ingest_servers(stream_id, status);
  END IF;
END $$;

-- ── 9) دوال مساعدة للبث المباشر ──

-- الحصول على البث المباشر النشط في قناة
CREATE OR REPLACE FUNCTION red_get_active_stream_in_channel(p_channel_id UUID)
RETURNS TABLE(
  id UUID,
  channel_id UUID,
  host_id UUID,
  title TEXT,
  started_at TIMESTAMPTZ,
  peak_viewers INT,
  current_viewers INT
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    ls.id, ls.channel_id, ls.host_id, ls.title, ls.started_at,
    ls.peak_viewers,
    (SELECT COUNT(*) FROM live_stream_viewers lsv WHERE lsv.stream_id = ls.id AND lsv.left_at IS NULL) AS current_viewers
  FROM live_streams ls
  WHERE ls.channel_id = p_channel_id
    AND ls.status = 'LIVE'
    AND ls.deleted_at IS NULL
  ORDER BY ls.started_at DESC
  LIMIT 1;
END;
$$ LANGUAGE plpgsql STABLE;

-- الحصول على البث المباشر المجدول القادم
CREATE OR REPLACE FUNCTION red_get_upcoming_streams(p_channel_id UUID, p_limit INT DEFAULT 10)
RETURNS TABLE(
  id UUID,
  channel_id UUID,
  host_id UUID,
  title TEXT,
  scheduled_at TIMESTAMPTZ,
  thumbnail_url TEXT
) AS $$
BEGIN
  RETURN QUERY
  SELECT ls.id, ls.channel_id, ls.host_id, ls.title, ls.scheduled_at, ls.thumbnail_url
  FROM live_streams ls
  WHERE ls.channel_id = p_channel_id
    AND ls.status = 'SCHEDULED'
    AND ls.scheduled_at > NOW()
    AND ls.deleted_at IS NULL
  ORDER BY ls.scheduled_at ASC
  LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

-- إحصائيات بث مباشر (للـ Dashboard)
CREATE OR REPLACE FUNCTION red_get_live_stream_analytics(p_stream_id UUID)
RETURNS TABLE(
  stream_id UUID,
  title TEXT,
  status TEXT,
  started_at TIMESTAMPTZ,
  ended_at TIMESTAMPTZ,
  duration_seconds BIGINT,
  peak_viewers INT,
  total_unique_viewers BIGINT,
  total_watch_time_seconds BIGINT,
  avg_watch_time_seconds NUMERIC,
  total_messages BIGINT,
  total_reactions BIGINT,
  total_gifts_amount NUMERIC
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    ls.id,
    ls.title,
    ls.status,
    ls.started_at,
    ls.ended_at,
    EXTRACT(EPOCH FROM (COALESCE(ls.ended_at, NOW()) - ls.started_at))::BIGINT,
    ls.peak_viewers,
    (SELECT COUNT(DISTINCT user_id) FROM live_stream_viewers WHERE stream_id = ls.id),
    (SELECT COALESCE(SUM(watch_duration_seconds), 0) FROM live_stream_viewers WHERE stream_id = ls.id),
    (SELECT COALESCE(AVG(watch_duration_seconds), 0)::NUMERIC FROM live_stream_viewers WHERE stream_id = ls.id AND watch_duration_seconds IS NOT NULL),
    (SELECT COUNT(*) FROM live_stream_chat WHERE stream_id = ls.id),
    (SELECT COALESCE(SUM(count), 0) FROM live_stream_reactions WHERE stream_id = ls.id),
    (SELECT COALESCE(SUM(amount), 0) FROM live_stream_gifts WHERE stream_id = ls.id)
  FROM live_streams ls
  WHERE ls.id = p_stream_id;
END;
$$ LANGUAGE plpgsql STABLE;

-- أفضل البثوث أداءً (Top Streams)
CREATE OR REPLACE FUNCTION red_get_top_streams(
  p_community_id UUID DEFAULT NULL,
  p_days INT DEFAULT 30,
  p_limit INT DEFAULT 20
) RETURNS TABLE(
  stream_id UUID,
  channel_id UUID,
  host_id UUID,
  title TEXT,
  started_at TIMESTAMPTZ,
  peak_viewers INT,
  total_unique_viewers BIGINT,
  total_watch_time_seconds BIGINT,
  engagement_score NUMERIC
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    ls.id,
    ls.channel_id,
    ls.host_id,
    ls.title,
    ls.started_at,
    ls.peak_viewers,
    COUNT(DISTINCT lsv.user_id) AS total_unique_viewers,
    COALESCE(SUM(lsv.watch_duration_seconds), 0) AS total_watch_time_seconds,
    -- درجة التفاعل: مشاهدون × متوسط وقت المشاهدة × تفاعلات
    (COUNT(DISTINCT lsv.user_id)::NUMERIC * 
     COALESCE(AVG(lsv.watch_duration_seconds), 1) * 
     (1 + COALESCE((SELECT COUNT(*) FROM live_stream_chat WHERE stream_id = ls.id), 0) * 0.1 +
      COALESCE((SELECT COUNT(*) FROM live_stream_reactions WHERE stream_id = ls.id), 0) * 0.05 +
      COALESCE((SELECT SUM(amount) FROM live_stream_gifts WHERE stream_id = ls.id), 0) * 0.01)
    ) AS engagement_score
  FROM live_streams ls
  LEFT JOIN live_stream_viewers lsv ON lsv.stream_id = ls.id
  WHERE ls.deleted_at IS NULL
    AND ls.started_at >= NOW() - (p_days || ' days')::INTERVAL
    AND (p_community_id IS NULL OR ls.community_id = p_community_id)
  GROUP BY ls.id, ls.channel_id, ls.host_id, ls.title, ls.started_at, ls.peak_viewers
  ORDER BY engagement_score DESC
  LIMIT p_limit;
END;
$$ LANGUAGE plpgsql STABLE;

-- ── 10) Views للقراءة السريعة (بدون Materialized) ──
-- البثوث النشطة حالياً
CREATE OR REPLACE VIEW v_active_live_streams AS
SELECT 
  ls.id, ls.channel_id, ls.host_id, ls.community_id, ls.title,
  ls.thumbnail_url, ls.started_at, ls.peak_viewers,
  (SELECT COUNT(*) FROM live_stream_viewers lsv WHERE lsv.stream_id = ls.id AND lsv.left_at IS NULL) AS current_viewers
FROM live_streams ls
WHERE ls.status = 'LIVE' AND ls.deleted_at IS NULL;

-- البثوث المجدولة القادمة
CREATE OR REPLACE VIEW v_upcoming_live_streams AS
SELECT 
  ls.id, ls.channel_id, ls.host_id, ls.community_id, ls.title,
  ls.thumbnail_url, ls.scheduled_at, ls.description
FROM live_streams ls
WHERE ls.status = 'SCHEDULED' AND ls.scheduled_at > NOW() AND ls.deleted_at IS NULL
ORDER BY ls.scheduled_at ASC;

-- ── 11) فهارس جزئية (Partial) للاستعلامات الشائعة ──
-- بثوث حية فقط (الأكثر استعلاماً)
CREATE INDEX IF NOT EXISTS idx_live_streams_live_only ON live_streams(channel_id, started_at DESC)
  WHERE status = 'LIVE' AND deleted_at IS NULL;

-- بثوث منتهية جاهزة للتسجيل/التحليل
CREATE INDEX IF NOT EXISTS idx_live_streams_ended_ready ON live_streams(host_id, ended_at DESC)
  WHERE status = 'ENDED' AND deleted_at IS NULL;

-- ── 12) إحصائيات مضيف (Host Analytics) ──
CREATE OR REPLACE FUNCTION red_get_host_stream_stats(p_host_id UUID, p_days INT DEFAULT 30)
RETURNS TABLE(
  total_streams BIGINT,
  total_live_hours NUMERIC,
  total_unique_viewers BIGINT,
  avg_peak_viewers NUMERIC,
  total_gifts_amount NUMERIC,
  avg_engagement_score NUMERIC
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    COUNT(*)::BIGINT,
    COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(ended_at, NOW()) - started_at))))::NUMERIC / 3600,
    COALESCE(SUM(viewer_count), 0)::BIGINT,
    COALESCE(AVG(peak_viewers), 0)::NUMERIC,
    COALESCE(SUM(gift_amount), 0)::NUMERIC,
    COALESCE(AVG(engagement), 0)::NUMERIC
  FROM (
    SELECT 
      ls.id,
      ls.peak_viewers,
      (SELECT COUNT(DISTINCT user_id) FROM live_stream_viewers WHERE stream_id = ls.id) AS viewer_count,
      (SELECT COALESCE(SUM(amount), 0) FROM live_stream_gifts WHERE stream_id = ls.id) AS gift_amount,
      (COUNT(DISTINCT lsv.user_id)::NUMERIC * COALESCE(AVG(lsv.watch_duration_seconds), 1)) AS engagement
    FROM live_streams ls
    LEFT JOIN live_stream_viewers lsv ON lsv.stream_id = ls.id
    WHERE ls.host_id = p_host_id
      AND ls.deleted_at IS NULL
      AND ls.started_at >= NOW() - (p_days || ' days')::INTERVAL
    GROUP BY ls.id, ls.peak_viewers
  ) s;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON TABLE live_streams IS 'البث المباشر - حالة: SCHEDULED, LIVE, ENDED, CANCELLED, ARCHIVED';
COMMENT ON TABLE live_stream_viewers IS 'مشاهدي البث المباشر - تتبع وقت الانضمام/المغادرة';
COMMENT ON TABLE live_stream_recordings IS 'تسجيلات البث المباشر - معالجة غير متزامنة';
COMMENT ON TABLE live_stream_chat IS 'دردشة البث المباشر - رسائل عالية التردد';
COMMENT ON FUNCTION red_get_live_stream_analytics IS 'تحليلات شاملة لبث مباشر واحد';
COMMENT ON FUNCTION red_get_top_streams IS 'أفضل البثوث أداءً خلال فترة';