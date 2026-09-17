package com.red.server.notification

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sovereign push wake cipher - AES-256-GCM keyed per device.
 *
 * Legacy key: SHA-256("red-sovereign-push-v1|" + endpoint). The endpoint URL is
 * the per-device bearer capability both sides already hold (device: its
 * UnifiedPush endpoint, server: device_push_tokens.token), so no extra secret
 * had to be provisioned. But an endpoint URL is low-entropy, often logged by
 * proxies and reversible to the distributor, so it is a weak sole key source.
 *
 * Rotation (v2 domain): each device gets a fresh 256-bit random secret from
 * [generateDeviceSecret], stored server-side next to the token
 * (device_push_tokens.secret, nullable) and provisioned to the device at
 * registration. The rotated key mixes that secret in as a salt:
 *   rotated key = SHA-256("red-sovereign-push-v2|" + secret + "|" + endpoint)
 * [open] with a secret tries the rotated key first and falls back to the legacy
 * key, so wakes sealed before rotation still open. Retire the fallback only
 * after every device has rotated AND the Android twin
 * (com.red.sovereign.push.SovereignPushCipher) mirrors this derivation.
 *
 * Wire envelope (client: com.red.sovereign.push.SovereignPushCipher):
 *   {@code {"v":2,"e":"<base64url(nonce[12] || ciphertext||tag16)>"}}
 * The envelope format is unchanged by rotation - only the key input changes.
 *
 * KEY_SALT / ROTATED_KEY_SALT / NONCE_SIZE / TAG_BITS / base64 flags must stay
 * byte-identical with the Android implementation, otherwise the device cannot
 * open the wake.
 */
object SovereignPushCipher {
    const val VERSION = 2
    const val ENVELOPE_TYPE = "e"

    private const val KEY_SALT = "red-sovereign-push-v1|"
    private const val ROTATED_KEY_SALT = "red-sovereign-push-v2|"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val DEVICE_SECRET_BYTES = 32

    private val rng = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /**
     * Generates a fresh per-device secret (256-bit, base64url no padding).
     * Store it alongside the push token and provision it to the device once;
     * it is used as salt in [keyFor], never sent to the distributor.
     */
    fun generateDeviceSecret(): String =
        encoder.encodeToString(ByteArray(DEVICE_SECRET_BYTES).also(rng::nextBytes))

    /** Seals [plaintext] with the legacy endpoint-derived key (backward compat). */
    fun seal(endpoint: String, plaintext: String): String =
        seal(endpoint, plaintext, deviceSecret = null)

    /** Seals [plaintext]; uses the rotated key when [deviceSecret] is provided. */
    fun seal(endpoint: String, plaintext: String, deviceSecret: String?): String {
        val nonce = ByteArray(NONCE_SIZE).also(rng::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(endpoint, deviceSecret), GCMParameterSpec(TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(nonce.size + ct.size)
        System.arraycopy(nonce, 0, out, 0, nonce.size)
        System.arraycopy(ct, 0, out, nonce.size, ct.size)
        return encoder.encodeToString(out)
    }

    /** Decrypts [sealedB64] with the legacy key. Null on any failure. */
    fun open(endpoint: String, sealedB64: String): String? =
        open(endpoint, sealedB64, deviceSecret = null)

    /**
     * Decrypts [sealedB64], trying the rotated key first and falling back to the
     * legacy endpoint-derived key, so pre-rotation wakes still open during the
     * rotation window. Null on any failure.
     */
    fun open(endpoint: String, sealedB64: String, deviceSecret: String?): String? {
        if (!deviceSecret.isNullOrBlank()) {
            openWithKey(keyFor(endpoint, deviceSecret), sealedB64)?.let { return it }
        }
        return openWithKey(keyFor(endpoint), sealedB64)
    }

    /** Full v2 envelope JSON ready to POST to the distributor (legacy key). */
    fun envelope(endpoint: String, plaintext: String): String =
        envelope(endpoint, plaintext, deviceSecret = null)

    /** Full v2 envelope JSON; uses the rotated key when [deviceSecret] is provided. */
    fun envelope(endpoint: String, plaintext: String, deviceSecret: String?): String =
        "{\"v\":$VERSION,\"$ENVELOPE_TYPE\":\"${seal(endpoint, plaintext, deviceSecret)}\"}"

    /** Legacy key derivation - byte-identical, do not change (backward compat). */
    fun keyFor(endpoint: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest((KEY_SALT + endpoint.trim()).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(key, "AES")
    }

    /**
     * Rotated key derivation: the high-entropy per-device [deviceSecret] acts as
     * salt in a separate ("...-v2|") domain so rotated keys can never collide
     * with legacy ones. Blank/null secret falls back to the legacy derivation.
     */
    fun keyFor(endpoint: String, deviceSecret: String?): SecretKeySpec {
        if (deviceSecret.isNullOrBlank()) return keyFor(endpoint)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ROTATED_KEY_SALT.toByteArray(Charsets.UTF_8))
        digest.update(deviceSecret.trim().toByteArray(Charsets.UTF_8))
        digest.update("|".toByteArray(Charsets.UTF_8))
        digest.update(endpoint.trim().toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun openWithKey(key: SecretKeySpec, sealedB64: String): String? = runCatching {
        val data = decoder.decode(sealedB64)
        check(data.size > NONCE_SIZE) { "sealed payload too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data.copyOfRange(0, NONCE_SIZE)))
        String(cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)), Charsets.UTF_8)
    }.getOrNull()
}
