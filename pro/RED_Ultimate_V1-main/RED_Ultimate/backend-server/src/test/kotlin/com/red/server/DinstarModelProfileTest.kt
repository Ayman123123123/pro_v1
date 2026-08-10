package com.red.server

import com.red.server.services.DinstarModelProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DinstarModelProfileTest {
    @Test
    fun `8G profile is explicitly GSM only and exposes eight ports`() {
        val profile = DinstarModelProfile.parse("UC2000-VE-8G")
        assertEquals(8, profile.portCount)
        assertEquals(0..7, profile.portRange)
        assertFalse(profile.supportsVolte)
        assertTrue(profile.radioCapability.startsWith("GSM"))
    }

    @Test
    fun `8T remains a distinct VoLTE profile and unknown labels are rejected`() {
        val profile = DinstarModelProfile.parse("uc2000-ve-8t")
        assertTrue(profile.supportsVolte)
        assertThrows(IllegalArgumentException::class.java) { DinstarModelProfile.parse("UC2000-VE-16T") }
    }
}
