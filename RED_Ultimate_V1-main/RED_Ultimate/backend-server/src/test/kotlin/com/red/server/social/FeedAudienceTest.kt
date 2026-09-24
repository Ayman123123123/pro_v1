package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
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
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** A known ID must not turn a private/blocked post into a readable response. */
class FeedAudienceTest {
    private val mongo: MongoTemplate = mock()
    private val jdbc: JdbcTemplate = mock()
    private val service = FeedService(mongo, mock<UserAccountRepository>(), jdbc)
    private val viewer = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val postId = "post-private-test"

    private fun post(visibility: PostVisibility) = PostDocument(
        id = postId, authorId = author.toString(), authorRedId = "73066",
        authorUsername = "author", authorDisplayName = "Author", text = "private",
        visibility = visibility
    ).also { whenever(mongo.findOne(any<Query>(), eq(PostDocument::class.java))).thenReturn(it) }

    private fun notFound(block: () -> Unit) {
        val error = assertThrows(ResponseStatusException::class.java) { block() }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    @Test
    fun `friends post cannot be read, reacted to, voted on or reposted by a stranger`() {
        post(PostVisibility.FRIENDS)
        notFound { service.thread(viewer, postId) }
        notFound { service.react(viewer, postId, ReactionRequest("LIKE", true)) }
        notFound { service.vote(viewer, postId, PollVoteRequest("option")) }
        notFound { service.repost(viewer, postId) }
        verify(mongo, never()).save(any<PostReaction>())
    }

    @Test
    fun `block hides even a public post from direct operations`() {
        post(PostVisibility.PUBLIC)
        whenever(jdbc.queryForList(any<String>(), eq(String::class.java),
            eq(viewer), eq(viewer), eq(viewer))).thenReturn(listOf(author.toString()))
        notFound { service.thread(viewer, postId) }
        notFound { service.repost(viewer, postId) }
    }

    @Test
    fun `mutual friend can read a friends post without changing post visibility`() {
        val visible = post(PostVisibility.FRIENDS)
        whenever(jdbc.queryForList(any<String>(), eq(String::class.java), eq(viewer)))
            .thenReturn(listOf(author.toString()))
        whenever(mongo.find(any<Query>(), eq(PostDocument::class.java))).thenReturn(listOf(visible))
        assertEquals(listOf(visible), service.thread(viewer, postId))
    }
}
