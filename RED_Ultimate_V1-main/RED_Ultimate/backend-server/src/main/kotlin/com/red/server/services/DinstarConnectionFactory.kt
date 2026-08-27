package com.red.server.services

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Ù…ØµÙ†Ø¹ Ø§ØªØµØ§Ù„Ø§Øª Ø¨ÙˆØ§Ø¨Ø§Øª DINSTAR.
 *
 * ÙƒØ§Ù†Øª `DinstarHardwareService` ØªØ¨Ù†ÙŠ `OkHttpClient` ÙˆØ§Ø­Ø¯Ù‹Ø§ Ù…Ø«Ø¨Ù‘ØªÙ‹Ø§ Ø¹Ù„Ù‰
 * Ø¹Ù†ÙˆØ§Ù† ÙˆØ§Ø­Ø¯ Ù…Ù† Ø§Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§ØªØŒ ÙØ§Ø³ØªØ­Ø§Ù„ Ù…Ø®Ø§Ø·Ø¨Ø© Ø¬Ù‡Ø§Ø² Ø«Ø§Ù†Ù. Ù‡Ù†Ø§ ÙŠÙÙØµÙ„ Ø¨Ù†Ø§Ø¡
 * Ø§Ù„Ø§ØªØµØ§Ù„ Ø¹Ù† Ù…Ù†Ø·Ù‚ Ø§Ù„Ø¹Ù…Ù„ Ù„ÙŠØµØ¨Ø­ Ù„ÙƒÙ„ Ø¨ÙˆØ§Ø¨Ø© ÙÙŠ Ø§Ù„Ø£Ø³Ø·ÙˆÙ„ Ø¹Ù…ÙŠÙ„Ù‡Ø§.
 *
 * ## Ø§Ù„Ù…ØµØ§Ø¯Ù‚Ø©
 * ØªØ³ØªØ®Ø¯Ù… ÙˆØ§Ø¬Ù‡Ø© UC2000 Ù…ØµØ§Ø¯Ù‚Ø© **HTTP Digest** (Ø§Ù„Ø¥ØµØ¯Ø§Ø±Ø§Øª Ø§Ù„Ø£Ù‚Ø¯Ù… Basic).
 * ÙŠÙØ³Ø¬ÙŽÙ‘Ù„ Ø§Ù„Ù…ÙØµØ§Ø¯ÙÙ‚Ø§Ù† Ù…Ø¹Ù‹Ø§ ÙˆÙŠØ®ØªØ§Ø± `DispatchingAuthenticator` Ø¨ÙŠÙ†Ù‡Ù…Ø§ Ø­Ø³Ø¨
 * ØªØ±ÙˆÙŠØ³Ø© `WWW-Authenticate`. Ø§Ù„Ù†ØªØ§Ø¦Ø¬ ØªÙØ®Ø²ÙŽÙ‘Ù† Ù…Ø¤Ù‚ØªÙ‹Ø§ Ù„ØªÙØ§Ø¯ÙŠ Ø¬ÙˆÙ„Ø© ØªØ­Ø¯ÙÙ‘
 * Ø¥Ø¶Ø§ÙÙŠØ© Ù…Ø¹ ÙƒÙ„ Ø·Ù„Ø¨.
 *
 * ## Ø´Ù‡Ø§Ø¯Ø© TLS
 * ØªÙØµØ¯ÙØ± Ø§Ù„Ø¨ÙˆØ§Ø¨Ø© Ø´Ù‡Ø§Ø¯Ø© Ù…ÙˆÙ‚Ù‘Ø¹Ø© Ø°Ø§ØªÙŠÙ‹Ø§ Ø¨Ø§Ø³Ù… Ù„Ø§ ÙŠØ·Ø§Ø¨Ù‚ Ø¹Ù†ÙˆØ§Ù† IP. Ø§Ù„Ù‚Ø¨ÙˆÙ„
 * Ù…Ø´Ø±ÙˆØ· Ø¨Ø£Ù…Ø±ÙŠÙ†: Ø£Ù† ÙŠÙƒÙˆÙ† Ø§Ù„Ø¹Ù†ÙˆØ§Ù† Ø®Ø§ØµÙ‹Ø§ (RFC 1918)ØŒ ÙˆØ£Ù† ÙŠÙƒÙˆÙ† Ø°Ù„Ùƒ Ø¹Ù„Ù‰
 * Ø´Ø¨ÙƒØ© Ø¥Ø¯Ø§Ø±Ø© Ù…Ø¹Ø²ÙˆÙ„Ø©. Ù„Ø°Ù„Ùƒ ÙŠØ±ÙØ¶ Ø§Ù„Ù…ØµÙ†Ø¹ Ø£ÙŠ Ø¹Ù†ÙˆØ§Ù† Ø¹Ø§Ù… Ø±ÙØ¶Ù‹Ø§ ØµØ±ÙŠØ­Ù‹Ø§ Ø¨Ø¯Ù„
 * Ø£Ù† ÙŠÙØªØ­ Ø«Ù‚Ø© Ø¹Ù…ÙŠØ§Ø¡ Ø¹Ù„Ù‰ Ø§Ù„Ø¥Ù†ØªØ±Ù†Øª.
 */
@Component
class DinstarConnectionFactory(
    @Value("\${red.dinstar.username:admin}") private val username: String,
    @Value("\${red.dinstar.password:admin}") private val password: String,
    @Value("\${red.dinstar.connect-timeout-seconds:5}") private val connectTimeout: Long,
    @Value("\${red.dinstar.read-timeout-seconds:10}") private val readTimeout: Long,
    @Value("\${red.dinstar.probe-timeout-seconds:2}") private val probeTimeout: Long,
    @Value("\${red.dinstar.cert-pins:}") private val certPinsConfig: String,
    private val mapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarConnectionFactory::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val clients = ConcurrentHashMap<String, DinstarClient>()

    fun clientFor(host: String, apiPort: Int, scheme: String): DinstarClient =
        clients.computeIfAbsent("$scheme://$host:$apiPort") {
            DinstarClient(host, apiPort, scheme, buildHttpClient(probe = false), mapper)
        }

    fun probeClientFor(host: String, apiPort: Int, scheme: String): DinstarClient =
        DinstarClient(host, apiPort, scheme, buildHttpClient(probe = true), mapper)

    /** ÙŠÙØ³ØªØ¯Ø¹Ù‰ Ø¹Ù†Ø¯ Ø­Ø°Ù Ø¨ÙˆØ§Ø¨Ø© Ø­ØªÙ‰ Ù„Ø§ ÙŠØªØ³Ø±Ø¨ Ø¹Ù…ÙŠÙ„ Ù…Ø¹Ù„Ù‘Ù‚. */
    fun evict(host: String, apiPort: Int, scheme: String) {
        clients.remove("$scheme://$host:$apiPort")
    }

    private fun buildHttpClient(probe: Boolean): OkHttpClient {
        val credentials = Credentials(username, password)
        val dispatching = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(credentials))
            .with("basic", BasicAuthenticator(credentials))
            .build()
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ssl = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }

        // Ø§Ù„ÙØ­Øµ ÙŠØ³ØªØ®Ø¯Ù… Ù…Ù‡Ù„Ø© Ø£Ù‚ØµØ±: Ø¹Ù†ÙˆØ§Ù† Ø¨Ù„Ø§ Ø¬Ù‡Ø§Ø² ÙŠØ¬Ø¨ Ø£Ù† ÙŠØ³Ù‚Ø· Ø¨Ø³Ø±Ø¹Ø©
        // ÙˆØ¥Ù„Ø§ Ø§Ø³ØªØºØ±Ù‚ Ù…Ø³Ø­ â€Ž/24 Ø¯Ù‚Ø§Ø¦Ù‚.
        val timeout = if (probe) probeTimeout else connectTimeout
        val builder = OkHttpClient.Builder()
            .authenticator(CachingAuthenticatorDecorator(dispatching, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }

        // SPKI pinning â€” Ù†ÙØ³ Ù…Ù†Ø·Ù‚ DinstarHardwareService: Ø§Ù„Ø«Ù‚Ø© Ø§Ù„Ù…Ø­Ù„ÙŠØ©
        // Ù„Ù„Ø´Ù‡Ø§Ø¯Ø§Øª Ø§Ù„Ø°Ø§ØªÙŠØ© Ù„Ø§ ØªÙ„ØºÙŠ ØªØ­Ù‚Ù‚ OkHttp Ù…Ù† Ø§Ù„Ø¯Ø¨ÙˆØ³ Ø¨Ø¹Ø¯ Ø¨Ù†Ø§Ø¡ Ø§Ù„Ø³Ù„Ø³Ù„Ø©.
        certPinsConfig.split(',')
            .map { it.trim() }
            .filter { it.startsWith("sha256/") }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { pins ->
                val pinner = CertificatePinner.Builder()
                pins.forEach { pinner.add("*", it) }
                builder.certificatePinner(pinner.build())
            }

        return builder
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(if (probe) probeTimeout else readTimeout, TimeUnit.SECONDS)
            .callTimeout(if (probe) probeTimeout * 2 else readTimeout + connectTimeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(!probe)
            .build()
    }

    /**
     * Ø¹Ù…ÙŠÙ„ Ù…ÙˆØ¬Ù‘Ù‡ Ø¥Ù„Ù‰ Ø¨ÙˆØ§Ø¨Ø© ÙˆØ§Ø­Ø¯Ø©. ÙƒÙ„ Ø§Ù„Ø§Ø³ØªØ¯Ø¹Ø§Ø¡Ø§Øª Ù‡Ù†Ø§ Ù…ÙˆØ«Ù‘Ù‚Ø© ÙÙŠ
     * Â«UC2000 HTTP APIÂ»Ø› Ø£ÙŠ Ø¹Ù…Ù„ÙŠØ© ØºÙŠØ± Ù…ÙˆØ«Ù‘Ù‚Ø© ØªÙØ±ÙØ¶ ÙÙŠ Ø§Ù„Ø·Ø¨Ù‚Ø© Ø§Ù„Ø£Ø¹Ù„Ù‰
     * Ø¨Ø¯Ù„ Ø§Ø®ØªØ±Ø§Ø¹ Ù…Ø³Ø§Ø±.
     */
    class DinstarClient(
        val host: String,
        val apiPort: Int,
        val scheme: String,
        private val http: OkHttpClient,
        private val mapper: ObjectMapper
    ) {
        private val base = run {
            require(scheme in setOf("http", "https")) { "DINSTAR scheme must be http or https" }
            require(isPrivate(host)) { "DINSTAR must be reached on a private management address" }
            "$scheme://$host:$apiPort".toHttpUrl()
        }

        val endpointLabel: String get() = "$scheme://$host:$apiPort"

        fun getJson(path: String, query: Map<String, String> = emptyMap()): Map<String, Any?> {
            val url = base.newBuilder().addPathSegments(path.removePrefix("/"))
            query.forEach(url::addQueryParameter)
            return execute(Request.Builder().url(url.build()).get().build())
        }

        fun postJson(path: String, value: Any): Map<String, Any?> {
            val body = mapper.writeValueAsBytes(value).toRequestBody(JSON)
            val url = base.newBuilder().addPathSegments(path.removePrefix("/")).build()
            return execute(Request.Builder().url(url).post(body).build())
        }

        /**
         * Ù‚Ø±Ø§Ø¡Ø© Ø­Ø§Ù„Ø© Ø§Ù„Ù…Ù†Ø§ÙØ°. `port` ØªÙÙ…Ø±ÙŽÙ‘Ø± ÙƒÙ‚Ø§Ø¦Ù…Ø© Ù…ÙØµÙˆÙ„Ø© Ø¨ÙÙˆØ§ØµÙ„ØŒ ÙˆØ¹Ø¯Ø¯
         * Ø§Ù„Ù…Ù†Ø§ÙØ° ÙŠÙØ´ØªÙ‚ Ù…Ù† Ø§Ù„Ø·Ø±Ø§Ø² Ø¨Ø¯Ù„ ØªØ«Ø¨ÙŠØªÙ‡ Ø¹Ù„Ù‰ 8.
         */
        @Suppress("UNCHECKED_CAST")
        fun getPortInfo(portCount: Int = 8): List<Map<String, Any?>> =
            queryPorts(portCount).ports

        /**
         * Ø§Ø³ØªØ¹Ù„Ø§Ù… Ø§Ù„Ù…Ù†Ø§ÙØ° Ù…Ø¹ Ø§Ù„Ø§Ø­ØªÙØ§Ø¸ Ø¨Ø§Ù„Ø±Ù‚Ù… Ø§Ù„ØªØ³Ù„Ø³Ù„ÙŠ.
         *
         * ØªÙˆØ«ÙŠÙ‚ `get_port_info` (Â§10.3) ÙŠÙ†Øµ Ø¹Ù„Ù‰ Ø£Ù† **ÙƒÙ„ Ø§Ø³ØªØ¬Ø§Ø¨Ø© ØªØ­Ù…Ù„
         * Ø­Ù‚Ù„ `sn`** = Ø§Ù„Ø±Ù‚Ù… Ø§Ù„ØªØ³Ù„Ø³Ù„ÙŠ Ù„Ù„Ø¨ÙˆØ§Ø¨Ø©. ÙƒØ§Ù† ÙŠÙÙ‡Ù…ÙŽÙ„ ÙˆÙŠÙÙ‚Ø±Ø£ Ø§Ù„ØªØ³Ù„Ø³Ù„ÙŠ
         * Ù…Ù† `get_status` ÙˆØ­Ø¯Ù‡ØŒ ÙˆÙ‡Ùˆ Ø£Ù…Ø± Ù„Ø§ ØªØ¯Ø¹Ù…Ù‡ Ø§Ù„Ø¥ØµØ¯Ø§Ø±Ø§Øª Ø§Ù„Ø£Ù‚Ø¯Ù… Ù…Ù†
         * 1102 â€” ÙØªÙÙ‚Ø¯ ØªÙ„Ùƒ Ø§Ù„Ø£Ø¬Ù‡Ø²Ø© Ù‡ÙˆÙŠØªÙ‡Ø§ Ø§Ù„Ø«Ø§Ø¨ØªØ© ÙˆØªÙØ¹Ø±ÙŽÙ‘Ù Ø¨Ø¹Ù†ÙˆØ§Ù†Ù‡Ø§
         * Ø§Ù„Ø´Ø¨ÙƒÙŠ Ø§Ù„Ø°ÙŠ ÙŠØªØ¨Ø¯Ù‘Ù„ Ù…Ø¹ DHCP.
         */
        fun queryPorts(portCount: Int = 8): PortQuery {
            val response = getJson(
                "/api/get_port_info",
                mapOf(
                    "port" to (0 until portCount).joinToString(","),
                    "info_type" to "type,imei,imsi,iccid,number,reg,slot,callstate,signal,gprs"
                )
            )
            require(isSuccess(response)) { "DINSTAR get_port_info failed on $endpointLabel" }
            @Suppress("UNCHECKED_CAST")
            val ports = response["info"] as? List<Map<String, Any?>> ?: emptyList()
            return PortQuery(
                ports = ports,
                serialNumber = response["sn"]?.toString()?.takeIf { it.isNotBlank() }
            )
        }

        fun getDeviceStatus(): Map<String, Any?> {
            val response = postJson(
                DinstarApiContract.Path.GET_STATUS,
                DinstarApiContract.Status.PERFORMANCE_BODY
            )
            val performance = DinstarApiContract.Status.performance(response)
            val serial = response[DinstarApiContract.PortInfo.SERIAL_KEY]?.toString()
            return if (serial.isNullOrBlank()) performance
            else performance + mapOf(DinstarApiContract.PortInfo.SERIAL_KEY to serial)
        }

        /** Ù†ØªÙŠØ¬Ø© Ø§Ø³ØªØ¹Ù„Ø§Ù… Ø§Ù„Ù…Ù†Ø§ÙØ° Ù…Ø¹ Ù‡ÙˆÙŠØ© Ø§Ù„Ø¬Ù‡Ø§Ø² Ø§Ù„Ù…Ø±Ø§ÙÙ‚Ø©. */
        data class PortQuery(
            val ports: List<Map<String, Any?>>,
            val serialNumber: String?
        )

        private fun execute(request: Request): Map<String, Any?> {
            val withAccept = request.newBuilder().header("Accept", "application/json").build()
            return http.newCall(withAccept).execute().use { response ->
                if (!response.isSuccessful) {
                    val challenge = response.challenges().joinToString(", ") { "${it.scheme} realm=${it.realm}" }
                    log.warn("DINSTAR HTTP {} on {}{} â€” challenge: {}",
                        response.code, endpointLabel, request.url.encodedPath, challenge)
                    throw IllegalStateException(
                        "DINSTAR HTTP ${response.code} on ${request.url.encodedPath} ($endpointLabel)"
                    )
                }
                val body = requireNotNull(response.body) { "DINSTAR returned an empty HTTP body" }
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(body.bytes(), Map::class.java) as Map<String, Any?>
            }
        }

        companion object {
            /** ÙˆØ§Ø¬Ù‡Ø© UC2000 ØªÙØ´ÙŠØ± Ø¥Ù„Ù‰ Ø§Ù„Ù†Ø¬Ø§Ø­ Ø¨Ù€ `error_code = 200`. */
            fun isSuccess(response: Map<String, Any?>): Boolean =
                (response["error_code"] as? Number)?.toInt() == 200

            private fun isPrivate(host: String): Boolean = runCatching {
                val a = InetAddress.getByName(host)
                a.isSiteLocalAddress || a.isLoopbackAddress
            }.getOrDefault(false)
        }
    }
}

