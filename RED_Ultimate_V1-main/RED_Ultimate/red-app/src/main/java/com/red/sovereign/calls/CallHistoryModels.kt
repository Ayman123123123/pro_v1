package com.red.sovereign.calls

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CallHistoryItem(
    val id: String,
    val peerId: String,
    val peerLabel: String,
    val direction: String,
    val type: String,
    val route: String,
    val status: String,
    val startedAt: String,
    val answeredAt: String? = null,
    val endedAt: String? = null
)

/** Accepts epoch millis, epoch seconds, or ISO-8601 from the backend Instant serializer. */
fun parseCallTimestamp(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    value.toLongOrNull()?.let { raw ->
        return if (raw in 1_000_000_000L until 100_000_000_000L) raw * 1000L else raw
    }
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
