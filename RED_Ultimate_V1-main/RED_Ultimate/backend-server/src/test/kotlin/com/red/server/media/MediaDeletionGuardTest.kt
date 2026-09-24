package com.red.server.media

import com.red.server.database.ChannelDocument
import com.red.server.database.ChannelMessageDocument
import com.red.server.database.GroupMessageDocument
import com.red.server.database.MessageDocument
import com.red.server.groups.GroupDocument
import com.red.server.social.PostDocument
import com.red.server.stories.StoryDocument
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
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** An uploader cannot silently destroy published content that shares this object key. */
class MediaDeletionGuardTest {
    private val mongo: MongoTemplate = mock()
    private val jdbc: JdbcTemplate = mock()
    private val service = MediaAccessService(mongo, jdbc)
    private val media: MediaService = mock()
    private val grants: MediaGrantService = mock()
    private val owner = UUID.randomUUID()
    private val file = "${UUID.randomUUID()}.jpg"
    private val key = "users/$owner/$file"

    @Test
    fun `referenced story image rejects explicit owner deletion before touching MinIO or grants`() {
        whenever(mongo.exists(any<Query>(), eq(StoryDocument::class.java))).thenReturn(true)
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(owner.toString())

        val error = assertThrows(ResponseStatusException::class.java) {
            MediaController(media, service, grants).delete(owner.toString(), file, authentication)
        }
        assertEquals(HttpStatus.CONFLICT, error.statusCode)
        verify(media, never()).delete(any())
        verify(grants, never()).revokeAll(any(), any())
    }

    @Test
    fun `group avatar and post media each block physical deletion`() {
        whenever(mongo.exists(any<Query>(), eq(GroupDocument::class.java))).thenReturn(true)
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException::class.java) {
            service.requireNoPublishedReferences(key)
        }.statusCode)
        whenever(mongo.exists(any<Query>(), eq(GroupDocument::class.java))).thenReturn(false)
        whenever(mongo.exists(any<Query>(), eq(PostDocument::class.java))).thenReturn(true)
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException::class.java) {
            service.requireNoPublishedReferences(key)
        }.statusCode)
    }

    @Test
    fun `channel avatar also blocks physical deletion`() {
        whenever(mongo.exists(any<Query>(), eq(ChannelDocument::class.java))).thenReturn(true)
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException::class.java) {
            service.requireNoPublishedReferences(key)
        }.statusCode)
    }

    @Test
    fun `active private group and channel message attachments block physical deletion`() {
        for (document in listOf(MessageDocument::class.java, GroupMessageDocument::class.java,
            ChannelMessageDocument::class.java)) {
            whenever(mongo.exists(any<Query>(), eq(document))).thenReturn(true)
            assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException::class.java) {
                service.requireNoPublishedReferences(key)
            }.statusCode)
            whenever(mongo.exists(any<Query>(), eq(document))).thenReturn(false)
        }
    }

    @Test
    fun `profile avatar stored in PostgreSQL blocks removal`() {
        whenever(jdbc.queryForObject(any<String>(), eq(Boolean::class.java), eq(key), eq(key)))
            .thenReturn(true)
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException::class.java) {
            service.requireNoPublishedReferences(key)
        }.statusCode)
    }

    @Test
    fun `unreferenced object is deleted before its grants are revoked`() {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(owner.toString())

        val response = MediaController(media, service, grants).delete(owner.toString(), file, authentication)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        val inOrder = org.mockito.kotlin.inOrder(media, grants)
        inOrder.verify(media).delete(key)
        inOrder.verify(grants).revokeAll(owner, key)
    }

    @Test
    fun `failed MinIO removal keeps existing grants for retry`() {
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(owner.toString())
        org.mockito.kotlin.doThrow(IllegalStateException("MinIO unavailable"))
            .whenever(media).delete(key)

        assertThrows(IllegalStateException::class.java) {
            MediaController(media, service, grants).delete(owner.toString(), file, authentication)
        }
        verify(grants, never()).revokeAll(any(), any())
    }

    @Test
    fun `reference store errors fail closed without deleting anything`() {
        whenever(mongo.exists(any<Query>(), eq(StoryDocument::class.java)))
            .thenThrow(IllegalStateException("Mongo unavailable"))
        val authentication: Authentication = mock()
        whenever(authentication.name).thenReturn(owner.toString())
        assertThrows(IllegalStateException::class.java) {
            MediaController(media, service, grants).delete(owner.toString(), file, authentication)
        }
        verify(media, never()).delete(any())
    }
}
