package com.red.server.auth

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.UUID

class AuthExceptionHandlerTest {
    private val handler = AuthExceptionHandler()

    @Test
    fun `illegal argument never exposes operational detail`() {
        val secret = "DINSTAR gateway 192.168.11.1 rejected admin password"

        val response = handler.badRequest(IllegalArgumentException(secret))

        assertSafe(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", secret)
    }

    @Test
    fun `not found never exposes database detail`() {
        val secret = "SELECT user_id FROM private_sessions WHERE token='secret-token'"

        val response = handler.notFound(NoSuchElementException(secret))

        assertSafe(response, HttpStatus.NOT_FOUND, "NOT_FOUND", secret)
    }

    @Test
    fun `conflict never exposes provider detail`() {
        val secret = "Asterisk SIP trunk secret is invalid"

        val response = handler.conflict(IllegalStateException(secret))

        assertSafe(response, HttpStatus.CONFLICT, "CONFLICT", secret)
    }

    @Test
    fun `unsupported operation uses stable code and diagnostic id`() {
        val secret = "Firmware endpoint /api/private/v1 is disabled"

        val response = handler.notImplemented(UnsupportedOperationException(secret))

        assertSafe(response, HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", secret)
    }

    private fun assertSafe(
        response: ResponseEntity<Map<String, String>>,
        expectedStatus: HttpStatus,
        expectedCode: String,
        secret: String
    ) {
        assertEquals(expectedStatus, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(expectedCode, body["error"])
        val diagnosticId = requireNotNull(body["diagnosticId"])
        assertDoesNotThrow { UUID.fromString(diagnosticId) }
        assertFalse(body.values.any { it.contains(secret) })
    }
}
