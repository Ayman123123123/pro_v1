package com.red.server.calls

import com.red.server.social.UuidV7
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CallHistoryService(private val mongo: MongoTemplate) {
    fun start(initiator: String, target: String, targetLabel: String, type: CallType, route: CallRoute, requestedId: String? = null): CallHistoryDocument {
        val id = requestedId?.takeIf { it.isNotBlank() } ?: UuidV7.next()
        mongo.findById(id, CallHistoryDocument::class.java)?.let { return it }
        return mongo.save(CallHistoryDocument(id, initiator, target, targetLabel, type, route, CallStatus.RINGING))
    }

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
