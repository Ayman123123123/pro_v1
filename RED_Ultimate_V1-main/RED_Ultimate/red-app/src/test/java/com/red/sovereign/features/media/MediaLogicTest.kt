package com.red.sovereign.features.media

import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.FileTypeUtil
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * اختبارات منطق الوسائط النقي (JVM): خرائط MIME، تنسيقات، تصنيفات، موجة مستقرة، تحليل manifest.
 */
class MediaLogicTest {

    // ── FileTypeUtil.getMimeFromExtension ────────────────────────────────

    @Test
    fun `mime mapping covers production formats`() {
        assertEquals("application/pdf", FileTypeUtil.getMimeFromExtension("report.pdf"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            FileTypeUtil.getMimeFromExtension("doc.docx")
        )
        assertEquals("application/vnd.ms-excel", FileTypeUtil.getMimeFromExtension("table.xls"))
        assertEquals("image/jpeg", FileTypeUtil.getMimeFromExtension("photo.jpg"))
        assertEquals("image/png", FileTypeUtil.getMimeFromExtension("shot.PNG"))
        assertEquals("video/mp4", FileTypeUtil.getMimeFromExtension("clip.mp4"))
        assertEquals("audio/mpeg", FileTypeUtil.getMimeFromExtension("song.mp3"))
        assertEquals("audio/opus", FileTypeUtil.getMimeFromExtension("voice.opus"))
        assertEquals("text/plain", FileTypeUtil.getMimeFromExtension("notes.txt"))
        assertEquals("application/zip", FileTypeUtil.getMimeFromExtension("bundle.zip"))
        assertEquals("application/vnd.android.package-archive", FileTypeUtil.getMimeFromExtension("app.apk"))
    }

    @Test
    fun `unknown extension falls back to octet-stream`() {
        assertEquals("application/octet-stream", FileTypeUtil.getMimeFromExtension("mystery.xyz"))
        assertEquals("application/octet-stream", FileTypeUtil.getMimeFromExtension("noext"))
    }

    // ── FileTypeUtil.formatFileSize ──────────────────────────────────────

    @Test
    fun `file size formatting units`() {
        assertEquals("0 B", FileTypeUtil.formatFileSize(0L))
        assertEquals("512 B", FileTypeUtil.formatFileSize(512L))
        assertTrue(FileTypeUtil.formatFileSize(2048L).endsWith("KB"))
        assertTrue(FileTypeUtil.formatFileSize(5L * 1024 * 1024).endsWith("MB"))
        assertTrue(FileTypeUtil.formatFileSize(2L * 1024 * 1024 * 1024).endsWith("GB"))
    }

    // ── FileTypeUtil.getFileCategory ─────────────────────────────────────

    @Test
    fun `category detection by mime`() {
        assertEquals(FileTypeUtil.FileCategory.IMAGE, FileTypeUtil.getFileCategory("image/webp"))
        assertEquals(FileTypeUtil.FileCategory.VIDEO, FileTypeUtil.getFileCategory("video/x-matroska"))
        assertEquals(FileTypeUtil.FileCategory.AUDIO, FileTypeUtil.getFileCategory("audio/flac"))
        assertEquals(FileTypeUtil.FileCategory.DOCUMENT, FileTypeUtil.getFileCategory("application/pdf"))
        assertEquals(FileTypeUtil.FileCategory.DOCUMENT, FileTypeUtil.getFileCategory("application/json"))
        assertEquals(FileTypeUtil.FileCategory.ARCHIVE, FileTypeUtil.getFileCategory("application/x-7z-compressed"))
        assertEquals(FileTypeUtil.FileCategory.OTHER, FileTypeUtil.getFileCategory("application/octet-stream"))
    }

    // ── formatMediaTime ──────────────────────────────────────────────────

    @Test
    fun `media time formatting`() {
        assertEquals("--:--", formatMediaTime(0L))
        assertEquals("--:--", formatMediaTime(-5L))
        assertEquals("00:05", formatMediaTime(5_000L))
        assertEquals("01:00", formatMediaTime(60_000L))
        assertEquals("01:01", formatMediaTime(61_000L))
        assertEquals("1:00:00", formatMediaTime(3_600_000L))
        assertEquals("1:02:03", formatMediaTime((3_600 + 123).toLong() * 1000))
    }

    // ── buildStableWaveform ──────────────────────────────────────────────

    @Test
    fun `waveform is deterministic per content and sized correctly`() {
        val f1 = File.createTempFile("wf1", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val f1b = File.createTempFile("wf1b", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        try {
            val w1 = buildStableWaveform(f1, 64)
            val w1again = buildStableWaveform(f1, 64)
            val wSameContent = buildStableWaveform(f1b, 64)
            assertEquals(64, w1.size)
            assertEquals(w1, w1again)
            assertEquals(w1, wSameContent)
            assertTrue(w1.all { it >= 0.08f && it <= 1f })
        } finally {
            f1.delete(); f1b.delete()
        }
    }

    @Test
    fun `waveform fallback for unreadable file`() {
        val ghost = File("/nonexistent/path/file.bin")
        val w = buildStableWaveform(ghost, 32)
        assertEquals(32, w.size)
        assertTrue(w.all { it == 0.35f })
    }

    // ── parseManifest ────────────────────────────────────────────────────

    private fun msg(payload: String) = DecryptedMessage(
        id = "m1",
        conversationId = "c1",
        senderRedId = "user-a",
        plaintext = payload.toByteArray(Charsets.UTF_8),
        timestamp = 100L,
        sequence = 1L,
        type = "FILE"
    )

    @Test
    fun `manifest parses valid payload`() {
        val json = """
            {"version":1,"objectKey":"k","url":"http://x/f.bin","name":"تقرير.pdf",
             "mimeType":"application/pdf","size":2048,"sha256":"abc",
             "key":"AAA=","nonce":"BBB="}
        """.trimIndent()
        val m = parseManifest(msg(json), Json { ignoreUnknownKeys = true })
        assertNotNull(m)
        assertEquals("تقرير.pdf", m!!.name)
        assertEquals("application/pdf", m.mimeType)
        assertEquals(2048L, m.size)
    }

    @Test
    fun `manifest tolerates unknown fields`() {
        val json = """{"objectKey":"k","url":"u","name":"f.zip","mimeType":"application/zip",
                        "size":9,"sha256":"d","key":"k1","nonce":"n1","futureField":123}"""
        assertNotNull(parseManifest(msg(json), Json { ignoreUnknownKeys = true }))
    }

    @Test
    fun `manifest rejects garbage and plain text`() {
        assertNull(parseManifest(msg("not json at all"), Json { ignoreUnknownKeys = true }))
        assertNull(parseManifest(msg("""{"foo":1}"""), Json { ignoreUnknownKeys = true }))
    }
}
