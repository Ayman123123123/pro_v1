package com.red.server.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.admin.service.ContentService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * اختبارات ContentService - Polls, Events, Hashtags, Sticker Packs
 */
class ContentServiceTest {
    private lateinit var polls: PollRepository
    private lateinit var pollOptions: PollOptionRepository
    private lateinit var pollVotes: PollVoteRepository
    private lateinit var events: EventRepository
    private lateinit var eventAttendees: EventAttendeeRepository
    private lateinit var highlights: StoryHighlightRepository
    private lateinit var hashtags: HashtagRepository
    private lateinit var hashtagFollows: HashtagFollowRepository
    private lateinit var savedMessages: SavedMessageRepository
    private lateinit var stickerPacks: StickerPackRepository
    private lateinit var stickers: StickerRepository
    private lateinit var userStickerPacks: UserStickerPackRepository
    private lateinit var service: ContentService

    @BeforeEach
    fun setup() {
        polls = mock()
        pollOptions = mock()
        pollVotes = mock()
        events = mock()
        eventAttendees = mock()
        highlights = mock()
        hashtags = mock()
        hashtagFollows = mock()
        savedMessages = mock()
        stickerPacks = mock()
        stickers = mock()
        userStickerPacks = mock()
        service = ContentService(
            polls, pollOptions, pollVotes, events, eventAttendees,
            highlights, hashtags, hashtagFollows, savedMessages, stickerPacks,
            stickers, userStickerPacks, ObjectMapper()
        )
    }

    // ━━━━━━━━━━━━━━━━ Polls Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `createPoll saves poll and options`() {
        val creatorId = UUID.randomUUID()
        val saved = Poll(id = UUID.randomUUID(), creatorId = creatorId, question = "Test?")
        whenever(polls.save(any<Poll>())).thenReturn(saved)

        val result = service.createPoll(
            creatorId = creatorId,
            question = "Test?",
            options = listOf("Option A", "Option B")
        )

        assertNotNull(result.id)
        verify(polls).save(any<Poll>())
        verify(pollOptions, times(2)).save(any<PollOption>())
    }

    @Test
    fun `vote increments vote count and updates options`() {
        val pollId = UUID.randomUUID()
        val optionId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val poll = Poll(id = pollId, status = "ACTIVE", totalVotes = 0, uniqueVoters = 0)
        val option = PollOption(id = optionId, pollId = pollId, voteCount = 0)

        whenever(polls.findById(pollId)).thenReturn(Optional.of(poll))
        whenever(pollVotes.countByPollIdAndUserId(pollId, userId)).thenReturn(0L)
        whenever(pollOptions.findById(optionId)).thenReturn(Optional.of(option))
        whenever(pollOptions.findByPollId(pollId)).thenReturn(listOf(option))

        service.vote(pollId, userId, listOf(optionId))

        verify(pollVotes).save(any<PollVote>())
        verify(pollOptions).save(option)
        assertEquals(1, option.voteCount)
        assertEquals(1, poll.totalVotes)
    }

    @Test
    fun `vote is rejected if already voted`() {
        val pollId = UUID.randomUUID()
        val optionId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val poll = Poll(id = pollId, status = "ACTIVE")
        whenever(polls.findById(pollId)).thenReturn(Optional.of(poll))
        whenever(pollOptions.findByPollId(pollId)).thenReturn(listOf(PollOption(id = optionId, pollId = pollId)))
        whenever(pollVotes.countByPollIdAndUserId(pollId, userId)).thenReturn(1L)

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            service.vote(pollId, userId, listOf(optionId))
        }
        verify(pollVotes, never()).save(any<PollVote>())
    }

    @Test
    fun `closePoll updates status`() {
        val pollId = UUID.randomUUID()
        val poll = Poll(id = pollId, status = "ACTIVE")
        whenever(polls.findById(pollId)).thenReturn(Optional.of(poll))
        service.closePoll(pollId)
        assertEquals("CLOSED", poll.status)
    }

    // ━━━━━━━━━━━━━━━━ Events Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `createEvent saves with defaults`() {
        val creatorId = UUID.randomUUID()
        val start = Instant.now().plusSeconds(3600)
        whenever(events.save(any<Event>())).thenAnswer { it.arguments[0] as Event }

        val event = service.createEvent(
            creatorId = creatorId,
            title = "Test Event",
            startsAt = start
        )

        assertEquals("MEETING", event.eventType)
        assertEquals("PUBLIC", event.visibility)
        assertEquals("SCHEDULED", event.status)
        assertEquals(0, event.currentAttendees)
    }

    @Test
    fun `rsvp creates new attendee and increments count`() {
        val eventId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val event = Event(id = eventId, currentAttendees = 5)
        whenever(events.findById(eventId)).thenReturn(Optional.of(event))
        whenever(eventAttendees.findByEventIdAndUserId(eventId, userId)).thenReturn(null)
        whenever(eventAttendees.save(any<EventAttendee>())).thenAnswer { it.arguments[0] as EventAttendee }

        val attendee = service.rsvp(eventId, userId, "GOING")
        assertNotNull(attendee)
        assertEquals(6, event.currentAttendees)
    }

    @Test
    fun `rsvp fails when event is full`() {
        val eventId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val event = Event(id = eventId, maxAttendees = 10, currentAttendees = 10)
        whenever(events.findById(eventId)).thenReturn(Optional.of(event))

        val attendee = service.rsvp(eventId, userId, "GOING")
        assertNull(attendee)
    }

    @Test
    fun `cancelEvent marks status as CANCELLED`() {
        val eventId = UUID.randomUUID()
        val event = Event(id = eventId, status = "SCHEDULED")
        whenever(events.findById(eventId)).thenReturn(Optional.of(event))

        val success = service.cancelEvent(eventId, "Test reason")
        assertTrue(success)
        assertEquals("CANCELLED", event.status)
        assertEquals("Test reason", event.cancellationReason)
    }

    // ━━━━━━━━━━━━━━━━ Hashtags Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `blockHashtag sets blocked fields`() {
        val hashtagId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val hashtag = Hashtag(id = hashtagId, isBlocked = false)
        whenever(hashtags.findById(hashtagId)).thenReturn(Optional.of(hashtag))

        service.blockHashtag(hashtagId, "Test reason", adminId)
        assertTrue(hashtag.isBlocked)
        assertEquals("Test reason", hashtag.blockedReason)
        assertEquals(adminId, hashtag.blockedBy)
    }

    @Test
    fun `unblockHashtag clears blocked fields`() {
        val hashtagId = UUID.randomUUID()
        val hashtag = Hashtag(id = hashtagId, isBlocked = true, blockedReason = "Old", blockedBy = UUID.randomUUID())
        whenever(hashtags.findById(hashtagId)).thenReturn(Optional.of(hashtag))

        service.unblockHashtag(hashtagId)
        assertFalse(hashtag.isBlocked)
        assertNull(hashtag.blockedReason)
    }

    @Test
    fun `followHashtag increments unique users`() {
        val hashtagId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val hashtag = Hashtag(id = hashtagId, uniqueUsers = 5)
        whenever(hashtagFollows.existsByUserIdAndHashtagId(userId, hashtagId)).thenReturn(false)
        whenever(hashtags.findById(hashtagId)).thenReturn(Optional.of(hashtag))

        val success = service.followHashtag(hashtagId, userId)
        assertTrue(success)
        assertEquals(6, hashtag.uniqueUsers)
    }

    @Test
    fun `followHashtag returns false if already following`() {
        val hashtagId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        whenever(hashtagFollows.existsByUserIdAndHashtagId(userId, hashtagId)).thenReturn(true)

        val success = service.followHashtag(hashtagId, userId)
        assertFalse(success)
    }

    // ━━━━━━━━━━━━━━━━ Sticker Packs Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `createStickerPack saves with defaults`() {
        whenever(stickerPacks.save(any<StickerPack>())).thenAnswer { it.arguments[0] as StickerPack }

        val pack = service.createStickerPack(
            name = "Younes Pack",
            description = "Test",
            coverMediaKey = "key-1"
        )

        assertEquals("Younes Pack", pack.name)
        assertTrue(pack.isFree)
        assertEquals(0, pack.priceCents)
        assertFalse(pack.isPublished)
    }

    @Test
    fun `publishStickerPack marks as published`() {
        val packId = UUID.randomUUID()
        val pack = StickerPack(id = packId, isPublished = false)
        whenever(stickerPacks.findById(packId)).thenReturn(Optional.of(pack))

        service.publishStickerPack(packId)
        assertTrue(pack.isPublished)
    }

    // ━━━━━━━━━━━━━━━━ Saved Messages Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `saveMessage returns null if already saved`() {
        val userId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        whenever(savedMessages.existsByUserIdAndMessageId(userId, messageId)).thenReturn(true)

        val result = service.saveMessage(userId, messageId)
        assertNull(result)
    }

    @Test
    fun `saveMessage creates new entry`() {
        val userId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        whenever(savedMessages.existsByUserIdAndMessageId(userId, messageId)).thenReturn(false)
        whenever(savedMessages.save(any<SavedMessage>())).thenAnswer { it.arguments[0] as SavedMessage }

        val result = service.saveMessage(userId, messageId, "WORK", "Important")
        assertNotNull(result)
        assertEquals("WORK", result!!.collection)
        assertEquals("Important", result.notes)
    }

    // ━━━━━━━━━━━━━━━━ Story Highlights Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `createHighlight assigns next display order`() {
        val userId = UUID.randomUUID()
        whenever(highlights.findByUserIdOrderByDisplayOrder(userId))
            .thenReturn(listOf(StoryHighlight(displayOrder = 0), StoryHighlight(displayOrder = 1)))
        whenever(highlights.save(any<StoryHighlight>())).thenAnswer { it.arguments[0] as StoryHighlight }

        val highlight = service.createHighlight(userId, "Test", "key", "EVERYONE")
        assertEquals(2, highlight.displayOrder)
    }
}
