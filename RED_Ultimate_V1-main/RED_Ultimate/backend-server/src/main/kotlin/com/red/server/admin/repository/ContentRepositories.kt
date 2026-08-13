package com.red.server.admin.repository

import com.red.server.admin.model.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

// ━━━━━━━━━━━━━━━━ Polls ━━━━━━━━━━━━━━━━
interface PollRepository : JpaRepository<Poll, UUID> {
    @Query("SELECT p FROM Poll p WHERE p.creatorId = :creatorId ORDER BY p.createdAt DESC")
    fun findByCreatorIdOrderByCreatedAtDesc(@Param("creatorId") creatorId: UUID, pageable: Pageable): Page<Poll>

    @Query("SELECT p FROM Poll p WHERE p.status = :status ORDER BY p.createdAt DESC")
    fun findByStatusOrderByCreatedAtDesc(@Param("status") status: String, pageable: Pageable): Page<Poll>

    @Query("SELECT p FROM Poll p WHERE p.targetGroupId = :targetGroupId AND p.status = :status ORDER BY p.createdAt DESC")
    fun findByTargetGroupIdAndStatusOrderByCreatedAtDesc(@Param("targetGroupId") targetGroupId: UUID, @Param("status") status: String, pageable: Pageable): Page<Poll>

    @Query("SELECT p FROM Poll p WHERE p.targetUserId = :targetUserId ORDER BY p.createdAt DESC")
    fun findByTargetUserIdOrderByCreatedAtDesc(@Param("targetUserId") targetUserId: UUID, pageable: Pageable): Page<Poll>

    @Query("SELECT p FROM Poll p WHERE p.status = 'ACTIVE' AND p.endsAt > :now ORDER BY p.createdAt DESC")
    fun findActivePolls(@Param("now") now: Instant): List<Poll>

    @Query("SELECT COUNT(p) FROM Poll p WHERE p.status = 'ACTIVE'")
    fun countActive(): Long
}

interface PollOptionRepository : JpaRepository<PollOption, UUID> {
    fun findByPollIdOrderByOptionOrder(pollId: UUID): List<PollOption>
    fun findByPollId(pollId: UUID): List<PollOption>

    /** حذف مجمّع لكل خيارات استطلاع — بديل N+1 (findByPollId.forEach delete). */
    @Modifying
    @Query("DELETE FROM PollOption p WHERE p.pollId = :pollId")
    fun deleteAllByPollId(@Param("pollId") pollId: UUID)
}

interface PollVoteRepository : JpaRepository<PollVote, UUID> {
    fun findByPollId(pollId: UUID): List<PollVote>
    fun findByPollIdAndUserId(pollId: UUID, userId: UUID): List<PollVote>
    fun countByPollId(pollId: UUID): Long
    fun countByPollIdAndUserId(pollId: UUID, userId: UUID): Long

    /** حذف مجمّع لكل أصوات استطلاع — يمنع بقاء أصوات يتيمة عند حذف الاستطلاع. */
    @Modifying
    @Query("DELETE FROM PollVote v WHERE v.pollId = :pollId")
    fun deleteAllByPollId(@Param("pollId") pollId: UUID)
}

// ━━━━━━━━━━━━━━━━ Events ━━━━━━━━━━━━━━━━
interface EventRepository : JpaRepository<Event, UUID> {
    fun findByCreatorIdOrderByStartsAtDesc(creatorId: UUID, pageable: Pageable): Page<Event>
    fun findByStatusOrderByStartsAtDesc(status: String, pageable: Pageable): Page<Event>

    @Query("SELECT e FROM Event e WHERE e.status = 'SCHEDULED' AND e.startsAt > :now ORDER BY e.startsAt ASC")
    fun findUpcoming(@Param("now") now: Instant): List<Event>

    @Query("SELECT e FROM Event e WHERE e.status = 'LIVE' ORDER BY e.startsAt ASC")
    fun findLive(): List<Event>

    @Query("SELECT COUNT(e) FROM Event e WHERE e.status = 'SCHEDULED' AND e.startsAt > :now")
    fun countUpcoming(@Param("now") now: Instant): Long
}

interface EventAttendeeRepository : JpaRepository<EventAttendee, Long> {
    fun findByEventId(eventId: UUID): List<EventAttendee>
    fun findByEventIdAndUserId(eventId: UUID, userId: UUID): EventAttendee?
    fun findByUserIdOrderByRsvpAtDesc(userId: UUID, pageable: Pageable): Page<EventAttendee>
    fun countByEventIdAndRsvpStatus(eventId: UUID, rsvpStatus: String): Long
}

// ━━━━━━━━━━━━━━━━ Story Highlights ━━━━━━━━━━━━━━━━
interface StoryHighlightRepository : JpaRepository<StoryHighlight, UUID> {
    fun findByUserIdOrderByDisplayOrder(userId: UUID): List<StoryHighlight>
    fun findByVisibilityOrderByDisplayOrder(visibility: String): List<StoryHighlight>
}

// ━━━━━━━━━━━━━━━━ Hashtags ━━━━━━━━━━━━━━━━
interface HashtagRepository : JpaRepository<Hashtag, UUID> {
    fun findByTagName(tagName: String): Hashtag?

    @Query("SELECT h FROM Hashtag h WHERE h.isTrending = TRUE AND h.isBlocked = FALSE ORDER BY h.trendingScore DESC")
    fun findTrending(): List<Hashtag>

    @Query("SELECT h FROM Hashtag h WHERE h.isBlocked = FALSE ORDER BY h.usageCount DESC")
    fun findPopular(): List<Hashtag>

    @Query("SELECT h FROM Hashtag h WHERE LOWER(h.tagName) LIKE :query AND h.isBlocked = FALSE")
    fun searchByTagName(@Param("query") query: String, pageable: Pageable): Page<Hashtag>
}

interface HashtagFollowRepository : JpaRepository<HashtagFollow, Long> {
    fun findByUserId(userId: UUID): List<HashtagFollow>
    fun findByHashtagId(hashtagId: UUID): List<HashtagFollow>
    fun existsByUserIdAndHashtagId(userId: UUID, hashtagId: UUID): Boolean
}

// ━━━━━━━━━━━━━━━━ Saved Messages ━━━━━━━━━━━━━━━━
interface SavedMessageRepository : JpaRepository<SavedMessage, UUID> {
    fun findByUserIdOrderBySavedAtDesc(userId: UUID, pageable: Pageable): Page<SavedMessage>
    fun findByUserIdAndCollectionOrderBySavedAtDesc(userId: UUID, collection: String, pageable: Pageable): Page<SavedMessage>
    fun existsByUserIdAndMessageId(userId: UUID, messageId: UUID): Boolean
    fun deleteByUserIdAndMessageId(userId: UUID, messageId: UUID)
}

// ━━━━━━━━━━━━━━━━ Sticker Packs ━━━━━━━━━━━━━━━━
interface StickerPackRepository : JpaRepository<StickerPack, UUID> {
    fun findByIsPublishedOrderByCreatedAtDesc(isPublished: Boolean): List<StickerPack>
    fun findByIsOfficialOrderByCreatedAtDesc(isOfficial: Boolean): List<StickerPack>
    fun findByCreatorIdOrderByCreatedAtDesc(creatorId: UUID): List<StickerPack>
    fun findByIsPublishedAndIsOfficial(isPublished: Boolean, isOfficial: Boolean): List<StickerPack>
}

interface StickerRepository : JpaRepository<Sticker, UUID> {
    fun findByPackIdOrderByDisplayOrder(packId: UUID): List<Sticker>
}

interface UserStickerPackRepository : JpaRepository<UserStickerPack, UserStickerPackId> {
    fun findByUserIdOrderByInstalledAtDesc(userId: UUID): List<UserStickerPack>
    fun findByIdUserIdAndIdPackId(userId: UUID, packId: UUID): UserStickerPack?
}
