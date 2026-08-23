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
    val isSpace: Boolean = false, // true for Audio Space, false for Video Conference
    val isPrivate: Boolean = false,
    val passwordHash: String? = null,
    val createdAt: Instant = Instant.now(),
    var endedAt: Instant? = null
) {
    var participantCount: Int = 0
}

@Service
class ConferenceRoomService(
    private val passwordHasher: RoomPasswordHasher
) {
    companion object { private val log = LoggerFactory.getLogger(ConferenceRoomService::class.java) }

    private val roomParticipants = ConcurrentHashMap<String, MutableSet<String>>()
    private val activeRooms = ConcurrentHashMap<String, ConferenceRoomRecord>()
    /** قائمة دعوة الجلسات الخاصة؛ تبقى مستقلة عن الحضور الفعلي. */
    private val roomInvitees = ConcurrentHashMap<String, MutableSet<String>>()

    fun createRoom(
        roomId: String,
        hostId: String,
        hostName: String,
        hostRedId: String,
        title: String,
        isSpace: Boolean,
        isPrivate: Boolean,
        password: String?,
        inviteeRedIds: Collection<String> = emptyList()
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
            isSpace = isSpace,
            isPrivate = isPrivate,
            passwordHash = passHash,
            createdAt = Instant.now()
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
        return activeRooms.values
            .filter { !it.isPrivate }
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

    fun addInvitees(roomId: String, redIds: Collection<String>) {
        val invitees = roomInvitees[roomId] ?: return
        invitees.addAll(redIds.filter { it.isNotBlank() })
    }

    fun addParticipant(roomId: String, userId: String): Int {
        val set = roomParticipants[roomId] ?: return -1
        set.add(userId)
        val count = set.size
        activeRooms[roomId]?.participantCount = count
        return count
    }

    fun removeParticipant(roomId: String, userId: String) {
        roomParticipants[roomId]?.remove(userId)
        val count = getParticipantCount(roomId)
        activeRooms[roomId]?.participantCount = count
    }

    fun getParticipantCount(roomId: String): Int = roomParticipants[roomId]?.size ?: 0

    fun closeRoom(roomId: String): Boolean {
        val removed = roomParticipants.remove(roomId) != null
        roomInvitees.remove(roomId)
        activeRooms.remove(roomId)
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
    private val callSignaling: com.red.server.websocket.CallWebSocketHandler
) {

    @PostMapping("/create")
    fun createRoom(
        @RequestBody request: CreateRoomRequest,
        authentication: Authentication
    ): ResponseEntity<ConferenceRoomResponse> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val roomId = request.roomId.trim().ifBlank { "room_${UUID.randomUUID().toString().take(12)}" }
        val record = roomService.createRoom(
            roomId = roomId,
            hostId = user.id.toString(),
            hostName = user.displayName,
            hostRedId = user.redId,
            title = request.title,
            isSpace = request.isSpace,
            isPrivate = request.isPrivate,
            password = request.password,
            inviteeRedIds = request.inviteeRedIds
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
            hostName = record.hostName,
            hostRedId = record.hostRedId,
            isSpace = record.isSpace,
            isPrivate = record.isPrivate,
            participantCount = 0,
            inviteLink = inviteLink
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
                hostName = record.hostName,
                hostRedId = record.hostRedId,
                isSpace = record.isSpace,
                isPrivate = record.isPrivate,
                participantCount = record.participantCount,
                inviteLink = "younes://${if (record.isSpace) "space" else "conference"}/${record.roomId}"
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
        val record = roomService.getRoom(roomId)
            ?: throw NoSuchElementException("Conference room not found")
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val authorized = roomService.canJoin(roomId, authentication.name, user.redId) &&
            roomService.verifyPassword(roomId, request.password)
        if (!authorized) {
            return ResponseEntity.status(403).body(JoinRoomResponse(
                authorized = false,
                roomId = roomId,
                errorMessage = "لا تملك صلاحية الانضمام إلى هذه المكالمة"
            ))
        }
        roomService.addParticipant(roomId, authentication.name)
        return ResponseEntity.ok(JoinRoomResponse(
            authorized = true,
            roomId = record.roomId,
            title = record.title,
            isSpace = record.isSpace,
            hostName = record.hostName
        ))
    }

    @PostMapping("/{roomId}/leave")
    fun leaveRoom(
        @PathVariable roomId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        roomService.removeParticipant(roomId, authentication.name)
        return ResponseEntity.ok(mapOf("roomId" to roomId, "participantCount" to roomService.getParticipantCount(roomId)))
    }

    @PostMapping("/{roomId}/close")
    fun closeRoom(
        @PathVariable roomId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = roomService.getRoom(roomId) ?: throw NoSuchElementException("Room not found")
        require(record.hostId == authentication.name) { "ONLY_HOST_CAN_CLOSE" }
        return ResponseEntity.ok(mapOf("roomId" to roomId, "closed" to roomService.closeRoom(roomId)))
    }

    @PostMapping("/{roomId}/invite")
    fun inviteMembers(
        @PathVariable roomId: String,
        @RequestBody request: InviteMembersRequest,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = roomService.getRoom(roomId)
            ?: throw NoSuchElementException("Room not found")
        val accountId = UUID.fromString(authentication.name)
        val inviter = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        require(record.hostId == authentication.name) { "ONLY_HOST_CAN_INVITE" }
        roomService.addInvitees(roomId, request.memberIds)
        val mode = if (record.isSpace) "SPACE" else "CONFERENCE"
        request.memberIds.filter { it.isNotBlank() && it != inviter.redId }.forEach { memberId ->
            notifications.sendVoipPushNotification(memberId, inviter.redId, roomId, mode)
            callSignaling.deliverInvite(
                targetRedId = memberId,
                type = "CONFERENCE_INVITE",
                roomId = roomId,
                sourceRedId = inviter.redId,
                mode = mode,
                payload = mapOf(
                    "title" to record.title,
                    "inviter" to inviter.displayName,
                    "video" to (!record.isSpace).toString()
                )
            )
        }
        return ResponseEntity.ok(mapOf("status" to "invited", "invitedCount" to request.memberIds.size, "roomId" to roomId))
    }
}

data class CreateRoomRequest(
    val roomId: String = "",
    val title: String = "",
    val isSpace: Boolean = false,
    val isPrivate: Boolean = false,
    val password: String? = null,
    val inviteeRedIds: List<String> = emptyList()
)

data class ConferenceRoomResponse(
    val roomId: String,
    val title: String,
    val hostName: String,
    val hostRedId: String,
    val isSpace: Boolean,
    val isPrivate: Boolean,
    val participantCount: Int,
    val inviteLink: String
)

data class JoinRoomRequest(
    val password: String? = null
)

data class JoinRoomResponse(
    val authorized: Boolean,
    val roomId: String,
    val title: String = "",
    val isSpace: Boolean = false,
    val hostName: String = "",
    val errorMessage: String? = null
)

data class InviteMembersRequest(
    val memberIds: List<String> = emptyList()
)
