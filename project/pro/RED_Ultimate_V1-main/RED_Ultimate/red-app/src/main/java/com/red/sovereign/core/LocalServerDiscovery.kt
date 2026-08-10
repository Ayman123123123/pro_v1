package com.red.sovereign.core

import android.content.Context
import com.red.sovereign.BuildConfig
import com.red.sovereign.auth.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Debug/LAN discovery without cloud dependencies. Candidates must prove both RED health and the
 * expected local identity-authority algorithm before the endpoint is persisted.
 */
class LocalServerDiscovery(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(450, TimeUnit.MILLISECONDS)
        .readTimeout(650, TimeUnit.MILLISECONDS)
        .callTimeout(1200, TimeUnit.MILLISECONDS)
        .build()

    suspend fun discover(port: Int = preferredPort()): ApiResult<String> {
        if (!BuildConfig.DEBUG) return ApiResult.Error(null, "LAN_DISCOVERY_DISABLED_IN_RELEASE")
        val candidates = candidateHosts().toList()
        for (batch in candidates.chunked(24)) {
            val found = coroutineScope {
                batch.map { host -> async(Dispatchers.IO) { verify(host, port) } }.awaitAll().firstOrNull { it != null }
            }
            if (found != null) {
                ServerEndpoint.update(context, found)
                return ApiResult.Success(200, found)
            }
        }
        return ApiResult.Error(null, "RED_SERVER_NOT_FOUND_ON_LAN")
    }

    private fun verify(host: String, port: Int): String? = runCatching {
        val base = "http://$host:$port"
        val health = client.newCall(Request.Builder().url("$base/health").get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
        if (!health.contains("\"status\":\"UP\"") && !health.contains("\"status\": \"UP\"")) return null
        if (!health.contains("1.0.0-YOUNES") && !health.contains("1.0.0-RED")) return null
        val authority = client.newCall(Request.Builder().url("$base/api/identity/authority").get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
        if (!authority.contains("ECDSA_P256_SHA256") || !authority.contains("\"v1\"")) return null
        base
    }.getOrNull()

    private fun candidateHosts(): LinkedHashSet<String> {
        val result = linkedSetOf<String>()
        runCatching { URI(ServerEndpoint.url()).host }.getOrNull()?.let(result::add)
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        interfaces.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }.forEach { network ->
            network.interfaceAddresses.forEach { binding ->
                val address = binding.address as? Inet4Address ?: return@forEach
                if (!address.isSiteLocalAddress) return@forEach
                val bytes = address.address.map { it.toInt() and 0xff }
                // Home/office RED deployments use one /24 broadcast domain. Limit discovery to 254
                // deterministic candidates rather than scanning arbitrary networks.
                for (last in 1..254) result += "${bytes[0]}.${bytes[1]}.${bytes[2]}.$last"
            }
        }
        result.remove("127.0.0.1")
        return result
    }

    private fun preferredPort(): Int = runCatching { URI(ServerEndpoint.url()).port.takeIf { it > 0 } ?: 8088 }.getOrDefault(8088)
}
