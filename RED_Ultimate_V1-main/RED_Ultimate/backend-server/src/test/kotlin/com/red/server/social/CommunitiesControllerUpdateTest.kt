package com.red.server.social

import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.security.core.Authentication
import java.time.Instant

class CommunitiesControllerUpdateTest {
    private val accountId = "account-owner"
    private val now = Instant.parse("2026-09-24T00:00:00Z")
    private val community = CommunityDocument(
        id = "community-1", name = "Original", description = "Old text", category = "GENERAL",
        createdBy = accountId, createdByUsername = "owner", rules = "Old rules",
        createdAt = now, updatedAt = now
    )
    private val auth = mock<Authentication> { on { name }.thenReturn(accountId) }

    @Test
    fun `admin can clear optional community text without changing access control`() {
        val mongo = mock<MongoTemplate>()
        whenever(mongo.findById(eq(community.id), eq(CommunityDocument::class.java))).thenReturn(community)
        whenever(mongo.findOne(any<Query>(), eq(CommunityMember::class.java))).thenReturn(
            CommunityMember("member-1", community.id, accountId, "RED1", "owner", CommunityRole.ADMIN, now)
        )
        whenever(mongo.count(any<Query>(), eq(CommunityMember::class.java))).thenReturn(1L)

        val response = CommunitiesController(mongo, mock<UserAccountRepository>()).update(
            auth, community.id, UpdateCommunityRequest(description = "  ", rules = "  ")
        )
        assertEquals(200, response.statusCode.value())
        val edits = argumentCaptor<Update>()
        verify(mongo).updateFirst(any<Query>(), edits.capture(), eq(CommunityDocument::class.java))
        val fields = edits.firstValue.updateObject["\$set"] as Map<*, *>
        assertTrue(fields.containsKey("description"))
        assertTrue(fields.containsKey("rules"))
        assertNull(fields["description"])
        assertNull(fields["rules"])
    }

    @Test
    fun `member cannot edit another community through optional-field clearing`() {
        val mongo = mock<MongoTemplate>()
        whenever(mongo.findById(eq(community.id), eq(CommunityDocument::class.java))).thenReturn(community)
        whenever(mongo.findOne(any<Query>(), eq(CommunityMember::class.java))).thenReturn(
            CommunityMember("member-2", community.id, accountId, "RED1", "reader", CommunityRole.MEMBER, now)
        )
        val response = CommunitiesController(mongo, mock<UserAccountRepository>()).update(
            auth, community.id, UpdateCommunityRequest(description = "")
        )
        assertEquals(403, response.statusCode.value())
        verify(mongo, never()).updateFirst(any<Query>(), any<Update>(), eq(CommunityDocument::class.java))
    }
}
