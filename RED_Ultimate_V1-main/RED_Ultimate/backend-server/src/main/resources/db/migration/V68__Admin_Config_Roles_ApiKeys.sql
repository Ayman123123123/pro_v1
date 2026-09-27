-- V68: مخازن لوحة الإدارة — إعدادات النظام، الأدوار والصلاحيات، مفاتيح API
-- الهدف: تفعيل صفحات (الإعدادات / الأدوار / مفاتيح API) ببيانات حقيقية بدل البيانات الوهمية.
-- أسلوب متسق مع بقية الترحيلات: UUID، TIMESTAMP، TEXT، وإنشاء آمن IF NOT EXISTS.

-- ─────────────────────────── إعدادات النظام ───────────────────────────
CREATE TABLE IF NOT EXISTS admin_system_config (
    id           UUID PRIMARY KEY,
    config_key   VARCHAR(200)  NOT NULL,
    config_value TEXT          NOT NULL DEFAULT '',
    value_type   VARCHAR(20)   NOT NULL DEFAULT 'STRING',
    description  VARCHAR(500),
    version      BIGINT        NOT NULL DEFAULT 1,
    created_by   UUID,
    updated_by   UUID,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_admin_system_config_key UNIQUE (config_key)
);
CREATE INDEX IF NOT EXISTS idx_admin_system_config_updated
    ON admin_system_config(updated_at DESC);

-- ─────────────────────────── الأدوار ───────────────────────────
-- permissions تُخزَّن نصًا بصيغة JSON_array (مثل '["users.read","users.ban"]')
-- لتجنب اعتماد نوع JSONB الخاص بمحرك معين في كيان JPA.
CREATE TABLE IF NOT EXISTS admin_roles (
    id          UUID PRIMARY KEY,
    role_name   VARCHAR(80)  NOT NULL,
    description VARCHAR(500),
    permissions TEXT         NOT NULL DEFAULT '[]',
    is_system   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by  UUID,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_admin_roles_name UNIQUE (role_name)
);
CREATE INDEX IF NOT EXISTS idx_admin_roles_name ON admin_roles(role_name);

-- أدوار نظام أولية (is_system = TRUE: لا تُحذف من الواجهة)
INSERT INTO admin_roles (id, role_name, description, permissions, is_system)
VALUES
    (gen_random_uuid(), 'SUPER_ADMIN', 'صلاحيات كاملة على النظام',
     '["*"]'::text, TRUE),
    (gen_random_uuid(), 'ADMIN', 'إدارة المستخدمين والمحتوى والنظام',
     '["users.read","users.ban","users.role.assign","posts.moderate","calls.read","reports.handle","system.config","backups.manage"]'::text, TRUE),
    (gen_random_uuid(), 'MODERATOR', 'إشراف على المحتوى والتقارير فقط',
     '["users.read","posts.moderate","reports.handle"]'::text, TRUE),
    (gen_random_uuid(), 'VIEWER', 'اطلاع فقط بلا تعديل',
     '["users.read","calls.read"]'::text, TRUE)
ON CONFLICT (role_name) DO NOTHING;

-- ─────────────────────────── مفاتيح API ───────────────────────────
-- المفتاح الخام يُعرض مرة واحدة عند الإنشاء فقط؛ في القاعدة SHA-256 (key_hash)
-- مع key_prefix لأغراض العرض والتعرف.
CREATE TABLE IF NOT EXISTS admin_api_keys (
    id           UUID PRIMARY KEY,
    key_name     VARCHAR(120) NOT NULL,
    key_hash     VARCHAR(128) NOT NULL,
    key_prefix   VARCHAR(24)  NOT NULL,
    scopes       TEXT         NOT NULL DEFAULT '[]',
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at   TIMESTAMP,
    last_used_at TIMESTAMP,
    created_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_admin_api_keys_hash UNIQUE (key_hash)
);
CREATE INDEX IF NOT EXISTS idx_admin_api_keys_active ON admin_api_keys(is_active, created_at DESC);
