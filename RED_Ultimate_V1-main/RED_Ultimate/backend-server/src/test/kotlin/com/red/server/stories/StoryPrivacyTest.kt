package com.red.server.stories

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.Optional
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/** Every visibility level must respect a later two-way block. */
class StoryPrivacyTest {
    private val mongo: MongoTemplate = mock()
    private val jdbc: JdbcTemplate = mock()
    private val users: UserAccountRepository = mock()
    private val service = StoryService(mongo, users, mock<MediaService>(), jdbc)
    private val owner = UUID.randomUUID()
    private val viewer = UUID.randomUUID()

    @Test
    fun `selected RED identifiers are converted to account UUIDs on publication`() {
        val target = UserAccount(id = viewer, redId = "28261", username = "guest")
        whenever(users.findById(owner)).thenReturn(Optional.of(UserAccount(id = owner, redId = "73066", username = "owner")))
        whenever(users.findByRedId("28261")).thenReturn(target)
        whenever(mongo.save(any<StoryDocument>())).thenAnswer { it.arguments[0] as StoryDocument }
        service.create(owner, CreateStoryRequest("text://story", caption = "hello", mediaType = "TEXT",
            visibility = StoryVisibility.SELECTED, allowedUserIds = setOf("28261")))
        val saved = org.mockito.kotlin.argumentCaptor<StoryDocument>()
        verify(mongo).save(saved.capture())
        assertEquals(setOf(viewer.toString()), saved.firstValue.allowedUserIds)
    }

    @Test
    fun `public and selected stories cannot disclose their owner to a blocked account`() {
        whenever(jdbc.queryForObject(any<String>(), eq(Boolean::class.java),
            eq(owner), eq(viewer), eq(viewer), eq(owner))).thenReturn(true)
        for (visibility in listOf(StoryVisibility.EVERYONE, StoryVisibility.SELECTED)) {
            val story = StoryDocument(
                id = UUID.randomUUID().toString(), ownerId = owner.toString(),
                ownerRedId = "73066", ownerUsername = "owner", ownerDisplayName = "Owner",
                mediaKey = "", mediaType = "TEXT", caption = "private text",
                visibility = visibility, allowedUserIds = setOf(viewer.toString()),
                expiresAt = Instant.now().plusSeconds(3600)
            )
            whenever(mongo.findOne(any<Query>(), eq(StoryDocument::class.java))).thenReturn(story)
            assertThrows(IllegalArgumentException::class.java) {
                service.replyTarget(viewer, story.id)
            }
            assertThrows(IllegalArgumentException::class.java) {
                service.viewed(viewer, story.id)
            }
        }
    }
}
