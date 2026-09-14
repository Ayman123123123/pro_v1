package com.red.server.auth

import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.UUID

/**
 * 📞 دليل المتصلين — V50
 * بحث عكسي عن أرقام الهواتف في الدليل المجتمعي (caller_directory).
 *
 * - GET /api/directory/phone/{phone} — بحث عكسي مجتمعي.
 *   404 PHONE_NOT_FOUND عند الغياب.
 * - PUT /api/directory/phone/{phone} {displayName} — اقتراح/تحديث اسم مجتمعي.
 * - POST /api/directory/phone/report {phone, reason} — بلاغ إزعاج (يرفع spam_score).
 *
 * البحث محدود المعدل بنفس صرامة PublicDirectoryController (فضاء الأرقام
 * قابل للتعداد، والدليل يكشف بيانات مجتمعية).
 */
@RestController
@RequestMapping("/api/directory/phone")
class CallerDirectoryController(
    private val jdbc: JdbcTemplate,
    private val rateLimiter: RateLimitService
) {

    data class UpsertRequest(val displayName: String = "")
    data class SpamReportRequest(val phone: String = "", val reason: String? = null)

    @GetMapping("/{phone}")
    fun lookup(@PathVariable phone: String, authentication: Authentication): ResponseEntity<Any> {
        checkRateLimit(authentication)
        val normalized = normalizePhone(phone) ?: throw IllegalArgumentException("INVALID_PHONE")

        // الدليل المجتمعي
        val row = jdbc.query(
            "SELECT phone, display_name, spam_score FROM caller_directory WHERE phone=?",
            { rs, _ ->
                mapOf(
                    "phone" to rs.getString("phone"),
                    "source" to "DIRECTORY",
                    "displayName" to rs.getString("display_name"),
                    "spamScore" to rs.getInt("spam_score")
                )
            },
            normalized
        ).firstOrNull() ?: throw NoSuchElementException("PHONE_NOT_FOUND")
        return ResponseEntity.ok(row)
    }

    @PutMapping("/{phone}")
    fun upsert(@PathVariable phone: String, @RequestBody req: UpsertRequest, authentication: Authentication): ResponseEntity<Any> {
        checkRateLimit(authentication)
        val normalized = normalizePhone(phone) ?: throw IllegalArgumentException("INVALID_PHONE")
        val name = req.displayName.trim()
        require(name.length in 1..100) { "INVALID_DISPLAY_NAME" }
        jdbc.update(
            """INSERT INTO caller_directory(phone, display_name, updated_at)
               VALUES (?,?,NOW())
               ON CONFLICT (phone) DO UPDATE SET display_name=EXCLUDED.display_name, updated_at=NOW()""",
            normalized, name
        )
        return ResponseEntity.ok(mapOf("success" to true, "phone" to normalized, "displayName" to name))
    }

    @PostMapping("/report")
    fun report(@RequestBody req: SpamReportRequest, authentication: Authentication): ResponseEntity<Any> {
        checkRateLimit(authentication)
        val normalized = normalizePhone(req.phone) ?: throw IllegalArgumentException("INVALID_PHONE")
        req.reason?.let { require(it.length <= 500) { "INVALID_REASON" } }
        val reporter = UUID.fromString(authentication.name)
        // السطر أولًا (بلا FK على phone عمدًا — الإبلاغ عن رقم مجهول يجب أن ينجح).
        jdbc.update(
            """INSERT INTO caller_directory(phone, display_name, updated_at)
               VALUES (?,?,NOW()) ON CONFLICT (phone) DO NOTHING""",
            normalized, normalized
        )
        jdbc.update(
            "INSERT INTO phone_spam_reports(phone, reporter, reason) VALUES (?,?,?)",
            normalized, reporter, req.reason?.trim()?.takeIf(String::isNotEmpty)
        )
        val score = jdbc.queryForObject(
            "UPDATE caller_directory SET spam_score = spam_score + 1, updated_at=NOW() WHERE phone=? RETURNING spam_score",
            Int::class.java, normalized
        ) ?: 0
        return ResponseEntity.ok(mapOf("success" to true, "phone" to normalized, "spamScore" to score))
    }

    private fun checkRateLimit(authentication: Authentication) {
        rateLimiter.check(
            namespace = RATE_LIMIT_NAMESPACE,
            identity = authentication.name,
            maximum = PHONE_MAX_QUERIES,
            window = Duration.ofMinutes(PHONE_WINDOW_MINUTES)
        )
    }

    companion object {
        const val RATE_LIMIT_NAMESPACE = "directory-phone-lookup"
        const val PHONE_MAX_QUERIES = 20L
        const val PHONE_WINDOW_MINUTES = 1L

        /** تطبيع رقم الهاتف: يحتفظ بالأرقام مع + بادئة اختيارية، وإلا null. */
        fun normalizePhone(raw: String): String? {
            val compact = raw.trim().replace(Regex("[\\s\\-().]"), "")
            if (!compact.matches(Regex("^\\+?[0-9]{6,19}$"))) return null
            return compact
        }
    }
}
