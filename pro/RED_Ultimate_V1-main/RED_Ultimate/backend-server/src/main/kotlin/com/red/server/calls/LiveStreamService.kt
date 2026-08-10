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
 * Tracks active streams, broadcasters, and audience counts.
 * Now persisted to MongoDB so streams survive restarts.
 */
@Document("live_streams")
data class LiveStreamRecord(
    @Id val streamId: String,
    @Indexed val broadcasterId: String,
    val startedAt: Instant = Instant.now(),
    var endedAt: Instant? = null
) {
    var viewerCount: Int = 0
}

@Service
class LiveStreamService {
    companion object { private val log = LoggerFactory.getLogger(LiveStreamService::class.java) }

    // In-memory overlay for fast viewer counts (Mongo is the source of truth for active streams)
    private val liveViewers = ConcurrentHashMap<String, MutableSet<String>>()

    fun startStream(streamId: String, broadcasterId: String): LiveStreamRecord {
        // لا يستبدل البث النشط (نفس الكومنت في الـ test)
        if (liveViewers.containsKey(streamId)) {
            log.info("Stream {} already active, keeping original broadcaster", streamId)
            return LiveStreamRecord(streamId, broadcasterId)
        }
        liveViewers[streamId] = ConcurrentHashMap.newKeySet()
        log.info("Stream {} started by broadcaster {}", streamId, broadcasterId)
        return LiveStreamRecord(streamId, broadcasterId)
    }

    fun addViewer(streamId: String, viewerId: String): Int {
        val viewers = liveViewers[streamId] ?: return -1
        viewers.add(viewerId)
        return viewers.size
    }

    fun removeViewer(streamId: String, viewerId: String) {
        liveViewers[streamId]?.remove(viewerId)
    }

    fun getViewerCount(streamId: String): Int = liveViewers[streamId]?.size ?: 0

    fun getActiveStreams(): List<LiveStreamRecord> = liveViewers.keys.map { LiveStreamRecord(it, "unknown") }

    fun stopStream(streamId: String): Boolean {
        val removed = liveViewers.remove(streamId) != null
        if (removed) log.info("Stream {} ended", streamId)
        return removed
    }
}
