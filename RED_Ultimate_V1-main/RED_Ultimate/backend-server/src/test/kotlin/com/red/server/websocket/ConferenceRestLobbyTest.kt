package com.red.server.websocket

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.ConferenceController
import com.red.server.calls.ConferenceRoomService
import com.red.server.calls.JoinRoomRequest
import com.red.server.calls.LobbyActionRequest
import com.red.server.calls.RoomPasswordHasher
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** REST, signaling and media membership must agree on who is in a room. */
class ConferenceRestLobbyTest {
    private val mapper = jacksonObjectMapper()
    private val service = ConferenceRoomService(RoomPasswordHasher())
    private val ws = ConferenceWebSocketHandler(mapper, conferenceRooms = service)
    private val users: UserAccountRepository = mock()
    private val controller = ConferenceController(service, users, mock<NotificationService>(),
        mock<CallHistoryService>(), mock<CallWebSocketHandler>(), ws)
    private val host = UserAccount(id = UUID.randomUUID(), redId = "73066", username = "host", displayName = "Host")
    private val guest = UserAccount(id = UUID.randomUUID(), redId = "28261", username = "guest", displayName = "Guest")
    private val roomId = "CONF_lobby_test_room"

    private class Probe(id: String, user: UserAccount) {
        val sent = CopyOnWriteArrayList<String>()
        val session: WebSocketSession = mock<WebSocketSession>().also { sock ->
            whenever(sock.id).thenReturn(id)
            whenever(sock.attributes).thenReturn(mutableMapOf<String, Any>(
                "userId" to user.redId, "accountId" to user.id.toString()
            ))
            whenever(sock.isOpen).thenReturn(true)
            doAnswer { invocation ->
                val message = invocation.getArgument<WebSocketMessage<*>>(0)
                if (message is TextMessage) sent.add(message.payload)
                null
            }.whenever(sock).sendMessage(any())
        }
    }

    private fun Probe.has(type: String, detail: String? = null) = sent.any {
        val frame = mapper.readTree(it)
        frame["type"]?.asString() == type &&
            (detail == null || frame["payload"]?.get("code")?.asString() == detail ||
                frame["payload"]?.get("state")?.asString() == detail)
    }

    private fun auth(user: UserAccount): Authentication = mock<Authentication>().also {
        whenever(it.name).thenReturn(user.id.toString())
    }

    private fun registerUsers() {
        whenever(users.findById(host.id)).thenReturn(Optional.of(host))
        whenever(users.findById(guest.id)).thenReturn(Optional.of(guest))
    }

    private fun createRoom(isPrivate: Boolean = false, lobby: Boolean = false, password: String? = null) {
        registerUsers()
        service.createRoom(roomId, host.id.toString(), host.displayName, host.redId,
            "Room", false, isPrivate, password)
        if (lobby) service.updateRoomFlags(roomId, waitingRoom = true)
    }

    private fun joinSocket(probe: Probe) =
        ws.handleTextMessage(probe.session, TextMessage("""{"type":"JOIN","roomId":"$roomId"}"""))

    @Test
    fun `REST join waits for approval and both waiting devices receive the admission`() {
        createRoom(lobby = true)
        assertEquals(HttpStatus.OK, controller.joinRoom(roomId, JoinRoomRequest(), auth(host)).statusCode)
        val hostSocket = Probe("host-ws", host)
        joinSocket(hostSocket)
        val response = controller.joinRoom(roomId, JoinRoomRequest(), auth(guest))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertTrue(response.body!!.waiting)
        assertFalse(response.body!!.authorized)
        assertFalse(service.isParticipant(roomId, guest.id.toString()), "No SFU ticket before approval")

        val phone = Probe("phone", guest)
        val tablet = Probe("tablet", guest)
        joinSocket(phone)
        joinSocket(tablet)
        assertTrue(phone.has("LOBBY", "waiting"))
        assertTrue(tablet.has("LOBBY", "waiting"))
        assertFalse(phone.has("ROOM_STATE"))
        assertFalse(tablet.has("ROOM_STATE"))
        assertEquals(1, service.lobbyCount(roomId)) // one seat requested for one account
        controller.admitLobby(roomId, LobbyActionRequest(listOf(guest.id.toString())), auth(host))
        assertTrue(phone.has("LOBBY", "admitted"))
        assertTrue(tablet.has("LOBBY", "admitted"))
        assertTrue(service.isParticipant(roomId, guest.id.toString()))

        joinSocket(phone)
        joinSocket(tablet)
        assertTrue(phone.has("ROOM_STATE"))
        assertTrue(tablet.has("ROOM_STATE"))
        assertEquals(2, service.getParticipantCount(roomId))
        hostSocket.sent.clear()
        ws.afterConnectionClosed(phone.session, CloseStatus.NORMAL)
        assertTrue(service.isParticipant(roomId, guest.id.toString()), "Other device still holds the seat")
        assertFalse(hostSocket.has("PARTICIPANT_LEFT"))
        ws.afterConnectionClosed(tablet.session, CloseStatus.NORMAL)
        assertFalse(service.isParticipant(roomId, guest.id.toString()))
        assertTrue(hostSocket.has("PARTICIPANT_LEFT"))
    }

    @Test
    fun `private room link may wait without an invite and a host kick blocks reentry`() {
        createRoom(isPrivate = true, lobby = true)
        controller.joinRoom(roomId, JoinRoomRequest(), auth(host))
        val hostSocket = Probe("host-ws", host)
        joinSocket(hostSocket)
        assertEquals(HttpStatus.FORBIDDEN, controller.joinRoom(roomId, JoinRoomRequest(), auth(guest)).statusCode)
        val token = service.createCallLink(roomId).token
        val waiting = controller.joinByLink(token, auth(guest))
        assertEquals(HttpStatus.ACCEPTED, waiting.statusCode)
        val guestSocket = Probe("guest-ws", guest)
        joinSocket(guestSocket)
        assertTrue(guestSocket.has("LOBBY", "waiting"))
        assertFalse(service.isParticipant(roomId, guest.id.toString()))
        controller.admitLobby(roomId, LobbyActionRequest(listOf(guest.id.toString())), auth(host))
        joinSocket(guestSocket)
        assertTrue(guestSocket.has("ROOM_STATE"))
        assertTrue(service.canJoin(roomId, guest.id.toString(), guest.redId),
            "An admitted private link guest may reconnect without becoming an invitee")
        ws.handleTextMessage(hostSocket.session, TextMessage("""{"type":"KICK_USER","roomId":"$roomId","payload":{"targetUserId":"${guest.redId}"}}"""))
        assertFalse(service.isParticipant(roomId, guest.id.toString()))
        assertFalse(service.canJoin(roomId, guest.id.toString(), guest.redId))
        assertEquals(HttpStatus.FORBIDDEN, controller.joinByLink(token, auth(guest)).statusCode)
        assertEquals(HttpStatus.FORBIDDEN, controller.joinRoom(roomId, JoinRoomRequest(), auth(guest)).statusCode)
        guestSocket.sent.clear()
        joinSocket(guestSocket)
        assertTrue(guestSocket.has("ERROR", "FORBIDDEN"))
        assertFalse(guestSocket.has("ROOM_STATE"))
    }

    @Test
    fun `private password admission survives a network drop without exposing the room`() {
        createRoom(isPrivate = true, password = "test-password-only")
        controller.joinRoom(roomId, JoinRoomRequest(), auth(host))
        assertEquals(HttpStatus.FORBIDDEN,
            controller.joinRoom(roomId, JoinRoomRequest(password = "wrong"), auth(guest)).statusCode)
        assertEquals(HttpStatus.OK,
            controller.joinRoom(roomId, JoinRoomRequest(password = "test-password-only"), auth(guest)).statusCode)
        val guestSocket = Probe("guest", guest)
        joinSocket(guestSocket)
        ws.afterConnectionClosed(guestSocket.session, CloseStatus.NORMAL)
        assertFalse(service.isParticipant(roomId, guest.id.toString()))
        assertEquals(HttpStatus.OK, controller.joinRoom(roomId, JoinRoomRequest(), auth(guest)).statusCode)
        val outsider = UserAccount(id = UUID.randomUUID(), redId = "99991", username = "outsider")
        whenever(users.findById(outsider.id)).thenReturn(Optional.of(outsider))
        assertEquals(HttpStatus.FORBIDDEN, controller.joinRoom(roomId, JoinRoomRequest(), auth(outsider)).statusCode)
    }

    @Test
    fun `private call link supports reconnect but a locked room rejects a new holder`() {
        createRoom(isPrivate = true)
        controller.joinRoom(roomId, JoinRoomRequest(), auth(host))
        val link = service.createCallLink(roomId).token
        assertEquals(HttpStatus.OK, controller.joinByLink(link, auth(guest)).statusCode)
        val guestSocket = Probe("guest", guest)
        joinSocket(guestSocket)
        ws.afterConnectionClosed(guestSocket.session, CloseStatus.NORMAL)
        assertEquals(HttpStatus.OK, controller.joinRoom(roomId, JoinRoomRequest(), auth(guest)).statusCode)
        service.setLocked(roomId, true)
        val outsider = UserAccount(id = UUID.randomUUID(), redId = "99991", username = "outsider")
        whenever(users.findById(outsider.id)).thenReturn(Optional.of(outsider))
        assertEquals(HttpStatus.LOCKED, controller.joinByLink(link, auth(outsider)).statusCode)
    }

    @Test
    fun `REST leave and host close revoke lingering websocket access`() {
        createRoom()
        controller.joinRoom(roomId, JoinRoomRequest(), auth(host))
        controller.joinRoom(roomId, JoinRoomRequest(), auth(guest))
        val hostSocket = Probe("host-ws", host)
        val guestSocket = Probe("guest-ws", guest)
        joinSocket(hostSocket)
        joinSocket(guestSocket)
        controller.leaveRoom(roomId, auth(guest))
        guestSocket.sent.clear()
        ws.handleTextMessage(guestSocket.session, TextMessage("""{"type":"PRODUCE","roomId":"$roomId"}"""))
        assertTrue(guestSocket.has("ERROR", "FORBIDDEN"))
        controller.closeRoom(roomId, auth(host))
        hostSocket.sent.clear()
        ws.handleTextMessage(hostSocket.session, TextMessage("""{"type":"PRODUCE","roomId":"$roomId"}"""))
        assertTrue(hostSocket.has("ERROR", "FORBIDDEN"))
    }
}
