package com.red.sovereign.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * اكتشاف خادم يونس على الشبكة المحلية.
 *
 * العطل السابق: `SecureOkHttpClient.build(connectTimeout = 800)` يفسر الرقم **ثواني**
 * لا ملي ثانية، ثم يُمسح النطاق /24 كاملًا (254 عنوانًا) دفعتين دفعتين مع `awaitAll`.
 * النتيجة: شاشة «جارٍ الاتصال بالسيرفر» لعشرات الثواني أو دقائق.
 *
 * المسار السريع يجرب العناوين المعروفة ومنفذ الإنتاج 8088 خلال أقل من ثانيتين
 * ويعود فور أول نجاح. لا يُقبل خادم Node على 8080.
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

    /** تحقق صريح من عنوان يُدخله المستخدم يدوياً â€” يعيد العنوان المقبول أو null. */
    suspend fun verifyExplicit(base: String): String? = withContext(Dispatchers.IO) {
        val normalized = base.trim().ifBlank { return@withContext null }
        val withScheme = if (normalized.contains("://")) normalized else "http://$normalized"
        verify(withScheme)
    }

    suspend fun discover(mode: Mode = Mode.FAST): ApiResult<String> = withContext(Dispatchers.IO) {
        // تحقق من WiFi أولاً
        if (!isWifiConnected()) {
            return@withContext ApiResult.Error(null, "WIFI_NOT_CONNECTED")
        }
        
        val known = knownBases()
        // كان الشرط يمرر FAST_BUDGET_MS في الحالتين â€” اكتشاف سريع ثم شامل لاحقاً.
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
            // 503 مع جسم يونس يعني أن العملية حيّة والتبعيات لم تكتمل بعد.
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
        // المرشّحات المدمجة في البناء (واي فاي + إيثرنت + loopback) تغطي كلا الواجهتين
        // فوراً دون مسح — يضبطها local-first-run.* من عناوين الجهاز الفعلية.
        val candidates = BuildConfig.RED_SERVER_CANDIDATES
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val seeds = buildList {
            add(ServerEndpoint.url())
            addAll(candidates)
            // 10.0.2.2 هو alias لمضيف المحاكي (Android Emulator) â€” عنوان تطوير قياسي وليس IP LAN حقيقي.
            add("http://10.0.2.2:8088")
        }
        seeds.forEach { seed ->
            if (seed.isBlank()) return@forEach
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
                // مسح الـ /24 كاملاً (يضمن إيجاد الخادم عند أي أوكتيه، مثل .244)،
                // مع تقديم الأوكتيهات الشائعة للخوادم أولاً لتقليل زمن الإيجاد.
                val octets = linkedSetOf<Int>()
                listOf(self, 1, 2, 50, 100, 101, 128, 200, 244, 254).forEach { if (it in 1..254) octets += it }
                for (n in 1..254) octets += n
                octets.forEach { last ->
                    ports.forEach { port -> bases += YounesServerSignature.baseUrl("$prefix.$last", port) }
                }
            }
        }

        // مسح شبكة الخادم المعروفة (من BuildConfig) حتى لو اختلفت عن شبكة الهاتف â€” مفيد عند وجود توجيه بين الشبكتين.
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
        const val THOROUGH_BUDGET_MS = 6_000L
        const val MDNS_BUDGET_MS = 1_200L
        const val MAX_PARALLEL = 16
    }

    /** يتحقق من اتصال WiFi قبل البدء في المسح */
    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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
