package com.red.server.calls

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * RED Master Live Stream Service
 * Tracks active streams, their broadcasters and live audience counts.
 */
@Service
class LiveStreamService {
    private val log = LoggerFactory.getLogger(javaClass)

    data class LiveStream(
        val streamId: String,
        val broadcasterId: String,
        val startedAt: Instant,
        val viewerCount: Int
    )

    // StreamId -> { broadcasterId, startedAt, viewers }
    private data class StreamState(
        val broadcasterId: String,
        val startedAt: Instant,
        val viewers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    )

    private val liveStreams = ConcurrentHashMap<String, StreamState>()

    fun startStream(streamId: String, broadcasterId: String): LiveStream {
        val state = liveStreams.computeIfAbsent(streamId) {
            log.info("RED LIVE: Stream {} started by {}", streamId, broadcasterId)
            StreamState(broadcasterId, Instant.now())
        }
        return toView(state, streamId)
    }

    fun addViewer(streamId: String, viewerId: String) {
        liveStreams[streamId]?.viewers?.add(viewerId)
    }

    fun removeViewer(streamId: String, viewerId: String) {
        liveStreams[streamId]?.viewers?.remove(viewerId)
    }

    fun getViewerCount(streamId: String): Int = liveStreams[streamId]?.viewers?.size ?: 0

    fun stopStream(streamId: String): Boolean {
        val removed = liveStreams.remove(streamId)
        if (removed != null) log.info("RED LIVE: Stream {} has ended.", streamId)
        return removed != null
    }

    fun getActiveStreams(): List<LiveStream> =
        liveStreams.map { (id, state) -> toView(state, id) }.sortedByDescending { it.startedAt }

    private fun toView(state: StreamState, streamId: String): LiveStream =
        LiveStream(streamId, state.broadcasterId, state.startedAt, state.viewers.size)
}
