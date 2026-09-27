package com.red.server.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.admin.model.AdminApiKey
import com.red.server.admin.model.AdminRole
import com.red.server.admin.model.AdminSystemConfig
import com.red.server.admin.repository.AdminApiKeyRepository
import com.red.server.admin.repository.AdminRoleRepository
import com.red.server.admin.repository.AdminSystemConfigRepository
import com.red.server.admin.service.AdminService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * ⚙️ مخازن لوحة الإدارة (V68): إعدادات النظام / الأدوار / مفاتيح API.
 *
 * عقد الحقول مطابق تمامًا للأنواع في `admin_dashboard/src/types/index.ts`:
 * - ConfigEntry: key, value, type, description, version, updatedAt, updatedBy
 * - Role:        id, name, description, permissions, isSystem, createdAt, updatedAt
 * - ApiKey:      id, name, keyPrefix, scopes, expiresAt, lastUsedAt, createdAt, createdBy, isActive
 *
 * الأمان:
 * - كل المسارات تحت النطاق الإداري المحمي بـ hasRole("ADMIN").
 * - المفتاح الخام لمفتاح API يُولَّد هنا، يُخزَّن SHA-256 فقط، ويُعاد **مرة واحدة** عند الإنشاء.
 * - أدوار النظام (is_system) لا تُعدَّل ولا تُحذف عبر الواجهة.
 * - كل عمليات التأثير تُسجَّل إلزاميًا في `admin_audit_log`.
 */
@RestController
@RequestMapping("/api/admin")
class AdminOpsStoresController(
    private val configRepo: AdminSystemConfigRepository,
    private val rolesRepo: AdminRoleRepository,
    private val apiKeysRepo: AdminApiKeyRepository,
    private val adminService: AdminService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(AdminOpsStoresController::class.java)

    // ───────────────────── 🧰 أدوات مساعدة ─────────────────────

    private fun audit(auth: Authentication, action: String, targetType: String, targetId: String, description: String, severity: String = "INFO") {
        val adminId = runCatching { UUID.fromString(auth.name) }.getOrNull()
        runCatching {
            adminService.recordAudit(
                adminId = adminId,
                adminUsername = auth.principal?.toString() ?: auth.name,
                action = action,
                category = "SYSTEM",
                targetType = targetType,
                targetId = targetId,
                description = description,
                severity = severity
            )
        }.onFailure { log.error("audit write failed for {} / {}: {}", action, targetId, it.message) }
    }

    private fun adminRef(auth: Authentication): UUID? = runCatching { UUID.fromString(auth.name) }.getOrNull()

    private fun parseScopes(raw: String): List<String> {
        val parsed: Array<String>? = runCatching {
            objectMapper.readValue(raw, objectMapper.typeFactory.constructArrayType(String::class.java)) as Array<String>
        }.getOrNull()
        return parsed?.toList() ?: emptyList()
    }

    // ───────────────────── ⚙️ Config: /api/admin/system/config ─────────────────────

    private fun configMap(e: AdminSystemConfig): Map<String, Any?> = mapOf(
        "key" to e.configKey,
        "value" to e.configValue,
        "type" to e.valueType,
        "description" to e.description,
        "version" to e.version,
        "updatedAt" to e.updatedAt.toString(),
        "updatedBy" to e.updatedBy?.toString()
    )

    @GetMapping("/system/config")
    fun getConfig(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int,
        @RequestParam(required = false) search: String?
    ): ResponseEntity<Map<String, Any?>> {
        val safeSize = size.coerceIn(1, 200)
        val pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        val result = if (search.isNullOrBlank()) configRepo.findAll(pageable)
        else configRepo.findByConfigKeyContainingIgnoreCaseOrDescriptionContainingIgnoreCaseOrderByUpdatedAtDesc(
            search.trim(), search.trim(), pageable
        )
        return ResponseEntity.ok(
            mapOf(
                "content" to result.content.map(::configMap),
                "page" to page,
                "size" to safeSize,
                "totalElements" to result.totalElements,
                "totalPages" to result.totalPages
            )
        )
    }

    @PutMapping("/system/config/{key}")
    fun putConfig(
        @PathVariable key: String,
        @RequestBody body: Map<String, Any?>,
        auth: Authentication
    ): ResponseEntity<Any> {
        val value = body["value"]?.toString() ?: return ResponseEntity.badRequest()
            .body(mapOf("success" to false, "error" to "VALUE_REQUIRED"))
        val existing = configRepo.findByConfigKey(key)
        val entry = (existing ?: AdminSystemConfig(configKey = key)).apply {
            configValue = value
            valueType = (body["type"]?.toString() ?: valueType).uppercase().takeIf { it in setOf("STRING", "NUMBER", "BOOLEAN", "JSON") } ?: "STRING"
            description = body["description"]?.toString() ?: description
            updatedBy = adminRef(auth)
            updatedAt = Instant.now()
            version = version + 1
        }
        configRepo.save(entry)
        audit(auth, if (existing == null) "CONFIG_CREATED" else "CONFIG_UPDATED", "CONFIG", key, "value set (version=${entry.version})", "WARNING")
        return ResponseEntity.ok(configMap(entry))
    }

    @PostMapping("/system/config")
    fun createConfig(@RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val key = body["key"]?.toString()?.trim().orEmpty()
        if (key.isEmpty() || key.length > 200) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_KEY"))
        }
        if (configRepo.findByConfigKey(key) != null) {
            return ResponseEntity.status(409).body(mapOf("success" to false, "error" to "KEY_EXISTS"))
        }
        val entry = configRepo.save(
            AdminSystemConfig(
                configKey = key,
                configValue = body["value"]?.toString() ?: "",
                valueType = (body["type"]?.toString() ?: "STRING").uppercase().takeIf { it in setOf("STRING", "NUMBER", "BOOLEAN", "JSON") } ?: "STRING",
                description = body["description"]?.toString(),
                createdBy = adminRef(auth),
                updatedBy = adminRef(auth)
            )
        )
        audit(auth, "CONFIG_CREATED", "CONFIG", key, "new config entry")
        return ResponseEntity.ok(configMap(entry))
    }

    @DeleteMapping("/system/config/{key}")
    fun deleteConfig(@PathVariable key: String, auth: Authentication): ResponseEntity<Any> {
        val existing = configRepo.findByConfigKey(key)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "KEY_NOT_FOUND"))
        configRepo.delete(existing)
        audit(auth, "CONFIG_DELETED", "CONFIG", key, "config entry deleted", "WARNING")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ───────────────────── 🛡️ Roles: /api/admin/security/roles ─────────────────────

    private fun roleMap(e: AdminRole): Map<String, Any?> = mapOf(
        "id" to e.id.toString(),
        "name" to e.roleName,
        "description" to e.description,
        "permissions" to parseScopes(e.permissions),
        "isSystem" to e.isSystem,
        "createdAt" to e.createdAt.toString(),
        "updatedAt" to e.updatedAt.toString()
    )

    @GetMapping("/security/roles")
    fun getRoles(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(rolesRepo.findAllByOrderByCreatedAtAsc().map(::roleMap))

    @PostMapping("/security/roles")
    fun createRole(@RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val name = body["name"]?.toString()?.trim()?.uppercase().orEmpty()
        if (name.isEmpty() || name.length > 80) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_NAME"))
        }
        if (rolesRepo.findByRoleName(name) != null) {
            return ResponseEntity.status(409).body(mapOf("success" to false, "error" to "ROLE_EXISTS"))
        }
        @Suppress("UNCHECKED_CAST")
        val permissions = (body["permissions"] as? List<*>)?.map(Any?::toString) ?: emptyList()
        val role = rolesRepo.save(
            AdminRole(
                roleName = name,
                description = body["description"]?.toString(),
                permissions = objectMapper.writeValueAsString(permissions),
                isSystem = false,
                createdBy = adminRef(auth)
            )
        )
        audit(auth, "ROLE_CREATED", "ROLE", name, "custom role created (permissions=${permissions.size})")
        return ResponseEntity.ok(roleMap(role))
    }

    @PutMapping("/security/roles/{id}")
    fun updateRole(@PathVariable id: UUID, @RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val role = rolesRepo.findById(id).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "ROLE_NOT_FOUND"))
        if (role.isSystem) {
            return ResponseEntity.status(403).body(mapOf("success" to false, "error" to "SYSTEM_ROLE_IMMUTABLE"))
        }
        @Suppress("UNCHECKED_CAST")
        val permissions = (body["permissions"] as? List<*>)?.map(Any?::toString)
        role.apply {
            body["description"]?.let { description = it.toString() }
            if (permissions != null) this.permissions = objectMapper.writeValueAsString(permissions)
            updatedAt = Instant.now()
        }
        rolesRepo.save(role)
        audit(auth, "ROLE_UPDATED", "ROLE", role.roleName, "custom role updated", "WARNING")
        return ResponseEntity.ok(roleMap(role))
    }

    @DeleteMapping("/security/roles/{id}")
    fun deleteRole(@PathVariable id: UUID, auth: Authentication): ResponseEntity<Any> {
        val role = rolesRepo.findById(id).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "ROLE_NOT_FOUND"))
        if (role.isSystem) {
            return ResponseEntity.status(403).body(mapOf("success" to false, "error" to "SYSTEM_ROLE_IMMUTABLE"))
        }
        rolesRepo.delete(role)
        audit(auth, "ROLE_DELETED", "ROLE", role.roleName, "custom role deleted", "WARNING")
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ───────────────────── 🔑 API Keys: النطاق الإداري /security/api-keys ─────────────────────

    private fun apiKeyMap(e: AdminApiKey, includeSecret: String? = null): Map<String, Any?> = mapOf(
        "id" to e.id.toString(),
        "name" to e.keyName,
        "keyPrefix" to e.keyPrefix,
        "scopes" to parseScopes(e.scopes),
        "expiresAt" to e.expiresAt?.toString(),
        "lastUsedAt" to e.lastUsedAt?.toString(),
        "createdAt" to e.createdAt.toString(),
        "createdBy" to e.createdBy?.toString(),
        "isActive" to e.isActive,
        "secret" to includeSecret
    )

    @GetMapping("/security/api-keys")
    fun getApiKeys(): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(apiKeysRepo.findAllByOrderByCreatedAtDesc().map { apiKeyMap(it) })

    @PostMapping("/security/api-keys")
    fun createApiKey(@RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val name = body["name"]?.toString()?.trim().orEmpty()
        if (name.isEmpty() || name.length > 120) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_NAME"))
        }
        @Suppress("UNCHECKED_CAST")
        val scopes = (body["scopes"] as? List<*>)?.map(Any?::toString) ?: emptyList()
        val expiresAt = body["expiresAt"]?.let { raw ->
            try {
                Instant.parse(raw.toString())
            } catch (_: DateTimeParseException) {
                return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_EXPIRES_AT"))
            }
        }
        // المفتاح الخام: red_sk_ + 32 بايت عشوائي بترميز hex (256-bit). لا يُخزَّن الخام أبدًا.
        val rawKey = "red_sk_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(rawKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val key = apiKeysRepo.save(
            AdminApiKey(
                keyName = name,
                keyHash = hash,
                keyPrefix = rawKey.take(12),
                scopes = objectMapper.writeValueAsString(scopes),
                expiresAt = expiresAt,
                createdBy = adminRef(auth)
            )
        )
        audit(auth, "API_KEY_CREATED", "API_KEY", key.keyPrefix, "API key created (name=$name, scopes=${scopes.size})", "WARNING")
        // المفتاح الخام يُعاد مرة واحدة فقط هنا.
        return ResponseEntity.ok(apiKeyMap(key, includeSecret = rawKey))
    }

    @DeleteMapping("/security/api-keys/{id}")
    fun revokeApiKey(@PathVariable id: UUID, auth: Authentication): ResponseEntity<Any> {
        val key = apiKeysRepo.findById(id).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "error" to "KEY_NOT_FOUND"))
        apiKeysRepo.delete(key)
        audit(auth, "API_KEY_REVOKED", "API_KEY", key.keyPrefix, "API key revoked permanently", "WARNING")
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
