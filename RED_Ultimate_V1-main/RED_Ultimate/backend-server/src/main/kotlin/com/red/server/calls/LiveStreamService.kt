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
class LiveStreamService(
    private val passwordHasher: RoomPasswordHasher,
    private val liveStreamRepository: LiveStreamRepository,
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler
) {
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
        require(streamId.isNotBlank()) { "STREAM_ID_REQUIRED" }
        require(broadcasterId.isNotBlank()) { "BROADCASTER_ID_REQUIRED" }
        require(!isPrivate || !password.isNullOrBlank()) { "PRIVATE_STREAM_PASSWORD_REQUIRED" }
        if (liveViewers.containsKey(streamId)) {
            val existing = activeStreamRecords[streamId] ?: error("LIVE_STREAM_STATE_CORRUPT")
            require(existing.broadcasterId == broadcasterId) { "STREAM_ID_ALREADY_OWNED" }
            log.info("Stream {} already active for the same broadcaster", streamId)
            return existing
        }
        val passHash = password?.takeIf { it.isNotBlank() }?.let { passwordHasher.hash(it) }
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
        
        // Save to MongoDB for persistent history
        liveStreamRepository.save(record)
        
        log.info("Stream {} created by broadcaster {} (title={}, private={})", streamId, broadcasterId, title, isPrivate)
        return record
    }

    fun verifyPassword(streamId: String, password: String?): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        if (!record.isPrivate) return true
        val passwordHash = record.passwordHash ?: return false
        if (password.isNullOrBlank()) return false
        return passwordHasher.verify(password, passwordHash)
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

    /** يثبت أن المشاهد مرّ عبر مسار الانضمام المعتمد قبل منحه تذكرة SFU. */
    fun isViewer(streamId: String, viewerId: String): Boolean =
        liveViewers[streamId]?.contains(viewerId) == true

    fun getActiveStreams(): List<LiveStreamRecord> {
        return activeStreamRecords.values.onEach { record -> record.viewerCount = getViewerCount(record.streamId) }.toList()
    }

    fun stopStream(streamId: String): Boolean {
        val removed = liveViewers.remove(streamId) != null
        val record = activeStreamRecords.remove(streamId)
        
        if (record != null) {
            record.endedAt = Instant.now()
            // Update MongoDB with final stats and end time
            liveStreamRepository.save(record)
        }
        
        if (removed) log.info("Stream {} ended", streamId)
        return removed
    }

    /** طرد مشاهد من البث المباشر (Kick Viewer) */
    fun kickViewer(streamId: String, broadcasterId: String, viewerIdToKick: String): Boolean {
        val record = activeStreamRecords[streamId] ?: return false
        require(record.broadcasterId == broadcasterId) { "ONLY_BROADCASTER_CAN_KICK" }
        
        val viewers = liveViewers[streamId]
        if (viewers?.remove(viewerIdToKick) == true) {
            record.viewerCount = viewers.size
            // إرسال إشارة الطرد عبر WebSocket للمشاهد
            callSignaling.deliverSignal(
                targetRedId = viewerIdToKick,
                type = "KICKED",
                roomId = streamId,
                payload = mapOf("reason" to "Kicked by broadcaster")
            )
            log.info("Viewer {} kicked from stream {} by broadcaster {}", viewerIdToKick, streamId, broadcasterId)
            return true
        }
        return false
    }

}
