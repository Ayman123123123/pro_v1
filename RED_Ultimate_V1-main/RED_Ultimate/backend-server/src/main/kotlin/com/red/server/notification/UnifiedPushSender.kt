package com.red.server.notification

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Sovereign push delivery - UnifiedPush, self-hosted.
 *
 * POSTs wake payloads straight to the endpoint URL the device's distributor
 * issued (stored per device in `device_push_tokens` via /api/devices/push-token).
 * No Google push SDK, no third party in the path: server -> distributor -> device.
 *
 * Wake payload contract (client: com.red.sovereign.push.RedPushService):
 *   {"v":2,"e":"<base64url(nonce||AES-GCM(plaintext))>"}   <- what goes on the wire
 * with an identifiers-only plaintext:
 *   {"t":"CALL","i":"<callId>","c":"<callType>","f":"<callerId>","m":"VOICE|VIDEO"}
 *   {"t":"CANCEL","i":"<callId>"}
 *   {"t":"MESSAGE","i":"<senderId>"}
 *
 * Any non-wake payload (or a sealing failure) is forwarded verbatim, so callers
 * that already build a bespoke body keep working.
 *
 * Tuning: PUSH_CONNECT_TIMEOUT_MS / PUSH_READ_TIMEOUT_MS (default 5000),
 * PUSH_ALLOW_HTTP_ENDPOINTS (default true - LAN distributors serve plain HTTP),
 * PUSH_MAX_RETRIES (default 2, bounded exponential backoff, honours Retry-After).
 */
@Service
class UnifiedPushSender {
    private val logger = LoggerFactory.getLogger(UnifiedPushSender::class.java)
    private val json = jacksonObjectMapper()

    private val connectTimeoutMs = envInt("PUSH_CONNECT_TIMEOUT_MS", 5000, 1000, 15000)
    private val readTimeoutMs = envInt("PUSH_READ_TIMEOUT_MS", 5000, 1000, 15000)
    private val maxRetries = envInt("PUSH_MAX_RETRIES", 2, 0, 5)
    private val retryBaseMs = envInt("PUSH_RETRY_BASE_MS", 300, 0, 5000)
    private val allowHttp = System.getenv("PUSH_ALLOW_HTTP_ENDPOINTS")?.toBooleanStrictOrNull() ?: true

    /**
     * Seals [payload] down to an identifiers-only wake (when recognised) and POSTs it.
     * @return HTTP status, or -1 when rejected/transport failed. Never throws.
     * Never logs the endpoint itself - topic URLs are bearer capabilities.
     */
    fun send(endpoint: String, payload: ByteArray): Int {
        // التسليم: سقف حجم الإيقاظ (8KB) — الحمولات الشاردة تُرفض محليًا بدل إغراق الموزع
        if (payload.isEmpty() || payload.size > 8 * 1024) {
            logger.warn("push.send_rejected reason=bad_size size={}", payload.size)
            return -1
        }
        val url = parse(endpoint) ?: return -1
        val outbound = sealWake(endpoint, payload)
        var code = -1
        var attempt = 0
        while (true) {
            val (status, retryAfterMs) = post(url, outbound)
            code = status
            if (status in 200..299) break
            val retryable = status < 0 || status == 408 || status == 425 || status == 429 || status in 500..599
            if (!retryable || attempt >= maxRetries) break
            val backoff = (retryBaseMs shl attempt).coerceAtMost(3000)
            val waitMs = (retryAfterMs ?: backoff).coerceIn(0, 3000)
            if (waitMs > 0) runCatching { Thread.sleep(waitMs.toLong()) }
            attempt++
        }
        return code
    }

    // ---------------------------------------------------------------- internals

    private fun parse(endpoint: String): URL? {
        val url = runCatching { URI(endpoint.trim()).toURL() }.getOrNull() ?: return null
        val schemeOk = url.protocol == "https" || (url.protocol == "http" && allowHttp)
        if (!schemeOk) {
            logger.warn("push.send_rejected reason=bad_scheme_or_http_blocked")
            return null
        }
        // SSRF guard: endpoints are device-supplied, never trust them blindly.
        val host = url.host?.lowercase().orEmpty()
        if (host.isEmpty() || host == "169.254.169.254" || host == "metadata.google.internal") {
            logger.warn("push.send_rejected reason=blocked_host")
            return null
        }
        return url
    }

    /** Reduces a recognised wake JSON to identifiers only and seals it; else passthrough. */
    private fun sealWake(endpoint: String, payload: ByteArray): ByteArray {
        val minimal = minimalWake(payload) ?: return payload
        val envelope = runCatching { SovereignPushCipher.envelope(endpoint, minimal) }.getOrNull() ?: return payload
        return envelope.toByteArray(Charsets.UTF_8)
    }

    private fun minimalWake(payload: ByteArray): String? = runCatching {
        val node = json.readTree(String(payload, Charsets.UTF_8))
        val type = node.path("type").asText("").ifBlank { node.path("t").asText("") }
        when (type) {
            "CALL" -> buildString {
                append("{\"t\":\"CALL\",")
                append("\"i\":").append(str(node.path("callId").asText(""))).append(',')
                append("\"c\":").append(str(node.path("callType").asText("").ifBlank { "1to1" })).append(',')
                append("\"f\":").append(str(node.path("callerId").asText(""))).append(',')
                append("\"m\":")
                    .append(str(if (node.path("mode").asText("").equals("VIDEO", true)) "VIDEO" else "VOICE"))
                append('}')
            }
            "CANCEL" -> "{\"t\":\"CANCEL\",\"i\":" + str(node.path("callId").asText("")) + "}"
            "MESSAGE" -> "{\"t\":\"MESSAGE\",\"i\":" + str(node.path("senderId").asText("")) + "}"
            else -> null
        }
    }.getOrNull()

    private fun post(url: URL, payload: ByteArray): Pair<Int, Int?> = runCatching {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Content-Length", payload.size.toString())
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            // Retry-After is seconds per RFC 9110 -> convert to ms for our backoff.
            val retryAfterMs = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.let { (it * 1000).toInt() }
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                logger.warn("push.send_error code={} body={}", code, err.take(300))
            }
            code to retryAfterMs
        } finally {
            conn.disconnect()
        }
    }.getOrElse {
        logger.warn("push.send_failed err={}", it.message)
        -1 to null
    }

    private fun str(value: String): String {
        val escaped = buildString(value.length + 8) {
            for (ch in value) when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        return "\"$escaped\""
    }

    private fun envInt(name: String, default: Int, min: Int, max: Int): Int =
        System.getenv(name)?.toIntOrNull()?.coerceIn(min, max) ?: default
}
