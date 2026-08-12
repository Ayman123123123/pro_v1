-- V19 CHECK on admin_audit_log.action rejected real admin actions
-- (USERS_LISTED, ACCOUNT_APPROVED, REPORT_RESOLVED, FEATURE_FLAG_UPDATED, …)
-- so listing users or resolving a report could 500. Audit must accept
-- new operational verbs without a migration for every page.
ALTER TABLE admin_audit_log DROP CONSTRAINT IF EXISTS admin_audit_action_check;
ALTER TABLE admin_audit_log DROP CONSTRAINT IF EXISTS admin_audit_category_check;
ALTER TABLE admin_audit_log ALTER COLUMN action TYPE VARCHAR(80);
ALTER TABLE admin_audit_log ALTER COLUMN category TYPE VARCHAR(40);
