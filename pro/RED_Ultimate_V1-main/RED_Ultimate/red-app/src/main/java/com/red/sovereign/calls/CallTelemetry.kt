package com.red.sovereign.calls

import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Client-side telemetry for calls.
 *
 * Privacy-first design:
 * - Only aggregated stats are sent (RTT avg, packet loss, bitrate).
 * - No PII (peer ID, content) is ever logged.
 * - Sampling rate: 1 event per call (not per second) to minimize battery.
 * - Local-first: events queued and sent in batch.
 */
@Serializable
data class CallTelemetryEvent(
    val callId: String,
    val type: String, // VOICE, VIDEO
    val route: String, // RED, DINSTAR
    val durationMs: Long,
    val avgRttMs: Long,
    val maxPacketLoss: Double,
    val qualityAtEnd: String, // EXCELLENT, GOOD, FAIR, POOR
    val wasRecorded: Boolean,
    val wasHeld: Int, // عدد المرات
    val endedAt: Long = System.currentTimeMillis()
)

object CallTelemetry {
    private val queue = ConcurrentLinkedQueue<CallTelemetryEvent>()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var lastStats: NetworkStats = NetworkStats()
    @Volatile private var maxPacketLoss: Double = 0.0
    @Volatile private var rttSum: Long = 0
    @Volatile private var rttCount: Int = 0
    @Volatile private var holdCount: Int = 0
    @Volatile private var wasRecorded: Boolean = false

    fun reset() {
        lastStats = NetworkStats()
        maxPacketLoss = 0.0
        rttSum = 0
        rttCount = 0
        holdCount = 0
        wasRecorded = false
    }

    fun onNetworkStats(stats: NetworkStats) {
        lastStats = stats
        if (stats.packetLossPercent > maxPacketLoss) maxPacketLoss = stats.packetLossPercent
        rttSum += stats.rttMs
        rttCount += 1
    }

    fun onHold() { holdCount += 1 }
    fun onRecordingStart() { wasRecorded = true }
    fun onCallEnded(callId: String, type: String, route: String, durationMs: Long) {
        val event = CallTelemetryEvent(
            callId = callId,
            type = type,
            route = route,
            durationMs = durationMs,
            avgRttMs = if (rttCount > 0) rttSum / rttCount else 0L,
            maxPacketLoss = maxPacketLoss,
            qualityAtEnd = lastStats.quality.name,
            wasRecorded = wasRecorded,
            wasHeld = holdCount
        )
        queue.add(event)
    }

    /**
     * يفرّغ الـ queue ويرسلها للـ backend. استدعى دورياً (e.g. كل 5 دقائق)
     * أو عند Wi-Fi connection.
     */
    fun flush(context: android.content.Context) {
        if (queue.isEmpty()) return
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val client = AuthorizedApiClient(TokenStore(context))
            while (queue.isNotEmpty()) {
                val event = queue.poll() ?: break
                try {
                    val payload = json.encodeToString(CallTelemetryEvent.serializer(), event)
                    client.request("POST", "/api/calls/telemetry", jsonBody = payload)
                } catch (_: Exception) {
                    // نعيد الإضافة في حالة الفشل
                    queue.add(event)
                    break
                }
            }
        }
    }
}
