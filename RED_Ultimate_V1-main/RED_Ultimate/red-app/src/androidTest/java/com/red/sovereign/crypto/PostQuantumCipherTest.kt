package com.red.sovereign.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostQuantumCipherTest {

    @Test
    fun testEncryptionDecryption() {
        val cipherManager = PostQuantumGroupCipherManager()
        val key = cipherManager.generateGroupKey()
        assertNotNull(key)

        val originalText = "Secret group message protected by 20 AI Agents and Post-Quantum E2EE"
        val encrypted = cipherManager.encryptMessage(originalText, key)
        assertNotNull(encrypted)

        val decrypted = cipherManager.decryptMessage(encrypted, key)
        assertEquals(originalText, decrypted)
    }
}
