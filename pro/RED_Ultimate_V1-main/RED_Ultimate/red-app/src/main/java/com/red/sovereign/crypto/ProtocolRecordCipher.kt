package com.red.sovereign.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts durable libsignal records before SQLite using a non-exportable Android Keystore key. */
class ProtocolRecordCipher {
    private val alias = "red.signal.records.v1"
    private val key: SecretKey by lazy { loadOrCreate() }

    fun encrypt(value: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(value)
    }

    fun decrypt(value: ByteArray): ByteArray {
        require(value.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, value.copyOfRange(0, IV_SIZE)))
        return cipher.doFinal(value.copyOfRange(IV_SIZE, value.size))
    }

    private fun loadOrCreate(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private companion object { const val TRANSFORMATION = "AES/GCM/NoPadding"; const val IV_SIZE = 12 }
}
