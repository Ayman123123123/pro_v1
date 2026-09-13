package com.red.server.zoom

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ZoomRoomRecord(
    val meetingId: String,
    val hostId: String,
    val hostRedId: String,
    val title: String,
    val isVideo: Boolean,
    val createdAt: Instant = Instant.now(),
    var endedAt: Instant? = null
)

/**
 * غرف Zoom مع ثبات Redis: نفس الـ API السابق تماماً، لكن كل طفرة تُكتب
 * في Redis (TTL يُجدَّد) وتُسترجع عند الإقلاع. Redis معطل/غائب = سلوك
 * الذاكرة القديم (لا كسر). المفاتيح: red:zoom:room:{id} (JSON أرقامه epoch)
 * + red:zoom:members:{id} (Set) + فهرس red:zoom:rooms.
 */
@Service
class ZoomRoomService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(ZoomRoomService::class.java)
    private val rooms = ConcurrentHashMap<String, ZoomRoomRecord>()
    private val participants = ConcurrentHashMap<String, MutableSet<String>>()

    @PostConstruct
    fun restoreFromRedis() {
        runCatching {
            val ids = redis.opsForSet().members(INDEX_KEY).orEmpty()
            var restored = 0
            ids.forEach { id ->
                val json = redis.opsForValue().get(roomKey(id)) ?: return@forEach
                val rec = parseRoom(id, json) ?: return@forEach
                if (rec.endedAt != null) {
                    dropFromRedis(id)
                    return@forEach
                }
                rooms[id] = rec
                val members = redis.opsForSet().members(membersKey(id)).orEmpty()
                    .filter { it.isNotBlank() }.toMutableSet()
                participants[id] = ConcurrentHashMap.newKeySet<String>().also { it.addAll(members) }
                restored++
            }
            if (restored > 0) log.info("Zoom rooms restored from Redis: {}", restored)
        }.onFailure { log.warn("Zoom Redis restore skipped: {}", it.message) }
    }

    fun createRoom(meetingId: String, hostId: String, hostRedId: String, title: String, isVideo: Boolean): ZoomRoomRecord {
        require(meetingId.matches(Regex("^[A-Za-z0-9_-]{4,128}$"))) { "Invalid meeting ID" }
        return rooms.computeIfAbsent(meetingId) {
            participants[meetingId] = ConcurrentHashMap.newKeySet()
            val rec = ZoomRoomRecord(meetingId, hostId, hostRedId, title.ifBlank { "اجتماع Zoom" }, isVideo)
            persist(rec)
            rec
        }
    }

    fun getRoom(meetingId: String): ZoomRoomRecord? = rooms[meetingId]

    fun isActive(meetingId: String): Boolean = rooms.containsKey(meetingId)

    fun addParticipant(meetingId: String, userId: String) {
        if (participants[meetingId]?.add(userId) == true) {
            runCatching {
                redis.opsForSet().add(membersKey(meetingId), userId)
                touch(meetingId)
            }
        }
    }

    fun removeParticipant(meetingId: String, userId: String) {
        if (participants[meetingId]?.remove(userId) == true) {
            runCatching {
                redis.opsForSet().remove(membersKey(meetingId), userId)
                touch(meetingId)
            }
        }
    }

    fun closeRoom(meetingId: String, requesterId: String): Boolean {
        val room = rooms[meetingId] ?: return false
        if (room.hostId != requesterId) return false
        rooms.remove(meetingId)
        participants.remove(meetingId)
        dropFromRedis(meetingId)
        return true
    }

    private fun persist(rec: ZoomRoomRecord) {
        runCatching {
            val json = objectMapper.writeValueAsString(
                mapOf(
                    "meetingId" to rec.meetingId,
                    "hostId" to rec.hostId,
                    "hostRedId" to rec.hostRedId,
                    "title" to rec.title,
                    "isVideo" to rec.isVideo,
                    "createdAt" to rec.createdAt.epochSecond,
                    "endedAt" to rec.endedAt?.epochSecond
                )
            )
            redis.opsForValue().set(roomKey(rec.meetingId), json, TTL_HOURS, TimeUnit.HOURS)
            redis.opsForSet().add(INDEX_KEY, rec.meetingId)
            redis.expire(INDEX_KEY, TTL_HOURS, TimeUnit.HOURS)
        }.onFailure { log.warn("Zoom Redis persist skipped for {}: {}", rec.meetingId, it.message) }
    }

    private fun touch(meetingId: String) {
        runCatching {
            redis.expire(roomKey(meetingId), TTL_HOURS, TimeUnit.HOURS)
            redis.expire(membersKey(meetingId), TTL_HOURS, TimeUnit.HOURS)
            redis.expire(INDEX_KEY, TTL_HOURS, TimeUnit.HOURS)
        }
    }

    private fun dropFromRedis(meetingId: String) {
        runCatching {
            redis.delete(roomKey(meetingId))
            redis.delete(membersKey(meetingId))
            redis.opsForSet().remove(INDEX_KEY, meetingId)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRoom(id: String, json: String): ZoomRoomRecord? = runCatching {
        val m = objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
        ZoomRoomRecord(
            meetingId = id,
            hostId = m["hostId"]?.toString() ?: return null,
            hostRedId = m["hostRedId"]?.toString() ?: "",
            title = m["title"]?.toString() ?: "اجتماع Zoom",
            isVideo = m["isVideo"] as? Boolean ?: false,
            createdAt = Instant.ofEpochSecond((m["createdAt"] as? Number)?.toLong() ?: Instant.now().epochSecond),
            endedAt = (m["endedAt"] as? Number)?.toLong()?.let(Instant::ofEpochSecond)
        )
    }.getOrNull()

    companion object {
        private const val INDEX_KEY = "red:zoom:rooms"
        private const val TTL_HOURS = 24L
        private fun roomKey(id: String) = "red:zoom:room:$id"
        private fun membersKey(id: String) = "red:zoom:members:$id"
    }
}
