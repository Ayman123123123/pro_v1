package com.red.server.stories

import com.mongodb.client.result.DeleteResult
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

/** Story references do not own their uploader's (possibly shared) MinIO object. */
class StoryMediaLifecycleTest {
    private val mongo: MongoTemplate = mock()
    private val media: MediaService = mock()
    private val service = StoryService(mongo, mock<UserAccountRepository>(), media, mock<JdbcTemplate>())
    private val owner = UUID.randomUUID()
    private val story = StoryDocument(
        id = UUID.randomUUID().toString(), ownerId = owner.toString(),
        ownerRedId = "12345", ownerUsername = "owner", ownerDisplayName = "Owner",
        mediaKey = "users/$owner/${UUID.randomUUID()}.jpg", mediaType = "image/jpeg",
        caption = null, expiresAt = Instant.now().plusSeconds(3600)
    )

    @Test
    fun `deleting a story revokes its reference but retains the shared upload`() {
        whenever(mongo.findOne(any<Query>(), eq(StoryDocument::class.java))).thenReturn(story)
        service.delete(owner, story.id)
        verify(mongo).updateFirst(any<Query>(), any<Update>(), eq(StoryDocument::class.java))
        verify(media, never()).delete(any())
    }

    @Test
    fun `expired story cleanup removes documents but not their shared upload`() {
        whenever(mongo.find(any<Query>(), eq(StoryDocument::class.java)))
            .thenReturn(listOf(story.copy(expiresAt = Instant.now().minusSeconds(60))))
        service.cleanupExpired()
        verify(mongo).remove(any<Query>(), eq(StoryDocument::class.java))
        verify(media, never()).delete(any())
    }

    @Test
    fun `bulk story purge does not destroy reusable uploads`() {
        whenever(mongo.remove(any<Query>(), eq(StoryDocument::class.java)))
            .thenReturn(DeleteResult.acknowledged(1))
        service.purgeAll()
        verify(mongo).remove(any<Query>(), eq(StoryDocument::class.java))
        verify(media, never()).delete(any())
    }
}
