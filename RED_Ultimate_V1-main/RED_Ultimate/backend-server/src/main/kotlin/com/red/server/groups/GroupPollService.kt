package com.red.server.groups

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

// LEGENDARY: استطلاعات المجموعات بتجميع خادم (كانت E2EE محلية بلا منع مزدوج/إغلاق/نتائج)
// التصويت: redId hash لمنع المزدوج + إخفاء اختياري + مهلة + إغلاق مشرف.

@Document("group_polls")
@CompoundIndex(name = "poll_group_created", def = "{'groupId': 1, 'createdAt': -1}")
data class GroupPollDocument(
    @Id val id: String = UUID.randomUUID().toString(),
    @Indexed val groupId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val createdBy: String = "",
    val allowMultiple: Boolean = false,
    val hideResults: Boolean = false,
    val closesAt: Instant? = null,
    var closed: Boolean = false,
    val createdAt: Instant = Instant.now()
)

@Document("group_poll_votes")
@CompoundIndex(name = "vote_poll_voter", def = "{'pollId': 1, 'voterHash': 1}", unique = true)
data class GroupPollVote(
    @Id val id: String = UUID.randomUUID().toString(),
    @Indexed val pollId: String = "",
    val voterHash: String = "",
    val optionIndexes: List<Int> = emptyList(),
    val createdAt: Instant = Instant.now()
) {
    companion object {
        operator fun invoke(pollId: String, voterHash: String, optionIndexes: List<Int>): GroupPollVote =
            GroupPollVote(id = "$pollId:$voterHash", pollId = pollId, voterHash = voterHash, optionIndexes = optionIndexes)
    }
}

data class CreateGroupPollRequest(
    val question: String = "",
    val options: List<String> = emptyList(),
    val allowMultiple: Boolean = false,
    val hideResults: Boolean = false,
    val closesInMinutes: Long? = null
)
data class VoteGroupPollRequest(val optionIndexes: List<Int> = emptyList())
data class GroupPollResult(
    val id: String, val question: String, val options: List<String>,
    val counts: List<Long>, val totalVoters: Long, val closed: Boolean,
    val closesAt: Instant?, val myVotes: List<Int> = emptyList()
)

@org.springframework.stereotype.Service
class GroupPollService(private val mongo: MongoTemplate) {
    private fun sha(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun requireMember(groupId: String, userId: UUID) {
        val m = mongo.findOne(
            Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMember::class.java
        ) ?: throw IllegalStateException("Must be group member")
    }

    private fun requireManager(groupId: String, userId: UUID) {
        val m = mongo.findOne(
            Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMember::class.java
        ) ?: throw IllegalStateException("Must be group member")
        require(m.role == GroupRole.OWNER || m.role == GroupRole.ADMIN) { "Insufficient group permission" }
    }

    fun create(actorId: UUID, groupId: String, req: CreateGroupPollRequest): GroupPollResult {
        requireMember(groupId, actorId)
        val q = req.question.trim()
        require(q.length in 2..300) { "POLL_QUESTION_INVALID" }
        val opts = req.options.map { it.trim() }.filter { it.isNotEmpty() }
        require(opts.size in 2..10) { "POLL_OPTIONS_INVALID" }
        require(opts.all { it.length <= 100 }) { "POLL_OPTION_TOO_LONG" }
        val closesAt = req.closesInMinutes?.let {
            require(it in 5..10080) { "POLL_CLOSE_INVALID" }
            Instant.now().plusSeconds(it * 60)
        }
        val doc = mongo.save(GroupPollDocument(
            groupId = groupId, question = q, options = opts,
            createdBy = actorId.toString(), allowMultiple = req.allowMultiple,
            hideResults = req.hideResults, closesAt = closesAt
        ))
        return results(actorId, doc.id)
    }

    fun vote(actorId: UUID, pollId: String, req: VoteGroupPollRequest): GroupPollResult {
        val poll = mongo.findById(pollId, GroupPollDocument::class.java)
            ?: throw NoSuchElementException("Poll not found")
        require(!poll.closed) { "POLL_CLOSED" }
        poll.closesAt?.let { require(Instant.now().isBefore(it)) { "POLL_CLOSED" } }
        requireMember(poll.groupId, actorId)
        require(req.optionIndexes.isNotEmpty()) { "POLL_VOTE_EMPTY" }
        require(req.optionIndexes.all { it in poll.options.indices }) { "POLL_OPTION_INVALID" }
        if (!poll.allowMultiple) require(req.optionIndexes.size == 1) { "POLL_SINGLE_ONLY" }
        val hash = sha("${poll.id}:${actorId}")
        // منع المزدوج: upsert — الثاني يستبدل الأول (تغيير رأي) بدل تكرار
        mongo.save(GroupPollVote(poll.id, hash, req.optionIndexes.distinct().sorted()))
        return results(actorId, poll.id)
    }

    fun close(actorId: UUID, pollId: String): GroupPollResult {
        val poll = mongo.findById(pollId, GroupPollDocument::class.java)
            ?: throw NoSuchElementException("Poll not found")
        val member = mongo.findOne(
            Query(Criteria.where("id").`is`("${poll.groupId}:$actorId")), GroupMember::class.java
        ) ?: throw IllegalStateException("Must be group member")
        val isManager = member.role == GroupRole.OWNER || member.role == GroupRole.ADMIN
        val isCreator = poll.createdBy == actorId.toString()
        require(isManager || isCreator) { "Insufficient group permission" }
        poll.closed = true
        mongo.save(poll)
        return results(actorId, poll.id)
    }

    fun results(actorId: UUID, pollId: String): GroupPollResult {
        val poll = mongo.findById(pollId, GroupPollDocument::class.java)
            ?: throw NoSuchElementException("Poll not found")
        requireMember(poll.groupId, actorId)
        val votes = mongo.find(Query(Criteria.where("pollId").`is`(poll.id)), GroupPollVote::class.java)
        val counts = MutableList(poll.options.size) { 0L }
        votes.forEach { v -> v.optionIndexes.forEach { i -> if (i in counts.indices) counts[i]++ } }
        val myHash = sha("${poll.id}:$actorId")
        val mine = votes.firstOrNull { it.voterHash == myHash }?.optionIndexes ?: emptyList()
        // إخفاء النتائج حتى الإغلاق/التصويت (واتساب 2026)
        val showCounts = !poll.hideResults || poll.closed || mine.isNotEmpty()
        return GroupPollResult(
            id = poll.id, question = poll.question, options = poll.options,
            counts = if (showCounts) counts else List(poll.options.size) { -1L },
            totalVoters = votes.size.toLong(), closed = poll.closed,
            closesAt = poll.closesAt, myVotes = mine
        )
    }

    fun list(actorId: UUID, groupId: String, limit: Int = 20): List<GroupPollResult> {
        requireMember(groupId, actorId)
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .limit(limit.coerceIn(1, 20)),
            GroupPollDocument::class.java
        ).map { results(actorId, it.id) }
    }
}

@RestController
@RequestMapping("/api/groups/{groupId}/polls")
class GroupPollController(private val polls: GroupPollService) {
    @PostMapping fun create(
        @PathVariable groupId: String, @RequestBody req: CreateGroupPollRequest, auth: Authentication
    ) = polls.create(UUID.fromString(auth.name), groupId, req)

    @GetMapping fun list(@PathVariable groupId: String, auth: Authentication) =
        polls.list(UUID.fromString(auth.name), groupId)

    @PostMapping("/{pollId}/vote") fun vote(
        @PathVariable groupId: String, @PathVariable pollId: String,
        @RequestBody req: VoteGroupPollRequest, auth: Authentication
    ) = polls.vote(UUID.fromString(auth.name), pollId, req)

    @PostMapping("/{pollId}/close") fun close(
        @PathVariable groupId: String, @PathVariable pollId: String, auth: Authentication
    ) = polls.close(UUID.fromString(auth.name), pollId)

    @GetMapping("/{pollId}") fun one(
        @PathVariable groupId: String, @PathVariable pollId: String, auth: Authentication
    ) = polls.results(UUID.fromString(auth.name), pollId)
}
