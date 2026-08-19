package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC mesh-style signaling + stage management for conferences & audio spaces.
 * Supports: JOIN, OFFER, ANSWER, ICE, PRODUCE, CONSUME, LEAVE,
 * plus stage control: RAISE_HAND, APPROVE_SPEAKER, DEMOTE_LISTENER,
 * GRANT_COHOST, REVOKE_COHOST, KICK_USER, MUTE_USER, REACTION, PIN_MESSAGE.
 *
 * Roles: HOST (first joiner), CO_HOST, SPEAKER, LISTENER.
 * Permission: only HOST or CO_HOST can manage stage (approve/demote/grant/kick/mute/pin).
 * Any participant can RAISE_HAND and REACTION.
 * For larger conferences (>4) the protocol proxies to media-sfu; Android speaks same shape.
 */
@Component
class ConferenceWebSocketHandler(private val objectMapper: ObjectMapper) : TextWebSocketHandler() {
    private val rooms = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionToRoom = ConcurrentHashMap<String, String>()
    private val roomRoles = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>() // roomId -> userId -> role
    private val roomHosts = ConcurrentHashMap<String, String>() // roomId -> host userId

    /**
     * الأيدي المرفوعة: roomId ← مجموعة من رفعوا أيديهم.
     *
     * كان RAISE_HAND يُبَثّ ولا يُحفَظ، فمن ينضم بعد الرفع — والمضيف
     * نفسه إن تأخّر — لا يرى الطلب أبدًا، فيبقى صاحبه منتظرًا بلا ردّ.
     */
    private val roomHands = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * المكتومون إداريًّا: roomId ← مجموعة من كتمهم المضيف.
     *
     * كان MUTE_USER يُبَثّ ولا يُحفَظ، وROOM_STATE يعلن الجميع
     * `_audio=true` نصًّا ثابتًا، فيعود المكتوم مسموعًا في واجهة أي
     * منضمٍّ جديد.
     */
    private val roomMuted = ConcurrentHashMap<String, MutableSet<String>>()

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val signal = objectMapper.readValue(message.payload, IncomingConferenceSignal::class.java)
        require(signal.roomId.isNotBlank()) { "roomId is required" }
        require(signal.roomId.matches(ROOM_ID)) { "Invalid roomId" }

        when (signal.type.uppercase()) {
            "JOIN" -> handleJoin(session, userId, signal)
            // CONSUME وICO والتفاوض متاحة للجميع — المستمع يحتاجها ليسمع.
            "OFFER", "ANSWER", "ICE", "CONSUME" -> relay(session, signal)
            // أما PRODUCE فنشرٌ للوسائط: يقتصر على أصحاب المنصة. بدون
            // هذه البوابة كان بوسع أي مستمع أن ينشر صوتًا وصورة فيبطل
            // نظام المنصة كلّه، إذ يصير APPROVE_SPEAKER زينةً في الواجهة
            // لا قيدًا فعليًّا على الخادم.
            "PRODUCE" -> {
                val role = roomRoles[signal.roomId]?.get(userId) ?: "LISTENER"
                if (role in PUBLISHERS) relay(session, signal) else sendError(
                    session, signal.roomId, "NOT_ON_STAGE",
                    "Only host, co-host or speaker may publish media"
                )
            }
            // التفاعل عابر فلا يُحفَظ، أما رفع اليد فحالة قائمة حتى
            // يبتّ فيها المضيف — تُحفَظ ليراها من ينضم لاحقًا.
            "REACTION" -> relay(session, signal)
            "RAISE_HAND" -> {
                val lowered = signal.payload["lowered"]?.toString() == "true"
                val hands = roomHands.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
                if (lowered) hands.remove(userId) else hands.add(userId)
                relayIncludingSender(signal, userId)
            }
            "APPROVE_SPEAKER", "DEMOTE_LISTENER", "GRANT_COHOST", "REVOKE_COHOST",
            "KICK_USER", "MUTE_USER", "PIN_MESSAGE" -> handleStageManagement(session, userId, signal)
            "LEAVE" -> handleLeave(session, signal)
            else -> throw IllegalArgumentException("Unsupported conference signal type: ${signal.type}")
        }
    }

    private fun handleJoin(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val room = rooms.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
        val roles = roomRoles.computeIfAbsent(signal.roomId) { ConcurrentHashMap() }
        synchronized(room) { room.add(session) }
        sessionToRoom[session.id] = signal.roomId

        // First participant becomes HOST if no host yet
        if (roomHosts[signal.roomId] == null) {
            roomHosts[signal.roomId] = userId
            roles[userId] = "HOST"
        } else {
            roles.putIfAbsent(userId, "LISTENER")
        }
        val myRole = roles[userId] ?: "LISTENER"
        val isHost = roomHosts[signal.roomId] == userId

        // Notify existing peers
        val joinMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_JOINED",
            "roomId" to signal.roomId,
            "payload" to mapOf(
                "userId" to userId,
                "role" to myRole,
                "isHost" to isHost.toString(),
                "hasVideo" to (signal.payload["hasVideo"] ?: "false"),
                "hasAudio" to (signal.payload["hasAudio"] ?: "true")
            )
        ))
        room.filter { it.id != session.id }.forEach { runCatching { it.sendMessage(TextMessage(joinMsg)) } }

        // Send room state to newcomer — includes roles
        val peers = room.filter { it.id != session.id }.mapNotNull { it.attributes["userId"] as? String }
        val statePayload = mutableMapOf<String, String>()
        peers.forEachIndexed { i, p -> statePayload["user_$i"] = p }
        val hands = roomHands[signal.roomId] ?: emptySet<String>()
        val muted = roomMuted[signal.roomId] ?: emptySet<String>()
        peers.forEach { p ->
            val role = roles[p] ?: "LISTENER"
            // الحالة الحقيقية لا قيمة ثابتة: كان الصوت والصورة يُعلَنان
            // "true" للجميع دائمًا، فيظهر المستمع الصامت في واجهة
            // المنضمّ الجديد كأنه متحدّث، ويعود المكتوم مسموعًا.
            statePayload["${p}_audio"] = (role in PUBLISHERS && p !in muted).toString()
            statePayload["${p}_video"] = (role in PUBLISHERS).toString()
            statePayload["${p}_role"] = role
            statePayload["${p}_muted"] = (p in muted).toString()
            statePayload["${p}_hand"] = (p in hands).toString()
        }
        statePayload["host"] = roomHosts[signal.roomId] ?: userId
        statePayload["self_role"] = myRole
        val stateMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "ROOM_STATE",
            "roomId" to signal.roomId,
            "payload" to statePayload
        ))
        session.sendMessage(TextMessage(stateMsg))
    }

    private fun relay(session: WebSocketSession, signal: IncomingConferenceSignal) {
        val room = rooms[signal.roomId] ?: return
        val source = session.attributes["userId"] as? String ?: ""
        val targetId = signal.payload["targetUserId"]?.toString()?.takeIf { it.isNotBlank() }
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to signal.type.uppercase(),
            "roomId" to signal.roomId,
            "userId" to source,
            "payload" to signal.payload
        ))
        val recipients = if (targetId != null) {
            room.filter { it.isOpen && (it.attributes["userId"] as? String) == targetId }
        } else {
            room.filter { it.id != session.id && it.isOpen }
        }
        recipients.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
    }

    private fun handleStageManagement(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val roomId = signal.roomId
        val roles = roomRoles[roomId] ?: return
        val senderRole = roles[userId] ?: "LISTENER"
        val isPrivileged = senderRole == "HOST" || senderRole == "CO_HOST"
        val type = signal.type.uppercase()

        // Only HOST/CO_HOST can do management actions except RAISE_HAND/REACTION already routed
        if (type in setOf("APPROVE_SPEAKER","DEMOTE_LISTENER","GRANT_COHOST","REVOKE_COHOST","KICK_USER","MUTE_USER","PIN_MESSAGE")) {
            if (!isPrivileged) {
                // silently reject but inform sender
                val err = objectMapper.writeValueAsString(mapOf(
                    "type" to "ERROR",
                    "roomId" to roomId,
                    "payload" to mapOf("code" to "FORBIDDEN", "message" to "Only host/co-host can $type")
                ))
                runCatching { session.sendMessage(TextMessage(err)) }
                return
            }
        }

        // Apply role changes atomically
        val targetId = signal.payload["targetUserId"]?.toString()
        if (targetId != null) {
            when (type) {
                // الترقية تُسقط طلب الرفع: تُركت اليد مرفوعة بعد الموافقة
                // فكان الطلب يظل معلّقًا في قائمة المضيف بلا معنى.
                "APPROVE_SPEAKER" -> {
                    roles[targetId] = "SPEAKER"
                    roomHands[roomId]?.remove(targetId)
                }
                "DEMOTE_LISTENER" -> {
                    roles[targetId] = "LISTENER"
                    roomHands[roomId]?.remove(targetId)
                }
                "MUTE_USER" -> roomMuted.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(targetId)
                "GRANT_COHOST" -> roles[targetId] = "CO_HOST"
                "REVOKE_COHOST" -> {
                    if (roles[targetId] == "CO_HOST") roles[targetId] = "SPEAKER"
                }
                "KICK_USER" -> {
                    roles.remove(targetId)
                    roomHands[roomId]?.remove(targetId)
                    roomMuted[roomId]?.remove(targetId)
                }
            }
        }

        // Relay to whole room (including sender for UI sync), or exclude kicker for KICK
        val room = rooms[roomId] ?: return
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to type,
            "roomId" to roomId,
            "userId" to userId,
            "payload" to if (type == "KICK_USER" && targetId != null) {
                signal.payload + mapOf("kicked" to true)
            } else signal.payload
        ))
        val recipients = if (type == "KICK_USER" && targetId != null) {
            // inform everyone including kicked user so they can leave gracefully
            room.filter { it.isOpen }
        } else {
            room.filter { it.isOpen }
        }
        recipients.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }

        // If kick, remove session(s) of target
        if (type == "KICK_USER" && targetId != null) {
            room.filter { (it.attributes["userId"] as? String) == targetId }.forEach {
                runCatching { it.close() }
            }
        }
    }

    /** يبلّغ المرسِل وحده بخطأ دون إسقاط الجلسة. */
    private fun sendError(session: WebSocketSession, roomId: String, code: String, message: String) {
        val err = objectMapper.writeValueAsString(mapOf(
            "type" to "ERROR",
            "roomId" to roomId,
            "payload" to mapOf("code" to code, "message" to message)
        ))
        runCatching { session.sendMessage(TextMessage(err)) }
    }

    /**
     * بثّ يشمل المرسِل — تحتاجه الحالات المحفوظة (رفع اليد) ليتأكّد
     * صاحبها أن الخادم سجّل طلبه، فلا تتباين واجهته عن بقية الغرفة.
     */
    private fun relayIncludingSender(signal: IncomingConferenceSignal, userId: String) {
        val room = rooms[signal.roomId] ?: return
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to signal.type.uppercase(),
            "roomId" to signal.roomId,
            "userId" to userId,
            "payload" to signal.payload
        ))
        room.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
    }

    private fun handleLeave(session: WebSocketSession, signal: IncomingConferenceSignal) {
        val room = rooms[signal.roomId] ?: return
        synchronized(room) { room.remove(session) }
        sessionToRoom.remove(session.id)
        val userId = session.attributes["userId"] as? String ?: return
        // المغادر يخرج من كل الحالات القائمة، وإلا بقيت يده مرفوعة في
        // قائمة المضيف وبقي كتمه ساريًا لو عاد بجلسة جديدة.
        roomHands[signal.roomId]?.remove(userId)
        roomMuted[signal.roomId]?.remove(userId)
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to signal.roomId,
            "payload" to mapOf("userId" to userId)
        ))
        room.forEach { runCatching { it.sendMessage(TextMessage(leaveMsg)) } }
        // If host left, elect new host from remaining if any
        if (roomHosts[signal.roomId] == userId) {
            val remaining = room.firstOrNull()?.attributes?.get("userId") as? String
            if (remaining != null) {
                roomHosts[signal.roomId] = remaining
                roomRoles[signal.roomId]?.set(remaining, "HOST")
                // notify new host election
                val hostMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "HOST_CHANGED",
                    "roomId" to signal.roomId,
                    "payload" to mapOf("userId" to remaining)
                ))
                room.forEach { runCatching { it.sendMessage(TextMessage(hostMsg)) } }
            } else {
                roomRoles.remove(signal.roomId)
                roomHosts.remove(signal.roomId)
                roomHands.remove(signal.roomId)
                roomMuted.remove(signal.roomId)
            }
        }
        if (room.isEmpty()) {
            rooms.remove(signal.roomId)
            roomRoles.remove(signal.roomId)
            roomHosts.remove(signal.roomId)
            roomHands.remove(signal.roomId)
            roomMuted.remove(signal.roomId)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        val roomId = sessionToRoom.remove(session.id) ?: return
        val room = rooms[roomId] ?: return
        synchronized(room) { room.remove(session) }
        val userId = session.attributes["userId"] as? String ?: return
        roomHands[roomId]?.remove(userId)
        roomMuted[roomId]?.remove(userId)
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to roomId,
            "payload" to mapOf("userId" to userId)
        ))
        room.forEach { runCatching { it.sendMessage(TextMessage(leaveMsg)) } }
        if (roomHosts[roomId] == userId) {
            val remaining = room.firstOrNull()?.attributes?.get("userId") as? String
            if (remaining != null) {
                roomHosts[roomId] = remaining
                roomRoles[roomId]?.set(remaining, "HOST")
            } else {
                roomRoles.remove(roomId)
                roomHosts.remove(roomId)
                roomHands.remove(roomId)
                roomMuted.remove(roomId)
            }
        }
        if (room.isEmpty()) {
            rooms.remove(roomId)
            roomRoles.remove(roomId)
            roomHosts.remove(roomId)
            roomHands.remove(roomId)
            roomMuted.remove(roomId)
        }
    }

    companion object {
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")

        /** الأدوار المسموح لها بإرسال وسائط — ثابت لا حالة لكل نسخة. */
        private val PUBLISHERS = setOf("HOST", "CO_HOST", "SPEAKER")
    }
}

data class IncomingConferenceSignal(
    val type: String,
    val roomId: String = "",
    val payload: Map<String, Any?> = emptyMap()
)
