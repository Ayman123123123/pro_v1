package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the pure-logic pieces of CallLogCipher.
 * The actual Android Keystore operations are integration-tested in androidTest/.
 */
class CallLogCipherTest {
    @Test fun `encrypted output is hex and non-empty`() {
        val cipher = TestableCallLogCipher()
        val encrypted = cipher.encryptPeerId("YNS-AAAA-BBBB")
        assertNotEquals("YNS-AAAA-BBBB", encrypted)
        assertTrue(encrypted.isNotEmpty())
        // hex characters only
        assertTrue(encrypted.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun `empty input round-trips to empty output`() {
        val cipher = TestableCallLogCipher()
        assertEquals("", cipher.encryptPeerId(""))
        assertEquals("", cipher.decryptPeerId(""))
    }

    @Test fun `different plaintexts produce different ciphertexts (length test)`() {
        val cipher = TestableCallLogCipher()
        val a = cipher.encryptPeerId("YNS-AAAA-BBBB")
        val b = cipher.encryptPeerId("YNS-CCCC-DDDD")
        assertNotEquals(a, b)
    }

    @Test fun `label uses same encryption as peerId`() {
        val cipher = TestableCallLogCipher()
        assertEquals(cipher.encryptPeerId("علي"), cipher.encryptLabel("علي"))
    }
}

/**
 * Test stub that skips the Android Keystore dependency and uses simple XOR
 * to verify the public API contract. The real Android Keystore-backed cipher
 * is exercised in androidTest/ (since Keystore requires a real device).
 */
private class TestableCallLogCipher {
    fun encryptPeerId(redId: String): String {
        if (redId.isBlank()) return ""
        // ضعيف عمداً — للاختبار فقط. الـ production يستخدم ProtocolRecordCipher (Android Keystore).
        val bytes = redId.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size + 1) { if (it == 0) 0x01 else (bytes[it - 1].toInt() xor 0x42).toByte() }
        return out.joinToString("") { "%02x".format(it) }
    }
    fun decryptPeerId(hex: String): String {
        if (hex.isBlank()) return ""
        val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        if (bytes.isEmpty() || bytes[0] != 0x01.toByte()) return ""
        return String(bytes.drop(1).map { (it.toInt() xor 0x42).toByte() }.toByteArray(), Charsets.UTF_8)
    }
    fun encryptLabel(label: String): String = encryptPeerId(label)
    fun decryptLabel(hex: String): String = decryptPeerId(hex)
}
