package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/livestream")
class LiveStreamController(
    private val liveStreamService: LiveStreamService,
    private val users: UserAccountRepository,
    private val notifications: NotificationService,
    private val history: CallHistoryService,
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler
) {

    @PostMapping("/create")
    fun create(
        @RequestBody request: CreateStreamRequest,
        authentication: Authentication
    ): ResponseEntity<LiveStreamResponse> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val streamId = request.streamId.trim().ifBlank { "stream_${UUID.randomUUID().toString().take(12)}" }
        require(streamId.matches(Regex("^[A-Za-z0-9_-]{8,128}$"))) { "INVALID_STREAM_ID" }
        val record = liveStreamService.createStream(
            streamId = streamId,
            broadcasterId = user.id.toString(),
            broadcasterName = user.displayName,
            broadcasterRedId = user.redId,
            title = request.title,
            isPrivate = request.isPrivate,
            password = request.password
        )
        val inviteLink = "younes://livestream/$streamId"
        return ResponseEntity.ok(LiveStreamResponse(
            streamId = record.streamId,
            title = record.title,
            broadcasterName = record.broadcasterName,
            broadcasterRedId = record.broadcasterRedId,
            isPrivate = record.isPrivate,
            viewerCount = 0,
            inviteLink = inviteLink
        ))
    }

    @GetMapping("/public")
    fun listPublic(
        @RequestParam(required = false) query: String?
    ): ResponseEntity<List<LiveStreamResponse>> {
        val streams = liveStreamService.searchPublicStreams(query)
        val responses = streams.map { record ->
            LiveStreamResponse(
                streamId = record.streamId,
                title = record.title,
                broadcasterName = record.broadcasterName,
                broadcasterRedId = record.broadcasterRedId,
                isPrivate = false,
                viewerCount = record.viewerCount,
                inviteLink = "younes://livestream/${record.streamId}"
            )
        }
        return ResponseEntity.ok(responses)
    }

    @PostMapping("/{streamId}/join")
    fun join(
        @PathVariable streamId: String,
        @RequestBody request: JoinStreamRequest,
        authentication: Authentication
    ): ResponseEntity<JoinStreamResponse> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val isAuth = liveStreamService.verifyPassword(streamId, request.password)
        if (!isAuth) {
            return ResponseEntity.status(403).body(JoinStreamResponse(
                authorized = false,
                streamId = streamId,
                errorMessage = "كلمة السر غير صحيحة"
            ))
        }
        liveStreamService.addViewer(streamId, authentication.name)
        return ResponseEntity.ok(JoinStreamResponse(
            authorized = true,
            streamId = record.streamId,
            title = record.title,
            isPrivate = record.isPrivate,
            broadcasterName = record.broadcasterName
        ))
    }

    @PostMapping("/{streamId}/leave")
    fun leave(
        @PathVariable streamId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        liveStreamService.removeViewer(streamId, authentication.name)
        return ResponseEntity.ok(mapOf("streamId" to streamId, "viewerCount" to liveStreamService.getViewerCount(streamId)))
    }

    @PostMapping("/{streamId}/stop")
    fun stop(
        @PathVariable streamId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        require(record.broadcasterId == authentication.name) { "ONLY_BROADCASTER_CAN_STOP" }
        return ResponseEntity.ok(mapOf("streamId" to streamId, "stopped" to liveStreamService.stopStream(streamId)))
    }

    @PostMapping("/{streamId}/invite")
    fun inviteFriends(
        @PathVariable streamId: String,
        @RequestBody request: InviteFriendsRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val accountId = UUID.fromString(authentication.name)
        val inviter = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        request.friendIds.filter { it.isNotBlank() && it != inviter.redId }.forEach { friendId ->
            notifications.sendVoipPushNotification(friendId, inviter.redId, streamId, "LIVESTREAM")
            callSignaling.deliverInvite(
                targetRedId = friendId,
                type = "LIVE_INVITE",
                roomId = streamId,
                sourceRedId = inviter.redId,
                mode = "LIVE",
                payload = mapOf("title" to record.title, "inviter" to inviter.displayName)
            )
        }
        return ResponseEntity.ok(mapOf("status" to "invited", "invitedCount" to request.friendIds.size, "streamId" to streamId))
    }
}

data class CreateStreamRequest(
    val streamId: String = "",
    val title: String = "",
    val isPrivate: Boolean = false,
    val password: String? = null
)

data class LiveStreamResponse(
    val streamId: String,
    val title: String,
    val broadcasterName: String,
    val broadcasterRedId: String,
    val isPrivate: Boolean,
    val viewerCount: Int,
    val inviteLink: String
)

data class JoinStreamRequest(
    val password: String? = null
)

data class JoinStreamResponse(
    val authorized: Boolean,
    val streamId: String,
    val title: String = "",
    val isPrivate: Boolean = false,
    val broadcasterName: String = "",
    val errorMessage: String? = null
)

data class InviteFriendsRequest(
    val friendIds: List<String> = emptyList()
)
