package com.red.server.zoom

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ZoomRoomRecord(
    val meetingId: String,
    val hostId: String,
    val hostRedId: String,
    val title: String,
    val isVideo: Boolean,
    val createdAt: Instant = Instant.now(),
    var endedAt: Instant? = null
)

@Service
class ZoomRoomService {
    private val rooms = ConcurrentHashMap<String, ZoomRoomRecord>()
    private val participants = ConcurrentHashMap<String, MutableSet<String>>()

    fun createRoom(meetingId: String, hostId: String, hostRedId: String, title: String, isVideo: Boolean): ZoomRoomRecord {
        require(meetingId.matches(Regex("^[A-Za-z0-9_-]{4,128}$"))) { "Invalid meeting ID" }
        return rooms.computeIfAbsent(meetingId) {
            participants[meetingId] = ConcurrentHashMap.newKeySet()
            ZoomRoomRecord(meetingId, hostId, hostRedId, title.ifBlank { "اجتماع Zoom" }, isVideo)
        }
    }

    fun getRoom(meetingId: String): ZoomRoomRecord? = rooms[meetingId]

    fun isActive(meetingId: String): Boolean = rooms.containsKey(meetingId)

    fun addParticipant(meetingId: String, userId: String) {
        participants[meetingId]?.add(userId)
    }

    fun removeParticipant(meetingId: String, userId: String) {
        participants[meetingId]?.remove(userId)
    }

    fun closeRoom(meetingId: String, requesterId: String): Boolean {
        val room = rooms[meetingId] ?: return false
        if (room.hostId != requesterId) return false
        rooms.remove(meetingId)
        participants.remove(meetingId)
        return true
    }
}
