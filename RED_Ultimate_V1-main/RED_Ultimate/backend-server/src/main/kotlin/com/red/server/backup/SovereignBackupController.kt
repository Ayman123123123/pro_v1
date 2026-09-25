package com.red.server.backup

import com.red.server.media.MediaService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

/**
 * ☁️ نسخ Sovereign السحابية — نقطة رفع اختيارية
 * يرفع الملف المشفر فقط (AES256-GCM) إلى MinIO عبر MediaService.
 * المفتاح يبقى في جهاز المستخدم — الخادم لا يستطيع فك التشفير.
 * المسار: POST /api/sovereign/backup/upload (multipart file + checksum)
 */
@RestController
@RequestMapping("/api/sovereign/backup")
class SovereignBackupController(
    private val media: MediaService,
    @Value("\${red.backup.max-size-mb:50}") private val maxSizeMb: Int,
    @Value("\${red.backup.bucket:red-backups}") private val bucket: String
) {
    private val log = LoggerFactory.getLogger(SovereignBackupController::class.java)

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "checksum", required = false) checksum: String?,
        @RequestParam(value = "createdAt", required = false) createdAt: String?,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        require(!file.isEmpty) { "Backup file is empty" }
        require(file.size <= maxSizeMb * 1024L * 1024L) { "Backup exceeds ${maxSizeMb}MB limit (got ${file.size / 1024}KB)" }
        // تحقق أساسي: الاسم يجب أن ينتهي بـ .enc والتوقيع اختياري
        val original = file.originalFilename ?: "red_backup.enc"
        require(original.endsWith(".enc", ignoreCase = true)) { "Backup must be encrypted (.enc)" }
        val normalizedChecksum = checksum?.trim()?.lowercase()
        if (!normalizedChecksum.isNullOrBlank()) {
            require(normalizedChecksum.matches(Regex("^[0-9a-f]{64}$"))) { "Invalid SHA-256 checksum" }
            // سلامة الوسائط: البصمة تُحسب على البايتات المستلمة فعلًا — عدم
            // التطابق = رفض بلا تخزين (fail-closed)، لا تحذير صامت.
            val actual = sha256Hex(file.bytes)
            require(actual == normalizedChecksum) { "Backup checksum mismatch" }
        }

        // مسار مخصص sovereign-backups/{userId}/{uuid}.enc — يحقق شرط verify
        // (المالكية بالبادئة) ويتجاوز رفض الماسح لامتداد .enc.
        val result = media.uploadBackup(userId, file)

        log.info("Sovereign backup uploaded: user={} key={} size={} checksum={}", userId, result.objectKey, file.size, normalizedChecksum?.take(12))

        return ResponseEntity.ok(mapOf(
            "id" to result.objectKey,
            "key" to result.objectKey,
            "url" to result.url,
            "size" to file.size,
            "checksum" to (checksum ?: ""),
            "createdAt" to (createdAt ?: Instant.now().toString()),
            "bucket" to bucket,
            "mimeType" to result.mimeType
        ))
    }

    @PostMapping("/verify")
    fun verify(
        @RequestParam("key") key: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        // المالكية بالبادئة: المسار الحالي sovereign-backups/{uid}/ + القديم
        // users/{uid}/ (توافق) — أي مفتاح آخر مرفوض قبل أي I/O.
        require(key.startsWith("sovereign-backups/$userId/") ||
            key.startsWith("users/$userId/")) { "Key does not belong to user" }
        val exists = try { media.metadata(key); true } catch (_: Exception) { false }
        return ResponseEntity.ok(mapOf("key" to key, "exists" to exists))
    }

    private fun sha256Hex(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
