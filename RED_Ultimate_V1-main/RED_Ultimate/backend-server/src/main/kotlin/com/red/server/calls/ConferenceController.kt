package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MongoDB document for Conference Rooms and Audio Spaces.
 */
@Document("conference_rooms")
data class ConferenceRoomRecord(
    @Id val roomId: String,
    @Indexed val hostId: String,
    val hostName: String = "",
    val hostRedId: String = "",
    val title: String = "",
    val description: String = "",
    val isSpace: Boolean = false, // true for Audio Space, false for Video Conference
    val isPrivate: Boolean = false,
    val passwordHash: String? = null,
    val createdAt: Instant = Instant.now(),
    var endedAt: Instant? = null,
    val maxSpeakers: Int = 20,
    val maxVideo: Int = 12,
    val maxCoHosts: Int = 2
) {
    var participantCount: Int = 0

    /**
     * غرفة الانتظار: من يدخل بالرابط أو من العامة لا يُقبل وسائطه قبل إذن المضيف أو
     * المشارك المضيف. الدعوة الصريحة وكلمة المرور الصحيحة تجاوزان الطابور — نفس
     * المنطق الذي يعفي المدعو من كلمة السر أعلاه.
     */
    var waitingRoomEnabled: Boolean = false

    /** مكتوم عند الدخول — سلوك الاجتماعات الكبيرة؛ واتساب لا يملكه أصلًا. */
    var mutedByDefault: Boolean = false

    /** بوابة مشاركة الشاشة: إيقافها يمنع نشر الشاشة فقط، لا الكاميرا والصوت. */
    var allowScreenShare: Boolean = true
}

@Service
class ConferenceRoomService(
    private val passwordHasher: RoomPasswordHasher
) {
    companion object {
        private val log = LoggerFactory.getLogger(ConferenceRoomService::class.java)
        /** حدود سعة مُنفذة (X Spaces: حتى 10-13 متحدث، مستمعون بلا حد). */
        const val MAX_PARTICIPANTS = 100
        const val MAX_VIDEO_TILES = 12
        const val MAX_SPEAKERS = 20
        const val MAX_COHOSTS = 2
        /** الغرف العامة تُخفى بعد 12 ساعة من الإنشاء (تنظيف منطقي بدون كسر in-memory). */
        const val PUBLIC_ROOM_TTL_HOURS = 12L

        /** عمر الرابط الأقصى 24 ساعة و1000 استخدام: رابط للأبد في مجموعة عامة ثغرة، لا راحة. */
        const val MAX_LINK_TTL_MINUTES = 24L * 60L
        const val MAX_LINK_USES = 1000
        /** 18 بايت = 24 رمز base64url: مساحة كافية لئلا يُخمَّن التوكن بالمسح. */
        const val LINK_TOKEN_BYTES = 18
    }

    private val roomParticipants = ConcurrentHashMap<String, MutableSet<String>>()
    private val activeRooms = ConcurrentHashMap<String, ConferenceRoomRecord>()
    /** قائمة دعوة الجلسات الخاصة؛ تبقى مستقلة عن الحضور الفعلي. */
    private val roomInvitees = ConcurrentHashMap<String, MutableSet<String>>()
    /** الغرف المقفلة: لا انضمام جديد إلا للمضيف/المدعوين الموجودين أصلاً. */
    private val lockedRooms = ConcurrentHashMap.newKeySet<String>()

    /** token → رابط. الروابط حالة غرفة لا وثيقة: عمرها أقصر من عمر إعادة التشغيل. */
    private val callLinks = ConcurrentHashMap<String, CallLink>()
    /** roomId → (accountId → طلب) — طابور واحد لكل غرفة، مرتّب بالوصول. */
    private val roomLobby = ConcurrentHashMap<String, ConcurrentHashMap<String, LobbyRequest>>()
    /** من أُذن لهم في هذه الغرفة: يعبرون الطابور عند إعادة الاتصال. */
    private val lobbyCleared = ConcurrentHashMap<String, MutableSet<String>>()
    /** من طُرد من الطابور مع خيار المنع: لا يعود مهما كرر الرابط. */
    private val lobbyBlocked = ConcurrentHashMap<String, MutableSet<String>>()
    private val linkRandom = java.security.SecureRandom()

    fun createRoom(
        roomId: String,
        hostId: String,
        hostName: String,
        hostRedId: String,
        title: String,
        isSpace: Boolean,
        isPrivate: Boolean,
        password: String?,
        inviteeRedIds: Collection<String> = emptyList(),
        description: String = ""
    ): ConferenceRoomRecord {
        require(roomId.isNotBlank()) { "ROOM_ID_REQUIRED" }
        require(hostId.isNotBlank()) { "HOST_ID_REQUIRED" }
        if (roomParticipants.containsKey(roomId)) {
            val existing = activeRooms[roomId] ?: error("CONFERENCE_ROOM_STATE_CORRUPT")
            require(existing.hostId == hostId) { "ROOM_ID_ALREADY_OWNED" }
            addInvitees(roomId, inviteeRedIds)
            return existing
        }
        val passHash = password?.takeIf { it.isNotBlank() }?.let { passwordHasher.hash(it) }
        val record = ConferenceRoomRecord(
            roomId = roomId,
            hostId = hostId,
            hostName = hostName.ifBlank { "مضيف المساحة" },
            hostRedId = hostRedId,
            title = title.ifBlank { if (isSpace) "مساحة صوتية 🎙️" else "مؤتمر جماعي 👥" },
            description = description.take(200),
            isSpace = isSpace,
            isPrivate = isPrivate,
            passwordHash = passHash,
            createdAt = Instant.now(),
            maxSpeakers = if (isSpace) MAX_SPEAKERS else MAX_VIDEO_TILES,
            maxVideo = MAX_VIDEO_TILES,
            maxCoHosts = MAX_COHOSTS
        )
        roomParticipants[roomId] = ConcurrentHashMap.newKeySet()
        roomInvitees[roomId] = ConcurrentHashMap.newKeySet<String>().apply {
            add(hostRedId)
            addAll(inviteeRedIds.filter { it.isNotBlank() })
        }
        activeRooms[roomId] = record
        log.info("Conference room {} created by host {} (space={}, private={})", roomId, hostId, isSpace, isPrivate)
        return record
    }

    fun verifyPassword(roomId: String, password: String?): Boolean {
        val record = activeRooms[roomId] ?: return false
        if (!record.isPrivate) return true
        // الجلسة الخاصة بدعوة صريحة لا تحتاج كلمة مرور مشتركة بين المدعوين.
        val passwordHash = record.passwordHash ?: return true
        if (password.isNullOrBlank()) return false
        return passwordHasher.verify(password, passwordHash)
    }

    fun searchPublicRooms(query: String?, isSpaceOnly: Boolean = false): List<ConferenceRoomRecord> {
        val cleanQuery = query?.trim()?.lowercase().orEmpty()
        val cutoff = Instant.now().minusSeconds(PUBLIC_ROOM_TTL_HOURS * 3600)
        return activeRooms.values
            .filter { !it.isPrivate }
            .filter { it.endedAt == null && it.createdAt.isAfter(cutoff) }
            .filter { if (isSpaceOnly) it.isSpace else true }
            .filter { record ->
                if (cleanQuery.isBlank()) true
                else record.title.lowercase().contains(cleanQuery) ||
                     record.hostName.lowercase().contains(cleanQuery) ||
                     record.hostRedId.lowercase().contains(cleanQuery) ||
                     record.roomId.lowercase().contains(cleanQuery)
            }
            .onEach { record -> record.participantCount = getParticipantCount(record.roomId) }
    }

    fun getRoom(roomId: String): ConferenceRoomRecord? = activeRooms[roomId]

    /** الجلسة العامة تسمح لأي مستخدم مصادق؛ الخاصة تسمح للمضيف أو للمدعوين فقط. */
    fun canJoin(roomId: String, accountId: String, redId: String): Boolean {
        val room = activeRooms[roomId] ?: return false
        if (room.endedAt != null || isLobbyBlocked(roomId, accountId)) return false
        if (!room.isPrivate) return true
        // A password/link entrant approved earlier has a valid REST seat without
        // becoming a permanent invitee. Let that account reconnect, not strangers.
        return room.hostId == accountId || roomInvitees[roomId]?.contains(redId) == true ||
            isParticipant(roomId, accountId) || isLobbyCleared(roomId, accountId) ||
            isWaiting(roomId, accountId)
    }

    /** مدعو صراحةً (أو المضيف) — يتجاوز كلمة السر لأن الدعوة نفسها اعتماد. */
    fun isInvited(roomId: String, accountId: String, redId: String): Boolean {
        val room = activeRooms[roomId] ?: return false
        return room.hostId == accountId || roomInvitees[roomId]?.contains(redId) == true
    }

    fun addInvitees(roomId: String, redIds: Collection<String>) {
        val invitees = roomInvitees[roomId] ?: return
        invitees.addAll(redIds.filter { it.isNotBlank() })
    }

    fun addParticipant(roomId: String, userId: String): Int {
        val set = roomParticipants[roomId] ?: return -1
        // A concurrent REST join/lobby admission must not pass the 100-seat cap.
        return synchronized(set) {
            if (activeRooms[roomId] == null || isLobbyBlocked(roomId, userId)) return@synchronized -1
            if (!set.contains(userId) && set.size >= MAX_PARTICIPANTS) return@synchronized -2
            set.add(userId)
            set.size.also { activeRooms[roomId]?.participantCount = it }
        }
    }

    fun isRoomFull(roomId: String): Boolean = getParticipantCount(roomId) >= MAX_PARTICIPANTS

    fun removeParticipant(roomId: String, userId: String) {
        val set = roomParticipants[roomId] ?: return
        synchronized(set) {
            set.remove(userId)
            activeRooms[roomId]?.participantCount = set.size
        }
    }

    /** A host kick revokes the seat, lobby clearance and invitation for this room. */
    fun blockParticipant(roomId: String, accountId: String, redId: String): Boolean {
        val room = activeRooms[roomId] ?: return false
        if (room.hostId == accountId) return false
        lobbyBlocked.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(accountId)
        lobbyCleared[roomId]?.remove(accountId)
        roomInvitees[roomId]?.remove(redId)
        leaveLobby(roomId, accountId)
        removeParticipant(roomId, accountId)
        return true
    }

    fun getParticipantCount(roomId: String): Int = roomParticipants[roomId]?.size ?: 0

    fun setLocked(roomId: String, locked: Boolean): Boolean {
        if (!activeRooms.containsKey(roomId)) return false
        if (locked) lockedRooms.add(roomId) else lockedRooms.remove(roomId)
        return true
    }

    fun isLocked(roomId: String): Boolean = lockedRooms.contains(roomId)

    fun isParticipant(roomId: String, userId: String): Boolean =
        roomParticipants[roomId]?.contains(userId) == true

    fun closeRoom(roomId: String): Boolean {
        val removed = roomParticipants.remove(roomId) != null
        roomInvitees.remove(roomId)
        activeRooms.remove(roomId)
        lockedRooms.remove(roomId)
        roomLobby.remove(roomId)
        lobbyCleared.remove(roomId)
        lobbyBlocked.remove(roomId)
        revokeCallLinks(roomId)
        if (removed) log.info("Conference room {} closed", roomId)
        return removed
    }

    // ── روابط الدعوة (call links) ───────────────────────────────────────────────
    /**
     * رابط انضمام قصير العمر لمكالمة جماعية أو مساحة صوتية. الرابط لا يحمل كلمة سر
     * ولا صلاحية المنصة: يعطي حق الحضور فقط، والطابور — إن كان مفتوحًا — يبقى سيّد القرار.
     */
    data class CallLink(
        val token: String,
        val roomId: String,
        val createdAt: Instant,
        val expiresAt: Instant?,
        val maxUses: Int?,
        var uses: Int = 0,
        var revoked: Boolean = false
    ) {
        fun liveAt(now: Instant): Boolean = !revoked &&
            (expiresAt == null || now.isBefore(expiresAt)) &&
            (maxUses == null || uses < maxUses)
    }

    /** طالب انضمام محبوس في الطابور حتى يبتّ فيه صاحب الصلاحية. */
    data class LobbyRequest(
        val accountId: String,
        val redId: String,
        val displayName: String,
        val requestedAt: Instant,
        val viaLink: Boolean
    )

    fun createCallLink(roomId: String, ttlMinutes: Long = 0, maxUses: Int = 0): CallLink {
        val record = activeRooms[roomId] ?: error("ROOM_NOT_OPEN")
        require(ttlMinutes in 0..MAX_LINK_TTL_MINUTES) { "LINK_TTL_OUT_OF_RANGE" }
        require(maxUses in 0..MAX_LINK_USES) { "LINK_USES_OUT_OF_RANGE" }
        val link = CallLink(
            token = newLinkToken(),
            roomId = record.roomId,
            createdAt = Instant.now(),
            expiresAt = if (ttlMinutes > 0) Instant.now().plusSeconds(ttlMinutes * 60) else null,
            maxUses = if (maxUses > 0) maxUses else null
        )
        callLinks[link.token] = link
        log.info("Call link created for room {} (ttl={}min, uses={})", roomId, ttlMinutes, maxUses)
        return link
    }

    /** إلغاء كل روابط الغرفة دفعة واحدة — الرابط المسرّب في مجموعة لا يُتتبَّع فرديًا. */
    fun revokeCallLinks(roomId: String): Int {
        var revoked = 0
        callLinks.values.forEach { if (it.roomId == roomId && !it.revoked) { it.revoked = true; revoked++ } }
        return revoked
    }

    fun linkFor(roomId: String): CallLink? =
        callLinks.values.lastOrNull { it.roomId == roomId && it.liveAt(Instant.now()) }

    /** معاينة لا تستهلك استخدامًا: مشاركة الرابط في دردشة يجب ألا تستنفده. */
    fun resolveCallLink(token: String): Pair<CallLink, ConferenceRoomRecord>? {
        val link = callLinks[token] ?: return null
        if (!link.liveAt(Instant.now())) return null
        val record = activeRooms[link.roomId] ?: run { link.revoked = true; return null }
        return link to record
    }

    /** الاستهلاك عند الدخول فعلًا، لا عند الفتح. */
    fun redeemCallLink(token: String): Boolean {
        val link = callLinks[token] ?: return false
        if (!link.liveAt(Instant.now())) return false
        link.uses += 1
        return true
    }

    private fun newLinkToken(): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(LINK_TOKEN_BYTES).also { linkRandom.nextBytes(it) })

    // ── غرفة الانتظار (lobby) ───────────────────────────────────────────────────
    fun isLobbyEnabled(roomId: String): Boolean = activeRooms[roomId]?.waitingRoomEnabled == true

    /** من أُذن له يبقى مسموحًا به في الجلسة نفسها: إعادة الاتصال بعد انقطاع الشبكة
     *  يجب أن لا يعيده إلى الطابور فيُحرم نصف الحديث من سماعه. */
    fun isLobbyCleared(roomId: String, accountId: String): Boolean = lobbyCleared[roomId]?.contains(accountId) == true

    /** A private-room password or call link grants access for this room's lifetime,
     *  not just while the last socket holds a seat. Host kick revokes this grant. */
    fun rememberPrivateAdmission(roomId: String, accountId: String) {
        if (activeRooms[roomId]?.isPrivate == true && isParticipant(roomId, accountId)) {
            lobbyCleared.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }.add(accountId)
        }
    }

    fun isLobbyBlocked(roomId: String, accountId: String): Boolean = lobbyBlocked[roomId]?.contains(accountId) == true

    /** المضيف والمدعو صراحةً لا يمران بالطابور: الدعوة نفسها اعتماد. */
    fun bypassesLobby(roomId: String, accountId: String, redId: String): Boolean {
        val room = activeRooms[roomId] ?: return true
        return room.hostId == accountId || roomInvitees[roomId]?.contains(redId) == true
    }

    /**
     * @return الطلب المُصفّى في الطابور، أو null عندما يُقبل الدخول مباشرة
     * (لوبّي مغلق، أو صاحب تجاوز، أو محجوبًا سابقًا — فيُرفض بصمت بلا صف).
     */
    fun enterLobby(roomId: String, accountId: String, redId: String, displayName: String, viaLink: Boolean): LobbyRequest? {
        if (!isLobbyEnabled(roomId)) return null
        if (isLobbyBlocked(roomId, accountId) || isLobbyCleared(roomId, accountId) || bypassesLobby(roomId, accountId, redId)) return null
        val entry = LobbyRequest(accountId, redId, displayName.take(64), Instant.now(), viaLink)
        roomLobby.computeIfAbsent(roomId) { ConcurrentHashMap() }[accountId] = entry
        return entry
    }

    fun lobbyQueue(roomId: String): List<LobbyRequest> =
        roomLobby[roomId]?.values?.sortedBy { it.requestedAt } ?: emptyList()

    fun lobbyCount(roomId: String): Int = roomLobby[roomId]?.size ?: 0

    fun isWaiting(roomId: String, accountId: String): Boolean =
        roomLobby[roomId]?.containsKey(accountId) == true

    fun leaveLobby(roomId: String, accountId: String): Boolean = roomLobby[roomId]?.remove(accountId) != null

    /**
     * إدخال طالب أو أكثر بالترتيب. الإضافة إلى الحاضرين تحدث هنا لا في النداء التالي،
     * وإلا فاز من يفتح الرابط أولًا بالسباق على مقاعد الغرفة الممتلئة.
     * @return من دُخِّلوا فعلًا (يتوقف الطابور عند اكتمال الغرفة لا عند أول رفض).
     */
    fun admitFromLobby(roomId: String, accountIds: Collection<String>): List<String> {
        val queue = roomLobby[roomId] ?: return emptyList()
        val cleared = lobbyCleared.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
        val admitted = ArrayList<String>(accountIds.size)
        for (accountId in accountIds) {
            if (!queue.containsKey(accountId)) continue
            if (addParticipant(roomId, accountId) < 0) break
            queue.remove(accountId)
            cleared.add(accountId)
            admitted += accountId
        }
        if (admitted.isNotEmpty()) log.info("Room {} admitted {} from lobby", roomId, admitted.size)
        return admitted
    }

    /** طرد من الطابور؛ والحجب يمنع عودته بنفس الجلسة (زر «امنع» عند المضيف). */
    fun denyFromLobby(roomId: String, accountIds: Collection<String>, block: Boolean = false): List<String> {
        val queue = roomLobby[roomId] ?: return emptyList()
        val denied = ArrayList<String>(accountIds.size)
        for (accountId in accountIds) {
            if (queue.remove(accountId) != null) denied += accountId
        }
        if (block) {
            val blocked = lobbyBlocked.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
            denied.forEach { blocked.add(it) }
        }
        return denied
    }

    /**
     * تعديل سياسة الغرفةlive. إغلاق اللوبي لا يترك من في الطابور معلقًا في غرفة مفتوحة:
     * يُدخَلون بالترتيب حتى تمتلئ.
     */
    fun updateRoomFlags(
        roomId: String,
        waitingRoom: Boolean? = null,
        mutedByDefault: Boolean? = null,
        allowScreenShare: Boolean? = null
    ): ConferenceRoomRecord? {
        val record = activeRooms[roomId] ?: return null
        waitingRoom?.let { record.waitingRoomEnabled = it }
        mutedByDefault?.let { record.mutedByDefault = it }
        allowScreenShare?.let { record.allowScreenShare = it }
        if (waitingRoom == false) {
            val queued = roomLobby.remove(roomId)
            if (queued != null) {
                val cleared = lobbyCleared.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
                queued.keys.sortedBy { queued[it]?.requestedAt }.forEach { accountId ->
                    if (addParticipant(roomId, accountId) >= 0) cleared.add(accountId)
                }
            }
        }
        return record
    }
}

@RestController
@RequestMapping("/api/conference")
class ConferenceController(
    private val roomService: ConferenceRoomService,
    private val users: UserAccountRepository,
    private val notifications: NotificationService,
    private val history: CallHistoryService,
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler,
    private val conferenceSignaling: com.red.server.websocket.ConferenceWebSocketHandler,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val aliases: RoomAliasService? = null
) {

    @PostMapping("/create")
    fun createRoom(
        @Valid @RequestBody request: CreateRoomRequest,
        authentication: Authentication
    ): ResponseEntity<ConferenceRoomResponse> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        // roomId اختياري: إن كان فارغاً يولّد الخادم معرفاً (إصلاح فشل Explore الذي كان يرسل "")
        // مسار إنشاء: تقنين إلى البادئة القانونية، والقديم بلا بادئة يُربط ولا يُكسر.
        val roomId = if (aliases != null) {
            aliases.canonicalize(RoomSeparationPolicy.PREFIX_CONF, request.roomId) {
                UUID.randomUUID().toString().replace("-", "").take(12)
            }
        } else {
            request.roomId.trim().ifBlank { "room_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
                .also { require(it.matches(Regex("^[A-Za-z0-9_-]{4,128}$"))) { "Invalid roomId" } }
        }
        val record = roomService.createRoom(
            roomId = roomId,
            hostId = user.id.toString(),
            hostName = user.displayName,
            hostRedId = user.redId,
            title = request.title,
            isSpace = request.isSpace,
            isPrivate = request.isPrivate,
            password = request.password,
            inviteeRedIds = request.inviteeRedIds,
            description = request.description
        )
        val inviteLink = "younes://${if (request.isSpace) "space" else "conference"}/$roomId"
        runCatching {
            history.start(
                initiator = user.redId,
                target = record.roomId,
                targetLabel = record.title,
                type = if (request.isSpace) CallType.SPACE else CallType.GROUP_VIDEO,
                route = CallRoute.RED,
                requestedId = record.roomId
            )
        }
        return ResponseEntity.ok(ConferenceRoomResponse(
            roomId = record.roomId,
            title = record.title,
            description = record.description,
            hostName = record.hostName,
            hostRedId = record.hostRedId,
            isSpace = record.isSpace,
            isPrivate = record.isPrivate,
            participantCount = 0,
            inviteLink = inviteLink,
            maxSpeakers = record.maxSpeakers,
            maxVideo = record.maxVideo
        ))
    }

    @GetMapping("/public")
    fun listPublic(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "false") isSpace: Boolean
    ): ResponseEntity<List<ConferenceRoomResponse>> {
        val rooms = roomService.searchPublicRooms(query, isSpace)
        val responses = rooms.map { record ->
            ConferenceRoomResponse(
                roomId = record.roomId,
                title = record.title,
                description = record.description,
                hostName = record.hostName,
                hostRedId = record.hostRedId,
                isSpace = record.isSpace,
                isPrivate = record.isPrivate,
                participantCount = record.participantCount,
                inviteLink = "younes://${if (record.isSpace) "space" else "conference"}/${record.roomId}",
                maxSpeakers = record.maxSpeakers,
                maxVideo = record.maxVideo
            )
        }
        return ResponseEntity.ok(responses)
    }

    @PostMapping("/{roomId}/join")
    fun joinRoom(
        @PathVariable roomId: String,
        @RequestBody request: JoinRoomRequest,
        authentication: Authentication
    ): ResponseEntity<JoinRoomResponse> {
        // مسار انضمام: حل المستعار إلى القانوني، مع سقوط للخام القديم.
        val rawTrimmed = roomId.trim()
        val effectiveRoomId = aliases?.resolve(roomId) ?: rawTrimmed
        val record = roomService.getRoom(effectiveRoomId)
            ?: roomService.getRoom(rawTrimmed)
            ?: run {
                if (RoomSeparationPolicy.kindOf(rawTrimmed) == RoomSeparationPolicy.RoomKind.LEGACY && RoomSeparationPolicy.isValidRoomId(rawTrimmed)) roomService.getRoom(RoomSeparationPolicy.PREFIX_CONF + rawTrimmed) else null
            } ?: throw NoSuchElementException("Conference room not found")
        val storedId = record.roomId
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        // المدعو/المضيف يتجاوز كلمة السر (الدعوة اعتماد) — كلمة السر للعامة المحمية فقط.
        // (كان المدعو لغرفة خاصة بكلمة سر يُرفض 403 بلا طريق دخول).
        val member = roomService.isInvited(storedId, authentication.name, user.redId)
        val alreadyAdmitted = roomService.isParticipant(storedId, authentication.name) ||
            roomService.isLobbyCleared(storedId, authentication.name)
        val passwordAccepted = record.passwordHash != null &&
            roomService.verifyPassword(storedId, request.password)
        val authorized = record.endedAt == null && !roomService.isLobbyBlocked(storedId, authentication.name) &&
            ((roomService.canJoin(storedId, authentication.name, user.redId) &&
                (member || alreadyAdmitted || !record.isPrivate)) || passwordAccepted)
        if (!authorized) {
            return ResponseEntity.status(403).body(JoinRoomResponse(
                authorized = false,
                roomId = storedId,
                errorMessage = "لا تملك صلاحية الانضمام إلى هذه المكالمة"
            ))
        }
        // الغرفة المقفلة: المضيف والمدعوون والحاضرون فقط — الغرباء يُرفضون برسالة واضحة.
        val hostBypass = record.hostId == authentication.name
        val alreadyIn = roomService.getParticipantCount(storedId) > 0 &&
            roomService.isParticipant(storedId, authentication.name)
        if (roomService.isLocked(storedId) && !hostBypass && !member && !alreadyAdmitted) {
            return ResponseEntity.status(423).body(JoinRoomResponse(
                authorized = false,
                roomId = storedId,
                errorMessage = "الغرفة مقفلة من المضيف — اطلب منه فتحها أو دعوتك"
            ))
        }
        // REST and WS must share the same lobby gate. Previously REST added a seat
        // immediately, so WS saw isParticipant=true and bypassed host approval.
        if (roomService.isLobbyEnabled(storedId) && !member && !alreadyIn &&
            !roomService.isLobbyCleared(storedId, authentication.name)) {
            roomService.enterLobby(storedId, authentication.name, user.redId, user.displayName, viaLink = false)
            val position = roomService.lobbyQueue(storedId).indexOfFirst { it.accountId == authentication.name } + 1
            return ResponseEntity.status(202).body(JoinRoomResponse(
                authorized = false, roomId = storedId, waiting = true,
                lobbyPosition = position.coerceAtLeast(1)
            ))
        }
        if (!alreadyIn && roomService.isRoomFull(storedId)) {
            return ResponseEntity.status(429).body(JoinRoomResponse(
                authorized = false,
                roomId = storedId,
                errorMessage = "الغرفة ممتلئة (حتى ${ConferenceRoomService.MAX_PARTICIPANTS} مشارك)"
            ))
        }
        if (roomService.addParticipant(storedId, authentication.name) < 0) {
            return ResponseEntity.status(429).body(JoinRoomResponse(
                authorized = false, roomId = storedId, errorMessage = "الغرفة ممتلئة"
            ))
        }
        if (passwordAccepted) roomService.rememberPrivateAdmission(storedId, authentication.name)
        return ResponseEntity.ok(JoinRoomResponse(
            authorized = true,
            roomId = record.roomId,
            title = record.title,
            description = record.description,
            isSpace = record.isSpace,
            hostName = record.hostName,
            mutedByDefault = record.mutedByDefault,
            allowScreenShare = record.allowScreenShare
        ))
    }

    @PostMapping("/{roomId}/leave")
    fun leaveRoom(
        @PathVariable roomId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val effective = aliases?.resolve(roomId) ?: roomId.trim()
        val stored = roomService.getRoom(effective)?.roomId ?: roomService.getRoom(roomId.trim())?.roomId ?: effective
        roomService.removeParticipant(stored, authentication.name)
        // توافق: إزالة من المفتاح الآخر أيضاً عند اختلاف المستعار عن المخزن.
        if (stored != roomId.trim()) roomService.removeParticipant(roomId.trim(), authentication.name)
        conferenceSignaling.evictAccount(stored, authentication.name)
        return ResponseEntity.ok(mapOf("roomId" to stored, "participantCount" to roomService.getParticipantCount(stored)))
    }

    @PostMapping("/{roomId}/lock")
    fun lockRoom(
        @PathVariable roomId: String,
        @RequestBody request: LockRoomRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val effective = aliases?.resolve(roomId) ?: roomId.trim()
        val record = roomService.getRoom(effective) ?: roomService.getRoom(roomId.trim()) ?: throw NoSuchElementException("Room not found")
        require(record.hostId == authentication.name) { "ONLY_HOST_CAN_LOCK" }
        roomService.setLocked(record.roomId, request.locked)
        return ResponseEntity.ok(mapOf("roomId" to record.roomId, "locked" to request.locked))
    }

    @PostMapping("/{roomId}/close")
    fun closeRoom(
        @PathVariable roomId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val effective = aliases?.resolve(roomId) ?: roomId.trim()
        val record = roomService.getRoom(effective) ?: roomService.getRoom(roomId.trim()) ?: throw NoSuchElementException("Room not found")
        require(record.hostId == authentication.name) { "ONLY_HOST_CAN_CLOSE" }
        val closed = roomService.closeRoom(record.roomId)
        if (closed) conferenceSignaling.closeSignalingRoom(record.roomId)
        return ResponseEntity.ok(mapOf("roomId" to record.roomId, "closed" to closed))
    }

    @PostMapping("/{roomId}/invite")
    fun inviteMembers(
        @PathVariable roomId: String,
        @RequestBody request: InviteMembersRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val effective = aliases?.resolve(roomId) ?: roomId.trim()
        val record = roomService.getRoom(effective) ?: roomService.getRoom(roomId.trim())
            ?: throw NoSuchElementException("Room not found")
        val storedId = record.roomId
        val accountId = UUID.fromString(authentication.name)
        val inviter = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        require(record.hostId == authentication.name) { "ONLY_HOST_CAN_INVITE" }
        roomService.addInvitees(storedId, request.memberIds)
        val mode = if (record.isSpace) "SPACE" else "CONFERENCE"
        request.memberIds.filter { it.isNotBlank() && it != inviter.redId }.forEach { memberId ->
            notifications.sendVoipPushNotification(memberId, inviter.redId, storedId, mode)
            callSignaling.deliverInvite(
                targetRedId = memberId,
                type = "CONFERENCE_INVITE",
                roomId = storedId,
                sourceRedId = inviter.redId,
                mode = mode,
                payload = mapOf(
                    "title" to record.title,
                    "inviter" to inviter.displayName,
                    "video" to (!record.isSpace).toString()
                )
            )
        }
        return ResponseEntity.ok(mapOf("status" to "invited", "invitedCount" to request.memberIds.size, "roomId" to storedId))
    }
    // ── روابط الدعوة ───────────────────────────────────────────────────────────

    /**
     * إنشاء رابط لمكالمة. المضيف وحده (المشارك المضيف صلاحية منصّة لا صلاحية غرفة،
     * فالدعوة إلى المكالمة قرار المضيف). `ttlMinutes=0` = لا انتهاء زمني، و`maxUses=0`
     * = بلا حد استخدام — والقيدان معًا افتراضيًا في حدود الغرفة.
     */
    @PostMapping("/{roomId}/link")
    fun createLink(
        @PathVariable roomId: String,
        @RequestBody(required = false) request: CreateLinkRequest?,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = hostOnly(roomId, authentication.name)
        val link = roomService.createCallLink(
            roomId = record.roomId,
            ttlMinutes = request?.ttlMinutes ?: 0L,
            maxUses = request?.maxUses ?: 0
        )
        return ResponseEntity.ok(linkPayload(link))
    }

    /** إلغاء روابط الغرفة كلها — الكافي عند تسرّب رابط إلى مجموعة عامة. */
    @DeleteMapping("/{roomId}/link")
    fun revokeLinks(
        @PathVariable roomId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = hostOnly(roomId, authentication.name)
        return ResponseEntity.ok(mapOf(
            "roomId" to record.roomId,
            "revoked" to roomService.revokeCallLinks(record.roomId)
        ))
    }

    /** معاينة الرابط قبل الدخول: اسم الغرفة ومضيفها وعددها، بلا أسماء الحاضرين. */
    @GetMapping("/link/{token}")
    fun previewLink(@PathVariable token: String): ResponseEntity<Map<String, Any?>> {
        val resolved = roomService.resolveCallLink(token)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "LINK_EXPIRED_OR_REVOKED"))
        val (link, record) = resolved
        return ResponseEntity.ok(mapOf(
            "roomId" to record.roomId,
            "title" to record.title,
            "hostName" to record.hostName,
            "isSpace" to record.isSpace,
            "participantCount" to roomService.getParticipantCount(record.roomId),
            "waitingRoomEnabled" to record.waitingRoomEnabled,
            "full" to roomService.isRoomFull(record.roomId),
            "expiresAt" to link.expiresAt?.toString(),
            "usesLeft" to link.maxUses?.minus(link.uses)
        ))
    }

    /** الدخول بالرابط: لا كلمة سر — الرابط نفسه اعتماد مؤقت، والطابور سيّد القرار إن كان مفتوحًا. */
    @PostMapping("/link/{token}/join")
    fun joinByLink(
        @PathVariable token: String,
        authentication: Authentication
    ): ResponseEntity<JoinRoomResponse> {
        val resolved = roomService.resolveCallLink(token)
            ?: return ResponseEntity.status(404).body(JoinRoomResponse(authorized = false, roomId = "", errorMessage = "الرابط منتهٍ أو ملغى"))
        val (link, record) = resolved
        if (roomService.isLobbyBlocked(record.roomId, authentication.name)) {
            return ResponseEntity.status(403).body(JoinRoomResponse(
                authorized = false, roomId = record.roomId, errorMessage = "لا تملك صلاحية العودة إلى هذه الغرفة"
            ))
        }
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        if (roomService.isLocked(record.roomId) &&
            !roomService.isInvited(record.roomId, authentication.name, user.redId) &&
            !roomService.isParticipant(record.roomId, authentication.name) &&
            !roomService.isLobbyCleared(record.roomId, authentication.name)) {
            return ResponseEntity.status(423).body(JoinRoomResponse(
                authorized = false, roomId = record.roomId, errorMessage = "الغرفة مقفلة من المضيف"
            ))
        }
        if (roomService.isRoomFull(record.roomId) && !roomService.isParticipant(record.roomId, authentication.name)) {
            return ResponseEntity.status(429).body(JoinRoomResponse(
                authorized = false,
                roomId = record.roomId,
                errorMessage = "المكالمة ممتلئة (حتى " + ConferenceRoomService.MAX_PARTICIPANTS + " مشارك)"
            ))
        }
        if (!roomService.redeemCallLink(token)) {
            return ResponseEntity.status(410).body(JoinRoomResponse(authorized = false, roomId = record.roomId, errorMessage = "استُنفد عدد استخدامات الرابط"))
        }
        val pending = roomService.enterLobby(
            roomId = record.roomId,
            accountId = authentication.name,
            redId = user.redId,
            displayName = user.displayName,
            viaLink = true
        )
        if (pending != null) {
            conferenceSignaling.notifyLobbyWaiting(record.roomId)
            return ResponseEntity.accepted().body(JoinRoomResponse(
                authorized = false,
                waiting = true,
                roomId = record.roomId,
                title = record.title,
                hostName = record.hostName,
                isSpace = record.isSpace,
                errorMessage = "بانتظار إذن المضيف",
                mutedByDefault = record.mutedByDefault,
                allowScreenShare = record.allowScreenShare
            ))
        }
        if (roomService.addParticipant(record.roomId, authentication.name) < 0) {
            return ResponseEntity.status(429).body(JoinRoomResponse(
                authorized = false, roomId = record.roomId, errorMessage = "الغرفة ممتلئة"
            ))
        }
        roomService.rememberPrivateAdmission(record.roomId, authentication.name)
        return ResponseEntity.ok(JoinRoomResponse(
            authorized = true,
            roomId = record.roomId,
            title = record.title,
            hostName = record.hostName,
            isSpace = record.isSpace,
            mutedByDefault = record.mutedByDefault,
            allowScreenShare = record.allowScreenShare
        ))
    }

    // ── غرفة الانتظار ──────────────────────────────────────────────────────────

    @GetMapping("/{roomId}/lobby")
    fun lobbyQueue(
        @PathVariable roomId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = hostOnly(roomId, authentication.name)
        val queue = roomService.lobbyQueue(record.roomId)
        return ResponseEntity.ok(mapOf(
            "roomId" to record.roomId,
            "waitingRoomEnabled" to record.waitingRoomEnabled,
            "pending" to queue.map {
                mapOf(
                    "accountId" to it.accountId,
                    "redId" to it.redId,
                    "displayName" to it.displayName,
                    "requestedAt" to it.requestedAt.toString(),
                    "viaLink" to it.viaLink
                )
            }
        ))
    }

    @PostMapping("/{roomId}/lobby/admit")
    fun admitLobby(
        @PathVariable roomId: String,
        @RequestBody request: LobbyActionRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = hostOnly(roomId, authentication.name)
        val targets = request.accountIds.ifEmpty { roomService.lobbyQueue(record.roomId).map { it.accountId } }
        val admitted = roomService.admitFromLobby(record.roomId, targets)
        if (admitted.isNotEmpty()) conferenceSignaling.notifyLobbyAdmitted(record.roomId, admitted)
        return ResponseEntity.ok(mapOf(
            "roomId" to record.roomId,
            "admitted" to admitted,
            "stillWaiting" to roomService.lobbyCount(record.roomId)
        ))
    }

    @PostMapping("/{roomId}/lobby/deny")
    fun denyLobby(
        @PathVariable roomId: String,
        @RequestBody request: LobbyActionRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = hostOnly(roomId, authentication.name)
        val denied = roomService.denyFromLobby(record.roomId, request.accountIds, block = request.block)
        if (denied.isNotEmpty()) conferenceSignaling.notifyLobbyDenied(record.roomId, denied)
        return ResponseEntity.ok(mapOf(
            "roomId" to record.roomId,
            "denied" to denied,
            "blocked" to request.block,
            "stillWaiting" to roomService.lobbyCount(record.roomId)
        ))
    }

    /** سياسة الغرفة live: اللوبي، الكتم الافتراضي، مشاركة الشاشة. */
    @PatchMapping("/{roomId}/settings")
    fun updateRoomSettings(
        @PathVariable roomId: String,
        @RequestBody request: RoomPolicyRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val record = hostOnly(roomId, authentication.name)
        val awaiting = if (request.waitingRoomEnabled == false) roomService.lobbyQueue(record.roomId).map { it.accountId }
            else emptyList()
        val updated = roomService.updateRoomFlags(
            roomId = record.roomId,
            waitingRoom = request.waitingRoomEnabled,
            mutedByDefault = request.mutedByDefault,
            allowScreenShare = request.allowScreenShare
        ) ?: return ResponseEntity.status(404).body(mapOf("error" to "ROOM_NOT_OPEN"))
        if (awaiting.isNotEmpty()) {
            val admitted = awaiting.filter { roomService.isParticipant(record.roomId, it) }
            conferenceSignaling.notifyLobbyAdmitted(record.roomId, admitted)
            conferenceSignaling.notifyLobbyDenied(record.roomId, awaiting - admitted.toSet())
        }
        return ResponseEntity.ok(mapOf(
            "roomId" to updated.roomId,
            "waitingRoomEnabled" to updated.waitingRoomEnabled,
            "mutedByDefault" to updated.mutedByDefault,
            "allowScreenShare" to updated.allowScreenShare,
            "stillWaiting" to roomService.lobbyCount(updated.roomId)
        ))
    }

    private fun hostOnly(roomId: String, accountId: String): ConferenceRoomRecord {
        val record = roomService.getRoom(roomId) ?: throw NoSuchElementException("Conference room not found")
        require(record.hostId == accountId) { "ONLY_HOST" }
        return record
    }

    private fun linkPayload(link: ConferenceRoomService.CallLink): Map<String, Any?> = mapOf(
        "token" to link.token,
        "roomId" to link.roomId,
        "url" to "younes://join/" + link.token,
        "createdAt" to link.createdAt.toString(),
        "expiresAt" to link.expiresAt?.toString(),
        "maxUses" to link.maxUses,
        "uses" to link.uses
    )
}

data class CreateRoomRequest(
    val roomId: String = "",
    @field:NotBlank val title: String,
    val description: String = "",
    val isSpace: Boolean = false,
    val isPrivate: Boolean = false,
    val password: String? = null,
    val inviteeRedIds: List<String> = emptyList()
)

data class ConferenceRoomResponse(
    val roomId: String,
    val title: String,
    val description: String = "",
    val hostName: String,
    val hostRedId: String,
    val isSpace: Boolean,
    val isPrivate: Boolean,
    val participantCount: Int,
    val inviteLink: String,
    val maxSpeakers: Int = 20,
    val maxVideo: Int = 12
)

data class JoinRoomRequest(
    val password: String? = null
)

data class LockRoomRequest(
    val locked: Boolean = true
)

data class JoinRoomResponse(
    val authorized: Boolean,
    val roomId: String,
    val title: String = "",
    val description: String = "",
    val isSpace: Boolean = false,
    val hostName: String = "",
    val errorMessage: String? = null,
    /** true = في غرفة الانتظار؛ أعِد المحاولة أو انتظر رسالة LOBBY على سوكت الإشارة. */
    val waiting: Boolean = false,
    val lobbyPosition: Int = 0,
    /** سياسة الغرفة تُبلَّغ مع القبول حتى لا تسأل الواجهة مرتين. */
    val mutedByDefault: Boolean = false,
    val allowScreenShare: Boolean = true
)

/** `ttlMinutes = 0` بلا انتهاء، و`maxUses = 0` بلا حد استخدام. */
data class CreateLinkRequest(
    val ttlMinutes: Long = 0,
    val maxUses: Int = 0
)

/** قائمة فارغة في `admit` = أدخل الطابور كله بالترتيب. */
data class LobbyActionRequest(
    val accountIds: List<String> = emptyList(),
    /** مع `deny` فقط: امنع العودة بهذه الجلسة. */
    val block: Boolean = false
)

data class RoomPolicyRequest(
    val waitingRoomEnabled: Boolean? = null,
    val mutedByDefault: Boolean? = null,
    val allowScreenShare: Boolean? = null
)

data class InviteMembersRequest(
    val memberIds: List<String> = emptyList()
)
