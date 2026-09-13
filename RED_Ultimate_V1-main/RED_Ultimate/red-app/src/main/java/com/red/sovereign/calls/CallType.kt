package com.red.sovereign.calls

/**
 * أنواع المكالمات الموحدة — كل نوع بمعماريته المناسبة للمشروع.
 */
enum class CallType(
    val displayName: String,
    val icon: String,
    val maxParticipants: Int,
    val architecture: Architecture,
    val supportsVideo: Boolean,
    val supportsScreenShare: Boolean,
    val supportsRecording: Boolean,
    val supportsReactions: Boolean,
    val supportsBreakoutRooms: Boolean
) {
    PRIVATE_VOICE("مكالمة صوتية", "call", 2, Architecture.P2P_MESH, false, false, true, true, false),
    PRIVATE_VIDEO("مكالمة فيديو", "videocam", 2, Architecture.P2P_MESH, true, true, true, true, false),

    GROUP_CHAT_VOICE("مكالمة مجموعة دردشة صوتية", "group", 4, Architecture.P2P_MESH, false, false, true, true, false),
    GROUP_CHAT_VIDEO("مكالمة مجموعة دردشة فيديو", "group", 4, Architecture.P2P_MESH_SFU_FALLBACK, true, true, true, true, false),

    GROUP_CALL_VOICE("مكالمة جماعية كبيرة صوتية", "mic", 100, Architecture.SFU, false, true, true, true, true),
    GROUP_CALL_VIDEO("مكالمة جماعية كبيرة فيديو", "videocam", 50, Architecture.SFU, true, true, true, true, true),

    CONFERENCE_VIDEO("مؤتمر فيديو", "business", 100, Architecture.SFU, true, true, true, true, true),
    AUDIO_SPACE("مساحة صوتية", "headset", 10_000, Architecture.SFU_SPEAKERS_MIXED_LISTENERS, false, true, true, true, false),

    LIVE_STREAM_VIDEO("بث مباشر فيديو", "live_tv", 100_000, Architecture.SFU_BROADCAST_HLS, true, true, true, true, false),
    LIVE_STREAM_AUDIO("بث مباشر صوتي", "mic", 100_000, Architecture.SFU_BROADCAST_HLS, false, true, true, true, false),

    PSTN_GSM("مكالمة هاتفية GSM", "phone", 1, Architecture.PSTN_LEGACY, false, false, true, false, false),
    PSTN_WEBRTC("مكالمة هاتفية WebRTC", "phone", 1, Architecture.PSTN_WEBRTC_SIP, true, false, true, false, false);

    enum class Architecture(
        val label: String,
        val description: String,
        val bestFor: String
    ) {
        P2P_MESH("P2P Mesh", "Peer-to-Peer مباشر", "1:1 وصفر تكلفة سيرفر"),
        P2P_MESH_SFU_FALLBACK("Mesh + SFU Fallback", "Mesh للـ 2-4 ثم SFU", "مجموعات الدردشة"),
        SFU("SFU", "Selective Forwarding Unit", "جماعية 5+ ومؤتمرات"),
        SFU_SPEAKERS_MIXED_LISTENERS("SFU + Mixed Listeners", "متحدثون SFU ومستمعون بتدفق مختلط", "X Spaces"),
        SFU_BROADCAST_HLS("SFU Broadcast + HLS", "1-to-many + HLS للأعداد الكبيرة", "YouTube/TikTok Live"),
        PSTN_LEGACY("PSTN Legacy", "AMI → Asterisk → DINSTAR", "هاتف GSM"),
        PSTN_WEBRTC_SIP("PSTN WebRTC-SIP", "WebRTC-SIP → Asterisk WSS → DINSTAR", "هاتف WebRTC")
    }

    companion object {
        fun fromString(type: String): CallType? = entries.firstOrNull { it.name == type }
        fun allVoiceTypes() = entries.filter { !it.supportsVideo }
        fun allVideoTypes() = entries.filter { it.supportsVideo }
        fun groupChatCallTypes() = entries.filter { it.name.startsWith("GROUP_CHAT_") }
        fun groupCallHubTypes() = entries.filter { it.name.startsWith("GROUP_CALL_") }
        fun conferenceTypes() = entries.filter { it == CONFERENCE_VIDEO || it == AUDIO_SPACE }
        fun liveStreamTypes() = entries.filter { it.name.startsWith("LIVE_STREAM") }
        fun pstnTypes() = entries.filter { it.name.startsWith("PSTN") }
    }
}
