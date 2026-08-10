package com.red.sovereign.calls

import kotlinx.serialization.Serializable

@Serializable
data class CallHistoryItem(
    val id: String,
    val peerId: String,
    val peerLabel: String,
    val direction: String,
    val type: String,
    val route: String,
    val status: String,
    val startedAt: String,
    val answeredAt: String? = null,
    val endedAt: String? = null
)
