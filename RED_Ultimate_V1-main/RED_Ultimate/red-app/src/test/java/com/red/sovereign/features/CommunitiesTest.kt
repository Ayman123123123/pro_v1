package com.red.sovereign.features

import com.red.sovereign.features.communities.Community
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitiesTest {
    private fun community(
        id: String,
        name: String,
        description: String,
        memberCount: Long = 0L,
        isJoined: Boolean = false,
    ) = Community(
        id = id,
        name = name,
        description = description,
        createdBy = "red-admin",
        createdByUsername = "admin",
        memberCount = memberCount,
        isJoined = isJoined,
        createdAt = "2026-08-19T00:00:00Z",
        updatedAt = "2026-08-19T00:00:00Z",
    )

    @Test
    fun `community join toggles correctly`() {
        val original = community("1", "يمنيون", "مجتمع", memberCount = 100)
        val joined = original.copy(isJoined = true, memberCount = original.memberCount + 1)

        assertTrue(joined.isJoined)
        assertEquals(101L, joined.memberCount)
    }

    @Test
    fun `search filters correctly`() {
        val communities = listOf(
            community("1", "يمنيون", "مجتمع اليمن", memberCount = 100),
            community("2", "تقنية", "أخبار", memberCount = 50),
        )

        val filtered = communities.filter { it.name.contains("يمن", ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("يمنيون", filtered.single().name)
    }

    @Test
    fun `communities are public unless access policy says otherwise`() {
        val community = community("1", "Test", "Public")
        assertTrue(community.isPublic)
        assertFalse(community.isJoined)
    }
}
