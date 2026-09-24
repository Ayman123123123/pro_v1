package com.red.sovereign.push

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Sovereign push wake cipher - AES-256-GCM.
 *
 * v2 (current server mirror): key = SHA-256("red-sovereign-push-v2|" + secret + "|" + endpoint).
 *   The per-device [secret] is a random 256-bit value generated once by
 *   VoipPushRegistrar and uploaded with the endpoint; the endpoint URL is the
 *   per-device bearer capability both sides already hold.
 * v1 (legacy rotation window): key = SHA-256("red-sovereign-push-v1|" + endpoint).
 *   Still accepted on open so wakes sealed before the secret rotation keep working.
 *
 * Wire envelope (server: com.red.server.notification.SovereignPushCipher):
 *   {"v":2,"e":"<base64url(nonce[12] || ciphertext||tag16)>"}
 *
 * KEY_SALT_V1 / KEY_SALT_V2 / NONCE_SIZE / TAG_BITS / base64 flags must stay
 * byte-identical with the server implementation, otherwise the wake fails to open.
 */
object SovereignPushCipher {
    const val VERSION = 2
    const val ENVELOPE_TYPE = "e"

    private const val KEY_SALT_V1 = "red-sovereign-push-v1|"
    private const val KEY_SALT_V2 = "red-sovereign-push-v2|"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    private val rng = SecureRandom()

    /** Extracts the base64 body of a v2 envelope, or null when [text] is not one. */
    fun envelopeBody(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("{")) return null
        return runCatching {
            JSONObject(t).optString(ENVELOPE_TYPE).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Opens [sealedB64]: tries v2 with [secret] first, then falls back to v1.
     * Null on any failure. Null/blank [secret] means v1 only (pre-rotation device).
     */
    fun open(endpoint: String, secret: String?, sealedB64: String): String? {
        if (!secret.isNullOrBlank()) {
            openV2(endpoint, secret, sealedB64)?.let { return it }
        }
        return openV1(endpoint, sealedB64)
    }

    /** Legacy v1 open (endpoint only). Kept for the rotation window. */
    fun open(endpoint: String, sealedB64: String): String? = openV1(endpoint, sealedB64)

    /** v2 open with the per-device secret. Null on any failure. */
    fun openV2(endpoint: String, secret: String, sealedB64: String): String? {
        val data = runCatching { Base64.decode(sealedB64, FLAGS) }.getOrNull() ?: return null
        if (data.size <= NONCE_SIZE) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyForV2(endpoint, secret),
                GCMParameterSpec(TAG_BITS, data.copyOfRange(0, NONCE_SIZE))
            )
            String(cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    /** v1 open with the endpoint only. Null on any failure. */
    fun openV1(endpoint: String, sealedB64: String): String? {
        val data = runCatching { Base64.decode(sealedB64, FLAGS) }.getOrNull() ?: return null
        if (data.size <= NONCE_SIZE) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyForV1(endpoint),
                GCMParameterSpec(TAG_BITS, data.copyOfRange(0, NONCE_SIZE))
            )
            String(cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Seals [plaintext] into a base64url body for [endpoint] (v1 test/debug helper). */
    fun seal(endpoint: String, plaintext: String): String {
        val nonce = ByteArray(NONCE_SIZE).also(rng::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyForV1(endpoint), GCMParameterSpec(TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(nonce.size + ct.size)
        System.arraycopy(nonce, 0, out, 0, nonce.size)
        System.arraycopy(ct, 0, out, nonce.size, ct.size)
        return Base64.encodeToString(out, FLAGS)
    }

    /** Seals [plaintext] with the v2 key (test/debug helper, mirrors the server). */
    fun sealV2(endpoint: String, secret: String, plaintext: String): String {
        val nonce = ByteArray(NONCE_SIZE).also(rng::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyForV2(endpoint, secret), GCMParameterSpec(TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(nonce.size + ct.size)
        System.arraycopy(nonce, 0, out, 0, nonce.size)
        System.arraycopy(ct, 0, out, nonce.size, ct.size)
        return Base64.encodeToString(out, FLAGS)
    }

    /** v2 key mirror: SHA-256("red-sovereign-push-v2|" + secret + "|" + endpoint). */
    fun keyForV2(endpoint: String, secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val material = KEY_SALT_V2 + secret + "|" + endpoint.trim()
        return SecretKeySpec(digest.digest(material.toByteArray(Charsets.UTF_8)), "AES")
    }

    /** v1 key (legacy): SHA-256("red-sovereign-push-v1|" + endpoint). */
    fun keyForV1(endpoint: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest((KEY_SALT_V1 + endpoint.trim()).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(key, "AES")
    }
}
