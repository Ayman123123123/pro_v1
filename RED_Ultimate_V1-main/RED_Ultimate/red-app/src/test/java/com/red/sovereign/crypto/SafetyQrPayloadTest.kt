package com.red.sovereign.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafetyQrPayloadTest {
    private val fingerprint = "ab".repeat(32)
    private val number = "12345".repeat(12)

    @Test
    fun `parses strict versioned YOUNES safety payload`() {
        val parsed = SafetyQrPayload.parse(
            "younes-safety-v1|46764|62908|94|$fingerprint|$number"
        )
        requireNotNull(parsed)
        assertEquals("46764", parsed.sourceRedId)
        assertEquals("62908", parsed.targetRedId)
        assertEquals(94, parsed.targetDeviceId)
        assertEquals(number, parsed.safetyNumber)
    }

    @Test
    fun `rejects malformed foreign and incomplete payloads`() {
        assertNull(SafetyQrPayload.parse("https://example.invalid"))
        assertNull(SafetyQrPayload.parse("younes-safety-v1|46764|62908|0|$fingerprint|$number"))
        assertNull(SafetyQrPayload.parse("younes-safety-v2|46764|62908|94|$fingerprint|$number"))
        assertNull(SafetyQrPayload.parse("younes-safety-v1|46764|62908|94|short|$number"))
    }
}
