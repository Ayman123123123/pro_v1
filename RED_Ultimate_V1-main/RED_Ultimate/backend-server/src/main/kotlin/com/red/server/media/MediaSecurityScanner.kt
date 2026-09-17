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
 *
 * السياسة الصارمة لـ application/octet-stream: لا قبول أعمى. الملفات المبهمة
 * (مشفرة فعلاً) تُقبل فقط معزولة — quarantine=true + bucket غير قابل للتنفيذ
 * ([QUARANTINE_BUCKET]: بدون قراءة عمومية، Content-Disposition: attachment،
 * X-Content-Type-Options: nosniff، لا عرض inline أبداً) + rescanRequired=true
 * لإعادة الفحص (AV غير متزامن) قبل أي تقديم. أي محتوى تنفيذي/نشط أو نوع
 * معروف متنكر يُرفض. على المتصل احترام حقول [ScanResult.quarantine] /
 * [ScanResult.bucket] / [ScanResult.rescanRequired] عند التخزين.
 *
 * شاشات مشتركة لكل الأنواع: كشف SVG/script (ملفات متعددة الأشكال متنكرة
 * كصور/PDF) وأبعاد PNG/GIF ضد قنابل فك الضغط.
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

        // 4. نافذة فحص (64KB) — magic bytes + كشف المحتوى النشط + الأبعاد
        val sample = ByteArray(SCAN_WINDOW_BYTES)
        val bytesRead: Int
        try {
            bytesRead = file.inputStream.use { stream ->
                var total = 0
                while (total < sample.size) {
                    val n = stream.read(sample, total, sample.size - total)
                    if (n <= 0) break
                    total += n
                }
                total
            }
            if (bytesRead < 2) return ScanResult(false, "Cannot read file header")
        } catch (_: Exception) {
            return ScanResult(false, "Failed to read file header")
        }

        // 5. octet-stream: سياسة صارمة (عزل + إعادة فحص) بدل القبول الأعمى
        if (mime == "application/octet-stream") {
            val strict = scanOctetStreamStrict(sample, bytesRead)
            if (!strict.allowed) return strict
            // مبهم فعلاً — أكمل فحوص اسم الملف ثم أعد نتيجة العزل كما هي
            if (name.contains("..") || name.contains("/") || name.contains("\\")) {
                return ScanResult(false, "Invalid filename: $name")
            }
            if (name.any { it.code < 0x20 }) {
                return ScanResult(false, "Invalid filename: contains control characters")
            }
            return strict
        }

        // 6. Magic bytes — يمنع تغيير الامتداد لخداع النظام
        val isValid = when (mime) {
            // === صور ===
            "image/jpeg" -> sample[0] == 0xFF.toByte() && sample[1] == 0xD8.toByte()
            "image/png" -> bytesRead >= 4 && sample[0] == 0x89.toByte() && sample[1] == 0x50.toByte() &&
                sample[2] == 0x4E.toByte() && sample[3] == 0x47.toByte()
            "image/gif" -> (sample[0] == 0x47.toByte() && sample[1] == 0x49.toByte() &&
                sample[2] == 0x46.toByte() && (sample[3] == 0x38.toByte() || sample[3] == 0x39.toByte()))
            "image/webp" -> bytesRead >= 12 && sample[0] == 0x52.toByte() && sample[1] == 0x49.toByte() &&
                sample[2] == 0x46.toByte() && sample[3] == 0x46.toByte() &&
                sample[8] == 0x57.toByte() && sample[9] == 0x45.toByte() &&
                sample[10] == 0x42.toByte() && sample[11] == 0x50.toByte()

            // === فيديو ===
            "video/mp4" -> hasMp4Ftyp(sample, bytesRead)
            "video/webm" -> bytesRead >= 4 && sample[0] == 0x1A.toByte() && sample[1] == 0x45.toByte() &&
                sample[2] == 0xDF.toByte() && sample[3] == 0xA3.toByte()

            // === صوت ===
            "audio/ogg" -> validateOgg(sample)
            "audio/mp4" -> hasMp4Ftyp(sample, bytesRead)  // M4A is MP4 container with audio-only
            "audio/mpeg" -> validateMp3(sample)

            // === مستندات ===
            "application/pdf" -> bytesRead >= 4 && sample[0] == 0x25.toByte() && sample[1] == 0x50.toByte() &&
                sample[2] == 0x44.toByte() && sample[3] == 0x46.toByte()

            else -> false  // صارم: رفض أي mime غير معروف
        }
        if (!isValid) return ScanResult(false, "Magic bytes mismatch for $mime")

        // 7. أبعاد PNG/GIF ضد قنابل فك الضغط (أبعاد معلنة ضخمة بملف صغير)
        checkImageDimensions(mime, sample, bytesRead)?.let { return it }

        // 8. المحتوى النشط: SVG/script متنكر + JavaScript مضمن في PDF
        findActiveContentMarker(mime, sample, bytesRead)?.let { marker ->
            return ScanResult(false, "Active content rejected in $mime: $marker")
        }

        // 9. اسم ملف آمن (لا مسارات)
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ScanResult(false, "Invalid filename: $name")
        }

        // 10. التحقق من اسم الملف (لا null bytes, لا control chars)
        if (name.any { it.code < 0x20 }) {
            return ScanResult(false, "Invalid filename: contains control characters")
        }

        return ScanResult(true, "OK")
    }

    /**
     * سياسة octet-stream الصارمة: رفض التنفيذيات والنصوص النشطة والأرشيفات
     * والأنواع المعروفة المتنكرة (يجب إعادة الرفع بالنوع الحقيقي ليمر بسياسته
     * الكاملة + إعادة فحص). البصمات المبهمة فعلاً تُقبل معزولة فقط.
     */
    private fun scanOctetStreamStrict(sample: ByteArray, bytesRead: Int): ScanResult {
        if (isExecutableHeader(sample, bytesRead)) {
            return ScanResult(false, "octet-stream rejected: executable header (non-executable bucket policy)")
        }
        findActiveContentMarker("application/octet-stream", sample, bytesRead)?.let { marker ->
            return ScanResult(false, "octet-stream rejected: active content ($marker)")
        }
        if (isZipHeader(sample, bytesRead)) {
            return ScanResult(false, "octet-stream rejected: archives must be declared, extracted and re-scanned")
        }
        sniffKnownMime(sample, bytesRead)?.let { realMime ->
            return ScanResult(
                false,
                "octet-stream rejected: content is $realMime — re-upload with the real content type (rescan required)",
                quarantine = true,
                rescanRequired = true
            )
        }
        return ScanResult(
            true,
            "OK (quarantined: opaque octet-stream — non-executable bucket, rescan required)",
            quarantine = true,
            bucket = QUARANTINE_BUCKET,
            rescanRequired = true
        )
    }

    /** رأس تنفيذي: MZ / ELF / Mach-O / fat / class / shebang. */
    private fun isExecutableHeader(s: ByteArray, len: Int): Boolean {
        if (len >= 2) {
            if (s[0] == 0x4D.toByte() && s[1] == 0x5A.toByte()) return true // MZ (PE/DOS)
            if (s[0] == 0x23.toByte() && s[1] == 0x21.toByte()) return true // #! script
        }
        if (len >= 4) {
            val b0 = s[0].toInt() and 0xFF
            val b1 = s[1].toInt() and 0xFF
            val b2 = s[2].toInt() and 0xFF
            val b3 = s[3].toInt() and 0xFF
            if (b0 == 0x7F && b1 == 0x45 && b2 == 0x4C && b3 == 0x46) return true // ELF
            if (b0 == 0xFE && b1 == 0xED && b2 == 0xFA && (b3 == 0xCE || b3 == 0xCF)) return true // Mach-O BE
            if ((b0 == 0xCE || b0 == 0xCF) && b1 == 0xFA && b2 == 0xED && b3 == 0xFE) return true // Mach-O LE
            if (b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE) return true // fat/class BE
            if (b0 == 0xBE && b1 == 0xBA && b2 == 0xFE && b3 == 0xCA) return true // fat LE
        }
        return false
    }

    private fun isZipHeader(s: ByteArray, len: Int): Boolean {
        if (len < 4) return false
        return s[0] == 0x50.toByte() && s[1] == 0x4B.toByte() &&
            ((s[2] == 0x03.toByte() && s[3] == 0x04.toByte()) ||
                (s[2] == 0x05.toByte() && s[3] == 0x06.toByte()) ||
                (s[2] == 0x07.toByte() && s[3] == 0x08.toByte()))
    }

    /** يستنشق النوع الحقيقي المتنكر كـ octet-stream (من الأنواع المسموحة فقط). */
    private fun sniffKnownMime(s: ByteArray, len: Int): String? {
        if (len >= 2 && s[0] == 0xFF.toByte() && s[1] == 0xD8.toByte()) return "image/jpeg"
        if (len >= 4 && s[0] == 0x89.toByte() && s[1] == 0x50.toByte() &&
            s[2] == 0x4E.toByte() && s[3] == 0x47.toByte()) return "image/png"
        if (len >= 4 && s[0] == 0x47.toByte() && s[1] == 0x49.toByte() &&
            s[2] == 0x46.toByte() && (s[3] == 0x38.toByte() || s[3] == 0x39.toByte())) return "image/gif"
        if (len >= 12 && s[0] == 0x52.toByte() && s[1] == 0x49.toByte() &&
            s[2] == 0x46.toByte() && s[3] == 0x46.toByte() &&
            s[8] == 0x57.toByte() && s[9] == 0x45.toByte() &&
            s[10] == 0x42.toByte() && s[11] == 0x50.toByte()) return "image/webp"
        if (len >= 4 && s[0] == 0x1A.toByte() && s[1] == 0x45.toByte() &&
            s[2] == 0xDF.toByte() && s[3] == 0xA3.toByte()) return "video/webm"
        if (hasMp4Ftyp(s, len)) return "video/mp4"
        if (validateOgg(s)) return "audio/ogg"
        if (validateMp3(s)) return "audio/mpeg"
        if (len >= 4 && s[0] == 0x25.toByte() && s[1] == 0x50.toByte() &&
            s[2] == 0x44.toByte() && s[3] == 0x46.toByte()) return "application/pdf"
        return null
    }

    /**
     * كشف المحتوى النشط (SVG/HTML/XML/script) — يصطاد الملفات متعددة الأشكال
     * (polyglot) المتنكرة كصور أو PDF أو blobs مبهمة. يُستثنى "<?xml" في PDF
     * لأن بيانات XMP المشروعة تبدأ به.
     */
    private fun findActiveContentMarker(mime: String, s: ByteArray, len: Int): String? {
        if (len <= 0) return null
        val text = String(s, 0, minOf(len, SCAN_WINDOW_BYTES), Charsets.ISO_8859_1).lowercase()
        for (marker in SCRIPT_MARKERS) {
            if (marker == "<?xml" && mime == "application/pdf") continue // XMP مشروعة
            if (text.contains(marker)) return marker
        }
        if (mime == "application/pdf" || mime == "application/octet-stream") {
            for (marker in PDF_ACTIVE_MARKERS) {
                if (text.contains(marker)) return marker
            }
        }
        return null
    }

    /** أبعاد PNG/GIF ضد قنابل فك الضغط — null تعني سليم. */
    private fun checkImageDimensions(mime: String, s: ByteArray, len: Int): ScanResult? = when (mime) {
        "image/png" -> checkPngDimensions(s, len)
        "image/gif" -> checkGifDimensions(s, len)
        else -> null
    }

    private fun checkPngDimensions(s: ByteArray, len: Int): ScanResult? {
        if (len < 24) return ScanResult(false, "Truncated PNG: missing IHDR")
        val sigOk = s[0] == 0x89.toByte() && s[1] == 0x50.toByte() && s[2] == 0x4E.toByte() &&
            s[3] == 0x47.toByte() && s[4] == 0x0D.toByte() && s[5] == 0x0A.toByte() &&
            s[6] == 0x1A.toByte() && s[7] == 0x0A.toByte()
        if (!sigOk) return ScanResult(false, "Corrupt PNG signature")
        if (!(s[12] == 0x49.toByte() && s[13] == 0x48.toByte() &&
                s[14] == 0x44.toByte() && s[15] == 0x52.toByte())) {
            return ScanResult(false, "Corrupt PNG: first chunk is not IHDR")
        }
        val w = u32BE(s, 16)
        val h = u32BE(s, 20)
        return checkDimensions("PNG", w, h)
    }

    private fun checkGifDimensions(s: ByteArray, len: Int): ScanResult? {
        if (len < 10) return ScanResult(false, "Truncated GIF: missing screen descriptor")
        val w = u16LE(s, 6)
        val h = u16LE(s, 8)
        return checkDimensions("GIF", w, h)
    }

    private fun checkDimensions(kind: String, w: Long, h: Long): ScanResult? {
        if (w <= 0 || h <= 0) return ScanResult(false, "Invalid $kind dimensions: ${w}x$h")
        if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
            return ScanResult(false, "$kind dimensions ${w}x$h exceed ${MAX_IMAGE_DIMENSION}px (decompression-bomb guard)")
        }
        if (w * h > MAX_IMAGE_PIXELS) {
            return ScanResult(false, "$kind pixel count ${w * h} exceeds $MAX_IMAGE_PIXELS (decompression-bomb guard)")
        }
        return null
    }

    private fun u32BE(s: ByteArray, off: Int): Long =
        ((s[off].toLong() and 0xFF) shl 24) or
            ((s[off + 1].toLong() and 0xFF) shl 16) or
            ((s[off + 2].toLong() and 0xFF) shl 8) or
            (s[off + 3].toLong() and 0xFF)

    private fun u16LE(s: ByteArray, off: Int): Long =
        (s[off].toLong() and 0xFF) or ((s[off + 1].toLong() and 0xFF) shl 8)

    /**
     * يتحقق من توقيع MP4/M4A — box "ftyp" في أي مكان من أول 16 بايت
     * MP4 boxes: 4 bytes size + 4 bytes type ("ftyp", "moov", "mdat", etc.)
     */
    private fun validateMp4(header: ByteArray, bytesRead: Int): Boolean = hasMp4Ftyp(header, bytesRead)

    /** بحث ftyp مشترك بين التحقق والاستنشاق (نفس السلوك السابق تماماً). */
    private fun hasMp4Ftyp(header: ByteArray, bytesRead: Int): Boolean {
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
        // fallback: box type في أي موضع ضمن أول 16 بايتًا (بعض الملفات
        // تبدأ بـ box مثل "free" قبل ftyp — البحث السابق توقف عند 8
        // فأفوت ftyp في offset 12)
        val f = 0x66.toByte(); val t = 0x74.toByte(); val y = 0x79.toByte(); val p = 0x70.toByte()
        val limit = minOf(bytesRead, header.size, 16) - 4
        for (i in 4..limit) {
            if (header[i] == f && header[i + 1] == t && header[i + 2] == y && header[i + 3] == p) {
                return true
            }
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
            // bits 3,4 = version (11=MPEG-1، 10=MPEG-2، 00=MPEG-2.5،
            // 01=محجوز/غير صالح). كان الشرط يرفض 0x18 (MPEG-1) — أي
            // يرفض 0xFB أكثر صيغ MP3 شيوعًا — بدل رفض المحجوزة 0x08.
            return layer in 1..3 && (second and 0x18) != 0x08
        }
        return false
    }

    companion object {
        /** bucket العزل للـ blobs المبهمة: خاص، غير قابل للتنفيذ، لا عرض inline. */
        const val QUARANTINE_BUCKET = "red-media-quarantine"

        /** نافذة الفحص من بداية الملف (تكفي للـ magic + كشف النصوص + IHDR). */
        const val SCAN_WINDOW_BYTES = 64 * 1024

        /** حد أبعاد الصور وأقصى عدد بكسلات ضد قنابل فك الضغط. */
        const val MAX_IMAGE_DIMENSION = 8192
        const val MAX_IMAGE_PIXELS = 33_554_432L // 32MP

        private val SCRIPT_MARKERS = listOf(
            "<svg", "<script", "</script", "javascript:", "onload=", "onerror=",
            "onfocus=", "<html", "<!doctype html", "<!doctype svg", "<!entity",
            "<?xml", "<?php", "<%", "%3cscript", "\\u003cscript", "\\x3csvg",
            "data:text/html", "<iframe", "<object", "<embed",
            "eval(", "fromcharcode", "document.cookie"
        )
        private val PDF_ACTIVE_MARKERS = listOf(
            "/javascript", "/launch", "/embeddedfile", "/openaction"
        )
    }

    data class ScanResult(
        val allowed: Boolean,
        val reason: String,
        /** عزل في bucket غير قابل للتنفيذ (octet-stream المبهمة). */
        val quarantine: Boolean = false,
        /** الـ bucket المستهدف عند العزل، null تعني المسار الطبيعي. */
        val bucket: String? = null,
        /** يتطلب إعادة فحص (AV غير متزامن) قبل أي تقديم. */
        val rescanRequired: Boolean = false
    )
}
