package com.red.server

import com.red.server.auth.model.UserAccount
import com.red.server.auth.security.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SfuTicketJwtTest {
    private val jwt = JwtService("this-is-a-test-only-secret-that-is-longer-than-thirty-two-characters", 3_600_000)

    @Test
    fun `SFU ticket is device and room scoped rather than an interchangeable API token`() {
        val user = UserAccount(redId = "16999", username = "ahmed", displayName = "Ahmed")
        val deviceId = UUID.randomUUID()
        val roomId = "018f5e23-3f80-7a00-8000-000000000001"

        val ticket = jwt.issueSfuTicket(user, deviceId, roomId, "MEMBER", canProduce = true)
        val claims = jwt.parse(ticket)

        assertEquals("16999", claims["redId"])
        assertEquals(roomId, claims["sfuGroupId"])
        assertEquals(deviceId.toString(), claims["deviceId"])
        assertEquals("MEMBER", claims["sfuGroupRole"])
        assertEquals(true, claims["sfuCanProduce"])
        assertTrue(claims.expiration.time - claims.issuedAt.time in 1..900_000)
        assertNotEquals(jwt.issue(user, deviceId), ticket)
    }
}
