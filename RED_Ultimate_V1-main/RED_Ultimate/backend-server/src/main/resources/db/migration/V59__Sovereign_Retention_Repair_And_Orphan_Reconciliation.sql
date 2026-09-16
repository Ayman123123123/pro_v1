-- V59__Sovereign_Retention_Repair_And_Orphan_Reconciliation.sql
-- Repair V58 breakage on fresh installs + real retention + orphan diagnostics.
-- All statements idempotent (IF EXISTS / IF NOT EXISTS + information_schema guards).
-- No modification of applied migrations (checksums safe).

-- 1) Drop impossible/duplicate indexes from V58 ------------------------------
DROP INDEX IF EXISTS idx_message_delivery_receipts_msg;
DROP INDEX IF EXISTS idx_sent_prekeys_consumed;
DROP INDEX IF EXISTS idx_backup_history_created;
DROP INDEX IF EXISTS idx_message_reactions_msg;
DROP INDEX IF EXISTS idx_admin_audit_log_created;
DROP INDEX IF EXISTS idx_audit_events_created;
DROP INDEX IF EXISTS idx_user_privacy_settings_user;
DROP INDEX IF EXISTS idx_notification_prefs_user;
DROP INDEX IF EXISTS idx_privacy_exceptions_user;
DROP INDEX IF EXISTS idx_group_members_group;
DROP INDEX IF EXISTS idx_channel_members_channel;
DROP INDEX IF EXISTS idx_call_participants_call;
DROP INDEX IF EXISTS idx_call_history_caller_created;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='backup_history' AND column_name='started_at') THEN
    CREATE INDEX IF NOT EXISTS idx_backup_history_started ON backup_history(started_at DESC);
  END IF;
END $$;

-- 2) Replace broken red_retention_delete (DELETE..LIMIT is invalid in Postgres)
CREATE OR REPLACE FUNCTION red_retention_delete(table_name TEXT, cutoff TIMESTAMPTZ, batch INT)
RETURNS BIGINT AS $$
DECLARE deleted BIGINT := 0; col TEXT; sql TEXT; safe_batch INT := GREATEST(100, LEAST(COALESCE(batch,10000), 50000));
BEGIN
  CASE table_name
    WHEN 'admin_audit_log' THEN col := 'created_at';
    WHEN 'audit_events' THEN col := 'created_at';
    WHEN 'system_health' THEN col := 'last_check_at';
    WHEN 'usage_stats' THEN col := 'created_at';
    WHEN 'backup_history' THEN col := 'started_at';
    WHEN 'user_notifications' THEN col := 'created_at';
    WHEN 'call_history' THEN col := 'started_at';
    ELSE RAISE EXCEPTION 'red_retention_delete: table % not allowed', table_name;
  END CASE;
  sql := format('DELETE FROM %I WHERE id IN (SELECT id FROM %I WHERE %I < $1 ORDER BY %I ASC LIMIT %s)', table_name, table_name, col, col, safe_batch);
  EXECUTE sql USING cutoff; GET DIAGNOSTICS deleted = ROW_COUNT; RETURN deleted;
END; $$ LANGUAGE plpgsql;

-- 3) Missing indexes (guarded) ------------------------------------------------
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='red_contacts') THEN
    CREATE INDEX IF NOT EXISTS idx_red_contacts_contact ON red_contacts(contact_id);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='user_blocks') THEN
    CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks(blocked_id);
    CREATE INDEX IF NOT EXISTS idx_user_blocks_pair ON user_blocks(blocker_id, blocked_id);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='contact_requests' AND column_name='recipient_id') THEN
    CREATE INDEX IF NOT EXISTS idx_contact_requests_recipient ON contact_requests(recipient_id, status, created_at DESC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='refresh_sessions' AND column_name='expires_at') THEN
    CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires ON refresh_sessions(expires_at) WHERE revoked_at IS NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='media_grants' AND column_name='owner_id') THEN
    CREATE INDEX IF NOT EXISTS idx_media_grants_owner ON media_grants(owner_id);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='user_reports' AND column_name='category') THEN
    CREATE INDEX IF NOT EXISTS idx_user_reports_category ON user_reports(category, status, created_at DESC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='polls' AND column_name='target_user_id') THEN
    CREATE INDEX IF NOT EXISTS idx_polls_target_user ON polls(target_user_id, status);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='story_viewers' AND column_name='viewer_id') THEN
    CREATE INDEX IF NOT EXISTS idx_story_viewers_viewer ON story_viewers(viewer_id, viewed_at DESC);
  END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='username') THEN
    CREATE INDEX IF NOT EXISTS idx_users_username_lower_trgm ON users USING gin (lower(username) gin_trgm_ops);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='hashtags' AND column_name='tag_name') THEN
    CREATE INDEX IF NOT EXISTS idx_hashtags_name_lower_trgm ON hashtags USING gin (lower(tag_name) gin_trgm_ops);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='admin_audit_log' AND column_name='created_at') THEN
    CREATE INDEX IF NOT EXISTS idx_admin_audit_log_retention ON admin_audit_log(created_at ASC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='system_health' AND column_name='last_check_at') THEN
    CREATE INDEX IF NOT EXISTS idx_system_health_retention ON system_health(last_check_at ASC);
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='user_notifications' AND column_name='created_at') THEN
    CREATE INDEX IF NOT EXISTS idx_user_notifications_retention ON user_notifications(created_at ASC);
  END IF;
END $$;

-- 4) Orphan diagnostics (read-only views, no auto-delete) --------------------
CREATE OR REPLACE VIEW v_orphan_channel_members AS
  SELECT m.* FROM channel_members m LEFT JOIN channels c ON c.id=m.channel_id WHERE c.id IS NULL;
CREATE OR REPLACE VIEW v_orphan_group_members AS
  SELECT m.* FROM group_members m LEFT JOIN groups g ON g.id=m.group_id WHERE g.id IS NULL;
CREATE OR REPLACE FUNCTION red_orphan_counts() RETURNS TABLE(name TEXT, cnt BIGINT) AS $$
  SELECT 'channel_members'::TEXT, COUNT(*) FROM v_orphan_channel_members UNION ALL
  SELECT 'group_members', COUNT(*) FROM v_orphan_group_members;
$$ LANGUAGE sql STABLE;
