package com.red.server.calls

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * متحكم المكالمات الحديث - يدير كل أنواع المكالمات بشكل منفصل ومنظم
 * 
 * كل نوع له عمله الأساسي والمتعارف وواجهته المناسبة:
 * 
 * 1. فردية صوت/فيديو: P2P WebRTC مباشر، ترن وتتعرف، E2EE
 * 2. جماعية: Mesh حتى 8، SFU بعد ذلك، ترن الجميع حتى 32 (مثل واتساب)
 * 3. مؤتمر/Zoom: حتى 100 مشارك عبر SFU، غرف جانبية، رفع يد
 * 4. بث مباشر: 1-to-N مع دردشة وهدايا، عام/خاص بكلمة سر
 * 5. مساحات صوتية: صوت فقط، مضيف ومتحدثون ومستمعون
 * 6. هاتف يمني: عبر DINSTAR وشرائح يمنية
 * 7. محلي P2P: بلا إنترنت ولا خادم، نفس الواي فاي
 */

data class StartCallRequest(
    val targetId: String? = null,
    val type: String, // AUDIO_1V1, VIDEO_1V1, GROUP_AUDIO, GROUP_VIDEO, CONFERENCE, LIVE, SPACE, PSTN, LAN
    val isVideo: Boolean = false,
    val groupId: String? = null,
    val participantIds: List<String> = emptyList(),
    val title: String? = null,
    val isPrivate: Boolean = false,
    val password: String? = null
)

data class CallResponse(
    val callId: String,
    val type: String,
    val status: String,
    val message: String,
    val iceServers: List<Map<String, Any>>? = null,
    val sfuTicket: String? = null
)

@RestController
@RequestMapping("/api/calls/v2")
class ModernCallsController(
    private val history: CallHistoryService,
    private val deliveryService: UnifiedCallDeliveryService
) {

    @PostMapping("/start")
    fun startCall(
        @RequestBody request: StartCallRequest,
        authentication: Authentication
    ): ResponseEntity<CallResponse> {
        val callerId = authentication.name
        
        // التحقق من الصلاحيات والحدود
        when (request.type.uppercase()) {
            "GROUP_AUDIO", "GROUP_VIDEO" -> {
                require(request.participantIds.size <= 32) { "Group call limit is 32 (WhatsApp limit)" }
                require(request.participantIds.isNotEmpty()) { "Participants required for group call" }
            }
            "CONFERENCE" -> {
                require(request.participantIds.size <= 100) { "Conference limit is 100" }
            }
            "AUDIO_1V1", "VIDEO_1V1" -> {
                require(!request.targetId.isNullOrBlank()) { "Target required for 1-1 call" }
                require(request.targetId != callerId) { "Cannot call yourself" }
            }
        }
        
        val callId = "call_${System.currentTimeMillis()}_${(1000..9999).random()}"
        
        // حفظ في التاريخ
        val callType = when (request.type.uppercase()) {
            "AUDIO_1V1" -> CallType.AUDIO_1V1
            "VIDEO_1V1" -> CallType.VIDEO_1V1
            "GROUP_AUDIO" -> CallType.GROUP_AUDIO
            "GROUP_VIDEO" -> CallType.GROUP_VIDEO
            "CONFERENCE" -> CallType.CONFERENCE
            "LIVE" -> CallType.LIVE_STREAM
            "SPACE" -> CallType.SPACE
            "PSTN" -> CallType.PSTN
            else -> CallType.AUDIO_1V1
        }
        
        val route = when (request.type.uppercase()) {
            "PSTN" -> CallRoute.DINSTAR
            "LAN" -> CallRoute.LAN_P2P
            else -> CallRoute.RED
        }
        
        // تسليم المكالمة عبر المسارات المتعددة
        if (request.type.uppercase() in listOf("GROUP_AUDIO", "GROUP_VIDEO")) {
            deliveryService.deliverGroupCall(
                callId = callId,
                hostId = callerId,
                hostName = callerId,
                memberIds = request.participantIds,
                isVideo = request.isVideo,
                groupId = request.groupId
            )
        } else if (!request.targetId.isNullOrBlank()) {
            deliveryService.deliverCall(
                callId = callId,
                sourceId = callerId,
                targetId = request.targetId,
                type = "OFFER",
                mode = request.type,
                payload = mapOf(
                    "isVideo" to request.isVideo,
                    "title" to request.title
                )
            )
        }
        
        // إنشاء ICE servers
        val iceServers = listOf(
            mapOf("urls" to listOf("stun:stun.l.google.com:19302")),
            mapOf("urls" to listOf("stun:stun1.l.google.com:19302"))
        )
        
        return ResponseEntity.ok(
            CallResponse(
                callId = callId,
                type = request.type,
                status = "RINGING",
                message = "Call started - Ringing ${request.targetId ?: "${request.participantIds.size} participants"}",
                iceServers = iceServers
            )
        )
    }
    
    @PostMapping("/{callId}/answer")
    fun answerCall(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<CallResponse> {
        val answererId = authentication.name
        deliveryService.onCallAnswered(callId, answererId)
        
        return ResponseEntity.ok(
            CallResponse(
                callId = callId,
                type = "ANSWER",
                status = "ACTIVE",
                message = "Call answered by $answererId"
            )
        )
    }
    
    @PostMapping("/{callId}/ringing")
    fun confirmRinging(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<CallResponse> {
        val targetId = authentication.name
        deliveryService.onRingingConfirmed(callId, targetId)
        
        return ResponseEntity.ok(
            CallResponse(
                callId = callId,
                type = "RINGING",
                status = "RINGING_CONFIRMED",
                message = "Ringing confirmed by $targetId - Stopping retry"
            )
        )
    }
    
    @PostMapping("/{callId}/end")
    fun endCall(
        @PathVariable callId: String,
        @RequestParam(required = false) reason: String?,
        authentication: Authentication
    ): ResponseEntity<CallResponse> {
        val enderId = authentication.name
        deliveryService.onCallEnded(callId, enderId, reason ?: "COMPLETED")
        
        return ResponseEntity.ok(
            CallResponse(
                callId = callId,
                type = "END",
                status = "ENDED",
                message = "Call ended by $enderId reason=${reason ?: "COMPLETED"}"
            )
        )
    }
    
    @PostMapping("/{callId}/reject")
    fun rejectCall(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<CallResponse> {
        val rejecterId = authentication.name
        deliveryService.onCallEnded(callId, rejecterId, "REJECTED")
        
        return ResponseEntity.ok(
            CallResponse(
                callId = callId,
                type = "REJECT",
                status = "REJECTED",
                message = "Call rejected by $rejecterId"
            )
        )
    }
    
    @GetMapping("/types")
    fun getCallTypes(): ResponseEntity<List<Map<String, String>>> {
        val types = listOf(
            mapOf(
                "id" to "ONE_TO_ONE_AUDIO",
                "name" to "فردية صوتية",
                "description" to "مكالمة صوتية فردية مشفرة E2EE عبر WebRTC P2P مباشر، ترن وتتعرف، تعمل على الشبكة المحلية وكل الشبكات",
                "route" to "RED WebRTC P2P → TURN → RED ID",
                "maxParticipants" to "2",
                "icon" to "📞",
                "tech" to "WebRTC + DTLS-SRTP + TURN + FCM Push"
            ),
            mapOf(
                "id" to "ONE_TO_ONE_VIDEO",
                "name" to "فردية فيديو",
                "description" to "مكالمة فيديو فردية مشفرة مع مشاركة شاشة وكاميرا أمامية/خلفية، تسجيل اختياري",
                "route" to "RED WebRTC P2P Video → TURN → RED ID",
                "maxParticipants" to "2",
                "icon" to "📹",
                "tech" to "WebRTC Video + VP8/VP9/H264 + Simulcast"
            ),
            mapOf(
                "id" to "GROUP_AUDIO",
                "name" to "جماعية صوتية",
                "description" to "مكالمة جماعية صوتية حتى 32 مشارك - Mesh حتى 8، SFU بعد ذلك، ترن الجميع مثل واتساب",
                "route" to "RED Mesh (<8) / SFU (8-32) → WebRTC → RED IDs",
                "maxParticipants" to "32",
                "icon" to "👥📞",
                "tech" to "Mesh + SFU mediasoup + Audio Level Observer"
            ),
            mapOf(
                "id" to "GROUP_VIDEO",
                "name" to "جماعية فيديو",
                "description" to "مكالمة جماعية فيديو حتى 32 مشارك - شبكية مع SFU، ترن الجميع، كتم الكل، طرد",
                "route" to "RED SFU Video → WebRTC → Room",
                "maxParticipants" to "32",
                "icon" to "👥📹",
                "tech" to "SFU + VP9 + Active Speaker Detection"
            ),
            mapOf(
                "id" to "CONFERENCE",
                "name" to "مؤتمر/Zoom",
                "description" to "مؤتمر فيديو حتى 100 مشارك عبر SFU مع غرف جانبية ورفع يد وتسجيل ومشاركة شاشة",
                "route" to "RED SFU (mediasoup) → WebRTC → Conference Room",
                "maxParticipants" to "100",
                "icon" to "🎥",
                "tech" to "mediasoup SFU + Breakout Rooms + Recording"
            ),
            mapOf(
                "id" to "LIVE_STREAM",
                "name" to "بث مباشر",
                "description" to "بث مباشر 1-to-N مع دردشة وتفاعلات وهدايا، عام أو خاص بكلمة سر، دعوة أصدقاء",
                "route" to "RED SFU 1-to-N → WebRTC → Viewers + Chat",
                "maxParticipants" to "1000",
                "icon" to "🔴",
                "tech" to "SFU 1-to-N + Live Chat + Gifts"
            ),
            mapOf(
                "id" to "SPACE_AUDIO",
                "name" to "مساحة صوتية",
                "description" to "مساحة صوتية جماعية - صوت فقط بلا فيديو، مضيف ومتحدثون ومستمعون مثل تويتر سبيس",
                "route" to "RED SFU Audio-Only → WebRTC → Space",
                "maxParticipants" to "100",
                "icon" to "🎙️",
                "tech" to "SFU Audio-Only + Roles (Host/Speaker/Listener)"
            ),
            mapOf(
                "id" to "PSTN_YEMENI",
                "name" to "هاتف يمني",
                "description" to "هاتف يمني عبر بوابة DINSTAR وشرائح يمن موبايل وسبأفون وYOU والهاتف الثابت - حصري",
                "route" to "Android → Backend Auth → Asterisk AMI → DINSTAR → SIM → Yemen Network",
                "maxParticipants" to "2",
                "icon" to "☎️🇾🇪",
                "tech" to "Asterisk + DINSTAR UC2000-VE + Yemen Operators"
            ),
            mapOf(
                "id" to "LAN_P2P",
                "name" to "محلي P2P",
                "description" to "مكالمة محلية P2P بلا إنترنت ولا خادم - نفس الواي فاي، مشفرة DTLS-SRTP، اكتشاف NSD",
                "route" to "NSD Discovery → DTLS-SRTP P2P → No Server",
                "maxParticipants" to "2",
                "icon" to "📶",
                "tech" to "NSD/mDNS + WebRTC Host-Only + No Internet"
            )
        )
        
        return ResponseEntity.ok(types)
    }
    
    @GetMapping("/stats")
    fun getCallStats(authentication: Authentication): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "pendingDeliveries" to deliveryService.getPendingCount(),
            "supportedTypes" to 9,
            "maxGroupSize" to 32,
            "maxConferenceSize" to 100,
            "features" to listOf(
                "E2EE for 1-1",
                "Mesh + SFU for groups",
                "SFU for conferences",
                "1-to-N for live",
                "Audio-only spaces",
                "PSTN via DINSTAR",
                "LAN P2P without internet",
                "Multi-path delivery",
                "FCM Push ringing",
                "Works on all local networks"
            )
        ))
    }
}
