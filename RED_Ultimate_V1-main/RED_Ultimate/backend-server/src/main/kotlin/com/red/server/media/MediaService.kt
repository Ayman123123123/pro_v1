package com.red.server.media

import com.red.server.social.UuidV7
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.File
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class MediaService(
    private val minio: MinioClient,
    @Value("\${red.minio.bucket}") private val bucket: String,
    private val scanner: MediaSecurityScanner? = null
) {
    private val log = LoggerFactory.getLogger(javaClass)
    @Synchronized
    fun ensureBucket() {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }

    fun upload(userId: UUID, file: MultipartFile): MediaObject {
        // 🛡️ فحص أمني قبل أي معالجة
        scanner?.scan(file)?.let { result ->
            require(result.allowed) { result.reason }
        }
        require(!file.isEmpty && file.size in 1..MAX_SIZE) { "Media file must contain 1 byte to 100 MiB" }
        val mime = file.contentType?.lowercase()?.substringBefore(';') ?: ""
        require(mime in ALLOWED) { "Unsupported media type" }
        ensureBucket()
        val extension = EXTENSIONS[mime] ?: "bin"
        val key = "users/$userId/${UuidV7.next()}.$extension"
        file.inputStream.use { input ->
            minio.putObject(PutObjectArgs.builder().bucket(bucket).`object`(key)
                .stream(input, file.size, -1).contentType(mime).build())
        }
        return MediaObject(key, mime, file.size, "/api/media/$key")
    }

    /**
     * 🔍 معاينة حقيقية 256x256 — صور عبر ImageIO، فيديو عبر ffmpeg frame at 1s
     * مع fallback placeholder إذا ffmpeg غير متوفر
     */
    fun generateThumbnail(key: String): String {
        validateKey(key)
        val meta = metadata(key)
        if (meta.mimeType.startsWith("image/")) {
            // handled below
        } else if (meta.mimeType.startsWith("video/")) {
            return generateVideoThumbnail(key)
        } else {
            return key
        }
        val thumbKey = "thumbs/$key"
        // If thumbnail already exists, reuse it
        if (exists(thumbKey)) return thumbKey
        // Generate 256x256 thumbnail via Java ImageIO (no external dependency)
        try {
            val tempFile = java.io.File.createTempFile("thumb-", ".jpg")
            try {
                minio.getObject(
                    io.minio.GetObjectArgs.builder().bucket(bucket).`object`(key).build()
                ).use { input ->
                    val original = javax.imageio.ImageIO.read(input) ?: return key
                    val thumb = scaleImage(original, 256)
                    javax.imageio.ImageIO.write(thumb, "jpg", tempFile)
                    tempFile.inputStream().use { thumbStream ->
                        minio.putObject(
                            io.minio.PutObjectArgs.builder().bucket(bucket).`object`(thumbKey)
                                .stream(thumbStream, tempFile.length(), -1)
                                .contentType("image/jpeg").build()
                        )
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (_: Exception) {
            // On any failure, fallback to original
            return key
        }
        return thumbKey
    }

    private fun scaleImage(src: java.awt.image.BufferedImage, maxSize: Int): java.awt.image.BufferedImage {
        val w = src.width
        val h = src.height
        val scale = maxSize.toDouble() / maxOf(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        val thumb = java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = thumb.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY)
        // Fill white background for transparent PNGs
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, nw, nh)
        g.drawImage(src, 0, 0, nw, nh, null)
        g.dispose()
        return thumb
    }

    private fun generateVideoThumbnail(key: String): String {
        val thumbKey = "thumbs/$key.jpg"
        if (exists(thumbKey)) return thumbKey
        // Try ffmpeg frame extraction at 1s
        try {
            val tmpVideo = File.createTempFile("vid-", ".mp4")
            val tmpThumb = File.createTempFile("vthumb-", ".jpg")
            try {
                minio.getObject(GetObjectArgs.builder().bucket(bucket).`object`(key).build()).use { input ->
                    tmpVideo.outputStream().use { input.copyTo(it) }
                }
                val ffmpeg = arrayOf("ffmpeg", "-y", "-i", tmpVideo.absolutePath, "-ss", "00:00:01", "-vframes", "1", "-vf", "scale=256:256:force_original_aspect_ratio=decrease,pad=256:256:(ow-iw)/2:(oh-ih)/2:color=white", tmpThumb.absolutePath)
                val proc = ProcessBuilder(*ffmpeg).redirectErrorStream(true).start()
                val finished = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                if (finished && proc.exitValue() == 0 && tmpThumb.exists() && tmpThumb.length() > 0) {
                    tmpThumb.inputStream().use { thumbStream ->
                        minio.putObject(PutObjectArgs.builder().bucket(bucket).`object`(thumbKey).stream(thumbStream, tmpThumb.length(), -1).contentType("image/jpeg").build())
                    }
                    return thumbKey
                }
            } finally {
                tmpVideo.delete()
                tmpThumb.delete()
            }
        } catch (e: Exception) {
            log.debug("Failed extracting ffmpeg video thumbnail: {}", e.message)
        }
        // Fallback: generate placeholder 256x256 with play icon
        try {
            val placeholder = BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB)
            val g = placeholder.createGraphics()
            g.color = java.awt.Color(20, 30, 45)
            g.fillRect(0, 0, 256, 256)
            g.color = java.awt.Color(255, 255, 255)
            g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 48)
            val fm = g.fontMetrics
            val play = "▶"
            g.drawString(play, (256 - fm.stringWidth(play)) / 2, 130)
            g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 14)
            g.drawString("VIDEO", (256 - g.fontMetrics.stringWidth("VIDEO")) / 2, 170)
            g.dispose()
            val tmp = File.createTempFile("vplaceholder-", ".jpg")
            try {
                javax.imageio.ImageIO.write(placeholder, "jpg", tmp)
                tmp.inputStream().use { s ->
                    minio.putObject(PutObjectArgs.builder().bucket(bucket).`object`(thumbKey).stream(s, tmp.length(), -1).contentType("image/jpeg").build())
                }
                return thumbKey
            } finally { tmp.delete() }
        } catch (e: Exception) {
            log.debug("Failed writing placeholder video thumbnail: {}", e.message)
        }
        return key
    }

    // Kept for backward compat
    fun generateThumbnailPlaceholder(key: String): String = generateThumbnail(key)

    /**
     * 🧹 تنظيف الملفات اليتيمة — يحذف كائنات MinIO بدون مرجع في MongoDB
     * يُستدعى يومياً عبر @Scheduled — يفحص stories/posts و media_grants
     */
    fun findOrphanKeys(allKeys: List<String>, referencedKeys: Set<String>): List<String> {
        return allKeys.filter { it !in referencedKeys }
    }

    fun listAllKeys(limit: Int = 500000): List<String> =
        listAllKeys(limit, Duration.ofSeconds(120))

    /**
     * سرد كامل مُرقَّم (بلا maxKeys مبتور): الـ Iterable يقلّب كل الصفحات
     * تلقائيًا حتى النفاد. [limit] سقف أمان فقط، و[timeout] يوقف السرد
     * المبكر بلا حذف (fail-closed عند المتصل).
     */
    fun listAllKeys(limit: Int, timeout: Duration): List<String> {
        ensureBucket()
        val deadline = Instant.now().plus(timeout.let { if (it.isNegative) Duration.ZERO else it })
        val keys = mutableListOf<String>()
        val cap = limit.coerceAtLeast(1)
        val result = minio.listObjects(
            io.minio.ListObjectsArgs.builder().bucket(bucket).recursive(true).build()
        )
        for (item in result) {
            if (Instant.now().isAfter(deadline)) {
                log.warn("Object listing TIMEOUT after {} keys — stopping scan early", keys.size)
                break
            }
            // minio Result<T> exposes only get() (throws ErrorResponseException) — no getOrNull.
            val obj = runCatching { item.get() }.getOrNull() ?: continue
            keys += obj.objectName()
            if (keys.size >= cap) {
                log.warn("Object listing capped at safety limit {} keys", cap)
                break
            }
        }
        return keys
    }

    fun deleteOrphans(referencedKeys: Set<String>, dryRun: Boolean = true): List<String> {
        return deleteOrphans(referencedKeys, dryRun, Duration.ofDays(DEFAULT_ORPHAN_GRACE_DAYS))
    }

    fun deleteOrphans(
        referencedKeys: Set<String>,
        dryRun: Boolean = true,
        gracePeriod: Duration
    ): List<String> = deleteOrphans(
        referencedKeys, dryRun, gracePeriod, Duration.ofSeconds(DEFAULT_ORPHAN_TIMEOUT_SECONDS)
    )

    /**
     * 🧹 حذف المرشّحين الأيتام مع حماية fail-closed:
     * - dryRun=true (الافتراضي الآمن): معاينة فقط — يُسجَّل المرشّحون ولا يُحذف شيء،
     *   وتُعاد قائمة المرشّحين للمراجعة.
     * - فترة سماح: أي كائن أحدث من [gracePeriod] (رفع جارٍ أو شظايا .partN أو
     *   وسائط لم تُربط مراجعها بعد) يُحمى ولا يُحذف.
     * - عمر مجهول (فشل stat): يُحمى ولا يُحذف أبدًا — الشكّ لصالح البقاء.
     * - مهلة [timeout]: انتهاؤها يوقف stat/الحذف فورًا؛ ما جُمع قبلها يُعاد
     *   كمعاينة (dryRun) أو كمحذوف فعلي جزئي — ولا حذف بعد المهلة أبدًا.
     * - dryRun=false: يُحذف فقط المرشّحون القدامى ذوو العمر المؤكد، وتُعاد
     *   قائمة المحذوف فعلًا.
     */
    fun deleteOrphans(
        referencedKeys: Set<String>,
        dryRun: Boolean = true,
        gracePeriod: Duration,
        timeout: Duration
    ): List<String> {
        val deadline = Instant.now().plus(timeout.let { if (it.isNegative) Duration.ZERO else it })
        return deleteOrphans(referencedKeys, dryRun, gracePeriod, deadline)
    }

    fun deleteOrphans(
        referencedKeys: Set<String>,
        dryRun: Boolean = true,
        gracePeriod: Duration,
        deadline: Instant
    ): List<String> {
        val all = listAllKeys(deadline = deadline)
        val orphans = findOrphanKeys(all, referencedKeys)
        val now = Instant.now()
        val deletable = mutableListOf<String>()
        var freshKept = 0
        var unknownKept = 0
        var timedOut = false
        orphans.forEach { key ->
            if (Instant.now().isAfter(deadline)) {
                timedOut = true
                return@forEach
            }
            val modified = objectLastModified(key)
            when {
                modified == null -> unknownKept++ // fail-closed: العمر المجهول = حماية
                Duration.between(modified, now) < gracePeriod -> freshKept++
                else -> deletable += key
            }
        }
        if (timedOut) {
            log.warn(
                "Orphan cleanup TIMEOUT — stopping early ({} orphans scanned, {} deletable so far held for review)",
                orphans.size, deletable.size
            )
            // قبل أي حذف: المعاينة تُعاد للمراجعة، والوضع الحقيقي يعيد فارغًا (لا شيء حُذف).
            return if (dryRun) deletable else emptyList()
        }
        log.warn(
            "Orphan preview: {} deletable candidates ({} orphans total, {} fresh<{}d kept, {} unknown-age kept). First 10: {}",
            deletable.size, orphans.size, freshKept, gracePeriod.toDays(), unknownKept, deletable.take(10)
        )
        if (dryRun) {
            log.warn("DRY-RUN — no objects deleted ({} candidates held for review)", deletable.size)
            return deletable
        }
        val deleted = mutableListOf<String>()
        deletable.forEach { key ->
            if (Instant.now().isAfter(deadline)) {
                log.warn("Orphan cleanup TIMEOUT during deletion — stopping ({} deleted so far)", deleted.size)
                return deleted
            }
            try {
                minio.removeObject(io.minio.RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
                deleted += key
            } catch (e: Exception) {
                log.warn("Failed removing orphan key {}: {}", key, e.message)
            }
        }
        return deleted
    }

    private fun listAllKeys(deadline: Instant, limit: Int = 500000): List<String> {
        val remaining = Duration.between(Instant.now(), deadline).let { if (it.isNegative) Duration.ZERO else it }
        return listAllKeys(limit, remaining)
    }

    /**
     * آخر تعديل للكائن عبر stat — null عند أي فشل (كائن مفقود/خطأ شبكة).
     * النوع المُعاد من MinIO (ZonedDateTime) لا يُذكَر صراحةً عمدًا.
     */
    fun objectLastModified(key: String): Instant? = runCatching {
        minio.statObject(StatObjectArgs.builder().bucket(bucket).`object`(key).build())
            .lastModified()?.toInstant()
    }.getOrNull()

    fun scheduleOrphanCleanup() {
        // Placeholder for @Scheduled — real logic is in OrphanCleanupScheduler which queries MongoDB
    }

    fun exists(key: String): Boolean = runCatching {
        validateKey(key); ensureBucket(); minio.statObject(StatObjectArgs.builder().bucket(bucket).`object`(key).build()); true
    }.getOrDefault(false)

    fun metadata(key: String): MediaMetadata {
        validateKey(key); ensureBucket()
        val stat = minio.statObject(StatObjectArgs.builder().bucket(bucket).`object`(key).build())
        return MediaMetadata(stat.contentType() ?: "application/octet-stream", stat.size())
    }

    fun stream(key: String, output: OutputStream) {
        validateKey(key); ensureBucket()
        minio.getObject(GetObjectArgs.builder().bucket(bucket).`object`(key).build()).use { it.copyTo(output) }
    }

    fun delete(key: String) {
        validateKey(key); minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
    }

    private fun validateKey(key: String) {
        require(key.isNotBlank() && key.length <= 512) { "Invalid media key" }
        require(!key.contains("..") && !key.contains("\\") && !key.startsWith("/") && key.none { it.code < 0x20 }) { "Invalid media key" }
        val ok = key.matches(
            Regex("^(?:users/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9_.-]{0,127}|thumbs/(?:users/[0-9a-fA-F-]{36}/)?[A-Za-z0-9][A-Za-z0-9_.-]{0,191}|sovereign-backups/[0-9a-fA-F-]{36}/[A-Za-z0-9][A-Za-z0-9_.-]{0,127})$")
        )
        require(ok) { "Invalid media key" }
    }

    companion object {
        const val MAX_SIZE = 100L * 1024 * 1024
        /** فترة السماح الافتراضية قبل اعتبار كائن يتيم قابلًا للحذف (أيام). */
        const val DEFAULT_ORPHAN_GRACE_DAYS = 7L
        /** المهلة الافتراضية لدورة التنظيف (ثوانٍ) — توقف مبكر بلا حذف لاحق. */
        const val DEFAULT_ORPHAN_TIMEOUT_SECONDS = 300L
        val ALLOWED = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "application/pdf", "application/octet-stream")
        val EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif", "video/mp4" to "mp4", "video/webm" to "webm", "audio/ogg" to "ogg", "audio/mp4" to "m4a", "audio/mpeg" to "mp3", "application/pdf" to "pdf", "application/octet-stream" to "bin")
    }
}

data class MediaObject(val objectKey: String, val mimeType: String, val size: Long, val url: String)
data class MediaMetadata(val mimeType: String, val size: Long)
