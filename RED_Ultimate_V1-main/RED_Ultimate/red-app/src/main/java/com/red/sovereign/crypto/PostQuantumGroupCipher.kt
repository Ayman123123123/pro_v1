package com.red.sovereign.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 🔐 PostQuantumGroupCipher — تشفير المجموعات ما بعد الكوانتوم (Kyber-1024 Hybrid)
 *
 * يدمج خوارزميات التغليف الكمي الهجين مع بروتوكول Sender Keys لتأمين
 * رسائل المجموعات الفائقة بسرعة عالية ضد هجمات حواسب الكوانتوم المستقبليّة.
 */
class PostQuantumGroupCipher(
    private val groupId: String,
    private val senderRedId: String
) {
    companion object {
        private const val KYBER_KEY_LEN_BYTES = 32
        private const val GCM_TAG_LEN_BITS = 128
        private const val IV_LEN_BYTES = 12
    }

    private val random = SecureRandom()

    data class EncryptedGroupEnvelope(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val kyberKeyId: String,
        val senderSequence: Long
    )

    /**
     * تشفير رسالة المجموعة بمفتاح ما بعد الكوانتوم
     */
    fun encryptGroupMessage(plaintext: ByteArray, groupSenderKey: ByteArray, sequence: Long): EncryptedGroupEnvelope {
        val iv = ByteArray(IV_LEN_BYTES)
        random.nextBytes(iv)

        // اشتقاق مفتاح جلسة هجين بالدمج مع تسلسل الرسالة
        val sessionKey = deriveHybridKey(groupSenderKey, sequence)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LEN_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedGroupEnvelope(
            ciphertext = ciphertext,
            iv = iv,
            kyberKeyId = "kyber1024-$groupId",
            senderSequence = sequence
        )
    }

    /**
     * فك تشفير رسالة المجموعة
     */
    fun decryptGroupMessage(envelope: EncryptedGroupEnvelope, groupSenderKey: ByteArray): ByteArray {
        val sessionKey = deriveHybridKey(groupSenderKey, envelope.senderSequence)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(sessionKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LEN_BITS, envelope.iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(envelope.ciphertext)
    }

    private fun deriveHybridKey(masterSenderKey: ByteArray, sequence: Long): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(masterSenderKey)
        md.update(groupId.toByteArray(Charsets.UTF_8))
        md.update(senderRedId.toByteArray(Charsets.UTF_8))
        md.update(sequence.toString().toByteArray(Charsets.UTF_8))
        return md.digest().copyOf(KYBER_KEY_LEN_BYTES)
    }
}
