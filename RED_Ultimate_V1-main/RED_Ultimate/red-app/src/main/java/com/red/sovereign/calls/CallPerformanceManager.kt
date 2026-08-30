package com.red.sovereign.calls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدير أداء المكالمة — Call Performance Manager
 *
 * يراقب أداء المكالمة ويقدم إحصائيات:
 * - جودة الشبكة (RTT, Packet Loss, Bitrate)
 * - جودة الصوت (MOS, Jitter)
 * - جودة الفيديو (FPS, Resolution)
 * - توصيات تلقائية لتحسين الجودة
 */
object CallPerformanceManager {

    data class PerformanceStats(
        val rttMs: Int = 0,
        val packetLossPercent: Float = 0f,
        val bitrateKbps: Int = 0,
        val fps: Int = 30,
        val resolutionWidth: Int = 0,
        val resolutionHeight: Int = 0,
        val mosScore: Float = 0f,
        val jitterMs: Float = 0f
    ) {
        val quality: NetworkQuality get() = CallQualityManager.classify(rttMs, packetLossPercent, bitrateKbps)
    }

    data class PerformanceRecommendation(
        val title: String,
        val description: String,
        val action: RecommendationAction?
    )

    enum class RecommendationAction { NONE, REDUCE_QUALITY, SWITCH_TO_AUDIO, RECONNECT }

    private val _stats = MutableStateFlow(PerformanceStats())
    val stats: StateFlow<PerformanceStats> = _stats.asStateFlow()

    private val _recommendations = MutableStateFlow<List<PerformanceRecommendation>>(emptyList())
    val recommendations: StateFlow<List<PerformanceRecommendation>> = _recommendations.asStateFlow()

    fun updateStats(rttMs: Int, packetLoss: Float, bitrateKbps: Int, fps: Int = 30) {
        val current = _stats.value
        _stats.value = current.copy(
            rttMs = rttMs,
            packetLossPercent = packetLoss,
            bitrateKbps = bitrateKbps,
            fps = fps
        )
        evaluateRecommendations()
    }

    fun updateVideoStats(width: Int, height: Int) {
        val current = _stats.value
        _stats.value = current.copy(
            resolutionWidth = width,
            resolutionHeight = height
        )
    }

    private fun evaluateRecommendations() {
        val s = _stats.value
        val recs = mutableListOf<PerformanceRecommendation>()

        if (s.quality == NetworkQuality.POOR) {
            recs.add(PerformanceRecommendation(
                title = "جودة شبكة ضعيفة",
                description = "يُوصى بتقليل جودة الفيديو أو التحول للمكالمات الصوتية",
                action = RecommendationAction.REDUCE_QUALITY
            ))
        }

        if (s.rttMs > 300) {
            recs.add(PerformanceRecommendation(
                title = "زمن استجابة عالٍ",
                description = "تأخير الشبكة يؤثر على جودة المكالمة",
                action = RecommendationAction.RECONNECT
            ))
        }

        if (s.packetLossPercent > 3f) {
            recs.add(PerformanceRecommendation(
                title = "فقدان حزم مرتفع",
                description = "الشبكة غير مستقرة، يُنصح باستخدام WiFi",
                action = null
            ))
        }

        _recommendations.value = recs
    }

    fun clearStats() {
        _stats.value = PerformanceStats()
        _recommendations.value = emptyList()
    }

    fun getAdaptiveBitrate(): Int = when (_stats.value.quality) {
        NetworkQuality.EXCELLENT -> 1500
        NetworkQuality.GOOD -> 900
        NetworkQuality.FAIR -> 450
        NetworkQuality.POOR -> 150
    }

    fun shouldDisableVideo(): Boolean = _stats.value.quality == NetworkQuality.POOR
    fun shouldSwitchToAudio(): Boolean = _stats.value.rttMs > 400 || _stats.value.packetLossPercent > 5f
}
