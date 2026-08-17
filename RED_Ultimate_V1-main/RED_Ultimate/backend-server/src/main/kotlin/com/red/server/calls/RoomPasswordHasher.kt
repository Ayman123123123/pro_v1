package com.red.server.calls

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hasher كلمات مرور الغرف (PBKDF2-SHA256) — أمان احترافي لـ Conference/LiveStream.
 * الصيغة: pbkdf2-sha256-v1$iterations$saltBase64$hashBase64
 */
@Component
class RoomPasswordHasher {

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 210_000
        private const val SALT_LENGTH = 16
        private const val HASH_LENGTH = 32
        private const val FORMAT_VERSION = "pbkdf2-sha256-v1"
    }

    private val random = SecureRandom()
    private val keyFactory = SecretKeyFactory.getInstance(ALGORITHM)

    /**
     * يهش كلمة المرور مع salt عشوائي.
     * @return سلسلة بصيغة: FORMAT_VERSION$iterations$saltBase64$hashBase64
     */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_LENGTH * 8)
        val hash = keyFactory.generateSecret(spec).encoded
        return "$FORMAT_VERSION$ITERATIONS${Base64.getEncoder().encodeToString(salt)}${Base64.getEncoder().encodeToString(hash)}"
    }

    /**
     * يتحقق من كلمة مرور ضد هاش مخزن.
     * يقارن بطريقة ثابتة الزمن (MessageDigest.isEqual) لمنع توقيت الهجوم.
     */
    fun verify(password: String, storedHash: String): Boolean {
        val parts = storedHash.split('$', limit = 4)
        if (parts.size != 4) return false
        if (parts[0] != FORMAT_VERSION) return false
        
        val iterations = parts[1].toIntOrNull() ?: ITERATIONS
        val salt = Base64.getDecoder().decode(parts[2])
        val expectedHash = Base64.getDecoder().decode(parts[3])
        
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, expectedHash.size * 8)
        val computedHash = keyFactory.generateSecret(spec).encoded
        
        return MessageDigest.isEqual(computedHash, expectedHash)
    }

    /**
     * يولد salt عشوائي جديد (للاستخدامات التي تحتاج salt يدوي).
     */
    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
}