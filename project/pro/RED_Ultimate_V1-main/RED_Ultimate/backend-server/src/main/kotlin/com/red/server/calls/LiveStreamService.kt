package com.red.server.calls

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * RED Master Live Stream Service
 * Tracks active streams and audience counts.
 */
@Service
class LiveStreamService {
    // StreamId -> List of ViewerSessionIds
    private val liveViewers = ConcurrentHashMap<String, MutableSet<String>>()

    fun startStream(streamId: String, broadcasterId: String) {
        liveViewers[streamId] = mutableSetOf()
        println("🔴 RED LIVE: Stream $streamId started by $broadcasterId")
    }

    fun addViewer(streamId: String, viewerId: String) {
        liveViewers[streamId]?.add(viewerId)
    }

    fun removeViewer(streamId: String, viewerId: String) {
        liveViewers[streamId]?.remove(viewerId)
    }

    fun getViewerCount(streamId: String): Int {
        return liveViewers[streamId]?.size ?: 0
    }

    fun stopStream(streamId: String) {
        liveViewers.remove(streamId)
        println("🚫 RED LIVE: Stream $streamId has ended.")
    }
}
