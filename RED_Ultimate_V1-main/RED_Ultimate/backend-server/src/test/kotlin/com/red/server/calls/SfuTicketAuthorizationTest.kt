package com.red.server.calls

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.security.JwtService
import com.red.server.groups.GroupService
import com.red.server.websocket.CallWebSocketHandler
import com.red.server.websocket.ConferenceWebSocketHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional
import java.util.UUID

/** Issuance is a *separate* gate from SFU JWT signature validation. */
class SfuTicketAuthorizationTest {
    private val jwtSecret = "access-only-test-secret-that-is-longer-than-thirty-two-characters"
    private val mediaSecret = "media-only-test-secret-that-is-longer-than-thirty-two-characters"
    private val jwt = JwtService(jwtSecret, 15, "red-sovereign", "red-app", mediaSecret)
    private val signer = SfuTicketSigner(mediaSecret, jwtSecret, "red-sovereign", "red-app")
    private val users: UserAccountRepository = mock()
    private val groups: GroupService = mock()
    private val activeCalls: ActiveCallRegistry = mock()
    private val conference = ConferenceRoomService(RoomPasswordHasher())
    private val live: LiveStreamService = mock()
    private val signaling = ConferenceWebSocketHandler(jacksonObjectMapper(), conferenceRooms = conference)
    private val callSignaling: CallWebSocketHandler = mock()
    private val controller = SfuTicketController(users, groups, jwt, signer, activeCalls, conference, live,
        signaling, callSignaling = callSignaling)
    private val host = UserAccount(id = UUID.randomUUID(), redId = "73066", username = "host", status = AccountStatus.APPROVED)
    private val guest = UserAccount(id = UUID.randomUUID(), redId = "28261", username = "guest", status = AccountStatus.APPROVED)
    private val roomId = "CONF_media_auth_test"

    private fun authentication(user: UserAccount): Authentication = mock<Authentication>().also {
        whenever(it.name).thenReturn(user.id.toString())
        whenever(it.credentials).thenReturn(jwt.issue(user, UUID.randomUUID()))
    }

    private fun forbidden(room: String, who: UserAccount) {
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.issueRoom(room, authentication(who))
        }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
    }

    @Test
    fun `REST room must be joined and lobby admitted before ticket issuance`() {
        conference.createRoom(roomId, host.id.toString(), host.displayName, host.redId,
            "Room", false, true, null)
        whenever(users.findById(host.id)).thenReturn(Optional.of(host))
        whenever(users.findById(guest.id)).thenReturn(Optional.of(guest))
        forbidden(roomId, host) // creating a room does not itself allocate a media seat
        forbidden(roomId, guest)
        conference.addParticipant(roomId, host.id.toString())
        conference.updateRoomFlags(roomId, waitingRoom = true)
        conference.enterLobby(roomId, guest.id.toString(), guest.redId, guest.displayName, true)
        forbidden(roomId, guest)
        assertEquals(listOf(guest.id.toString()), conference.admitFromLobby(roomId, listOf(guest.id.toString())))
        val issued = controller.issueRoom(roomId, authentication(guest))
        assertEquals(HttpStatus.OK, issued.statusCode)
        assertEquals(roomId, issued.body!!.roomId)
        assertTrue(issued.body!!.canProduce)
        assertEquals("MEMBER", issued.body!!.role)
        assertTrue(issued.body!!.token.isNotBlank())
        assertTrue(conference.blockParticipant(roomId, guest.id.toString(), guest.redId))
        forbidden(roomId, guest) // no new ticket after kick; issued tokens remain valid until expiry
    }

    @Test
    fun `knowing an active call ID never grants its SFU media capability`() {
        val callId = "GROUP_private_call"
        whenever(users.findById(guest.id)).thenReturn(Optional.of(guest))
        whenever(activeCalls.isActiveCall(callId)).thenReturn(true)
        assertFalse(activeCalls.isParticipant(callId, guest.redId))
        forbidden(callId, guest)
        whenever(activeCalls.isParticipant(callId, guest.redId)).thenReturn(true)
        val issued = controller.issueRoom(callId, authentication(guest))
        assertEquals(callId, issued.body!!.roomId)
        assertTrue(issued.body!!.canProduce)
    }

    @Test
    fun `approved space listener is limited to consuming SFU media`() {
        val spaceId = "CONF_listen_only_test"
        conference.createRoom(spaceId, host.id.toString(), host.displayName, host.redId,
            "Space", true, false, null)
        conference.addParticipant(spaceId, guest.id.toString())
        whenever(users.findById(guest.id)).thenReturn(Optional.of(guest))
        val ticket = controller.issueRoom(spaceId, authentication(guest)).body!!
        assertEquals("LISTENER", ticket.role)
        assertFalse(ticket.canProduce)
        assertEquals(spaceId, ticket.roomId)
    }

    @Test
    fun `known group call ID does not grant media access to a non participant`() {
        val groupId = "FRND_private_sfu_test"
        whenever(users.findById(guest.id)).thenReturn(Optional.of(guest))
        whenever(callSignaling.groupCallHost(groupId)).thenReturn(host.redId)
        forbidden(groupId, guest)
        whenever(callSignaling.isGroupCallParticipant(groupId, guest.redId)).thenReturn(true)
        val ticket = controller.issueRoom(groupId, authentication(guest)).body!!
        assertEquals(groupId, ticket.roomId)
        assertTrue(ticket.canProduce)
    }

    @Test
    fun `unknown room cannot mint a ticket even for an authenticated device`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            controller.issueRoom("CONF_does_not_exist", authentication(guest))
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }
}
