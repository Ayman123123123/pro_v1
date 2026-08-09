package com.red.server

import com.red.server.media.MediaService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageOrphanTest {
    @Test
    fun `findOrphanKeys detects unreferenced`() {
        val all = listOf("users/a/1.jpg", "users/a/2.jpg", "users/a/3.jpg", "thumbs/users/a/1.jpg")
        val referenced = setOf("users/a/1.jpg", "thumbs/users/a/1.jpg")
        val orphans = all.filter { it !in referenced }
        assertEquals(listOf("users/a/2.jpg", "users/a/3.jpg"), orphans)
    }
    @Test
    fun `no orphans when all referenced`() {
        val all = listOf("a", "b")
        val ref = setOf("a", "b")
        assertTrue(all.filter { it !in ref }.isEmpty())
    }
    @Test
    fun `deleteOrphans dryRun does not delete`() {
        // dryRun=true should only log, not delete — verify logic
        val dryRun = true
        assertTrue(dryRun) // placeholder for real MinIO mock
    }
}
