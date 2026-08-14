package com.red.server.calls

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Call Recording API.
 *
 * Endpoints:
 * - POST /api/recordings: تسجيل metadata لتسجيل مكالمة
 * - GET  /api/recordings: قائمة تسجيلات المستخدم
 * - GET  /api/recordings/{id}/download: تحميل التسجيل
 * - DELETE /api/recordings/{id}: حذف التسجيل
 *
 * الملفات الفعلية محفوظة محلياً على جهاز المستخدم (مشفر بـ Android Keystore).
 * هذا الـ endpoint يحفظ الـ metadata فقط (callId, sha256, size, duration).
 */
@Document("call_recordings")
data class CallRecordingDocument(
    @Id val id: String,
    @Indexed val ownerId: String,
    val callId: String,
    val peerId: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val encrypted: Boolean = true,
    val createdAt: Instant = Instant.now()
)

interface CallRecordingRepository : MongoRepository<CallRecordingDocument, String> {
    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: String): List<CallRecordingDocument>
    fun findTop200ByOwnerIdOrderByCreatedAtDesc(ownerId: String): List<CallRecordingDocument>
}

@RestController
@RequestMapping("/api/recordings")
class CallRecordingController(private val repository: CallRecordingRepository) {

    @PostMapping
    fun register(
        @RequestBody request: RegisterRecordingRequest,
        authentication: Authentication
    ): ResponseEntity<CallRecordingDocument> {
        val ownerId = authentication.name
        val recording = CallRecordingDocument(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            callId = request.callId,
            peerId = request.peerId,
            sha256 = request.sha256,
            sizeBytes = request.sizeBytes,
            durationMs = request.durationMs,
            encrypted = true,
            createdAt = Instant.now()
        )
        val saved = repository.save(recording)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(saved)
    }

    @GetMapping
    fun list(authentication: Authentication): List<CallRecordingDocument> {
        // سقف 200 حتى لا يُحمّل تاريخ كامل بلا حدود في ذاكرة الخادم.
        return repository.findTop200ByOwnerIdOrderByCreatedAtDesc(authentication.name)
    }

    @GetMapping("/{id}/manifest")
    fun manifest(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<RecordingManifestResponse> {
        val recording = repository.findById(id).orElseThrow { NoSuchElementException("Recording not found") }
        if (recording.ownerId != authentication.name) throw SecurityException("Not owner")
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(RecordingManifestResponse(
                id = recording.id,
                sha256 = recording.sha256,
                sizeBytes = recording.sizeBytes,
                durationMs = recording.durationMs,
                encryption = if (recording.encrypted) "AES-256-GCM" else "none"
            ))
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val recording = repository.findById(id).orElseThrow { NoSuchElementException("Recording not found") }
        if (recording.ownerId != authentication.name) throw SecurityException("Not owner")
        repository.delete(recording)
        return ResponseEntity.noContent().build()
    }
}

data class RegisterRecordingRequest(
    val callId: String,
    val peerId: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long
)

data class RecordingManifestResponse(
    val id: String,
    val sha256: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val encryption: String
)
