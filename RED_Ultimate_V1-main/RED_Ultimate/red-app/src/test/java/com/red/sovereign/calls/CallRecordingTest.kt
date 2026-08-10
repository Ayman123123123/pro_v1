package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يختبر الـ data structures والمنطق الأساسي للتسجيل. الـ MediaRecorder وAndroid Keystore
 * يعتمدان على Android runtime — يتم اختبارهما في androidTest/.
 */
class CallRecordingTest {
    @Test fun `CallRecording carries all required fields`() {
        val rec = CallRecording(
            callId = "call-1",
            filePath = "/data/data/com.red.sovereign/cache/recordings/call-1.m4a.enc",
            sizeBytes = 1024L,
            encrypted = true,
            createdAt = 1_700_000_000_000L,
            durationMs = 45_000L
        )
        assertEquals("call-1", rec.callId)
        assertTrue(rec.encrypted)
        assertTrue(rec.sizeBytes > 0)
        assertTrue(rec.durationMs > 0)
    }

    @Test fun `CallRecording fields are immutable`() {
        val rec = CallRecording("c1", "/path", 100L, true, 1L, 5_000L)
        // data class — equals/hashCode/toString مضمونة
        val copy = rec.copy(durationMs = 10_000L)
        assertEquals(10_000L, copy.durationMs)
        assertEquals(rec.callId, copy.callId)
    }
}

/**
 * يختبر CallSignaling.ICE/TURN configuration parsing.
 */
class IceConfigurationTest {
    @Test fun `STUN and TURN URLs are valid`() {
        val cfg = IceConfigurationDto(
            expiresAt = 1_700_000_000L,
            iceServers = listOf(
                IceServerDto(urls = listOf("stun:turn.example.com:3478")),
                IceServerDto(urls = listOf("turn:turn.example.com:3478?transport=udp", "turn:turn.example.com:3478?transport=tcp"), username = "1700000000:user-1", credential = "abc123")
            )
        )
        assertEquals(1_700_000_000L, cfg.expiresAt)
        assertEquals(2, cfg.iceServers.size)
        assertEquals("turn:turn.example.com:3478?transport=udp", cfg.iceServers[1].urls[0])
        assertNotNull(cfg.iceServers[1].username)
        assertNotNull(cfg.iceServers[1].credential)
    }
}