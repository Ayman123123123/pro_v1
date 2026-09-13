package com.red.server.websocket

import com.google.protobuf.ByteString
// MessageDocument lives top-level in database/SovereignMongoDocuments.kt
import com.red.server.database.MessageDocument
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.RedisManager
import com.red.server.messaging.DeleteService
import com.red.server.messaging.MessageService
import com.red.server.services.AdminUserIntelligenceService
import com.red.server.services.NotificationService
import com.red.server.social.UserStatusService
import com.red.sovereign.proto.RedProtos
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.red.server.auth.RedIdGenerator

@Component
class RedMasterHandler(
    private val messages: MessageService,
    private val deletes: DeleteService,
    private val redisManager: RedisManager,
    private val redis: StringRedisTemplate,
    private val userIntelligence: AdminUserIntelligenceService,
    private val accessGuard: ApprovedDeviceSessionGuard,
    private val notifications: NotificationService,
    private val users: UserAccountRepository,
    private val jdbc: JdbcTemplate,
    private val presencePrivacy: UserStatusService
) : BinaryWebSocketHandler() {
    private val log = LoggerFactory.getLogger(RedMasterHandler::class.java)
    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
    // Per-connection fixed-window guard: bounds CPU/DB work a single socket can demand
    // before a distributed gateway-level limit is applied.
    private val frameLimiter = WebSocketRateLimiter(maxMessages = 120, windowMillis = 60_000)

    override fun handleBinaryMessage(session: WebSocketSession, frame: BinaryMessage) {
        // Revalidate the long-lived socket on every frame: rate-limit first (cheap, in-memory),
        // then confirm the account + device are still APPROVED so a revoked/disabled device is
        // dropped immediately with POLICY_VIOLATION rather than after token expiry.
        if (!frameLimiter.tryAcquire(session.id) ||
            !accessGuard.isStillAuthorized(
                session.attributes["accountId"] as? String,
                session.attributes["deviceId"] as? String
            )
        ) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        // تحديث حضور لحظي عند كل إطار — يبقي red:presence:index حياً ويحدّث last_seen
        touchPresence(session)
        // Never let a malformed frame crash the handler thread — close the socket as BAD_DATA.
        val envelope = runCatching { RedProtos.RedRED.parseFrom(frame.payload) }.getOrElse {
            session.close(CloseStatus.BAD_DATA)
            return
        }
        // 🛡️ أي فشل في معالجة المغلف (تحقق/عضوية/تسلسل) لا يقتل المقبس — نسجّل ونكمل.
        handleEnvelopeSafely(session, envelope)
    }

    /** تحديث حضور فوري و last_seen — يُستدعى عند كل إطار للحفاظ على نافذة 5د حية */
    private fun touchPresence(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        val now = System.currentTimeMillis().toDouble()
        runCatching { redis.opsForZSet().add("red:presence:index", redId, now) }
        // تحديث last_seen في DB بشكل خفيف (لا ننتظر النتيجة)
        runCatching { jdbc.update("UPDATE users SET last_seen = ?, updated_at = ? WHERE red_id = ?", Instant.now(), Instant.now(), redId) }
        // أيضاً تحديث حالة ONLINE في UserStatusService للوحة والخصوصية
        runCatching { redis.opsForSet().add("users:online", redId) }
    }

    /** معالجة مغلف واحد بعزل الأخطاء — مرونة بمستوى واتساب: رسالة مرفوضة
     *  تُسجَّل فقط، ويبقى اتصال المرسل حياً لاستقبال الرسائل التالية. */
    private fun handleEnvelopeSafely(session: WebSocketSession, envelope: RedProtos.RedRED) {
        try {
            when (envelope.signalCase) {
                RedProtos.RedRED.SignalCase.MESSAGE -> receiveMessage(session, envelope.message)
                RedProtos.RedRED.SignalCase.ACK -> receiveAck(session, envelope.ack)
                RedProtos.RedRED.SignalCase.TYPING -> receiveTyping(session, envelope.typing)
                RedProtos.RedRED.SignalCase.SYNC_REQ -> sync(session, envelope.syncReq)
                RedProtos.RedRED.SignalCase.DELETE -> delete(session, envelope.delete)
                RedProtos.RedRED.SignalCase.REMOTE_WIPE_ACK -> receiveRemoteWipeAck(session, envelope.remoteWipeAck)
                else -> Unit
            }
        } catch (e: Exception) {
            log.warn("Rejected {} frame from session {}: {}", envelope.signalCase, session.id, e.message)
        }
    }

    private fun receiveRemoteWipeAck(session: WebSocketSession, ack: RedProtos.RemoteWipeAck) {
        // The account UUID is set by JwtHandshakeInterceptor and already validated by accessGuard
        // above, so it is a parseable UUID string here.
        val accountId = session.attributes["accountId"] as? String ?: return
        userIntelligence.markRemoteWipeAcknowledged(UUID.fromString(accountId), ack.commandId)
    }

    private fun receiveMessage(session: WebSocketSession, incoming: RedProtos.ChatMessage) {
        val sender = userId(session)
        require(incoming.senderId == sender) { "senderId does not match authenticated RED ID" }
        val stored = messages.processIncoming(incoming)
        send(session, ack(stored, "SENT"))
        sendToDevice(stored.receiverId, stored.receiverDeviceId, messageEnvelope(stored))
        // المستلم غير متصل الآن إطلاقاً — نسجّل إشعاراً داخل التطبيق ونحاول FCM اختياري
        // كي لا تُفوَّت الرسالة حتى لو لم يفتح التطبيق (البريد المعلق يغطي إعادة الاتصال فقط).
        val receiverHasLiveSession = sessions[stored.receiverId]?.values?.any { it.isOpen } == true
        if (!receiverHasLiveSession) {
            notifications.sendChatMessagePush(stored.receiverId, stored.senderId)
        }
        // Synchronize the sender's other approved devices without echoing to this socket.
        sendToUser(sender, messageEnvelope(stored), exceptSessionId = session.id)
    }

    private fun receiveAck(session: WebSocketSession, incoming: RedProtos.MessageAck) {
        val recipient = userId(session)
        val deviceId = session.attributes["protocolDeviceId"] as? Int ?: error("Protocol device is missing")
        // احترام خصوصية إيصالات القراءة — إن كان المرسل الأصلي حظر READ، نمنع التحديث
        if (incoming.status == "READ") {
            val storedOpt = runCatching { messages.findMessage(incoming.messageId) }.getOrNull()
            if (storedOpt != null) {
                val senderRedId = storedOpt.senderId
                val privacy = runCatching { presencePrivacy.getPrivacySettings(senderRedId) }.getOrNull()
                if (privacy != null && privacy.readReceipts == "NOBODY") {
                    // المرسل لا يريد إيصالات قراءة — نتجاهل READ ونعتبره DELIVERED فقط
                    val delivered = messages.acknowledge(recipient, deviceId, incoming.messageId, "DELIVERED")
                    val ackDelivered = ack(delivered, delivered.status)
                    sendToUser(delivered.senderId, ackDelivered)
                    sendToUser(delivered.receiverId, ackDelivered, exceptSessionId = session.id)
                    return
                }
                if (privacy != null && privacy.readReceipts == "CONTACTS") {
                    val senderIsContact = runCatching { messages.isContact(senderRedId, recipient) }.getOrDefault(false)
                    if (!senderIsContact && senderRedId != recipient) {
                        val delivered = messages.acknowledge(recipient, deviceId, incoming.messageId, "DELIVERED")
                        val ackDelivered = ack(delivered, delivered.status)
                        sendToUser(delivered.senderId, ackDelivered)
                        sendToUser(delivered.receiverId, ackDelivered, exceptSessionId = session.id)
                        return
                    }
                }
            }
        }
        val stored = messages.acknowledge(recipient, deviceId, incoming.messageId, incoming.status)
        val ack = ack(stored, stored.status)
        sendToUser(stored.senderId, ack)
        sendToUser(stored.receiverId, ack, exceptSessionId = session.id)
    }

    private fun receiveTyping(session: WebSocketSession, typing: RedProtos.TypingRED) {
        val sender = userId(session)
        require(typing.userId == sender) { "userId does not match authenticated RED ID" }
        // احترام خصوصية typing — إن عطّل المرسل مؤشرات الكتابة لا تُبث (اختياري مستقبلاً)
        // حالياً نحترم فقط عدم الإزعاج للمحظورين عبر requireDirectAllowed
        // حفظ مؤقت + بث فوري + تنظيف تلقائي TTL 5s عبر RedisManager
        redisManager.setTyping(sender, typing.conversationId)
        // 📝 مؤشر الكتابة الجماعي: conversationId = معرف مجموعة (UUID > 32) — يُبث لكل الأعضاء
        if (typing.conversationId.length > 32) {
            messages.requireGroupMember(typing.conversationId, sender)
            val envelope = RedProtos.RedRED.newBuilder().setTyping(typing).build()
            messages.groupMemberRedIds(typing.conversationId)
                .filter { it != sender }
                .forEach { sendToUser(it, envelope) }
            // أيضاً نشر عبر قناة red:typing الجماعية للتكامل مع الخدمات الأخرى
            runCatching { redis.convertAndSend("red:typing", "${typing.conversationId}:$sender:${typing.isTyping}") }
        } else {
            require(typing.targetUserId.isNotBlank() && typing.targetUserId != sender) { "targetUserId is required" }
            messages.requireDirectAllowed(sender, typing.targetUserId)
            // فحص خصوصية target إن كان حظر typing (مستقبلاً)
            sendToUser(typing.targetUserId, RedProtos.RedRED.newBuilder().setTyping(typing).build())
            runCatching { redis.convertAndSend("red:typing", "${typing.conversationId}:$sender:${typing.isTyping}") }
        }
    }

    private fun sync(session: WebSocketSession, request: RedProtos.SyncRequest) {
        messages.getMissedMessages(userId(session), request.conversationId, request.fromSequence, request.toSequence)
            .forEach { send(session, messageEnvelope(it)) }
    }

    private fun delete(session: WebSocketSession, request: RedProtos.DeleteRED) {
        if (!request.forEveryone) return
        val original = deletes.deleteForEveryone(request.messageId, userId(session)) ?: return
        val envelope = RedProtos.RedRED.newBuilder().setDelete(request).build()
        sendToUser(original.senderId, envelope)
        sendToUser(original.receiverId, envelope)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = userId(session)
        sessions.computeIfAbsent(redId) { ConcurrentHashMap() }[session.id] = session
        val protocolDeviceId = session.attributes["protocolDeviceId"] as? Int
            ?: throw IllegalStateException("Messaging requires an approved protocol device")
        val now = System.currentTimeMillis().toDouble()
        redis.opsForZSet().add("red:presence:index", redId, now)
        redis.opsForSet().add("users:online", redId)
        // تحديث last_seen فوري في قاعدة البيانات
        runCatching { jdbc.update("UPDATE users SET last_seen = ?, updated_at = ? WHERE red_id = ?", Instant.now(), Instant.now(), redId) }
        runCatching { jdbc.update("UPDATE users SET last_seen = ?, updated_at = ? WHERE red_id = ? AND last_seen IS NULL", Instant.now(), Instant.now(), redId) }
        messages.pendingFor(redId, protocolDeviceId).forEach { send(session, messageEnvelope(it)) }
        log.debug("Presence ONLINE for {} (sessions={})", redId, sessions[redId]?.size)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        frameLimiter.remove(session.id)
        val redId = session.attributes["userId"] as? String ?: return
        val removed = sessions[redId]?.let { userSessions ->
            userSessions.remove(session.id)
            if (userSessions.isEmpty()) {
                sessions.remove(redId, userSessions)
                true
            } else false
        } ?: false
        // إن لم يعد له أي جلسة حية — اعتبره offline فعلياً وحذّث last_seen
        if (removed || sessions[redId].isNullOrEmpty()) {
            runCatching { redis.opsForZSet().remove("red:presence:index", redId) }
            runCatching { redis.opsForSet().remove("users:online", redId) }
            runCatching { jdbc.update("UPDATE users SET last_seen = ?, updated_at = ? WHERE red_id = ?", Instant.now(), Instant.now(), redId) }
            log.debug("Presence OFFLINE for {}", redId)
        }
    }

    /** تنظيف دوري للـ ZSet — يزيل الإدخالات التي تجاوزت نافذة 5 دقائق (حتى بلا فتح الدشبورد) */
    @Scheduled(fixedRate = 60_000)
    fun cleanupStalePresence() {
        val cutoff = (System.currentTimeMillis() - 5 * 60_000L).toDouble()
        runCatching { redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff) }
        // مزامنة users:online مع ZSet الحية
        runCatching {
            val live = redis.opsForZSet().range("red:presence:index", 0, -1) ?: emptySet()
            val online = redis.opsForSet().members("users:online") ?: emptySet()
            (online - live).forEach { redis.opsForSet().remove("users:online", it) }
        }
    }

    private fun sendToDevice(redId: String, protocolDeviceId: Int, envelope: RedProtos.RedRED) {
        sessions[redId]?.values?.filter { it.isOpen && it.attributes["protocolDeviceId"] == protocolDeviceId }?.forEach { send(it, envelope) }
    }

    private fun sendToUser(redId: String, envelope: RedProtos.RedRED, exceptSessionId: String? = null) {
        sessions[redId]?.values?.filter { it.isOpen && it.id != exceptSessionId }?.forEach { send(it, envelope) }
    }

    private fun send(session: WebSocketSession, envelope: RedProtos.RedRED) {
        synchronized(session) {
            if (session.isOpen) session.sendMessage(BinaryMessage(envelope.toByteArray()))
        }
    }

    private fun messageEnvelope(message: MessageDocument): RedProtos.RedRED {
        val value = RedProtos.ChatMessage.newBuilder()
            .setId(message.uuid).setConversationId(message.conversationId)
            .setSenderId(message.senderId).setReceiverId(message.receiverId)
            .setPayload(ByteString.copyFrom(message.payload)).setTimestamp(message.createdAt.toEpochMilli())
            .setSequenceNumber(message.sequenceNumber).setType(message.messageType)
            .setSenderDeviceId(message.senderDeviceId).setReceiverDeviceId(message.receiverDeviceId)
            .setCiphertextType(message.ciphertextType).build()
        return RedProtos.RedRED.newBuilder().setMessage(value).build()
    }

    private fun ack(message: MessageDocument, status: String): RedProtos.RedRED = RedProtos.RedRED.newBuilder().setAck(
        RedProtos.MessageAck.newBuilder().setMessageId(message.uuid).setSequenceNumber(message.sequenceNumber).setStatus(status)
    ).build()

    /**
     * 🧨 إشعار فوري بمسح التطبيق عن بُعد (أمر إداري).
     * النوع المخصص RemoteWipe/RemoteWipeAck أصبح موجوداً في shared-proto الآن، لكن للتوافق مع
     * إصدارات عميل Android الحالية نُبقي على رسالة SYSTEM بـ JSON كمسار الإشعار اللحظي best-effort —
     * العميل يتعرف عليها ويرد بـ RemoteWipeAck{commandId} التي يعالجها receiveRemoteWipeAck لتعليم
     * الحالة ACKNOWLEDGED. المسار القاطع يبقى RedSecurityService.sendWipeSignal وحالة remoteWipeStatus
     * التي يفحصها التطبيق عند التشغيل.
     */
    fun sendRemoteWipe(redId: String, commandId: String, reason: String) {
        val payload = """{"command":"REMOTE_APP_WIPE","commandId":"$commandId","reason":"$reason"}"""
        val control = RedProtos.ChatMessage.newBuilder()
            .setId(commandId)
            .setConversationId("red-control")
            .setSenderId(RedIdGenerator.SYSTEM_ID) // محجوز للنظام: لا يُخصَّص لمستخدم فيُنتحل به
            .setReceiverId(redId)
            .setPayload(ByteString.copyFrom(payload, Charsets.UTF_8))
            .setTimestamp(System.currentTimeMillis())
            .setType("SYSTEM")
            .setCiphertextType(0)
            .build()
        sendToUser(redId, RedProtos.RedRED.newBuilder().setMessage(control).build())
    }

    /**
     * 🔔 بث لحظي بتغيّر عضوية/حالة مجموعة لكل الأعضاء المتصلين (GROUP_SYNC).
     * رسالة تحكم غير مخزّنة: العميل عند استلامها يُحدّث قائمة مجموعاته ويعيد توزيع مفاتيح
     * Sender Key للعضو الجديد عند أول إرسال. الأعضاء غير المتصلين يلتقطون الحالة عبر load() العادي.
     */
    @org.springframework.context.event.EventListener
    fun onGroupMembershipChanged(event: com.red.server.groups.GroupMembershipChangedEvent) {
        val payload = """{"groupId":"${event.groupId}"}"""
        event.memberRedIds.forEach { redId ->
            val control = RedProtos.ChatMessage.newBuilder()
                .setId("sync-${event.groupId}-${System.nanoTime()}")
                .setConversationId("red-control")
                .setSenderId(RedIdGenerator.SYSTEM_ID)
                .setReceiverId(redId)
                .setPayload(ByteString.copyFrom(payload, Charsets.UTF_8))
                .setTimestamp(System.currentTimeMillis())
                .setType("GROUP_SYNC")
                .setCiphertextType(0)
                .build()
            sendToUser(redId, RedProtos.RedRED.newBuilder().setMessage(control).build())
        }
    }

    private fun userId(session: WebSocketSession): String =
        session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
}
