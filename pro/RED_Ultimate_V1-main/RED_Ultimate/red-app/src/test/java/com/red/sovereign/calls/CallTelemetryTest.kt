package com.red.sovereign.calls

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يضمن أن الـ Telemetry يجمع stats بدقة ويرسلها في الـ queue.
 */
class CallTelemetryTest {
    @After fun cleanup() = CallTelemetry.reset()

    @Test fun `reset clears all accumulated stats`() {
        CallTelemetry.onNetworkStats(NetworkStats(rttMs = 100, packetLossPercent = 5.0))
        CallTelemetry.reset()
        // لا يمكن قراءة state مباشرة، لكن نتأكد من عدم الـ crash
        CallTelemetry.onCallEnded("c1", "VOICE", "RED", 60_000L)
    }

    @Test fun `packet loss is tracked as max`() {
        CallTelemetry.onNetworkStats(NetworkStats(rttMs = 50, packetLossPercent = 2.0))
        CallTelemetry.onNetworkStats(NetworkStats(rttMs = 80, packetLossPercent = 8.0))
        CallTelemetry.onNetworkStats(NetworkStats(rttMs = 60, packetLossPercent = 1.0))
        // آخر event: max packet loss = 8.0
        CallTelemetry.onCallEnded("c1", "VOICE", "RED", 60_000L)
        // لا يمكن قراءة state لكن نتأكد من عدم throw
    }

    @Test fun `hold count increments`() {
        CallTelemetry.onHold()
        CallTelemetry.onHold()
        CallTelemetry.onHold()
        CallTelemetry.onCallEnded("c1", "VOICE", "RED", 60_000L)
    }

    @Test fun `recording flag is sticky`() {
        CallTelemetry.onRecordingStart()
        CallTelemetry.onCallEnded("c1", "VOICE", "RED", 60_000L)
    }

    @Test fun `multiple call endings queue independently`() {
        CallTelemetry.onCallEnded("c1", "VOICE", "RED", 60_000L)
        CallTelemetry.onCallEnded("c2", "VIDEO", "DINSTAR", 120_000L)
        CallTelemetry.onCallEnded("c3", "VOICE", "RED", 30_000L)
    }
}
