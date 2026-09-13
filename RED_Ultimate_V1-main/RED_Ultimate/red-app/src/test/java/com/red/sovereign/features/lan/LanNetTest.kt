package com.red.sovereign.features.lan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanNetTest {

    @Test
    fun `prefix24 parses valid ipv4`() {
        assertEquals("192.168.1", LanNet.prefix24Of("192.168.1.23"))
        assertEquals("10.0.0", LanNet.prefix24Of("10.0.0.5"))
    }

    @Test
    fun `prefix24 rejects garbage`() {
        assertNull(LanNet.prefix24Of("not-an-ip"))
        assertNull(LanNet.prefix24Of("192.168.1"))
        assertNull(LanNet.prefix24Of("192.168.1.999"))
        assertNull(LanNet.prefix24Of(""))
    }

    @Test
    fun `sameSubnet matches slash24`() {
        assertTrue(LanNet.sameSubnet("192.168.1.23", "192.168.1.87"))
        assertFalse(LanNet.sameSubnet("192.168.1.23", "192.168.2.23"))
        assertFalse(LanNet.sameSubnet("192.168.1.23", "garbage"))
    }

    @Test
    fun `private ranges recognized`() {
        assertTrue(LanNet.isPrivateIpv4("10.4.5.6"))
        assertTrue(LanNet.isPrivateIpv4("172.16.0.9"))
        assertTrue(LanNet.isPrivateIpv4("172.31.255.1"))
        assertTrue(LanNet.isPrivateIpv4("192.168.11.4"))
        assertFalse(LanNet.isPrivateIpv4("172.32.0.1"))
        assertFalse(LanNet.isPrivateIpv4("8.8.8.8"))
        assertFalse(LanNet.isPrivateIpv4("1.2.3"))
    }
}
