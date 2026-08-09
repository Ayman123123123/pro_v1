package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * RED Master Live Stream Service
 * Tracks active streams and audience counts.
 */
@Service
class LiveStreamService {
    companion object { private val log = LoggerFactory.getLogger(LiveStreamService::class.java) }

    // StreamId -> Set of ViewerSessionIds
    private val liveViewers = ConcurrentHashMap<String, MutableSet<String>>()

    fun startStream(streamId: String, broadcasterId: String) {
        liveViewers[streamId] = ConcurrentHashMap.newKeySet()
        log.info("Stream {} started by broadcaster {}", streamId, broadcasterId)
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

    fun getActiveStreams(): Set<String> = liveViewers.keys.toSet()

    fun stopStream(streamId: String) {
        liveViewers.remove(streamId)
        log.info("Stream {} ended", streamId)
    }
}
