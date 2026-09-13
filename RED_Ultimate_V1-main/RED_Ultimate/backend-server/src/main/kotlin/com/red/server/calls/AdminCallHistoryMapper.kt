package com.red.server.calls

import java.util.UUID

/** Maps internal RED call events onto the admin call-history representation. */
object AdminCallHistoryMapper {
    fun sqlType(type: String): String = when (type.uppercase()) {
        "VIDEO" -> "VOIP_VIDEO"
        "GROUP", "CONFERENCE" -> "CONFERENCE"
        "LIVE" -> "LIVE_BROADCAST"
        "SPACE" -> "AUDIO_SPACE"
        else -> "VOIP_AUDIO"
    }

    fun sqlId(callId: String): UUID =
        runCatching { UUID.fromString(callId) }.getOrElse {
            UUID.nameUUIDFromBytes("younes-call:$callId".toByteArray())
        }
}
