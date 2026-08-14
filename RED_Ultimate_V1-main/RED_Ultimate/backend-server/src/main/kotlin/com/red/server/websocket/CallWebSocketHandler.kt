package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.ActiveCallRegistry
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
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
    private val activeCalls: ActiveCallRegistry
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
                val groupCallId = requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }
                val invitees = signal.inviteeIds.filter { it.isNotBlank() && it != source }
                require(invitees.isNotEmpty()) { "inviteeIds is required" }
                // حدّد المشغولين أولاً (قبل التسجيل — وإلا يُعتبر كل مدعو مشغولاً بنفسه)
                val busyInvitees = invitees.filter { activeCalls.isInCall(it) }.toSet()
                groupRooms[groupCallId] = GroupCallRoom(host = source, members = invitees)
                // سجّل المضيف والمدعوين غير المشغولين كـ "في مكالمة" — لكشف BUSY ولمعالجة الجماعية
                activeCalls.register(groupCallId, listOf(source) + invitees.filterNot { it in busyInvitees })
                val payload = signal.payload + ("hostName" to (signal.payload["hostName"] ?: ""))
                invitees.forEach { invitee ->
                    // خط مشغول: العضو في مكالمة نشطة (1:1 أو جماعية) — أخبر المضيف فوراً بدل الرنين
                    if (invitee in busyInvitees) {
                        val busySignal = OutgoingCallSignal(groupCallId, invitee, source, "GROUP_CALL_STATUS", signal.mode.uppercase(), mapOf("memberStatus" to "busy"))
                        val hostTargets = liveSessions(source)
                        if (hostTargets.isEmpty()) enqueue(source, busySignal)
                        else hostTargets.forEach { target -> runCatching { target.sendMessage(TextMessage(objectMapper.writeValueAsString(busySignal))) } }
                        return@forEach
                    }
                    val outbound = OutgoingCallSignal(groupCallId, source, invitee, type, signal.mode.uppercase(), payload)
                    val targets = liveSessions(invitee)
                    if (targets.isEmpty()) {
                        enqueue(invitee, outbound)
                        notifications.sendVoipPushNotification(invitee, source, groupCallId, signal.mode)
                    } else {
                        val json = objectMapper.writeValueAsString(outbound)
                        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
                    }
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to groupCallId))))
                return
            }

            // إشارات المكالمة الجماعية: الردود من الأعضاء إلى المضيف، والإنهاء للجميع
            "GROUP_CALL_ACCEPT", "GROUP_CALL_DECLINE", "GROUP_CALL_STATUS" -> {
                val groupCallId = requireCallId(signal)
                val room = groupRooms[groupCallId]
                val hostId = room?.host ?: source
                // رفض/غادر/لم يرد → حرّر العضو ليصبح متاحاً لاستقبال المكالمات من جديد
                if (type == "GROUP_CALL_DECLINE" || signal.memberStatus == "no_answer" || signal.memberStatus == "left") {
                    val memberId = if (source == room?.host && signal.memberStatus == "no_answer") {
                        signal.payload["memberId"]?.toString()?.takeIf { it in room.members }
                    } else {
                        source
                    }
                    if (memberId != null) activeCalls.releaseMember(groupCallId, memberId)
                }
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
            "GROUP_CALL_END" -> {
                val groupCallId = requireCallId(signal)
                val room = groupRooms.remove(groupCallId)
                activeCalls.unregister(groupCallId)
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
        }

        require(signal.targetUserId.isNotBlank()) { "targetUserId is required" }
        require(signal.targetUserId != source) { "Cannot call the same RED ID" }
        val callId: String
        if (type == "OFFER") {
            // خط مشغول حقيقي: المُستدعى في مكالمة نشطة (1:1 أو جماعية) — لا يُرن أبداً
            if (activeCalls.isInCall(signal.targetUserId)) {
                val busyCallId = history.start(source, signal.targetUserId, signal.targetUserId,
                    callTypeForMode(signal.mode), CallRoute.RED, signal.callId).id
                runCatching { history.busy(busyCallId) }
                dropPending(busyCallId)
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "BUSY",
                    "callId" to busyCallId,
                    "sourceUserId" to signal.targetUserId
                ))))
                return
            }
            callId = history.start(source, signal.targetUserId, signal.targetUserId,
                callTypeForMode(signal.mode), CallRoute.RED, signal.callId).id
            // سجّل المكالمة كنشطة: لكشف BUSY للوارد لاحقاً + عداد لوحة الأدمن
            activeCalls.register(callId, listOf(source, signal.targetUserId))
        } else {
            callId = when (type) {
                "ANSWER" -> requireCallId(signal).also { history.answer(it, source); activeCalls.touch(it) }
                "END" -> requireCallId(signal).also { history.end(it, source); activeCalls.unregister(it) }
                "ICE", "HOLD", "RESUME", "RENEGOTIATE", "CALL_REACTION", "CALL_RAISE_HAND" -> requireCallId(signal).also { activeCalls.touch(it) }
                "REJECT" -> requireCallId(signal).also { history.rejected(it, source); activeCalls.unregister(it) }
                "CONFERENCE_INVITE", "LIVE_INVITE" -> requireCallId(signal)
                else -> throw IllegalArgumentException("Unsupported call signal type")
            }
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
    }

    private fun liveSessions(redId: String) = sessions[redId]?.filter(WebSocketSession::isOpen).orEmpty()

    private fun enqueue(target: String, signal: OutgoingCallSignal) {
        val list = pending.computeIfAbsent(target) { CopyOnWriteArrayList() }
        list.removeIf { it.expiresAt.isBefore(Instant.now()) }
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

    private fun requireCallId(signal: IncomingCallSignal) =
        requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }

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

    companion object {
        private const val PENDING_TTL_SECONDS = 60L
        private val TERMINAL_TYPES = setOf("ANSWER", "REJECT", "END")
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
    val members: List<String>
)

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
