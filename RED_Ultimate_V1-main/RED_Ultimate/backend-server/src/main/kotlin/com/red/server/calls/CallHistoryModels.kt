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
    var endedAt: Instant? = null
)

enum class CallType { VOICE, VIDEO, GROUP, LIVE, SPACE }
enum class CallRoute { RED, DINSTAR }
enum class CallStatus { RINGING, ACTIVE, ENDED, MISSED, FAILED }

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
    val endedAt: Instant?
)
