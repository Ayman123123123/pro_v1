package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.security.JwtService
import com.red.server.groups.GroupRole
import com.red.server.groups.GroupService
import com.red.server.websocket.ConferenceWebSocketHandler
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Issues a short-lived, group-membership-bound capability for one mediasoup room. */
@RestController
@RequestMapping("/api/sfu/groups")
class SfuTicketController(
    private val users: UserAccountRepository,
    private val groups: GroupService,
    private val jwt: JwtService,
    private val activeCalls: ActiveCallRegistry,
    private val conferenceRooms: ConferenceRoomService,
    private val liveStreams: LiveStreamService,
    private val conferenceSignaling: ConferenceWebSocketHandler,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private val aliases: RoomAliasService? = null
) {
    @GetMapping("/{groupId}/ticket")
    fun issue(@PathVariable groupId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        // مسار تذكرة مجموعة: حل المستعار، والخام القديم يبقى مقبولاً.
        val effectiveGroupId = aliases?.resolve(groupId) ?: groupId.trim()
        require(effectiveGroupId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val groupRole = groups.roleFor(accountId, effectiveGroupId)
            ?: throw NoSuchElementException("Group membership not found")
        val canProduce = groupRole in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MEMBER)
        val ticket = jwt.issueSfuTicket(user, deviceId, effectiveGroupId, groupRole.name, canProduce)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, SFU_TICKET_EXPIRES_SECONDS, effectiveGroupId, groupRole.name, canProduce))
    }

    /** Conference / live rooms that are not a stored group still need a short SFU capability. */
    @GetMapping("/rooms/{roomId}/ticket")
    fun issueRoom(@PathVariable roomId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        // مسار تذكرة انضمام: حل المستعار إلى القانوني، مع سقوط للخام القديم.
        val effectiveRoomId = aliases?.resolve(roomId) ?: roomId.trim()
        require(effectiveRoomId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        val rawTrimmed = roomId.trim()
        // لا تذاكر لغرف عشوائية: الغرفة يجب أن تكون مؤتمراً/مساحة نشطة أو مكالمة جماعية مسجّلة.
        // هذا يمنع فحص الغرف واستنزاف موارد mediasoup عبر استدعاءات join مجهولة.
        val conferenceRoom = conferenceRooms.getRoom(effectiveRoomId)
            ?: conferenceRooms.getRoom(rawTrimmed)
            ?: if (RoomSeparationPolicy.kindOf(rawTrimmed) == RoomSeparationPolicy.RoomKind.LEGACY && RoomSeparationPolicy.isValidRoomId(rawTrimmed)) conferenceRooms.getRoom(RoomSeparationPolicy.PREFIX_CONF + rawTrimmed) else null
        val legitimateRoom = conferenceRoom != null || activeCalls.isActiveCall(effectiveRoomId) || activeCalls.isActiveCall(rawTrimmed)
        require(legitimateRoom) { "Room not open for SFU" }
        val canonicalRoomId = conferenceRoom?.roomId ?: effectiveRoomId
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        if (conferenceRoom != null) {
            require(conferenceRooms.canJoin(canonicalRoomId, authentication.name, user.redId)) { "Not authorized for this meeting" }
        }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        // LEGENDARY Phase 7: مساحات الصوت — المستمع تذكرة استهلاك فقط (يمنع نشر عميل معَدَّل
        // متجاوزاً بوابة PRODUCE الخاصة بالـ mesh)؛ مؤتمرات الفيديو والمكالمات الجماعية للجميع.
        val roomRole = if (conferenceRoom != null && conferenceRoom.isSpace) {
            when {
                conferenceRoom.hostId == authentication.name -> "HOST"
                else -> conferenceSignaling.getRole(canonicalRoomId, user.redId, authentication.name)
            }
        } else "MEMBER"
        val canProduce = conferenceRoom == null || !conferenceRoom.isSpace || roomRole in setOf("HOST", "CO_HOST", "SPEAKER")
        val ticket = jwt.issueSfuTicket(user, deviceId, canonicalRoomId, roomRole, canProduce = canProduce)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, SFU_TICKET_EXPIRES_SECONDS, canonicalRoomId, roomRole, canProduce))
    }

    /**
     * LIVE requires an explicit REST join before a viewer can obtain a media
     * capability. The broadcaster and approved co-hosts (max 4) receive producer permission.
     */
    @GetMapping("/live/{streamId}/ticket")
    fun issueLive(@PathVariable streamId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        // مسار تذكرة بث: حل المستعار إلى القانوني، مع سقوط للخام القديم.
        val effectiveStreamId = aliases?.resolve(streamId) ?: streamId.trim()
        require(effectiveStreamId.matches(ROOM_ID)) { "Invalid live stream ID" }
        val rawTrimmed = streamId.trim()
        val record = liveStreams.getStreamRecord(effectiveStreamId)
            ?: liveStreams.getStreamRecord(rawTrimmed)
            ?: if (RoomSeparationPolicy.kindOf(rawTrimmed) == RoomSeparationPolicy.RoomKind.LEGACY && RoomSeparationPolicy.isValidRoomId(rawTrimmed)) liveStreams.getStreamRecord(RoomSeparationPolicy.PREFIX_LIVE + rawTrimmed) else null
            ?: throw NoSuchElementException("Live stream not found or ended")
        val canonicalStreamId = record.streamId
        val accountId = UUID.fromString(authentication.name)
        val accountIdText = accountId.toString()
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val isBroadcaster = record.broadcasterId == accountIdText
        require(isBroadcaster || liveStreams.isViewerAny(canonicalStreamId, user.redId, accountIdText) || liveStreams.isViewerAny(rawTrimmed, user.redId, accountIdText)) {
            "Join the live stream before requesting media access"
        }
        // LEGENDARY Phase 6: approved co-hosts (max 4) publish to the SFU room too —
        // match either id form since approval targets arrive as redId.
        val isCoHost = !isBroadcaster && (liveStreams.getCoHosts(canonicalStreamId) + liveStreams.getCoHosts(rawTrimmed)).any { it == accountIdText || it == user.redId }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val ticket = jwt.issueSfuTicket(
            user = user,
            deviceId = deviceId,
            groupId = canonicalStreamId,
            groupRole = if (isBroadcaster) "BROADCASTER" else if (isCoHost) "COHOST" else "VIEWER",
            canProduce = isBroadcaster || isCoHost
        )
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, SFU_TICKET_EXPIRES_SECONDS, canonicalStreamId, if (isBroadcaster) "BROADCASTER" else if (isCoHost) "COHOST" else "VIEWER", isBroadcaster || isCoHost))
    }

    companion object {
        /** معرف الغرفة موحّد مع SFU (4..128) ومع ConferenceWebSocketHandler — كان 8..128 فيسبب فشل الغرف القصيرة. */
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")
        /** يطابق JwtService.issueSfuTicket (10 دقائق) — كان 120 فيسبب تجديداً مبكراً خاطئاً. */
        const val SFU_TICKET_EXPIRES_SECONDS = 600L
    }
}

data class SfuTicketResponse(
    val token: String,
    val expiresInSeconds: Long,
    val roomId: String,
    val role: String,
    val canProduce: Boolean
)
