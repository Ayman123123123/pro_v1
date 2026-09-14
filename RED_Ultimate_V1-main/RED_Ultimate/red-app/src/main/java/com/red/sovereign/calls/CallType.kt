package com.red.sovereign.calls

/**
 * أنواع المكالمات الموحدة - نظيف بدون PSTN/DINSTAR
 * معمارية 2026: P2P للخاص، SFU للجماعي، بث هجين للبث المباشر
 * 
 * - مكالمات خاصة: صوت منفصل وفيديو منفصل (رنين، جودة، واجهات أفضل من واتس وتيليجرام)
 * - مكالمات مجموعات الدردشة: صوت/فيديو كل على حدة
 * - مكالمات جماعية للأصدقاء: تشبه زووم/إيمو منفصلة تماماً
 * - بث مباشر: أفضل من تيك توك ويوتيوب (تفاعلات فقط بدون هدايا)
 * - مؤتمرات ومساحات: أفضل من تويتر X
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
    // مكالمات خاصة 1:1 - P2P مباشر، رنين، جودة عالية
    PRIVATE_VOICE("مكالمة صوتية", "call", 2, Architecture.P2P_MESH, false, false, true, true, false),
    PRIVATE_VIDEO("مكالمة فيديو", "videocam", 2, Architecture.P2P_MESH, true, true, true, true, false),

    // مكالمات مجموعات الدردشة - Mesh + SFU fallback
    GROUP_CHAT_VOICE("مكالمة مجموعة دردشة صوتية", "group", 8, Architecture.P2P_MESH_SFU_FALLBACK, false, false, true, true, false),
    GROUP_CHAT_VIDEO("مكالمة مجموعة دردشة فيديو", "group", 8, Architecture.P2P_MESH_SFU_FALLBACK, true, true, true, true, false),

    // مكالمات جماعية للأصدقاء تشبه زووم/إيمو - منفصلة تماماً عن مكالمات المحادثات
    GROUP_CALL_VOICE("مكالمة جماعية صوتية", "mic", 100, Architecture.SFU, false, true, true, true, true),
    GROUP_CALL_VIDEO("مكالمة جماعية فيديو", "videocam", 50, Architecture.SFU, true, true, true, true, true),

    // مؤتمرات ومساحات صوتية أفضل من تويتر X
    CONFERENCE_VIDEO("مؤتمر فيديو", "business", 500, Architecture.SFU, true, true, true, true, true),
    CONFERENCE_VOICE("مؤتمر صوتي", "headset", 500, Architecture.SFU, false, true, true, true, true),
    AUDIO_SPACE("مساحة صوتية", "headset", 10_000, Architecture.SFU_SPEAKERS_MIXED_LISTENERS, false, true, true, true, false),

    // بث مباشر أفضل من تيك توك ويوتيوب - تفاعلات فقط
    LIVE_STREAM_VIDEO("بث مباشر فيديو", "live_tv", 100_000, Architecture.SFU_BROADCAST_HLS, true, true, true, true, false),
    LIVE_STREAM_AUDIO("بث مباشر صوتي", "mic", 100_000, Architecture.SFU_BROADCAST_HLS, false, true, true, true, false);

    enum class Architecture(
        val label: String,
        val description: String,
        val bestFor: String
    ) {
        P2P_MESH("P2P Mesh", "Peer-to-Peer مباشر مع STUN/TURN", "1:1 وصفر تكلفة سيرفر - أفضل من واتس"),
        P2P_MESH_SFU_FALLBACK("Mesh + SFU Fallback", "Mesh للـ 2-4 ثم SFU تلقائياً", "مجموعات الدردشة الصغيرة"),
        SFU("SFU", "Selective Forwarding Unit - بدون تحويل ترميز", "جماعية 5+ ومؤتمرات زووم - simulcast 3 طبقات"),
        SFU_SPEAKERS_MIXED_LISTENERS("SFU + Mixed Listeners", "متحدثون SFU ومستمعون بتدفق مختلط", "X Spaces - 13 متحدث + مستمعين لا نهائي"),
        SFU_BROADCAST_HLS("SFU Broadcast + HLS", "WebRTC <500ms للمتفاعلين + LL-HLS للجمهور", "TikTok/YouTube Live - هجين تفاعلي")
    }

    companion object {
        fun fromString(type: String): CallType? = entries.firstOrNull { it.name == type }
        fun allVoiceTypes() = entries.filter { !it.supportsVideo }
        fun allVideoTypes() = entries.filter { it.supportsVideo }
        fun groupChatCallTypes() = entries.filter { it.name.startsWith("GROUP_CHAT_") }
        fun groupCallHubTypes() = entries.filter { it.name.startsWith("GROUP_CALL_") }
        fun conferenceTypes() = entries.filter { it.name in setOf("CONFERENCE_VIDEO", "CONFERENCE_VOICE", "AUDIO_SPACE") }
        fun liveStreamTypes() = entries.filter { it.name.startsWith("LIVE_STREAM") }
        fun privateCallTypes() = entries.filter { it.name.startsWith("PRIVATE_") }
        fun zoomLikeTypes() = entries.filter { it.name.startsWith("GROUP_CALL_") || it.name.startsWith("CONFERENCE_") }
    }
}
