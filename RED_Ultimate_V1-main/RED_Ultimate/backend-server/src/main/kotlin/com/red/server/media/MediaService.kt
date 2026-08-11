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

    fun listAllKeys(limit: Int = 10000): List<String> {
        ensureBucket()
        val keys = mutableListOf<String>()
        var result = minio.listObjects(
            io.minio.ListObjectsArgs.builder().bucket(bucket).maxKeys(limit).build()
        )
        for (item in result) {
            // minio Result<T> exposes only get() (throws ErrorResponseException) — no getOrNull.
            val obj = runCatching { item.get() }.getOrNull() ?: continue
            keys += obj.objectName()
            if (keys.size >= limit) break
        }
        return keys
    }

    fun deleteOrphans(referencedKeys: Set<String>, dryRun: Boolean = true): List<String> {
        val all = listAllKeys()
        val orphans = findOrphanKeys(all, referencedKeys)
        if (!dryRun) {
            orphans.forEach { key ->
                try {
                    minio.removeObject(io.minio.RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
                } catch (e: Exception) {
                    log.warn("Failed removing orphan key {}: {}", key, e.message)
                }
            }
        }
        return orphans
    }

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
        require(key.matches(Regex("^(?:users|thumbs/users)/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}\\.[a-z0-9]{2,5}$"))) { "Invalid media key" }
    }

    companion object {
        const val MAX_SIZE = 100L * 1024 * 1024
        val ALLOWED = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "application/pdf", "application/octet-stream")
        val EXTENSIONS = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif", "video/mp4" to "mp4", "video/webm" to "webm", "audio/ogg" to "ogg", "audio/mp4" to "m4a", "audio/mpeg" to "mp3", "application/pdf" to "pdf", "application/octet-stream" to "bin")
    }
}

data class MediaObject(val objectKey: String, val mimeType: String, val size: Long, val url: String)
data class MediaMetadata(val mimeType: String, val size: Long)
