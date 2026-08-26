-- ══════════════════════════════════════════════════════════════════
-- V28: Admin RBAC and Security Hardening
-- PostgreSQL — توسيع نظام الصلاحيات وتحصين الوصول الإداري
-- ══════════════════════════════════════════════════════════════════

-- ━━━━ RBAC Roles ━━━━
-- إضافة أدوار إدارية محددة: SUPER_ADMIN, MODERATOR, SUPPORT
-- يتم التحقق منها في SecurityConfig و AdminV2Controller
DO $$
BEGIN
    ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_role;
    ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN', 'SUPER_ADMIN', 'MODERATOR', 'SUPPORT'));
EXCEPTION
    WHEN undefined_table THEN
        -- Table might not exist in some environments, though unlikely here.
        NULL;
END $$;

-- ━━━━ Permission Mapping ━━━━
CREATE TABLE IF NOT EXISTS admin_permissions (
    id UUID PRIMARY KEY,
    role VARCHAR(20) NOT NULL,
    permission VARCHAR(50) NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(role, permission)
);

-- ━━━━ Default Permissions ━━━━
INSERT INTO admin_permissions (id, role, permission) VALUES
    (gen_random_uuid(), 'SUPER_ADMIN', '*'),
    (gen_random_uuid(), 'ADMIN', 'USER_MANAGE'),
    (gen_random_uuid(), 'ADMIN', 'DINSTAR_MANAGE'),
    (gen_random_uuid(), 'ADMIN', 'CONTENT_MODERATE'),
    (gen_random_uuid(), 'ADMIN', 'SECURITY_VIEW'),
    (gen_random_uuid(), 'MODERATOR', 'CONTENT_MODERATE'),
    (gen_random_uuid(), 'MODERATOR', 'USER_VIEW'),
    (gen_random_uuid(), 'SUPPORT', 'USER_VIEW'),
    (gen_random_uuid(), 'SUPPORT', 'DINSTAR_VIEW')
ON CONFLICT DO NOTHING;

-- ━━━━ Extended Audit Metadata ━━━━
ALTER TABLE admin_audit_log ADD COLUMN IF NOT EXISTS target_red_id VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_admin_audit_red_id ON admin_audit_log(target_red_id) WHERE target_red_id IS NOT NULL;

COMMENT ON TABLE admin_permissions IS 'Granular RBAC permissions for sovereign management';
COMMENT ON COLUMN admin_audit_log.target_red_id IS 'Younes RED ID for fast filtering of user actions';
