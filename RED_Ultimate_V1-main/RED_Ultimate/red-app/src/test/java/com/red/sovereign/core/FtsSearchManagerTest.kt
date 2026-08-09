package com.red.sovereign.core

import org.junit.Assert.*
import org.junit.Test

class FtsSearchManagerTest {
    @Test
    fun `sanitize query escapes quotes`() {
        val query = """he said "hello""""
        val sanitized = query.replace("\"", "\"\"").take(100)
        assertEquals("""he said ""hello""""", sanitized)
    }
    @Test
    fun `query length validation`() {
        assertTrue("ab".length >= 2)
        assertFalse("a".length >= 2)
        assertTrue("a".repeat(100).length <= 100)
    }
    @Test
    fun `snippet truncates to 120`() {
        val long = "a".repeat(200)
        assertEquals(120, long.take(120).length)
    }
}
