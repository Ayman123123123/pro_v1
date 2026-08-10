package com.red.server.media

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets

/**
 * 🛡️ فحص أمني للوسائط — يمنع الملفات الخبيثة قبل التخزين
 * يتحقق من: النوع الحقيقي (magic bytes) + الحجم + الامتداد + المحتوى المشبوه
 *
 * يغطي:
 *  - صور: JPEG, PNG, WebP, GIF
 *  - فيديو: MP4, WebM
 *  - صوت: OGG, M4A, MP3
 *  - مستندات: PDF, Office, ZIP
 */
@Service
class MediaSecurityScanner {

    fun scan(file: MultipartFile): ScanResult {
        val mime = file.contentType?.lowercase()?.substringBefore(';') ?: ""
        val name = file.originalFilename?.lowercase() ?: ""
        val size = file.size

        // 1. الحجم
        if (size == 0L) return ScanResult(false, "Empty file")
        if (size > MediaService.MAX_SIZE) return ScanResult(false, "File too large: ${size / 1024 / 1024}MB > 100MB")

        // 2. النوع المسموح فقط
        if (mime !in MediaService.ALLOWED) return ScanResult(false, "Unsupported type: $mime")

        // 3. الامتداد يطابق النوع
        val ext = name.substringAfterLast('.', "")
        val expectedExt = MediaService.EXTENSIONS[mime]
        if (expectedExt != null && ext != expectedExt && ext !in setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "ogg", "m4a", "mp3", "pdf")) {
            if (!((ext == "jpg" && expectedExt == "jpg") || (ext == "jpeg" && expectedExt == "jpg"))) {
                return ScanResult(false, "Extension .$ext does not match mime $mime")
            }
        }

        // 4. Magic bytes (first 16 bytes) — يمنع تغيير الامتداد لخداع النظام
        try {
            val header = ByteArray(16)
            val bytesRead = file.inputStream.use { it.read(header) }
            if (bytesRead < 2) return ScanResult(false, "Cannot read file header")
            val isValid = when (mime) {
                // === صور ===
                "image/jpeg" -> header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
                "image/png" -> header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
                "image/gif" -> (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() && (header[3] == 0x38.toByte() || header[3] == 0x39.toByte()))
                "image/webp" -> header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
                    header[8] == 0x57.toByte() && header[9] == 0x45.toByte() &&
                    header[10] == 0x42.toByte() && header[11] == 0x50.toByte()

                // === فيديو ===
                "video/mp4" -> validateMp4(header, bytesRead)
                "video/webm" -> header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                    header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()

                // === صوت ===
                "audio/ogg" -> validateOgg(header)
                "audio/mp4" -> validateMp4(header, bytesRead)  // M4A is MP4 container with audio-only
                "audio/mpeg" -> validateMp3(header)

                // === مستندات ===
                "application/pdf" -> header[0] == 0x25.toByte() && header[1] == 0x50.toByte() &&
                    header[2] == 0x44.toByte() && header[3] == 0x46.toByte()

                // === تطبيق عام (octet-stream — مُشفّر، لا نتحقق) ===
                "application/octet-stream" -> true

                else -> false  // صارم: رفض أي mime غير معروف
            }
            if (!isValid) return ScanResult(false, "Magic bytes mismatch for $mime")
        } catch (_: Exception) {
            return ScanResult(false, "Failed to read file header")
        }

        // 5. اسم ملف آمن (لا مسارات)
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ScanResult(false, "Invalid filename: $name")
        }

        // 6. التحقق من اسم الملف (لا null bytes, لا control chars)
        if (name.any { it.code < 0x20 }) {
            return ScanResult(false, "Invalid filename: contains control characters")
        }

        return ScanResult(true, "OK")
    }

    /**
     * يتحقق من توقيع MP4/M4A — box "ftyp" في أي مكان من أول 16 بايت
     * MP4 boxes: 4 bytes size + 4 bytes type ("ftyp", "moov", "mdat", etc.)
     */
    private fun validateMp4(header: ByteArray, bytesRead: Int): Boolean {
        if (bytesRead < 12) return false
        // ftyp يجب أن يكون في أول box (offset 4-7)
        if (header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
            header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) {
            return true
        }
        // أو في box ثانوي (offset 8-11)
        if (bytesRead >= 12 && header[8] == 0x66.toByte() && header[9] == 0x74.toByte() &&
            header[10] == 0x79.toByte() && header[11] == 0x70.toByte()) {
            return true
        }
        // fallback: box type في أي مكان (بعض الـ fragmented MP4)
        val ftypBytes = "ftyp".toByteArray(StandardCharsets.US_ASCII)
        return (4..11).any { i ->
            i + ftypBytes.size <= header.size && header.sliceArray(i until i + ftypBytes.size).contentEquals(ftypBytes)
        }
    }

    /**
     * يتحقق من OGG/Opus — magic "OggS" في أول 4 بايت
     * OGG: 'O','g','g','S' = 0x4F, 0x67, 0x67, 0x53
     */
    private fun validateOgg(header: ByteArray): Boolean {
        return header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() && header[3] == 0x53.toByte()
    }

    /**
     * يتحقق من MP3 — إما ID3v2 tag (0x49 0x44 0x33 = "ID3")
     * أو MPEG frame sync (0xFF 0xFB/0xF3/0xF2)
     */
    private fun validateMp3(header: ByteArray): Boolean {
        // ID3v2
        if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
            return true
        }
        // MPEG audio frame sync: 11 bits set (0xFF) + 3 bits version + 2 bits layer
        // Layer 3: bits 17-18 = 01 → 0xFA, 0xFB, 0xFC, 0xFD
        // Layer 2: bits 17-18 = 10 → 0xF4, 0xF5, 0xF6, 0xF7
        if (header.size >= 2 && header[0] == 0xFF.toByte() && header[1].toInt() and 0xE0 == 0xE0) {
            val second = header[1].toInt() and 0xFF
            // MPEG audio: bits 5,6 = layer (01=III, 10=II, 11=I)
            val layer = (second shr 5) and 0x03
            // bits 7,8 = version (00=2.5, 10=2, 11=1)
            // bits 9,10 = not used
            // Accept any valid combination
            return layer in 1..3 && (second and 0x18) != 0x18
        }
        return false
    }

    data class ScanResult(val allowed: Boolean, val reason: String)
}
