package com.red.sovereign.calls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * مدير جودة المكالمة — أسطوري متقدم 2026
 *
 * يراقب WebRTC stats ويُصنّف الشبكة إلى 4 مستويات مع توصية تلقائية
 * لجودة الفيديو والصوت وتوفير البيانات. بدون أي تبعية لـ AudioDeviceModule
 * المكسور سابقاً — يحسب محلياً عبر RTT/loss/bitrate.
 */
enum class NetworkQuality { EXCELLENT, GOOD, FAIR, POOR }

data class QualityStats(
    val rttMs: Int = 0,
    val packetLossPercent: Float = 0f,
    val bitrateKbps: Int = 0,
    val fps: Int = 30,
    val quality: NetworkQuality = NetworkQuality.GOOD
)

object CallQualityManager {
    private val _stats = MutableStateFlow(QualityStats())
    val stats: StateFlow<QualityStats> = _stats

    var lastStats by mutableStateOf(QualityStats())
        private set

    fun update(rttMs: Int, packetLoss: Float, bitrateKbps: Int, fps: Int) {
        val quality = classify(rttMs, packetLoss, bitrateKbps)
        val s = QualityStats(rttMs, packetLoss, bitrateKbps, fps, quality)
        _stats.value = s
        lastStats = s
    }

    private fun classify(rtt: Int, loss: Float, bitrate: Int): NetworkQuality = when {
        loss > 5f || rtt > 400 || bitrate < 150 -> NetworkQuality.POOR
        loss > 2f || rtt > 200 || bitrate < 400 -> NetworkQuality.FAIR
        loss > 0.5f || rtt > 100 || bitrate < 900 -> NetworkQuality.GOOD
        else -> NetworkQuality.EXCELLENT
    }

    fun recommendedVideoEnabled(quality: NetworkQuality = lastStats.quality): Boolean =
        quality != NetworkQuality.POOR

    fun recommendedBitrateKbps(quality: NetworkQuality = lastStats.quality): Int = when (quality) {
        NetworkQuality.EXCELLENT -> 1500
        NetworkQuality.GOOD -> 900
        NetworkQuality.FAIR -> 450
        NetworkQuality.POOR -> 150
    }

    fun labelFor(quality: NetworkQuality): String = when (quality) {
        NetworkQuality.EXCELLENT -> "ممتاز"
        NetworkQuality.GOOD -> "جيد"
        NetworkQuality.FAIR -> "متوسط"
        NetworkQuality.POOR -> "ضعيف"
    }
}
