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
    // خنق كتابة الحضور: آخر ZADD لكل redId — يُسقَط ما دون العتبة بلا أي عملية Redis.
    private val presenceLastTouch = ConcurrentHashMap<String, Long>()
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
        // حضور مخنوق (≤ كتابة/20s لكل مستخدم) — يبقي red:presence:index حياً دون ZADD لكل إطار.
        touchPresence(session)
        // Never let a malformed frame crash the handler thread — close the socket as BAD_DATA.
        val envelope = runCatching { RedProtos.RedRED.parseFrom(frame.payload) }.getOrElse {
            session.close(CloseStatus.BAD_DATA)
            return
        }
        // 🛡️ أي فشل في معالجة المغلف (تحقق/عضوية/تسلسل) لا يقتل المقبس — نسجّل ونكمل.
        handleEnvelopeSafely(session, envelope)
    }

    /** تحديث حضور مخنوق: كتابة Redis واحدة كل 20s لكل مستخدم بدل ZADD+SADD لكل إطار
     * (كان حتى 120 كتابة/دقيقة/مستخدم عند حد الـ frameLimiter). نافذة التطهير 5m
     * أوسع بكثير من دورة الخنق فلا يُسقَط أي حيّ خطأً. الـ ZSET بلا TTL — يُطهَّر
     * حسب Score في cleanupStalePresence لا بـ EXPIRE. */
    private fun touchPresence(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        val nowMs = System.currentTimeMillis()
        val last = presenceLastTouch[redId] ?: 0L
        if (nowMs - last < PRESENCE_TOUCH_THROTTLE_MS) return
        presenceLastTouch[redId] = nowMs
        val now = nowMs.toDouble()
        runCatching { redis.opsForZSet().add("red:presence:index", redId, now) }
        // أيضاً تحديث حالة ONLINE في UserStatusService للوحة والخصوصية
        runCatching { redis.opsForSet().add("red:online", redId) }
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
                RedProtos.RedRED.SignalCase.REACTION -> receiveReaction(session, envelope.reaction)
                // (2026-09-15) حُذف CALL_SIGNAL — كان مساراً ميتاً (Log فقط، بلا عميل
                // يرسله). المسار الحي لإشارات المكالمات هو /ws/calls (JSON) في
                // CallWebSocketHandler — انظر الخطة الأسطورية §1. Proto: حُذف حقل
                // call_signal=9 من red_protocol.proto، وعملاء قدامى يُتجاهَلون بأمان.
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

    /**
     * 😀 التفاعلات — تُحفظ في Mongo (خاصة/مجموعة/قناة + مرآة Postgres) ثم تُبث
     * لكل المشاركين: جلسات المرسل الأخرى + المستلم/الأعضاء — لا صدى للمرسل وحده.
     * remove=true هو REACTION_REMOVE: يحذف تفاعل المستخدم ثم يبث الحذف للجميع.
     * أي فشل (رسالة مجهولة، غير مشارك، إيموجي غير صالح) يُسجَّل فقط ويبقى المقبس حياً.
     */
    private fun receiveReaction(session: WebSocketSession, reaction: RedProtos.ReactionRED) {
        val reactor = userId(session)
        val target = try {
            messages.toggleReaction(reaction.targetMessageId, reactor, reaction.emoji, reaction.remove)
        } catch (e: Exception) {
            log.warn("Rejected reaction from {} on {}: {}", reactor, reaction.targetMessageId, e.message)
            return
        }
        val envelope = RedProtos.RedRED.newBuilder().setReaction(reaction).build()
        target.recipients.forEach { redId ->
            if (redId == reactor) sendToUser(redId, envelope, exceptSessionId = session.id)
            else sendToUser(redId, envelope)
        }
    }

    // (2026-09-15) حُذف receiveCallSignal — كان Log فقط (مسار CALL_SIGNAL الميت).
    // أي CALL_SIGNAL من عميل قديم يصلك الآن كحقل proto مجهول ويُتجاهل بأمان (else).

    private fun receiveMessage(session: WebSocketSession, incoming: RedProtos.ChatMessage) {
        val sender = userId(session)
        require(incoming.senderId == sender) { "senderId does not match authenticated RED ID" }
        val stored = messages.processIncoming(incoming)
        send(session, ack(stored, "SENT"))
        val envelope = messageEnvelope(stored)
        // FIX (messages reach device): وسم الجهاز مرآة لتخمين المرسل وقد ينحرف
        // (إعادة تثبيت / إعادة تسجيل / استعادة نسخة احتياطية). كان الانحراف يعني:
        // sendToDevice لا يطابق أي جلسة ⇒ لا تسليم حيّ، وreceiverHasLiveSession يبقى
        // صحيحاً (جلسة حيّة على جهاز آخر) ⇒ لا دفع أيضاً ⇒ الرسالة مخزَّنة لكن غير
        // مرئية أبداً. النسخة المكافئة أُصلحت في العميل
        // (RedConnectionService.handleEnvelope: التسامح مع الوسم المنحرف لأن وسم
        // التشفير وحده يحسم من يفكّ) — فنُسقط هنا إلى كل جلسات الحساب.
        sendToDevice(stored.receiverId, stored.receiverDeviceId, envelope) ||
            sendToUser(stored.receiverId, envelope)
        // المستلم غير متصل الآن إطلاقاً — نسجّل إشعاراً داخل التطبيق ونحاول الدفع السيادي
        // كي لا تُفوَّت الرسالة حتى لو لم يفتح التطبيق (البريد المعلق يغطي إعادة الاتصال فقط).
        val receiverHasLiveSession = sessions[stored.receiverId]?.values?.any { it.isOpen } == true
        if (!receiverHasLiveSession) {
            notifications.sendChatMessagePush(stored.receiverId, stored.senderId)
        }
        // Synchronize the sender's other approved devices without echoing to this socket.
        sendToUser(sender, envelope, exceptSessionId = session.id)
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
        // خصوصية مؤشر الكتابة للكاتب نفسه: NOBODY يسقط البث كله، CONTACTS يشترط جهة متبادلة.
        // (كان البث يتم دائماً — الإعداد المحلي لا يمنع شيئاً على الخادم).
        val typingScope = runCatching { presencePrivacy.getPrivacySettings(sender).typingIndicators }.getOrDefault("EVERYONE")
        if (typingScope == "NOBODY") return
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
            if (typingScope == "CONTACTS" &&
                !runCatching { messages.isContact(sender, typing.targetUserId) }.getOrDefault(false)
            ) return
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
        // دفاع عمقي: المعترض يرفض /ws/master بلا جهاز أصلاً (401). إن وصلت جلسة
        // بلا protocolDeviceId لسبب ما، إغلاق نظيف 1008 بدل رمي استثناء (كان ERROR
        // متكرراً كل دقائق + حلقة reconnect تبدو كتعليق).
        val protocolDeviceId = session.attributes["protocolDeviceId"] as? Int
        if (protocolDeviceId == null) {
            log.warn("Closing master socket without protocol device for {}", redId)
            runCatching { session.close(CloseStatus.POLICY_VIOLATION) }
            return
        }
        val now = System.currentTimeMillis().toDouble()
        presenceLastTouch[redId] = System.currentTimeMillis()
        redis.opsForZSet().add("red:presence:index", redId, now)
        redis.opsForSet().add("red:online", redId)
        // تحديث last_seen فوري في قاعدة البيانات (مرة واحدة — كانت مكررة بسطر ثانٍ زائد)
        runCatching { jdbc.update("UPDATE users SET last_seen = ?, updated_at = ? WHERE red_id = ?", Instant.now(), Instant.now(), redId) }
        // FIX (رسالة مفقودة): المقيَّد بالجهاز وحده لا يسترجع شيئاً لجهاز انحرف معرّفه
        messages.pendingForDeviceOrAccount(redId, protocolDeviceId, liveDeviceIds(redId))
            .forEach { send(session, messageEnvelope(it)) }
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
            presenceLastTouch.remove(redId)
            runCatching { redis.opsForZSet().remove("red:presence:index", redId) }
            runCatching { redis.opsForSet().remove("red:online", redId) }
            runCatching { jdbc.update("UPDATE users SET last_seen = ?, updated_at = ? WHERE red_id = ?", Instant.now(), Instant.now(), redId) }
            log.debug("Presence OFFLINE for {}", redId)
        }
    }

    /** تنظيف دوري للـ ZSet — يزيل الإدخالات التي تجاوزت نافذة 5 دقائق (حتى بلا فتح الدشبورد) */
    @Scheduled(fixedRate = 60_000)
    fun cleanupStalePresence() {
        val cutoff = (System.currentTimeMillis() - 5 * 60_000L).toDouble()
        runCatching { redis.opsForZSet().removeRangeByScore("red:presence:index", 0.0, cutoff) }
        // مزامنة red:online مع ZSet الحية
        runCatching {
            val live = redis.opsForZSet().range("red:presence:index", 0, -1) ?: emptySet()
            val online = redis.opsForSet().members("red:online") ?: emptySet()
            (online - live).forEach { redis.opsForSet().remove("red:online", it) }
        }
    }

    /**
     * AUTO-FIX (message reliability): at-least-once redelivery pump.
     * afterConnectionEstablished() replays pending messages only once per connect and
     * MessageService.pendingFor() caps a single fetch, so a message whose ACK was lost - or a
     * backlog larger than one page - stayed undelivered until the client reconnected. This pump
     * re-sends still-`SENT` messages to currently connected devices until the client ACKs them.
     * Duplicates are harmless: the client stores messages by id (REPLACE), so a repeat frame is a no-op.
     */
    @Scheduled(fixedDelay = 30_000)
    fun redeliverPendingMessages() {
        sessions.forEach { (redId, perUser) ->
            perUser.values.filter { it.isOpen }.forEach deviceLoop@{ session ->
                val deviceId = session.attributes["protocolDeviceId"] as? Int ?: return@deviceLoop
                runCatching {
                    val cutoff = Instant.now().minusSeconds(PENDING_REDELIVER_MIN_AGE_SECONDS)
                    messages.pendingForDeviceOrAccount(redId, deviceId, liveDeviceIds(redId), PENDING_REDELIVER_LIMIT)
                        .filter { it.createdAt.isBefore(cutoff) }
                        .forEach { pending ->
                            // حُدّ المحاولات: رسالة لا تُقرّ أبداً (مفاتيح E2E مفقودة بإعادة تثبيت)
                            // كانت تُعاد كل 30s إلى الأبد. عند استنفاد المحاولات تُوسم FAILED فلا
                            // تعودها الاستعلامات (كلها تشترط status=SENT).
                            if (messages.recordDeliveryAttempt(pending.uuid, PENDING_REDELIVER_MAX_ATTEMPTS)) {
                                send(session, messageEnvelope(pending))
                            }
                        }
                }.onFailure { log.debug("redeliverPendingMessages failed for {}: {}", redId, it.message) }
            }
        }
    }

    /** @return true إذا سُلِّم لجهاز مطابق واحد على الأقل. */
    private fun sendToDevice(redId: String, protocolDeviceId: Int, envelope: RedProtos.RedRED): Boolean {
        val matched = sessions[redId]?.values?.filter { it.isOpen && it.attributes["protocolDeviceId"] == protocolDeviceId }
        matched?.forEach { send(it, envelope) }
        return matched?.isNotEmpty() == true
    }

    /** @return true إذا سُلِّم لجلسة واحدة على الأقل من جلسات الحساب. */
    private fun sendToUser(redId: String, envelope: RedProtos.RedRED, exceptSessionId: String? = null): Boolean {
        val targets = sessions[redId]?.values?.filter { it.isOpen && it.id != exceptSessionId }
        targets?.forEach { send(it, envelope) }
        return targets?.isNotEmpty() == true
    }

    /**
     * معرّفات الأجهزة المتصلة الآن لهذا الحساب.
     * تُستخدم لتفريق «وسم جهاز ميت/منحرف» (نسترجعه لأي جهاز حيّ) عن «جهاز حيّ آخر»
     * (لا نُزاحمه برسائله) — انظر MessageService.pendingForDeviceOrAccount.
     */
    private fun liveDeviceIds(redId: String): Set<Int> =
        sessions[redId]?.values
            ?.filter { it.isOpen }
            ?.mapNotNull { it.attributes["protocolDeviceId"] as? Int }
            ?.toSet()
            ?: emptySet()

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

    private companion object {
        /** خنق كتابة الحضور: كتابة Redis واحدة لكل مستخدم كل 20s (ضمن نطاق 10-30s).
         * نافذة التطهير 5m ≫ دورة الخنق فلا إسقاط خطأ للأحياء. */
        private const val PRESENCE_TOUCH_THROTTLE_MS = 20_000L
        /** AUTO-FIX (message reliability): max pending messages re-sent per device per tick. */
        private const val PENDING_REDELIVER_LIMIT = 50
        /** AUTO-FIX: never race the live push - only re-send messages older than this. */
        private const val PENDING_REDELIVER_MIN_AGE_SECONDS = 10L
        /** ~10 دقائق عند دورة 30s — بعدها تُوسم الرسالة FAILED بدل إعادة لا نهائية. */
        private const val PENDING_REDELIVER_MAX_ATTEMPTS = 20
    }
}
