package com.red.sovereign.core

import java.net.URI

/**
 * بصمة خادم يونس كما تظهر على السلك.
 *
 * اكتشاف الشبكة يقبل عقد الإنتاج فقط (`1.0.0-YOUNES` + `ECDSA_P256_SHA256`).
 * خادم Node/SQLite (`red-dev-server`) ليس مسارًا قانونيًا.
 */
object YounesServerSignature {
    const val VERSION = "1.0.0-YOUNES"
    const val DEFAULT_PORT = 8088
    const val DEFAULT_HTTPS_PORT = 8443

    private val HEALTH_READY = Regex("\\\"status\\\"\\s*:\\s*\\\"(UP|HEALTHY|DEGRADED)\\\"", RegexOption.IGNORE_CASE)
    private val YOUNES_MARK = Regex(
        "1\\.0\\.0-(YOUNES|RED)|\\\"brand\\\"\\s*:\\s*\\\"YOUNES\\\"|يونس",
        RegexOption.IGNORE_CASE,
    )
    private val AUTHORITY_ALG = Regex("ECDSA_P256_SHA256")
    private val AUTHORITY_HINT = Regex("\\\"v1\\\"|\\\"version\\\"\\s*:\\s*\\\"v1\\\"|\\\"publicKey\\\"")

    fun isReadyHealth(body: String): Boolean = HEALTH_READY.containsMatchIn(body)

    fun isYounesHealth(body: String): Boolean =
        body.isNotBlank() && isReadyHealth(body) && YOUNES_MARK.containsMatchIn(body)

    fun isYounesAuthority(body: String): Boolean =
        body.isNotBlank() && AUTHORITY_ALG.containsMatchIn(body) && AUTHORITY_HINT.containsMatchIn(body)

    /**
     * الخادم يُقبل إذا كان جاهزًا للدخول وثبتت هويته الإنتاجية.
     */
    fun isYounesServer(healthBody: String, authorityBody: String): Boolean {
        if (!isReadyHealth(healthBody)) return false
        return isYounesHealth(healthBody) || isYounesAuthority(authorityBody)
    }

    fun hostOf(value: String): String? = runCatching {
        val trimmed = value.trim()
        val uri = if (trimmed.contains("://")) URI(trimmed) else URI("http://$trimmed")
        uri.host?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun portOf(value: String, fallback: Int = DEFAULT_PORT): Int = runCatching {
        val trimmed = value.trim()
        val uri = if (trimmed.contains("://")) URI(trimmed) else URI("http://$trimmed")
        uri.port.takeIf { it > 0 } ?: fallback
    }.getOrDefault(fallback)

    fun baseUrl(hostOrUrl: String, port: Int): String {
        val raw = hostOrUrl.trim().trimEnd('/')
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            val uri = URI(raw)
            val host = uri.host ?: return raw
            val resolved = if (uri.port > 0) uri.port else port
            return URI(uri.scheme, null, host, resolved, null, null, null).toString().trimEnd('/')
        }
        val scheme = if (port == 443 || port == DEFAULT_HTTPS_PORT) "https" else "http"
        return "$scheme://$raw:$port"
    }

    fun ports(preferred: Int): List<Int> {
        val primary = if (preferred > 0) preferred else DEFAULT_PORT
        return linkedSetOf(primary, DEFAULT_PORT, DEFAULT_HTTPS_PORT).toList()
    }
    
    /** إنشاء URL كامل من IP/مضيف فقط */
    fun buildUrl(host: String, port: Int = DEFAULT_PORT): String {
        val scheme = if (port == 443 || port == DEFAULT_HTTPS_PORT) "https" else "http"
        return "$scheme://$host:$port"
    }
    
    /** استخراج IP/مضيف وبورت من URL للعرض */
    fun parseForDisplay(url: String): Pair<String, Int> {
        val host = hostOf(url) ?: "غير معروف"
        val port = portOf(url)
        return host to port
    }
}
