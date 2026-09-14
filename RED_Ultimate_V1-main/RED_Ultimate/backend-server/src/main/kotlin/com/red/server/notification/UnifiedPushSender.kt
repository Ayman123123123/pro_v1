package com.red.server.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI

/**
 * Sovereign push delivery — UnifiedPush, self-hosted.
 *
 * POSTs wake payloads straight to the endpoint URL the device's distributor
 * issued (stored per device in `device_push_tokens` via /api/devices/push-token).
 * No Google push SDK, no third party in the path: server → distributor → device.
 *
 * Payload contract (client: RedPushService):
 * - `{"v":1,"type":"CALL","callId","mode","callerId","callerName","callType","ts"}`
 * - `{"v":1,"type":"MESSAGE","senderId","ts"}` (no preview — E2EE stays sealed)
 * - `{"v":1,"type":"CANCEL","callId","ts"}`
 *
 * Tuning: PUSH_CONNECT_TIMEOUT_MS / PUSH_READ_TIMEOUT_MS (default 5000),
 * PUSH_ALLOW_HTTP_ENDPOINTS (default true — LAN distributors serve plain HTTP).
 */
@Service
class UnifiedPushSender {
    private val logger = LoggerFactory.getLogger(UnifiedPushSender::class.java)
    private val connectTimeoutMs =
        System.getenv("PUSH_CONNECT_TIMEOUT_MS")?.toIntOrNull()?.coerceIn(1000, 15000) ?: 5000
    private val readTimeoutMs =
        System.getenv("PUSH_READ_TIMEOUT_MS")?.toIntOrNull()?.coerceIn(1000, 15000) ?: 5000
    private val allowHttp =
        System.getenv("PUSH_ALLOW_HTTP_ENDPOINTS")?.toBooleanStrictOrNull() ?: true

    /**
     * POSTs [payload] to a distributor endpoint.
     * @return HTTP status, or -1 when rejected/transport failed. Never throws.
     * Never logs the endpoint itself — topic URLs are bearer capabilities.
     */
    fun send(endpoint: String, payload: ByteArray): Int {
        val url = runCatching { URI(endpoint.trim()).toURL() }.getOrNull()
        val schemeOk = url != null && (url.protocol == "https" || (url.protocol == "http" && allowHttp))
        if (!schemeOk || url == null) {
            logger.warn("push.send_rejected reason=bad_scheme_or_http_blocked")
            return -1
        }
        // SSRF guard: endpoints are device-supplied, never trust them blindly.
        val host = url.host?.lowercase().orEmpty()
        if (host.isEmpty() || host == "169.254.169.254" || host == "metadata.google.internal") {
            logger.warn("push.send_rejected reason=blocked_host")
            return -1
        }
        return runCatching {
            val conn = url.openConnection() as java.net.HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Content-Length", payload.size.toString())
                conn.outputStream.use { it.write(payload) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    logger.warn("push.send_error code={} body={}", code, err.take(300))
                }
                code
            } finally {
                conn.disconnect()
            }
        }.getOrElse {
            logger.warn("push.send_failed err={}", it.message)
            -1
        }
    }
}
