package com.red.server.calls

import com.red.server.social.UuidV7
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class CallHistoryService(
    private val mongo: MongoTemplate,
    private val publisher: CallEventPublisher
) {
    fun start(initiator: String, target: String, targetLabel: String, type: CallType, route: CallRoute, requestedId: String? = null): CallHistoryDocument {
        val id = requestedId?.takeIf { it.isNotBlank() } ?: UuidV7.next()
        mongo.findById(id, CallHistoryDocument::class.java)?.let { existing ->
            require(existing.initiatorId == initiator && existing.targetId == target) {
                "Call id reuse cannot redirect an existing call"
            }
            return existing
        }
        val doc = mongo.save(CallHistoryDocument(id, initiator, target, targetLabel, type, route, CallStatus.RINGING))
        publisher.callStarted(id, initiator, target, type.name, route.name)
        return doc
    }

    fun answer(callId: String, actorId: String? = null): CallHistoryDocument = update(callId) {
        if (actorId != null) require(it.targetId == actorId) { "Only the called account can answer" }
        require(it.status == CallStatus.RINGING) { "Call is not ringing" }
        it.status = CallStatus.ACTIVE
        it.answeredAt = Instant.now()
        publisher.callAnswered(callId)
    }

    fun end(callId: String, actorId: String? = null, failed: Boolean = false): CallHistoryDocument = update(callId) {
        if (actorId != null) {
            require(it.initiatorId == actorId || it.targetId == actorId) { "Only call participants can end" }
        }
        val now = Instant.now()
        it.endedAt = now
        if (failed) {
            it.status = CallStatus.FAILED
            publisher.callEnded(callId, 0L, "FAILED")
            return@update
        }
        if (it.answeredAt == null || it.status == CallStatus.RINGING) {
            it.status = CallStatus.MISSED
            publisher.callMissed(callId)
            return@update
        }
        val durationMs = Duration.between(it.answeredAt, now).toMillis()
        it.durationSeconds = durationMs / 1000
        it.status = CallStatus.ENDED
        publisher.callEnded(callId, durationMs, "NORMAL")
    }

    fun missed(callId: String): CallHistoryDocument = update(callId) {
        it.status = CallStatus.MISSED
        it.endedAt = Instant.now()
        publisher.callMissed(callId)
    }

    fun findById(callId: String): CallHistoryDocument? = mongo.findById(callId, CallHistoryDocument::class.java)

    /** الطرف المُستدعى رفض المكالمة صراحةً — تُسجَّل REJECTED وليس MISSED. */
    fun rejected(callId: String, actorId: String? = null): CallHistoryDocument = update(callId) {
        if (actorId != null) require(it.targetId == actorId) { "Only the called account can reject" }
        it.status = CallStatus.REJECTED
        it.endedAt = Instant.now()
        publisher.callEnded(callId, 0L, "REJECTED")
    }

    /** الطرف المُستدعى في مكالمة نشطة بالفعل — تُسجَّل BUSY. */
    fun busy(callId: String): CallHistoryDocument = update(callId) {
        it.status = CallStatus.BUSY
        it.endedAt = Instant.now()
        publisher.callEnded(callId, 0L, "BUSY")
    }

    /** WhatsApp/Telegram: unanswered RINGING older than 45s becomes MISSED. */
    fun expireStaleRinging(olderThan: Duration = Duration.ofSeconds(45)): Int {
        val cutoff = Instant.now().minus(olderThan)
        val query = Query(
            Criteria.where("status").`is`(CallStatus.RINGING).and("startedAt").lt(cutoff)
        ).limit(200)
        val stale = mongo.find(query, CallHistoryDocument::class.java)
        stale.forEach { doc ->
            runCatching { missed(doc.id) }
        }
        return stale.size
    }

    fun history(redId: String, limit: Int): List<CallHistoryItem> {
        val query = Query(Criteria().orOperator(Criteria.where("initiatorId").`is`(redId), Criteria.where("targetId").`is`(redId)))
            .with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(limit.coerceIn(1, 100))
        return mongo.find(query, CallHistoryDocument::class.java).map { call ->
            val outgoing = call.initiatorId == redId
            CallHistoryItem(
                id = call.id,
                peerId = if (outgoing) call.targetId else call.initiatorId,
                peerLabel = if (outgoing) call.targetLabel else call.initiatorId,
                direction = if (outgoing) "OUTGOING" else "INCOMING",
                type = call.type,
                route = call.route,
                status = call.status,
                startedAt = call.startedAt,
                answeredAt = call.answeredAt,
                endedAt = call.endedAt,
                mediaServerId = call.mediaServerId,
                gatewayUsed = call.gatewayUsed,
                durationSeconds = call.durationSeconds,
                qualityScore = call.qualityScore,
                callSource = call.callSource,
                groupId = call.groupId,
                roomId = call.roomId,
                participantIds = call.participantIds,
                hadScreenShare = call.hadScreenShare,
                wasRecorded = call.wasRecorded
            )
        }
    }

    private fun update(id: String, action: (CallHistoryDocument) -> Unit): CallHistoryDocument {
        val doc = mongo.findById(id, CallHistoryDocument::class.java)
            ?: throw NoSuchElementException("Call not found")
        action(doc)
        return mongo.save(doc)
    }
}
