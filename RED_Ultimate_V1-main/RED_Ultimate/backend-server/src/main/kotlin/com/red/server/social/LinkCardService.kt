package com.red.server.social

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * 🔗 بطاقات الروابط — تستخرج Open Graph مع حماية SSRF.
 *
 * تُمنع العناوين الخاصة (loopback / range 10.x / 192.168.x / link-local / any-local)
 * **في كل قففة** من سلسلة إعادة التوجيه، وليس على العنوان الأول فقط، مع سقف لعدد
 * القففات وحجم الاستجابة. الفشل في حل الاسم يعامل كعنوان خاص (fail-closed).
 */
@Service
class LinkCardService {

    private val log = LoggerFactory.getLogger(javaClass)

    fun fetch(url: String): LinkCard? {
        val normalized = normalizeUrl(url) ?: return null
        if (isPrivateAddress(normalized.host)) return null
        return try {
            val (finalUrl, body) = followToDocument(normalized)
            val html = String(body, Charsets.UTF_8)
            val doc = Jsoup.parse(html, finalUrl.toString())
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.selectFirst("meta[name=twitter:title]")?.attr("content")
                ?: doc.title().takeIf { it.isNotBlank() }
            val desc = doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.selectFirst("meta[name=description]")?.attr("content")
            val image = doc.selectFirst("meta[property=og:image]")?.attr("content")
            if (title.isNullOrBlank() && desc.isNullOrBlank()) return null
            val safeImage = image?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.let { normalizeImage(it) }
            LinkCard(url = finalUrl.toString(), title = title?.take(100), description = desc?.take(200), imageUrl = safeImage)
        } catch (e: Exception) {
            log.debug("Link card fetch failed for {}: {}", normalized, e.message)
            null
        }
    }

    private fun followToDocument(start: URL): Triple<URL, ByteArray, String?> {
        var current = start
        var redirects = 0
        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "YOUNES-LinkCard/1.0")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                val next = resolveRedirect(current, location)
                if (next == null || ++redirects > MAX_REDIRECTS) return Triple(start, byteArrayOf(), null)
                current = next
                continue
            }
            try {
                if (code != HttpURLConnection.HTTP_OK) return Triple(start, byteArrayOf(), null)
                val input: InputStream = connection.inputStream
                val body = readCapped(input, MAX_BODY_BYTES)
                val contentType = connection.contentType
                return Triple(current, body, contentType)
            } finally {
                connection.disconnect()
            }
        }
    }

    /** يعيد عنوان القففة التالية بعد التحقق من بروتوكوله ومضيفه (لا خاص ولا تحويل بروتوكول خارج http/https). */
    private fun resolveRedirect(current: URL, location: String?): URL? = runCatching {
        val next = URL(current, location ?: return null)
        require(next.protocol in setOf("http", "https")) { "Redirect to unsupported protocol" }
        if (isPrivateAddress(next.host)) return null
        next
    }.getOrNull()

    private fun normalizeImage(raw: String): String? = runCatching {
        val url = URL(raw)
        require(url.protocol in setOf("http", "https")) { "Unsupported image protocol" }
        if (isPrivateAddress(url.host)) null else raw
    }.getOrNull()

    private fun readCapped(input: InputStream, cap: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > cap) {
                buffer.reset()
                break
            }
            buffer.write(chunk, 0, read)
        }
        input.close()
        return buffer.toByteArray()
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

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5000
        const val READ_TIMEOUT_MS = 5000
        const val MAX_REDIRECTS = 5
        const val MAX_BODY_BYTES = 1_048_576
        private val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,   // 301
            HttpURLConnection.HTTP_MOVED_TEMP,   // 302
            HttpURLConnection.HTTP_SEE_OTHER,    // 303
            307,
            308
        )
    }
}