package com.red.sovereign.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxQueryTest {
    private data class Row(val id: String, val archived: Boolean, val pinned: Boolean, val favorite: Boolean, val unread: Boolean)

    private val rows = listOf(
        Row("a", archived = false, pinned = true, favorite = true, unread = true),
        Row("b", archived = false, pinned = false, favorite = false, unread = true),
        Row("c", archived = true, pinned = false, favorite = false, unread = false),
        Row("d", archived = false, pinned = false, favorite = true, unread = false)
    )

    private fun run(filter: InboxFilter, folder: Set<String>? = null) = InboxQuery.filter(
        items = rows,
        filter = filter,
        folderChatIds = folder,
        archived = { it.archived },
        pinned = { it.pinned },
        favorite = { it.favorite },
        unread = { it.unread },
        idOf = { it.id }
    ).map { it.id }

    @Test
    fun `all hides archived and keeps pinned first`() {
        assertEquals(listOf("a", "b", "d"), run(InboxFilter.ALL))
    }

    @Test
    fun `unread only active unread`() {
        assertEquals(listOf("a", "b"), run(InboxFilter.UNREAD))
    }

    @Test
    fun `favorites exclude archived`() {
        assertEquals(listOf("a", "d"), run(InboxFilter.FAVORITES))
    }

    @Test
    fun `archive filter shows only archived`() {
        assertEquals(listOf("c"), run(InboxFilter.ARCHIVED))
    }

    @Test
    fun `custom folder intersects with active list`() {
        assertEquals(listOf("d"), run(InboxFilter.ALL, setOf("d", "c")))
    }
}
