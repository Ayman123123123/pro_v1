-- V60__Call_Quality_Indexes.sql
-- ═══════════════════════════════════════════════════════════════════════
-- فهارس لجودة المكالمات (Call Quality Metrics) - 2026-09-17
-- تحسين استعلامات: جودة المكالمة، إحصائيات الشبكة، تحليلات الأداء
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) فهارس جدول call_history (PG المحاسبي) ──
-- استعلامات: جودة حسب caller/callee، نطاق زمني، نوع المكالمة
CREATE INDEX IF NOT EXISTS idx_call_history_quality_caller_time ON call_history(caller_id, started_at DESC)
  INCLUDE (callee_id, ended_at, duration_seconds, call_type, quality_score, network_type);
CREATE INDEX IF NOT EXISTS idx_call_history_quality_callee_time ON call_history(callee_id, started_at DESC)
  INCLUDE (caller_id, ended_at, duration_seconds, call_type, quality_score, network_type);

-- فلترة حسب نوع المكالمة وجودة الشبكة
CREATE INDEX IF NOT EXISTS idx_call_history_type_quality ON call_history(call_type, quality_score DESC, started_at DESC)
  WHERE quality_score IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_call_history_network_quality ON call_history(network_type, quality_score DESC, started_at DESC)
  WHERE quality_score IS NOT NULL AND network_type IS NOT NULL;

-- تحليلات يومية/أسبوعية: تجميع حسب التاريخ
CREATE INDEX IF NOT EXISTS idx_call_history_date_quality ON call_history(started_at::date, quality_score DESC)
  WHERE quality_score IS NOT NULL;

-- ── 2) فهارس جدول call_participants (المشاركون في المكالمات الجماعية/المباشرة) ──
CREATE INDEX IF NOT EXISTS idx_call_participants_call_quality ON call_participants(call_id, joined_at, left_at)
  INCLUDE (user_id, quality_score, packet_loss_percent, jitter_ms, rtt_ms, codec);
CREATE INDEX IF NOT EXISTS idx_call_participants_user_time ON call_participants(user_id, joined_at DESC)
  INCLUDE (call_id, quality_score, packet_loss_percent, jitter_ms, rtt_ms);

-- ── 3) فهارس جدول call_timeline (من V44 - خط زمني للمكالمات) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='call_timeline') THEN
    CREATE INDEX IF NOT EXISTS idx_call_timeline_call_event ON call_timeline(call_id, event_type, event_at)
      INCLUDE (user_id, payload);
    CREATE INDEX IF NOT EXISTS idx_call_timeline_user_time ON call_timeline(user_id, event_at DESC);
  END IF;
END $$;

-- ── 4) فهارس جدول call_quality_metrics (إذا موجود - للجودة التفصيلية) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='call_quality_metrics') THEN
    CREATE INDEX IF NOT EXISTS idx_call_quality_metrics_call_time ON call_quality_metrics(call_id, measured_at)
      INCLUDE (user_id, packet_loss, jitter, rtt, bitrate, resolution, fps);
    CREATE INDEX IF NOT EXISTS idx_call_quality_metrics_user_time ON call_quality_metrics(user_id, measured_at DESC);
  END IF;
END $$;

-- ── 5) دوال مساعدة لتحليلات الجودة ──
-- متوسط الجودة لمستخدم خلال فترة
CREATE OR REPLACE FUNCTION red_call_quality_avg(user_id UUID, since TIMESTAMPTZ, until TIMESTAMPTZ)
RETURNS TABLE(avg_quality NUMERIC, total_calls BIGINT, avg_packet_loss NUMERIC, avg_jitter NUMERIC, avg_rtt NUMERIC) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    AVG(q.quality_score)::NUMERIC,
    COUNT(*)::BIGINT,
    AVG(q.packet_loss_percent)::NUMERIC,
    AVG(q.jitter_ms)::NUMERIC,
    AVG(q.rtt_ms)::NUMERIC
  FROM call_participants q
  JOIN call_history c ON c.id = q.call_id
  WHERE q.user_id = user_id
    AND q.joined_at >= since
    AND q.joined_at <= until
    AND q.quality_score IS NOT NULL;
END;
$$ LANGUAGE plpgsql STABLE;

-- ── 6) فهارس BRIN للجداول الكبيرة زمنيًا (call_history, call_participants) ──
-- BRIN indexes أصغر بكثير من B-tree للبيانات المرتبة زمنيًا
CREATE INDEX IF NOT EXISTS idx_call_history_brin_started ON call_history USING BRIN(started_at);
CREATE INDEX IF NOT EXISTS idx_call_participants_brin_joined ON call_participants USING BRIN(joined_at);

-- ── 7) View لتحليلات الجودة اليومية (Materialized view candidate - see V62) ──
CREATE OR REPLACE VIEW v_daily_call_quality AS
SELECT 
  started_at::date AS call_date,
  call_type,
  network_type,
  COUNT(*) AS total_calls,
  AVG(quality_score)::NUMERIC(5,2) AS avg_quality,
  PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY quality_score) AS median_quality,
  AVG(duration_seconds)::NUMERIC(10,2) AS avg_duration,
  COUNT(*) FILTER (WHERE quality_score < 60) AS poor_quality_count
FROM call_history
WHERE quality_score IS NOT NULL
GROUP BY started_at::date, call_type, network_type;

COMMENT ON VIEW v_daily_call_quality IS 'تحليلات جودة المكالمات اليومية - مرشحة للـ Materialized View في V62';