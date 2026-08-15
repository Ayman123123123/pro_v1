package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * RED Master Live Stream Service
 * Tracks active streams, broadcasters, and audience counts for the running SFU session.
 * Durable stream history belongs in analytics; active WebRTC sessions cannot survive a process restart.
 */
@Document("live_streams")
data class LiveStreamRecord(
    @Id val streamId: String,
    @Indexed val broadcasterId: String,
    val broadcasterName: String = "",
    val broadcasterRedId: String = "",
    val title: String = "",
    val isPrivate: Boolean = false,
    val passwordHash: String? = null,
    val startedAt: Instant = Instant.now(),
    var endedAt: Instant? = null
) {
    var viewerCount: Int = 0
}

@Service
class LiveStreamService {
    companion object { private val log = LoggerFactory.getLogger(LiveStreamService::class.java) }

    // In-memory overlay for fast viewer counts
    private val liveViewers = ConcurrentHashMap<String, MutableSet<String>>()
    private val activeStreamRecords = ConcurrentHashMap<String, LiveStreamRecord>()

    fun startStream(streamId: String, broadcasterId: String): LiveStreamRecord {
        return createStream(streamId, broadcasterId, "", "", "بث مباشر", false, null)
    }

    fun createStream(
        streamId: String,
        broadcasterId: String,
        broadcasterName: String,
        broadcasterRedId: String,
        title: String,
        isPrivate: Boolean,
        password: String?
    ): LiveStreamRecord {
        if (liveViewers.containsKey(streamId)) {
            val existing = activeStreamRecords[streamId] ?: error("LIVE_STREAM_STATE_CORRUPT")
            require(existing.broadcasterId == broadcasterId) { "STREAM_ID_ALREADY_OWNED" }
            log.info("Stream {} already active for the same broadcaster", streamId)
            return existing
        }
        require(!isPrivate || !password.isNullOrBlank()) { "A private room requires a password" }
        val passHash = password?.takeIf { it.isNotBlank() }?.let(RoomPasswordHasher::hash)
        val record = LiveStreamRecord(
            streamId = streamId,
            broadcasterId = broadcasterId,
            broadcasterName = broadcasterName.ifBlank { "مُبث يونس" },
            broadcasterRedId = broadcasterRedId,
            title = title.ifBlank { "بث مباشر يونس 🔴" },
            isPrivate = isPrivate,
            passwordHash = passHash,
            startedAt = Instant.now()
        )
        liveViewers[streamId] = ConcurrentHashMap.newKeySet()
        activeStreamRecords[streamId] = record
        log.info("Stream {} created by broadcaster {} (title={}, private={})", streamId, broadcasterId, title, isPrivate)
        return record
    }

    fun verifyPassword(streamId: String, password: String?): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        if (!record.isPrivate || record.passwordHash.isNullOrBlank()) return true
        if (password.isNullOrBlank()) return false
        return RoomPasswordHasher.verify(password, record.passwordHash)
    }

    fun searchPublicStreams(query: String?): List<LiveStreamRecord> {
        val cleanQuery = query?.trim()?.lowercase().orEmpty()
        return activeStreamRecords.values
            .filter { !it.isPrivate }
            .filter { record ->
                if (cleanQuery.isBlank()) true
                else record.title.lowercase().contains(cleanQuery) ||
                     record.broadcasterName.lowercase().contains(cleanQuery) ||
                     record.broadcasterRedId.lowercase().contains(cleanQuery) ||
                     record.streamId.lowercase().contains(cleanQuery)
            }
            .onEach { record -> record.viewerCount = getViewerCount(record.streamId) }
    }

    fun getStreamRecord(streamId: String): LiveStreamRecord? = activeStreamRecords[streamId]

    fun addViewer(streamId: String, viewerId: String): Int {
        val viewers = liveViewers[streamId] ?: return -1
        viewers.add(viewerId)
        val count = viewers.size
        activeStreamRecords[streamId]?.viewerCount = count
        return count
    }

    fun removeViewer(streamId: String, viewerId: String) {
        liveViewers[streamId]?.remove(viewerId)
        val count = getViewerCount(streamId)
        activeStreamRecords[streamId]?.viewerCount = count
    }

    fun getViewerCount(streamId: String): Int = liveViewers[streamId]?.size ?: 0

    fun isViewer(streamId: String, accountId: String): Boolean =
        liveViewers[streamId]?.contains(accountId) == true

    fun getActiveStreams(): List<LiveStreamRecord> {
        return activeStreamRecords.values.onEach { record -> record.viewerCount = getViewerCount(record.streamId) }.toList()
    }

    fun stopStream(streamId: String): Boolean {
        val removed = liveViewers.remove(streamId) != null
        activeStreamRecords.remove(streamId)
        if (removed) log.info("Stream {} ended", streamId)
        return removed
    }


}
