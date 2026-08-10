package com.red.sovereign.calls

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSignalSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun `OFFER signal round-trips`() {
        val original = CallSignal(
            callId = "abc-123",
            targetUserId = "YNS-AAAA-BBBB",
            sourceUserId = "YNS-CCCC-DDDD",
            type = "OFFER",
            mode = "VIDEO",
            payload = mapOf("sdp" to "v=0...")
        )
        val encoded = json.encodeToString(CallSignal.serializer(), original)
        val decoded = json.decodeFromString(CallSignal.serializer(), encoded)
        assertEquals(original.callId, decoded.callId)
        assertEquals(original.targetUserId, decoded.targetUserId)
        assertEquals(original.type, decoded.type)
        assertEquals(original.mode, decoded.mode)
        assertEquals("v=0...", decoded.payload["sdp"])
    }

    @Test fun `ICE signal carries sdpMid, sdpMLineIndex, candidate`() {
        val original = CallSignal(
            callId = "x",
            targetUserId = "YNS-X-X",
            type = "ICE",
            payload = mapOf(
                "sdpMid" to "0",
                "sdpMLineIndex" to "0",
                "candidate" to "candidate:1 1 udp 1.2.3.4 5000 typ host"
            )
        )
        val encoded = json.encodeToString(CallSignal.serializer(), original)
        val decoded = json.decodeFromString(CallSignal.serializer(), encoded)
        assertEquals("ICE", decoded.type)
        assertTrue(decoded.payload.containsKey("candidate"))
    }
}

class LiveStreamSignalSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test fun `JOIN with broadcaster role`() {
        val original = LiveStreamSignal(
            type = "JOIN",
            roomId = "stream-1",
            userId = "YNS-AAAA",
            payload = mapOf("role" to "broadcaster")
        )
        val encoded = json.encodeToString(LiveStreamSignal.serializer(), original)
        val decoded = json.decodeFromString(LiveStreamSignal.serializer(), encoded)
        assertEquals("JOIN", decoded.type)
        assertEquals("broadcaster", decoded.payload["role"])
    }
}
