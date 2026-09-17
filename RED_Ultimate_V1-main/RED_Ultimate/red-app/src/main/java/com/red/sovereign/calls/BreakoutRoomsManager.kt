package com.red.sovereign.calls

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BreakoutRoom(
    val id: String,
    val name: String,
    val memberIds: MutableList<String> = mutableListOf(),
    var timerMinutes: Int = 0,
    var isTimerActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * مدير غرف الانقسام — BreakoutRoomsManager (Liquid Glass 2026)
 * يدعم الإنشاء الديناميكي، التعيين اليدوي والتلقائي، المؤقتات، Thread Safety، ومعالجة الأخطاء.
 */
object BreakoutRoomsManager {
    private val lock = Any()
    val rooms = mutableStateListOf<BreakoutRoom>()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timerJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    interface Listener {
        fun onRoomCreated(room: BreakoutRoom)
        fun onRoomDeleted(roomId: String)
        fun onMemberAssigned(roomId: String, userId: String)
        fun onMemberRemoved(roomId: String, userId: String)
        fun onTimerUpdate(roomId: String, remainingSeconds: Int)
        fun onTimerExpired(roomId: String)
        fun onError(message: String)
    }
    
    private var listener: Listener? = null
    
    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun createRoom(name: String): BreakoutRoom = synchronized(lock) {
        val room = BreakoutRoom(id = "br_${System.currentTimeMillis()}_${(0..9999).random()}", name = name)
        rooms.add(room)
        listener?.onRoomCreated(room)
        room
    }

    fun assignMember(roomId: String, userId: String) = synchronized(lock) {
        rooms.find { it.id == roomId }?.let { room ->
            if (userId !in room.memberIds) {
                room.memberIds.add(userId)
                listener?.onMemberAssigned(roomId, userId)
            }
        }
    }

    fun removeMember(roomId: String, userId: String) = synchronized(lock) {
        rooms.find { it.id == roomId }?.memberIds?.remove(userId)
        listener?.onMemberRemoved(roomId, userId)
    }

    fun deleteRoom(roomId: String) = synchronized(lock) {
        cancelTimer(roomId)
        rooms.removeIf { it.id == roomId }
        listener?.onRoomDeleted(roomId)
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
            if (active && minutes > 0) {
                startTimer(roomId, minutes * 60)
            } else {
                cancelTimer(roomId)
            }
        }
    }
    
    private fun startTimer(roomId: String, totalSeconds: Int) {
        cancelTimer(roomId)
        val job = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isProcessing) {
                delay(1000)
                remaining--
                mainHandler.post { listener?.onTimerUpdate(roomId, remaining) }
            }
            if (remaining <= 0 && isProcessing) {
                mainHandler.post { 
                    listener?.onTimerExpired(roomId)
                    // Auto-close room when timer expires
                    deleteRoom(roomId)
                }
            }
        }
        timerJobs[roomId] = job
    }
    
    private fun cancelTimer(roomId: String) {
        timerJobs.remove(roomId)?.cancel()
    }
    
    @Volatile
    private var isProcessing = true
    
    fun clear() = synchronized(lock) {
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        rooms.clear()
    }
    
    fun shutdown() {
        isProcessing = false
        clear()
        scope.cancel()
    }
}
