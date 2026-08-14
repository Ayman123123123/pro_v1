package com.red.server.calls

import java.util.UUID

/**
 * Maps the live Mongo call document onto the admin Postgres call_history row.
 * Admin analytics expect VOIP_AUDIO / CONFERENCE / LIVE_BROADCAST — not the
 * product enums VOICE / GROUP / LIVE.
 */
object AdminCallHistoryMapper {
    fun sqlType(type: String, route: String): String {
        if (route.equals("DINSTAR", ignoreCase = true)) return "PSTN_DINSTAR"
        return when (type.uppercase()) {
            "VIDEO" -> "VOIP_VIDEO"
            "GROUP", "CONFERENCE" -> "CONFERENCE"
            "LIVE" -> "LIVE_BROADCAST"
            "SPACE" -> "AUDIO_SPACE"
            else -> "VOIP_AUDIO"
        }
    }

    fun sqlId(callId: String): UUID =
        runCatching { UUID.fromString(callId) }.getOrElse {
            UUID.nameUUIDFromBytes("younes-call:$callId".toByteArray())
        }

    fun looksLikePhone(targetId: String): Boolean =
        targetId.any(Char::isDigit) && targetId.none { it.isLetter() } && targetId.filter(Char::isDigit).length >= 6
}
