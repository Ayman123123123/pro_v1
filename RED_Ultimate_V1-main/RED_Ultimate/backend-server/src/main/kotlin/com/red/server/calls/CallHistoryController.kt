package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/calls")
class CallHistoryController(
    private val history: CallHistoryService,
    private val users: UserAccountRepository,
    private val notificationService: NotificationService,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val callWebSocketHandler: com.red.server.websocket.CallWebSocketHandler? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) since: Long?,
        @RequestParam(required = false) offset: Int? = null,
        auth: Authentication
    ): List<CallHistoryItem> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        // AUTO-FIX (calls history): since/offset are now honoured by the Mongo query itself, so
        // "load more" returns real older pages instead of repeating the newest 20 rows.
        val sinceInstant = since?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        return history.history(user.redId, limit, offset ?: 0, sinceInstant) { peerRedId ->
            runCatching { users.findByRedId(peerRedId)?.displayName }.getOrNull()
        }
    }

    @PostMapping("/history/sync")
    fun syncHistory(@RequestBody(required = false) body: List<Map<String, Any?>>?, auth: Authentication): ResponseEntity<Map<String, Any>> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        // AUTO-FIX (calls history): this endpoint used to be a stub that stored nothing, so call logs
        // created while offline were silently dropped. Rows are upserted into call_history now.
        val rows = body ?: emptyList()
        val stored = runCatching { history.upsertFromSync(user.redId, rows) }.getOrElse { 0 }
        return ResponseEntity.ok(mapOf("status" to "synced", "received" to rows.size, "stored" to stored))
    }

    /** Sovereign wake endpoint — يخزن العرض للسحب لاحقاً عند اتصال المستلم (Path 2 of Multi-Path Delivery). */
    @PostMapping("/push-notify")
    fun pushNotify(@RequestBody request: PushNotifyRequest, auth: Authentication): ResponseEntity<Any> {
        val authenticated = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        if (request.callId.isBlank() || request.targetRedId.isBlank() || request.offerSdp.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_CALL_OFFER"))
        }

        val now = Instant.now()
        purgeExpiredOffers(now)
        val key = "pending:${request.callId}:${request.targetRedId}"
        if (!pendingOffers.containsKey(key) && pendingOffers.size >= MAX_PENDING_OFFERS) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("error" to "PENDING_OFFER_CAPACITY_REACHED"))
        }

        // هوية JWT هي مصدر هوية المتصل؛ لا نقبل callerId الوارد من الجهاز لأنه قابل للانتحال.
        val ttlSeconds = request.ttlSeconds?.coerceIn(MIN_PENDING_OFFER_TTL_SECONDS, MAX_PENDING_OFFER_TTL_SECONDS)
            ?: DEFAULT_PENDING_OFFER_TTL_SECONDS
        pendingOffers[key] = PendingOffer(
            callId = request.callId,
            targetRedId = request.targetRedId,
            callerId = authenticated.redId,
            mode = request.mode,
            offerSdp = request.offerSdp,
            ttlSeconds = ttlSeconds,
            createdAt = now
        )
        // أرسل إشعار إيقاظ سيادياً (NotificationService يتعامل مع نقاط النهاية).
        scope.launch {
            notificationService.sendVoipPushNotification(
                request.targetRedId,
                authenticated.redId,
                request.callId,
                request.mode
            )
        }
        return ResponseEntity.ok(mapOf("status" to "stored"))
    }

    /** سحب العرض المخزن عند اتصال المستلم (Path 3 of Multi-Path Delivery). */
    @PostMapping("/pending")
    fun pullPending(@RequestBody request: PullPendingRequest, auth: Authentication): ResponseEntity<Any> {
        val authenticated = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        // لا نثق في targetRedId القادم من الهاتف؛ هوية JWT هي المصدر الوحيد الصحيح.
        // هذا يمنع فشل polling عندما تكون قيمة RED ID المحلية قديمة بعد تبديل الحساب.
        val targetRedId = authenticated.redId
        val entry = if (request.callId.isNullOrBlank()) {
            pendingOffers.entries.firstOrNull { it.value.targetRedId.equals(targetRedId, ignoreCase = true) }
        } else {
            pendingOffers.entries.firstOrNull { it.key == "pending:${request.callId}:${targetRedId}" }
        } ?: return ResponseEntity.noContent().build()
        // يمكن أن يسحب جهاز ثانٍ العرض بين البحث والإزالة؛ هذه ليست حالة خطأ للـ poller.
        val offer = pendingOffers.remove(entry.key) ?: return ResponseEntity.noContent().build()
        if (offer.createdAt.plusSeconds(offer.ttlSeconds.toLong()).isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.GONE).body(mapOf("error" to "EXPIRED"))
        }
        return ResponseEntity.ok(mapOf(
            "callId" to offer.callId,
            "callerId" to offer.callerId,
            "mode" to offer.mode,
            "offerSdp" to offer.offerSdp,
            "ttlSeconds" to offer.ttlSeconds
        ))
    }

    private fun purgeExpiredOffers(now: Instant) {
        pendingOffers.entries.forEach { (key, offer) ->
            if (offer.createdAt.plusSeconds(offer.ttlSeconds.toLong()).isBefore(now)) {
                // Conditional remove keeps a concurrently replaced offer intact.
                pendingOffers.remove(key, offer)
            }
        }
    }

    data class PushNotifyRequest(

        val callId: String,
        val targetRedId: String,
        val callerId: String,
        val mode: String,
        val offerSdp: String,
        val ttlSeconds: Int? = null
    )

    class PullPendingRequest {
        var callId: String? = null
        var targetRedId: String? = null
    }

    /** دعوة إضافية أثناء مكالمة جماعية مستقلة — يرن الجدد ويُسجلون كأعضاء الغرفة. */
    @PostMapping("/group/invite-extra")
    fun inviteExtra(
        @RequestBody request: InviteExtraRequest,
        auth: Authentication
    ): ResponseEntity<Any> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        val callSignalingHandler = callWebSocketHandler
            ?: throw IllegalStateException("signaling unavailable")
        require(request.groupCallId.isNotBlank()) { "groupCallId is required" }
        // فقط مضيف الغرفة يدعو إضافيين — كان أي مصادق يستطيع الحقن في أي مكالمة.
        // (غرفة مجهولة بعد إعادة التشغيل تُقبل من المدعي مضيفاً — تُعاد إنشاؤها عبر الدمج).
        val knownHost = callSignalingHandler.groupCallHost(request.groupCallId)
        if (knownHost != null) {
            require(knownHost.equals(user.redId, ignoreCase = true)) { "ONLY_HOST_CAN_INVITE" }
        }
        // وضع المكالمة الحقيقي (كان VOICE ثابتاً فيُدعى أعضاء الفيديو بدعوة صوتية).
        val mode = request.mode.takeIf { it.isNotBlank() }?.uppercase() ?: "VOICE"
        val added = callSignalingHandler.addGroupCallMembers(
            request.groupCallId, user.redId, request.inviteeIds, mode, mapOf("hostName" to request.hostName)
        )
        return ResponseEntity.ok(mapOf(
            "status" to "invited",
            "invitedCount" to added.size,
            "skippedCount" to (request.inviteeIds.size - added.size),
            "roomSize" to callSignalingHandler.groupCallSize(request.groupCallId),
            "maxMembers" to com.red.server.websocket.CallWebSocketHandler.MAX_GROUP_CALL_MEMBERS
        ))
    }

    data class InviteExtraRequest(
        val groupCallId: String,
        val inviteeIds: List<String> = emptyList(),
        val hostName: String = "",
        val mode: String = "VOICE"
    )

    data class PendingOffer(
        val callId: String,
        val targetRedId: String,
        val callerId: String,
        val mode: String,
        val offerSdp: String,
        val ttlSeconds: Int,
        val createdAt: Instant
    )

    companion object {
        private const val DEFAULT_PENDING_OFFER_TTL_SECONDS = 45
        private const val MIN_PENDING_OFFER_TTL_SECONDS = 5
        private const val MAX_PENDING_OFFER_TTL_SECONDS = 120
        private const val MAX_PENDING_OFFERS = 10_000

        private val pendingOffers = ConcurrentHashMap<String, PendingOffer>()
    }
}
