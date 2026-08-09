package com.red.server.websocket

import com.red.server.calls.IceServerController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class IceServerControllerTest {
    private val secret = "abcdefghijklmnopqrstuvwxyz123456" // 32+ chars

    private fun makeController(tlsPort: Int = 5349, altPort: Int = 443): IceServerController =
        IceServerController(
            host = "turn.example.com",
            port = 3478,
            tlsPort = tlsPort,
            altPort = altPort,
            secret = secret,
            ttlSeconds = 3600
        )

    private fun auth(userId: String): Authentication =
        UsernamePasswordAuthenticationToken(userId, "device-token")

    @Test fun `STUN and TURN servers are returned`() {
        val controller = makeController(tlsPort = 0, altPort = 0)
        val result = controller.iceServers(auth("user-1"))
        assertTrue(result.iceServers.isNotEmpty())
        val urls = result.iceServers.flatMap { it.urls }
        assertTrue(urls.any { it.startsWith("stun:") }) { "Expected STUN URL in $urls" }
        assertTrue(urls.any { it.startsWith("turn:") }) { "Expected TURN URL in $urls" }
    }

    @Test fun `TURNS on 5349 is included when enabled`() {
        val controller = makeController(tlsPort = 5349, altPort = 0)
        val result = controller.iceServers(auth("user-1"))
        val urls = result.iceServers.flatMap { it.urls }
        assertTrue(urls.any { it.contains(":5349") }) { "Expected TURNS:5349 in $urls" }
    }

    @Test fun `TURN on 443 is included when altPort is set`() {
        val controller = makeController(tlsPort = 0, altPort = 443)
        val result = controller.iceServers(auth("user-1"))
        val urls = result.iceServers.flatMap { it.urls }
        assertTrue(urls.any { it.contains(":443") }) { "Expected TURN:443 in $urls" }
    }

    @Test fun `credentials are time-limited HMAC`() {
        val controller = makeController()
        val result = controller.iceServers(auth("user-1"))
        val turnServer = result.iceServers.firstOrNull { it.username != null }!!
        val username = turnServer.username!!
        val credential = turnServer.credential!!
        // username format: "expiresAt:userId"
        val parts = username.split(":")
        assertEquals(2, parts.size)
        assertEquals("user-1", parts[1])
        val expiresAt = parts[0].toLong()
        assertTrue(expiresAt > System.currentTimeMillis() / 1000) { "Expiry should be in the future" }
        // Verify HMAC
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA1"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(username.toByteArray()))
        assertEquals(expected, credential)
    }

    @Test fun `expiresAt is within ttl window`() {
        val controller = makeController(ttlSeconds = 600)
        val before = System.currentTimeMillis() / 1000
        val result = controller.iceServers(auth("user-1"))
        val after = System.currentTimeMillis() / 1000
        // Expires within ttl window (600 seconds)
        assertTrue(result.expiresAt in (before + 599)..(after + 601)) {
            "expiresAt=${result.expiresAt} should be within 600s of now (before=$before, after=$after)"
        }
    }

    @Test fun `short secret is rejected`() {
        val badController = IceServerController("turn.example.com", 3478, 5349, 443, "short", 3600)
        try {
            badController.iceServers(auth("user-1"))
            assertTrue(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }
}
