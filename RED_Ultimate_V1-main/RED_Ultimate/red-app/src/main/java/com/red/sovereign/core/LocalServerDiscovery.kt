package com.red.sovereign.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.red.sovereign.BuildConfig
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 🧠 YOUNES Smart Server & IP Auto-Discovery Engine.
 *
 * يكتشف مكان الخادم آلياً وذكياً حتى لو تغير عنوان IP أو شبكة الخادم (LAN / WAN / Wi-Fi / Dynamic IP):
 * 1. فحص كفاءة آخر عنوان معروف (Stored Last-Known Endpoint)
 * 2. فحص النطاق الافتراضي المكون (Configured Domain / URL)
 * 3. الفحص الشبكي الآلي الموازي للنطاق المحلي (/24 Subnet Parallel Scanner)
 * 4. خدمة الاكتشاف الآلي عبر mDNS / Android NsdManager (_younes._tcp.)
 * 5. فحص الخادم المباشر عبر الهوية والمعيار (/health + /api/identity/authority)
 */
class LocalServerDiscovery(private val context: Context) {
    private val client: OkHttpClient = SecureOkHttpClient.build(
        context = context,
        connectTimeout = 800,
        readTimeout = 1000,
        writeTimeout = 1000
    )

    suspend fun discover(port: Int = preferredPort()): ApiResult<String> = withContext(Dispatchers.IO) {
        val candidates = candidateHosts().toList()
        
        // 1) فحص الدفعة الأولى من العناوين المرشحة بمتوازيات فائقة السرعة
        for (batch in candidates.chunked(32)) {
            val found = coroutineScope {
                batch.map { host -> async(Dispatchers.IO) { verify(host, port) } }.awaitAll().firstOrNull { it != null }
            }
            if (found != null) {
                ServerEndpoint.update(context, found)
                return@withContext ApiResult.Success(200, found)
            }
        }

        // 2) إذا لم يُعثر عليه في المسح السريع، يتم تشغيل ميزة mDNS / NSD Discovery على شبكة Wi-Fi المحلية
        val nsdEndpoint = discoverMdnsNsd(port)
        if (nsdEndpoint != null) {
            ServerEndpoint.update(context, nsdEndpoint)
            return@withContext ApiResult.Success(200, nsdEndpoint)
        }

        ApiResult.Error(null, "YOUNES_SERVER_NOT_FOUND")
    }

    private fun verify(host: String, port: Int): String? = runCatching {
        val scheme = if (port == 443 || port == 8443) "https" else "http"
        val base = if (host.startsWith("http://") || host.startsWith("https://")) host.trimEnd('/') else "$scheme://$host:$port"
        val health = client.newCall(Request.Builder().url("$base/health").get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }
        if (!health.contains("\"status\":\"UP\"") && !health.contains("\"status\": \"UP\"")) return null
        if (!health.contains("1.0.0-YOUNES") && !health.contains("1.0.0-RED")) return null

        val authority = client.newCall(Request.Builder().url("$base/api/identity/authority").get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }
        if (!authority.contains("ECDSA_P256_SHA256") || !authority.contains("\"v1\"")) return null
        base
    }.getOrNull()

    private fun discoverMdnsNsd(port: Int): String? {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val latch = CountDownLatch(1)
        var discoveredHost: String? = null

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) { latch.countDown() }
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo?.serviceType?.contains("younes") == true || serviceInfo?.serviceType?.contains("red") == true) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) { latch.countDown() }
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                            val host = resolvedInfo?.host?.hostAddress
                            if (!host.isNullOrBlank()) {
                                discoveredHost = verify(host, resolvedInfo.port)
                            }
                            latch.countDown()
                        }
                    })
                }
            }
        }

        try {
            nsdManager.discoverServices("_younes._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(2, TimeUnit.SECONDS)
            nsdManager.stopDiscovery(listener)
        } catch (_: Exception) {
            runCatching { nsdManager.stopDiscovery(listener) }
        }

        return discoveredHost
    }

    private fun candidateHosts(): LinkedHashSet<String> {
        val result = linkedSetOf<String>()
        // إضافة آخر عنوان ناجح معروف
        runCatching { URI(ServerEndpoint.url()).host }.getOrNull()?.let(result::add)
        result.add(BuildConfig.RED_SERVER_URL)
        result.add("10.0.2.2") // Android Emulator Host Loopback
        result.add("192.168.1.1")
        result.add("192.168.0.1")

        // مسح عناوين النطاق المحلي /24 لكل واجهات الشبكة
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        interfaces.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }.forEach { network ->
            network.interfaceAddresses.forEach { binding ->
                val address = binding.address as? Inet4Address ?: return@forEach
                if (!address.isSiteLocalAddress) return@forEach
                val bytes = address.address.map { it.toInt() and 0xff }
                for (last in 1..254) result += "${bytes[0]}.${bytes[1]}.${bytes[2]}.$last"
            }
        }
        result.remove("127.0.0.1")
        return result
    }

    private fun preferredPort(): Int = runCatching { URI(ServerEndpoint.url()).port.takeIf { it > 0 } ?: 8088 }.getOrDefault(8088)
}
