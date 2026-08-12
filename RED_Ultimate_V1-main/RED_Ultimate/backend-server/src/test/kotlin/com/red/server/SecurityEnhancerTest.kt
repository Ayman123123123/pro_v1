package com.red.server.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SecurityEnhancerTest {

    private lateinit var securityEnhancer: SecurityEnhancer
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @BeforeEach
    fun setup() {
        securityEnhancer = SecurityEnhancer()
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
    }

    @Test
    fun `addSecurityHeaders adds all required headers`() {
        securityEnhancer.preHandle(request, response, object {})

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        assertEquals("DENY", response.getHeader("X-Frame-Options"))
        assertEquals("1; mode=block", response.getHeader("X-XSS-Protection"))
        assertNotNull(response.getHeader("Strict-Transport-Security"))
        assertNotNull(response.getHeader("X-Request-ID"))
        assertEquals("1.0.0", response.getHeader("X-API-Version"))
    }

    @Test
    fun `rate limiting allows requests within limit`() {
        repeat(99) {
            request = MockHttpServletRequest()
            request.setRemoteAddr("127.0.0.1")
            assertTrue(securityEnhancer.preHandle(request, response, object {}))
        }
    }

    @Test
    fun `rate limiting blocks excessive requests`() {
        repeat(101) {
            request = MockHttpServletRequest()
            request.setRemoteAddr("127.0.0.1")
            val result = securityEnhancer.preHandle(request, response, object {})
            if (it >= 100) {
                assertFalse(result)
                assertEquals(429, response.status)
            }
        }
    }

    @Test
    fun `untrusted forwarded header cannot evade rate limiting`() {
        val untrusted = SecurityEnhancer(trustXForwardedFor = false)
        repeat(SecurityEnhancer.MAX_REQUESTS_PER_MINUTE + 1) { index ->
            val req = MockHttpServletRequest().apply {
                setRemoteAddr("203.0.113.10")
                addHeader("X-Forwarded-For", "198.51.100.$index")
            }
            val allowed = untrusted.preHandle(req, MockHttpServletResponse(), object {})
            if (index == SecurityEnhancer.MAX_REQUESTS_PER_MINUTE) assertFalse(allowed)
        }
    }

    @Test
    fun `trusted proxy header identifies distinct clients`() {
        val trusted = SecurityEnhancer(trustXForwardedFor = true)
        repeat(SecurityEnhancer.MAX_REQUESTS_PER_MINUTE + 1) { index ->
            val req = MockHttpServletRequest().apply {
                setRemoteAddr("172.20.0.10")
                addHeader("X-Forwarded-For", "198.51.100.$index")
            }
            assertTrue(trusted.preHandle(req, MockHttpServletResponse(), object {}))
        }
    }

    @Test
    fun `isValidEmail returns correct results`() {
        assertTrue(securityEnhancer.isValidEmail("test@example.com"))
        assertTrue(securityEnhancer.isValidEmail("user.name+tag@domain.co.uk"))
        assertFalse(securityEnhancer.isValidEmail("invalid"))
        assertFalse(securityEnhancer.isValidEmail("@domain.com"))
        assertFalse(securityEnhancer.isValidEmail("user@"))
    }

    @Test
    fun `isValidPhone returns correct results`() {
        assertTrue(securityEnhancer.isValidPhone("+1234567890"))
        assertTrue(securityEnhancer.isValidPhone("1234567890"))
        assertTrue(securityEnhancer.isValidPhone("+123456789012345"))
        assertFalse(securityEnhancer.isValidPhone("123"))
        assertFalse(securityEnhancer.isValidPhone("abc"))
    }

    @Test
    fun `isStrongPassword returns correct results`() {
        assertTrue(securityEnhancer.isStrongPassword("StrongPass123!"))
        assertTrue(securityEnhancer.isStrongPassword("MyStr0ng#Pass"))
        assertFalse(securityEnhancer.isStrongPassword("weak"))
        assertFalse(securityEnhancer.isStrongPassword("nouppercase123!"))
        assertFalse(securityEnhancer.isStrongPassword("NOLOWERCASE123!"))
        assertFalse(securityEnhancer.isStrongPassword("NoDigitsHere!"))
    }

    @Test
    fun `sanitizeInput works correctly`() {
        assertEquals("hello", securityEnhancer.sanitizeInput("  hello  "))
        assertEquals("test", securityEnhancer.sanitizeInput("test"))
        assertNull(securityEnhancer.sanitizeInput("   "))
        assertNull(securityEnhancer.sanitizeInput(null))
    }

    @Test
    fun `isValidUuid returns correct results`() {
        assertTrue(securityEnhancer.isValidUuid("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(securityEnhancer.isValidUuid("not-a-uuid"))
        assertFalse(securityEnhancer.isValidUuid("550e8400-e29b-41d4"))
    }

    @Test
    fun `hashData produces consistent output`() {
        val hash1 = securityEnhancer.hashData("test")
        val hash2 = securityEnhancer.hashData("test")
        assertEquals(hash1, hash2)
        assertNotEquals("test", hash1)
    }

    @Test
    fun `isIpLockedOut returns false initially`() {
        assertFalse(securityEnhancer.isIpLockedOut("127.0.0.1"))
    }

    @Test
    fun `recordFailedAttempt increments counter`() {
        repeat(4) {
            securityEnhancer.recordFailedAttempt("127.0.0.1")
        }
        assertFalse(securityEnhancer.isIpLockedOut("127.0.0.1"))
    }

    @Test
    fun `recordFailedAttempt locks IP after max attempts`() {
        repeat(6) {
            securityEnhancer.recordFailedAttempt("127.0.0.1")
        }
        assertTrue(securityEnhancer.isIpLockedOut("127.0.0.1"))
    }

    @Test
    fun `clearFailedAttempts resets lockout`() {
        repeat(6) {
            securityEnhancer.recordFailedAttempt("127.0.0.1")
        }
        assertTrue(securityEnhancer.isIpLockedOut("127.0.0.1"))

        securityEnhancer.clearFailedAttempts("127.0.0.1")
        assertFalse(securityEnhancer.isIpLockedOut("127.0.0.1"))
    }
}
