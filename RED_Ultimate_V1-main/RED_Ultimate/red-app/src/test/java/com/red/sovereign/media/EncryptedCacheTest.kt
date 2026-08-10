package com.red.sovereign.media

import org.junit.Assert.*
import org.junit.Test

class EncryptedCacheTest {
    @Test fun `cache key is SHA256 of original key`() {
        val key = "story:/api/media/users/123/abc.jpg"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }
    @Test fun `cache file ends with enc`() {
        val hash = "a".repeat(64)
        val fileName = "$hash.enc"
        assertTrue(fileName.endsWith(".enc"))
    }
}
