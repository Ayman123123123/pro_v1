package com.red.sovereign.stories

import com.red.sovereign.core.database.StoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryCacheMapperTest {
    @Test
    fun `cache refresh preserves server owner metadata for an existing story`() {
        val serverStory = Story(
            id = "story-1",
            ownerRedId = "12345",
            ownerUsername = "ahmad",
            ownerDisplayName = "أحمد",
            mediaUrl = "/api/media/old",
            mediaType = "image/jpeg",
            caption = "قديم",
            createdAt = "2026-08-21T10:00:00Z",
            expiresAt = "2026-08-22T10:00:00Z",
            visibleTo = "CONTACTS",
        )
        val cached = StoryEntity(
            id = "story-1",
            userId = "12345",
            mediaUrl = "/api/media/fresh",
            mediaType = "image/jpeg",
            caption = "محدّث",
            timestamp = 1_725_000_000_000,
            expiresAt = 1_725_086_400_000,
        )

        val merged = mergeCachedStories(listOf(cached), listOf(serverStory)).single()

        assertEquals("أحمد", merged.ownerDisplayName)
        assertEquals("ahmad", merged.ownerUsername)
        assertEquals("/api/media/fresh", merged.mediaUrl)
        assertEquals("محدّث", merged.caption)
    }

    @Test
    fun `offline cache falls back to RED ID rather than a generic owner placeholder`() {
        val cached = StoryEntity(
            id = "story-2",
            userId = "54321",
            mediaUrl = "/api/media/offline",
            mediaType = "image/jpeg",
            timestamp = 1_725_000_000_000,
            expiresAt = 1_725_086_400_000,
        )

        val restored = mergeCachedStories(listOf(cached), emptyList()).single()

        assertEquals("54321", restored.ownerRedId)
        assertEquals("54321", restored.ownerDisplayName)
        assertEquals("54321", restored.ownerUsername)
    }
}
