package com.red.server.websocket

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.RoomAliasService
import com.red.server.calls.RoomSeparationPolicy
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val roomAliases: RoomAliasService? = null
) : TextWebSocketHandler() {
    private val rooms = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionToRoom = ConcurrentHashMap<String, String>()
    private val roomRoles = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>() // roomId -> userId -> role
    private val roomHosts = ConcurrentHashMap<String, String>() // roomId -> host userId

    /**
     * غرفة الانتظار (Lobby — على غرار Zoom):
     * - [roomLobby]: roomId ← مفعّلة؟
     * - [roomWaiting]: roomId ← جلسات المحتجزين بانتظار موافقة المضيف.
     * المحتجز ليس داخل [rooms] فلا يصله أي بثّ وسائط/حالات، ولا يستقبل إلا
     * LOBBY_* وإلا لبقي صندوقاً أسود. أول منضّم (المضيف) والعودة بعد انقطاع
     * (لها دور معروف) تتجاوزان الانتظار.
     */
    private val roomLobby = ConcurrentHashMap<String, Boolean>()
    private val roomWaiting = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

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

    /** LEGENDARY Phase 7: دور مشارك (للتذاكر الواعية بالدور) — الأدوار مفتاحها redId. */
    fun getRole(roomId: String, vararg ids: String): String {
        val effective = resolveRoom(roomId)
        val roles = roomRoles[effective] ?: roomRoles[roomId.trim()] ?: return "LISTENER"
        for (id in ids) { val r = roles[id]; if (r != null) return r }
        return "LISTENER"
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val incoming = objectMapper.readValue(message.payload, IncomingConferenceSignal::class.java)
        // G13: حل alias عبر RoomAliasService (Redis+ذاكرة) مع سقوط للخام.
        val resolvedRoomId = resolveRoom(incoming.roomId)
        val signal = if (resolvedRoomId == incoming.roomId) incoming else incoming.copy(roomId = resolvedRoomId)
        // تحقّق غير قاتل: كان require يرمي فيُغلق سوكت المؤتمر بالكامل عند أول
        // إطار شاذ؛ نُبلّغ المرسِل ونُبقي الجلسة (بقية الغرفة لا تتضرر).
        if (signal.roomId.isBlank() || !signal.roomId.matches(ROOM_ID)) {
            sendError(session, signal.roomId, "INVALID_ROOM_ID", "roomId is required and must match ${ROOM_ID.pattern}")
            return
        }

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
            // مسح كل الأيدي (المضيف/المضيف المشارك) — كان عميل الأندرويد يرسل هذا
            // النوع بينما الخادم لا يعرفه فيرمي ويُسقط الجلسة. يُحفَظ كحالة ويُبَثّ.
            "CLEAR_ALL_HANDS" -> {
                val role = roomRoles[signal.roomId]?.get(userId) ?: "LISTENER"
                if (role != "HOST" && role != "CO_HOST") {
                    sendError(session, signal.roomId, "FORBIDDEN", "Only host/co-host can clear raised hands")
                } else {
                    roomHands[signal.roomId]?.clear()
                    relayIncludingSender(signal, userId)
                }
            }
            "APPROVE_SPEAKER", "DEMOTE_LISTENER", "GRANT_COHOST", "REVOKE_COHOST",
            "KICK_USER", "MUTE_USER", "MUTE_ALL", "PIN_MESSAGE" -> handleStageManagement(session, userId, signal)
            // غرفة الانتظار: تفعيل/إيقاف وقبول/رفض — للمضيف والمضيف المشارك فقط (فحص الدور داخل handleLobby).
            "LOBBY_SET", "LOBBY_APPROVE", "LOBBY_DENY", "LOBBY_APPROVE_ALL" -> handleLobby(session, userId, signal)
            // مشاركة الشاشة إعلان عابر (تسمية البلاطة) — يقتصر على أصحاب المنصة كالنشر.
            "SCREEN_SHARE_START", "SCREEN_SHARE_STOP" -> {
                val role = roomRoles[signal.roomId]?.get(userId) ?: "LISTENER"
                if (role in PUBLISHERS) relayIncludingSender(signal, userId) else sendError(
                    session, signal.roomId, "NOT_ON_STAGE",
                    "Only host, co-host or speaker may share screen"
                )
            }
            "LEAVE" -> handleLeave(session, signal)
            else -> {
                // صلابتها: نوع غير مدعوم يُسجَّل ويُبلَّغ المرسِل فقط — لا رمي يُغلق
                // الجلسة (كان أي إشارة أحدث من الخادم تُسقط سوكت المؤتمر كاملاً).
                sendError(session, signal.roomId, "UNSUPPORTED_TYPE", "Unsupported conference signal type: ${signal.type}")
            }
        }
    }

    private fun handleJoin(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val room = rooms.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
        // حدّ السعة مُنفذ (كان يقبل عدداً غير محدود فيخنق الـ mesh): 100 مشارك كحد X العملي.
        if (room.size >= MAX_PARTICIPANTS && room.none { (it.attributes["userId"] as? String) == userId }) {
            sendError(session, signal.roomId, "ROOM_FULL", "Room is full (max $MAX_PARTICIPANTS)")
            return
        }
        // غرفة الانتظار (Lobby): مفعّلة + الغرفة قائمة (لها مضيف) + المنضمّ ليس مشاركاً
        // معروفاً → يُحتجَز بانتظار موافقة المضيف بدل الدخول. أول منضّم (يصبح المضيف)
        // والعودة بعد انقطاع (لها دور محفوظ) تتجاوزان الانتظار دائماً.
        val existingRoles = roomRoles[signal.roomId]
        if (roomLobby[signal.roomId] == true && roomHosts[signal.roomId] != null &&
            existingRoles?.containsKey(userId) != true
        ) {
            val waiting = roomWaiting.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
            if (waiting.size >= MAX_PARTICIPANTS) {
                sendError(session, signal.roomId, "ROOM_FULL", "Waiting room is full (max $MAX_PARTICIPANTS)")
                return
            }
            synchronized(waiting) { waiting.add(session) }
            sessionToRoom[session.id] = signal.roomId
            runCatching {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "LOBBY_WAITING",
                    "roomId" to signal.roomId,
                    "payload" to mapOf("userId" to userId)
                ))))
            }
            notifyHostsOfWaiting(signal.roomId, userId)
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

        // Send room state to newcomer — includes roles (+ lobby state/waiting list)
        session.sendMessage(TextMessage(buildRoomState(signal.roomId, room, session, userId)))
    }

    /**
     * بناء إطار ROOM_STATE الكامل لجلسة منضمّة — مستخرج ليستخدمه الانضمام
     * القياسي وقبول غرفة الانتظار معاً (مسار واحد = لا تباين حالات).
     * يتضمن: الأدوار/الصوت/الصورة/الأيدي/الكتم + حالة اللوبي وقائمة المنتظرين.
     */
    private fun buildRoomState(
        roomId: String,
        room: MutableSet<WebSocketSession>,
        session: WebSocketSession,
        userId: String
    ): TextMessage {
        val roles = roomRoles[roomId] ?: ConcurrentHashMap()
        val peers = room.filter { it.id != session.id }.mapNotNull { it.attributes["userId"] as? String }
        val statePayload = mutableMapOf<String, String>()
        peers.forEachIndexed { i, p -> statePayload["user_$i"] = p }
        val hands = roomHands[roomId] ?: emptySet<String>()
        val muted = roomMuted[roomId] ?: emptySet<String>()
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
        statePayload["host"] = roomHosts[roomId] ?: userId
        statePayload["self_role"] = roles[userId] ?: "LISTENER"
        statePayload["lobby"] = (roomLobby[roomId] == true).toString()
        val waiting = waitingIds(roomId)
        statePayload["waiting_count"] = waiting.size.toString()
        waiting.forEachIndexed { i, w -> statePayload["waiting_user_$i"] = w }
        return TextMessage(objectMapper.writeValueAsString(mapOf(
            "type" to "ROOM_STATE",
            "roomId" to roomId,
            "payload" to statePayload
        )))
    }

    /** هويات المنتظرين في لوبي غرفة — مرتبة لثبات القائمة بين الواجهات. */
    private fun waitingIds(roomId: String): List<String> =
        roomWaiting[roomId]?.mapNotNull { it.attributes["userId"] as? String }?.sorted().orEmpty()

    // ─────────────────────── غرفة الانتظار (Lobby) ───────────────────────

    private fun handleLobby(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val roomId = signal.roomId
        val role = roomRoles[roomId]?.get(userId) ?: "LISTENER"
        if (role != "HOST" && role != "CO_HOST") {
            sendError(session, roomId, "FORBIDDEN", "Only host/co-host can manage the lobby")
            return
        }
        when (signal.type.uppercase()) {
            "LOBBY_SET" -> {
                val enabled = signal.payload["enabled"] == "true"
                roomLobby[roomId] = enabled
                rooms[roomId]?.filter { it.isOpen }?.forEach {
                    runCatching {
                        it.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                            "type" to "LOBBY_STATE",
                            "roomId" to roomId,
                            "payload" to mapOf("enabled" to enabled.toString())
                        ))))
                    }
                }
                // إيقاف اللوبي يقبل كل المنتظرين — لا يُتركون بلا إجابة إلى الأبد.
                if (!enabled) admitAll(roomId)
            }
            "LOBBY_APPROVE" -> signal.payload["targetUserId"]?.takeIf { it.isNotBlank() }?.let { admit(roomId, it) }
            "LOBBY_APPROVE_ALL" -> admitAll(roomId)
            "LOBBY_DENY" -> signal.payload["targetUserId"]?.takeIf { it.isNotBlank() }?.let { deny(roomId, it) }
        }
    }

    /** إدخال منتظر واحد إلى الغرفة — نفس مسار JOIN القياسي بعد البوابة. */
    private fun admit(roomId: String, targetUserId: String) {
        val waiting = roomWaiting[roomId] ?: return
        val target = waiting.firstOrNull { (it.attributes["userId"] as? String) == targetUserId }
        synchronized(waiting) { if (target != null) waiting.remove(target) }
        // إشعار مباشر للمقبول (هو خارج الغرفة فلا يصل بثّها) + إعلان للغرفة لتنظيف قائمة الواجهة.
        if (target != null && target.isOpen) {
            runCatching {
                target.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "LOBBY_APPROVED",
                    "roomId" to roomId,
                    "payload" to mapOf("targetUserId" to targetUserId)
                ))))
            }
        }
        announceLobbyDecision(roomId, "LOBBY_APPROVED", targetUserId)
        if (target == null || !target.isOpen) return
        val room = rooms.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
        val roles = roomRoles.computeIfAbsent(roomId) { ConcurrentHashMap() }
        synchronized(room) { room.add(target) }
        roles.putIfAbsent(targetUserId, "LISTENER")
        // حالة الغرفة كاملة للمقبول (يعرف الأدوار والأيدي والمكتومين من الآن)
        runCatching { target.sendMessage(buildRoomState(roomId, room, target, targetUserId)) }
        // PARTICIPANT_JOINED للبقية — نفس شكل handleJoin حرفياً
        val joinMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_JOINED",
            "roomId" to roomId,
            "payload" to mapOf(
                "userId" to targetUserId,
                "role" to (roles[targetUserId] ?: "LISTENER"),
                "isHost" to "false",
                "hasVideo" to "false",
                "hasAudio" to "true"
            )
        ))
        room.filter { it.id != target.id }.forEach { runCatching { it.sendMessage(TextMessage(joinMsg)) } }
    }

    private fun admitAll(roomId: String) {
        waitingIds(roomId).forEach { admit(roomId, it) }
    }

    /** رفض منتظر: إشعار مباشر ثم إغلاق جلسته (ليس في الغرفة فلا PARTICIPANT_LEFT). */
    private fun deny(roomId: String, targetUserId: String) {
        val waiting = roomWaiting[roomId] ?: return
        val target = waiting.firstOrNull { (it.attributes["userId"] as? String) == targetUserId }
        synchronized(waiting) { if (target != null) waiting.remove(target) }
        announceLobbyDecision(roomId, "LOBBY_DENIED", targetUserId)
        if (target != null) {
            runCatching {
                target.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf(
                    "type" to "LOBBY_DENIED",
                    "roomId" to roomId,
                    "payload" to mapOf("targetUserId" to targetUserId)
                ))))
            }
            runCatching { target.close(org.springframework.web.socket.CloseStatus.NORMAL) }
        }
    }

    /** بثّ قرار لوبي إلى الغرفة — تنظّف به واجهات المضيفين قائمة المنتظرين. */
    private fun announceLobbyDecision(roomId: String, type: String, targetUserId: String) {
        val room = rooms[roomId] ?: return
        val msg = objectMapper.writeValueAsString(mapOf(
            "type" to type,
            "roomId" to roomId,
            "payload" to mapOf("targetUserId" to targetUserId)
        ))
        room.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(msg)) } }
    }

    /** بلّغ مضيفي الغرفة (وألاحق المضيف لاحقاً عبر ROOM_STATE) بمنتظر جديد. */
    private fun notifyHostsOfWaiting(roomId: String, waitingUserId: String) {
        val room = rooms[roomId] ?: return
        val roles = roomRoles[roomId] ?: return
        val msg = objectMapper.writeValueAsString(mapOf(
            "type" to "LOBBY_REQUEST",
            "roomId" to roomId,
            "payload" to mapOf("userId" to waitingUserId)
        ))
        room.filter { s ->
            val role = (s.attributes["userId"] as? String)?.let { roles[it] }
            s.isOpen && (role == "HOST" || role == "CO_HOST")
        }.forEach { runCatching { it.sendMessage(TextMessage(msg)) } }
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
        if (type in setOf("APPROVE_SPEAKER","DEMOTE_LISTENER","GRANT_COHOST","REVOKE_COHOST","KICK_USER","MUTE_USER","MUTE_ALL","PIN_MESSAGE")) {
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
                // دعم الفكّ أيضاً: payload[\"muted\"] == false يفكّ الكتم (كان الكتم
                // أحادياً فيبقى المستخدم مكتوماً في حالة الغرفة بلا طريق للعودة).
                "MUTE_USER" -> {
                    val muted = signal.payload["muted"]?.toString()?.equals("false", ignoreCase = true) != true
                    val set = roomMuted.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
                    if (muted) set.add(targetId) else set.remove(targetId)
                }
                // MUTE_ALL بلا target — تُطبَّق بعد كتلة targetId (انظر أدناه).
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

        // LEGENDARY Phase 7: كتم الكل — كل الأدوار ما عدا المرسل (المضيف يكتم نفسه زرّه الخاص).
        if (type == "MUTE_ALL") {
            val muted = roomMuted.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
            roles.keys.forEach { if (it != userId) muted.add(it) }
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
        // مغادرٌ من لوبي الانتظار؟ نظّف قائمته دون بثّ PARTICIPANT_LEFT
        // (لم يكن داخل الغرفة أصلاً) وأبلغ المضيفين لإسقاط سطره من الواجهة.
        roomWaiting[signal.roomId]?.let { waiting ->
            val wasWaiting = synchronized(waiting) { waiting.remove(session) }
            if (wasWaiting) {
                val uid = session.attributes["userId"] as? String
                if (uid != null) announceLobbyDecision(signal.roomId, "LOBBY_LEFT", uid)
                sessionToRoom.remove(session.id)
                return
            }
        }
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
            roomLobby.remove(signal.roomId)
            roomWaiting.remove(signal.roomId)
            evictReactionKeys(signal.roomId)
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
                // تعميم انتخاب المضيف الجديد (كان في مسار LEAVE وحده) — وإلا فانقطاع
                // سوكت المضيف يترك غرفةً بلا مضيف معروف لدى بقية العملاء أبدياً.
                val remainingSessions = room.toList()
                val hostMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "HOST_CHANGED",
                    "roomId" to roomId,
                    "payload" to mapOf("userId" to remaining)
                ))
                remainingSessions.forEach { runCatching { it.sendMessage(TextMessage(hostMsg)) } }
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
            roomLobby.remove(roomId)
            roomWaiting.remove(roomId)
            evictReactionKeys(roomId)
        }
    }

    /** تنظيف مفاتيح حدّ التفاعلات عند موت الغرفة — وإلا تراكمت بلا حد في الذاكرة. */
    private fun evictReactionKeys(roomId: String) {
        val prefix = "$roomId:"
        lastReactionAt.keys.removeIf { it.startsWith(prefix) }
    }

    /** G13: حل alias عبر RoomAliasService (Redis+ذاكرة) مع سقوط للخام. */
    private fun resolveRoom(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return v
        return runCatching { roomAliases?.resolve(v) ?: RoomSeparationPolicy.resolve(v) }.getOrNull()?.takeIf { it.isNotBlank() } ?: v
    }

    companion object {
        /** موحّد مع SFU (4..128) ومع SfuTicketController — كان 8..128 فيفشل القصير. */
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")

        /** الأدوار المسموح لها بإرسال وسائط — ثابت لا حالة لكل نسخة. */
        private val PUBLISHERS = setOf("HOST", "CO_HOST", "SPEAKER")

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
