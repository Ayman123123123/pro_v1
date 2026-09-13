package com.red.sovereign.calls

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * يراقب تبديل الشبكة (Wi-Fi ↔ بيانات خلوية) أثناء المكالمة النشطة.
 *
 * يُستدعى [onNetworkRecovered] في حالتين:
 * 1) عودة بعد انقطاع (onLost ثم onAvailable) — الحالة الأصلية.
 * 2) handover مباشر WiFi↔4G دون انقطاع يُرى (تغيّر الـ bearer النشط عبر
 *    onCapabilitiesChanged) — كان يُفوَّت كلياً فيبقى الصوت/الفيديو
 *    معلقاً على IP ميت حتى يسقط الـ PeerConnection.
 *
 * على المتصل عند الإطلاق تنفيذ restartIce لكلا ناقلي SFU (send/recv عبر
 * SfuMediaClient.restartSfuIce) + MeshRtcSession.restartIce، وإن تعذّر
 * الوصول للـ transport (FAILED مستمر) fallback كامل re-join عبر
 * SfuMediaClient.rejoin.
 */
class NetworkChangeWatcher(
    private val context: Context,
    private val onNetworkRecovered: () -> Unit
) {
    companion object {
        private const val TAG = "NetworkChangeWatcher"
        private const val TRANSPORT_UNKNOWN = -1
        private const val TRANSPORT_OTHER = 100
    }

    @Volatile private var wasOffline = false
    @Volatile private var lastTransport = TRANSPORT_UNKNOWN
    @Volatile private var lastFireMs = 0L
    private val isStarted = AtomicBoolean(false)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            wasOffline = true
            Log.d(TAG, "Network lost event received")
        }

        override fun onAvailable(network: Network) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            updateTransport(cm?.getNetworkCapabilities(network))
            if (wasOffline) {
                wasOffline = false
                Log.d(TAG, "Network recovered after being offline — triggering ICE restart")
                fire()
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val current = transportOf(caps)
            val previous = lastTransport
            lastTransport = current
            if (previous != TRANSPORT_UNKNOWN && current != TRANSPORT_UNKNOWN && current != previous) {
                wasOffline = false
                Log.d(TAG, "Transport handover detected: $previous -> $current — requesting ICE restart")
                fire()
            }
        }
    }

    fun start() {
        if (!isStarted.compareAndSet(false, true)) return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            lastTransport = transportOf(cm.getNetworkCapabilities(cm.activeNetwork))
            cm.registerDefaultNetworkCallback(callback)
            Log.d(TAG, "NetworkChangeWatcher started successfully (initial transport: $lastTransport)")
        }.onFailure { e ->
            isStarted.set(false)
            Log.e(TAG, "Failed to start NetworkChangeWatcher", e)
        }
    }

    fun stop() {
        if (!isStarted.compareAndSet(true, false)) return
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.unregisterNetworkCallback(callback)
            Log.d(TAG, "NetworkChangeWatcher stopped successfully")
        }.onFailure { e ->
            Log.e(TAG, "Failed to stop NetworkChangeWatcher", e)
        }
    }

    private fun updateTransport(caps: NetworkCapabilities?) {
        val current = transportOf(caps)
        if (current != TRANSPORT_UNKNOWN) lastTransport = current
    }

    /** إطلاق واحد منزوع الارتداد: دفعة قدرات واحدة أثناء الـ handover = استدعاء واحد. */
    private fun fire() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFireMs < 2_000) return
        lastFireMs = now
        runCatching {
            onNetworkRecovered()
        }.onFailure { e ->
            Log.e(TAG, "Error executing onNetworkRecovered callback", e)
        }
    }

    private fun transportOf(caps: NetworkCapabilities?): Int {
        if (caps == null) return TRANSPORT_UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCapabilities.TRANSPORT_CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkCapabilities.TRANSPORT_ETHERNET
            android.os.Build.VERSION.SDK_INT >= 31 &&
                caps.hasTransport(8 /* NetworkCapabilities.TRANSPORT_USB */) -> NetworkCapabilities.TRANSPORT_ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkCapabilities.TRANSPORT_VPN
            else -> TRANSPORT_OTHER
        }
    }
}
