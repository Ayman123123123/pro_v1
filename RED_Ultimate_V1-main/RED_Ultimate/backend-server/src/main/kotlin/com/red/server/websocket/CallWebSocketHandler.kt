package com.red.server.websocket

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.ObjectMapper
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.ActiveCallRegistry
import com.red.server.calls.CallType
import com.red.server.calls.RoomAliasService
import com.red.server.calls.RoomSeparationPolicy
import com.red.server.services.NotificationService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Authenticated WebRTC signaling router with multi-device ringing and offline offer mailbox. */
@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val history: CallHistoryService,
    private val notifications: NotificationService,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val roomAliases: RoomAliasService? = null,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val activeCalls: ActiveCallRegistry? = null
) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>()
    private val pending = ConcurrentHashMap<String, CopyOnWriteArrayList<PendingCallSignal>>()
    private val groupRooms = ConcurrentHashMap<String, GroupCallRoom>()

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val source = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val signal = objectMapper.readValue(message.payload, IncomingCallSignal::class.java)
        val type = signal.type.uppercase()
        when (type) {
            // دعوة مكالمة جماعية: targetUserId فارغ والقائمة في inviteeIds — يُرن لكل مدعو
            "GROUP_CALL_INVITE" -> {
                val rawGroupCallId = requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }
                val groupCallId = resolveRoom(rawGroupCallId)
                // حد واتساب: 32 مشاركاً كحد أقصى — كان الخادم يقبل عدداً غير محدود.
                val invitees = signal.inviteeIds.filter { it.isNotBlank() && it != source }.take(MAX_GROUP_CALL_MEMBERS)
                require(invitees.isNotEmpty()) { "inviteeIds is required" }
                groupRooms[groupCallId] = GroupCallRoom(host = source, members = invitees.toMutableList())
                val payload = signal.payload + ("hostName" to (signal.payload["hostName"] ?: ""))
                invitees.forEach { invitee ->
                    val outbound = OutgoingCallSignal(groupCallId, source, invitee, type, signal.mode.uppercase(), payload)
                    val targets = liveSessions(invitee)
                    if (targets.isEmpty()) {
                        enqueue(invitee, outbound)
                        notifications.sendVoipPushNotification(invitee, source, groupCallId, signal.mode)
                    } else {
                        val json = objectMapper.writeValueAsString(outbound)
                        targets.forEach { target -> target.sendSafe(TextMessage(json)) }
                    }
                }
                session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }

            // ردود الأعضاء إلى المضيف: ACCEPT/DECLINE جوابٌ على الدعوة
            // فوجهته المضيف طبعًا.
            "GROUP_CALL_ACCEPT", "GROUP_CALL_DECLINE" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                // Kicked members rejoining (stale invite / message tap): bounce them out cleanly.
                if (type == "GROUP_CALL_ACCEPT" && room != null && room.kicked.any { it.equals(source, ignoreCase = true) }) {
                    val bounce = OutgoingCallSignal(groupCallId, room.host, source, "GROUP_CALL_END", signal.mode.uppercase(), mapOf("reason" to "kicked"))
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(bounce)))
                    return
                }
                // وجهة صريحة إن أرسلها العميل، وإلا المضيف، وإلا المصدر نفسه.
                val hostId = signal.targetUserId.takeIf { it.isNotBlank() } ?: room?.host ?: source
                val outbound = OutgoingCallSignal(groupCallId, source, hostId, type, signal.mode.uppercase(), signal.payload + ("memberStatus" to signal.memberStatus.orEmpty()))
                val targets = liveSessions(hostId)
                if (targets.isEmpty()) {
                    enqueue(hostId, outbound)
                } else {
                    val json = objectMapper.writeValueAsString(outbound)
                    targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }

            // ═══ GROUP_CALL_STATUS — تحديث سجل الأعضاء، لا رسالة للمضيف وحده ═══
            //
            // ⚠️ كان هذا مدموجًا مع ACCEPT/DECLINE فيُوجَّه دائمًا إلى
            // `room.host`. وذلك يكسر أهم مُنتِج للإشارة: **المضيف نفسه**.
            // فـGroupCallService.kt:224-232 يبثّ `memberStatus=no_answer`
            // لكل عضو لم يُجب عند انتهاء مهلة الرنين (45s)، وبما أن المرسل
            // هو المضيف فإن `hostId == source` ⇒ ترتد الإشارة إلى المضيف
            // نفسه ولا يعلم أي عضو أن الرنين انتهى، فتبقى بطاقات الأعضاء
            // على RINGING أبدًا في واجهات البقية.
            //
            // ودلالة الإشارة أصلًا «حالة عضو تغيّرت» — معلومة تخصّ كل من في
            // الغرفة لا المضيف فقط، خصوصًا في المكالمة الشبكية (mesh) حيث
            // يبني كل عضو اتصالًا مباشرًا بالبقية ويحتاج سجلًا متطابقًا.
            //
            // الترتيب: وجهة صريحة إن وُجدت، ثم كل مشاركي الغرفة عدا المصدر،
            // ثم المضيف كسقوط أخير حين لا تكون الغرفة معروفة للخادم (مثل
            // إعادة تشغيله وسط مكالمة).
            "GROUP_CALL_STATUS" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                val recipients: List<String> = when {
                    signal.targetUserId.isNotBlank() -> listOf(signal.targetUserId)
                    room != null -> (room.members + room.host)
                        .filter { it.isNotBlank() && it != source }
                        .distinct()
                    else -> emptyList()
                }.ifEmpty { listOf(source) }

                val enriched = signal.payload + ("memberStatus" to signal.memberStatus.orEmpty())
                recipients.forEach { recipient ->
                    val outbound = OutgoingCallSignal(groupCallId, source, recipient, type, signal.mode.uppercase(), enriched)
                    val targets = liveSessions(recipient)
                    if (targets.isEmpty()) {
                        enqueue(recipient, outbound)
                    } else {
                        val json = objectMapper.writeValueAsString(outbound)
                        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
                    }
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            "GROUP_CALL_END" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms.remove(groupCallId) ?: groupRooms.remove(signal.callId?.trim().orEmpty())
                val targets = (room?.members ?: emptyList()) + room?.host
                dropPending(groupCallId)
                targets.filterNotNull().filter { it.isNotBlank() && it != source }.forEach { memberId ->
                    val outbound = OutgoingCallSignal(groupCallId, source, memberId, type, signal.mode.uppercase(), signal.payload)
                    val memberTargets = liveSessions(memberId)
                    if (memberTargets.isEmpty()) enqueue(memberId, outbound)
                    else memberTargets.forEach { t -> runCatching { t.sendMessage(TextMessage(objectMapper.writeValueAsString(outbound))) } }
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            // كتم الكل — المضيف فقط، يُبث لكل الأعضاء (كان يُسقط: لا targetUserId فيُرفض).
            "GROUP_CALL_MUTE_ALL" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                if (room == null || !room.host.equals(source, ignoreCase = true)) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                    return
                }
                (room.members + room.host)
                    .filter { it.isNotBlank() && !it.equals(source, ignoreCase = true) }
                    .distinct()
                    .forEach { memberId ->
                        val outbound = OutgoingCallSignal(groupCallId, source, memberId, type, signal.mode.uppercase(), signal.payload)
                        val memberTargets = liveSessions(memberId)
                        if (memberTargets.isEmpty()) enqueue(memberId, outbound)
                        else memberTargets.forEach { t -> runCatching { t.sendMessage(TextMessage(objectMapper.writeValueAsString(outbound))) } }
                    }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            // طرد عضو — المضيف فقط: يُحذف من الغرفة + يُمنع من العودة + يُبث للجميع.
            "GROUP_CALL_KICK" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                val victim = (signal.payload["memberId"] as? String).orEmpty()
                if (room == null || !room.host.equals(source, ignoreCase = true) || victim.isBlank()
                    || victim.equals(source, ignoreCase = true)
                ) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                    return
                }
                room.members.removeIf { it.equals(victim, ignoreCase = true) }
                room.kicked.add(victim)
                ((room.members + room.host + victim).filter { it.isNotBlank() }.distinct()).forEach { memberId ->
                    val outbound = OutgoingCallSignal(groupCallId, source, memberId, type, signal.mode.uppercase(), signal.payload)
                    val memberTargets = liveSessions(memberId)
                    if (memberTargets.isEmpty()) enqueue(memberId, outbound)
                    else memberTargets.forEach { t -> runCatching { t.sendMessage(TextMessage(objectMapper.writeValueAsString(outbound))) } }
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
            // كتم عضو واحد — المضيف فقط: يُوجَّه للعضو نفسه (+ المضيف يعرف ضمنياً).
            "GROUP_CALL_MUTE_MEMBER" -> {
                val groupCallId = resolveRoom(requireCallId(signal))
                val room = groupRooms[groupCallId] ?: groupRooms[signal.callId?.trim().orEmpty()]
                val victim = (signal.payload["memberId"] as? String).orEmpty()
                if (room == null || !room.host.equals(source, ignoreCase = true) || victim.isBlank()
                    || victim.equals(source, ignoreCase = true)
                ) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                    return
                }
                val outbound = OutgoingCallSignal(groupCallId, source, victim, type, signal.mode.uppercase(), signal.payload)
                val memberTargets = liveSessions(victim)
                if (memberTargets.isEmpty()) enqueue(victim, outbound)
                else memberTargets.forEach { t -> runCatching { t.sendMessage(TextMessage(objectMapper.writeValueAsString(outbound))) } }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }
        }

        // Group-call media/signaling (mesh) reuses the 1:1 envelope but carries the group call id
        // as `callId`. Relay those to the room members instead of creating a bogus 1:1 call record
        // (a shared callId could otherwise collide and abort the frame).
        val rawCallId = signal.callId?.trim().orEmpty()
        val groupRoom = if (rawCallId.isNotBlank()) {
            groupRooms[resolveRoom(rawCallId)] ?: groupRooms[rawCallId]
        } else null
        if (groupRoom != null) {
            val recipients = signal.targetUserId.takeIf { it.isNotBlank() && it != source }
                ?.let { listOf(it) }
                ?: (groupRoom.members + groupRoom.host)
                    .filter { it.isNotBlank() && !it.equals(source, ignoreCase = true) }
                    .distinct()
            recipients.forEach { recipient ->
                val outbound = OutgoingCallSignal(rawCallId, source, recipient, type, signal.mode.uppercase(), signal.payload)
                val targets = liveSessions(recipient)
                if (targets.isNotEmpty()) {
                    val json = objectMapper.writeValueAsString(outbound)
                    targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
                }
            }
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to rawCallId))))
            return
        }
        if (signal.targetUserId.isBlank()) {
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "ERROR",
                "callId" to rawCallId,
                "payload" to mapOf("error" to "targetUserId is required")
            ))))
            return
        }
        if (signal.targetUserId.equals(source, ignoreCase = true)) {
            session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf(
                "type" to "ERROR",
                "callId" to rawCallId,
                "payload" to mapOf("error" to "Cannot call the same RED ID")
            ))))
            return
        }
        val callId = when (type) {
            "OFFER" -> {
                // BUSY: if the callee is already in an established call, answer BUSY instead of
                // ringing. The registry is only populated once a call is answered.
                val busy = activeCalls?.let {
                    it.isInCall(signal.targetUserId) && !it.isActiveCall(rawCallId)
                } == true
                if (busy) {
                    val busySignal = OutgoingCallSignal(rawCallId, signal.targetUserId, source, "BUSY", signal.mode.uppercase(), emptyMap())
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(busySignal)))
                    session.sendSafe(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to rawCallId))))
                    return
                }
                history.start(source, signal.targetUserId, signal.targetUserId,
                    callTypeForMode(signal.mode), CallRoute.RED, signal.callId).id
            }
            "ANSWER" -> requireCallId(signal).also {
                history.answer(it, source)
                activeCalls?.register(it, listOf(source, signal.targetUserId))
            }
            "END" -> requireCallId(signal).also {
                history.end(it, source)
                activeCalls?.unregister(it)
            }
            "ICE", "HOLD", "RESUME", "RENEGOTIATE", "CALL_REACTION", "CALL_RAISE_HAND" -> requireCallId(signal)
            "REJECT" -> requireCallId(signal).also { id ->
                val doc = runCatching { history.findById(id) }.getOrNull()
                if (doc != null && doc.status == CallStatus.RINGING && doc.targetId == source) {
                    history.rejected(id, source)
                } else {
                    history.end(id, source)
                }
                activeCalls?.unregister(id)
            }
            "CONFERENCE_INVITE", "LIVE_INVITE" -> requireCallId(signal)
            else -> throw IllegalArgumentException("Unsupported call signal type")
        }

        val outbound = OutgoingCallSignal(callId, source, signal.targetUserId, type, signal.mode.uppercase(), signal.payload)
        val targets = liveSessions(signal.targetUserId)
        if (targets.isEmpty()) {
            enqueue(signal.targetUserId, outbound)
            if (type == "OFFER") {
                notifications.sendVoipPushNotification(signal.targetUserId, source, callId, signal.mode)
            }
            if (type in TERMINAL_TYPES) dropPending(callId)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "RINGING_PUSH_SENT", "callId" to callId))))
            return
        }

        val json = objectMapper.writeValueAsString(outbound)
        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to callId))))

        // Once one device answers/rejects/ends, stop the ringing state on the user's other devices.
        if (type in TERMINAL_TYPES) {
            dropPending(callId)
            val cancelType = if (type == "ANSWER") "CANCELLED" else type
            val cancel = objectMapper.writeValueAsString(mapOf("type" to cancelType, "callId" to callId, "sourceUserId" to source))
            targets.filter { it.id != session.id }.forEach { target -> runCatching { target.sendMessage(TextMessage(cancel)) } }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        val list = sessions.computeIfAbsent(redId) { CopyOnWriteArrayList() }
        list.removeIf { !it.isOpen }
        list.add(session)
        flushPending(redId, session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val redId = session.attributes["userId"] as? String
        if (redId == null) {
            sessions.values.forEach { it.removeIf { candidate -> candidate.id == session.id } }
        } else {
            sessions.computeIfPresent(redId) { _, list ->
                list.removeIf { it.id == session.id }
                list.takeIf { it.isNotEmpty() }
            }
        }
        // P0: تنظيف العضوية الجماعية عند انقطاع الاتصال
        if (redId != null) handleGroupCallDisconnect(redId)
    }

    /**
     * تنظيف العضوية الجماعية عند انقطاع الاتصال.
     *
     * الغرفة مخزنة بقيم غير قابلة للتغيير (GroupCallRoom مع List)، فالتحديث
     * يكون بنسخة جديدة عبر copy — لا تعديل مباشر. وroomId هو مفتاح الغرفة
     * (groupCallId) نفسه لا اشتقاق هش من الحقول.
     */
    private fun handleGroupCallDisconnect(redId: String) {
        groupRooms.forEach { (streamId, room) ->
            if (redId != room.host && redId !in room.members) return@forEach
            val remaining = room.members.filter { it != redId }.toMutableList()
            if (room.host == redId) {
                // المضيف انقطع: إنهاء للبقية وإسقاط الغرفة كاملة.
                groupRooms.remove(streamId)
                val endJson = objectMapper.writeValueAsString(
                    mapOf("type" to "GROUP_CALL_END", "roomId" to streamId, "payload" to mapOf("userId" to redId))
                )
                remaining.filter { it.isNotBlank() }.forEach { memberId ->
                    liveSessions(memberId).forEach { session -> session.sendSafe(TextMessage(endJson)) }
                }
            } else {
                // عضو عادي: تحديث الغرفة وإشعار البقية بالمغادرة.
                groupRooms[streamId] = room.copy(members = remaining)
                val leftJson = objectMapper.writeValueAsString(
                    mapOf("type" to "PARTICIPANT_LEFT", "roomId" to streamId, "userId" to redId)
                )
                (remaining + room.host).filter { it.isNotBlank() }.distinct().forEach { memberId ->
                    liveSessions(memberId).forEach { session -> session.sendSafe(TextMessage(leftJson)) }
                }
            }
        }
    }

    private fun liveSessions(redId: String) = sessions[redId]?.filter(WebSocketSession::isOpen).orEmpty()

    private fun enqueue(target: String, signal: OutgoingCallSignal) {
        val list = pending.computeIfAbsent(target) { CopyOnWriteArrayList() }
        list.removeIf { it.expiresAt.isBefore(Instant.now()) }
        // سقف صندوق البريد: 50 لكل مستخدم (إسقاط الأقدم) — بلا سقف كان الإغراق يفجر الذاكرة.
        while (list.size >= MAX_PENDING_PER_USER) {
            list.removeAt(0)
        }
        list.add(
            PendingCallSignal(
                json = objectMapper.writeValueAsString(signal),
                expiresAt = Instant.now().plusSeconds(PENDING_TTL_SECONDS),
                callId = signal.callId,
                type = signal.type
            )
        )
    }

    private fun flushPending(redId: String, session: WebSocketSession) {
        val list = pending.remove(redId) ?: return
        val now = Instant.now()
        list.filter { it.expiresAt.isAfter(now) }.forEach { item ->
            runCatching { session.sendMessage(TextMessage(item.json)) }
        }
    }

    private fun dropPending(callId: String) {
        pending.values.forEach { list -> list.removeIf { it.callId == callId } }
        pending.entries.removeIf { it.value.isEmpty() }
    }

    /** Deliver a conference/live invite to a RED ID: live socket if present, else 60s mailbox. */
    fun deliverInvite(targetRedId: String, type: String, roomId: String, sourceRedId: String, mode: String, payload: Map<String, Any?> = emptyMap()) {
        val outbound = OutgoingCallSignal(roomId, sourceRedId, targetRedId, type.uppercase(), mode.uppercase(), payload)
        val targets = liveSessions(targetRedId)
        if (targets.isEmpty()) {
            enqueue(targetRedId, outbound)
            notifications.sendVoipPushNotification(targetRedId, sourceRedId, roomId, mode)
            return
        }
        val json = objectMapper.writeValueAsString(outbound)
        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
    }

    /**
     * نسخة REST من تدفق GROUP_CALL_INVITE أعلاه (handleTextMessage س 34-53):
     * تسجّل الغرفة ثم ترنّ لكل مدعو عبر deliverInvite (سوكت حي أو صندوق 60s + push).
     */
    fun deliverGroupCallInvite(
        groupCallId: String,
        hostRedId: String,
        inviteeIds: List<String>,
        mode: String,
        payload: Map<String, Any?> = emptyMap()
    ) {
        val added = addGroupCallMembers(groupCallId, hostRedId, inviteeIds, mode, payload)
        require(added.isNotEmpty()) { "inviteeIds is required" }
    }

    /**
     * دمج أعضاء جدد في غرفة قائمة بدل استبدالها — كان كل استدعاء لـ
     * deliverGroupCallInvite يستبدل groupRooms كاملة فيسقط المضيف والأعضاء
     * الأصليين (دعوة invite-extra كانت تطرد الجميع ما عدا الأخير).
     * يرجع قائمة من أُضيف فعلاً (بعد إسقاط المكرر والحد 32).
     */
    fun addGroupCallMembers(
        groupCallId: String,
        hostRedId: String,
        extraIds: List<String>,
        mode: String,
        payload: Map<String, Any?> = emptyMap()
    ): List<String> {
        val effectiveId = resolveRoom(groupCallId)
        val existing = groupRooms[effectiveId] ?: groupRooms[groupCallId.trim()]
        val current = (existing?.members.orEmpty() + (existing?.host?.let { listOf(it) } ?: emptyList()))
            .filter { it.isNotBlank() }.distinct()
        val fresh = extraIds.filter { it.isNotBlank() && it != hostRedId && it !in current }
            .take((MAX_GROUP_CALL_MEMBERS - current.size).coerceAtLeast(0))
        if (existing == null) {
            require(fresh.isNotEmpty()) { "inviteeIds is required" }
            groupRooms[effectiveId] = GroupCallRoom(host = hostRedId, members = fresh.toMutableList())
        } else if (fresh.isNotEmpty()) {
            groupRooms[effectiveId] = existing.copy(members = (existing.members + fresh).distinct().toMutableList())
        }
        val enriched = payload + ("hostName" to (payload["hostName"] ?: ""))
        fresh.forEach { invitee ->
            deliverInvite(invitee, "GROUP_CALL_INVITE", effectiveId, hostRedId, mode, enriched)
        }
        return fresh
    }

    /** مضيف الغرفة الجماعية — للتحقق من صلاحية الدعوات الإضافية عبر REST. */
    fun groupCallHost(groupCallId: String): String? {
        val effective = resolveRoom(groupCallId)
        return groupRooms[effective]?.host ?: groupRooms[groupCallId.trim()]?.host
    }

    /** عدد مشاركي الغرفة الجماعية (مضيف + أعضاء) — لفرض الحد. */
    fun groupCallSize(groupCallId: String): Int {
        val effective = resolveRoom(groupCallId)
        val room = groupRooms[effective] ?: groupRooms[groupCallId.trim()] ?: return 0
        return ((room.members + room.host).filter { it.isNotBlank() }.distinct()).size
    }

    /** إشارة عامة (KICKED وغيرها): نفس بدائية deliverInvite بتوقيع مسمّى مريح. */
    fun deliverSignal(
        targetRedId: String,
        type: String,
        roomId: String,
        sourceRedId: String = "",
        mode: String = "SIGNAL",
        payload: Map<String, Any?> = emptyMap()
    ) = deliverInvite(targetRedId, type, roomId, sourceRedId, mode, payload)

    /**
     * تنظيف الغرف الجماعية العالقة: يزيل الغرف التي لا يملك مضيفها ولا أي عضو
     * جلسة حية. صندوق البريد المؤقت (pending) لا يُمس — يُسلَّم عند إعادة الاتصال،
     * ومسار ACCEPT يتحمل غياب الغرفة (hostId يسقط إلى source).
     */
    fun cleanupStaleGroups() {
        val stale = groupRooms.filter { (_, room) ->
            val participants = (room.members + room.host).filter { it.isNotBlank() }
            participants.none { liveSessions(it).isNotEmpty() }
        }.keys
        stale.forEach { groupRooms.remove(it) }
    }

    private fun requireCallId(signal: IncomingCallSignal) =
        requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }

    /** G13: حل alias الغرفة عبر RoomAliasService (Redis+ذاكرة) مع سقوط للخام. */
    private fun resolveRoom(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return v
        return runCatching { roomAliases?.resolve(v) ?: RoomSeparationPolicy.resolve(v) }.getOrNull()?.takeIf { it.isNotBlank() } ?: v
    }

    /** تحويل أنماط التطبيق (VOICE/VIDEO/…) إلى أنواع سجل المكالمات عند بدء OFFER. */
    private fun callTypeForMode(mode: String): CallType = when (mode.uppercase()) {
        "VIDEO" -> CallType.VIDEO_1V1
        "VOICE" -> CallType.AUDIO_1V1
        "GROUP" -> CallType.GROUP_AUDIO
        "GROUP_VIDEO" -> CallType.GROUP_VIDEO
        "LIVE" -> CallType.LIVE_STREAM
        "SPACE" -> CallType.SPACE
        else -> CallType.AUDIO_1V1
    }

    private fun WebSocketSession.sendSafe(message: TextMessage) {
        if (isOpen) {
            synchronized(this) {
                runCatching { sendMessage(message) }
            }
        }
    }

    companion object {
        private const val PENDING_TTL_SECONDS = 60L
        private const val MAX_PENDING_PER_USER = 50
        private val TERMINAL_TYPES = setOf("ANSWER", "REJECT", "END")
        /** حد واتساب للمكالمات الجماعية — يُفرض في WS وREST معاً. */
        const val MAX_GROUP_CALL_MEMBERS = 32
    }
}

private data class PendingCallSignal(
    val json: String,
    val expiresAt: Instant,
    val callId: String,
    val type: String
)

/** غرفة مكالمة جماعية — يوجّه السيرفر بها ردود الأعضاء إلى المضيف والإنهاء للجميع. */
private data class GroupCallRoom(
    val host: String,
    val members: MutableList<String> = mutableListOf(),
    val kicked: MutableSet<String> = mutableSetOf()
)

/**
 * تجاهل الحقول الزائدة من التطبيق (نفس علة IncomingConferenceSignal:
 * UnrecognizedPropertyException كانت تقتل جلسة /ws/calls).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IncomingCallSignal(
    val callId: String? = null,
    val targetUserId: String = "",
    val type: String = "",
    val mode: String = "VOICE",
    val inviteeIds: List<String> = emptyList(),
    val memberStatus: String? = null,
    val payload: Map<String, Any?> = emptyMap()
)

data class OutgoingCallSignal(
    val callId: String,
    val sourceUserId: String,
    val targetUserId: String,
    val type: String,
    val mode: String,
    val payload: Map<String, Any?>
)
