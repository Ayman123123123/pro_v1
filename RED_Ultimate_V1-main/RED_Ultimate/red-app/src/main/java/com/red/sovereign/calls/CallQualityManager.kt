package com.red.sovereign.calls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدير جودة المكالمة — أسطوري متقدم 2026
 *
 * يراقب WebRTC stats ويُصنّف الشبكة إلى 4 مستويات مع توصية تلقائية
 * لجودة الفيديو والصوت وتوفير البيانات. بدون أي تبعية لـ AudioDeviceModule
 * المكسور سابقاً — يحسب محلياً عبر RTT/loss/bitrate/jitter.
 */
enum class NetworkQuality { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN }

data class QualityStats(
    val rttMs: Int = 0,
    val packetLossPercent: Float = 0f,
    val bitrateKbps: Int = 0,
    val fps: Int = 30,
    val jitterMs: Int = 0,
    val quality: NetworkQuality = NetworkQuality.GOOD
) {
    val mos: Double
        get() = SdpMediaOptimizer.mos(rttMs.toLong(), packetLossPercent.toDouble())
}

object CallQualityManager {
    private val _stats = MutableStateFlow(QualityStats())
    val stats: StateFlow<QualityStats> = _stats.asStateFlow()

    var lastStats by mutableStateOf(QualityStats())
        private set

    @Synchronized
    fun update(rttMs: Int, packetLoss: Float, bitrateKbps: Int, fps: Int, jitterMs: Int = 0) {
        val clampedRtt = rttMs.coerceAtLeast(0)
        val clampedLoss = packetLoss.coerceIn(0f, 100f)
        val clampedBitrate = bitrateKbps.coerceAtLeast(0)
        val clampedFps = fps.coerceIn(1, 60)
        val clampedJitter = jitterMs.coerceAtLeast(0)

        val quality = classify(clampedRtt, clampedLoss, clampedBitrate, clampedJitter)
        val s = QualityStats(clampedRtt, clampedLoss, clampedBitrate, clampedFps, clampedJitter, quality)
        _stats.value = s
        lastStats = s
    }

    @Synchronized
    fun reset() {
        val defaultStats = QualityStats(quality = NetworkQuality.UNKNOWN)
        _stats.value = defaultStats
        lastStats = defaultStats
    }

    private fun classify(rtt: Int, loss: Float, bitrate: Int, jitter: Int): NetworkQuality = when {
        loss > 5.0f || rtt > 400 || bitrate < 150 || jitter > 50 -> NetworkQuality.POOR
        loss > 2.0f || rtt > 200 || bitrate < 400 || jitter > 30 -> NetworkQuality.FAIR
        loss > 0.5f || rtt > 100 || bitrate < 900 || jitter > 15 -> NetworkQuality.GOOD
        else -> NetworkQuality.EXCELLENT
    }

    fun recommendedVideoEnabled(quality: NetworkQuality = lastStats.quality): Boolean =
        quality != NetworkQuality.POOR && quality != NetworkQuality.UNKNOWN

    fun recommendedBitrateKbps(quality: NetworkQuality = lastStats.quality): Int = when (quality) {
        NetworkQuality.EXCELLENT -> 1500
        NetworkQuality.GOOD -> 900
        NetworkQuality.FAIR -> 450
        NetworkQuality.POOR, NetworkQuality.UNKNOWN -> 150
    }

    fun labelFor(quality: NetworkQuality): String = when (quality) {
        NetworkQuality.EXCELLENT -> "ممتاز"
        NetworkQuality.GOOD -> "جيد"
        NetworkQuality.FAIR -> "متوسط"
        NetworkQuality.POOR -> "ضعيف"
        NetworkQuality.UNKNOWN -> "غير معروف"
    }
}
