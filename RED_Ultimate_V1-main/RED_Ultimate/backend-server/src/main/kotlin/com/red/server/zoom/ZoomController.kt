package com.red.server.zoom

import com.red.server.auth.repository.UserAccountRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/zoom")
class ZoomController(
    private val zoomRooms: ZoomRoomService,
    private val users: UserAccountRepository
) {
    @PostMapping("/create")
    fun create(@RequestBody req: CreateZoomRequest, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val meetingId = req.meetingId.trim().ifBlank { ZoomGroupCallServiceHelper.generateId() }
        val room = zoomRooms.createRoom(meetingId, user.id.toString(), user.redId, req.title, req.isVideo)
        return ResponseEntity.ok(mapOf("meetingId" to room.meetingId, "title" to room.title, "isVideo" to room.isVideo))
    }

    @GetMapping("/{meetingId}")
    fun getRoom(@PathVariable meetingId: String): ResponseEntity<ZoomRoomRecord> {
        val room = zoomRooms.getRoom(meetingId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(room)
    }

    @PostMapping("/{meetingId}/join")
    fun join(@PathVariable meetingId: String, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val room = zoomRooms.getRoom(meetingId) ?: return ResponseEntity.notFound().build()
        zoomRooms.addParticipant(meetingId, authentication.name)
        return ResponseEntity.ok(mapOf("meetingId" to meetingId, "title" to room.title))
    }

    @PostMapping("/{meetingId}/leave")
    fun leave(@PathVariable meetingId: String, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        zoomRooms.removeParticipant(meetingId, authentication.name)
        return ResponseEntity.ok(mapOf("meetingId" to meetingId))
    }

    @PostMapping("/{meetingId}/close")
    fun close(@PathVariable meetingId: String, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val closed = zoomRooms.closeRoom(meetingId, authentication.name)
        return ResponseEntity.ok(mapOf("closed" to closed))
    }
}

data class CreateZoomRequest(val meetingId: String = "", val title: String = "", val isVideo: Boolean = true)

object ZoomGroupCallServiceHelper {
    fun generateId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("").chunked(4).joinToString("-")
    }
}
