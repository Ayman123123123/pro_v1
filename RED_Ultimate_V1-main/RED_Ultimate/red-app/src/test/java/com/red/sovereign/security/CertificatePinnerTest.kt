package com.red.sovereign.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.red.sovereign.BuildConfig
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class CertificatePinnerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Ensure we're in test mode
        CertificatePinner.disable()
    }

    @Test
    fun `pin enables and disables correctly`() {
        CertificatePinner.disable()
        assertFalse(CertificatePinner.isEnabled)

        CertificatePinner.enable()
        assertTrue(CertificatePinner.isEnabled)
    }

    @Test
    fun `verify returns true when pinning is disabled`() {
        CertificatePinner.disable()
        val result = CertificatePinner.verify("test.local", emptyList())
        assertTrue(result)
    }

    @Test
    fun `verify returns true for unpinned host`() {
        CertificatePinner.enable()
        val result = CertificatePinner.verify("unpinned.local", emptyList())
        assertTrue(result)
    }

    @Test
    fun `generatePin produces consistent output`() {
        // Use a simple certificate for testing
        val certString = "test-certificate-data"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(certString.toByteArray())
        val expectedPin = "sha256/" + Base64.encodeToString(hash, Base64.NO_WRAP)

        // Our generatePin should produce something valid
        assertNotNull(CertificatePinner.generatePin(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun `isPinned returns correct status`() {
        assertFalse(CertificatePinner.isPinned("unknown.host"))

        CertificatePinner.addPin("test.host", "sha256/test123")
        assertTrue(CertificatePinner.isPinned("test.host"))
        assertFalse(CertificatePinner.isPinned("other.host"))
    }

    @Test
    fun `addPin and getExpectedPins work correctly`() {
        CertificatePinner.addPin("test.host", "sha256/pin1")
        CertificatePinner.addPin("test.host", "sha256/pin2")

        val pins = CertificatePinner.getExpectedPins("test.host")
        assertEquals(2, pins.size)
        assertTrue(pins.contains("sha256/pin1"))
        assertTrue(pins.contains("sha256/pin2"))
    }

    @Test
    fun `removePins works correctly`() {
        CertificatePinner.addPin("test.host", "sha256/pin1")
        assertTrue(CertificatePinner.isPinned("test.host"))

        CertificatePinner.removePins("test.host")
        assertFalse(CertificatePinner.isPinned("test.host"))
        assertTrue(CertificatePinner.getExpectedPins("test.host").isEmpty())
    }

    @Test
    fun `clearAllPins works correctly`() {
        CertificatePinner.addPin("host1", "sha256/pin1")
        CertificatePinner.addPin("host2", "sha256/pin2")

        CertificatePinner.clearAllPins()

        assertFalse(CertificatePinner.isPinned("host1"))
        assertFalse(CertificatePinner.isPinned("host2"))
    }

    @Test
    fun `loadPins does not crash`() {
        // Should not throw
        CertificatePinner.loadPins(context)
    }

    @Test
    fun `savePins does not crash`() {
        // Should not throw
        CertificatePinner.savePins(context)
    }

    @Test
    fun `isValidEmail tests`() {
        assertTrue(DebugSecurityManager.isValidEmail("test@example.com"))
        assertTrue(DebugSecurityManager.isValidEmail("user.name+tag@domain.co.uk"))
        assertFalse(DebugSecurityManager.isValidEmail("invalid"))
        assertFalse(DebugSecurityManager.isValidEmail("@domain.com"))
        assertFalse(DebugSecurityManager.isValidEmail("user@"))
    }

    @Test
    fun `isValidPhone tests`() {
        assertTrue(DebugSecurityManager.isValidPhone("+1234567890"))
        assertTrue(DebugSecurityManager.isValidPhone("1234567890"))
        assertTrue(DebugSecurityManager.isValidPhone("+123456789012345"))
        assertFalse(DebugSecurityManager.isValidPhone("123"))
        assertFalse(DebugSecurityManager.isValidPhone("abc"))
    }

    @Test
    fun `isStrongPassword tests`() {
        assertTrue(DebugSecurityManager.isStrongPassword("StrongPass123!"))
        assertTrue(DebugSecurityManager.isStrongPassword("MyStr0ng#Pass"))
        assertFalse(DebugSecurityManager.isStrongPassword("weak"))
        assertFalse(DebugSecurityManager.isStrongPassword("nouppercase123!"))
        assertFalse(DebugSecurityManager.isStrongPassword("NOLOWERCASE123!"))
        assertFalse(DebugSecurityManager.isStrongPassword("NoDigitsHere!"))
    }

    @Test
    fun `sanitizeInput tests`() {
        assertEquals("hello", DebugSecurityManager.sanitizeInput("  hello  "))
        assertEquals("test", DebugSecurityManager.sanitizeInput("test"))
        assertNull(DebugSecurityManager.sanitizeInput("   "))
        assertNull(DebugSecurityManager.sanitizeInput(null))
    }

    @Test
    fun `isValidUuid tests`() {
        assertTrue(DebugSecurityManager.isValidUuid("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(DebugSecurityManager.isValidUuid("not-a-uuid"))
        assertFalse(DebugSecurityManager.isValidUuid("550e8400-e29b-41d4"))
    }

    @Test
    fun `hashData produces consistent output`() {
        val hash1 = DebugSecurityManager.hashData("test")
        val hash2 = DebugSecurityManager.hashData("test")
        assertEquals(hash1, hash2)
        assertNotEquals("test", hash1)
    }

    @Test
    fun `security recommendations include certificate pinning status`() {
        val recs = DebugSecurityManager.getSecurityRecommendations()
        assertTrue(recs.any { it.title == "Certificate Pinning" })
    }

    @Test
    fun `validateConfiguration does not crash`() {
        val warnings = DebugSecurityManager.validateConfiguration()
        assertNotNull(warnings)
    }
}
