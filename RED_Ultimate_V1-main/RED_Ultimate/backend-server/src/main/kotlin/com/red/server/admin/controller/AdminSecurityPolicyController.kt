package com.red.server.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.admin.model.AdminSystemConfig
import com.red.server.admin.repository.AdminSystemConfigRepository
import com.red.server.admin.service.AdminService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * 🛡️ سياسة الأمان الإدارية (MFA + SSO) — فوق مخزن `admin_system_config` (V68) بلا جداول جديدة.
 *
 * - MFA: سياسة إشراف (إلزام للأدمن/الجميع، الطرق المسموحة، نافذة TOTP) — مفاتيح `mfa.*`.
 *   تسجيل TOTP الفعلي للتسليم يتطلب تدفق دخول جديد (مشروع مستقل موثق).
 * - SSO: إعداد OIDC — مفاتيح `sso.oidc.*`. `clientSecret` يُكتب فقط ولا يُعاد في القراءة أبدًا.
 * - الاختبار حقيقي: جلب `{issuer}/.well-known/openid-configuration` والتحقق من JSON
 *   وحقوله الأساسية — نتيجة صريحة لا ادعاء نجاح.
 * - كل عمليات التأثير بتدقيق إلزامي في `admin_audit_log` (فئة SYSTEM، خطورة WARNING).
 * - كل المسارات تحت النطاق الإداري المحمي بـ hasRole("ADMIN").
 */
@RestController
@RequestMapping("/api/admin")
class AdminSecurityPolicyController(
    private val configRepo: AdminSystemConfigRepository,
    private val adminService: AdminService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(AdminSecurityPolicyController::class.java)
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private companion object {
        val ALLOWED_METHODS = setOf("TOTP", "WEB_AUTHN", "BACKUP_CODES", "SMS", "EMAIL")
        const val KEY_ENFORCE_ADMINS = "mfa.enforce_for_admins"
        const val KEY_ENFORCE_ALL = "mfa.enforce_for_all"
        const val KEY_METHODS = "mfa.allowed_methods"
        const val KEY_TOTP_WINDOW = "mfa.totp_window"
        const val KEY_SSO_PREFIX = "sso.oidc."
    }

    // ───────────────────── أدوات المخزن ─────────────────────

    private fun readConfig(key: String): String? = configRepo.findByConfigKey(key)?.configValue

    private fun writeConfig(key: String, value: String, desc: String, auth: Authentication) {
        val existing = configRepo.findByConfigKey(key)
        val entry = (existing ?: AdminSystemConfig(configKey = key)).apply {
            configValue = value
            valueType = "JSON"
            description = desc
            updatedBy = runCatching { UUID.fromString(auth.name) }.getOrNull()
            updatedAt = java.time.Instant.now()
            version = version + 1
        }
        configRepo.save(entry)
    }

    private fun audit(auth: Authentication, action: String, targetId: String, description: String) {
        val adminId = runCatching { UUID.fromString(auth.name) }.getOrNull()
        runCatching {
            adminService.recordAudit(
                adminId = adminId,
                adminUsername = auth.principal?.toString() ?: auth.name,
                action = action,
                category = "SYSTEM",
                targetType = "SECURITY_POLICY",
                targetId = targetId,
                description = description,
                severity = "WARNING"
            )
        }.onFailure { log.error("audit write failed for {} / {}: {}", action, targetId, it.message) }
    }

    private fun parseBool(raw: String?, default: Boolean): Boolean =
        raw?.lowercase()?.let { it == "true" || it == "1" } ?: default

    private fun parseScopesArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val parsed: Array<String>? = runCatching {
            objectMapper.readValue(raw, objectMapper.typeFactory.constructArrayType(String::class.java)) as Array<String>
        }.getOrNull()
        return parsed?.toList() ?: emptyList()
    }

    // ───────────────────── 🔐 MFA Policy ─────────────────────

    private fun mfaPolicyMap(): Map<String, Any?> = mapOf(
        "enforceForAdmins" to parseBool(readConfig(KEY_ENFORCE_ADMINS), true),
        "enforceForAll" to parseBool(readConfig(KEY_ENFORCE_ALL), false),
        "allowedMethods" to parseScopesArray(readConfig(KEY_METHODS)).ifEmpty { listOf("TOTP", "WEB_AUTHN", "BACKUP_CODES") },
        "totpWindow" to (readConfig(KEY_TOTP_WINDOW)?.toIntOrNull() ?: 1)
    )

    @GetMapping("/mfa/policy")
    fun getMfaPolicy(): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(mfaPolicyMap())

    @PutMapping("/mfa/policy")
    fun putMfaPolicy(@RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val allowedMethods = (body["allowedMethods"] as? List<*>)
            ?.map(Any?::toString)
            ?.filter { it in ALLOWED_METHODS }
            ?.distinct()
            ?: return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "error" to "ALLOWED_METHODS_REQUIRED"))

        if (allowedMethods.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(mapOf("success" to false, "error" to "AT_LEAST_ONE_METHOD_REQUIRED"))
        }

        val totpWindow = when (val raw = body["totpWindow"]) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
                ?: return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_TOTP_WINDOW"))
            null -> mfaPolicyMap()["totpWindow"] as Int
            else -> return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_TOTP_WINDOW"))
        }
        if (totpWindow !in 0..10) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "TOTP_WINDOW_OUT_OF_RANGE"))
        }

        val enforceAdmins = (body["enforceForAdmins"] as? Boolean) ?: (mfaPolicyMap()["enforceForAdmins"] as Boolean)
        val enforceAll = (body["enforceForAll"] as? Boolean) ?: (mfaPolicyMap()["enforceForAll"] as Boolean)

        writeConfig(KEY_ENFORCE_ADMINS, enforceAdmins.toString(), "سياسة MFA: إلزام الأدمن", auth)
        writeConfig(KEY_ENFORCE_ALL, enforceAll.toString(), "سياسة MFA: إلزام الجميع", auth)
        writeConfig(KEY_METHODS, objectMapper.writeValueAsString(allowedMethods), "سياسة MFA: الطرق المسموحة", auth)
        writeConfig(KEY_TOTP_WINDOW, totpWindow.toString(), "سياسة MFA: نافذة TOTP", auth)

        audit(auth, "MFA_POLICY_UPDATED", "mfa.policy", "admins=$enforceAdmins,all=$enforceAll,methods=$allowedMethods,window=$totpWindow")
        return ResponseEntity.ok(mfaPolicyMap())
    }

    // ───────────────────── 🌐 SSO (OIDC) ─────────────────────

    private fun ssoConfigMap(): Map<String, Any?> {
        val enabled = parseBool(readConfig("${KEY_SSO_PREFIX}enabled"), false)
        val issuer = readConfig("${KEY_SSO_PREFIX}issuer")
        return mapOf(
            "oidc" to mapOf(
                "enabled" to enabled,
                "issuer" to issuer,
                "clientId" to readConfig("${KEY_SSO_PREFIX}client_id"),
                "scopes" to parseScopesArray(readConfig("${KEY_SSO_PREFIX}scopes")).ifEmpty { listOf("openid", "profile", "email") },
                "redirectUri" to readConfig("${KEY_SSO_PREFIX}redirect_uri"),
                "logoutUri" to readConfig("${KEY_SSO_PREFIX}logout_uri"),
                "lastSync" to readConfig("${KEY_SSO_PREFIX}last_sync")
            ),
            "saml" to mapOf("enabled" to false)
        )
    }

    @GetMapping("/security/sso")
    fun getSsoConfig(): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(ssoConfigMap())

    @PutMapping("/security/sso")
    fun putSsoConfig(@RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val issuer = body["issuer"]?.toString()?.trim()?.trimEnd('/')
        if (issuer != null && issuer.isNotEmpty()) {
            if (!issuer.startsWith("https://")) {
                return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "ISSUER_MUST_BE_HTTPS"))
            }
            try {
                URI(issuer)
            } catch (_: Exception) {
                return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_ISSUER_URI"))
            }
        }
        if (body.containsKey("clientId")) {
            val clientId = body["clientId"]?.toString()?.trim().orEmpty()
            if (clientId.length > 200) {
                return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to "INVALID_CLIENT_ID"))
            }
        }

        fun put(key: String, value: String?, desc: String) {
            if (value != null) writeConfig("${KEY_SSO_PREFIX}$key", value, desc, auth)
        }
        put("enabled", body["enabled"]?.toString()?.lowercase(), "SSO OIDC مفعّل")
        put("issuer", issuer?.ifEmpty { null }, "SSO OIDC issuer")
        put("client_id", body["clientId"]?.toString()?.trim()?.ifEmpty { null }, "SSO OIDC client id")
        // clientSecret يُكتب إن حُرر ولا يُعاد أبدًا في القراءة
        put("client_secret", body["clientSecret"]?.toString()?.ifEmpty { null }, "SSO OIDC client secret (سر)")
        val scopes = (body["scopes"] as? List<*>)?.map(Any?::toString)
        if (scopes != null) writeConfig("${KEY_SSO_PREFIX}scopes", objectMapper.writeValueAsString(scopes), "SSO OIDC scopes", auth)
        put("redirect_uri", body["redirectUri"]?.toString()?.trim()?.ifEmpty { null }, "SSO OIDC redirect uri")
        put("logout_uri", body["logoutUri"]?.toString()?.trim()?.ifEmpty { null }, "SSO OIDC logout uri")
        writeConfig("${KEY_SSO_PREFIX}last_sync", java.time.Instant.now().toString(), "آخر مزامنة SSO", auth)

        audit(auth, "SSO_CONFIG_UPDATED", "sso.oidc", "issuer=${issuer ?: "—"}")
        return ResponseEntity.ok(ssoConfigMap())
    }

    /**
     * POST /security/sso/test/{issuer} — اختبار اكتشاف OIDC حقيقي:
     * يجلب `.well-known/openid-configuration` ويتحقق من JSON والحقول الأساسية.
     * النتيجة صريحة دائمًا: discovery صالح أو سبب الفشل — لا ادعاء نجاح.
     */
    @PutMapping("/security/sso/test/{issuerTest}")
    fun testSso(@PathVariable issuerTest: String, @RequestBody body: Map<String, Any?>, auth: Authentication): ResponseEntity<Any> {
        val issuer = (body["issuer"]?.toString() ?: issuerTest).trim().trimEnd('/')
        if (!issuer.startsWith("https://")) {
            return ResponseEntity.badRequest().body(
                mapOf("success" to false, "error" to "ISSUER_MUST_BE_HTTPS", "reachable" to false)
            )
        }
        val discoveryUrl = "$issuer/.well-known/openid-configuration"
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI(discoveryUrl))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                return ResponseEntity.ok(
                    mapOf(
                        "success" to false,
                        "reachable" to true,
                        "httpStatus" to response.statusCode(),
                        "error" to "DISCOVERY_HTTP_${response.statusCode()}",
                        "discoveryUrl" to discoveryUrl
                    )
                )
            }
            val json: Map<*, *> = runCatching { objectMapper.readValue(response.body(), Map::class.java) }
                .getOrElse {
                    return ResponseEntity.ok(
                        mapOf("success" to false, "reachable" to true, "error" to "DISCOVERY_INVALID_JSON", "discoveryUrl" to discoveryUrl)
                    )
                }
            val authorization = json["authorization_endpoint"]?.toString()
            val token = json["token_endpoint"]?.toString()
            if (authorization.isNullOrBlank() || token.isNullOrBlank()) {
                return ResponseEntity.ok(
                    mapOf("success" to false, "reachable" to true, "error" to "DISCOVERY_MISSING_ENDPOINTS", "discoveryUrl" to discoveryUrl)
                )
            }
            audit(auth, "SSO_TEST_OK", "sso.oidc", "discovery ok for $issuer")
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "reachable" to true,
                    "discoveryUrl" to discoveryUrl,
                    "issuer" to (json["issuer"]?.toString() ?: issuer),
                    "authorizationEndpoint" to authorization,
                    "tokenEndpoint" to token,
                    "scopesSupported" to (json["scopes_supported"] as? List<*> ?: emptyList<String>())
                )
            )
        } catch (e: Exception) {
            log.warn("SSO discovery test failed for {}: {}", issuer, e.message)
            ResponseEntity.ok(
                mapOf(
                    "success" to false,
                    "reachable" to false,
                    "error" to "DISCOVERY_UNREACHABLE",
                    "detail" to (e.message ?: e.javaClass.simpleName),
                    "discoveryUrl" to discoveryUrl
                )
            )
        }
    }
}
