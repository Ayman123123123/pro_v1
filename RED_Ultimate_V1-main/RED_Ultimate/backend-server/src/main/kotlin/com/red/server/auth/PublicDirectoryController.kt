package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/**
 * الدليل العام — البحث عن مستخدم معتمد بمعرّف يونس أو باسم المستخدم.
 *
 * ⚠️ ملاحظة أمنية جوهرية بعد اختصار معرّف يونس إلى خمسة أرقام:
 * فضاء المعرّفات صار 90,000 فقط (10000..99999)، أي قابلًا للتعداد
 * الكامل. الصيغة القديمة `41382` كانت تعطي ما يفوق 10¹²
 * احتمالًا فتجعل التخمين مستحيلًا عمليًا؛ أما الآن فالمعرّف صار
 * كرقم الهاتف: مُعرِّفًا للعرض لا سرًّا.
 *
 * لذلك انتقلت الحماية من طول المعرّف إلى ضبط المعدل هنا:
 * [DIRECTORY_MAX_QUERIES] بحثًا في [DIRECTORY_WINDOW_MINUTES] دقيقة لكل
 * مستخدم. بهذا الحد يحتاج مسح الفضاء كاملًا إلى أكثر من ثلاثة أشهر
 * من الطلبات المتواصلة، بينما لا يقترب أي استخدام بشري طبيعي منه.
 *
 * الحدّ مرتبط بهوية المتصل (UUID) لا بعنوان IP: تغيير العنوان لا
 * يُعيد ضبط العدّاد، والحساب يلزمه اعتماد المسؤول أصلًا.
 */
@RestController
@RequestMapping("/api/directory")
class PublicDirectoryController(
    private val users: UserAccountRepository,
    private val rateLimiter: RateLimitService,
    private val jdbc: JdbcTemplate? = null
) {
    @GetMapping("/search")
    fun search(@RequestParam query: String, authentication: Authentication): List<PublicRedProfile> {
        val term = query.trim()
        require(term.length in 3..32) { "Search query must contain 3-32 characters" }

        val caller = UUID.fromString(authentication.name)
        // يرمي RateLimitExceededException ← 429 RATE_LIMITED عبر AuthExceptionHandler
        rateLimiter.check(
            namespace = RATE_LIMIT_NAMESPACE,
            identity = caller.toString(),
            maximum = DIRECTORY_MAX_QUERIES,
            window = Duration.ofMinutes(DIRECTORY_WINDOW_MINUTES),
        )

        // البحث بالمعرّف: يُطبَّع أولًا حتى يقبل ما يلصقه المستخدم من
        // الصيغة القديمة (YNS-12345) أو بمسافات، ثم يُطابَق تطابقًا تامًّا.
        // الفحص السابق `term.startsWith("RED-")` كان معطوبًا: البادئة
        // المولَّدة كانت `YNS-` فلم يكن البحث بالمعرّف يعمل إطلاقًا.
        val normalized = RedIdGenerator.normalize(term)
        val matches = if (normalized != null) {
            listOfNotNull(users.findByRedId(normalized))
        } else {
            users.findAllByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(term, term)
        }

        return matches
            .filter { it.status == AccountStatus.APPROVED }
            .filter { it.id != caller }
            .map { PublicRedProfile(it.redId, it.username, it.displayName, visibleAvatar(caller, it.id, it.avatarUrl)) }
            .ifEmpty {
                // تكامل V50: الاستعلام الشبيه بالهاتف يسقط على البحث العكسي
                // (شريحة مربوطة ثم caller_directory) عند غياب مطابقات المستخدمين.
                if (isPhoneLike(term)) phoneReverseLookup(term) else emptyList()
            }
    }

    /** LEGENDARY: خصوصية الصورة — EVERYONE/CONTACTS/NOBODY + حظر ثنائي (كانت تُكشف للجميع) */
    private fun visibleAvatar(viewerId: java.util.UUID, ownerId: java.util.UUID, avatarUrl: String?): String? {
        if (avatarUrl.isNullOrBlank()) return avatarUrl
        val blocked = runCatching {
            jdbc?.queryForObject(
                "SELECT COUNT(*) FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",
                Int::class.java, viewerId, ownerId, ownerId, viewerId
            ) ?: 0
        }.getOrDefault(0) > 0
        if (blocked) return null
        val scope = runCatching {
            jdbc?.queryForObject(
                "SELECT profile_photo FROM user_privacy_settings WHERE user_id=?",
                String::class.java, ownerId
            ) ?: "EVERYONE"
        }.getOrDefault("EVERYONE")
        return when (scope.uppercase()) {
            "NOBODY" -> null
            "CONTACTS", "CONTACTS_EXCEPT", "ONLY_SHARE_WITH" -> {
                val contact = runCatching {
                    jdbc?.queryForObject(
                        "SELECT COUNT(*) FROM red_contacts WHERE owner_id=? AND contact_id=?",
                        Int::class.java, ownerId, viewerId
                    ) ?: 0
                }.getOrDefault(0) > 0
                if (contact) avatarUrl else null
            }
            else -> avatarUrl
        }
    }

    /**
     * 📞 بحث عكسي مدمج — الدليل المجتمعي داخل /api/directory/search.
     * best-effort: أي عطل في JDBC يعيد قائمة فارغة ولا يكسر البحث.
     */
    private fun phoneReverseLookup(term: String): List<PublicRedProfile> = runCatching {
        val phone = CallerDirectoryController.normalizePhone(term) ?: return emptyList()
        val hit = jdbc?.query(
            "SELECT display_name, spam_score FROM caller_directory WHERE phone=?",
            { rs, _ -> PublicRedProfile("", phone, rs.getString("display_name"), null, phone, rs.getInt("spam_score")) },
            phone
        )?.firstOrNull()
        if (hit != null) listOf(hit) else emptyList()
    }.getOrDefault(emptyList())

    companion object {
        const val RATE_LIMIT_NAMESPACE = "directory-search"
        const val DIRECTORY_MAX_QUERIES = 20L
        const val DIRECTORY_WINDOW_MINUTES = 1L

        private val PHONE_LIKE = Regex("^[+]?[0-9][0-9\\s\\-().]{5,20}$")
        fun isPhoneLike(term: String): Boolean = PHONE_LIKE.matches(term.trim())
    }
}

data class PublicRedProfile(
    val redId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /** رقم الهاتف — يُملأ فقط لنتائج البحث العكسي (V50). */
    val phone: String? = null,
    /** درجة الإزعاج المجتمعية — لنتائج البحث العكسي فقط (V50). */
    val spamScore: Int? = null
)
