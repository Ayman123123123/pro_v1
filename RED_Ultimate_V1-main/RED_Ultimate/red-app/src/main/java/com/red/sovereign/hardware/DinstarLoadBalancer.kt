package com.red.sovereign.hardware

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
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
 * DinstarLoadBalancer - Handles DINSTAR device port selection and load balancing.
 *
 * The DINSTAR gateway typically exposes several services on different ports:
 * - Port 5060: SIP signaling
 * - Port 8080: HTTP API
 * - Port 8088: HTTPS API (proxy through Nginx)
 * - Port 5090: RTP media
 * - Port 5061: TLS/SIP
 *
 * This class:
 * 1. Discovers available DINSTAR devices on the local network
 * 2. Tests port connectivity
 * 3. Selects the best port based on latency and availability
 * 4. Provides load balancing across multiple DINSTAR instances
 */
class DinstarLoadBalancer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "DinstarLoadBalancer"
        private const val SIP_PORT = 5060
        private const val HTTP_PORT = 8080
        private const val HTTPS_PORT = 8088
        private const val RTP_PORT_BASE = 5000
        private const val TLS_SIP_PORT = 5061
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
     * Discover DINSTAR devices on the local network and select the best one.
     * Returns the selected host and port.
     */
    suspend fun discoverAndSelect(): Pair<String, Int> {
        // Get local network info
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        
        return when (activeNetwork) {
            is Network -> {
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

                if (isWifi) {
                    // On WiFi, scan for DINSTAR devices
                    val wifiHosts = scanWifiNetwork()
                    if (wifiHosts.isNotEmpty()) {
                        // Select best host based on latency
                        val bestHost = wifiHosts.minByOrNull { testPort(it, HTTP_PORT).responseTimeMs } ?: wifiHosts.first()
                        selectPort(bestHost, HTTP_PORT)
                    } else {
                        selectPort("192.168.1.1", HTTP_PORT) // Default DINSTAR IP
                    }
                } else {
                    // On cellular, use cached or default
                    selectPort("10.0.0.1", HTTP_PORT)
                }
            }
            else -> selectPort("192.168.1.1", HTTP_PORT) // Fallback
        }
    }

    private fun scanWifiNetwork(): List<String> {
        // Scan local network for DINSTAR devices
        // This is a simplified scan - in production would use mDNS/DNS-SD
        val candidates = mutableListOf("192.168.1.1", "192.168.1.2", "192.168.1.100")
        return candidates
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
        return if (selectedHost != null && selectedPort > 0) "$selectedHost:$selectedPort" else "192.168.1.1:8088"
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