package com.red.server.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.auth.repository.SqlLike
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 📊 Content Service - خدمات المحتوى (Polls, Events, Stickers, Hashtags)
 */
@Service
class ContentService(
    private val polls: PollRepository,
    private val pollOptions: PollOptionRepository,
    private val pollVotes: PollVoteRepository,
    private val events: EventRepository,
    private val eventAttendees: EventAttendeeRepository,
    private val highlights: StoryHighlightRepository,
    private val hashtags: HashtagRepository,
    private val hashtagFollows: HashtagFollowRepository,
    private val savedMessages: SavedMessageRepository,
    private val stickerPacks: StickerPackRepository,
    private val stickers: StickerRepository,
    private val userStickerPacks: UserStickerPackRepository,
    private val json: ObjectMapper
) {
    // ━━━━━━━━━━━━━━━━ Polls ━━━━━━━━━━━━━━━━
    fun getPolls(status: String? = null, pageable: Pageable): Page<Poll> {
        return if (status != null) polls.findByStatusOrderByCreatedAtDesc(status, pageable)
        else polls.findAll(pageable)
    }

    fun getActivePolls(): List<Poll> = polls.findActivePolls(Instant.now())

    @Transactional
    fun createPoll(
        creatorId: UUID,
        question: String,
        options: List<String>,
        pollType: String = "SINGLE_CHOICE",
        isAnonymous: Boolean = false,
        allowAddOptions: Boolean = false,
        endsAt: Instant? = null,
        targetType: String = "GLOBAL",
        targetGroupId: UUID? = null,
        targetUserId: UUID? = null
    ): Poll {
        val poll = polls.save(Poll(
            creatorId = creatorId,
            question = question,
            pollType = pollType,
            isAnonymous = isAnonymous,
            allowAddOptions = allowAddOptions,
            endsAt = endsAt,
            targetType = targetType,
            targetGroupId = targetGroupId,
            targetUserId = targetUserId
        ))
        options.forEachIndexed { idx, text ->
            pollOptions.save(PollOption(
                pollId = poll.id, optionText = text, optionOrder = idx
            ))
        }
        return poll
    }

    @Transactional
    fun vote(pollId: UUID, userId: UUID, optionIds: List<UUID>) {
        require(optionIds.isNotEmpty()) { "NO_OPTIONS" }
        val poll = polls.findById(pollId).orElse(null)
            ?: throw NoSuchElementException("POLL_NOT_FOUND")
        if (poll.status != "ACTIVE") throw IllegalStateException("POLL_NOT_ACTIVE")
        val pollEndsAt = poll.endsAt // local copy — mutable var cannot smart-cast
        if (pollEndsAt != null && pollEndsAt.isBefore(Instant.now()))
            throw IllegalStateException("POLL_ENDED")

        // تحقق أن كل خيار ينتمي لهذا الاستطلاع — يمنع التصويت بخيار من استطلاع آخر
        val validOptionIds = pollOptions.findByPollId(pollId).map { it.id }.toSet()
        val invalid = optionIds.filter { it !in validOptionIds }
        require(invalid.isEmpty()) { "INVALID_OPTION" }

        // Check if user already voted — رفض صريح بدل العودة الصامتة
        val existing = pollVotes.countByPollIdAndUserId(pollId, userId)
        if (existing > 0) throw IllegalStateException("ALREADY_VOTED")

        optionIds.forEach { optionId ->
            pollVotes.save(PollVote(pollId = pollId, optionId = optionId, userId = userId))
            pollOptions.findById(optionId).ifPresent { option ->
                option.voteCount += 1
                pollOptions.save(option)
            }
        }
        poll.totalVotes += optionIds.size
        poll.uniqueVoters += 1
        polls.save(poll)
    }

    @Transactional
    fun closePoll(pollId: UUID) {
        polls.findById(pollId).ifPresent { poll ->
            poll.status = "CLOSED"
            polls.save(poll)
        }
    }

    @Transactional
    fun deletePoll(pollId: UUID): Boolean {
        return if (polls.existsById(pollId)) {
            // حذف مجمّع (بديل N+1) + حذف الأصوات لمنع اليتم
            pollOptions.deleteAllByPollId(pollId)
            pollVotes.deleteAllByPollId(pollId)
            polls.deleteById(pollId); true
        } else false
    }

    fun getPollResults(pollId: UUID): Map<String, Any> {
        val poll = polls.findById(pollId).orElse(null) ?: return emptyMap()
        val options = pollOptions.findByPollIdOrderByOptionOrder(pollId)
        return mapOf(
            "poll" to poll,
            "options" to options.map { mapOf(
                "id" to it.id,
                "text" to it.optionText,
                "votes" to it.voteCount,
                "percentage" to if (poll.totalVotes > 0) (it.voteCount.toDouble() / poll.totalVotes * 100) else 0.0
            )}
        )
    }

    // ━━━━━━━━━━━━━━━━ Events ━━━━━━━━━━━━━━━━
    fun getEvents(status: String? = null, pageable: Pageable): Page<Event> {
        return if (status != null) events.findByStatusOrderByStartsAtDesc(status, pageable)
        else events.findAll(pageable)
    }

    fun getUpcomingEvents(): List<Event> = events.findUpcoming(Instant.now())
    fun getLiveEvents(): List<Event> = events.findLive()

    /** تفاصيل فعالية واحدة — يُستدعى من تطبيق المستخدم لعرض صفحة الفعالية قبل RSVP. */
    fun getEvent(eventId: UUID): Event? = events.findById(eventId).orElse(null)

    @Transactional
    fun createEvent(
        creatorId: UUID,
        title: String,
        description: String? = null,
        locationName: String? = null,
        locationAddress: String? = null,
        startsAt: Instant,
        endsAt: Instant? = null,
        eventType: String = "MEETING",
        visibility: String = "PUBLIC",
        maxAttendees: Int? = null,
        rsvpEnabled: Boolean = true,
        rsvpDeadline: Instant? = null
    ): Event {
        return events.save(Event(
            creatorId = creatorId, title = title, description = description,
            locationName = locationName, locationAddress = locationAddress,
            startsAt = startsAt, endsAt = endsAt, eventType = eventType,
            visibility = visibility, maxAttendees = maxAttendees,
            rsvpEnabled = rsvpEnabled, rsvpDeadline = rsvpDeadline
        ))
    }

    @Transactional
    fun rsvp(eventId: UUID, userId: UUID, status: String = "GOING"): EventAttendee? {
        val event = events.findById(eventId).orElse(null) ?: return null
        if (event.status != "SCHEDULED" && event.status != "LIVE") return null
        val rsvpDeadline = event.rsvpDeadline // نسخة محلية — الخاصية قابلة للتعديل فلا smart-cast
        if (rsvpDeadline != null && rsvpDeadline.isBefore(Instant.now())) return null
        val maxAttendees = event.maxAttendees // local copy — mutable var
        if (maxAttendees != null && event.currentAttendees >= maxAttendees &&
            status == "GOING") return null

        val existing = eventAttendees.findByEventIdAndUserId(eventId, userId)
        return if (existing != null) {
            existing.rsvpStatus = status
            eventAttendees.save(existing)
        } else {
            val attendee = eventAttendees.save(EventAttendee(
                eventId = eventId, userId = userId, rsvpStatus = status
            ))
            if (status == "GOING") {
                event.currentAttendees += 1
                events.save(event)
            }
            attendee
        }
    }

    @Transactional
    fun checkInAttendee(eventId: UUID, userId: UUID): Boolean {
        val attendee = eventAttendees.findByEventIdAndUserId(eventId, userId) ?: return false
        attendee.checkedInAt = Instant.now()
        eventAttendees.save(attendee)
        return true
    }

    @Transactional
    fun cancelEvent(eventId: UUID, reason: String): Boolean {
        val event = events.findById(eventId).orElse(null) ?: return false
        event.status = "CANCELLED"
        event.cancelledAt = Instant.now()
        event.cancellationReason = reason
        events.save(event)
        return true
    }

    @Transactional
    fun deleteEvent(eventId: UUID): Boolean {
        return if (events.existsById(eventId)) {
            events.deleteById(eventId); true
        } else false
    }

    // ━━━━━━━━━━━━━━━━ Hashtags ━━━━━━━━━━━━━━━━
    fun getTrendingHashtags(limit: Int = 50): List<Hashtag> =
        hashtags.findTrending().take(limit)

    fun getPopularHashtags(limit: Int = 50): List<Hashtag> =
        hashtags.findPopular().take(limit)

    fun searchHashtags(query: String, pageable: Pageable): Page<Hashtag> =
        hashtags.searchByTagName(SqlLike.contains(query), pageable)

    @Transactional
    fun blockHashtag(hashtagId: UUID, reason: String, adminId: UUID) {
        hashtags.findById(hashtagId).ifPresent { hashtag ->
            hashtag.isBlocked = true
            hashtag.blockedReason = reason
            hashtag.blockedBy = adminId
            hashtags.save(hashtag)
        }
    }

    @Transactional
    fun unblockHashtag(hashtagId: UUID) {
        hashtags.findById(hashtagId).ifPresent { hashtag ->
            hashtag.isBlocked = false
            hashtag.blockedReason = null
            hashtag.blockedBy = null
            hashtags.save(hashtag)
        }
    }

    @Transactional
    fun followHashtag(hashtagId: UUID, userId: UUID): Boolean {
        if (hashtagFollows.existsByUserIdAndHashtagId(userId, hashtagId)) return false
        hashtagFollows.save(HashtagFollow(hashtagId = hashtagId, userId = userId))
        hashtags.findById(hashtagId).ifPresent { it.uniqueUsers += 1; hashtags.save(it) }
        return true
    }

    @Transactional
    fun unfollowHashtag(hashtagId: UUID, userId: UUID): Boolean {
        val follows = hashtagFollows.findByHashtagId(hashtagId).filter { it.userId == userId }
        if (follows.isEmpty()) return false
        hashtagFollows.deleteAll(follows)
        hashtags.findById(hashtagId).ifPresent { it.uniqueUsers = (it.uniqueUsers - 1).coerceAtLeast(0); hashtags.save(it) }
        return true
    }

    // ━━━━━━━━━━━━━━━━ Story Highlights ━━━━━━━━━━━━━━━━
    fun getUserHighlights(userId: UUID): List<StoryHighlight> =
        highlights.findByUserIdOrderByDisplayOrder(userId)

    @Transactional
    fun createHighlight(
        userId: UUID,
        title: String,
        coverMediaKey: String,
        visibility: String = "EVERYONE"
    ): StoryHighlight {
        val order = highlights.findByUserIdOrderByDisplayOrder(userId).size
        return highlights.save(StoryHighlight(
            userId = userId, title = title, coverMediaKey = coverMediaKey,
            visibility = visibility, displayOrder = order
        ))
    }

    @Transactional
    fun deleteHighlight(highlightId: UUID): Boolean {
        return if (highlights.existsById(highlightId)) {
            highlights.deleteById(highlightId); true
        } else false
    }

    // ━━━━━━━━━━━━━━━━ Saved Messages ━━━━━━━━━━━━━━━━
    fun getSavedMessages(userId: UUID, collection: String? = null, pageable: Pageable): Page<SavedMessage> {
        return if (collection != null) {
            savedMessages.findByUserIdAndCollectionOrderBySavedAtDesc(userId, collection, pageable)
        } else {
            savedMessages.findByUserIdOrderBySavedAtDesc(userId, pageable)
        }
    }

    @Transactional
    fun saveMessage(userId: UUID, messageId: UUID, collection: String = "DEFAULT", notes: String? = null): SavedMessage? {
        if (savedMessages.existsByUserIdAndMessageId(userId, messageId)) return null
        return savedMessages.save(SavedMessage(
            userId = userId, messageId = messageId, collection = collection, notes = notes
        ))
    }

    @Transactional
    fun unsaveMessage(userId: UUID, messageId: UUID): Boolean {
        val count = savedMessages.count()
        savedMessages.deleteByUserIdAndMessageId(userId, messageId)
        return true
    }

    // ━━━━━━━━━━━━━━━━ Sticker Packs ━━━━━━━━━━━━━━━━
    fun getOfficialStickerPacks(): List<StickerPack> =
        stickerPacks.findByIsPublishedAndIsOfficial(true, true)

    fun getAllStickerPacks(): List<StickerPack> =
        stickerPacks.findByIsPublishedOrderByCreatedAtDesc(true)

    @Transactional
    fun createStickerPack(
        name: String,
        description: String?,
        coverMediaKey: String,
        creatorId: UUID? = null,
        isOfficial: Boolean = false,
        isFree: Boolean = true,
        priceCents: Int = 0
    ): StickerPack {
        return stickerPacks.save(StickerPack(
            name = name, description = description,
            coverMediaKey = coverMediaKey, creatorId = creatorId,
            isOfficial = isOfficial, isFree = isFree, priceCents = priceCents
        ))
    }

    @Transactional
    fun publishStickerPack(packId: UUID): StickerPack? {
        val pack = stickerPacks.findById(packId).orElse(null) ?: return null
        pack.isPublished = true
        return stickerPacks.save(pack)
    }

    @Transactional
    fun deleteStickerPack(packId: UUID): Boolean {
        return if (stickerPacks.existsById(packId)) {
            stickerPacks.deleteById(packId); true
        } else false
    }

    // ─── الملصقات للمستخدم (قراءة + تثبيت) ───────────────────────────────

    /** الحزم المنشورة المتاحة لكل المستخدمين (المشتركين والمجانية). */
    fun getPublishedStickerPacks(): List<StickerPack> =
        stickerPacks.findByIsPublishedOrderByCreatedAtDesc(true)

    /** الملصقات الفردية داخل حزمة (مرتبة بـ display_order). */
    fun getStickersInPack(packId: UUID): List<Sticker> =
        stickers.findByPackIdOrderByDisplayOrder(packId)

    /** يثبّت مستخدم حزمة ملصقات (تظهر في منتقاه). */
    @Transactional
    fun installStickerPack(userId: UUID, packId: UUID): UserStickerPack {
        require(stickerPacks.existsById(packId)) { "STICKER_PACK_NOT_FOUND" }
        // إن كان مثبّتاً مسبقاً نُرجعه دون تكرار
        userStickerPacks.findByIdUserIdAndIdPackId(userId, packId)?.let { return it }
        val installed = UserStickerPack(userId = userId, packId = packId, installedAt = Instant.now())
        return userStickerPacks.save(installed)
    }

    /** يُلغي تثبيت حزمة. */
    @Transactional
    fun uninstallStickerPack(userId: UUID, packId: UUID): Boolean {
        val existing = userStickerPacks.findByIdUserIdAndIdPackId(userId, packId) ?: return false
        userStickerPacks.delete(existing)
        return true
    }

    /** حزم المستخدم المثبّتة. */
    fun getInstalledStickerPacks(userId: UUID): List<StickerPack> {
        val packIds = userStickerPacks.findByUserIdOrderByInstalledAtDesc(userId).map { it.packId }
        return stickerPacks.findAllById(packIds)
    }
}
