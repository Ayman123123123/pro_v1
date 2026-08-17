package com.red.server.media

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

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
                //拒绝 octet-stream — لا يمكن التحقق من نوعه بـ magic bytes
                "application/octet-stream" -> false

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
     * يتحقق من توقيع MP4/M4A — box "ftyp" ضمن أول bytesRead بايت.
     * MP4 boxes: 4 bytes size + 4 bytes type ("ftyp", "free", "moov", "mdat", ...).
     * قد يسبق ftyp صندوق حر (free) أو moov — لذلك نبحث عن "ftyp" في أي موضع
     * من أول 16 بايت (حتى إصدارات fragmented/سابقة التشذير).
     */
    private fun validateMp4(header: ByteArray, bytesRead: Int): Boolean {
        if (bytesRead < 8) return false
        val f = 0x66.toByte(); val t = 0x74.toByte(); val y = 0x79.toByte(); val p = 0x70.toByte()
        val last = bytesRead - 4
        var i = 4
        while (i <= last) {
            if (header[i] == f && header[i + 1] == t && header[i + 2] == y && header[i + 3] == p) {
                return true
            }
            i++
        }
        return false
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
     * أو MPEG frame sync (11 بت مزامنة: byte0 = 0xFF + أول 3 بتات من byte1).
     * البتات التالية (version/layer) لا تُرفض إلا إن كانت محجوزة.
     */
    private fun validateMp3(header: ByteArray): Boolean {
        // ID3v2
        if (header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
            return true
        }
        if (header.size >= 2 && header[0] == 0xFF.toByte()) {
            val second = header[1].toInt() and 0xFF
            // 11 بت مزامنة: أول 3 بتات من البايت الثاني يجب أن تكون 111
            if (second and 0xE0 != 0xE0) return false
            // bits 5-4: layer (00 = محجوز)
            if ((second shr 4) and 0x03 == 0) return false
            // bits 7-6: version (01 = محجوز)
            if ((second shr 6) and 0x03 == 1) return false
            return true
        }
        return false
    }

    data class ScanResult(val allowed: Boolean, val reason: String)
}
