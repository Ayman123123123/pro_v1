package com.red.sovereign.calls

import androidx.compose.runtime.mutableStateListOf

data class BreakoutRoom(
    val id: String,
    val name: String,
    val memberIds: MutableList<String> = mutableListOf(),
    var timerMinutes: Int = 0,
    var isTimerActive: Boolean = false
)

/**
 * مدير غرف الانقسام — BreakoutRoomsManager (Liquid Glass 2026)
 * يدعم الإنشاء الديناميكي، التعيين اليدوي والتلقائي، المؤقتات، Thread Safety، ومعالجة الأخطاء.
 */
object BreakoutRoomsManager {
    private val lock = Any()
    val rooms = mutableStateListOf<BreakoutRoom>()

    fun createRoom(name: String): BreakoutRoom = synchronized(lock) {
        val room = BreakoutRoom(id = "room-${System.currentTimeMillis()}-${(0..999).random()}", name = name)
        rooms.add(room)
        room
    }

    fun assignMember(roomId: String, userId: String) = synchronized(lock) {
        rooms.find { it.id == roomId }?.let { room ->
            if (userId !in room.memberIds) {
                room.memberIds.add(userId)
            }
        }
    }

    fun removeMember(roomId: String, userId: String) = synchronized(lock) {
        rooms.find { it.id == roomId }?.memberIds?.remove(userId)
    }

    fun deleteRoom(roomId: String) = synchronized(lock) {
        rooms.removeIf { it.id == roomId }
    }

    fun autoAssign(roomNames: List<String>, memberIds: List<String>) = synchronized(lock) {
        clear()
        if (roomNames.isEmpty() || memberIds.isEmpty()) return@synchronized
        val createdRooms = roomNames.map { createRoom(it) }
        memberIds.forEachIndexed { index, userId ->
            val targetRoom = createdRooms[index % createdRooms.size]
            assignMember(targetRoom.id, userId)
        }
    }

    fun setTimer(roomId: String, minutes: Int, active: Boolean) = synchronized(lock) {
        rooms.find { it.id == roomId }?.let { room ->
            room.timerMinutes = minutes
            room.isTimerActive = active
        }
    }

    fun clear() = synchronized(lock) {
        rooms.clear()
    }
}
