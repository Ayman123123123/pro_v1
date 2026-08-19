package com.red.server.calls

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted, deliberately expensive password verifier for private live rooms. */
internal object RoomPasswordHasher {
    private const val VERSION = "pbkdf2-sha256-v1"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun hash(password: String): String {
        require(password.length in 4..128) { "Room password must contain 4-128 characters" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(password, salt, ITERATIONS)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(VERSION, ITERATIONS.toString(), encoder.encodeToString(salt), encoder.encodeToString(derived)).joinToString("\$")
    }

    fun verify(password: String?, encoded: String?): Boolean {
        if (password.isNullOrEmpty() || encoded.isNullOrBlank()) return false
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 100_000..1_000_000 } ?: return false
        return runCatching {
            val decoder = Base64.getUrlDecoder()
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            salt.size >= SALT_BYTES && expected.size == KEY_BITS / 8 &&
                MessageDigest.isEqual(expected, derive(password, salt, iterations))
        }.getOrDefault(false)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
