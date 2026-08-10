package com.red.sovereign.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.security.cert.Certificate

class CertificatePinnerTest {
    @Before
    fun setup() {
        CertificatePinner.clearAllPins()
        CertificatePinner.disable()
    }

    @Test
    fun pinEnablesAndDisablesCorrectly() {
        CertificatePinner.disable()
        assertFalse(CertificatePinner.isEnabled)
        CertificatePinner.enable()
        assertTrue(CertificatePinner.isEnabled)
    }

    @Test
    fun verifyReturnsTrueWhenPinningIsDisabled() {
        CertificatePinner.disable()
        assertTrue(CertificatePinner.verify("test.local", emptyList()))
    }

    @Test
    fun verifyReturnsTrueForUnpinnedHost() {
        CertificatePinner.enable()
        assertTrue(CertificatePinner.verify("unpinned.local", emptyList()))
    }

    @Test
    fun generatePinProducesSha256Pin() {
        val pin = CertificatePinner.generatePin(FakeCertificate("certificate-data".toByteArray()))
        assertTrue(pin.startsWith("sha256/"))
        assertTrue(pin.length > "sha256/".length)
    }

    @Test
    fun addPinAndGetExpectedPinsWorkCorrectly() {
        CertificatePinner.addPin("test.host", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        CertificatePinner.addPin("test.host", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        assertEquals(2, CertificatePinner.getExpectedPins("test.host").size)
    }

    @Test
    fun removePinsWorksCorrectly() {
        CertificatePinner.addPin("test.host", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertTrue(CertificatePinner.isPinned("test.host"))
        CertificatePinner.removePins("test.host")
        assertFalse(CertificatePinner.isPinned("test.host"))
    }

    @Test
    fun debugSecurityValidationHelpersWork() {
        assertTrue(DebugSecurityManager.isValidEmail("test@example.com"))
        assertFalse(DebugSecurityManager.isValidEmail("invalid"))
        assertTrue(DebugSecurityManager.isStrongPassword("StrongPass123!"))
        assertFalse(DebugSecurityManager.isStrongPassword("weak"))
        assertEquals("hello", DebugSecurityManager.sanitizeInput("  hello  "))
        assertNull(DebugSecurityManager.sanitizeInput("   "))
        assertTrue(DebugSecurityManager.isValidUuid("550e8400-e29b-41d4-a716-446655440000"))
        assertFalse(DebugSecurityManager.isValidUuid("not-a-uuid"))
        assertEquals(DebugSecurityManager.hashData("test"), DebugSecurityManager.hashData("test"))
    }

    private class FakeCertificate(private val bytes: ByteArray) : Certificate("X.509") {
        override fun getEncoded(): ByteArray = bytes
        override fun verify(key: PublicKey?) = Unit
        override fun verify(key: PublicKey?, sigProvider: String?) = Unit
        override fun toString(): String = "FakeCertificate"
        override fun getPublicKey(): PublicKey? = null
    }
}
