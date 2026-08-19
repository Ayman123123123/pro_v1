package com.red.server.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Double-submit CSRF validation for the administrator refresh cookie flow.
 * Bearer-authenticated mobile endpoints remain stateless and do not use this validator.
 */
internal object CsrfTokenValidator {
    fun matches(cookieToken: String?, headerToken: String?): Boolean {
        if (cookieToken.isNullOrBlank() || headerToken.isNullOrBlank()) return false
        return MessageDigest.isEqual(
            cookieToken.toByteArray(StandardCharsets.UTF_8),
            headerToken.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
