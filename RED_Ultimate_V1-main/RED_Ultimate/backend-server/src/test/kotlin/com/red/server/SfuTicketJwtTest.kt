package com.red.server

import com.red.server.auth.model.UserAccount
import com.red.server.auth.security.JwtService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

class SfuTicketJwtTest {
    private val jwtSecret = "access-only-test-secret-that-is-longer-than-thirty-two-characters"
    private val sfuSecret = "media-only-test-secret-that-is-longer-than-thirty-two-characters"
    private val jwt = JwtService(jwtSecret, 15, "red-sovereign", "red-app", sfuSecret)

    @Test
    fun `SFU ticket cannot be used as a regular API token`() {
        val user = UserAccount(redId = "16999", username = "ahmed", displayName = "Ahmed")
        val deviceId = UUID.randomUUID()
        val roomId = "018f5e23-3f80-7a00-8000-000000000001"
        val ticket = jwt.issueSfuTicket(user, deviceId, roomId, "MEMBER", canProduce = true)
        val mediaKey = Keys.hmacShaKeyFor(MessageDigest.getInstance("SHA-256")
            .digest(sfuSecret.toByteArray(StandardCharsets.UTF_8)))
        val claims = Jwts.parser().verifyWith(mediaKey).build().parseSignedClaims(ticket).payload

        assertEquals("16999", claims["redId"])
        assertEquals(roomId, claims["sfuGroupId"])
        assertEquals(deviceId.toString(), claims["deviceId"])
        assertEquals("MEMBER", claims["sfuGroupRole"])
        assertEquals(true, claims["sfuCanProduce"])
        assertTrue(claims.expiration.time - claims.issuedAt.time in 1..900_000)
        assertNotEquals(jwt.issue(user, deviceId), ticket)
        assertThrows(JwtException::class.java) { jwt.parse(ticket) }
    }
}
