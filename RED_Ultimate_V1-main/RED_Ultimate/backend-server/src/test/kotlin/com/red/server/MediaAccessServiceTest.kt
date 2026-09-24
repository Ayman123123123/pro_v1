package com.red.server

import com.red.server.media.MediaAccessService
import com.red.server.stories.StoryDocument
import com.red.server.stories.StoryVisibility
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

class MediaAccessServiceTest {
    private val mongo: MongoTemplate = mock()
    private val jdbc: JdbcTemplate = mock()
    private val service = MediaAccessService(mongo, jdbc)
    private val owner = UUID.randomUUID()
    private val foreignKey = "users/${UUID.randomUUID()}/${UUID.randomUUID()}.mp4"

    @Test
    fun `owner can download private media without a public reference`() {
        val key = "users/$owner/${UUID.randomUUID()}.mp4"
        assertDoesNotThrow { service.requireDownloadAllowed(owner, key) }
        verify(mongo, never()).exists(any<Query>(), eq(StoryDocument::class.java))
    }

    @Test
    fun `active story media is available to another approved account`() {
        val storyOwner = UUID.randomUUID()
        val story = StoryDocument(
            id = UUID.randomUUID().toString(),
            ownerId = storyOwner.toString(),
            ownerRedId = "red-owner",
            ownerUsername = "owner",
            ownerDisplayName = "Owner",
            mediaKey = foreignKey,
            mediaType = "video/mp4",
            caption = null,
            visibility = StoryVisibility.EVERYONE,
            expiresAt = Instant.now().plusSeconds(3600)
        )
        whenever(mongo.findOne(any<Query>(), eq(StoryDocument::class.java))).thenReturn(story)
        assertDoesNotThrow { service.requireDownloadAllowed(owner, foreignKey) }
    }

    @Test
    fun `a block defeats a stale explicit grant for foreign media`() {
        val blockedOwner = UUID.randomUUID()
        val key = "users/$blockedOwner/file.mp4"
        // With this return value the first block check is true. A previous
        // implementation returned on the grant before consulting user_blocks.
        whenever(jdbc.queryForObject(any<String>(), eq(Boolean::class.java),
            eq(blockedOwner), eq(owner), eq(owner), eq(blockedOwner))).thenReturn(true)
        val error = assertThrows(ResponseStatusException::class.java) {
            service.requireDownloadAllowed(owner, key)
        }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
    }

    @Test
    fun `a public story is not downloadable after its owner blocks the viewer`() {
        val storyOwner = UUID.randomUUID()
        val key = "users/$storyOwner/story.mp4"
        val story = StoryDocument(
            id = UUID.randomUUID().toString(), ownerId = storyOwner.toString(),
            ownerRedId = "red-owner", ownerUsername = "owner", ownerDisplayName = "Owner",
            mediaKey = key, mediaType = "video/mp4", caption = null,
            visibility = StoryVisibility.EVERYONE, expiresAt = Instant.now().plusSeconds(3600)
        )
        whenever(mongo.findOne(any<Query>(), eq(StoryDocument::class.java))).thenReturn(story)
        whenever(jdbc.queryForObject(any<String>(), eq(Boolean::class.java),
            eq(storyOwner), eq(owner), eq(owner), eq(storyOwner))).thenReturn(true)
        val error = assertThrows(ResponseStatusException::class.java) {
            service.requireDownloadAllowed(owner, key)
        }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
    }

    @Test
    fun `unreferenced foreign media is forbidden`() {
        whenever(mongo.exists(any<Query>(), eq(StoryDocument::class.java))).thenReturn(false)
        val error = assertThrows(ResponseStatusException::class.java) {
            service.requireDownloadAllowed(owner, foreignKey)
        }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
    }
}
