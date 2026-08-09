package com.red.server.calls

import com.red.server.social.UuidV7
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Authoritative state machine for RED call records.
 *
 * Every state transition is tied to a participant. WebSocket handlers must never trust a
 * client-supplied target or allow a third account to mutate a call it does not participate in.
 */
@Service
class CallHistoryService(private val mongo: MongoTemplate) {
    fun start(
        initiator: String,
        target: String,
        targetLabel: String,
        type: CallType,
        route: CallRoute,
        requestedId: String? = null
    ): CallHistoryDocument {
        require(initiator.isNotBlank() && target.isNotBlank() && initiator != target) { "A call requires two distinct participants" }
        val id = requestedId?.takeIf { it.isNotBlank() } ?: UuidV7.next()
        mongo.findById(id, CallHistoryDocument::class.java)?.let { existing ->
            require(
                existing.initiatorId == initiator && existing.targetId == target &&
                    existing.type == type && existing.route == route
            ) { "Call ID belongs to another call" }
            return existing
        }
        return mongo.save(CallHistoryDocument(id, initiator, target, targetLabel, type, route, CallStatus.RINGING))
    }

    /** Applies a signaling transition only after proving that [actor] is a participant. */
    fun authorizeSignal(callId: String, actor: String, signalType: String): CallHistoryDocument {
        val call = mongo.findById(callId, CallHistoryDocument::class.java)
            ?: throw NoSuchElementException("Call not found")
        require(actor == call.initiatorId || actor == call.targetId) { "Only a call participant may signal this call" }

        when (signalType.uppercase()) {
            "ANSWER" -> {
                require(actor == call.targetId) { "Only the called account may answer" }
                require(call.status == CallStatus.RINGING) { "Only a ringing call may be answered" }
                call.status = CallStatus.ACTIVE
                call.answeredAt = Instant.now()
            }
            "REJECT" -> {
                require(actor == call.targetId) { "Only the called account may reject" }
                require(call.status == CallStatus.RINGING) { "Only a ringing call may be rejected" }
                call.status = CallStatus.ENDED
                call.endedAt = Instant.now()
            }
            "END" -> {
                require(call.status == CallStatus.RINGING || call.status == CallStatus.ACTIVE) { "Call is already closed" }
                call.status = CallStatus.ENDED
                call.endedAt = Instant.now()
            }
            "ICE", "HOLD", "RESUME" -> {
                require(call.status == CallStatus.RINGING || call.status == CallStatus.ACTIVE) { "Call is not active" }
            }
            else -> throw IllegalArgumentException("Unsupported call signal type")
        }

        return if (signalType.equals("ICE", true) || signalType.equals("HOLD", true) || signalType.equals("RESUME", true)) call
        else mongo.save(call)
    }

    fun markMissed(callId: String, actor: String): CallHistoryDocument {
        val call = mongo.findById(callId, CallHistoryDocument::class.java)
            ?: throw NoSuchElementException("Call not found")
        require(actor == call.initiatorId) { "Only the initiator may mark an unavailable call missed" }
        if (call.status == CallStatus.RINGING) {
            call.status = CallStatus.MISSED
            call.endedAt = Instant.now()
            return mongo.save(call)
        }
        return call
    }

    fun peerFor(call: CallHistoryDocument, actor: String): String = when (actor) {
        call.initiatorId -> call.targetId
        call.targetId -> call.initiatorId
        else -> throw IllegalArgumentException("Actor is not a call participant")
    }

    // Legacy internal helpers retained for the PSTN service. New RED signaling uses authorizeSignal().
    fun answer(callId: String) = update(callId) { it.status = CallStatus.ACTIVE; it.answeredAt = Instant.now() }
    fun end(callId: String, failed: Boolean = false) = update(callId) { it.status = if (failed) CallStatus.FAILED else CallStatus.ENDED; it.endedAt = Instant.now() }
    fun missed(callId: String) = update(callId) { it.status = CallStatus.MISSED; it.endedAt = Instant.now() }

    fun history(redId: String, limit: Int): List<CallHistoryItem> {
        val query = Query(Criteria().orOperator(Criteria.where("initiatorId").`is`(redId), Criteria.where("targetId").`is`(redId)))
            .with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(limit.coerceIn(1, 100))
        return mongo.find(query, CallHistoryDocument::class.java).map { call ->
            val outgoing = call.initiatorId == redId
            CallHistoryItem(call.id, if (outgoing) call.targetId else call.initiatorId,
                if (outgoing) call.targetLabel else call.initiatorId, if (outgoing) "OUTGOING" else "INCOMING",
                call.type, call.route, call.status, call.startedAt, call.answeredAt, call.endedAt)
        }
    }

    private fun update(id: String, action: (CallHistoryDocument) -> Unit) {
        mongo.findById(id, CallHistoryDocument::class.java)?.let { action(it); mongo.save(it) }
    }
}
