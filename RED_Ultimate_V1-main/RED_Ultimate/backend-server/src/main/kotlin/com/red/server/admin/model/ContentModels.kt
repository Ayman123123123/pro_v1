package com.red.server.admin.model

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

// ━━━━━━━━━━━━━━━━ Polls ━━━━━━━━━━━━━━━━
@Entity
@Table(name = "polls")
class Poll(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "creator_id", nullable = false)
    var creatorId: UUID = UUID.randomUUID(),

    @Column(nullable = false, columnDefinition = "TEXT")
    var question: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "poll_type", nullable = false, length = 20)
    var pollType: String = "SINGLE_CHOICE",

    @Column(name = "is_anonymous", nullable = false)
    var isAnonymous: Boolean = false,

    @Column(name = "allow_add_options", nullable = false)
    var allowAddOptions: Boolean = false,

    @Column(name = "show_results_before_vote", nullable = false)
    var showResultsBeforeVote: Boolean = false,

    @Column(name = "show_results_after_close", nullable = false)
    var showResultsAfterClose: Boolean = true,

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE",

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant = Instant.now(),

    @Column(name = "ends_at")
    var endsAt: Instant? = null,

    @Column(name = "target_type", nullable = false, length = 20)
    var targetType: String = "GLOBAL",

    @Column(name = "target_group_id")
    var targetGroupId: UUID? = null,

    @Column(name = "target_user_id")
    var targetUserId: UUID? = null,

    @Column(name = "total_votes", nullable = false)
    var totalVotes: Int = 0,

    @Column(name = "unique_voters", nullable = false)
    var uniqueVoters: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "poll_options")
class PollOption(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "poll_id", nullable = false)
    var pollId: UUID = UUID.randomUUID(),

    @Column(name = "option_text", nullable = false, length = 200)
    var optionText: String = "",

    @Column(name = "option_order", nullable = false)
    var optionOrder: Int = 0,

    @Column(name = "vote_count", nullable = false)
    var voteCount: Int = 0,

    @Column(length = 20)
    var color: String? = null,

    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null
)

@Entity
@Table(name = "poll_votes")
class PollVote(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "poll_id", nullable = false)
    var pollId: UUID = UUID.randomUUID(),

    @Column(name = "option_id", nullable = false)
    var optionId: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column
    var rank: Int? = null,

    @Column(name = "voted_at", nullable = false)
    var votedAt: Instant = Instant.now()
)

// ━━━━━━━━━━━━━━━━ Events ━━━━━━━━━━━━━━━━
@Entity
@Table(name = "events")
class Event(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "creator_id", nullable = false)
    var creatorId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 200)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "location_name", length = 200)
    var locationName: String? = null,

    @Column(name = "location_address", columnDefinition = "TEXT")
    var locationAddress: String? = null,

    @Column(name = "location_lat")
    var locationLat: Double? = null,

    @Column(name = "location_lng")
    var locationLng: Double? = null,

    @Column(name = "location_url", length = 500)
    var locationUrl: String? = null,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant = Instant.now(),

    @Column(name = "ends_at")
    var endsAt: Instant? = null,

    @Column(nullable = false, length = 50)
    var timezone: String = "Asia/Aden",

    @Column(name = "event_type", nullable = false, length = 30)
    var eventType: String = "MEETING",

    @Column(nullable = false, length = 20)
    var visibility: String = "PUBLIC",

    @Column(name = "max_attendees")
    var maxAttendees: Int? = null,

    @Column(name = "current_attendees", nullable = false)
    var currentAttendees: Int = 0,

    @Column(name = "waitlist_enabled", nullable = false)
    var waitlistEnabled: Boolean = false,

    @Column(nullable = false, length = 20)
    var status: String = "SCHEDULED",

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    var cancellationReason: String? = null,

    @Column(name = "cover_image_media_key", length = 200)
    var coverImageMediaKey: String? = null,

    @Column(name = "rsvp_enabled", nullable = false)
    var rsvpEnabled: Boolean = true,

    @Column(name = "rsvp_deadline")
    var rsvpDeadline: Instant? = null,

    @Column(name = "reminder_sent", nullable = false)
    var reminderSent: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "event_attendees")
class EventAttendee(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,

    @Column(name = "event_id", nullable = false)
    var eventId: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(name = "rsvp_status", nullable = false, length = 20)
    var rsvpStatus: String = "GOING",

    @Column(name = "rsvp_at", nullable = false)
    var rsvpAt: Instant = Instant.now(),

    @Column(name = "checked_in_at")
    var checkedInAt: Instant? = null,

    @Column(nullable = false, length = 20)
    var role: String = "ATTENDEE",

    @Column(columnDefinition = "TEXT")
    var notes: String? = null
)

// ━━━━━━━━━━━━━━━━ Story Highlights ━━━━━━━━━━━━━━━━
@Entity
@Table(name = "story_highlights")
class StoryHighlight(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 100)
    var title: String = "",

    @Column(name = "cover_media_key", nullable = false, length = 200)
    var coverMediaKey: String = "",

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,

    @Column(nullable = false, length = 20)
    var visibility: String = "EVERYONE",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

// ━━━━━━━━━━━━━━━━ Hashtags ━━━━━━━━━━━━━━━━
@Entity
@Table(name = "hashtags")
class Hashtag(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "tag_name", nullable = false, unique = true, length = 100)
    var tagName: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(length = 50)
    var category: String? = null,

    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 0,

    @Column(name = "posts_count", nullable = false)
    var postsCount: Int = 0,

    @Column(name = "stories_count", nullable = false)
    var storiesCount: Int = 0,

    @Column(name = "unique_users", nullable = false)
    var uniqueUsers: Int = 0,

    @Column(name = "trending_score", nullable = false)
    var trendingScore: Double = 0.0,

    @Column(name = "is_trending", nullable = false)
    var isTrending: Boolean = false,

    @Column(name = "trending_since")
    var trendingSince: Instant? = null,

    @Column(name = "is_blocked", nullable = false)
    var isBlocked: Boolean = false,

    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    var blockedReason: String? = null,

    @Column(name = "blocked_by")
    var blockedBy: UUID? = null,

    @Column(length = 10)
    var language: String? = "ar",

    @Column(name = "first_used_at", nullable = false)
    var firstUsedAt: Instant = Instant.now(),

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "hashtag_follows")
class HashtagFollow(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,

    @Column(name = "hashtag_id", nullable = false)
    var hashtagId: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(name = "notifications_enabled", nullable = false)
    var notificationsEnabled: Boolean = true,

    @Column(name = "followed_at", nullable = false)
    var followedAt: Instant = Instant.now()
)

// ━━━━━━━━━━━━━━━━ Saved Messages ━━━━━━━━━━━━━━━━
@Entity
@Table(name = "saved_messages")
class SavedMessage(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(name = "message_id", nullable = false)
    var messageId: UUID = UUID.randomUUID(),

    @Column(name = "saved_at", nullable = false)
    var savedAt: Instant = Instant.now(),

    @Column(nullable = false, length = 50)
    var collection: String = "DEFAULT",

    @Column(columnDefinition = "TEXT")
    var notes: String? = null
)

// ━━━━━━━━━━━━━━━━ Sticker Packs ━━━━━━━━━━━━━━━━
@Entity
@Table(name = "sticker_packs")
class StickerPack(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 100)
    var name: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "creator_id")
    var creatorId: UUID? = null,

    @Column(name = "is_official", nullable = false)
    var isOfficial: Boolean = false,

    @Column(name = "cover_media_key", nullable = false, length = 200)
    var coverMediaKey: String = "",

    @Column(name = "preview_media_key", length = 200)
    var previewMediaKey: String? = null,

    @Column(name = "sticker_count", nullable = false)
    var stickerCount: Int = 0,

    @Column(name = "total_downloads", nullable = false)
    var totalDownloads: Int = 0,

    @Column(name = "is_free", nullable = false)
    var isFree: Boolean = true,

    @Column(name = "price_cents", nullable = false)
    var priceCents: Int = 0,

    @Column(nullable = false, length = 3)
    var currency: String = "USD",

    @Column(name = "is_published", nullable = false)
    var isPublished: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

/**
 * ملصق فرد داخل حزمة — يُرسل في الدردشة كرسالة وسائط.
 * media_key يُشير إلى ملف مشفّر في MinIO (نفس آلية المرفقات).
 */
@Entity
@Table(name = "stickers")
class Sticker(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "pack_id", nullable = false)
    var packId: UUID = UUID.randomUUID(),

    @Column(length = 100)
    var name: String? = null,

    @Column(name = "media_key", nullable = false, length = 200)
    var mediaKey: String = "",

    @Column(name = "emoji_tags", columnDefinition = "TEXT[]")
    var emojiTags: Array<String> = emptyArray(),

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0
)

/**
 * تثبيت مستخدم لحزمة ملصقات (يُخزّن أيها مثبّت ومفضّل).
 */
@Entity
@Table(name = "user_sticker_packs")
@IdClass(UserStickerPackId::class)
class UserStickerPack(
    @Id
    @Column(name = "user_id")
    var userId: UUID = UUID.randomUUID(),

    @Id
    @Column(name = "pack_id")
    var packId: UUID = UUID.randomUUID(),

    @Column(name = "installed_at", nullable = false)
    var installedAt: Instant = Instant.now(),

    @Column(name = "is_favorite", nullable = false)
    var isFavorite: Boolean = false
)

data class UserStickerPackId(
    val userId: UUID = UUID.randomUUID(),
    val packId: UUID = UUID.randomUUID()
) : java.io.Serializable
