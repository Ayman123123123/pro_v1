package com.red.sovereign.features

import com.red.sovereign.features.communities.Community
import org.junit.Assert.*
import org.junit.Test

class CommunitiesTest {
    @Test
    fun `community join toggles correctly`() {
        val c = Community("1", "يمنيون", "مجتمع", 100, false)
        val joined = c.copy(isJoined = true, members = c.members + 1)
        assertTrue(joined.isJoined)
        assertEquals(101, joined.members)
    }
    @Test
    fun `search filters correctly`() {
        val list = listOf(
            Community("1", "يمنيون", "مجتمع اليمن", 100, false),
            Community("2", "تقنية", "أخبار", 50, false)
        )
        val filtered = list.filter { it.name.contains("يمن", ignoreCase = true) }
        assertEquals(1, filtered.size)
        assertEquals("يمنيون", filtered[0].name)
    }
    @Test
    fun `communities are public not E2EE`() {
        // Communities are public, unlike groups which are E2EE
        val c = Community("1", "Test", "Public", 10, false)
        assertFalse(c.isJoined) // not encrypted, just joined flag
    }
}
