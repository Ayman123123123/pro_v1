package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
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
            listOfNotNull(users.findByUsernameIgnoreCase(term))
        }

        return matches
            .filter { it.status == AccountStatus.APPROVED }
            .filter { it.id != caller }
            .map { PublicRedProfile(it.redId, it.username, it.displayName, it.avatarUrl) }
    }

    companion object {
        const val RATE_LIMIT_NAMESPACE = "directory-search"
        const val DIRECTORY_MAX_QUERIES = 20L
        const val DIRECTORY_WINDOW_MINUTES = 1L
    }
}

data class PublicRedProfile(
    val redId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null
)
