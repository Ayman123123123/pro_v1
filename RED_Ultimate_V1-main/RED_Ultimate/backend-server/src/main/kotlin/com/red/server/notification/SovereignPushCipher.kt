package com.red.server.notification

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sovereign push wake cipher - AES-256-GCM keyed by the UnifiedPush endpoint URL.
 *
 * The endpoint URL is the per-device bearer capability both sides already hold
 * (device: its UnifiedPush endpoint, server: device_push_tokens.token), so no
 * extra secret has to be provisioned or rotated out of band. The distributor
 * (self-hosted ntfy) only ever relays an opaque blob: the wake carries
 * identifiers only, never a caller name or a message preview.
 *
 * Wire envelope (client: com.red.sovereign.push.SovereignPushCipher):
 *   {@code {"v":2,"e":"<base64url(nonce[12] || ciphertext||tag16)>"}}
 *
 * KEY_SALT / NONCE_SIZE / TAG_BITS / base64 flags must stay byte-identical with
 * the Android implementation, otherwise the device cannot open the wake.
 */
object SovereignPushCipher {
    const val VERSION = 2
    const val ENVELOPE_TYPE = "e"

    private const val KEY_SALT = "red-sovereign-push-v1|"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128

    private val rng = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** Seals [plaintext] into a base64url body keyed by [endpoint]. */
    fun seal(endpoint: String, plaintext: String): String {
        val nonce = ByteArray(NONCE_SIZE).also(rng::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(endpoint), GCMParameterSpec(TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(nonce.size + ct.size)
        System.arraycopy(nonce, 0, out, 0, nonce.size)
        System.arraycopy(ct, 0, out, nonce.size, ct.size)
        return encoder.encodeToString(out)
    }

    /** Decrypts [sealedB64] with the key derived from [endpoint]. Null on any failure. */
    fun open(endpoint: String, sealedB64: String): String? = runCatching {
        val data = decoder.decode(sealedB64)
        check(data.size > NONCE_SIZE) { "sealed payload too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyFor(endpoint), GCMParameterSpec(TAG_BITS, data.copyOfRange(0, NONCE_SIZE)))
        String(cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)), Charsets.UTF_8)
    }.getOrNull()

    /** Full v2 envelope JSON ready to POST to the distributor. */
    fun envelope(endpoint: String, plaintext: String): String =
        "{\"v\":$VERSION,\"$ENVELOPE_TYPE\":\"${seal(endpoint, plaintext)}\"}"

    fun keyFor(endpoint: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest((KEY_SALT + endpoint.trim()).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(key, "AES")
    }
}
