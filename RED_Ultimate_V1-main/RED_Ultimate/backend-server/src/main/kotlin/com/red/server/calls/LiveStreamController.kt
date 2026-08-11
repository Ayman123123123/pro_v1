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
    private val notifications: NotificationService
) {

    @PostMapping("/create")
    fun create(
        @RequestBody request: CreateStreamRequest,
        authentication: Authentication
    ): ResponseEntity<LiveStreamResponse> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val streamId = "stream_${UUID.randomUUID().toString().take(12)}"
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
        return ResponseEntity.ok(JoinStreamResponse(
            authorized = true,
            streamId = record.streamId,
            title = record.title,
            isPrivate = record.isPrivate,
            broadcasterName = record.broadcasterName
        ))
    }

    @PostMapping("/{streamId}/invite")
    fun inviteFriends(
        @PathVariable streamId: String,
        @RequestBody request: InviteFriendsRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val inviter = authentication.name
        request.friendIds.forEach { friendId ->
            notifications.sendVoipPushNotification(
                targetUserId = friendId,
                callerId = inviter,
                callId = streamId,
                mode = "LIVESTREAM"
            )
        }
        return ResponseEntity.ok(mapOf("status" to "invited", "invitedCount" to request.friendIds.size, "streamId" to streamId))
    }
}

data class CreateStreamRequest(
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
