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
    }

    private val roomParticipants = ConcurrentHashMap<String, MutableSet<String>>()
    private val activeRooms = ConcurrentHashMap<String, ConferenceRoomRecord>()
    /** قائمة دعوة الجلسات الخاصة؛ تبقى مستقلة عن الحضور الفعلي. */
    private val roomInvitees = ConcurrentHashMap<String, MutableSet<String>>()
    /** الغرف المقفلة: لا انضمام جديد إلا للمضيف/المدعوين الموجودين أصلاً. */
    private val lockedRooms = ConcurrentHashMap.newKeySet<String>()

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
        if (!room.isPrivate) return true
        return room.hostId == accountId || roomInvitees[roomId]?.contains(redId) == true
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
        if (!set.contains(userId) && set.size >= MAX_PARTICIPANTS) return -2 // ROOM_FULL
        set.add(userId)
        val count = set.size
        activeRooms[roomId]?.participantCount = count
        return count
    }

    fun isRoomFull(roomId: String): Boolean = getParticipantCount(roomId) >= MAX_PARTICIPANTS

    fun removeParticipant(roomId: String, userId: String) {
        roomParticipants[roomId]?.remove(userId)
        val count = getParticipantCount(roomId)
        activeRooms[roomId]?.participantCount = count
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
        if (removed) log.info("Conference room {} closed", roomId)
        return removed
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
        val authorized = roomService.canJoin(storedId, authentication.name, user.redId) &&
            (member || roomService.verifyPassword(storedId, request.password))
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
        if (roomService.isLocked(storedId) && !hostBypass && !member && !alreadyIn) {
            return ResponseEntity.status(423).body(JoinRoomResponse(
                authorized = false,
                roomId = storedId,
                errorMessage = "الغرفة مقفلة من المضيف — اطلب منه فتحها أو دعوتك"
            ))
        }
        if (roomService.isRoomFull(storedId)) {
            return ResponseEntity.status(429).body(JoinRoomResponse(
                authorized = false,
                roomId = storedId,
                errorMessage = "الغرفة ممتلئة (حتى ${ConferenceRoomService.MAX_PARTICIPANTS} مشارك)"
            ))
        }
        roomService.addParticipant(storedId, authentication.name)
        return ResponseEntity.ok(JoinRoomResponse(
            authorized = true,
            roomId = record.roomId,
            title = record.title,
            description = record.description,
            isSpace = record.isSpace,
            hostName = record.hostName
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
        return ResponseEntity.ok(mapOf("roomId" to record.roomId, "closed" to roomService.closeRoom(record.roomId)))
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
    val errorMessage: String? = null
)

data class InviteMembersRequest(
    val memberIds: List<String> = emptyList()
)
