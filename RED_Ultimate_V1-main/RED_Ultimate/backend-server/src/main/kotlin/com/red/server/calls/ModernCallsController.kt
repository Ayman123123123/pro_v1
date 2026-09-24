package com.red.server.calls

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

/**
 * متحكم المكالمات الحديث - يدير كل أنواع المكالمات بشكل منفصل ومنظم
 *
 * العقد الوحيد لـ `/api/calls/v2` (المتحكم المكرر UnifiedCallsControllerV2 عُطّل
 * وأُزيل تسجيله حتى لا تتسابق نسختان in-memory على نفس المسارات).
 *
 * - الهوية من Authentication فقط (JWT principal) — لا تُقرأ أي ترويسة X-RED-ID.
 * - السجل الدائم عبر CallHistoryService (Mongo call_history) — لا ConcurrentHashMap هنا.
 * - التسليم عبر UnifiedCallDeliveryService (WebSocket + Push + mailbox).
 * - الأنواع الخزنية هي CallType الستة؛ الأسماء القديمة (CONFERENCE/LEGACY/LAN/…)
 *   تُطبع على أقرب نظير دائم (موثق أدناه) بدل كيانين متوازيين.
 *
 * 1. فردية صوت/فيديو: P2P WebRTC مباشر، ترن وتتعرف، E2EE
 * 2. جماعية: Mesh حتى 8، SFU بعد ذلك، ترن الجميع حتى 32 (مثل واتساب)
 * 3. مؤتمر/Zoom: حتى 100 مشارك عبر SFU، غرف جانبية، رفع يد (يُخزَّن GROUP_VIDEO)
 * 4. بث مباشر: 1-to-N مع دردشة وهدايا، عام/خاص بكلمة سر (LIVE_STREAM)
 * 5. مساحات صوتية: صوت فقط، مضيف ومتحدثون ومستمعون (SPACE)
 * 6. هاتف يمني: عبر LEGACY-GW وشرائح يمنية (يُخزَّن AUDIO_1V1 + route RED)
 * 7. محلي P2P: بلا إنترنت ولا خادم، نفس الواي فاي (يُخزَّن AUDIO_1V1 + route RED)
 */

data class StartCallRequest(
    val targetId: String? = null,
    val type: String, // AUDIO_1V1, VIDEO_1V1, GROUP_AUDIO, GROUP_VIDEO, CONFERENCE, LIVE, SPACE, LAN
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
    private val deliveryService: UnifiedCallDeliveryService,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val jdbc: org.springframework.jdbc.core.JdbcTemplate? = null,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val users: com.red.server.auth.repository.UserAccountRepository? = null
) {

    /** طباعة النوع المطلوب على نظيره الدائم (CallType لا يملك CONFERENCE/LEGACY/LAN). */
    private fun persistentType(requested: String): CallType = when (requested.uppercase()) {
        "AUDIO_1V1", "LAN" -> CallType.AUDIO_1V1
        "VIDEO_1V1" -> CallType.VIDEO_1V1
        "GROUP_AUDIO" -> CallType.GROUP_AUDIO
        "GROUP_VIDEO", "CONFERENCE" -> CallType.GROUP_VIDEO
        "LIVE" -> CallType.LIVE_STREAM
        "SPACE" -> CallType.SPACE
        else -> CallType.AUDIO_1V1
    }

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
                require(request.participantIds.isNotEmpty() || !request.groupId.isNullOrBlank()) { "Participants required for group call" }
            }
            "CONFERENCE" -> {
                require(request.participantIds.size <= 100) { "Conference limit is 100" }
            }
            "AUDIO_1V1", "VIDEO_1V1", "LAN" -> {
                require(!request.targetId.isNullOrBlank()) { "Target required for 1-1 call" }
                require(request.targetId != callerId) { "Cannot call yourself" }
                // فحص الجمهور الموحّد: حظر ثنائي + خصوصية المكالمات للطرف المستدعى.
                // الأهداف غير الحسابية (أرقام LEGACY) تُتجاوز — لا كيان مستخدم لفحصه.
                checkCallAudience(callerId, request.targetId)?.let { return it }
            }
        }

        val callId = "call_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val callType = persistentType(request.type)
        // السجل الدائم أولاً (fail-closed: لا رنين بلا سجل مشاركين).
        val primaryTarget = request.targetId?.takeIf { it.isNotBlank() }
            ?: request.groupId?.takeIf { !it.isNullOrBlank() }
            ?: request.participantIds.firstOrNull()
            ?: callerId
        history.start(
            initiator = callerId,
            target = primaryTarget,
            targetLabel = primaryTarget,
            type = callType,
            route = CallRoute.RED,
            requestedId = callId
        )

        // تسليم المكالمة عبر المسارات المتعددة
        if (request.type.uppercase() in listOf("GROUP_AUDIO", "GROUP_VIDEO", "CONFERENCE")) {
            deliveryService.deliverGroupCall(
                callId = callId,
                hostId = callerId,
                hostName = callerId,
                memberIds = request.participantIds,
                isVideo = request.isVideo || request.type.uppercase() == "GROUP_VIDEO",
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
        // ACL دائم: المُستدعى وحده يجيب (يطابق CallHistoryService.answer).
        val doc = history.findById(callId)
            ?: return ResponseEntity.notFound().build()
        require(doc.targetId == answererId) { "Only the called account can answer" }
        history.answer(callId, answererId)
        runCatching { deliveryService.onCallAnswered(callId, answererId) }

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
        val doc = history.findById(callId)
            ?: return ResponseEntity.notFound().build()
        require(doc.targetId == targetId || doc.initiatorId == targetId) { "Only call participants can confirm ringing" }
        runCatching { deliveryService.onRingingConfirmed(callId, targetId) }

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
        val doc = history.findById(callId)
            ?: return ResponseEntity.ok(
                CallResponse(callId, "END", "ENDED", "Call already ended")
            )
        require(doc.initiatorId == enderId || doc.targetId == enderId) { "Only call participants can end" }
        history.end(callId, enderId)
        runCatching { deliveryService.onCallEnded(callId, enderId, reason ?: "COMPLETED") }

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
        val doc = history.findById(callId)
            ?: return ResponseEntity.notFound().build()
        require(doc.targetId == rejecterId) { "Only the called account can reject" }
        history.rejected(callId, rejecterId)
        runCatching { deliveryService.onCallEnded(callId, rejecterId, "REJECTED") }

        return ResponseEntity.ok(
            CallResponse(
                callId = callId,
                type = "REJECT",
                status = "REJECTED",
                message = "Call rejected by $rejecterId"
            )
        )
    }

    @GetMapping("/{callId}")
    fun getCall(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val callerId = authentication.name
        val doc = history.findById(callId)
            ?: return ResponseEntity.notFound().build()
        if (doc.initiatorId != callerId && doc.targetId != callerId) {
            return ResponseEntity.status(403).body(mapOf("error" to "NOT_CALL_PARTICIPANT"))
        }
        return ResponseEntity.ok(doc)
    }

    @GetMapping("/active")
    fun listActiveCalls(authentication: Authentication): ResponseEntity<List<CallHistoryItem>> {
        val callerId = authentication.name
        // السجل الدائم هو المصدر؛ النشط = RINGING/ACTIVE فقط.
        val active = history.history(callerId, limit = 50).filter {
            it.status == CallStatus.RINGING || it.status == CallStatus.ACTIVE
        }
        return ResponseEntity.ok(active)
    }

    /**
     * فحص جمهور المكالمة الفردية: يعيد 403 عند الحظر الثنائي أو مخالفة
     * خصوصية `calls` للمستدعى (NOBODY / CONTACTS بلا صداقة متبادلة)،
     * وnull عند السماح أو تعذّر الحسم (هدف غير حسابي كأرقام LEGACY،
     * أو غياب حقن الاختيارية في الاختبارات).
     */
    private fun checkCallAudience(callerId: String, targetId: String): ResponseEntity<CallResponse>? {
        val db = jdbc ?: return null
        val repo = users ?: return null
        val callerUuid = runCatching { java.util.UUID.fromString(callerId) }.getOrNull() ?: return null
        val target = runCatching { java.util.UUID.fromString(targetId) }
            .getOrNull()?.let { runCatching { repo.findById(it).orElse(null) }.getOrNull() }
            ?: repo.findByRedId(targetId.trim().uppercase()) ?: return null
        if (target.id == callerUuid) return null // رفض الذات مغطى بـ require أعلاه
        if (com.red.server.social.AudienceGuard.isBlockedEitherDirection(db, callerUuid, target.id)) {
            return ResponseEntity.status(403).body(CallResponse("", "BLOCKED", "FORBIDDEN", "Call blocked by audience policy"))
        }
        val callsPrivacy = runCatching {
            db.queryForObject(
                "SELECT calls FROM user_privacy_settings WHERE user_id=?",
                String::class.java, target.id
            )
        }.getOrNull() ?: "CONTACTS"
        val allowed = when (callsPrivacy.uppercase()) {
            "NOBODY" -> false
            "CONTACTS", "CONTACTS_EXCEPT", "ONLY_SHARE_WITH" ->
                com.red.server.social.AudienceGuard.isMutualContact(db, callerUuid, target.id)
            else -> true
        }
        if (!allowed) {
            return ResponseEntity.status(403).body(CallResponse("", "PRIVACY", "FORBIDDEN", "Callee does not accept calls from this account"))
        }
        return null
    }

    @GetMapping("/types")
    fun getCallTypes(authentication: Authentication): ResponseEntity<List<Map<String, String>>> {
        authentication.name // مصادقة فقط — الكتالوج ثابت ولا يكشف بيانات مستخدمين.
        val types = listOf(
            mapOf(
                "id" to "ONE_TO_ONE_AUDIO",
                "name" to "فردية صوتية",
                "description" to "مكالمة صوتية فردية مشفرة E2EE عبر WebRTC P2P مباشر، ترن وتتعرف، تعمل على الشبكة المحلية وكل الشبكات",
                "route" to "RED WebRTC P2P → TURN → RED ID",
                "maxParticipants" to "2",
                "icon" to "📞",
                "tech" to "WebRTC + DTLS-SRTP + TURN + Sovereign Push"
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
        authentication.name // مصادقة فقط — الإحصاء تشغيلي لا يكشف أطرافاً.
        return ResponseEntity.ok(mapOf(
            "pendingDeliveries" to deliveryService.getPendingCount(),
            "supportedTypes" to 8,
            "maxGroupSize" to 32,
            "maxConferenceSize" to 100,
            "features" to listOf(
                "E2EE for 1-1",
                "Mesh + SFU for groups",
                "SFU for conferences",
                "1-to-N for live",
                "Audio-only spaces",
                "LAN P2P without internet",
                "Multi-path delivery",
                "Sovereign Push ringing",
                "Works on all local networks"
            )
        ))
    }
}
