package com.red.server.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsrfTokenValidatorTest {
    @Test
    fun `accepts matching nonblank double-submit tokens`() {
        assertTrue(CsrfTokenValidator.matches("csrf-token-abc", "csrf-token-abc"))
    }

    @Test
    fun `rejects missing blank and mismatched double-submit tokens`() {
        assertFalse(CsrfTokenValidator.matches(null, "csrf-token-abc"))
        assertFalse(CsrfTokenValidator.matches("csrf-token-abc", null))
        assertFalse(CsrfTokenValidator.matches("", "csrf-token-abc"))
        assertFalse(CsrfTokenValidator.matches("csrf-token-abc", "other-token"))
    }
}
