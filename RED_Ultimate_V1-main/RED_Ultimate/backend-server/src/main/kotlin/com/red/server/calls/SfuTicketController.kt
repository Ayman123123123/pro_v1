package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.security.JwtService
import com.red.server.groups.GroupRole
import com.red.server.groups.GroupService
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
    private val liveStreams: LiveStreamService
) {
    @GetMapping("/{groupId}/ticket")
    fun issue(@PathVariable groupId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        require(groupId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val groupRole = groups.roleFor(accountId, groupId)
            ?: throw NoSuchElementException("Group membership not found")
        val canProduce = groupRole in setOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MEMBER)
        val ticket = jwt.issueSfuTicket(user, deviceId, groupId, groupRole.name, canProduce)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, 120, groupId, groupRole.name, canProduce))
    }

    /** Conference / live rooms that are not a stored group still need a short SFU capability. */
    @GetMapping("/rooms/{roomId}/ticket")
    fun issueRoom(@PathVariable roomId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        require(roomId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        // لا تذاكر لغرف عشوائية: الغرفة يجب أن تكون مؤتمراً/مساحة نشطة أو مكالمة جماعية مسجّلة.
        // هذا يمنع فحص الغرف واستنزاف موارد mediasoup عبر استدعاءات join مجهولة.
        val conferenceRoom = conferenceRooms.getRoom(roomId)
        val legitimateRoom = conferenceRoom != null || activeCalls.isActiveCall(roomId)
        require(legitimateRoom) { "Room not open for SFU" }
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        if (conferenceRoom != null) {
            require(conferenceRooms.canJoin(roomId, authentication.name, user.redId)) { "Not authorized for this meeting" }
        }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val ticket = jwt.issueSfuTicket(user, deviceId, roomId, "MEMBER", canProduce = true)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, 120, roomId, "MEMBER", true))
    }

    /**
     * LIVE requires an explicit REST join before a viewer can obtain a media
     * capability. The broadcaster alone receives producer permission.
     */
    @GetMapping("/live/{streamId}/ticket")
    fun issueLive(@PathVariable streamId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        require(streamId.matches(ROOM_ID)) { "Invalid live stream ID" }
        val record = liveStreams.getStreamRecord(streamId)
            ?: throw NoSuchElementException("Live stream not found or ended")
        val accountId = UUID.fromString(authentication.name)
        val accountIdText = accountId.toString()
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val isBroadcaster = record.broadcasterId == accountIdText
        require(isBroadcaster || liveStreams.isViewer(streamId, accountIdText)) {
            "Join the live stream before requesting media access"
        }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }
        val ticket = jwt.issueSfuTicket(
            user = user,
            deviceId = deviceId,
            groupId = streamId,
            groupRole = if (isBroadcaster) "BROADCASTER" else "VIEWER",
            canProduce = isBroadcaster
        )
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(SfuTicketResponse(ticket, 120, streamId, if (isBroadcaster) "BROADCASTER" else "VIEWER", isBroadcaster))
    }

    companion object {
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    }
}

data class SfuTicketResponse(
    val token: String,
    val expiresInSeconds: Long,
    val roomId: String,
    val role: String,
    val canProduce: Boolean
)
