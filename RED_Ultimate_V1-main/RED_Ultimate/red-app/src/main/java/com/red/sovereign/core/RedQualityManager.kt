package com.red.sovereign.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.red.sovereign.settings.SettingsRuntime

/**
 * Ø£Ù‚ÙˆÙ‰ Ù…Ø¨Ø±Ù…Ø¬ â€” Ù…Ø¯ÙŠØ± Ø§Ù„Ø¬ÙˆØ¯Ø© Ø§Ù„Ø§Ø­ØªØ±Ø§ÙÙŠ Ø§Ù„Ø±Ø³Ù…ÙŠ
 * ÙŠØ­Ø¯Ø¯ Ø¬ÙˆØ¯Ø© Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª ÙˆØ§Ù„ÙˆØ³Ø§Ø¦Ø· Ø­Ø³Ø¨ Ø§Ù„Ø´Ø¨ÙƒØ© ÙˆØ¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… â€” ØºÙŠØ± Ù…Ù‡Ø±Ø¬
 */
object RedQualityManager {
    /** تهيئة مبكرة آمنة — جودة تلقائية تُفعَّل لاحقاً عند توفر مقاييس. */
    fun initialize(context: android.content.Context) { /* reserved */ }
    enum class NetworkTier { WIFI, CELL_4G, CELL_3G, OFFLINE }
    data class QualityProfile(val videoWidth: Int, val videoHeight: Int, val fps: Int, val videoKbps: Int, val audioKbps: Int)

    private val wifiProfile = QualityProfile(1280, 720, 30, 1200, 48)
    private val goodCellProfile = QualityProfile(960, 540, 24, 700, 32)
    private val poorCellProfile = QualityProfile(640, 480, 20, 350, 24)
    private val saverProfile = QualityProfile(640, 480, 15, 250, 24)

    fun tier(context: Context): NetworkTier {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkTier.OFFLINE
        val net = cm.activeNetwork ?: return NetworkTier.OFFLINE
        val cap = cm.getNetworkCapabilities(net) ?: return NetworkTier.OFFLINE
        return when {
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTier.WIFI
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val down = cap.linkDownstreamBandwidthKbps
                if (down >= 5000) NetworkTier.CELL_4G else NetworkTier.CELL_3G
            }
            else -> NetworkTier.OFFLINE
        }
    }

    fun videoProfile(context: Context): QualityProfile {
        if (try { SettingsRuntime.current.dataSaverCalls } catch (_: Exception) { false }) return saverProfile
        return when (tier(context)) {
            NetworkTier.WIFI -> wifiProfile
            NetworkTier.CELL_4G -> goodCellProfile
            NetworkTier.CELL_3G, NetworkTier.OFFLINE -> poorCellProfile
        }
    }

    fun shouldAutoDownload(context: Context, sizeBytes: Long): Boolean {
        val autoLimit = try { SettingsRuntime.current.autoDownloadLimitMb * 1024L * 1024L } catch (_: Exception) { 10 * 1024L * 1024L }
        if (sizeBytes > autoLimit) return false
        return when (tier(context)) {
            NetworkTier.WIFI -> try { SettingsRuntime.current.autoDownloadWifi } catch (_: Exception) { true }
            NetworkTier.CELL_4G, NetworkTier.CELL_3G -> try { SettingsRuntime.current.autoDownloadMobile } catch (_: Exception) { false }
            NetworkTier.OFFLINE -> false
        }
    }
}
