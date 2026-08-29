package com.red.sovereign.calls

import androidx.compose.runtime.mutableStateListOf

data class BreakoutRoom(
    val id: String,
    val name: String,
    val memberIds: MutableList<String> = mutableListOf()
)

object BreakoutRoomsManager {
    val rooms = mutableStateListOf<BreakoutRoom>()

    fun createRoom(name: String): BreakoutRoom {
        val room = BreakoutRoom(id = "room-${System.currentTimeMillis()}", name = name)
        rooms.add(room)
        return room
    }

    fun assignMember(roomId: String, userId: String) {
        rooms.find { it.id == roomId }?.memberIds?.add(userId)
    }

    fun removeMember(roomId: String, userId: String) {
        rooms.find { it.id == roomId }?.memberIds?.remove(userId)
    }

    fun deleteRoom(roomId: String) {
        rooms.removeIf { it.id == roomId }
    }

    fun clear() = rooms.clear()
}
