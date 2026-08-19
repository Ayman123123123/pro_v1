package com.red.server.calls

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("call_history")
data class CallHistoryDocument(
    @Id val id: String,
    @Indexed val initiatorId: String,
    @Indexed val targetId: String,
    val targetLabel: String,
    val type: CallType,
    val route: CallRoute,
    var status: CallStatus,
    val startedAt: Instant = Instant.now(),
    var answeredAt: Instant? = null,
    var endedAt: Instant? = null,
    var mediaServerId: String? = null,
    var gatewayUsed: String? = null,
    var durationSeconds: Long = 0L,
    var qualityScore: Float = 0f,
    var callSource: String = "PRIVATE",
    var groupId: String? = null,
    var roomId: String? = null,
    var participantIds: List<String> = emptyList(),
    var hadScreenShare: Boolean = false,
    var wasRecorded: Boolean = false
)

enum class CallType { AUDIO_1V1, VIDEO_1V1, GROUP_AUDIO, GROUP_VIDEO, LIVE_STREAM, SPACE }
enum class CallRoute { RED, DINSTAR }
enum class CallStatus { INITIATED, RINGING, ACTIVE, ENDED, MISSED, REJECTED, BUSY, FAILED }

data class CallHistoryItem(
    val id: String,
    val peerId: String,
    val peerLabel: String,
    val direction: String,
    val type: CallType,
    val route: CallRoute,
    val status: CallStatus,
    val startedAt: Instant,
    val answeredAt: Instant?,
    val endedAt: Instant?,
    val mediaServerId: String? = null,
    val gatewayUsed: String? = null,
    val durationSeconds: Long = 0L,
    val qualityScore: Float = 0f,
    val callSource: String = "PRIVATE",
    val groupId: String? = null,
    val roomId: String? = null,
    val participantIds: List<String> = emptyList(),
    val hadScreenShare: Boolean = false,
    val wasRecorded: Boolean = false
)
