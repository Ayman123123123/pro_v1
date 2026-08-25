package com.red.sovereign.media

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * 🔒 التخزين المشفر للوسائط — كل ملف مشفر بـ AES-GCM 256 قبل الكتابة للقرص
 * المفتاح في Android Keystore، لا يغادر الجهاز أبداً
 * يمنع تسريب الصور/الفيديو حتى لو سُرق الجهاز وفُك تشفيره
 */
class EncryptedMediaCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "encrypted_media").apply { mkdirs() }
    private val alias = "red.media.cache.v1"
    private val key: SecretKey by lazy { loadOrCreateKey() }

    fun put(cacheKey: String, plaintext: ByteArray): File {
        val file = fileFor(cacheKey)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, this.key)
        val encrypted = cipher.iv + cipher.doFinal(plaintext)
        file.writeBytes(encrypted)
        return file
    }

    fun get(cacheKey: String): ByteArray? {
        val file = fileFor(cacheKey)
        if (!file.exists()) return null
        return try {
            val data = file.readBytes()
            require(data.size > 12)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, this.key, GCMParameterSpec(128, data.copyOfRange(0, 12)))
            cipher.doFinal(data.copyOfRange(12, data.size))
        } catch (_: Exception) { null }
    }

    fun exists(key: String): Boolean = fileFor(key).exists()

    fun delete(key: String) { fileFor(key).delete() }

    fun clear() { cacheDir.listFiles()?.forEach { it.delete() } }

    fun size(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun fileFor(key: String): File {
        // Use SHA-256 of key as filename to avoid path traversal
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.enc")
    }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build())
            generateKey()
        }
    }

    companion object { const val TRANSFORMATION = "AES/GCM/NoPadding"; const val IV_SIZE = 12 }
}
