package com.red.server.calls.v1

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.LiveStreamService
import com.red.server.calls.ScheduledCallController
import com.red.server.services.NotificationService
import com.red.server.websocket.CallWebSocketHandler
import com.red.server.websocket.ConferenceWebSocketHandler
import com.red.server.websocket.LiveStreamWebSocketHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import java.util.UUID
import java.time.Instant

@RestController
@RequestMapping("/api/v1/calls")
class CallsV1Controller(
    private val callSignaling: CallWebSocketHandler,
    private val conferenceSignaling: ConferenceWebSocketHandler,
    private val liveStreamService: LiveStreamService,
    private val liveStreamSignaling: LiveStreamWebSocketHandler,
    private val callHistory: CallHistoryService,
    private val users: UserAccountRepository,
    private val notifications: NotificationService,
    private val scheduledCallController: ScheduledCallController
) {

    @PostMapping("/initiate")
    fun initiate(
        @Valid @RequestBody request: InitiateCallRequest,
        authentication: Authentication
    ): ResponseEntity<InitiateCallResponse> {
        val callerId = UUID.fromString(authentication.name)
        val caller = users.findById(callerId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val target = users.findByRedId(request.targetRedId)
            .orElseThrow { NoSuchElementException("Target user not found") }
        
        // Check if blocked
        val blocked = users.isBlocked(caller.redId, target.redId)
        if (blocked) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(InitiateCallResponse(
                success = false,
                error = "CALL_BLOCKED"
            ))
        }
        
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val roomId = "call_${callId}"
        
        // Store call offer for offline delivery
        callSignaling.storeCallOffer(
            callId = callId,
            callerRedId = caller.redId,
            targetRedId = target.redId,
            mode = request.mode,
            offerSdp = request.offerSdp,
            ttlSeconds = 120
        )
        
        // Send push notification
        notifications.sendVoipPushNotification(
            target.redId,
            caller.redId,
            callId,
            request.mode
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(InitiateCallResponse(
            success = true,
            callId = callId,
            roomId = roomId
        ))
    }

    @PostMapping("/group/initiate")
    fun initiateGroup(
        @Valid @RequestBody request: InitiateGroupCallRequest,
        authentication: Authentication
    ): ResponseEntity<InitiateGroupCallResponse> {
        val callerId = UUID.fromString(authentication.name)
        val caller = users.findById(callerId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val groupId = request.groupId
        val members = callSignaling.getGroupCallMembers(groupId)
        val validInvitees = request.inviteeIds.filter { id ->
            users.findByRedId(id) != null && !users.isBlocked(caller.redId, id)
        }
        
        val callId = "group_call_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        
        conferenceSignaling.createGroupCall(
            groupCallId = callId,
            hostRedId = caller.redId,
            initialMembers = validInvitees,
            mode = request.mode
        )
        
        // Notify invitees
        validInvitees.forEach { inviteeId ->
            notifications.sendVoipPushNotification(inviteeId, caller.redId, callId, request.mode)
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(InitiateGroupCallResponse(
            success = true,
            callId = callId,
            groupId = groupId,
            invitedCount = validInvitees.size
        ))
    }

    @PostMapping("/{callId}/answer")
    fun answer(
        @PathVariable callId: String,
        @Valid @RequestBody request: AnswerCallRequest,
        authentication: Authentication
    ): ResponseEntity<AnswerCallResponse> {
        val calleeId = UUID.fromString(authentication.name)
        val callee = users.findById(calleeId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val offer = callSignaling.takeCallOffer(callId, callee.redId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(AnswerCallResponse(
                success = false,
                error = "CALL_NOT_FOUND_OR_EXPIRED"
            ))
        
        // Notify caller
        callSignaling.sendAnswerToCaller(offer.callId, callee.redId, request.answerSdp)
        
        return ResponseEntity.ok(AnswerCallResponse(
            success = true,
            callId = callId,
            callerRedId = offer.callerId
        ))
    }

    @PostMapping("/{callId}/reject")
    fun reject(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<RejectCallResponse> {
        val calleeId = UUID.fromString(authentication.name)
        val callee = users.findById(calleeId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val offer = callSignaling.takeCallOffer(callId, callee.redId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(RejectCallResponse(
                success = false,
                error = "CALL_NOT_FOUND_OR_EXPIRED"
            ))
        
        callSignaling.sendRejectToCaller(offer.callId, callee.redId)
        
        return ResponseEntity.ok(RejectCallResponse(
            success = true,
            callId = callId
        ))
    }

    @PostMapping("/{callId}/end")
    fun end(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<EndCallResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        callSignaling.endCall(callId, user.redId)
        conferenceSignaling.endGroupCall(callId, user.redId)
        
        return ResponseEntity.ok(EndCallResponse(
            success = true,
            callId = callId
        ))
    }

    @PostMapping("/{callId}/ice")
    fun iceCandidate(
        @PathVariable callId: String,
        @Valid @RequestBody request: IceCandidateRequest,
        authentication: Authentication
    ): ResponseEntity<IceCandidateResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        callSignaling.relayIceCandidate(callId, user.redId, request.candidate, request.sdpMLineIndex, request.sdpMid)
        
        return ResponseEntity.ok(IceCandidateResponse(success = true))
    }

    @PostMapping("/{callId}/screen-share")
    fun screenShare(
        @PathVariable callId: String,
        @Valid @RequestBody request: ScreenShareRequest,
        authentication: Authentication
    ): ResponseEntity<ScreenShareResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        callSignaling.toggleScreenShare(callId, user.redId, request.enabled)
        
        return ResponseEntity.ok(ScreenShareResponse(
            success = true,
            callId = callId,
            enabled = request.enabled
        ))
    }

    @PostMapping("/{callId}/record")
    fun record(
        @PathVariable callId: String,
        @Valid @RequestBody request: RecordCallRequest,
        authentication: Authentication
    ): ResponseEntity<RecordCallResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val recordingId = callSignaling.startRecording(callId, user.redId, request.mode)
        
        return ResponseEntity.ok(RecordCallResponse(
            success = recordingId != null,
            callId = callId,
            recordingId = recordingId
        ))
    }

    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) since: Long?,
        @RequestParam(required = false) offset: Int? = null,
        authentication: Authentication
    ): List<com.red.server.calls.CallHistoryItem> {
        val user = users.findById(UUID.fromString(authentication.name)).orElseThrow { NoSuchElementException("User not found") }
        val sinceInstant = since?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        return callHistory.history(user.redId, limit, offset ?: 0, sinceInstant) { peerRedId ->
            runCatching { users.findByRedId(peerRedId)?.displayName }.getOrNull()
        }
    }
}

data class InitiateCallRequest(
    val targetRedId: String,
    val mode: String, // VOICE, VIDEO
    val offerSdp: String
)

data class InitiateCallResponse(
    val success: Boolean,
    val callId: String? = null,
    val roomId: String? = null,
    val error: String? = null
)

data class InitiateGroupCallRequest(
    val groupId: String,
    val inviteeIds: List<String>,
    val mode: String // VOICE, VIDEO
)

data class InitiateGroupCallResponse(
    val success: Boolean,
    val callId: String? = null,
    val groupId: String? = null,
    val invitedCount: Int = 0
)

data class AnswerCallRequest(
    val answerSdp: String
)

data class AnswerCallResponse(
    val success: Boolean,
    val callId: String? = null,
    val callerRedId: String? = null,
    val error: String? = null
)

data class RejectCallResponse(
    val success: Boolean,
    val callId: String? = null,
    val error: String? = null
)

data class EndCallResponse(
    val success: Boolean,
    val callId: String? = null
)

data class IceCandidateRequest(
    val candidate: String,
    val sdpMLineIndex: Int,
    val sdpMid: String?
)

data class IceCandidateResponse(
    val success: Boolean
)

data class ScreenShareRequest(
    val enabled: Boolean
)

data class ScreenShareResponse(
    val success: Boolean,
    val callId: String,
    val enabled: Boolean
)

data class RecordCallRequest(
    val mode: String // LOCAL, SERVER
)

data class RecordCallResponse(
    val success: Boolean,
    val callId: String,
    val recordingId: String? = null
)