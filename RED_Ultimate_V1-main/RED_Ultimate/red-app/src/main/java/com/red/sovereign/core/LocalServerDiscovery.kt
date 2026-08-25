package com.red.sovereign.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.red.sovereign.BuildConfig
import com.red.sovereign.auth.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ø§ÙƒØªØ´Ø§Ù Ø®Ø§Ø¯Ù… ÙŠÙˆÙ†Ø³ Ø¹Ù„Ù‰ Ø§Ù„Ø´Ø¨ÙƒØ© Ø§Ù„Ù…Ø­Ù„ÙŠØ©.
 *
 * Ø§Ù„Ø¹Ø·Ù„ Ø§Ù„Ø³Ø§Ø¨Ù‚: `SecureOkHttpClient.build(connectTimeout = 800)` ÙŠÙØ³Ø± Ø§Ù„Ø±Ù‚Ù… **Ø«ÙˆØ§Ù†ÙŠ**
 * Ù„Ø§ Ù…Ù„ÙŠ Ø«Ø§Ù†ÙŠØ©ØŒ Ø«Ù… ÙŠÙÙ…Ø³Ø­ Ø§Ù„Ù†Ø·Ø§Ù‚ /24 ÙƒØ§Ù…Ù„Ù‹Ø§ (254 Ø¹Ù†ÙˆØ§Ù†Ù‹Ø§) Ø¯ÙØ¹ØªÙŠÙ† Ø¯ÙØ¹ØªÙŠÙ† Ù…Ø¹ `awaitAll`.
 * Ø§Ù„Ù†ØªÙŠØ¬Ø©: Ø´Ø§Ø´Ø© Â«Ø¬Ø§Ø±Ù Ø§Ù„Ø§ØªØµØ§Ù„ Ø¨Ø§Ù„Ø³ÙŠØ±ÙØ±Â» Ù„Ø¹Ø´Ø±Ø§Øª Ø§Ù„Ø«ÙˆØ§Ù†ÙŠ Ø£Ùˆ Ø¯Ù‚Ø§Ø¦Ù‚.
 *
 * Ø§Ù„Ù…Ø³Ø§Ø± Ø§Ù„Ø³Ø±ÙŠØ¹ ÙŠØ¬Ø±Ø¨ Ø§Ù„Ø¹Ù†Ø§ÙˆÙŠÙ† Ø§Ù„Ù…Ø¹Ø±ÙˆÙØ© ÙˆÙ…Ù†ÙØ° Ø§Ù„Ø¥Ù†ØªØ§Ø¬ 8088 Ø®Ù„Ø§Ù„ Ø£Ù‚Ù„ Ù…Ù† Ø«Ø§Ù†ÙŠØªÙŠÙ†
 * ÙˆÙŠØ¹ÙˆØ¯ ÙÙˆØ± Ø£ÙˆÙ„ Ù†Ø¬Ø§Ø­. Ù„Ø§ ÙŠÙÙ‚Ø¨Ù„ Ø®Ø§Ø¯Ù… Node Ø¹Ù„Ù‰ 8080.
 */
class LocalServerDiscovery(private val context: Context) {
    enum class Mode { FAST, THOROUGH }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(WRITE_MS, TimeUnit.MILLISECONDS)
        .callTimeout(CALL_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** ØªØ­Ù‚Ù‚ ØµØ±ÙŠØ­ Ù…Ù† Ø¹Ù†ÙˆØ§Ù† ÙŠÙØ¯Ø®Ù„Ù‡ Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… ÙŠØ¯ÙˆÙŠØ§Ù‹ â€” ÙŠØ¹ÙŠØ¯ Ø§Ù„Ø¹Ù†ÙˆØ§Ù† Ø§Ù„Ù…Ù‚Ø¨ÙˆÙ„ Ø£Ùˆ null. */
    suspend fun verifyExplicit(base: String): String? = withContext(Dispatchers.IO) {
        val normalized = base.trim().ifBlank { return@withContext null }
        val withScheme = if (normalized.contains("://")) normalized else "http://$normalized"
        verify(withScheme)
    }

    suspend fun discover(mode: Mode = Mode.FAST): ApiResult<String> = withContext(Dispatchers.IO) {
        val known = knownBases()
        // ÙƒØ§Ù† Ø§Ù„Ø´Ø±Ø· ÙŠÙ…Ø±Ø± FAST_BUDGET_MS ÙÙŠ Ø§Ù„Ø­Ø§Ù„ØªÙŠÙ† â€” Ø§ÙƒØªØ´Ø§Ù Ø³Ø±ÙŠØ¹ Ø«Ù… Ø´Ø§Ù…Ù„ Ù„Ø§Ø­Ù‚Ø§Ù‹.
        firstVerified(known, FAST_BUDGET_MS)?.let { found ->
            ServerEndpoint.update(context, found)
            return@withContext ApiResult.Success(200, found)
        }

        if (mode == Mode.FAST) {
            return@withContext ApiResult.Error(null, "YOUNES_SERVER_NOT_FOUND")
        }

        firstVerified(lanBases(), THOROUGH_BUDGET_MS)?.let { found ->
            ServerEndpoint.update(context, found)
            return@withContext ApiResult.Success(200, found)
        }

        val nsd = withTimeoutOrNull(MDNS_BUDGET_MS) { discoverMdnsNsd() }
        if (nsd != null) {
            ServerEndpoint.update(context, nsd)
            return@withContext ApiResult.Success(200, nsd)
        }

        ApiResult.Error(null, "YOUNES_SERVER_NOT_FOUND")
    }

    private fun verify(base: String): String? = runCatching {
        val normalized = base.trimEnd('/')
        val healthCall = client.newCall(Request.Builder().url("$normalized/health").get().build())
        val health = healthCall.execute().use { response ->
            // 503 Ù…Ø¹ Ø¬Ø³Ù… ÙŠÙˆÙ†Ø³ ÙŠØ¹Ù†ÙŠ Ø£Ù† Ø§Ù„Ø¹Ù…Ù„ÙŠØ© Ø­ÙŠÙ‘Ø© ÙˆØ§Ù„ØªØ¨Ø¹ÙŠØ§Øª Ù„Ù… ØªÙƒØªÙ…Ù„ Ø¨Ø¹Ø¯.
            if (response.code !in 200..599) return null
            response.body?.string().orEmpty()
        }
        if (!YounesServerSignature.isReadyHealth(health)) return null

        val authorityCall = client.newCall(Request.Builder().url("$normalized/api/identity/authority").get().build())
        val authority = authorityCall.execute().use { response ->
            if (!response.isSuccessful) return@use ""
            response.body?.string().orEmpty()
        }
        if (!YounesServerSignature.isYounesServer(health, authority)) return null
        normalized
    }.getOrNull()

    private suspend fun firstVerified(targets: Collection<String>, budgetMs: Long): String? {
        val unique = targets.map { it.trimEnd('/') }.filter { it.startsWith("http") }.distinct()
        if (unique.isEmpty()) return null
        return supervisorScope {
            val done = CompletableDeferred<String>()
            val gate = Semaphore(MAX_PARALLEL)
            val jobs = unique.map { target ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        if (done.isCompleted) return@async
                        val hit = verify(target) ?: return@async
                        done.complete(hit)
                    }
                }
            }
            val winner = withTimeoutOrNull(budgetMs) { done.await() }
            jobs.forEach { it.cancel() }
            winner
        }
    }

    private fun knownBases(): LinkedHashSet<String> {
        val bases = linkedSetOf<String>()
        val preferred = preferredPort()
        val seeds = listOf(
            ServerEndpoint.url(),
            BuildConfig.RED_SERVER_URL,
            // 10.0.2.2 Ù‡Ùˆ alias Ù„Ù…Ø¶ÙŠÙ Ø§Ù„Ù…Ø­Ø§ÙƒÙŠ (Android Emulator) â€” Ø¹Ù†ÙˆØ§Ù† ØªØ·ÙˆÙŠØ± Ù‚ÙŠØ§Ø³ÙŠ ÙˆÙ„ÙŠØ³ IP LAN Ø­Ù‚ÙŠÙ‚ÙŠ.
            "http://10.0.2.2:8088",
        )
        seeds.forEach { seed ->
            val host = YounesServerSignature.hostOf(seed) ?: return@forEach
            YounesServerSignature.ports(YounesServerSignature.portOf(seed, preferred)).forEach { port ->
                bases += YounesServerSignature.baseUrl(host, port)
            }
        }
        return bases
    }

    private fun lanBases(): LinkedHashSet<String> {
        val bases = linkedSetOf<String>()
        val ports = YounesServerSignature.ports(preferredPort())
        val lastKnown = YounesServerSignature.hostOf(ServerEndpoint.url())
        val lastOctet = lastKnown?.substringAfterLast('.')?.toIntOrNull()

        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        interfaces.filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }.forEach { network ->
            network.interfaceAddresses.forEach { binding ->
                val address = binding.address as? Inet4Address ?: return@forEach
                if (!address.isSiteLocalAddress) return@forEach
                val bytes = address.address.map { it.toInt() and 0xff }
                val prefix = "${bytes[0]}.${bytes[1]}.${bytes[2]}"
                val self = bytes[3]
                val octets = linkedSetOf(1, 2, 50, 100, 101, 254, self)
                lastOctet?.let(octets::add)
                for (delta in -3..3) {
                    val value = self + delta
                    if (value in 1..254) octets += value
                }
                octets.forEach { last ->
                    ports.forEach { port -> bases += YounesServerSignature.baseUrl("$prefix.$last", port) }
                }
            }
        }

        // Ù…Ø³Ø­ Ø´Ø¨ÙƒØ© Ø§Ù„Ø®Ø§Ø¯Ù… Ø§Ù„Ù…Ø¹Ø±ÙˆÙØ© (Ù…Ù† BuildConfig) Ø­ØªÙ‰ Ù„Ùˆ Ø§Ø®ØªÙ„ÙØª Ø¹Ù† Ø´Ø¨ÙƒØ© Ø§Ù„Ù‡Ø§ØªÙ â€” Ù…ÙÙŠØ¯ Ø¹Ù†Ø¯ ÙˆØ¬ÙˆØ¯ ØªÙˆØ¬ÙŠÙ‡ Ø¨ÙŠÙ† Ø§Ù„Ø´Ø¨ÙƒØªÙŠÙ†.
        val knownHost = YounesServerSignature.hostOf(BuildConfig.RED_SERVER_URL)
        if (knownHost != null && knownHost != lastKnown) {
            val parts = knownHost.split('.').map { it.toIntOrNull() }
            if (parts.size == 4 && parts.take(3).none { it == null } && parts[3] != null) {
                val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                val hostOctet = parts[3] ?: return bases
                val octets = linkedSetOf(hostOctet, 1, 2, 50, 100, 101, 254)
                for (delta in -3..3) {
                    val value = hostOctet + delta
                    if (value in 1..254) octets += value
                }
                octets.forEach { last ->
                    ports.forEach { port -> bases += YounesServerSignature.baseUrl("$prefix.$last", port) }
                }
            }
        }
        return bases
    }

    private fun discoverMdnsNsd(): String? {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val latch = CountDownLatch(1)
        var discoveredHost: String? = null
        val preferred = preferredPort()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) { latch.countDown() }
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val type = serviceInfo?.serviceType.orEmpty()
                if (!type.contains("younes") && !type.contains("red")) return
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) { latch.countDown() }
                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                        val host = resolvedInfo?.host?.hostAddress
                        val port = resolvedInfo?.port?.takeIf { it > 0 } ?: preferred
                        if (!host.isNullOrBlank()) {
                            discoveredHost = verify(YounesServerSignature.baseUrl(host, port))
                        }
                        latch.countDown()
                    }
                })
            }
        }

        try {
            nsdManager.discoverServices("_younes._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(MDNS_BUDGET_MS, TimeUnit.MILLISECONDS)
nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        return discoveredHost
    }

    private fun preferredPort(): Int = YounesServerSignature.portOf(ServerEndpoint.url(), 8080)

    private companion object {
        const val CONNECT_MS = 250L
        const val READ_MS = 600L
        const val WRITE_MS = 400L
        const val CALL_MS = 800L
        const val FAST_BUDGET_MS = 1_800L
        const val THOROUGH_BUDGET_MS = 3_200L
        const val MDNS_BUDGET_MS = 1_200L
        const val MAX_PARALLEL = 16
    }
    /** يتحقق من إدخال المستخدم (host | host:port | رابط كامل) ويعيد الرابط الموثوق. */
    fun verifyUserInput(input: String): ApiResult<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ApiResult.Error(code = null, message = "أدخل عنوان الخادم")
        val host = com.red.sovereign.core.YounesServerSignature.hostOf(trimmed)
            ?: return ApiResult.Error(code = null, message = "عنوان غير صالح")
        val preferred = com.red.sovereign.core.YounesServerSignature.portOf(trimmed)
        val ports = linkedSetOf(preferred,
            com.red.sovereign.core.YounesServerSignature.DEFAULT_PORT,
            com.red.sovereign.core.YounesServerSignature.DEFAULT_HTTPS_PORT)
        for (pt in ports) {
            val base = com.red.sovereign.core.YounesServerSignature.buildUrl(host, pt)
            verify(base)?.let { return ApiResult.Success(code = 200, value = it) }
        }
        return ApiResult.Error(code = null, message = "لا يوجد خادم RED صالح على $host")
    }

    /** فحص سريع للمرشح المعروف: العنوان الحالي ثم بوابة LAN الافتراضية. */
    fun quickVerifyKnown(): String? {
        ServerEndpoint.url().takeIf { it.isNotBlank() }?.let { cur ->
            verify(cur)?.let { return it }
        }
        val ip = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { nif -> nif.inetAddresses.asSequence() }
                .filterIsInstance<java.net.Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull() ?: return null
        val base = com.red.sovereign.core.YounesServerSignature.buildUrl(ip, 8088)
        return verify(base)
    }
}
