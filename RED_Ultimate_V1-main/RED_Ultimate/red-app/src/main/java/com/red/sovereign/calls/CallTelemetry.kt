package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side telemetry for calls with enhanced thread safety, MOS score tracking,
 * robust exception handling, and batched flushing.
 *
 * Privacy-first design:
 * - Only aggregated stats are sent (RTT avg, packet loss, bitrate, MOS).
 * - No PII (peer ID, content) is ever logged.
 * - Sampling rate: 1 event per call (not per second) to minimize battery.
 * - Local-first: events queued and sent in batch.
 */
@Serializable
data class CallTelemetryEvent(
    val callId: String,
    val type: String, // VOICE, VIDEO
    val route: String, // RED, RED
    val durationMs: Long,
    val avgRttMs: Long,
    val maxPacketLoss: Double,
    val avgMosScore: Double,
    val qualityAtEnd: String, // EXCELLENT, GOOD, FAIR, POOR
    val wasRecorded: Boolean,
    val wasHeld: Int, // عدد المرات
    val endedAt: Long = System.currentTimeMillis()
)

object CallTelemetry {
    private const val TAG = "CallTelemetry"
    private val queue = ConcurrentLinkedQueue<CallTelemetryEvent>()
    private val json = Json { ignoreUnknownKeys = true }

    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastStats: NetworkStats = NetworkStats()
    private val maxPacketLossAtomic = AtomicLong(0L)
    @Volatile private var maxPacketLossVal: Double = 0.0
    private val rttSum = AtomicLong(0L)
    private val rttCount = AtomicInteger(0)
    private val mosSum = AtomicLong(0L)
    private val holdCount = AtomicInteger(0)
    private val wasRecordedAtomic = AtomicBoolean(false)

    fun reset() {
        lastStats = NetworkStats()
        maxPacketLossVal = 0.0
        maxPacketLossAtomic.set(0L)
        rttSum.set(0L)
        rttCount.set(0)
        mosSum.set(0L)
        holdCount.set(0)
        wasRecordedAtomic.set(false)
        Log.d(TAG, "Telemetry state reset.")
    }

    fun onNetworkStats(stats: NetworkStats) {
        lastStats = stats
        if (stats.packetLossPercent > maxPacketLossVal) {
            maxPacketLossVal = stats.packetLossPercent
            maxPacketLossAtomic.set(java.lang.Double.doubleToRawLongBits(maxPacketLossVal))
        }
        rttSum.addAndGet(stats.rttMs)
        rttCount.incrementAndGet()

        val mos = SdpMediaOptimizer.mos(stats.rttMs, stats.packetLossPercent.toDouble())
        mosSum.addAndGet((mos * 100).toLong())
    }

    fun onHold() {
        holdCount.incrementAndGet()
        Log.d(TAG, "Call put on hold (total holds: ${holdCount.get()})")
    }

    data class Snapshot(
        val avgRttMs: Long,
        val maxLoss: Double,
        val avgMos: Double,
        val quality: String,
        val pending: Int
    )

    fun snapshot(): Snapshot {
        val count = rttCount.get()
        val avgRtt = if (count > 0) rttSum.get() / count else 0L
        val avgMos = if (count > 0) (mosSum.get().toDouble() / count) / 100.0 else 0.0
        return Snapshot(
            avgRttMs = avgRtt,
            maxLoss = maxPacketLossVal,
            avgMos = avgMos,
            quality = lastStats.quality.name,
            pending = queue.size
        )
    }

    fun onRecordingStart() {
        wasRecordedAtomic.set(true)
        Log.d(TAG, "Call recording started.")
    }

    fun onCallEnded(callId: String, type: String, route: String, durationMs: Long) {
        val count = rttCount.get()
        val avgRtt = if (count > 0) rttSum.get() / count else 0L
        val avgMos = if (count > 0) (mosSum.get().toDouble() / count) / 100.0 else 0.0

        val event = CallTelemetryEvent(
            callId = callId,
            type = type,
            route = route,
            durationMs = durationMs,
            avgRttMs = avgRtt,
            maxPacketLoss = maxPacketLossVal,
            avgMosScore = avgMos,
            qualityAtEnd = lastStats.quality.name,
            wasRecorded = wasRecordedAtomic.get(),
            wasHeld = holdCount.get()
        )
        queue.add(event)
        Log.d(TAG, "Call telemetry event queued for callId=$callId (queue size: ${queue.size})")
    }

    /**
     * يفرّغ الـ queue ويرسلها للـ backend. يُستدعى عند endCall.
     */
    fun flush(context: Context) {
        val enabled = runCatching {
            com.red.sovereign.settings.SettingsRuntime.current.devTelemetryEnabled
        }.getOrElse { true }

        if (!enabled) {
            Log.d(TAG, "Telemetry flushing skipped (devTelemetryEnabled is false).")
            return
        }
        if (queue.isEmpty()) return

        flushScope.launch {
            val client = AuthorizedApiClient(TokenStore(context))
            while (queue.isNotEmpty()) {
                val event = queue.poll() ?: break
                runCatching {
                    val payload = json.encodeToString(CallTelemetryEvent.serializer(), event)
                    client.request("POST", "/api/calls/telemetry", jsonBody = payload)
                    Log.d(TAG, "Telemetry event successfully flushed for callId=${event.callId}")
                }.getOrElse { e ->
                    Log.w(TAG, "Failed to flush telemetry event for callId=${event.callId}: ${e.message}", e)
                    queue.add(event)
                    break
                }
            }
        }
    }
}
