package com.red.server.media.v1

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaService
import jakarta.validation.Valid
import com.red.server.media.MediaMetadata
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStream
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
class MediaV1Controller(
    private val media: MediaService,
    private val users: UserAccountRepository
) {

    @PostMapping("/upload/initiate")
    fun initiateUpload(
        @Valid @RequestBody request: InitiateUploadRequest,
        authentication: Authentication
    ): ResponseEntity<InitiateUploadResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        require(request.totalSize in 1..MAX_UPLOAD_SIZE) { "INVALID_FILE_SIZE" }
        require(request.mimeType in ALLOWED_MIME_TYPES) { "UNSUPPORTED_MIME_TYPE" }
        require(request.chunkSize in 1024..MAX_CHUNK_SIZE) { "INVALID_CHUNK_SIZE" }
        require(request.totalChunks in 1..MAX_TOTAL_CHUNKS) { "INVALID_TOTAL_CHUNKS" }
        require(request.fileName.isNotBlank() && request.fileName.length <= 255) { "INVALID_FILE_NAME" }
        // سلامة الوسائط: اسم بلا مسارات + اتساق الحجم مع الشظايا (fail-closed)
        val cleanName = request.fileName.trim()
        require(!cleanName.contains("..") && !cleanName.contains("/") && !cleanName.contains("\\") &&
            cleanName.none { it.code < 0x20 }) { "INVALID_FILE_NAME" }
        require(request.totalSize <= request.totalChunks.toLong() * request.chunkSize &&
            request.totalSize > (request.totalChunks - 1).toLong() * request.chunkSize) { "SIZE_CHUNKS_MISMATCH" }
        
        val uploadId = "upload_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val extension = EXTENSIONS[request.mimeType] ?: "bin"
        val objectKey = "users/${user.id}/${uploadId}.${extension}"
        
        // Store upload metadata in Redis
        val uploadMeta = UploadMetadata(
            uploadId = uploadId,
            objectKey = objectKey,
            userId = user.id.toString(),
            fileName = request.fileName,
            mimeType = request.mimeType,
            totalSize = request.totalSize,
            chunkSize = request.chunkSize,
            totalChunks = request.totalChunks,
            uploadedChunks = mutableSetOf(),
            createdAt = java.time.Instant.now(),
            expiresAt = java.time.Instant.now().plusSeconds(UPLOAD_TTL_SECONDS.toLong())
        )
        
        media.storeUploadMetadata(uploadId, uploadMeta)
        
        return ResponseEntity.ok(InitiateUploadResponse(
            uploadId = uploadId,
            objectKey = objectKey,
            chunkSize = request.chunkSize,
            expiresIn = UPLOAD_TTL_SECONDS
        ))
    }

    @PostMapping("/upload/{uploadId}/chunk")
    fun uploadChunk(
        @PathVariable uploadId: String,
        @RequestParam chunkIndex: Int,
        @RequestParam chunkHash: String,
        @RequestPart("chunk") chunk: MultipartFile,
        authentication: Authentication
    ): ResponseEntity<ChunkUploadResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val metadata = media.getUploadMetadata(uploadId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ChunkUploadResponse(
                success = false,
                error = "UPLOAD_NOT_FOUND"
            ))
        
        require(metadata.userId == user.id.toString()) { "UNAUTHORIZED" }
        require(!metadata.isExpired) { "UPLOAD_EXPIRED" }
        require(chunkIndex in 0 until metadata.totalChunks) { "INVALID_CHUNK_INDEX" }
        require(chunkHash.matches(Regex("^[0-9a-fA-F]{64}$"))) { "INVALID_CHUNK_HASH" }
        require(chunk.size in 1..MAX_CHUNK_SIZE) { "INVALID_CHUNK_SIZE" }
        require(chunk.size == metadata.chunkSize || (chunkIndex == metadata.totalChunks - 1 && chunk.size <= metadata.chunkSize)) { "INVALID_CHUNK_SIZE" }
        
        // Verify chunk hash
        val computedHash = computeHash(chunk.inputStream)
        require(computedHash == chunkHash) { "CHUNK_HASH_MISMATCH" }
        
        // Store chunk in MinIO
        val chunkKey = "${metadata.objectKey}.part${chunkIndex}"
        media.storeChunk(chunkKey, chunk.inputStream, chunk.size, metadata.mimeType)
        
        // Mark chunk as uploaded
        metadata.uploadedChunks.add(chunkIndex)
        media.updateUploadMetadata(uploadId, metadata)
        
        return ResponseEntity.ok(ChunkUploadResponse(
            success = true,
            chunkIndex = chunkIndex,
            uploadedChunks = metadata.uploadedChunks.size,
            totalChunks = metadata.totalChunks
        ))
    }

    @PostMapping("/upload/{uploadId}/complete")
    fun completeUpload(
        @PathVariable uploadId: String,
        @RequestBody request: CompleteUploadRequest,
        authentication: Authentication
    ): ResponseEntity<CompleteUploadResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val metadata = media.getUploadMetadata(uploadId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CompleteUploadResponse(
                success = false,
                error = "UPLOAD_NOT_FOUND"
            ))
        
        require(metadata.userId == user.id.toString()) { "UNAUTHORIZED" }
        require(!metadata.isExpired) { "UPLOAD_EXPIRED" }
        require(metadata.uploadedChunks.size == metadata.totalChunks) { "MISSING_CHUNKS" }
        val fileHash = request.fileHash.trim().lowercase()
        require(fileHash.matches(Regex("^[0-9a-f]{64}$"))) { "FILE_HASH_REQUIRED" }

        // Compose chunks into final object
        val finalKey = metadata.objectKey
        try {
            media.composeChunks(
                objectKey = finalKey,
                totalChunks = metadata.totalChunks,
                mimeType = metadata.mimeType,
                expectedHash = fileHash
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CompleteUploadResponse(
                success = false,
                error = e.message ?: "COMPOSE_FAILED"
            ))
        }
        
        // Clean up chunk parts
        (0 until metadata.totalChunks).forEach { i ->
            media.deleteChunk("${metadata.objectKey}.part${i}")
        }
        
        // Clean up upload metadata
        media.deleteUploadMetadata(uploadId)
        
        val mediaMetadata = media.metadata(finalKey)
        
        return ResponseEntity.ok(CompleteUploadResponse(
            success = true,
            objectKey = finalKey,
            url = "/api/v1/media/${finalKey}",
            size = mediaMetadata.size,
            mimeType = mediaMetadata.mimeType
        ))
    }

    @GetMapping("/{objectKey:.+}")
    fun download(
        @PathVariable objectKey: String,
        @RequestHeader(value = HttpHeaders.RANGE, required = false) range: String?,
        authentication: Authentication
    ): ResponseEntity<*> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        // Check access
        require(media.canAccess(user.id, objectKey)) { "ACCESS_DENIED" }
        
        val metadata = media.metadata(objectKey)
        val fileSize = metadata.size
        if (fileSize <= 0) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .build()
        }

        var start = 0L
        var end = fileSize - 1
        var status = HttpStatus.OK

        if (range != null) {
            val parsed = parseRange(range, fileSize)
            if (parsed == null) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */$fileSize")
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build()
            }
            start = parsed.first
            end = parsed.second
            if (start != 0L || end != fileSize - 1) status = HttpStatus.PARTIAL_CONTENT
        }

        val contentLength = end - start + 1

        val body = StreamingResponseBody { output: OutputStream ->
            media.streamRange(objectKey, output, start, end)
        }

        val builder = ResponseEntity.status(status)
            .header(HttpHeaders.CONTENT_TYPE, metadata.mimeType)
            .header(HttpHeaders.CONTENT_LENGTH, contentLength.toString())
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
        if (status == HttpStatus.PARTIAL_CONTENT) {
            builder.header(HttpHeaders.CONTENT_RANGE, "bytes $start-$end/$fileSize")
        }
        return builder.body(body)
    }

    @PostMapping("/{objectKey}/transcode")
    fun transcode(
        @PathVariable objectKey: String,
        @Valid @RequestBody request: TranscodeRequest,
        authentication: Authentication
    ): ResponseEntity<TranscodeResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        require(media.canAccess(user.id, objectKey)) { "ACCESS_DENIED" }
        
        val metadata = media.metadata(objectKey)
        require(metadata.mimeType.startsWith("video/") || metadata.mimeType.startsWith("audio/")) { "ONLY_AUDIO_VIDEO_CAN_BE_TRANSCODED" }

        val jobId = try {
            media.startTranscode(
                objectKey = objectKey,
                outputFormat = request.outputFormat, // HLS, DASH
                qualityProfiles = request.qualityProfiles // e.g., ["1080p", "720p", "480p"]
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(TranscodeResponse(
                jobId = "",
                status = "REJECTED",
                objectKey = objectKey
            ))
        }
        
        return ResponseEntity.ok(TranscodeResponse(
            jobId = jobId,
            status = "QUEUED",
            objectKey = objectKey
        ))
    }

    @GetMapping("/transcode/{jobId}/status")
    fun transcodeStatus(
        @PathVariable jobId: String,
        authentication: Authentication
    ): ResponseEntity<TranscodeStatusResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val status = media.getTranscodeStatus(jobId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(TranscodeStatusResponse(
                jobId = jobId,
                status = "NOT_FOUND"
            ))
        
        return ResponseEntity.ok(TranscodeStatusResponse(
            jobId = jobId,
            status = status.status,
            progress = status.progress,
            outputUrls = status.outputUrls,
            error = status.error
        ))
    }

    /**
     * تحليل Range (bytes=start-end / bytes=start- / bytes=-suffix) مع تثبيت
     * fail-closed: خارج الحدود أو معكوس أو غير رقمي → null (يُرد 416).
     */
    private fun parseRange(header: String, fileSize: Long): Pair<Long, Long>? = runCatching {
        val spec = header.trim()
        if (!spec.startsWith("bytes=")) return null
        val range = spec.substring(6).trim()
        if (range.startsWith("-")) {
            val suffix = range.substring(1).toLongOrNull() ?: return null
            if (suffix <= 0) return null
            val s = (fileSize - suffix).coerceAtLeast(0)
            return s to fileSize - 1
        }
        val dash = range.indexOf('-')
        if (dash < 0) return null
        val start = range.substring(0, dash).toLongOrNull() ?: return null
        val endPart = range.substring(dash + 1)
        val end = if (endPart.isBlank()) fileSize - 1 else endPart.toLongOrNull() ?: return null
        if (start < 0 || end < start || start >= fileSize) return null
        start to end.coerceAtMost(fileSize - 1)
    }.getOrNull()

    private fun computeHash(inputStream: java.io.InputStream): String {        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        // سقف الجلسة 500MB (كان 500GB خطأً — يفوق multipart 100MB ويفتح إغراق تخزين).
        const val MAX_UPLOAD_SIZE = 500L * 1024 * 1024 // 500 MB
        const val MAX_CHUNK_SIZE = 100L * 1024 * 1024 // 100 MB
        const val MAX_TOTAL_CHUNKS = 5000
        const val UPLOAD_TTL_SECONDS = 24 * 3600 // 24 hours

        // تُطابق MediaService.ALLOWED (الماسح يملك magic لها) — zip بلا magic مرفوض.
        val ALLOWED_MIME_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav",
            "application/pdf", "application/octet-stream"
        )
        
        val EXTENSIONS = mapOf(
            "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/gif" to "gif",
            "video/mp4" to "mp4", "video/webm" to "webm", "video/quicktime" to "mov",
            "audio/ogg" to "ogg", "audio/mp4" to "m4a", "audio/mpeg" to "mp3", "audio/wav" to "wav",
            "application/pdf" to "pdf", "application/octet-stream" to "bin"
        )
    }
}

data class InitiateUploadRequest(
    val fileName: String,
    val mimeType: String,
    val totalSize: Long,
    val chunkSize: Long = 5L * 1024 * 1024, // 5 MB default
    val totalChunks: Int
)

data class InitiateUploadResponse(
    val uploadId: String,
    val objectKey: String,
    val chunkSize: Long,
    val expiresIn: Int
)

data class ChunkUploadResponse(
    val success: Boolean,
    val chunkIndex: Int = -1,
    val uploadedChunks: Int = 0,
    val totalChunks: Int = 0,
    val error: String? = null
)

data class CompleteUploadRequest(
    val fileHash: String
)

data class CompleteUploadResponse(
    val success: Boolean,
    val objectKey: String? = null,
    val url: String? = null,
    val size: Long = 0,
    val mimeType: String = "",
    val error: String? = null
)

data class TranscodeRequest(
    val outputFormat: String, // HLS, DASH
    val qualityProfiles: List<String> = listOf("1080p", "720p", "480p", "360p")
)

data class TranscodeResponse(
    val jobId: String,
    val status: String,
    val objectKey: String
)

data class TranscodeStatusResponse(
    val jobId: String,
    val status: String, // QUEUED, PROCESSING, COMPLETED, FAILED
    val progress: Int = 0,
    val outputUrls: Map<String, String> = emptyMap(),
    val error: String? = null
)