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
            .body(SfuTicketResponse(ticket, 120, groupId, groupRole.name, canProduce))
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
