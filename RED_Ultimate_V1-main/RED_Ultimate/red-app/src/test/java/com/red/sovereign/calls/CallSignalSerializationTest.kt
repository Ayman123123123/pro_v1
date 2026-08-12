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
            targetUserId = "73066",
            sourceUserId = "28261",
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

    @Test fun `RENEGOTIATE signal round-trips without becoming a new OFFER`() {
        val original = CallSignal(
            callId = "same-call",
            targetUserId = "73066",
            sourceUserId = "28261",
            type = "RENEGOTIATE",
            mode = "VOICE",
            payload = mapOf("sdp" to "v=0-restart")
        )
        val decoded = json.decodeFromString(CallSignal.serializer(), json.encodeToString(CallSignal.serializer(), original))
        assertEquals("RENEGOTIATE", decoded.type)
        assertEquals("same-call", decoded.callId)
        assertEquals("v=0-restart", decoded.payload["sdp"])
    }

    @Test fun `conference invite is not an OFFER`() {
        val original = CallSignal(
            callId = "room-1",
            targetUserId = "73066",
            sourceUserId = "28261",
            type = "CONFERENCE_INVITE",
            mode = "SPACE",
            payload = mapOf("inviter" to "علي", "video" to "false")
        )
        val decoded = json.decodeFromString(CallSignal.serializer(), json.encodeToString(CallSignal.serializer(), original))
        assertEquals("CONFERENCE_INVITE", decoded.type)
        assertEquals("SPACE", decoded.mode)
        assertEquals("علي", decoded.payload["inviter"])
    }

    @Test fun `ICE signal carries sdpMid, sdpMLineIndex, candidate`() {
        val original = CallSignal(
            callId = "x",
            targetUserId = "99559",
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
            userId = "91179",
            payload = mapOf("role" to "broadcaster")
        )
        val encoded = json.encodeToString(LiveStreamSignal.serializer(), original)
        val decoded = json.decodeFromString(LiveStreamSignal.serializer(), encoded)
        assertEquals("JOIN", decoded.type)
        assertEquals("broadcaster", decoded.payload["role"])
    }
}
