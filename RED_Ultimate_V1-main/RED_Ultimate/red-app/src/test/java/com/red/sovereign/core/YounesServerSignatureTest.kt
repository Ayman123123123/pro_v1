package com.red.sovereign.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YounesServerSignatureTest {
    @Test
    fun `production health and authority are accepted`() {
        val health = """{"brand":"YOUNES","status":"UP","version":"1.0.0-YOUNES"}"""
        val authority = """{"algorithm":"ECDSA_P256_SHA256","version":"v1","publicKey":"abc"}"""
        assertTrue(YounesServerSignature.isYounesHealth(health))
        assertTrue(YounesServerSignature.isYounesAuthority(authority))
        assertTrue(YounesServerSignature.isYounesServer(health, authority))
    }

    @Test
    fun `sqlite mock health is rejected`() {
        val health = """{"status":"UP","service":"red-dev-server","db":"sqlite"}"""
        val authority = """{"publicKey":"abc","algorithm":"SHA256withECDSA","curve":"prime256v1"}"""
        assertFalse(YounesServerSignature.isYounesHealth(health))
        assertFalse(YounesServerSignature.isYounesAuthority(authority))
        assertFalse(YounesServerSignature.isYounesServer(health, authority))
    }

    @Test
    fun `degraded postgres-up health is still a found server`() {
        val health = """{"brand":"YOUNES","status":"DEGRADED","version":"1.0.0-YOUNES"}"""
        val authority = """{"algorithm":"ECDSA_P256_SHA256","version":"v1","publicKey":"abc"}"""
        assertTrue(YounesServerSignature.isYounesServer(health, authority))
    }

    @Test
    fun `random up health without younes identity is rejected`() {
        val health = """{"status":"UP"}"""
        val authority = """{"ok":true}"""
        assertFalse(YounesServerSignature.isYounesServer(health, authority))
    }

    @Test
    fun `down health is never accepted`() {
        val health = """{"brand":"YOUNES","status":"DOWN","version":"1.0.0-YOUNES"}"""
        val authority = """{"algorithm":"ECDSA_P256_SHA256","version":"v1","publicKey":"abc"}"""
        assertFalse(YounesServerSignature.isYounesServer(health, authority))
    }

    @Test
    fun `ports always try api then dashboard`() {
        assertEquals(listOf(8088, 8443), YounesServerSignature.ports(8088))
        assertEquals(listOf(8088, 8443), YounesServerSignature.ports(0))
    }

    @Test
    fun `base url keeps scheme and injects port`() {
        assertEquals("http://10.0.2.2:8080", YounesServerSignature.baseUrl("10.0.2.2", 8080))
        assertEquals("http://192.168.1.50:8088", YounesServerSignature.baseUrl("http://192.168.1.50:8088", 8080))
    }
}
