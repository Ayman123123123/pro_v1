package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/livestream")
class LiveStreamController(
    private val liveStreamService: LiveStreamService,
    private val users: UserAccountRepository,
    private val notifications: NotificationService,
    private val history: CallHistoryService,
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler,
    private val liveSignaling: com.red.server.websocket.LiveStreamWebSocketHandler
) {

    @PostMapping("/create")
    fun create(
        @RequestBody request: CreateStreamRequest,
        authentication: Authentication
    ): ResponseEntity<LiveStreamResponse> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val streamId = request.streamId.trim().ifBlank { "stream_${UUID.randomUUID().toString().take(12)}" }
        require(streamId.matches(Regex("^[A-Za-z0-9_-]{8,128}$"))) { "INVALID_STREAM_ID" }
        val record = liveStreamService.createStream(
            streamId = streamId,
            broadcasterId = user.id.toString(),
            broadcasterName = user.displayName,
            broadcasterRedId = user.redId,
            title = request.title,
            isPrivate = request.isPrivate,
            password = request.password,
            category = request.category,
            description = request.description,
            broadcasterAvatar = runCatching { user.avatarUrl }.getOrNull()
        )
        val inviteLink = "younes://livestream/$streamId"
        return ResponseEntity.ok(LiveStreamResponse(
            streamId = record.streamId,
            title = record.title,
            broadcasterName = record.broadcasterName,
            broadcasterRedId = record.broadcasterRedId,
            isPrivate = record.isPrivate,
            viewerCount = 0,
            inviteLink = inviteLink,
            category = record.category,
            broadcasterAvatar = record.broadcasterAvatar
        ))
    }

    /**
     * قائمة البثوث العامة — مع بحث وترقيم صفحات اختياريين.
     * page الغائب/السالب = السلوك القديم (الكل دون ترتيب إضافي)؛ page>=0
     * يفعّل الترقيم مرتبًا بعدد المشاهدين. نفس نوع الإرجاع — بلا كسر للعملاء.
     */
    @GetMapping("/public")
    fun listPublic(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false, defaultValue = "-1") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) category: String?
    ): ResponseEntity<List<LiveStreamResponse>> {
        val streams = if (page < 0) liveStreamService.searchPublicStreams(query, category)
                      else liveStreamService.searchPublicStreams(query, page, size.coerceIn(1, 100), category)
        val responses = streams.map { record ->
            LiveStreamResponse(
                streamId = record.streamId,
                title = record.title,
                broadcasterName = record.broadcasterName,
                broadcasterRedId = record.broadcasterRedId,
                isPrivate = false,
                viewerCount = record.viewerCount,
                inviteLink = "younes://livestream/${record.streamId}",
                category = record.category,
                slowModeSec = record.slowModeSec,
                recordingEnabled = record.recordingEnabled,
                broadcasterAvatar = record.broadcasterAvatar
            )
        }
        return ResponseEntity.ok(responses)
    }

    /** قائمة الإعادات VOD — أساس مشغل الإعادات (فارغة حتى تفعيل تسجيل الخادم). */
    @GetMapping("/vods")
    fun listVods(
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): ResponseEntity<List<Map<String, Any?>>> {
        val vods = liveStreamService.getVodHistory(limit).map { r ->
            mapOf<String, Any?>(
                "streamId" to r.streamId,
                "title" to r.title,
                "broadcasterName" to r.broadcasterName,
                "category" to r.category,
                "startedAt" to r.startedAt.toString(),
                "endedAt" to r.endedAt?.toString(),
                "peakViewers" to r.peakViewers,
                "hlsUrl" to r.hlsUrl,
                "vodUrl" to r.vodUrl
            )
        }
        return ResponseEntity.ok(vods)
    }

    @PostMapping("/{streamId}/join")
    fun join(
        @PathVariable streamId: String,
        @RequestBody request: JoinStreamRequest,
        authentication: Authentication
    ): ResponseEntity<JoinStreamResponse> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val isAuth = liveStreamService.verifyPassword(streamId, request.password)
        if (!isAuth) {
            return ResponseEntity.status(403).body(JoinStreamResponse(
                authorized = false,
                streamId = streamId,
                errorMessage = "كلمة السر غير صحيحة"
            ))
        }
        // توحيد الهوية Legendary V2: liveViewers يخزن RedId (مثل WS userId)
        // بدل UUID — وإلا يفشل الطرد والعداد (UUID vs RedId).
        val accountId = runCatching { UUID.fromString(authentication.name) }.getOrNull()
        val redId = accountId?.let { runCatching { users.findById(it).orElse(null)?.redId }.getOrNull() }
            ?: authentication.name
        // المحظور لا ينضم
        if (liveStreamService.isBanned(streamId, redId)) {
            return ResponseEntity.status(403).body(JoinStreamResponse(
                authorized = false,
                streamId = streamId,
                errorMessage = "تم حظرك من هذا البث"
            ))
        }
        liveStreamService.addViewer(streamId, redId)
        return ResponseEntity.ok(JoinStreamResponse(
            authorized = true,
            streamId = record.streamId,
            title = record.title,
            isPrivate = record.isPrivate,
            broadcasterName = record.broadcasterName
        ))
    }

    @PostMapping("/{streamId}/leave")
    fun leave(
        @PathVariable streamId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val accountId = runCatching { UUID.fromString(authentication.name) }.getOrNull()
        val redId = accountId?.let { runCatching { users.findById(it).orElse(null)?.redId }.getOrNull() }
            ?: authentication.name
        liveStreamService.removeViewer(streamId, redId)
        // توافق مع الإدخالات القديمة المخزنة بـ UUID
        if (redId != authentication.name) liveStreamService.removeViewer(streamId, authentication.name)
        return ResponseEntity.ok(mapOf("streamId" to streamId, "viewerCount" to liveStreamService.getViewerCount(streamId)))
    }

    @PostMapping("/{streamId}/stop")
    fun stop(
        @PathVariable streamId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        require(record.broadcasterId == authentication.name) { "ONLY_BROADCASTER_CAN_STOP" }
        return ResponseEntity.ok(mapOf("streamId" to streamId, "stopped" to liveStreamService.stopStream(streamId)))
    }

    @PostMapping("/{streamId}/invite")
    fun inviteFriends(
        @PathVariable streamId: String,
        @RequestBody request: InviteFriendsRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val accountId = UUID.fromString(authentication.name)
        val inviter = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        // حد أمان: 32 دعوة كحد أقصى لكل طلب (منع السبام/إغراق FCM) + عداد حقيقي
        val targets = request.friendIds.filter { it.isNotBlank() && it != inviter.redId }.distinct().take(32)
        targets.forEach { friendId ->
            notifications.sendVoipPushNotification(friendId, inviter.redId, streamId, "LIVESTREAM")
            callSignaling.deliverInvite(
                targetRedId = friendId,
                type = "LIVE_INVITE",
                roomId = streamId,
                sourceRedId = inviter.redId,
                mode = "LIVE",
                payload = mapOf("title" to record.title, "inviter" to inviter.displayName)
            )
        }
        return ResponseEntity.ok(mapOf("status" to "invited", "invitedCount" to targets.size, "streamId" to streamId))
    }

    @PostMapping("/{streamId}/kick/{viewerId}")
    fun kickViewer(
        @PathVariable streamId: String,
        @PathVariable viewerId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val kicked = liveStreamService.kickViewer(streamId, authentication.name, viewerId)
        if (!kicked) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Failed to kick viewer. Make sure you are the broadcaster and the viewer is active."))
        }
        return ResponseEntity.ok(mapOf("status" to "kicked", "viewerId" to viewerId, "streamId" to streamId))
    }

    // ── Legendary V2: meta / moderation / slow-mode / co-hosts / recording / analytics ──

    @GetMapping("/{streamId}")
    fun getMeta(@PathVariable streamId: String): ResponseEntity<LiveStreamResponse> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        return ResponseEntity.ok(LiveStreamResponse(
            streamId = record.streamId,
            title = record.title,
            broadcasterName = record.broadcasterName,
            broadcasterRedId = record.broadcasterRedId,
            isPrivate = record.isPrivate,
            viewerCount = liveStreamService.getViewerCount(streamId),
            inviteLink = "younes://livestream/${record.streamId}",
            category = record.category,
            slowModeSec = record.slowModeSec,
            recordingEnabled = record.recordingEnabled,
            broadcasterAvatar = record.broadcasterAvatar
        ))
    }

    @PostMapping("/{streamId}/slowmode")
    fun setSlowMode(
        @PathVariable streamId: String,
        @RequestBody body: Map<String, Int>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val v = liveStreamService.setSlowMode(streamId, authentication.name, body["seconds"] ?: 0)
        return ResponseEntity.ok(mapOf("streamId" to streamId, "slowModeSec" to v))
    }

    @PostMapping("/{streamId}/moderate")
    fun moderate(
        @PathVariable streamId: String,
        @RequestBody req: ModerateRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        when (req.action.uppercase()) {
            "MUTE" -> liveStreamService.muteUser(streamId, authentication.name, req.targetId, true)
            "UNMUTE" -> liveStreamService.muteUser(streamId, authentication.name, req.targetId, false)
            "BAN" -> liveStreamService.banUser(streamId, authentication.name, req.targetId, true)
            "UNBAN" -> liveStreamService.banUser(streamId, authentication.name, req.targetId, false)
            "PIN" -> liveStreamService.setPinned(streamId, authentication.name, req.targetId.ifBlank { null }, req.value)
            "UNPIN" -> liveStreamService.setPinned(streamId, authentication.name, null, null)
            "WORDS" -> liveStreamService.updateBlockedWords(streamId, authentication.name,
                req.value?.split(",")?.map { it.trim() } ?: emptyList())
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "UNKNOWN_ACTION"))
        }
        return ResponseEntity.ok(mapOf("status" to "ok", "action" to req.action, "streamId" to streamId))
    }

    @PostMapping("/{streamId}/cohost")
    fun cohost(
        @PathVariable streamId: String,
        @RequestBody req: CohostRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val ok = when (req.action.uppercase()) {
            "APPROVE" -> liveStreamService.approveCoHost(streamId, authentication.name, req.targetId)
            "REMOVE", "REJECT" -> liveStreamService.removeCoHost(streamId, authentication.name, req.targetId)
            "LEAVE" -> liveStreamService.removeCoHost(streamId, req.targetId, req.targetId)
            else -> false
        }
        if (!ok) return ResponseEntity.badRequest().body(mapOf("error" to "COHOST_FAILED"))
        return ResponseEntity.ok(mapOf("status" to "ok", "coHosts" to liveStreamService.getCoHosts(streamId)))
    }

    @GetMapping("/{streamId}/cohosts")
    fun listCohosts(@PathVariable streamId: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("streamId" to streamId, "coHosts" to liveStreamService.getCoHosts(streamId)))

    @PostMapping("/{streamId}/recording")
    fun recording(
        @PathVariable streamId: String,
        @RequestBody req: RecordingRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val ok = liveStreamService.setRecording(streamId, authentication.name, req.enabled, req.hlsUrl, req.vodUrl)
        if (!ok) return ResponseEntity.badRequest().body(mapOf("error" to "RECORDING_FAILED"))
        return ResponseEntity.ok(mapOf("status" to "ok", "recordingEnabled" to req.enabled))
    }

    /** تدوير كلمة السر — يبطل القديمة فوراً حتى لا يعود المطرود. */
    @PostMapping("/{streamId}/password")
    fun rotatePassword(
        @PathVariable streamId: String,
        @RequestBody body: Map<String, String?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val ok = liveStreamService.rotatePassword(streamId, authentication.name, body["password"])
        if (!ok) return ResponseEntity.badRequest().body(mapOf("error" to "PASSWORD_ROTATE_FAILED"))
        return ResponseEntity.ok(mapOf("status" to "ok", "streamId" to streamId))
    }

    @GetMapping("/{streamId}/analytics")
    fun analytics(
        @PathVariable streamId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        require(record.broadcasterId == authentication.name) { "ONLY_BROADCASTER" }
        return ResponseEntity.ok(liveStreamService.getAnalytics(streamId))
    }

    /** سجل الشات — آخر 30 رسالة + ترقيم بـ before (timestamp) للانضمام المتأخر. */
    @GetMapping("/{streamId}/chat")
    fun chatHistory(
        @PathVariable streamId: String,
        @RequestParam(required = false, defaultValue = "30") limit: Int,
        @RequestParam(required = false) before: Long?,
        authentication: Authentication
    ): ResponseEntity<List<Map<String, Any>>> {
        // يجب أن يكون منضماً (مذيع أو مشاهد) — وإلا 403 ضمني عبر isViewerAny
        val accountId = runCatching { UUID.fromString(authentication.name) }.getOrNull()
        val redId = accountId?.let { runCatching { users.findById(it).orElse(null)?.redId }.getOrNull() }
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val isBroadcaster = record.broadcasterId == authentication.name
        val allowed = isBroadcaster || (redId != null && liveStreamService.isViewerAny(streamId, redId, authentication.name))
        require(allowed) { "JOIN_REQUIRED" }
        val history = liveStreamService.getChatHistory(streamId, limit, before).map { e ->
            mapOf<String, Any>("id" to e.id, "senderId" to e.senderId, "senderName" to e.senderName, "text" to e.text, "replyToId" to (e.replyToId ?: ""), "createdAt" to e.createdAt)
        }
        return ResponseEntity.ok(history)
    }

    /** حالة الإشراف الكاملة — للمذيع العائد بعد انقطاع (بطيء/كلمات/كتم/حظر/مضيفون/مثبت). */
    @GetMapping("/{streamId}/moderation")
    fun moderationState(
        @PathVariable streamId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = liveStreamService.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        require(record.broadcasterId == authentication.name) { "ONLY_BROADCASTER" }
        return ResponseEntity.ok(mapOf(
            "streamId" to streamId,
            "slowModeSec" to record.slowModeSec,
            "blockedWords" to record.blockedWords,
            "mutedIds" to record.mutedIds,
            "bannedIds" to record.bannedIds,
            "coHostIds" to record.coHostIds,
            "pinnedChatId" to record.pinnedChatId,
            "pinnedText" to record.pinnedText,
            "raisedHands" to liveStreamService.getRaisedHands(streamId)
        ))
    }

    /** حذف رسالة — يبث CHAT_DELETED لكل الغرفة عبر قناة الإشارة. */
    @PostMapping("/{streamId}/chat/delete")
    fun deleteChat(
        @PathVariable streamId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val chatId = body["chatId"]?.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "CHAT_ID_REQUIRED"))
        val ok = liveStreamService.deleteChat(streamId, authentication.name, chatId)
        if (!ok) return ResponseEntity.badRequest().body(mapOf("error" to "DELETE_FAILED"))
        liveSignaling.broadcastToRoom(streamId, "CHAT_DELETED", mapOf("chatId" to chatId), authentication.name)
        return ResponseEntity.ok(mapOf("status" to "deleted", "chatId" to chatId))
    }
}

data class CreateStreamRequest(
    val streamId: String = "",
    val title: String = "",
    val isPrivate: Boolean = false,
    val password: String? = null,
    val category: String = "عام",
    val description: String = ""
)

data class LiveStreamResponse(
    val streamId: String,
    val title: String,
    val broadcasterName: String,
    val broadcasterRedId: String,
    val isPrivate: Boolean,
    val viewerCount: Int,
    val inviteLink: String,
    val category: String = "عام",
    val slowModeSec: Int = 0,
    val recordingEnabled: Boolean = false,
    val broadcasterAvatar: String? = null
)

data class JoinStreamRequest(
    val password: String? = null
)

data class JoinStreamResponse(
    val authorized: Boolean,
    val streamId: String,
    val title: String = "",
    val isPrivate: Boolean = false,
    val broadcasterName: String = "",
    val errorMessage: String? = null
)

data class InviteFriendsRequest(
    val friendIds: List<String> = emptyList()
)

data class ModerateRequest(
    val action: String = "",
    val targetId: String = "",
    val value: String? = null
)

data class CohostRequest(
    val action: String = "",
    val targetId: String = ""
)

data class RecordingRequest(
    val enabled: Boolean = false,
    val hlsUrl: String? = null,
    val vodUrl: String? = null
)
