package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.RedisManager
import com.red.server.services.NotificationService
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
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
    private val redis: RedisManager,
    private val json: ObjectMapper,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val callWebSocketHandler: com.red.server.websocket.CallWebSocketHandler? = null,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val aliases: RoomAliasService? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val log = LoggerFactory.getLogger(CallHistoryController::class.java)
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

    /**
     * مزامنة التدرج باستخدام الترقيم القائم على المؤشر (Cursor-based).
     * يعيد العناصر الجديدة والمحدثة مع مؤشر الصفحة التالية.
     */
    @PostMapping("/history/sync")
    fun syncHistory(
        @RequestBody(required = false) request: CallHistorySyncRequest?,
        auth: Authentication
    ): ResponseEntity<CallHistorySyncResponse> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        val req = request ?: CallHistorySyncRequest()
        val result = history.syncHistory(
            redId = user.redId,
            cursor = req.cursor,
            limit = req.limit.coerceIn(1, 200),
            sinceVersion = req.sinceVersion,
            filter = req.filter
        )
        return ResponseEntity.ok(result)
    }

    /**
     * دفع التغييرات المحلية إلى الخادم
     */
    @PostMapping("/history/push")
    fun pushHistory(
        @RequestBody request: Map<String, Any?>,
        auth: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        val items = request["items"] as? List<*> ?: emptyList()
        val stored = runCatching { history.upsertFromSync(user.redId, items.map { it as Map<String, Any?> }) }.getOrElse { 0 }
        return ResponseEntity.ok(mapOf("status" to "synced", "received" to items.size, "stored" to stored))
    }

    /**
     * حذف سجل المكالمات عن المستدعي وحده.
     *
     * «الحذف» إخفاء لا محو: مستند السجل مشترك بين الطرفين، فمحوه يمحو سجل الطرف الآخر
     * أيضاً. لذلك نضيف معرّف المستدعي إلى `hiddenFor` فيختفي من سجله ويبقى للطرف الآخر.
     * بدونه كان «مسح السجل» في التطبيق يُلغى عند أول مزامنة — `load()` يعيد جلب صفوف
     * الخادم ويخزّنها بـ REPLACE فتعود المحذوفة فوراً.
     */
    @DeleteMapping("/history")
    fun clearHistory(auth: Authentication): ResponseEntity<Map<String, Any>> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        val hidden = runCatching { history.hideAllFor(user.redId) }.getOrDefault(0)
        return ResponseEntity.ok(mapOf("status" to "cleared", "hidden" to hidden))
    }

    /** إخفاء سجلات محددة (حذف مفرد أو متعدد) عن المستدعي وحده. */
    @PostMapping("/history/delete")
    fun deleteHistory(@RequestBody request: DeleteHistoryRequest, auth: Authentication): ResponseEntity<Any> {
        val user = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        if (request.callIds.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "CALL_IDS_REQUIRED"))
        }
        if (request.callIds.size > MAX_HISTORY_DELETE_IDS) {
            return ResponseEntity.badRequest().body(mapOf("error" to "TOO_MANY_CALL_IDS"))
        }
        val hidden = runCatching { history.hideFor(user.redId, request.callIds) }.getOrDefault(0)
        return ResponseEntity.ok(mapOf("status" to "deleted", "hidden" to hidden))
    }

    /** Sovereign wake endpoint — يخزن العرض للسحب لاحقاً عند اتصال المستلم (Path 2 of Multi-Path Delivery). */
    @PostMapping("/push-notify")
    fun pushNotify(@RequestBody request: PushNotifyRequest, auth: Authentication): ResponseEntity<Any> {
        val authenticated = users.findById(UUID.fromString(auth.name)).orElseThrow { NoSuchElementException("User not found") }
        if (request.callId.isBlank() || request.targetRedId.isBlank() || request.offerSdp.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_CALL_OFFER"))
        }

        // تنظيف تراجع الذاكرة فقط؛ في Redis التنظيف أصلي عبر TTL فلا حاجة لجولة مسح.
        purgeExpiredOffers(Instant.now())
        // هوية JWT هي مصدر هوية المتصل؛ لا نقبل callerId الوارد من الجهاز لأنه قابل للانتحال.
        val ttlSeconds = request.ttlSeconds?.coerceIn(MIN_PENDING_OFFER_TTL_SECONDS, MAX_PENDING_OFFER_TTL_SECONDS)
            ?: DEFAULT_PENDING_OFFER_TTL_SECONDS
        val offer = PendingOffer(
            callId = request.callId,
            targetRedId = request.targetRedId,
            callerId = authenticated.redId,
            mode = request.mode,
            offerSdp = request.offerSdp,
            ttlSeconds = ttlSeconds,
            createdAt = Instant.now()
        )
        if (!storeOffer(offer)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("error" to "PENDING_OFFER_CAPACITY_REACHED"))
        }
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
        // سحب ذرّي: جهاز ثانٍ يسحب العرض نفسه لا يرى شيئاً (204) — ليست حالة خطأ للـ poller.
        val offer = takeOffer(targetRedId, request.callId) ?: return ResponseEntity.noContent().build()
        if (offer.createdAt.plusSeconds(offer.ttlSeconds.toLong()).isBefore(Instant.now())) {
            return ResponseEntity.status(HttpStatus.GONE).body(mapOf("error" to "EXPIRED"))
        }
        return ResponseEntity.ok(mapOf(
            "callId" to offer.callId,
            "callerId" to offer.callerId,
            "mode" to offer.mode,
            "offerSdp" to offer.offerSdp,
            "ttlSeconds" to offer.ttlSeconds,
            "createdAtMs" to offer.createdAt.toEpochMilli()
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

    /**
     * تخزين العرض في Redis أولاً — يدوم عبر إعادة التشغيل ويُشارَك بين النسخ خلف موازن —
     * وإن تعذّر Redis (انقطاع، أو نشر بلا Redis) نتراجع إلى خريطة الذاكرة كي لا يسقط
     * مسار الإيقاظ كلياً. نتيجة Redis **الناجحة نهائية** فلا نخلط بين المخزنين.
     */
    private fun storeOffer(offer: PendingOffer): Boolean {
        val viaRedis = runCatching {
            redis.storePendingCallOffer(
                offer.targetRedId,
                offer.callId,
                json.writeValueAsString(StoredPendingOffer.from(offer)),
                offer.ttlSeconds.toLong()
            )
        }
        if (viaRedis.isSuccess) return viaRedis.getOrThrow()
        log.warn("pending-offer Redis store failed ({}), using in-memory fallback", viaRedis.exceptionOrNull()?.message)
        val key = "pending:${offer.callId}:${offer.targetRedId}"
        if (!pendingOffers.containsKey(key) && pendingOffers.size >= MAX_PENDING_OFFERS) return false
        pendingOffers[key] = offer
        return true
    }

    /** سحب العرض: Redis أولاً، ثم تراجع الذاكرة عند تعذّر Redis. */
    private fun takeOffer(targetRedId: String, callId: String?): PendingOffer? {
        val viaRedis = runCatching { redis.takePendingCallOffer(targetRedId, callId) }
        if (viaRedis.isSuccess) {
            val raw = viaRedis.getOrThrow() ?: return null
            return runCatching { json.readValue(raw, StoredPendingOffer::class.java).toPendingOffer(targetRedId) }
                .onFailure { log.warn("pending offer in Redis was unreadable: {}", it.message) }
                .getOrNull()
        }
        log.warn("pending-offer Redis take failed ({}), using in-memory fallback", viaRedis.exceptionOrNull()?.message)
        val entry = if (callId.isNullOrBlank()) {
            pendingOffers.entries.firstOrNull { it.value.targetRedId.equals(targetRedId, ignoreCase = true) }
        } else {
            pendingOffers.entries.firstOrNull { it.key == "pending:$callId:$targetRedId" }
        } ?: return null
        return pendingOffers.remove(entry.key)
    }

    /**
     * صيغة التخزين في Redis: حقول بدائية و`createdAtMs` رقمي — لا `Instant` — فلا يعتمد
     * التخزين على مسجّل زمني في Jackson. والقيم الافتراضية تمنع فشل القراءة إن أُضيف حقل لاحقاً.
     */
    data class StoredPendingOffer(
        val callId: String = "",
        val callerId: String = "",
        val mode: String = "",
        val offerSdp: String = "",
        val ttlSeconds: Int = 0,
        val createdAtMs: Long = 0L
    ) {
        fun toPendingOffer(targetRedId: String) = PendingOffer(
            callId = callId,
            targetRedId = targetRedId,
            callerId = callerId,
            mode = mode,
            offerSdp = offerSdp,
            ttlSeconds = ttlSeconds,
            createdAt = Instant.ofEpochMilli(createdAtMs)
        )

        companion object {
            fun from(offer: PendingOffer) = StoredPendingOffer(
                callId = offer.callId,
                callerId = offer.callerId,
                mode = offer.mode,
                offerSdp = offer.offerSdp,
                ttlSeconds = offer.ttlSeconds,
                createdAtMs = offer.createdAt.toEpochMilli()
            )
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

data class DeleteHistoryRequest(val callIds: List<String> = emptyList())

/** طلب مزامنة السجل مع الترقيم القائم على المؤشر */
data class CallHistorySyncRequest(
    val cursor: String? = null,
    val limit: Int = 50,
    val sinceVersion: Long = 0,
    val filter: CallHistoryFilter = CallHistoryFilter()
)

data class CallHistoryFilter(
    val types: List<String> = emptyList(),
    val directions: List<String> = emptyList(),
    val statuses: List<String> = emptyList(),
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val includeDeleted: Boolean = false
)

/** استجابة مزامنة السجل */
data class CallHistorySyncResponse(
    val items: List<CallHistoryItem>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val serverVersion: Long
)

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
        // مسار مجموعة: حل الاسم المستعار إلى القانوني، والخام القديم يبقى مقبولاً.
        val effectiveGroupId = aliases?.resolve(request.groupCallId) ?: request.groupCallId.trim()
        // فقط مضيف الغرفة يدعو إضافيين — كان أي مصادق يستطيع الحقن في أي مكالمة.
        // (غرفة مجهولة بعد إعادة التشغيل تُقبل من المدعي مضيفاً — تُعاد إنشاؤها عبر الدمج).
        val knownHost = callSignalingHandler.groupCallHost(effectiveGroupId)
        if (knownHost != null) {
            require(knownHost.equals(user.redId, ignoreCase = true)) { "ONLY_HOST_CAN_INVITE" }
        }
        // وضع المكالمة الحقيقي (كان VOICE ثابتاً فيُدعى أعضاء الفيديو بدعوة صوتية).
        val mode = request.mode.takeIf { it.isNotBlank() }?.uppercase() ?: "VOICE"
        val added = callSignalingHandler.addGroupCallMembers(
            effectiveGroupId, user.redId, request.inviteeIds, mode, mapOf("hostName" to request.hostName)
        )
        return ResponseEntity.ok(mapOf(
            "status" to "invited",
            "invitedCount" to added.size,
            "skippedCount" to (request.inviteeIds.size - added.size),
            "roomSize" to callSignalingHandler.groupCallSize(effectiveGroupId),
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
        /**
         * يجب أن يطابق `CallRingPolicy.MAILBOX_TTL_SECONDS` في التطبيق (120s).
         * كان 45s = مهلة عدم الرد فقط، وهي **أقصر** من نافذة صندوق بريد العميل:
         * العميل يرن حتى 45s ثم يسجّل «مكالمة فائتة» حتى 120s. فانتهاء العرض عند 45s
         * على الخادم كان يُلغي ذلك النطاق كاملاً (والسحب بعدها يعيد 410/204) ⇒ مكالمات
         * فائتة من تطبيق مقتول تُفقد بصمت ولا يعلم بها المستلم.
         */
        private const val DEFAULT_PENDING_OFFER_TTL_SECONDS = 120
        private const val MIN_PENDING_OFFER_TTL_SECONDS = 5
        private const val MAX_PENDING_OFFER_TTL_SECONDS = 120
        private const val MAX_PENDING_OFFERS = 10_000
        /** سقف معرّفات الحذف في الطلب الواحد — يمنع طلباً واحداً يمسّ آلاف الصفوف. */
        private const val MAX_HISTORY_DELETE_IDS = 500

        private val pendingOffers = ConcurrentHashMap<String, PendingOffer>()
    }
}
