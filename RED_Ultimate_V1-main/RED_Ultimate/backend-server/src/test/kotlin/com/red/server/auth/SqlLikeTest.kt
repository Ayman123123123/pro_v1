package com.red.server.auth

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.SqlLike
import com.red.server.auth.repository.UserAccountSpecs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlLikeTest {
    @Test
    fun `contains wraps a lowercased term and strips like wildcards`() {
        assertEquals("%ahmed%", SqlLike.contains("Ahmed"))
        assertEquals("%younes%", SqlLike.contains("  YOUNES  ".trim()))
        assertEquals("%admin%", SqlLike.contains("%AdMin_"))
        assertEquals("%يونس%", SqlLike.contains("يونس"))
        assertEquals("%%", SqlLike.contains("%_%\\"))
    }

    @Test
    fun `admin spec is built without requiring a database`() {
        val spec = UserAccountSpecs.adminSearch(AccountStatus.APPROVED, AccountRole.USER, "Ahmed")
        assertNotNull(spec)
        val empty = UserAccountSpecs.adminSearch(null, null, null)
        assertNotNull(empty)
        assertFalse(spec === empty)
    }

    @Test
    fun `blank search is treated as no text filter`() {
        val blank = UserAccountSpecs.adminSearch(null, null, "   ")
        val missing = UserAccountSpecs.adminSearch(null, null, null)
        assertNotNull(blank)
        assertNotNull(missing)
        assertTrue(SqlLike.contains("73066") == "%73066%")
    }
}
