-- Retention scheduler selects oldest rows in bounded batches.
-- These time indexes keep operational cleanup and range dashboards scalable.
CREATE INDEX IF NOT EXISTS idx_admin_audit_retention_created ON admin_audit_log(created_at ASC);
CREATE INDEX IF NOT EXISTS idx_dinstar_cdr_retention_start ON dinstar_cdr(start_time ASC);
CREATE INDEX IF NOT EXISTS idx_system_health_retention_checked ON system_health(last_check_at ASC);

COMMENT ON INDEX idx_admin_audit_retention_created IS 'Bounded retention batch selection';
COMMENT ON INDEX idx_dinstar_cdr_retention_start IS 'Bounded CDR retention batch selection';
COMMENT ON INDEX idx_system_health_retention_checked IS 'Bounded health retention batch selection';
