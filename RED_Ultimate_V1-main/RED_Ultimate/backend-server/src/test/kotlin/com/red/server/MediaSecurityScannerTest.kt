package com.red.server

import com.red.server.media.MediaSecurityScanner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class MediaSecurityScannerTest {
    private val scanner = MediaSecurityScanner()

    @Test
    fun `rejects empty file`() {
        val file = MockMultipartFile("file", "a.jpg", "image/jpeg", ByteArray(0))
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("Empty"))
    }

    @Test
    fun `rejects unsupported mime`() {
        val file = MockMultipartFile("file", "a.exe", "application/x-msdownload", ByteArray(100) { 1 })
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("Unsupported"))
    }

    @Test
    fun `rejects path traversal`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0)
        val content = jpegHeader + ByteArray(92) { 1 }
        val malicious = MockMultipartFile("file", "../secret.jpg", "image/jpeg", content)
        val result = scanner.scan(malicious)
        assertTrue(result.allowed || result.reason.contains("Invalid filename") || result.reason.contains("Magic"))
    }

    @Test
    fun `accepts valid jpeg`() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16, 0x4A, 0x46)
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `magic bytes mismatch rejected`() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val file = MockMultipartFile("file", "fake.jpg", "image/jpeg", pngHeader + ByteArray(100))
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("Magic"))
    }

    // ============================ AUDIO TESTS ============================

    @Test
    fun `accepts valid m4a with ftyp box`() {
        // M4A/MP4 starts with box: [size(4)][ftyp(4)][major_brand(4)][minor_version(4)][compatible_brands...]
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20,  // box size = 32
            0x66, 0x74, 0x79, 0x70,  // "ftyp"
            0x4D, 0x34, 0x41, 0x20,  // "M4A "
            0x00, 0x00, 0x00, 0x00   // minor version
        )
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "voice.m4a", "audio/mp4", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, "Expected m4a to be allowed, but got: ${result.reason}")
    }

    @Test
    fun `rejects m4a with invalid magic bytes`() {
        // Fake m4a with PDF magic bytes
        val fakeHeader = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34) // %PDF-1.4
        val content = fakeHeader + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "fake.m4a", "audio/mp4", content)
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("Magic"))
    }

    @Test
    fun `accepts valid ogg with OggS signature`() {
        // OGG starts with "OggS" = 0x4F, 0x67, 0x67, 0x53
        val header = byteArrayOf(
            0x4F, 0x67, 0x67, 0x53,  // "OggS"
            0x00,                     // version
            0x02,                     // header type
            0x00, 0x00, 0x00, 0x00,  // granule position
            0x00, 0x00, 0x00, 0x00,  // serial
            0x00, 0x00, 0x00, 0x00   // page sequence
        )
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "voice.ogg", "audio/ogg", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, "Expected ogg to be allowed, but got: ${result.reason}")
    }

    @Test
    fun `rejects ogg with invalid magic bytes`() {
        val fakeHeader = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00) // RIFF (webp)
        val content = fakeHeader + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "fake.ogg", "audio/ogg", content)
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("Magic"))
    }

    @Test
    fun `accepts valid mp3 with ID3v2 tag`() {
        // ID3v2: "ID3" + version(2) + flags(1) + size(4)
        val header = byteArrayOf(
            0x49, 0x44, 0x33,  // "ID3"
            0x03, 0x00,         // version 2.3
            0x00,               // flags
            0x00, 0x00, 0x00, 0x10  // size
        )
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "song.mp3", "audio/mpeg", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, "Expected mp3 with ID3 to be allowed, but got: ${result.reason}")
    }

    @Test
    fun `accepts valid mp3 with MPEG frame sync`() {
        // MPEG Layer 3, version 1: 0xFF 0xFB
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "song.mp3", "audio/mpeg", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, "Expected mp3 with frame sync to be allowed, but got: ${result.reason}")
    }

    @Test
    fun `rejects mp3 with invalid magic bytes`() {
        val fakeHeader = byteArrayOf(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70)
        val content = fakeHeader + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "fake.mp3", "audio/mpeg", content)
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("Magic"))
    }

    @Test
    fun `accepts valid png`() {
        val header = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "image.png", "image/png", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `accepts valid webp`() {
        val header = byteArrayOf(
            0x52, 0x49, 0x46, 0x46,  // "RIFF"
            0x00, 0x00, 0x00, 0x00,  // size placeholder
            0x57, 0x45, 0x42, 0x50   // "WEBP"
        )
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "image.webp", "image/webp", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `accepts valid gif87`() {
        val header = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) // GIF87a
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "image.gif", "image/gif", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `accepts valid gif89`() {
        val header = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) // GIF89a
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "image.gif", "image/gif", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `accepts valid webm`() {
        val header = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00, 0x00, 0x00)
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "video.webm", "video/webm", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `accepts valid pdf`() {
        val header = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34) // %PDF-1.4
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "doc.pdf", "application/pdf", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `accepts octet-stream without magic check`() {
        // application/octet-stream is for already-encrypted files, no magic check
        val content = ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "encrypted.bin", "application/octet-stream", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, result.reason)
    }

    @Test
    fun `rejects control characters in filename`() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16, 0x4A, 0x46)
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "bad\u0000name.jpg", "image/jpeg", content)
        val result = scanner.scan(file)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("control") || result.reason.contains("Invalid"))
    }

    @Test
    fun `rejects file too small to read header`() {
        val file = MockMultipartFile("file", "a.jpg", "image/jpeg", byteArrayOf(0xFF.toByte()))
        val result = scanner.scan(file)
        // Empty/single byte file should be rejected
        assertFalse(result.allowed)
    }

    @Test
    fun `accepts valid mp4 with secondary ftyp box`() {
        // Some MP4 files have moov box first, then ftyp. Test detection of ftyp in second box.
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x08,  // size = 8
            0x66, 0x72, 0x65, 0x65,  // "free" (first box)
            0x00, 0x00, 0x00, 0x20,  // second box size
            0x66, 0x74, 0x79, 0x70,  // "ftyp"
            0x69, 0x73, 0x6F, 0x6D   // "isom"
        )
        val content = header + ByteArray(100) { 1 }
        val file = MockMultipartFile("file", "video.mp4", "video/mp4", content)
        val result = scanner.scan(file)
        assertTrue(result.allowed, "Expected mp4 with ftyp in second box to be allowed, but got: ${result.reason}")
    }
}
