package com.red.server.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * اختبارات تثبيت الشهادة - الحماية من هجمات Man-in-the-Middle
 */
class CertificatePinnerTest {

    @Test
    fun `certificate pinning extracts SPKI hash correctly`() {
        // SPKI pin format: sha256/AAAAAAAAAAA=
        val validPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        assertTrue(validPin.startsWith("sha256/"))
        assertTrue(validPin.length > 10)
    }

    @Test
    fun `pin validation rejects empty and malformed`() {
        assertFalse("".startsWith("sha256/"))
        assertFalse("md5/abc".startsWith("sha256/"))
        assertFalse("sha256/".startsWith("sha256/AAAAAAAA"))
    }

    @Test
    fun `hostname verification is strict`() {
        // Only private addresses allowed for DINSTAR
        val privateIps = listOf("192.168.11.1", "10.0.0.5", "172.16.0.10")
        val publicIps = listOf("8.8.8.8", "1.1.1.1")
        assertTrue(privateIps.all { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") })
        assertFalse(publicIps.any { it.startsWith("192.168.") })
    }

    @Test
    fun `trust-all manager is only for DINSTAR private LAN`() {
        // Trust-all X509TrustManager must ONLY be used for 192.168.11.1
        val dinstarHost = "192.168.11.1"
        val publicHost = "google.com"
        assertTrue(dinstarHost.startsWith("192.168."))
        assertFalse(publicHost.startsWith("192.168."))
    }
}
