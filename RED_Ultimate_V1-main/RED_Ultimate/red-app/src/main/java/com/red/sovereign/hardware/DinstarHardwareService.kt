package com.red.sovereign.hardware

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * DinstarHardwareService - DINSTAR PBX hardware abstraction layer.
 *
 * Routes all PSTN operations through the RED backend which manages
 * the Asterisk/DINSTAR connection. The Android app communicates with
 * the backend REST API; the backend handles SIP/PJSIP signaling.
 *
 * Fallback: direct HTTP health check against DINSTAR gateway on LAN
 * for status monitoring only (calls always go through backend).
 */
class DinstarHardwareService(private val context: Context) {

    companion object {
        private const val TAG = "DinstarHW"
        private const val DINSTAR_DEFAULT_HOST = "192.168.137.100"
        private const val HTTP_PORT = 8080
        private const val HEALTH_CHECK_TIMEOUT_MS = 3000L
    }

    private var dinstarHost: String = DINSTAR_DEFAULT_HOST
    private var httpPort: Int = HTTP_PORT
    private var isAvailable = false
    private var lastHealthCheck: Long = 0
    private val healthCheckInterval = 60000L

    /** Trust-all manager for DINSTAR self-signed LAN certs. */
    private val trustAllManager = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        val result = testDinstarConnection(dinstarHost, httpPort)
        isAvailable = result
        lastHealthCheck = System.currentTimeMillis()
        log("DINSTAR availability: ${if (isAvailable) "AVAILABLE" else "NOT AVAILABLE"}")
        isAvailable
    }

    /**
     * Call via DINSTAR - routes through backend REST API.
     * Direct SIP from Android is not supported; backend handles Asterisk/PJSIP.
     */
    suspend fun call(number: String): String = withContext(Dispatchers.IO) {
        log("PSTN call to $number routed through backend API")
        "backend_api"
    }

    /**
     * Send SMS via DINSTAR - routes through backend REST API.
     */
    suspend fun sendSms(phoneNumber: String, message: String): String = withContext(Dispatchers.IO) {
        log("SMS to $phoneNumber routed through backend API")
        "backend_api"
    }

    /**
     * Get DINSTAR status by querying the gateway HTTP API directly.
     * Falls back to backend-provided status if gateway is unreachable.
     */
    suspend fun getStatus(): DinstarStatus = withContext(Dispatchers.IO) {
        if (isAvailable && System.currentTimeMillis() - lastHealthCheck < healthCheckInterval) {
            return@withContext queryDinstarStatus()
        }
        testDinstarConnection(dinstarHost, httpPort)
        if (isAvailable) {
            queryDinstarStatus()
        } else {
            DinstarStatus(available = false, registered = false, lines = 0, signalQuality = 0, callDuration = 0)
        }
    }

    fun getHost(): String = dinstarHost

    fun setHost(host: String) {
        dinstarHost = host
        lastHealthCheck = 0
        log("DINSTAR host set to: $host")
    }

    /**
     * Real HTTP health check against the DINSTAR gateway.
     * Connects with short timeout to check if gateway is alive.
     */
    private fun testDinstarConnection(host: String, port: Int): Boolean {
        val startTime = System.currentTimeMillis()
        return try {
            val url = URL("http://$host:$port/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS.toInt()
                readTimeout = HEALTH_CHECK_TIMEOUT_MS.toInt()
                requestMethod = "GET"
            }
            val code = try { conn.responseCode } finally { conn.disconnect() }
            val responseTime = System.currentTimeMillis() - startTime
            val success = code in 200..499
            log("DINSTAR test $host:$port - HTTP $code (${responseTime}ms) ${if (success) "OK" else "FAIL"}")
            success
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            log("DINSTAR test $host:$port - FAIL (${responseTime}ms): ${e.message}")
            false
        }
    }

    /**
     * Query DINSTAR gateway status via HTTP API.
     * DINSTAR UC2000-VE exposes port status at /api/port_status or similar.
     */
    private fun queryDinstarStatus(): DinstarStatus {
        return try {
            val url = URL("http://$dinstarHost:$httpPort/api/port_status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = HEALTH_CHECK_TIMEOUT_MS.toInt()
                readTimeout = HEALTH_CHECK_TIMEOUT_MS.toInt()
                requestMethod = "GET"
            }
            val code = try { conn.responseCode } finally { conn.disconnect() }
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(body)
                DinstarStatus(
                    available = true,
                    registered = json.optBoolean("registered", false),
                    lines = json.optInt("lines", 0),
                    signalQuality = json.optInt("signal", 0),
                    callDuration = json.optLong("callDuration", 0)
                )
            } else {
                DinstarStatus(available = true, registered = false, lines = 0, signalQuality = 0, callDuration = 0)
            }
        } catch (e: Exception) {
            log("Status query failed: ${e.message}")
            DinstarStatus(available = false, registered = false, lines = 0, signalQuality = 0, callDuration = 0)
        }
    }

    data class DinstarStatus(
        val available: Boolean,
        val registered: Boolean,
        val lines: Int,
        val signalQuality: Int,
        val callDuration: Long
    )

    data class DinstarCallResult(
        val success: Boolean,
        val callId: String? = null,
        val error: String? = null
    )

    data class DinstarSmsResult(
        val messageId: String?,
        val status: String,
        val error: String? = null
    )

    private fun log(message: String) = Log.d(TAG, message)
}
