package com.red.server.websocket

import com.google.protobuf.ByteString
import com.red.server.database.MessageDocument
import com.red.server.database.RedisManager
import com.red.server.messaging.DeleteService
import com.red.server.messaging.MessageService
import com.red.server.services.AdminUserIntelligenceService
import com.red.server.websocket.ApprovedDeviceSessionGuard
import com.red.server.websocket.WebSocketRateLimiter
import com.red.sovereign.proto.RedProtos
import com.red.sovereign.proto.RedProtos.RemoteWipe
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.BinaryMessage
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.BinaryWebSocketHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class RedMasterHandler(
    private val messages: MessageService,
    private val deletes: DeleteService,
    private val redisManager: RedisManager,
    private val redis: StringRedisTemplate,
    private val userIntelligence: AdminUserIntelligenceService,
    private val accessGuard: ApprovedDeviceSessionGuard
) : BinaryWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
    private val frameLimiter = WebSocketRateLimiter(maxMessages = 120, windowMillis = 60_000)

    override fun handleBinaryMessage(session: WebSocketSession, frame: BinaryMessage) {
        if (!frameLimiter.tryAcquire(session.id) || !accessGuard.isStillAuthorized(
                session.attributes["accountId"] as? String,
                session.attributes["deviceId"] as? String
            )
        ) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        val envelope = runCatching { RedProtos.RedRED.parseFrom(frame.payload) }.getOrElse {
            session.close(CloseStatus.BAD_DATA)
            return
        }
        when (envelope.signalCase) {
            RedProtos.RedRED.SignalCase.MESSAGE -> receiveMessage(session, envelope.message)
            RedProtos.RedRED.SignalCase.ACK -> receiveAck(session, envelope.ack)
            RedProtos.RedRED.SignalCase.TYPING -> receiveTyping(session, envelope.typing)
            RedProtos.RedRED.SignalCase.SYNC_REQ -> sync(session, envelope.syncReq)
            RedProtos.RedRED.SignalCase.DELETE -> delete(session, envelope.delete)
            RedProtos.RedRED.SignalCase.REMOTE_WIPE_ACK -> userIntelligence.markRemoteWipeAcknowledged(
                UUID.fromString(session.attributes["accountId"] as String), envelope.remoteWipeAck.commandId
            )
            else -> Unit
        }
    }

    private fun receiveMessage(session: WebSocketSession, incoming: RedProtos.ChatMessage) {
        val sender = userId(session)
        require(incoming.senderId == sender) { "senderId does not match authenticated RED ID" }
        val stored = messages.processIncoming(incoming)
        send(session, ack(stored, "SENT"))
        sendToDevice(stored.receiverId, stored.receiverDeviceId, messageEnvelope(stored))
        // Synchronize the sender's other approved devices without echoing to this socket.
        sendToUser(sender, messageEnvelope(stored), exceptSessionId = session.id)
    }

    private fun receiveAck(session: WebSocketSession, incoming: RedProtos.MessageAck) {
        val recipient = userId(session)
        val deviceId = session.attributes["protocolDeviceId"] as? Int ?: error("Protocol device is missing")
        val stored = messages.acknowledge(recipient, deviceId, incoming.messageId, incoming.status)
        val ack = ack(stored, stored.status)
        sendToUser(stored.senderId, ack)
        sendToUser(stored.receiverId, ack, exceptSessionId = session.id)
    }

    private fun receiveTyping(session: WebSocketSession, typing: RedProtos.TypingRED) {
        val sender = userId(session)
        require(typing.userId == sender) { "userId does not match authenticated RED ID" }
        require(typing.targetUserId.isNotBlank() && typing.targetUserId != sender) { "targetUserId is required" }
        redisManager.setTyping(sender, typing.conversationId)
        sendToUser(typing.targetUserId, RedProtos.RedRED.newBuilder().setTyping(typing).build())
    }

    private fun sync(session: WebSocketSession, request: RedProtos.SyncRequest) {
        messages.getMissedMessages(userId(session), request.conversationId, request.fromSequence, request.toSequence)
            .forEach { send(session, messageEnvelope(it)) }
    }

    private fun delete(session: WebSocketSession, request: RedProtos.DeleteRED) {
        if (!request.forEveryone) return
        val original = deletes.deleteForEveryone(request.messageId, userId(session)) ?: return
        val envelope = RedProtos.RedRED.newBuilder().setDelete(request).build()
        sendToUser(original.senderId, envelope)
        sendToUser(original.receiverId, envelope)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = userId(session)
        sessions.computeIfAbsent(redId) { ConcurrentHashMap() }[session.id] = session
        val protocolDeviceId = session.attributes["protocolDeviceId"] as? Int
            ?: throw IllegalStateException("Messaging requires an approved protocol device")
        redis.opsForZSet().add("red:presence:index", redId, System.currentTimeMillis().toDouble())
        messages.pendingFor(redId, protocolDeviceId).forEach { send(session, messageEnvelope(it)) }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val redId = session.attributes["userId"] as? String ?: return
        sessions[redId]?.let { userSessions ->
            userSessions.remove(session.id)
            if (userSessions.isEmpty()) sessions.remove(redId, userSessions)
        }
    }

    private fun sendToDevice(redId: String, protocolDeviceId: Int, envelope: RedProtos.RedRED) {
        sessions[redId]?.values?.filter { it.isOpen && it.attributes["protocolDeviceId"] == protocolDeviceId }?.forEach { send(it, envelope) }
    }

    private fun sendToUser(redId: String, envelope: RedProtos.RedRED, exceptSessionId: String? = null) {
        sessions[redId]?.values?.filter { it.isOpen && it.id != exceptSessionId }?.forEach { send(it, envelope) }
    }

    private fun send(session: WebSocketSession, envelope: RedProtos.RedRED) {
        synchronized(session) {
            if (session.isOpen) session.sendMessage(BinaryMessage(envelope.toByteArray()))
        }
    }

    private fun messageEnvelope(message: MessageDocument): RedProtos.RedRED {
        val value = RedProtos.ChatMessage.newBuilder()
            .setId(message.uuid).setConversationId(message.conversationId)
            .setSenderId(message.senderId).setReceiverId(message.receiverId)
            .setPayload(ByteString.copyFrom(message.payload)).setTimestamp(message.createdAt.toEpochMilli())
            .setSequenceNumber(message.sequenceNumber).setType(message.messageType)
            .setSenderDeviceId(message.senderDeviceId).setReceiverDeviceId(message.receiverDeviceId)
            .setCiphertextType(message.ciphertextType).build()
        return RedProtos.RedRED.newBuilder().setMessage(value).build()
    }

    private fun ack(message: MessageDocument, status: String): RedProtos.RedRED = RedProtos.RedRED.newBuilder().setAck(
        RedProtos.MessageAck.newBuilder().setMessageId(message.uuid).setSequenceNumber(message.sequenceNumber).setStatus(status)
    ).build()

    private fun userId(session: WebSocketSession): String =
        session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")

    // Remote wipe command sent to target device
    fun sendRemoteWipe(redId: String, commandId: String, reason: String) {
        val wipeMessage = RedProtos.RedRED.newBuilder()
            .setRemoteWipe(RemoteWipe.newBuilder()
                .setCommandId(commandId)
                .setReason(reason)
                .build())
            .build()
        sendToUser(redId, wipeMessage)
    }
}
