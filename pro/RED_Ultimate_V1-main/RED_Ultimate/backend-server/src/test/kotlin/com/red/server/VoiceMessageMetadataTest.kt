package com.red.server

import com.red.server.database.VoiceMessageMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * اختبارات VoiceMessageMetadata — يضمن سلامة البيانات الوصفية للرسائل الصوتية
 */
class VoiceMessageMetadataTest {

    @Test
    fun `valid metadata with defaults`() {
        val metadata = VoiceMessageMetadata(durationMs = 5000, waveform = "")
        assertEquals(5000, metadata.durationMs)
        assertEquals(44100, metadata.sampleRate)
        assertEquals(96000, metadata.bitrate)
        assertEquals("AAC", metadata.codec)
        assertEquals("audio/mp4", metadata.mimeType)
    }

    @Test
    fun `custom sample rate and bitrate accepted`() {
        val metadata = VoiceMessageMetadata(
            durationMs = 30000,
            waveform = "",
            sampleRate = 48000,
            bitrate = 128000
        )
        assertEquals(48000, metadata.sampleRate)
        assertEquals(128000, metadata.bitrate)
    }

    @Test
    fun `opuses codec supported`() {
        val metadata = VoiceMessageMetadata(
            durationMs = 1000,
            waveform = "",
            codec = "Opus"
        )
        assertEquals("Opus", metadata.codec)
    }

    @Test
    fun `ogg mime type supported`() {
        val metadata = VoiceMessageMetadata(
            durationMs = 1000,
            waveform = "",
            mimeType = "audio/ogg"
        )
        assertEquals("audio/ogg", metadata.mimeType)
    }

    @Test
    fun `durationMs cannot exceed 10 minutes`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceMessageMetadata(durationMs = 600_001, waveform = "")
        }
    }

    @Test
    fun `durationMs at 10 minutes is allowed`() {
        val metadata = VoiceMessageMetadata(durationMs = 600_000, waveform = "")
        assertEquals(600_000, metadata.durationMs)
    }

    @Test
    fun `sample rate below 8 kHz rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceMessageMetadata(durationMs = 1000, waveform = "", sampleRate = 4000)
        }
    }

    @Test
    fun `sample rate above 48 kHz rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceMessageMetadata(durationMs = 1000, waveform = "", sampleRate = 96000)
        }
    }

    @Test
    fun `bitrate below 8 kbps rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceMessageMetadata(durationMs = 1000, waveform = "", bitrate = 4000)
        }
    }

    @Test
    fun `bitrate above 320 kbps rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceMessageMetadata(durationMs = 1000, waveform = "", bitrate = 500_000)
        }
    }

    @Test
    fun `waveform as base64 of 96 ints encodes correctly`() {
        // 96 ints × 4 bytes = 384 bytes raw
        val waveform = (0 until 96).map { it }
        val bytes = ByteArray(96 * 4) { idx ->
            val int = waveform[idx / 4]
            // little-endian
            when (idx % 4) {
                0 -> (int and 0xFF).toByte()
                1 -> ((int shr 8) and 0xFF).toByte()
                2 -> ((int shr 16) and 0xFF).toByte()
                else -> ((int shr 24) and 0xFF).toByte()
            }
        }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val metadata = VoiceMessageMetadata(durationMs = 1000, waveform = encoded)
        // 384 bytes → ~512 chars base64
        assertTrue(metadata.waveform.length in 500..520)
    }

    @Test
    fun `empty waveform is allowed`() {
        val metadata = VoiceMessageMetadata(durationMs = 1000, waveform = "")
        assertEquals("", metadata.waveform)
    }

    @Test
    fun `zero duration is allowed`() {
        val metadata = VoiceMessageMetadata(durationMs = 0, waveform = "")
        assertEquals(0, metadata.durationMs)
    }
}
