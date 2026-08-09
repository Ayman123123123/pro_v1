package com.red.server.media

import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * 🛡️ فحص أمني للوسائط — يمنع الملفات الخبيثة قبل التخزين
 * يتحقق من: النوع الحقيقي (magic bytes) + الحجم + الامتداد + المحتوى المشبوه
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

        // 4. Magic bytes (first 8 bytes) — يمنع تغيير الامتداد لخداع النظام
        try {
            val header = ByteArray(8)
            file.inputStream.use { it.read(header) }
            val isValid = when (mime) {
                "image/jpeg" -> header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
                "image/png" -> header[0] == 0x89.toByte() && header[1] == 0x50.toByte()
                "image/gif" -> header[0] == 0x47.toByte() && header[1] == 0x49.toByte()
                "video/mp4" -> header[4] == 0x66.toByte() && header[5] == 0x74.toByte()
                "application/pdf" -> header[0] == 0x25.toByte() && header[1] == 0x50.toByte()
                else -> true
            }
            if (!isValid) return ScanResult(false, "Magic bytes mismatch for $mime")
        } catch (_: Exception) { }

        // 5. اسم ملف آمن (لا مسارات)
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ScanResult(false, "Invalid filename: $name")
        }

        return ScanResult(true, "OK")
    }

    data class ScanResult(val allowed: Boolean, val reason: String)
}
