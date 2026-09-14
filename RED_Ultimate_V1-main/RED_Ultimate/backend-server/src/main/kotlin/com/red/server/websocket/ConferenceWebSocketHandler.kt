package com.red.server.websocket

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
class ConferenceWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val conferenceRooms: com.red.server.calls.ConferenceRoomService
) : TextWebSocketHandler() {
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

    /** حدّ التفاعلات: آخر إرسال لكل مستخدم (منع الإغراق — مستمع كان يرسل بلا حد). */
    private val lastReactionAt = ConcurrentHashMap<String, Long>()

    /** جلسات المنتظرين: accountId → session، لتبليغهم بالقبول فور occurrence. */
    private val lobbySessions = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()

    /** هل لهذا المستخدم سلطة إدارة الطابور في هذه الغرفة؟ */
    private fun isPrivileged(accountId: String, redId: String, roomId: String): Boolean {
        if (roomHosts[roomId] == redId || roomHosts[roomId] == accountId) return true
        val roles = roomRoles[roomId] ?: return false
        return roles[redId] in PRIVILEGED || roles[accountId] in PRIVILEGED
    }

    /** بلّغ المضيفين بعدد الطابور — واجهة المضيف تعرض «في الانتظار N» بلا استطلاع دوري. */
    private fun broadcastLobbyCount(roomId: String) {
        val room = rooms[roomId] ?: return
        val count = conferenceRooms.lobbyCount(roomId)
        val msg = objectMapper.writeValueAsString(mapOf(
            "type" to "LOBBY",
            "roomId" to roomId,
            "payload" to mapOf("state" to "queue", "waiting" to count.toString())
        ))
        room.filter { it.isOpen && isPrivileged(it.attributes["userId"] as? String ?: "", it.attributes["userId"] as? String ?: "", roomId) }
            .forEach { runCatching { it.sendMessage(TextMessage(msg)) } }
    }

    /** نداء REST: الطابور تغيّر من خارج سوكت الغرفة (رابط انضمام/لوحة إدارة). */
    fun notifyLobbyWaiting(roomId: String) = broadcastLobbyCount(roomId)

    /** بلّغ المقبولين بالانضمام الآن — العميل يعيد إرسال JOIN فيجد البوابة مفتوحة. */
    fun notifyLobbyAdmitted(roomId: String, accountIds: Collection<String>) {
        val waiting = lobbySessions[roomId] ?: return
        accountIds.forEach { accountId ->
            val session = waiting.remove(accountId) ?: return@forEach
            if (!session.isOpen) return@forEach
            runCatching {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "LOBBY",
                    "roomId" to roomId,
                    "payload" to mapOf("state" to "admitted")
                ))))
            }
        }
        broadcastLobbyCount(roomId)
    }

    /** الممنوع/المطرود من الطابور يُغلق سوكتُه: تركه مفتوحًا يترك واجنته معلّقة للأبد. */
    fun notifyLobbyDenied(roomId: String, accountIds: Collection<String>) {
        val waiting = lobbySessions[roomId] ?: return
        accountIds.forEach { accountId ->
            val session = waiting.remove(accountId) ?: return@forEach
            runCatching {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                        "type" to "LOBBY",
                        "roomId" to roomId,
                        "payload" to mapOf("state" to "denied")
                    ))))
                    session.close()
                }
            }
        }
        broadcastLobbyCount(roomId)
    }

    private fun sendLobbyState(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        if (!isPrivileged(session.attributes["accountId"] as? String ?: userId, userId, signal.roomId)) {
            sendError(session, signal.roomId, "FORBIDDEN", "Only host or co-host may read the lobby")
            return
        }
        val payload = conferenceRooms.lobbyQueue(signal.roomId).map {
            mapOf(
                "accountId" to it.accountId,
                "redId" to it.redId,
                "displayName" to it.displayName,
                "viaLink" to it.viaLink.toString(),
                "waitingSeconds" to java.time.Duration.between(it.requestedAt, java.time.Instant.now()).seconds.toString()
            )
        }
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
            "type" to "LOBBY",
            "roomId" to signal.roomId,
            "payload" to mapOf("state" to "list", "waiting" to payload.size.toString(), "entries" to payload)
        ))))
    }

    private fun handleLobbyAction(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val accountId = session.attributes["accountId"] as? String ?: userId
        if (!isPrivileged(accountId, userId, signal.roomId)) {
            sendError(session, signal.roomId, "FORBIDDEN", "Only host or co-host may manage the lobby")
            return
        }
        val targets = (signal.payload["accountIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val type = signal.type.uppercase()
        if (type == "LOBBY_ADMIT") {
            val admitted = if (targets.isEmpty()) {
                conferenceRooms.admitFromLobby(signal.roomId, conferenceRooms.lobbyQueue(signal.roomId).map { it.accountId })
            } else {
                conferenceRooms.admitFromLobby(signal.roomId, targets)
            }
            notifyLobbyAdmitted(signal.roomId, admitted)
        } else {
            val blocked = signal.payload["block"]?.toString() == "true"
            val denied = conferenceRooms.denyFromLobby(signal.roomId, targets, block = blocked)
            notifyLobbyDenied(signal.roomId, denied)
        }
    }

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
                // شاشةٌ في غرفة أُغلقت فيها المشاركة: رفضٌ صريح، لا إخفاءٌ في الواجهة فقط.
                val sharingScreen = signal.payload["kind"]?.toString() == "screen" ||
                    signal.payload["source"]?.toString() == "screen"
                if (sharingScreen && conferenceRooms.getRoom(signal.roomId)?.allowScreenShare == false) {
                    sendError(session, signal.roomId, "SCREEN_SHARE_DISABLED", "Host disabled screen sharing")
                } else if (role in PUBLISHERS) relay(session, signal) else sendError(
                    session, signal.roomId, "NOT_ON_STAGE",
                    "Only host, co-host or speaker may publish media"
                )
            }
            // التفاعل عابر فلا يُحفَظ، أما رفع اليد فحالة قائمة حتى
            // يبتّ فيها المضيف — تُحفَظ ليراها من ينضم لاحقًا.
            // حدّ الإغراق: تفاعل واحد كل 800ms لكل مستخدم (X Spaces يجمّع الإيموجي).
            "REACTION" -> {
                val now = System.currentTimeMillis()
                val key = "${signal.roomId}:$userId"
                val last = lastReactionAt[key] ?: 0L
                if (now - last < 800) {
                    sendError(session, signal.roomId, "RATE_LIMITED", "Slow down reactions")
                } else {
                    lastReactionAt[key] = now
                    relay(session, signal)
                }
            }
            "RAISE_HAND" -> {
                val lowered = signal.payload["lowered"]?.toString() == "true"
                val hands = roomHands.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
                if (lowered) hands.remove(userId) else hands.add(userId)
                relayIncludingSender(signal, userId)
            }
            "APPROVE_SPEAKER", "DEMOTE_LISTENER", "GRANT_COHOST", "REVOKE_COHOST",
            "KICK_USER", "MUTE_USER", "PIN_MESSAGE" -> handleStageManagement(session, userId, signal)
            // الطابور حالةٌ على الخادم لا محادثة جانبية: المضيف يقرأ قائمة، يُدخل، أو يمنع.
            "LOBBY_LIST" -> sendLobbyState(session, userId, signal)
            "LOBBY_ADMIT", "LOBBY_DENY" -> handleLobbyAction(session, userId, signal)
            "LEAVE" -> handleLeave(session, signal)
            else -> throw IllegalArgumentException("Unsupported conference signal type: ${signal.type}")
        }
    }

    private fun handleJoin(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val accountId = session.attributes["accountId"] as? String ?: userId
        // بوابة غرفة الانتظار قبل أي مقعد: من في الطابور لا يُحسب مشاركًا ولا تُقبل وسائطه،
        // وإلا صار الطابور ستارًا تجميليًا يستهلك سعة الغرفة ويغذي الـ SFU.
        if (conferenceRooms.isLobbyEnabled(signal.roomId) && !isPrivileged(accountId, userId, signal.roomId)) {
            val entry = conferenceRooms.enterLobby(
                roomId = signal.roomId,
                accountId = accountId,
                redId = userId,
                displayName = (signal.payload["displayName"] ?: "").toString(),
                viaLink = signal.payload["viaLink"]?.toString() == "true"
            )
            if (entry != null) {
                lobbySessions.computeIfAbsent(signal.roomId) { ConcurrentHashMap() }[accountId] = session
                sessionToRoom[session.id] = signal.roomId
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "LOBBY",
                    "roomId" to signal.roomId,
                    "payload" to mapOf(
                        "state" to "waiting",
                        "position" to conferenceRooms.lobbyQueue(signal.roomId).indexOfFirst { it.accountId == accountId }.let { if (it < 0) 1 else it + 1 }.toString(),
                        "waiting" to conferenceRooms.lobbyCount(signal.roomId).toString()
                    )
                ))))
                broadcastLobbyCount(signal.roomId)
                return
            }
            // مُذَّن سابقًا (إعادة اتصال) أو مدعو: يُسمح به فورًا ويُخرج من الطابور إن كان فيه.
            lobbySessions[signal.roomId]?.remove(accountId)
            conferenceRooms.leaveLobby(signal.roomId, accountId)
        }
        val room = rooms.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
        // حدّ السعة مُنفذ (كان يقبل عدداً غير محدود فيخنق الـ mesh): 100 مشارك كحد X العملي.
        if (room.size >= MAX_PARTICIPANTS && room.none { (it.attributes["userId"] as? String) == userId }) {
            sendError(session, signal.roomId, "ROOM_FULL", "Room is full (max $MAX_PARTICIPANTS)")
            return
        }
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
                    val speakers = roles.count { it.value == "SPEAKER" || it.value == "CO_HOST" }
                    if (speakers >= MAX_SPEAKERS) {
                        sendError(session, roomId, "STAGE_FULL", "Stage is full (max $MAX_SPEAKERS speakers)")
                        return
                    }
                    roles[targetId] = "SPEAKER"
                    roomHands[roomId]?.remove(targetId)
                }
                "DEMOTE_LISTENER" -> {
                    roles[targetId] = "LISTENER"
                    roomHands[roomId]?.remove(targetId)
                }
                "MUTE_USER" -> roomMuted.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(targetId)
                "GRANT_COHOST" -> {
                    // حدّ X: مضيفان مشاركان فقط (كان بلا حد فيصعّد الامتياز بلا نهاية).
                    val cohosts = roles.count { it.value == "CO_HOST" }
                    if (cohosts >= MAX_COHOSTS) {
                        sendError(session, roomId, "TOO_MANY_COHOSTS", "Max $MAX_COHOSTS co-hosts")
                        return
                    }
                    roles[targetId] = "CO_HOST"
                }
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
        // الحضور والطابور مفاتيحهما accountId (هكذا تكتبهما REST)، فحذفهما بـ redId صامتٌ
        // ولا يحرّر شيئًا: يبقى المقعد محجوزًا حتى تمتلئ الغرفة ولا يدخل أحد بعدها.
        val accountId = session.attributes["accountId"] as? String ?: userId
        conferenceRooms.removeParticipant(signal.roomId, accountId)
        lobbySessions[signal.roomId]?.remove(accountId)
        conferenceRooms.leaveLobby(signal.roomId, accountId)
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
            lobbySessions.remove(signal.roomId)
            evictReactionKeys(signal.roomId)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        val roomId = sessionToRoom.remove(session.id) ?: return
        val room = rooms[roomId] ?: return
        synchronized(room) { room.remove(session) }
        val userId = session.attributes["userId"] as? String ?: return
        val accountId = session.attributes["accountId"] as? String ?: userId
        roomHands[roomId]?.remove(userId)
        roomMuted[roomId]?.remove(userId)
        conferenceRooms.removeParticipant(roomId, accountId)
        lobbySessions[roomId]?.remove(accountId)
        conferenceRooms.leaveLobby(roomId, accountId)
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
            evictReactionKeys(roomId)
        }
    }

    /** تنظيف مفاتيح حدّ التفاعلات عند موت الغرفة — وإلا تراكمت بلا حد في الذاكرة. */
    private fun evictReactionKeys(roomId: String) {
        val prefix = "$roomId:"
        lastReactionAt.keys.removeIf { it.startsWith(prefix) }
    }

    companion object {
        /** موحّد مع SFU (4..128) ومع SfuTicketController — كان 8..128 فيفشل القصير. */
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")

        /** الأدوار المسموح لها بإرسال وسائط — ثابت لا حالة لكل نسخة. */
        private val PUBLISHERS = setOf("HOST", "CO_HOST", "SPEAKER")

        /** من يدير الطابور: المضيف وشريكه فقط — لا المتحدث على المنصة. */
        private val PRIVILEGED = setOf("HOST", "CO_HOST")

        /** حدود X Spaces العملية: 100 مشارك، 20 متحدثاً، مضيفان مشاركان. */
        const val MAX_PARTICIPANTS = 100
        const val MAX_SPEAKERS = 20
        const val MAX_COHOSTS = 2
    }
}

/**
 * إشارات التطبيق تتطور أسرع من العقد (التطبيق يرسل userId/deviceId زائدة) —
 * تجاهل المجهول بدل قتل الجلسة (كان UnrecognizedPropertyException يغلق
 * livestream/conference فور اتصال النسخ الحديثة).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IncomingConferenceSignal(
    val type: String,
    val roomId: String = "",
    val payload: Map<String, Any?> = emptyMap()
)
