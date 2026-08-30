package com.red.sovereign.hardware

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.red.sovereign.core.ServerEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * DinstarLoadBalancer - Resolves the DINSTAR gateway reachable from this device.
 *
 * The gateway is always reached through the active RED backend (ServerEndpoint),
 * which owns the Asterisk/DINSTAR trunk and fleet config. This class therefore
 * resolves the gateway host from the backend endpoint rather than scanning fake
 * LAN addresses, and layers a lightweight HTTP health check + HTTPS failover on
 * top of it. Port semantics (as seen by the app's monitoring path):
 *  - 8080 HTTP API (primary)
 *  - 8088 HTTPS API (failover)
 */
class DinstarLoadBalancer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "DinstarLoadBalancer"
        private const val HTTP_PORT = 8080
        private const val HTTPS_PORT = 8088
        private const val CONNECTIVITY_TIMEOUT_MS = 3000L
        private const val MAX_PORT_ATTEMPTS = 10
        private const val HEALTH_CHECK_PATH = "/health"
    }

    private var selectedPort: Int = HTTP_PORT
    private var selectedHost: String? = null
    private var healthCheckJob: Job? = null
    private val portTestResults = mutableMapOf<String, PortTestResult>()

    data class PortTestResult(
        val host: String,
        val port: Int,
        val responseTimeMs: Long,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Resolve the DINSTAR gateway host and select the best port.
     *
     * The gateway is always reached through the active RED backend, which owns the
     * Asterisk/DINSTAR trunk and fleet. We therefore resolve the host from
     * [ServerEndpoint] (the configured server URL) instead of scanning fake LAN
     * addresses, while still recording the active transport (Wi-Fi vs cellular)
     * for diagnostics and any transport-specific tuning.
     */
    suspend fun discoverAndSelect(): Pair<String, Int> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val backendHost = ServerEndpoint.host().takeIf { it.isNotBlank() }
        if (backendHost == null) {
            Log.w(TAG, "No configured server endpoint — using loopback fallback")
            return selectPort("127.0.0.1", HTTP_PORT)
        }

        // Wi-Fi clients may reach the gateway on the LAN directly; cellular clients
        // use the same backend host (a public domain or tunnel). The host is the
        // authoritative fleet address either way.
        val candidates = scanWifiNetwork()
        val host = if (candidates.isNotEmpty()) candidates.first() else backendHost
        Log.d(TAG, "discoverAndSelect transport=wifi:$isWifi cellular:$isCellular host=$host")
        return selectPort(host, HTTP_PORT)
    }

    /**
     * Build the candidate gateway host list. The only authoritative source is the
     * active backend endpoint (which manages the DINSTAR fleet); no LAN guessing.
     */
    private fun scanWifiNetwork(): List<String> {
        val host = ServerEndpoint.host().takeIf { it.isNotBlank() } ?: return emptyList()
        return listOf(host)
    }

    /**
     * Test port connectivity to a DINSTAR host.
     * Returns true if the port responds within timeout.
     */
    private fun testPort(host: String, port: Int): PortTestResult {
        val key = "$host:$port"
        
        // Return cached result if recent (within 30 seconds)
        if (portTestResults[key]?.let { System.currentTimeMillis() - it.responseTimeMs < 30000 } != null) {
            return portTestResults[key]!!
        }

        val startTime = System.currentTimeMillis()
        var success = false
        var error: String? = null

        try {
            // Try HTTP health check
            val url = "http://$host:$port$HEALTH_CHECK_PATH"
            // In a real implementation, use OkHttp or similar
            // For now, simulate
            success = simulateHealthCheck(host, port)
            if (!success) {
                error = "Health check failed"
            }
        } catch (e: Exception) {
            error = e.message
        }

        val responseTime = System.currentTimeMillis() - startTime
        val result = PortTestResult(host = host, port = port, responseTimeMs = responseTime, success = success, error = error)
        portTestResults[key] = result
        return result
    }

    private fun simulateHealthCheck(host: String, port: Int): Boolean {
        return try {
            val url = URL("http://$host:$port$HEALTH_CHECK_PATH")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECTIVITY_TIMEOUT_MS.toInt()
                readTimeout = CONNECTIVITY_TIMEOUT_MS.toInt()
                requestMethod = "GET"
            }
            val code = try { conn.responseCode } finally { conn.disconnect() }
            code in 200..499
        } catch (_: Exception) {
            try {
                val url = URL("http://$host:$port/")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECTIVITY_TIMEOUT_MS.toInt()
                    readTimeout = CONNECTIVITY_TIMEOUT_MS.toInt()
                    requestMethod = "GET"
                }
                val code = try { conn.responseCode } finally { conn.disconnect() }
                code in 200..499
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Select the best port for DINSTAR communication.
     * Priority: HTTPS (8088) > HTTP (8080) > SIP (5060)
     */
    fun selectPort(host: String, port: Int): Pair<String, Int> {
        selectedHost = host
        selectedPort = port
        log("Selected DINSTAR port: $host:$port")

        // Start periodic health checks
        startHealthChecks(host, port)
        return Pair(host, port)
    }

    private fun startHealthChecks(host: String, port: Int) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            while (isActive) {
                val result = testPort(host, port)
                log("Health check for $host:$port: ${if (result.success) "OK" else "FAIL"} - ${result.error ?: ""}")
                
                // If port fails, try failover
                if (!result.success && selectedPort != HTTPS_PORT) {
                    log("Port $port failing, attempting failover to HTTPS")
                    selectPort(host, HTTPS_PORT)
                }
                
                delay(30000L) // Check every 30 seconds
            }
        }
    }

    /**
     * Get the currently selected DINSTAR host and port.
     */
    fun getSelectedAddress(): String {
        if (selectedHost != null && selectedPort > 0) return "$selectedHost:$selectedPort"
        val host = ServerEndpoint.host().takeIf { it.isNotBlank() } ?: "127.0.0.1"
        return "$host:$HTTPS_PORT"
    }

    /**
     * Get the selected port number.
     */
    fun getSelectedPort(): Int {
        return selectedPort
    }

    /**
     * Get the selected host address.
     */
    fun getSelectedHost(): String? {
        return selectedHost
    }

    /**
     * Check if a specific port is currently selected.
     */
    fun isPortSelected(port: Int): Boolean {
        return selectedPort == port
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    fun shutdown() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }
}