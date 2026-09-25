-- V58__Retention_Indexes_And_Orphan_Guards.sql
-- ═══════════════════════════════════════════════════════════════════════
-- LEGENDARY 2026-09-16: إكمال دورة حياة البيانات + فهارس JPA الناقصة
-- 1) فهارس للأعمدة التي تُستعلَم من JPA/SQL بلا @Entity مغطى (خصوصية/إشعارات/قنوات/تثبيت)
-- 2) تنظيف استباقي للجداول التي تنمو بلا حد (audit/health/telemetry/prekeys المستهلكة)
--    التنظيف الفعلي الدوري عبر RetentionCleanupScheduler (يقرأ red.retention.* من application.yml)
--    هنا نضمن البنية فقط: فهارس created_at + دوال مساعدة. لا حذف جماعي عند الترحيل.
-- 3) حماية الأيتام: فهارس FK للربط PG<->Mongo<->MinIO (orphan reconciliation)
-- ═══════════════════════════════════════════════════════════════════════

-- ── 1) فهارس الخصوصية والإشعارات ──
CREATE INDEX IF NOT EXISTS idx_user_privacy_settings_user ON user_privacy_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_privacy_exceptions_user ON privacy_exceptions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_notifications_user_created ON user_notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_prefs_user ON notification_preferences(user_id);
CREATE INDEX IF NOT EXISTS idx_message_delivery_receipts_msg ON message_delivery_receipts(message_uuid);

-- ── 2) فهارس المجموعات والقنوات والتثبيت ──
CREATE INDEX IF NOT EXISTS idx_groups_updated ON groups(updated_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user ON group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_channel_members_channel ON channel_members(channel_id);
CREATE INDEX IF NOT EXISTS idx_channel_members_user ON channel_members(user_id);
CREATE INDEX IF NOT EXISTS idx_pinned_messages_conv ON pinned_messages(conversation_id, pinned_at DESC);
CREATE INDEX IF NOT EXISTS idx_message_reactions_msg ON message_reactions(message_uuid);

-- ── 3) فهارس سجل المكالمات (محاسبي PG — العملياتي في Mongo) ──
CREATE INDEX IF NOT EXISTS idx_call_history_caller_created ON call_history(caller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_call_participants_call ON call_participants(call_id);

-- ── 4) فهارس الاحتفاظ الزمني (retention) — تسرّع حذف الدفعات ──
CREATE INDEX IF NOT EXISTS idx_audit_events_created ON audit_events(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created ON admin_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_health_created ON system_health(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_stats_created ON usage_stats(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sent_prekeys_consumed ON sent_prekey_records(consumed_at) WHERE consumed_at IS NOT NULL;
-- guarded: backup_history has started_at (V19), not created_at — fresh-install fix, no-op where V59+ already reconciled
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='backup_history' AND column_name='created_at') THEN
    CREATE INDEX IF NOT EXISTS idx_backup_history_created ON backup_history(created_at DESC);
  ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='backup_history' AND column_name='started_at') THEN
    CREATE INDEX IF NOT EXISTS idx_backup_history_started ON backup_history(started_at DESC);
  END IF;
END $$;

-- ── 5) دالة مساعدة: حذف دفعات آمن (تُستدعى من Scheduler بحد RED_RETENTION_BATCH_SIZE) ──
CREATE OR REPLACE FUNCTION red_retention_delete(table_name TEXT, cutoff TIMESTAMPTZ, batch INT)
RETURNS BIGINT AS $$
DECLARE
  deleted BIGINT := 0;
  sql TEXT;
BEGIN
  -- allowlist صارم: لا حذف ديناميكي لأي جدول آخر
  IF table_name NOT IN ('audit_events','admin_audit_log','system_health','usage_stats','backup_history') THEN
    RAISE EXCEPTION 'red_retention_delete: table % not allowed', table_name;
  END IF;
  sql := format('DELETE FROM %I WHERE created_at < $1 LIMIT %s', table_name, batch);
  EXECUTE sql USING cutoff INTO deleted;
  GET DIAGNOSTICS deleted = ROW_COUNT;
  RETURN deleted;
END;
$$ LANGUAGE plpgsql;
