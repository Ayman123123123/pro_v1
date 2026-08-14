package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.security.JwtService
import com.red.server.groups.GroupRole
import com.red.server.groups.GroupService
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
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
    private val conferenceRooms: ConferenceRoomService,
    private val liveStreams: LiveStreamService,
    private val jwt: JwtService
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
            .body(SfuTicketResponse(ticket, JwtService.SFU_TICKET_TTL_SECONDS, groupId, groupRole.name, canProduce))
    }

    /**
     * Capability for a REST-authorized conference or live stream participant.
     * Calling this endpoint is not a substitute for joining the room: private
     * passwords are verified by the corresponding join endpoint first.
     */
    @GetMapping("/rooms/{roomId}/ticket")
    fun issueRoom(@PathVariable roomId: String, authentication: Authentication): ResponseEntity<SfuTicketResponse> {
        require(roomId.matches(ROOM_ID)) { "Invalid SFU room ID" }
        val accountId = UUID.fromString(authentication.name)
        val accountIdText = accountId.toString()
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val accessToken = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        val deviceId = requireNotNull(jwt.deviceId(accessToken)) { "An approved device token is required" }

        val conference = conferenceRooms.getRoom(roomId)
        val stream = liveStreams.getStreamRecord(roomId)
        val authorization = when {
            conference != null && conference.hostId == accountIdText -> RoomAuthorization("HOST", canProduce = true)
            conference != null && conferenceRooms.isParticipant(roomId, accountIdText) -> RoomAuthorization("MEMBER", canProduce = true)
            stream != null && stream.broadcasterId == accountIdText -> RoomAuthorization("BROADCASTER", canProduce = true)
            stream != null && liveStreams.isViewer(roomId, accountIdText) -> RoomAuthorization("VIEWER", canProduce = false)
            conference == null && stream == null -> throw NoSuchElementException("SFU room not found")
            else -> throw AccessDeniedException("Join the room before requesting an SFU ticket")
        }

        val ticket = jwt.issueSfuTicket(user, deviceId, roomId, authorization.role, authorization.canProduce)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(
                SfuTicketResponse(
                    ticket,
                    JwtService.SFU_TICKET_TTL_SECONDS,
                    roomId,
                    authorization.role,
                    authorization.canProduce
                )
            )
    }

    companion object {
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    }
}

private data class RoomAuthorization(val role: String, val canProduce: Boolean)

data class SfuTicketResponse(
    val token: String,
    val expiresInSeconds: Long,
    val roomId: String,
    val role: String,
    val canProduce: Boolean
)
