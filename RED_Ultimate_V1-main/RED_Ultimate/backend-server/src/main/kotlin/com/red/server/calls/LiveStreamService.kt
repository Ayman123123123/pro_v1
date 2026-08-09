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
    companion object { private val log = LoggerFactory.getLogger(LiveStreamService::class.java) }

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
            log.info("Stream {} started by broadcaster {}", streamId, broadcasterId)
            StreamState(broadcasterId, Instant.now())
        }
        return toView(state, streamId)
    }

    fun addViewer(streamId: String, viewerId: String): Int {
        val viewers = liveStreams[streamId]?.viewers ?: return -1
        viewers.add(viewerId)
        return viewers.size
    }

    fun removeViewer(streamId: String, viewerId: String) {
        liveStreams[streamId]?.viewers?.remove(viewerId)
    }

    fun getViewerCount(streamId: String): Int = liveStreams[streamId]?.viewers?.size ?: 0

    fun getActiveStreams(): List<LiveStream> =
        liveStreams.map { (id, state) -> toView(state, id) }.sortedByDescending { it.startedAt }

    fun stopStream(streamId: String): Boolean {
        val removed = liveStreams.remove(streamId)
        if (removed != null) log.info("Stream {} ended", streamId)
        return removed != null
    }

    private fun toView(state: StreamState, streamId: String): LiveStream =
        LiveStream(streamId, state.broadcasterId, state.startedAt, state.viewers.size)
}
