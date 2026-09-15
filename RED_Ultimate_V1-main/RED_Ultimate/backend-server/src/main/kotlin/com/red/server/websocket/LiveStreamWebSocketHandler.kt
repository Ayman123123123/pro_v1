package com.red.server.websocket

import tools.jackson.databind.ObjectMapper
import com.red.server.calls.RoomAliasService
import com.red.server.calls.RoomSeparationPolicy
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * WebRTC signaling for live broadcasts with ownership verification.
 * - Only the broadcaster who created the stream via REST (/api/livestream/create)
 *   can claim BROADCASTER role via WebSocket. Any other authenticated user
 *   attempting BROADCASTER is rejected with 403-style error and session closed.
 * - Broadcaster OFFER is broadcast to ALL viewers (true 1-to-many), not firstOnly.
 * - Viewers ANSWER/ICE go to broadcaster.
 * - CHAT, REACTION, RAISE_HAND, APPROVE_COHOST, PIN_MESSAGE are relayed with permission checks.
 * - GIFT is DISABLED by product decision ("بدون هدايا"): legacy GIFT frames are
 *   relayed as free REACTION events (no wallet, no cost) for backward compatibility.
 * - Unified viewer events for all clients: VIEWER_JOINED / PARTICIPANT_LEFT /
 *   VIEWER_COUNT_UPDATED (count is authoritative) / KICKED / STREAM_ENDED.
 */
@Component
class LiveStreamWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val liveStreamService: com.red.server.calls.LiveStreamService,
    private val accessGuard: ApprovedDeviceSessionGuard,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val roomAliases: RoomAliasService? = null
) : TextWebSocketHandler() {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)
    private val broadcasters = ConcurrentHashMap<String, WebSocketSession>()
    private val viewers = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionToStream = ConcurrentHashMap<String, String>()
    private val sessionRole = ConcurrentHashMap<String, Role>()
    private val sessionUser = ConcurrentHashMap<String, String>()
    /** حدّ معدل الشات/الهدايا لكل مستخدم — كان التعليق يعد بـ 1msg/sec ولا شيء مطبق. */
    private val lastChatAt = ConcurrentHashMap<String, Long>()
    private val lastGiftAt = ConcurrentHashMap<String, Long>()

    enum class Role { BROADCASTER, VIEWER }

    private val statsScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "livestream-stats").also { it.isDaemon = true }
    }

    /** بث إحصائيات المذيع كل 5s (المشاهدون/الذروة/الرسائل) — لوحة المذيع الحية. */
    @PostConstruct
    fun startStatsBroadcast() {
        statsScheduler.scheduleAtFixedRate({
            runCatching { broadcastStats() }
        }, 5, 5, TimeUnit.SECONDS)
    }

    @PreDestroy
    fun stopStatsBroadcast() {
        runCatching { statsScheduler.shutdownNow() }
    }

    private fun broadcastStats() {
        val rooms = (broadcasters.keys + viewers.keys).toSet()
        for (roomId in rooms) {
            val record = liveStreamService.getStreamRecord(roomId) ?: continue
            val count = viewers[roomId]?.size ?: 0
            val msg = objectMapper.writeValueAsString(mapOf(
                "type" to "STREAM_STATS",
                "roomId" to roomId,
                "payload" to mapOf(
                    "viewerCount" to count,
                    "peakViewers" to maxOf(record.peakViewers, count),
                    "totalMessages" to record.totalMessages,
                    "totalReactions" to record.totalReactions,
                    "slowModeSec" to record.slowModeSec,
                    "raisedCount" to liveStreamService.getRaisedHands(roomId).size,
                    "coHostCount" to liveStreamService.getCoHosts(roomId).size
                )
            ))
            val text = TextMessage(msg)
            broadcasters[roomId]?.let { b -> if (b.isOpen) sendSafe(b, text) }
            // للمشاهدين: حدّث العداد فقط كل 10s لتقليل الضجيج (كل بث ثانٍ)
            if ((System.currentTimeMillis() / 1000) % 10 < 5) {
                viewers[roomId]?.filter { it.isOpen }?.forEach { sendSafe(it, text) }
            }
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Revalidate device approval on every frame
        if (!accessGuard.isStillAuthorized(
                session.attributes["accountId"] as? String,
                session.attributes["deviceId"] as? String
            )
        ) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        val userId = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val accountId = session.attributes["accountId"] as? String
        val redId = session.attributes["redId"] as? String ?: userId
        val incoming = objectMapper.readValue(message.payload, IncomingConferenceSignal::class.java)
        // G13: حل alias عبر RoomAliasService (Redis+ذاكرة) مع سقوط للخام.
        val resolvedRoomId = resolveRoom(incoming.roomId)
        val signal = if (resolvedRoomId == incoming.roomId) incoming else incoming.copy(roomId = resolvedRoomId)
        // تحقّق غير قاتل: كان require يرمي فيُغلق السوكت قبل أي ردّ واضح للعميل.
        if (signal.roomId.isBlank() || !signal.roomId.matches(STREAM_ID)) {
            val err = objectMapper.writeValueAsString(mapOf(
                "type" to "ERROR",
                "roomId" to signal.roomId,
                "payload" to mapOf("code" to "INVALID_STREAM_ID", "message" to "streamId is required and must match ${STREAM_ID.pattern}")
            ))
            runCatching { session.sendMessage(TextMessage(err)) }
            return
        }

        when (signal.type.uppercase()) {
            "JOIN" -> {
                val requestedRole = if ((signal.payload["role"] ?: "viewer").toString().equals("broadcaster", ignoreCase = true)) Role.BROADCASTER else Role.VIEWER
                // Ownership verification for broadcaster
                if (requestedRole == Role.BROADCASTER) {
                    val record = liveStreamService.getStreamRecord(signal.roomId)
                    if (record == null) {
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "STREAM_NOT_FOUND", "message" to "Live stream not found. Create via REST first.")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close() }
                        return
                    }
                    val isOwner = record.broadcasterId == accountId
                        || record.broadcasterId == userId
                        || record.broadcasterId == redId
                        || record.broadcasterRedId == redId
                        || record.broadcasterRedId == userId
                    if (!isOwner) {
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "NOT_OWNER", "message" to "Only stream owner can broadcast")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close() }
                        return
                    }
                    // Owner verified
                    sessionToStream[session.id] = signal.roomId
                    sessionRole[session.id] = Role.BROADCASTER
                    sessionUser[session.id] = userId
                    broadcasters[signal.roomId]?.let { old ->
                        if (old.id != session.id) runCatching { old.close() }
                    }
                    broadcasters[signal.roomId] = session
                    // AUTO-FIX (livestream race): replay existing viewers to the broadcaster so it
                    // offers to viewers who joined before the broadcaster's mesh was ready.
                    runCatching {
                        viewers[signal.roomId]?.forEach { v ->
                            val vid = sessionUser[v.id]
                            if (!vid.isNullOrBlank()) {
                                val ev = objectMapper.writeValueAsString(mapOf(
                                    "type" to "VIEWER_JOINED",
                                    "roomId" to signal.roomId,
                                    "userId" to vid
                                ))
                                session.sendMessage(TextMessage(ev))
                            }
                        }
                    }
                    // قائمة الأيادي المحفوظة خادمياً للمذيع العائد من انقطاع
                    runCatching {
                        val hands = liveStreamService.getRaisedHands(signal.roomId)
                        val cohosts = liveStreamService.getCoHosts(signal.roomId)
                        if (hands.isNotEmpty() || cohosts.isNotEmpty()) {
                            val roster = objectMapper.writeValueAsString(mapOf(
                                "type" to "COHOST_LIST",
                                "roomId" to signal.roomId,
                                "payload" to mapOf(
                                    "coHosts" to cohosts.joinToString(","),
                                    "raisedHands" to hands.entries.joinToString(";") { "${it.key}=${it.value}" }
                                )
                            ))
                            session.sendMessage(TextMessage(roster))
                            // أعد بث كل يد كحدث منفصل ليتوافق مع العملاء القدامى
                            hands.forEach { (uid, uname) ->
                                val ev = objectMapper.writeValueAsString(mapOf(
                                    "type" to "RAISE_HAND",
                                    "roomId" to signal.roomId,
                                    "userId" to uid,
                                    "payload" to mapOf("userName" to uname)
                                ))
                                runCatching { session.sendMessage(TextMessage(ev)) }
                            }
                        }
                    }
                } else {
                    // Viewer path — LEGENDARY FIX (غرف شبح): ارفض الانضمام لغرفة غير موجودة
                    // بدل إنشاء viewers شبح بلا سجل (كان يضخم الذاكرة والعداد).
                    val record = liveStreamService.getStreamRecord(signal.roomId)
                    if (record == null) {
                        log.warn("JOIN rejected: stream {} not found (user {})", signal.roomId, userId)
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "STREAM_NOT_FOUND", "message" to "Stream not found or ended")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close(CloseStatus.NOT_ACCEPTABLE) }
                        return
                    }
                    if (liveStreamService.isBanned(signal.roomId, userId)) {
                        log.warn("JOIN rejected: banned user {} stream {}", userId, signal.roomId)
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "BANNED", "message" to "You are banned from this stream")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close(CloseStatus.POLICY_VIOLATION) }
                        return
                    }
                    if (record.isPrivate) {
                        val providedPassword = signal.payload["password"] as? String
                        if (!liveStreamService.verifyPassword(signal.roomId, providedPassword)) {
                            val err = objectMapper.writeValueAsString(mapOf(
                                "type" to "ERROR",
                                "roomId" to signal.roomId,
                                "payload" to mapOf("code" to "FORBIDDEN", "message" to "Private stream - password required")
                            ))
                            runCatching { session.sendMessage(TextMessage(err)) }
                            runCatching { session.close() }
                            return
                        }
                    }
                    sessionToStream[session.id] = signal.roomId
                    sessionRole[session.id] = Role.VIEWER
                    sessionUser[session.id] = userId
                    viewers.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }.add(session)
                    // مزامنة REST/WS Legendary V2: سجل المشاهد في liveViewers (RedId)
                    // حتى يتطابق عداد /public مع عداد السوكت ويعمل الطرد والتذكرة.
                    runCatching { liveStreamService.addViewer(signal.roomId, userId) }
                    // توحيد الأحداث: VIEWER_JOINED للجميع (المذيع يبني الـ mesh،
                    // والمشاهدون يبنون قائمة المشاهدين)، ثم COUNT_UPDATED كمرجع
                    // رقمي واحد — كان الانضمام يصل المذيع فقط فيبقى عدّاد المشاهِد
                    // صفرًا لدى بقية المشاهدين حتى أول مغادرة.
                    val joinedMsg = objectMapper.writeValueAsString(mapOf(
                        "type" to "VIEWER_JOINED",
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to mapOf("userId" to userId, "isBroadcaster" to "false")
                    ))
                    val countMsg = objectMapper.writeValueAsString(mapOf(
                        "type" to "VIEWER_COUNT_UPDATED",
                        "roomId" to signal.roomId,
                        "payload" to mapOf("viewerCount" to (viewers[signal.roomId]?.size ?: 0))
                    ))
                    broadcasters[signal.roomId]?.let { b ->
                        if (b.isOpen) runCatching { b.sendMessage(TextMessage(joinedMsg)) }
                    }
                    viewers[signal.roomId]?.filter { it.isOpen && it.id != session.id }
                        ?.forEach { runCatching { it.sendMessage(TextMessage(joinedMsg)) } }
                    val allForCount = mutableListOf<WebSocketSession>()
                    broadcasters[signal.roomId]?.let { allForCount.add(it) }
                    viewers[signal.roomId]?.let { allForCount.addAll(it) }
                    allForCount.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(countMsg)) } }
                }
            }
            "VIEWER_NEEDS_MESH" -> {
                // LEGENDARY Phase 6: viewer without SFU asks the broadcaster for a mesh fallback.
                val broadcaster = broadcasters[signal.roomId] ?: return
                if (broadcaster.isOpen) {
                    val outbound = objectMapper.writeValueAsString(mapOf(
                        "type" to "VIEWER_NEEDS_MESH",
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to signal.payload
                    ))
                    runCatching { broadcaster.sendMessage(TextMessage(outbound)) }
                }
            }
            "OFFER" -> {
                val role = sessionRole[session.id] ?: return
                if (role == Role.BROADCASTER) {
                    val vs = viewers[signal.roomId] ?: return
                    val targetId = signal.payload["targetUserId"]?.toString()?.takeIf { it.isNotBlank() }
                    val outbound = objectMapper.writeValueAsString(mapOf(
                        "type" to "OFFER",
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to signal.payload
                    ))
                    val recipients = if (targetId != null) {
                        vs.filter { it.isOpen && (it.attributes["userId"] as? String) == targetId }
                    } else {
                        vs.filter { it.isOpen }
                    }
                    recipients.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
                } else {
                    // Viewer should not send OFFER in live mode, but relay to broadcaster for co-host case
                    val broadcaster = broadcasters[signal.roomId] ?: return
                    if (broadcaster.isOpen) {
                        val outbound = objectMapper.writeValueAsString(mapOf(
                            "type" to "OFFER",
                            "roomId" to signal.roomId,
                            "userId" to userId,
                            "payload" to signal.payload
                        ))
                        runCatching { broadcaster.sendMessage(TextMessage(outbound)) }
                    }
                }
            }
            "ANSWER", "ICE" -> {
                val role = sessionRole[session.id] ?: return
                if (role == Role.VIEWER) {
                    val broadcaster = broadcasters[signal.roomId] ?: return
                    if (broadcaster.isOpen) {
                        val outbound = objectMapper.writeValueAsString(mapOf(
                            "type" to signal.type.uppercase(),
                            "roomId" to signal.roomId,
                            "userId" to userId,
                            "payload" to signal.payload
                        ))
                        runCatching { broadcaster.sendMessage(TextMessage(outbound)) }
                    }
                } else {
                    // Broadcaster ANSWER/ICE to specific viewer if target specified, otherwise to all
                    val targetId = signal.payload["targetUserId"]?.toString()
                    val vs = viewers[signal.roomId] ?: return
                    val outbound = objectMapper.writeValueAsString(mapOf(
                        "type" to signal.type.uppercase(),
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to signal.payload
                    ))
                    if (targetId != null) {
                        vs.filter { (it.attributes["userId"] as? String) == targetId && it.isOpen }
                            .forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
                    } else {
                        vs.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
                    }
                }
            }
            "CHAT", "REACTION", "RAISE_HAND", "LOWER_HAND" -> {
                // بوابة العضوية: من لم يُكمل JOIN لا يبثّ شيئاً في غرفة حية
                // (كان بإمكان أي سوكت متصل دون JOIN أن يغرِق الشات).
                if (sessionRole[session.id] == null) {
                    val err = objectMapper.writeValueAsString(mapOf(
                        "type" to "ERROR",
                        "roomId" to signal.roomId,
                        "payload" to mapOf("code" to "JOIN_REQUIRED", "message" to "Send JOIN before interactive actions")
                    ))
                    runCatching { session.sendMessage(TextMessage(err)) }
                    return
                }
                if (signal.type.equals("RAISE_HAND", ignoreCase = true)) {
                    val uname = signal.payload["userName"]?.toString().orEmpty().take(64).ifBlank { userId }
                    runCatching { liveStreamService.addRaisedHand(signal.roomId, userId, uname) }
                }
                if (signal.type.equals("LOWER_HAND", ignoreCase = true)) {
                    runCatching { liveStreamService.removeRaisedHand(signal.roomId, userId) }
                }
                // خنق التفاعلات: 800ms لكل مستخدم (كانت بلا حد — فيض O(N) عند الحماس)
                if (signal.type.equals("REACTION", ignoreCase = true)) {
                    val now = System.currentTimeMillis()
                    val key = "${signal.roomId}:$userId"
                    if (now - (lastGiftAt[key] ?: 0L) < REACTION_MIN_INTERVAL_MS) return
                    lastGiftAt[key] = now
                    liveStreamService.incrementReactionCount(signal.roomId)
                }
                // CHAT moderation: length 1..200 + slow-mode ديناميكي من البث + كلمات محظورة للبث
                if (signal.type.equals("CHAT", ignoreCase = true)) {
                    val text = signal.payload["text"]?.toString().orEmpty().trim()
                    if (text.isEmpty() || text.length > 200) return
                    // LEGENDARY FIX (إشراف فعلي): المحظور لا يرسل — مع طرد الجلسة
                    if (liveStreamService.isBanned(signal.roomId, userId)) {
                        log.warn("CHAT rejected: banned user {} stream {}", userId, signal.roomId)
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "BANNED", "message" to "You are banned from this stream")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close(CloseStatus.POLICY_VIOLATION) }
                        removeSession(session, signal.roomId, userId)
                        return
                    }
                    // المكتوم لا يرسل — مع تنبيه صريح للمرسل بدل الإسقاط الصامت
                    if (liveStreamService.isMuted(signal.roomId, userId)) {
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "MUTED", "message" to "تم كتمك من المذيع")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        return
                    }
                    // slow-mode ديناميكي (0 = الوضع القديم 1.2s كحد أدنى)
                    val slowSec = liveStreamService.getSlowMode(signal.roomId)
                    val minInterval = maxOf(CHAT_MIN_INTERVAL_MS, slowSec * 1000L)
                    val now = System.currentTimeMillis()
                    val key = "${signal.roomId}:$userId"
                    if (now - (lastChatAt[key] ?: 0L) < minInterval) {
                        // تنبيه الخنق للمرسل بدل الإسقاط الصامت (تشخيص أسرع على 4G)
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "RATE_LIMITED", "message" to "أنت ترسل بسرعة — انتظر قليلاً")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        return
                    }
                    lastChatAt[key] = now
                    // bad words: العامة + كلمات المذيع المخصصة
                    val lower = text.lowercase()
                    val blocked = listOf("spam", "abuse", "xxx") + liveStreamService.getBlockedWords(signal.roomId)
                    if (blocked.any { it.isNotBlank() && lower.contains(it) }) return
                    val senderName = signal.payload["senderName"]?.toString().orEmpty().take(64)
                    val replyToId = signal.payload["replyToId"]?.toString()?.take(64)?.takeIf { it.isNotBlank() }
                    runCatching { liveStreamService.saveChat(signal.roomId, userId, senderName, text, replyToId) }
                }
                // GIFT معطّل بقرار المنتج — كان التحقق هنا يخصم من محفظة الجهاز.
                // (أُسقطت كتلة التحقق؛ انظر فرع "GIFT" المستقل أدناه: ترحيل كتفاعل مجاني.)
                // Broadcast to whole stream except sender (chat) or including sender (reactions for echo).
                // GIFT كان يُستثنى مُرسِله (صدى محلي) — الآن REACTION يشمل المُرسِل للصدى.
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to signal.type.uppercase(),
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                val filterSelf = signal.type.equals("CHAT", ignoreCase = true)
                allSessions.filter { (if (filterSelf) it.id != session.id else true) && it.isOpen }
                    .forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "GIFT" -> {
                // الهدايا معطلة بقرار المنتج ("بدون هدايا"): أي إشارة GIFT من نسخ
                // قديمة تُرحَّل كتفاعل مجاني REACTION — بلا محفظة ولا تكلفة ولا حدث مدفوع.
                val now = System.currentTimeMillis()
                val key = "${signal.roomId}:$userId"
                if (now - (lastGiftAt[key] ?: 0L) < GIFT_MIN_INTERVAL_MS) return
                lastGiftAt[key] = now
                val emoji = signal.payload["giftEmoji"]?.toString()?.takeIf { it.isNotBlank() } ?: "❤️"
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to "REACTION",
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to mapOf("emoji" to emoji)
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "APPROVE_COHOST" -> {
                // Only broadcaster can approve (max 4 enforced in service)
                val role = sessionRole[session.id] ?: return
                if (role != Role.BROADCASTER) return
                val target = signal.payload["targetUserId"]?.toString().orEmpty()
                if (target.isNotBlank()) {
                    val ok = runCatching {
                        liveStreamService.approveCoHost(signal.roomId, resolveBroadcasterId(signal.roomId, userId), target)
                    }.getOrDefault(true)
                    if (!ok) return
                }
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to "APPROVE_COHOST",
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "REJECT_COHOST", "REMOVE_COHOST", "LOWER_HAND_FORCE" -> {
                val role = sessionRole[session.id] ?: return
                if (role != Role.BROADCASTER) return
                if (signal.type.uppercase() in setOf("REJECT_COHOST", "REMOVE_COHOST")) {
                    val target = signal.payload["targetUserId"]?.toString().orEmpty()
                    if (target.isNotBlank()) {
                        runCatching { liveStreamService.removeCoHost(signal.roomId, resolveBroadcasterId(signal.roomId, userId), target) }
                        runCatching { liveStreamService.removeRaisedHand(signal.roomId, target) }
                    }
                }
                if (signal.type.equals("LOWER_HAND_FORCE", ignoreCase = true)) {
                    val target = signal.payload["targetUserId"]?.toString().orEmpty().ifBlank { signal.payload["userId"]?.toString().orEmpty() }
                    if (target.isNotBlank()) runCatching { liveStreamService.removeRaisedHand(signal.roomId, target) }
                }
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to signal.type.uppercase(),
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "SLOW_MODE_SET", "MUTED", "UNMUTED", "COHOST_LIST", "STREAM_STATS", "RECORDING_STARTED", "RECORDING_STOPPED", "CHAT_DELETED" -> {
                // أحداث إدارة من المذيع فقط — تُرحّل للجميع
                val role = sessionRole[session.id] ?: return
                if (role != Role.BROADCASTER) return
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to signal.type.uppercase(),
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "PIN_MESSAGE", "UNPIN_MESSAGE" -> {
                // تثبيت تعليق: المذيع فقط — يُرحَّل للجميع (إضافة آمنة، العملاء
                // القدامى يتجاهلون النوع المجهول عبر ignoreUnknownKeys).
                val role = sessionRole[session.id] ?: return
                if (role != Role.BROADCASTER) return
                if (signal.type.equals("PIN_MESSAGE", ignoreCase = true)) {
                    val text = signal.payload["text"]?.toString().orEmpty().trim()
                    if (text.isEmpty() || text.length > 200) return
                }
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to signal.type.uppercase(),
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "KICK" -> {
                // طرد فوري على قناة الإشارة نفسها (المذيع فقط): يصل المطرود حدث
                // KICKED على سوكت /ws/livestream ثم تُغلق جلسته وتُبث الأعداد.
                // (مسار REST /kick يبقى للتوافق ويرسل KICKED عبر سوكت المكالمات.)
                val role = sessionRole[session.id] ?: return
                if (role != Role.BROADCASTER) return
                val targetUserId = signal.payload["targetUserId"]?.toString()?.takeIf { it.isNotBlank() } ?: return
                val target = viewers[signal.roomId]
                    ?.firstOrNull { it.isOpen && (it.attributes["userId"] as? String) == targetUserId }
                    ?: return
                val kickedMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "KICKED",
                    "roomId" to signal.roomId,
                    "userId" to targetUserId,
                    "payload" to mapOf("reason" to "Kicked by broadcaster")
                ))
                runCatching { target.sendMessage(TextMessage(kickedMsg)) }
                runCatching { target.close(CloseStatus.POLICY_VIOLATION) }
                // removeSession ينظّف الحالة كاملة (مشاهدون/عدّاد/جداول الجلسة)
                // ويبثّ PARTICIPANT_LEFT، فلا تبقى جلسة "شبح".
                removeSession(target, signal.roomId, targetUserId)
                liveStreamService.removeViewer(signal.roomId, targetUserId)
            }
            "LEAVE" -> removeSession(session, signal.roomId, userId)
            "SET_QUALITY" -> {
                // LEGENDARY FIX (exception ميتة): الجودة محلية للعميل — قبول صامت مسجل
                // بدل رمي IllegalArgumentException الذي كان يكسر الـ relay.
                log.debug("SET_QUALITY from {} room {} (client-local)", userId, signal.roomId)
            }
            "LEAVE_COHOST" -> {
                // LEGENDARY FIX (ضيف عالق): مغادرة طوعية تُرحَّل للمذيع + تنظيف خادمي
                runCatching { liveStreamService.removeCoHost(signal.roomId, resolveBroadcasterId(signal.roomId, userId), userId) }
                runCatching { liveStreamService.removeRaisedHand(signal.roomId, userId) }
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to "LEAVE_COHOST",
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            else -> {
                // LEGENDARY FIX (صلابة): نوع مجهول يُسجَّل ويُتجاهل — لا رمي يكسر الجلسة الحية
                log.warn("Unsupported live signal type '{}' from {} room {}", signal.type, userId, signal.roomId)
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        val streamId = sessionToStream.remove(session.id) ?: return
        val userId = session.attributes["userId"] as? String ?: return
        removeSession(session, streamId, userId)
    }

    private fun resolveBroadcasterId(streamId: String, fallbackUserId: String): String {
        return liveStreamService.getStreamRecord(streamId)?.broadcasterId ?: fallbackUserId
    }

    /** G13: حل alias عبر RoomAliasService (Redis+ذاكرة) مع سقوط للخام. */
    private fun resolveRoom(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return v
        return runCatching { roomAliases?.resolve(v) ?: RoomSeparationPolicy.resolve(v) }.getOrNull()?.takeIf { it.isNotBlank() } ?: v
    }

    /** بث حدث غرفة لكل المتصلين (مذيع + مشاهدون) — للاستدعاء من REST (حذف شات/تثبيت...). */
    fun broadcastToRoom(roomId: String, type: String, payload: Map<String, Any?>, fromUserId: String = "server") {        val allSessions = mutableListOf<WebSocketSession>()
        broadcasters[roomId]?.let { allSessions.add(it) }
        viewers[roomId]?.let { allSessions.addAll(it) }
        if (allSessions.isEmpty()) return
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to type.uppercase(),
            "roomId" to roomId,
            "userId" to fromUserId,
            "payload" to payload
        ))
        allSessions.filter { it.isOpen }.forEach { sendSafe(it, TextMessage(outbound)) }
    }

    /** LEGENDARY FIX (حظر فعلي): طرد جلسة مستخدم من الغرفة — للاستدعاء من REST عند BAN/KICK */
    fun disconnectUser(roomId: String, userId: String, reason: String = "Kicked by broadcaster") {
        val targets = (viewers[roomId]?.filter { it.isOpen && (it.attributes["userId"] as? String) == userId } ?: emptyList()) +
            (broadcasters[roomId]?.takeIf { it.isOpen && (it.attributes["userId"] as? String) == userId }?.let { listOf(it) } ?: emptyList())
        if (targets.isEmpty()) return
        val msg = objectMapper.writeValueAsString(mapOf(
            "type" to "KICKED",
            "roomId" to roomId,
            "userId" to userId,
            "payload" to mapOf("reason" to reason)
        ))
        targets.forEach { s ->
            runCatching { s.sendMessage(TextMessage(msg)) }
            runCatching { s.close(CloseStatus.POLICY_VIOLATION) }
            removeSession(s, roomId, userId)
        }
        runCatching { liveStreamService.removeViewer(roomId, userId) }
        log.info("disconnectUser {} room {} reason={}", userId, roomId, reason)
    }

    /**
     * إرسال متزامن لكل جلسة — جلسات Spring غير آمنة خيطياً، والإرسال المتزامن
     * من خيط الإشارة وجدول الإحصائيات كان يرمي TEXT_PARTIAL_WRITING ويضيع الحدث.
     * الأقفال تُنظَّف مع الجلسة في removeSession حتى لا تتسرب.
     */
    private val sendLocks = ConcurrentHashMap<String, Any>()

    private fun sendSafe(session: WebSocketSession, message: TextMessage) {
        val lock = sendLocks.computeIfAbsent(session.id) { Any() }
        synchronized(lock) {
            runCatching { if (session.isOpen) session.sendMessage(message) }
        }
    }

    private fun removeSession(session: WebSocketSession, streamId: String, userId: String) {
        val role = sessionRole.remove(session.id) ?: return
        sendLocks.remove(session.id)
        sessionUser.remove(session.id)
        sessionToStream.remove(session.id)
        when (role) {
            Role.BROADCASTER -> {
                broadcasters.remove(streamId, session)
                val vs = viewers[streamId]
                val endMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "STREAM_ENDED",
                    "roomId" to streamId,
                    "payload" to mapOf("userId" to userId)
                ))
                vs?.forEach { sendSafe(it, TextMessage(endMsg)) }
            }
            Role.VIEWER -> {
                viewers[streamId]?.remove(session)
                if (viewers[streamId]?.isEmpty() == true) viewers.remove(streamId)
                // مزامنة المغادرة مع REST (إصلاح الشبح المتضخم)
                runCatching { liveStreamService.removeViewer(streamId, userId) }
            }
        }
        val all = mutableSetOf<WebSocketSession>()
        broadcasters[streamId]?.let { if (it.isOpen) all.add(it) }
        viewers[streamId]?.filter { it.isOpen }?.let { all.addAll(it) }

        // توحيد المغادرة: userId على المستوى الأعلى (مثل VIEWER_JOINED) إضافة
        // للحمولة — العملاء يقرأون signal.userId مع fallback للحمولة.
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to streamId,
            "userId" to userId,
            "payload" to mapOf("userId" to userId, "role" to role.name)
        ))
        all.forEach { sendSafe(it, TextMessage(leaveMsg)) }

        val countMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "VIEWER_COUNT_UPDATED",
            "roomId" to streamId,
            "payload" to mapOf("viewerCount" to (viewers[streamId]?.size ?: 0))
        ))
        all.forEach { sendSafe(it, TextMessage(countMsg)) }
        // تنظيف مفاتيح حدّ المعدل عند فراغ البث — وإلا تراكمت بلا حد.
        if (broadcasters[streamId] == null && viewers[streamId].isNullOrEmpty()) {
            val prefix = "$streamId:"
            lastChatAt.keys.removeIf { it.startsWith(prefix) }
            lastGiftAt.keys.removeIf { it.startsWith(prefix) }
        }
    }

    companion object {
        private val STREAM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
        const val CHAT_MIN_INTERVAL_MS = 1200L
        const val REACTION_MIN_INTERVAL_MS = 800L
        const val GIFT_MIN_INTERVAL_MS = 3000L
    }
}
