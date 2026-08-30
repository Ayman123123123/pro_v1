-- V44 — Call Timeline, Memory Management & Analytics
-- تتبع مراحل المكالمة + إحصائيات الأداء + فهارس محسّنة

-- ═══════════════════════════════════════════════════════════════
-- 1) جدول timeline المكالمات
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS pstn_call_timeline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_id VARCHAR(64) NOT NULL REFERENCES pstn_active_calls(call_id) ON DELETE CASCADE,
    stage VARCHAR(20) NOT NULL CHECK (stage IN ('DIALING', 'RINGING', 'BRIDGING', 'ACTIVE', 'ENDED')),
    stage_data JSONB,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- فهرس للبحث السريع عن timeline مكالمة
CREATE INDEX IF NOT EXISTS idx_call_timeline_call_id
    ON pstn_call_timeline (call_id, started_at DESC);

-- فهرس للبحث عن المراحل حسب الوقت
CREATE INDEX IF NOT EXISTS idx_call_timeline_started_at
    ON pstn_call_timeline (started_at DESC);

COMMENT ON TABLE pstn_call_timeline IS 'تتبع مراحل المكالمة (DIALING→RINGING→BRIDGING→ACTIVE→ENDED)';

-- ═══════════════════════════════════════════════════════════════
-- 2) تحسينات على جدول gateway_route_decisions
-- ═══════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_route_decisions_created_at
    ON gateway_route_decisions (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_route_decisions_outcome
    ON gateway_route_decisions (outcome, created_at DESC);

-- ═══════════════════════════════════════════════════════════════
-- 3) تحسينات على جدول dinstar_cdr
-- ═══════════════════════════════════════════════════════════════
CREATE INDEX IF NOT EXISTS idx_cdr_start_time
    ON dinstar_cdr (start_time DESC);

CREATE INDEX IF NOT EXISTS idx_cdr_status
    ON dinstar_cdr (status, start_time DESC);

CREATE INDEX IF NOT EXISTS idx_cdr_direction
    ON dinstar_cdr (direction, start_time DESC);

-- ═══════════════════════════════════════════════════════════════
-- 4) View لإحصائيات الأداء اليومية
-- ═══════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW v_pstn_daily_stats AS
SELECT
    DATE(start_time)::date as stat_date,
    COUNT(*) as total_calls,
    COUNT(*) FILTER (WHERE status = 'answered') as answered_calls,
    COUNT(*) FILTER (WHERE status = 'no_answer') as no_answer_calls,
    COUNT(*) FILTER (WHERE status = 'busy') as busy_calls,
    COUNT(*) FILTER (WHERE status = 'failed') as failed_calls,
    AVG(duration_seconds) as avg_duration,
    AVG(ring_duration_seconds) as avg_ring_duration,
    COUNT(DISTINCT gateway_id) as gateways_used,
    COUNT(DISTINCT caller_number) as unique_callers
FROM dinstar_cdr
GROUP BY DATE(start_time)::date
ORDER BY stat_date DESC;

COMMENT ON VIEW v_pstn_daily_stats IS 'إحصائيات يومية مجمعة للمكالمات';

-- ═══════════════════════════════════════════════════════════════
-- 5) دالة لحساب إحصائيات الوقت الحقيقي
-- ═══════════════════════════════════════════════════════════════
CREATE OR REPLACE FUNCTION get_pstn_realtime_stats(hours_back INTEGER DEFAULT 1)
RETURNS TABLE (
    total_calls BIGINT,
    active_calls BIGINT,
    avg_duration DOUBLE PRECISION,
    success_rate DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        COUNT(*)::BIGINT,
        (SELECT COUNT(*) FROM pstn_active_calls WHERE expires_at > NOW())::BIGINT,
        AVG(duration_seconds),
        CASE WHEN COUNT(*) = 0 THEN 0.0
             ELSE COUNT(*) FILTER (WHERE status = 'answered')::DOUBLE PRECISION / COUNT(*)
        END
    FROM dinstar_cdr
    WHERE start_time >= NOW() - (hours_back || ' hours')::INTERVAL;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_pstn_realtime_stats(INTEGER) IS 'حساب إحصائيات PSTN اللحظية لـ N ساعة الماضية';
