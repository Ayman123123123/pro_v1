package com.red.server.social

import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.URL

/**
 * 🔗 بطاقات الروابط — تستخرج Open Graph مع حماية SSRF
 * تمنع الوصول إلى 127.0.0.1, 10.x, 192.168.x, ::1
 */
@Service
class LinkCardService {

    fun fetch(url: String): LinkCard? {
        val normalized = normalizeUrl(url) ?: return null
        if (isPrivateAddress(normalized.host)) return null
        return try {
            val doc = Jsoup.connect(normalized.toString())
                .userAgent("YOUNES-LinkCard/1.0")
                .timeout(5000)
                .followRedirects(true)
                .get()
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:title]")?.attr("content")
                ?: doc.title().takeIf { it.isNotBlank() }
            val desc = doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.selectFirst("meta[name=description]")?.attr("content")
            val image = doc.selectFirst("meta[property=og:image]")?.attr("content")
            if (title.isNullOrBlank() && desc.isNullOrBlank()) return null
            LinkCard(url = normalized.toString(), title = title?.take(100), description = desc?.take(200), imageUrl = image?.takeIf { it.startsWith("http") })
        } catch (_: Exception) { null }
    }

    private fun normalizeUrl(raw: String): URL? = try {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        URL(withScheme).also { require(it.protocol in setOf("http", "https")) }
    } catch (_: Exception) { null }

    private fun isPrivateAddress(host: String): Boolean = runCatching {
        val addr = InetAddress.getByName(host)
        addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isAnyLocalAddress
    }.getOrDefault(true) // Fail closed — if cannot resolve, treat as private
}
