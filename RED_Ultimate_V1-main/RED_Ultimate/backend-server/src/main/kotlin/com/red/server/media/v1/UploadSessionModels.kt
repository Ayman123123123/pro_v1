package com.red.server.media.v1

import java.time.Instant

/**
 * جلسة رفع مجزأ — تُحفظ في Redis (red:media:upload:{uploadId}, TTL 24h)
 * مع مرآة ذاكرة احتياطية داخل MediaService عند غياب Redis.
 */
data class UploadMetadata(
    val uploadId: String = "",
    val objectKey: String = "",
    val userId: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val totalSize: Long = 0L,
    val chunkSize: Long = 0L,
    val totalChunks: Int = 0,
    val uploadedChunks: MutableSet<Int> = mutableSetOf(),
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plusSeconds(86400)
) {
    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)
}

/** حالة مهمة ترميز — QUEUED فقط حتى يكتمل عامل الترميز الخلفي. */
data class TranscodeJobStatus(
    val jobId: String = "",
    val status: String = "QUEUED", // QUEUED, PROCESSING, COMPLETED, FAILED
    val progress: Int = 0,
    val outputUrls: Map<String, String> = emptyMap(),
    val error: String? = null,
    val updatedAt: Instant = Instant.now()
)
