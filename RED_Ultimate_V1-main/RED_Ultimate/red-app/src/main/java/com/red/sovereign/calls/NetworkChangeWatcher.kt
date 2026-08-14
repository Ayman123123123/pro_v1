package com.red.sovereign.calls

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * يراقب تبديل الشبكة (Wi-Fi ↔ بيانات خلوية) أثناء المكالمة النشطة.
 *
 * عند فقدان شبكة ثم توفر شبكة جديدة (التبديل الأكثر شيوعاً أثناء المكالمة)،
 * يُستدعى [onNetworkRecovered] — وعلى المتصل تنفيذ IceRestart لإعادة استكشاف
 * المسار عبر الشبكة الجديدة دون انقطاع الصوت/الفيديو.
 */
class NetworkChangeWatcher(
    private val context: Context,
    private val onNetworkRecovered: () -> Unit
) {
    @Volatile private var wasOffline = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            wasOffline = true
        }

        override fun onAvailable(network: Network) {
            if (wasOffline) {
                wasOffline = false
                onNetworkRecovered()
            }
        }
    }

    fun start() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}