package com.red.server.admin.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * 🗄️ مخازن لوحة الإدارة (V68): إعدادات النظام، الأدوار، مفاتيح API.
 *
 * تُغذّي صفحات: Config / Roles / ApiKeys في admin_dashboard ببيانات حقيقية
 * بدل البيانات الوهمية — نفس حقول العقد في
 * `admin_dashboard/src/types/index.ts` (ConfigEntry / Role / ApiKey).
 */

@Entity
@Table(name = "admin_system_config")
class AdminSystemConfig(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "config_key", nullable = false, length = 200, unique = true)
    var configKey: String = "",

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    var configValue: String = "",

    /** STRING | NUMBER | BOOLEAN | JSON */
    @Column(name = "value_type", nullable = false, length = 20)
    var valueType: String = "STRING",

    @Column(length = 500)
    var description: String? = null,

    @Column(nullable = false)
    var version: Long = 1,

    @Column(name = "created_by")
    var createdBy: UUID? = null,

    @Column(name = "updated_by")
    var updatedBy: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "admin_roles")
class AdminRole(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "role_name", nullable = false, length = 80, unique = true)
    var roleName: String = "",

    @Column(length = 500)
    var description: String? = null,

    /** JSON array نصًا، مثل: ["users.read","posts.moderate"] */
    @Column(nullable = false, columnDefinition = "TEXT")
    var permissions: String = "[]",

    @Column(name = "is_system", nullable = false)
    var isSystem: Boolean = false,

    @Column(name = "created_by")
    var createdBy: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "admin_api_keys")
class AdminApiKey(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "key_name", nullable = false, length = 120)
    var keyName: String = "",

    /** SHA-256 hex للمفتاح الخام — لا يُخزَّن الخام إطلاقًا. */
    @Column(name = "key_hash", nullable = false, length = 128, unique = true)
    var keyHash: String = "",

    /** أول 12 حرفًا للعرض، مثل: red_sk_1a2b3c… */
    @Column(name = "key_prefix", nullable = false, length = 24)
    var keyPrefix: String = "",

    /** JSON array نصًا، مثل: ["calls.read","users.read"] */
    @Column(nullable = false, columnDefinition = "TEXT")
    var scopes: String = "[]",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "created_by")
    var createdBy: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
