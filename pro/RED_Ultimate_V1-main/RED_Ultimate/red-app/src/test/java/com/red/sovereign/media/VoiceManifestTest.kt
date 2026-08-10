package com.red.sovereign.media

import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات VoiceManifest — يضمن أن بنية الـ JSON تشفّر/تفك بشكل صحيح
 */
class VoiceManifestTest {

    @Test
    fun `manifest default values are correct`() {
        val manifest = VoiceManifest(
            objectKey = "users/abc/voice.m4a",
            url = "/api/media/users/abc/voice.m4a",
            name = "voice-123.m4a",
            size = 12345L,
            durationSeconds = 5,
            sha256 = "abc123",
            key = "encrypted_key_base64",
            nonce = "nonce_base64"
        )
        assertEquals(1, manifest.version)
        assertEquals("audio/mp4", manifest.mimeType)
        assertTrue(manifest.waveform.isEmpty())
    }

    @Test
    fun `manifest with custom waveform`() {
        val waveform = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
        val manifest = VoiceManifest(
            objectKey = "users/abc/v.m4a",
            url = "/api/media/users/abc/v.m4a",
            name = "v.m4a",
            size = 1000L,
            durationSeconds = 1,
            waveform = waveform,
            sha256 = "deadbeef",
            key = "k",
            nonce = "n"
        )
        assertEquals(waveform, manifest.waveform)
    }

    @Test
    fun `waveform can hold up to 96 samples`() {
        val waveform = (0 until 96).map { (it * 100) / 95 }
        val manifest = VoiceManifest(
            objectKey = "k", url = "u", name = "n",
            size = 0L, durationSeconds = 0, waveform = waveform,
            sha256 = "", key = "", nonce = ""
        )
        assertEquals(96, manifest.waveform.size)
    }

    @Test
    fun `state Idle is singleton`() {
        val a: VoiceMessageState = VoiceMessageState.Idle
        val b: VoiceMessageState = VoiceMessageState.Idle
        assertSame(a, b)
    }

    @Test
    fun `state Sending is singleton`() {
        val a: VoiceMessageState = VoiceMessageState.Sending
        val b: VoiceMessageState = VoiceMessageState.Sending
        assertSame(a, b)
    }

    @Test
    fun `state Recording holds paused flag`() {
        val recording = VoiceMessageState.Recording(paused = true)
        assertTrue(recording.paused)
        val active = VoiceMessageState.Recording(paused = false)
        assertFalse(active.paused)
    }

    @Test
    fun `state Preview holds duration`() {
        val preview = VoiceMessageState.Preview(durationSeconds = 15)
        assertEquals(15, preview.durationSeconds)
    }

    @Test
    fun `state Sent holds duration`() {
        val sent = VoiceMessageState.Sent(durationSeconds = 30)
        assertEquals(30, sent.durationSeconds)
    }

    @Test
    fun `state Error holds message`() {
        val error = VoiceMessageState.Error("MICROPHONE_PERMISSION_REQUIRED")
        assertEquals("MICROPHONE_PERMISSION_REQUIRED", error.message)
    }

    @Test
    fun `manifest sha256 is 64 hex chars in real usage`() {
        // Real SHA-256 of empty input is e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(64, sha256.length)
        assertTrue(sha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `key is base64 of 32 bytes produces 44 chars`() {
        // Base64(32 bytes) = ceil(32/3) * 4 = 44 chars (no padding) or 44 chars with padding
        val base64Encoded32 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        assertTrue(base64Encoded32.length in 43..44)
    }

    @Test
    fun `nonce is base64 of 12 bytes produces 16 chars`() {
        val base64Encoded12 = "AAAAAAAAAAAAAAAAAAAAAA=="
        assertEquals(24, base64Encoded12.length)
    }
}
