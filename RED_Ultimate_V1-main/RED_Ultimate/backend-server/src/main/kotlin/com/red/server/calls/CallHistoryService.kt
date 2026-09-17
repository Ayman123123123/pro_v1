package com.red.server.calls

import com.red.server.social.UuidV7
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
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

    /**
     * Paged call history (newest first). [offset]/[since] are honoured by the Mongo
     * query itself so "load more" returns real older pages; [resolveName] upgrades
     * the stored peer label to the live display name when available.
     */
    fun history(
        redId: String,
        limit: Int,
        offset: Int = 0,
        since: Instant? = null,
        resolveName: (String) -> String? = { null }
    ): List<CallHistoryItem> {
        val party = Criteria().andOperator(
            Criteria().orOperator(
                Criteria.where("initiatorId").`is`(redId),
                Criteria.where("targetId").`is`(redId)
            ),
            // السجل المخفي لهذا المستخدم يُستبعد من سجله وحده؛ `$ne` يطابق المستندات
            // التي لا تحمل حقل hiddenFor أصلاً، فالصفوف القديمة تبقى ظاهرة كما هي.
            Criteria.where("hiddenFor").ne(redId)
        )
        val criteria = if (since != null) {
            Criteria().andOperator(party, Criteria.where("startedAt").lt(since))
        } else party
        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, "startedAt"))
            .skip(offset.coerceAtLeast(0).toLong())
            .limit(limit.coerceIn(1, 100))
        return mongo.find(query, CallHistoryDocument::class.java).map { call ->
            val outgoing = call.initiatorId == redId
            val peerId = if (outgoing) call.targetId else call.initiatorId
            CallHistoryItem(
                id = call.id,
                peerId = peerId,
                peerLabel = resolveName(peerId) ?: if (outgoing) call.targetLabel else call.initiatorId,
                direction = if (outgoing) "OUTGOING" else "INCOMING",
                type = call.type,
                route = call.route,
                status = call.status,
                startedAt = call.startedAt,
                answeredAt = call.answeredAt,
                endedAt = call.endedAt,
                mediaServerId = call.mediaServerId,
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

    /**
     * Syncs client-side call rows created while offline into the call_history
     * collection (upsert by id). Returns the number of rows stored. One malformed
     * row never fails the batch; rows for other users are skipped.
     */
    fun upsertFromSync(redId: String, rows: List<Map<String, Any?>>): Int {
        var stored = 0
        for (row in rows) {
            try {
                val id = (row["id"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                val peer = (row["peerId"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                val outgoing = (row["direction"] as? String)?.equals("OUTGOING", ignoreCase = true) ?: true
                val type = runCatching { CallType.valueOf(row["type"] as? String ?: "") }
                    .getOrDefault(CallType.AUDIO_1V1)
                val status = runCatching { CallStatus.valueOf(row["status"] as? String ?: "") }
                    .getOrDefault(CallStatus.ENDED)
                val startedAt = (row["startedAt"] as? Number)?.toLong()
                    ?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
                val existing = mongo.findById(id, CallHistoryDocument::class.java)
                if (existing != null) {
                    if (existing.initiatorId != redId && existing.targetId != redId) continue
                    existing.status = status
                    existing.endedAt = (row["endedAt"] as? Number)?.toLong()
                        ?.let { Instant.ofEpochMilli(it) } ?: existing.endedAt
                    existing.durationSeconds = (row["durationSeconds"] as? Number)?.toLong()
                        ?: existing.durationSeconds
                    mongo.save(existing)
                } else {
                    mongo.save(
                        CallHistoryDocument(
                            id = id,
                            initiatorId = if (outgoing) redId else peer,
                            targetId = if (outgoing) peer else redId,
                            targetLabel = (row["peerLabel"] as? String) ?: peer,
                            type = type,
                            route = CallRoute.RED,
                            status = status,
                            startedAt = startedAt
                        )
                    )
                }
                stored++
            } catch (_: Exception) {
                continue
            }
        }
        return stored
    }

    /**
     * «حذف» سجل مكالمة من سجل [redId] وحده — إخفاء لا محو.
     *
     * المستند مشترك بين الطرفين، فمحوه كان سيمحو سجل الطرف الآخر. نضيف معرّف المستدعي
     * إلى `hiddenFor` فيبقى الصف سليماً للطرف الآخر ويختفي من سجل المستدعي فقط.
     * ولا نخفي إلا ما يشارك فيه المستدعي فعلاً (initiator أو target) — فلا يُخفى سجل
     * مكالمة لا تخصه ولو خمّن معرّفها.
     */
    fun hideFor(redId: String, callIds: Collection<String>): Int {
        val ids = callIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return 0
        val query = Query(
            Criteria().andOperator(
                Criteria.where("_id").`in`(ids),
                Criteria().orOperator(
                    Criteria.where("initiatorId").`is`(redId),
                    Criteria.where("targetId").`is`(redId)
                )
            )
        )
        return mongo.updateMulti(query, Update().addToSet("hiddenFor", redId), CallHistoryDocument::class.java)
            .modifiedCount.toInt()
    }

    /** إخفاء كل سجل [redId] عنه وحده — لا يمس سجل الطرف الآخر. */
    fun hideAllFor(redId: String): Int {
        val query = Query(
            Criteria().orOperator(
                Criteria.where("initiatorId").`is`(redId),
                Criteria.where("targetId").`is`(redId)
            )
        )
        return mongo.updateMulti(query, Update().addToSet("hiddenFor", redId), CallHistoryDocument::class.java)
            .modifiedCount.toInt()
    }

    /**
     * مزامنة التدرج باستخدام الترقيم القائم على المؤشر (Cursor-based).
     * يستخدم cursor كإزاحة زمنية (startedAt) للصفحة التالية.
     */
    fun syncHistory(
        redId: String,
        cursor: String?,
        limit: Int,
        sinceVersion: Long,
        filter: CallHistoryFilter
    ): CallHistorySyncResponse {
        val party = Criteria().andOperator(
            Criteria().orOperator(
                Criteria.where("initiatorId").`is`(redId),
                Criteria.where("targetId").`is`(redId)
            ),
            Criteria.where("hiddenFor").ne(redId)
        )

        val criteria = cursor?.let { cursorInstant ->
            Criteria().andOperator(
                party,
                Criteria.where("startedAt").lt(cursorInstant)
            )
        } ?: party

        // Apply filters
        filter.types?.ifNotEmpty { criteria.and("type").`in`(it) }
        filter.directions?.ifNotEmpty { 
            // We can't easily filter by direction since it's derived
            // Skip for now, could be added with a direction field in the document
        }
        filter.statuses?.ifNotEmpty { criteria.and("status").`in`(it) }
        filter.dateFrom?.let { 
            runCatching { Instant.parse(it) }?.getOrNull()?.let { criteria.and("startedAt").gte(it) } 
        }
        filter.dateTo?.let { 
            runCatching { Instant.parse(it) }?.getOrNull()?.let { criteria.and("startedAt").lte(it) } 
        }

        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, "startedAt"))
            .limit(limit.coerceIn(1, 200).toLong() + 1) // +1 to check if there's more

        val docs = mongo.find(query, CallHistoryDocument::class.java)
        
        val hasMore = docs.size > limit
        val pageDocs = if (hasMore) docs.dropLast(1) else docs
        
        val nextCursor = if (hasMore && pageDocs.isNotEmpty()) {
            pageDocs.last().startedAt.toString()
        } else null

        val serverVersion = System.currentTimeMillis() // Simple version based on timestamp

        val items = pageDocs.map { call ->
            val outgoing = call.initiatorId == redId
            val peerId = if (outgoing) call.targetId else call.initiatorId
            CallHistoryItem(
                id = call.id,
                peerId = peerId,
                peerLabel = if (outgoing) call.targetLabel else call.initiatorId,
                direction = if (outgoing) "OUTGOING" else "INCOMING",
                type = call.type,
                route = call.route,
                status = call.status,
                startedAt = call.startedAt.toString(),
                answeredAt = call.answeredAt?.toString(),
                endedAt = call.endedAt?.toString(),
                mediaServerId = call.mediaServerId,
                durationSeconds = call.durationSeconds,
                qualityScore = call.qualityScore,
                callSource = call.callSource,
                groupId = call.groupId,
                roomId = call.roomId,
                participantIds = call.participantIds,
                hadScreenShare = call.hadScreenShare,
                wasRecorded = call.wasRecorded,
                version = call.version ?: 1,
                updatedAt = call.updatedAt?.toString()
            )
        }

        return CallHistorySyncResponse(
            items = items,
            nextCursor = nextCursor,
            hasMore = hasMore,
            serverVersion = serverVersion
        )
    }

    private fun update(id: String, action: (CallHistoryDocument) -> Unit): CallHistoryDocument {
        val doc = mongo.findById(id, CallHistoryDocument::class.java)
            ?: throw NoSuchElementException("Call not found: $id")
        action(doc)
        return mongo.save(doc)
    }
}
