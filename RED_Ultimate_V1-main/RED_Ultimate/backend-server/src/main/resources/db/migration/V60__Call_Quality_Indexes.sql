-- V60__Call_Quality_Indexes.sql
-- ═══════════════════════════════════════════════════════════════════════
-- فهارس لجودة المكالمات (Call Quality Metrics) - 2026-09-17
-- V64-revised: كل الفهارس محمية بـ IF EXISTS على الأعمدة
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) فهارس call_history (تُطبق فقط بعد V64 التي تضيف الأعمدة) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='call_history' AND column_name='quality_score') THEN
    CREATE INDEX IF NOT EXISTS idx_call_history_quality_caller_time ON call_history(caller_id, started_at DESC)
      INCLUDE (callee_id, ended_at, quality_score);
    CREATE INDEX IF NOT EXISTS idx_call_history_type_quality ON call_history(call_type, quality_score DESC, started_at DESC)
      WHERE quality_score IS NOT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='call_history' AND column_name='network_type') THEN
    CREATE INDEX IF NOT EXISTS idx_call_history_network_quality ON call_history(network_type, quality_score DESC, started_at DESC)
      WHERE quality_score IS NOT NULL AND network_type IS NOT NULL;
  END IF;
END $$;

-- فهارس أساسية موجودة دائمًا
CREATE INDEX IF NOT EXISTS idx_call_history_date ON call_history(started_at DESC);

-- ── 2) فهارس call_participants (تُطبق فقط بعد V64) ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='call_participants' AND column_name='quality_score') THEN
    CREATE INDEX IF NOT EXISTS idx_call_participants_call_quality ON call_participants(call_id, joined_at, left_at)
      INCLUDE (user_id, quality_score);
    CREATE INDEX IF NOT EXISTS idx_call_participants_user_time ON call_participants(user_id, joined_at DESC)
      INCLUDE (call_id, quality_score);
  END IF;
END $$;

-- ── 3) فهارس call_timeline ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='call_timeline') THEN
    CREATE INDEX IF NOT EXISTS idx_call_timeline_call_event ON call_timeline(call_id, event_type, event_at);
    CREATE INDEX IF NOT EXISTS idx_call_timeline_user_time ON call_timeline(user_id, event_at DESC);
  END IF;
END $$;

-- ── 4) فهارس call_quality_metrics ──
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='call_quality_metrics') THEN
    CREATE INDEX IF NOT EXISTS idx_call_quality_metrics_call_time ON call_quality_metrics(call_id, measured_at);
    CREATE INDEX IF NOT EXISTS idx_call_quality_metrics_user_time ON call_quality_metrics(user_id, measured_at DESC);
  END IF;
END $$;

-- ── 5) دوال مساعدة ──
CREATE OR REPLACE FUNCTION red_call_quality_avg(p_user_id UUID, since TIMESTAMPTZ, until TIMESTAMPTZ)
RETURNS TABLE(avg_quality NUMERIC, total_calls BIGINT) AS $$
BEGIN
  RETURN QUERY
  SELECT
    AVG(ch.quality_score)::NUMERIC,
    COUNT(*)::BIGINT
  FROM call_history ch
  WHERE ch.caller_id = p_user_id
    AND ch.started_at >= since
    AND ch.started_at <= until
    AND ch.quality_score IS NOT NULL;
END;
$$ LANGUAGE plpgsql STABLE;

-- ── 6) BRIN indexes ──
CREATE INDEX IF NOT EXISTS idx_call_history_brin_started ON call_history USING BRIN(started_at);
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='call_participants' AND column_name='joined_at') THEN
    CREATE INDEX IF NOT EXISTS idx_call_participants_brin_joined ON call_participants USING BRIN(joined_at);
  END IF;
END $$;

COMMENT ON FUNCTION red_call_quality_avg IS 'متوسط جودة المكالمات لمستخدم خلال فترة';
