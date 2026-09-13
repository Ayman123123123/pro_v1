package com.red.sovereign.features.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * P2-LAN — أدوات الشبكة المحلية (نفس الواي فاي).
 *
 * بلا أذونات موقع: نستخدم LinkProperties (عناوين IP) لا SSID.
 * يتطلب التطبيق في المانيفست: ACCESS_WIFI_STATE + CHANGE_WIFI_MULTICAST_STATE (لـ NSD).
 */
object LanNet {

    data class WifiNet(
        /** عنوان IPv4 المحلي، مثلا 192.168.1.23 */
        val ip: String,
        /** بادئة /24، مثلا 192.168.1 */
        val prefix24: String,
        /** هل النقل الحالي واي فاي */
        val transportWifi: Boolean
    )

    /** معلومات الشبكة الحالية، أو null إن لم نكن على IP خاص. */
    fun currentWifiNet(context: Context): WifiNet? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network)
        val transportWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val props = cm.getLinkProperties(network) ?: return null
        val ip = props.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress ?: return null
        // احتياطي: إن لم يوجد site-local (هوسبوت غريب)، اقبل أي IPv4 غير loopback
        return WifiNet(ip = ip, prefix24 = prefix24Of(ip) ?: return null, transportWifi = transportWifi)
    }

    /** عنوان IPv4 لمضيف المحاكي/الجهاز عبر واجهات الشبكة (احتياطي بلا ConnectivityManager). */
    fun firstSiteLocalIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses?.toList().orEmpty() }
            ?.mapNotNull { it as? Inet4Address }
            ?.firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    fun prefix24Of(ip: String): String? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        if (parts.any { it.toIntOrNull() !in 0..255 }) return null
        return parts.take(3).joinToString(".")
    }

    /** هل العنوانان على نفس /24 (نفس الشبكة اللاسلكية غالبا). */
    fun sameSubnet(a: String, b: String): Boolean {
        val pa = prefix24Of(a) ?: return false
        val pb = prefix24Of(b) ?: return false
        return pa == pb
    }

    fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.trim().split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168)
    }
}
