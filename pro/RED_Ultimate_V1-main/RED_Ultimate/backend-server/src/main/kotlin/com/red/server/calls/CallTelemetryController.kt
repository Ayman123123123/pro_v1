package com.red.server.calls

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * Client-side telemetry endpoint.
 * Privacy: peer ID NOT stored. Only aggregated quality metrics.
 * Used to monitor call quality and improve the service.
 */
@Document("call_telemetry")
data class CallTelemetryDocument(
    @Id val id: String? = null,
    @Indexed val callId: String,
    val type: String,
    val route: String,
    val durationMs: Long,
    val avgRttMs: Long,
    val maxPacketLoss: Double,
    val qualityAtEnd: String,
    val wasRecorded: Boolean,
    val wasHeld: Int,
    val ownerHash: Int, // hash(userId) للبحث الإحصائي دون كشف الهوية
    val receivedAt: Instant = Instant.now()
)

interface CallTelemetryRepository : MongoRepository<CallTelemetryDocument, String> {
    fun countByQualityAtEndAndReceivedAtAfter(quality: String, after: Instant): Long
    fun findByReceivedAtAfter(after: Instant): List<CallTelemetryDocument>
}

@RestController
@RequestMapping("/api/calls/telemetry")
class CallTelemetryController(private val repository: CallTelemetryRepository) {

    @PostMapping
    fun upload(
        @RequestBody event: CallTelemetryDto,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val ownerHash = authentication.name.hashCode()
        repository.save(CallTelemetryDocument(
            callId = event.callId,
            type = event.type,
            route = event.route,
            durationMs = event.durationMs,
            avgRttMs = event.avgRttMs,
            maxPacketLoss = event.maxPacketLoss,
            qualityAtEnd = event.qualityAtEnd,
            wasRecorded = event.wasRecorded,
            wasHeld = event.wasHeld,
            ownerHash = ownerHash
        ))
        return ResponseEntity.ok(mapOf("status" to "received"))
    }

    /**
     * Admin endpoint: إحصائيات مجمعة (anonymous).
     * لا يكشف هوية المستخدم — فقط hash.
     */
    @GetMapping("/stats")
    fun stats(
        @RequestParam(defaultValue = "7") days: Int
    ): Map<String, Any> {
        val since = Instant.now().minusSeconds(days * 24L * 3600L)
        val events = repository.findByReceivedAtAfter(since)
        val total = events.size
        val avgDuration = if (events.isNotEmpty()) events.map { it.durationMs }.average() else 0.0
        val avgRtt = if (events.isNotEmpty()) events.map { it.avgRttMs }.average() else 0.0
        val byQuality = events.groupingBy { it.qualityAtEnd }.eachCount()
        return mapOf(
            "totalCalls" to total,
            "avgDurationMs" to avgDuration,
            "avgRttMs" to avgRtt,
            "byQuality" to byQuality
        )
    }
}

data class CallTelemetryDto(
    val callId: String,
    val type: String,
    val route: String,
    val durationMs: Long,
    val avgRttMs: Long,
    val maxPacketLoss: Double,
    val qualityAtEnd: String,
    val wasRecorded: Boolean,
    val wasHeld: Int
)
