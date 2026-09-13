package com.red.sovereign.features.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * P2-LAN — نظير مكتشف على نفس الشبكة اللاسلكية عبر NSD (mDNS).
 *
 * @param verified true إن كان redId ضمن جهات الاتصال (يضبطها LanCallManager لاحقا).
 */
data class LanPeer(
    val redId: String,
    val name: String,
    val host: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val verified: Boolean = false
)

/**
 * حضور LAN: نشر خدمتنا + اكتشاف الأقران.
 *
 * - النوع: `_redpeer._tcp.` — منفصل عن `_younes._tcp.` الخاص باكتشاف الخادم.
 * - سجل TXT: redId + name (مرمزة) + ver.
 * - يستخدم MulticastLock (NSD يفشل بصمت بدونه على أجهزة كثيرة).
 * - يجب الاستدعاء من الخيط الرئيسي (NSD يستخدم Looper الحالي).
 */
class LanPresence(
    private val context: Context,
    private val myRedId: String,
    private val myName: String,
    private val signalPort: Int
) {
    companion object {
        const val SERVICE_TYPE = "_redpeer._tcp."
        const val TXT_VER = "1"
        /** انتهاء النظير بعد 45s بلا إعلان. */
        const val PEER_TTL_MS = 45_000L
        private const val TAG = "LanPresence"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nsd: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    private var registeredName: String? = null
    private var discovering = false
    private var sweeper: Job? = null
    private val peers = LinkedHashMap<String, LanPeer>()

    private val _peersFlow = MutableStateFlow<List<LanPeer>>(emptyList())
    val peersFlow: StateFlow<List<LanPeer>> = _peersFlow.asStateFlow()

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registeredName = info.serviceName
        }

        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "register failed code=$errorCode")
        }

        override fun onServiceUnregistered(info: NsdServiceInfo) {
            registeredName = null
        }

        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "unregister failed code=$errorCode")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            discovering = true
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType != SERVICE_TYPE) return
            if (service.serviceName == registeredName) return // أنفسنا
            runCatching { nsd?.resolveService(service, resolveListener) }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            // الإزالة الحقيقية عبر الـ sweeper (قد يكون فقدانا عابرا)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            discovering = false
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "discovery start failed code=$errorCode")
            discovering = false
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.w(TAG, "discovery stop failed code=$errorCode")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "resolve failed ${serviceInfo.serviceName} code=$errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val attrs = serviceInfo.attributes
            val redId = attrs["redId"]?.toString(StandardCharsets.UTF_8)?.trim()?.uppercase().orEmpty()
            if (redId.isBlank() || redId == myRedId.trim().uppercase()) return
            if (attrs["ver"]?.toString(StandardCharsets.UTF_8) != TXT_VER) return
            val rawName = attrs["name"]?.toString(StandardCharsets.UTF_8).orEmpty()
            val name = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
                .take(32).ifBlank { redId.take(8) }
            val host = lanHostOf(serviceInfo) ?: return
            val peer = LanPeer(redId = redId, name = name, host = host, port = serviceInfo.port)
            synchronized(peers) { peers[redId] = peer }
            emit()
        }
    }

    /** بدء النشر + الاكتشاف. آمنة للتكرار. */
    fun start() {
        val manager = nsd
        if (manager == null) {
            Log.w(TAG, "NSD unavailable")
            return
        }
        acquireMulticastLock()
        runCatching {
            val info = NsdServiceInfo().apply {
                serviceName = "RED-${myRedId.takeLast(6)}"
                serviceType = SERVICE_TYPE
                port = signalPort
                setAttribute("redId", myRedId.trim().uppercase())
                setAttribute("name", URLEncoder.encode(myName.take(32), "UTF-8"))
                setAttribute("ver", TXT_VER)
            }
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }.onFailure { Log.w(TAG, "register threw", it) }
        runCatching {
            if (!discovering) manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure { Log.w(TAG, "discover threw", it) }
        sweeper?.cancel()
        sweeper = scope.launch {
            while (isActive) {
                delay(15_000)
                val cutoff = System.currentTimeMillis() - PEER_TTL_MS
                var changed = false
                synchronized(peers) {
                    val stale = peers.filterValues { it.lastSeen < cutoff }.keys.toList()
                    stale.forEach { peers.remove(it) }
                    changed = stale.isNotEmpty()
                }
                if (changed) emit()
            }
        }
    }

    fun stop() {
        sweeper?.cancel()
        sweeper = null
        runCatching { if (discovering) nsd?.stopServiceDiscovery(discoveryListener) }
        runCatching { nsd?.unregisterService(registrationListener) }
        runCatching { multicastLock?.release() }
        multicastLock = null
        synchronized(peers) { peers.clear() }
        emit()
    }

    fun snapshot(): List<LanPeer> = synchronized(peers) { peers.values.sortedBy { it.name } }

    private fun emit() {
        _peersFlow.value = snapshot()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? android.net.wifi.WifiManager ?: return
        multicastLock = wm.createMulticastLock("red-lan-presence").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun lanHostOf(info: NsdServiceInfo): String? {
        // API 34+: عناوين صريحة. قبلها: host (قد يكون اسم mDNS .local — ن affinity للـ IPv4).
        if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses
                ?.mapNotNull { it as? java.net.Inet4Address }
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress?.let { return it }
        }
        val h = runCatching { info.host?.hostAddress }.getOrNull()
        if (!h.isNullOrBlank() && LanNet.isPrivateIpv4(h)) return h
        return null
    }
}
