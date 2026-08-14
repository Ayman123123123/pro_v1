package com.red.sovereign.core

import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.groups.GroupMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMentionsTest {

    private val ali = GroupMember("1", "g", "u1", "16999", "ali", "MEMBER", "0")
    private val sara = GroupMember("2", "g", "u2", "17001", "sara", "ADMIN", "0")
    private val me = GroupMember("3", "g", "u3", "18000", "me", "OWNER", "0")
    private val friends = listOf(
        PublicRedProfile("16999", "ali", "علي محمد"),
        PublicRedProfile("17001", "sara", "سارة"),
    )

    @Test
    fun `query reads trailing at fragment`() {
        assertEquals("عل", GroupMentions.query("مرحبا @عل"))
        assertEquals("", GroupMentions.query("انظر @"))
        assertEquals(null, GroupMentions.query("بدون إشارة"))
    }

    @Test
    fun `candidates match display name and hide self`() {
        val found = GroupMentions.candidates("عل", listOf(ali, sara, me), friends, ownRedId = "18000")
        assertEquals(1, found.size)
        assertEquals("علي محمد", found.single().displayName)
        assertEquals("16999", found.single().redId)
    }

    @Test
    fun `insert replaces the at query with username`() {
        val next = GroupMentions.insert("راجع @عل", "ali")
        assertEquals("راجع @ali ", next)
    }

    @Test
    fun `mentionIds map username to red id`() {
        val ids = GroupMentions.mentionIds("راجع @ali و @sara", listOf(ali, sara), friends)
        assertEquals(listOf("@16999", "@17001"), ids)
    }

    @Test
    fun `mentionIds keep an explicit red id`() {
        val ids = GroupMentions.mentionIds("أهلا @16999", listOf(ali, sara), friends)
        assertEquals(listOf("@16999"), ids)
    }

    @Test
    fun `displayLabel prefers friend name`() {
        assertEquals("علي محمد", GroupMentions.displayLabel("@16999", listOf(ali, sara), friends))
        assertEquals("sara", GroupMentions.displayLabel("17001", listOf(sara), emptyList()))
    }

    @Test
    fun `blank query lists members without the author`() {
        val found = GroupMentions.candidates("", listOf(ali, sara, me), friends, "18000")
        assertEquals(2, found.size)
        assertTrue(found.none { it.redId == "18000" })
    }
}
