package com.red.sovereign.calls

import kotlinx.serialization.Serializable
import org.webrtc.IceCandidate

/**
 * إشارة المكالمة الموحدة — متوافقة مع YounesCallService / GroupCallService / Backend.
 */
@Serializable
data class CallSignal(
    val callId: String? = null,
    val callType: String = CallType.PRIVATE_VOICE.name,
    val targetUserId: String = "",
    val sourceUserId: String? = null,
    val type: String,
    val mode: String = "VOICE",
    val payload: Map<String, String> = emptyMap(),
    val groupCallId: String? = null,
    val inviteeIds: List<String> = emptyList(),
    val memberStatus: String? = null,
    val roomId: String? = null,
    val role: String? = null,
    val streamId: String? = null,
    val viewerCount: Int = 0,
    val phoneNumber: String? = null,
    val slotIndex: Int? = null,
    val simulcastLayer: Int = -1,
    val bandwidthEstimate: Long = 0,
    val rtt: Long = 0,
    val packetLoss: Double = 0.0
) {
    companion object {
        const val OFFER = "OFFER"
        const val ANSWER = "ANSWER"
        const val ICE = "ICE"
        const val RENEGOTIATE = "RENEGOTIATE"
        const val END = "END"
        const val REJECT = "REJECT"
        const val CANCELLED = "CANCELLED"
        const val UNAVAILABLE = "UNAVAILABLE"
        const val BUSY = "BUSY"
        const val RINGING = "RINGING"
        const val RINGING_PUSH_SENT = "RINGING_PUSH_SENT"
        const val ACK = "ACK"
        const val HOLD = "HOLD"
        const val RESUME = "RESUME"
        const val MUTE = "MUTE"
        const val UNMUTE = "UNMUTE"
        const val GROUP_CALL_INVITE = "GROUP_CALL_INVITE"
        const val GROUP_CALL_ACCEPT = "GROUP_CALL_ACCEPT"
        const val GROUP_CALL_DECLINE = "GROUP_CALL_DECLINE"
        const val GROUP_CALL_STATUS = "GROUP_CALL_STATUS"
        const val GROUP_CALL_END = "GROUP_CALL_END"
        const val GROUP_CALL_MUTE_ALL = "GROUP_CALL_MUTE_ALL"
        const val CONFERENCE_INVITE = "CONFERENCE_INVITE"
        const val LIVE_INVITE = "LIVE_INVITE"
        const val CALL_REACTION = "CALL_REACTION"
        const val CALL_RAISE_HAND = "CALL_RAISE_HAND"
        const val SIMULCAST_LAYER_CHANGE = "SIMULCAST_LAYER_CHANGE"
        const val BANDWIDTH_ESTIMATE = "BANDWIDTH_ESTIMATE"
        const val ERROR = "ERROR"

        fun createOffer(callId: String, targetUserId: String, mode: String, sdp: String): CallSignal =
            CallSignal(callId = callId, targetUserId = targetUserId, type = OFFER, mode = mode, payload = mapOf("sdp" to sdp))

        fun createAnswer(callId: String, targetUserId: String, mode: String, sdp: String): CallSignal =
            CallSignal(callId = callId, targetUserId = targetUserId, type = ANSWER, mode = mode, payload = mapOf("sdp" to sdp))

        fun createIce(callId: String, targetUserId: String, candidate: IceCandidate): CallSignal =
            CallSignal(
                callId = callId,
                targetUserId = targetUserId,
                type = ICE,
                payload = mapOf(
                    "sdpMid" to candidate.sdpMid.orEmpty(),
                    "sdpMLineIndex" to candidate.sdpMLineIndex.toString(),
                    "candidate" to candidate.sdp
                )
            )

        fun createEnd(callId: String, targetUserId: String, mode: String): CallSignal =
            CallSignal(callId = callId, targetUserId = targetUserId, type = END, mode = mode)

        fun createReject(callId: String, targetUserId: String, mode: String): CallSignal =
            CallSignal(callId = callId, targetUserId = targetUserId, type = REJECT, mode = mode)
    }
}
