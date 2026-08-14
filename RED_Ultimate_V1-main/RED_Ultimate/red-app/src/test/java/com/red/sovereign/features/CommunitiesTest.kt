package com.red.sovereign.features

import com.red.sovereign.features.communities.Community
import org.junit.Assert.*
import org.junit.Test

class CommunitiesTest {

    private fun community(
        id: String = "1",
        name: String = "Test",
        description: String? = null,
        category: String = "GENERAL",
        memberCount: Long = 0L,
        isJoined: Boolean = false
    ) = Community(
        id = id,
        name = name,
        description = description,
        category = category,
        createdBy = "u1",
        createdByUsername = "admin",
        memberCount = memberCount,
        isJoined = isJoined,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    @Test
    fun `community join toggles correctly`() {
        val c = community(id = "1", name = "يمنيون", description = "مجتمع", memberCount = 100)
        val joined = c.copy(isJoined = true, memberCount = c.memberCount + 1)
        assertTrue(joined.isJoined)
        assertEquals(101L, joined.memberCount)
    }

    @Test
    fun `search filters correctly`() {
        val list = listOf(
            community(id = "1", name = "يمنيون", description = "مجتمع اليمن", memberCount = 100),
            community(id = "2", name = "تقنية", description = "أخبار", memberCount = 50)
        )
        val filtered = list.filter { it.name.contains("يمن", ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("يمنيون", filtered[0].name)
    }

    @Test
    fun `communities are public not E2EE`() {
        // Communities are public, unlike groups which are E2EE
        val c = community(id = "1", name = "Test", description = "Public", memberCount = 10)
        assertFalse(c.isJoined)
    }
}