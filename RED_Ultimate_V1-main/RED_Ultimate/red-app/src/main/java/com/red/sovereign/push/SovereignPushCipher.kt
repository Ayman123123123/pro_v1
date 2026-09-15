package com.red.sovereign.push

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Sovereign push wake cipher - AES-256-GCM keyed by the UnifiedPush endpoint URL.
 *
 * The endpoint URL is the per-device bearer capability the app and the backend
 * already share (app: TokenStore.pushEndpoint, server: device_push_tokens.token),
 * so no extra secret has to be provisioned. The distributor (self-hosted ntfy)
 * only ever relays an opaque blob; the wake carries identifiers only, never a
 * caller name, a message preview, or any human-readable content.
 *
 * Wire envelope (server: com.red.server.notification.SovereignPushCipher):
 *   {"v":2,"e":"<base64url(nonce[12] || ciphertext||tag16)>"}
 *
 * KEY_SALT / NONCE_SIZE / TAG_BITS / base64 flags must stay byte-identical with
 * the server implementation, otherwise the wake simply fails to open.
 */
object SovereignPushCipher {
    const val VERSION = 2
    const val ENVELOPE_TYPE = "e"

    private const val KEY_SALT = "red-sovereign-push-v1|"
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

    /** Decrypts [sealedB64] with the key derived from [endpoint]. Null on any failure. */
    fun open(endpoint: String, sealedB64: String): String? {
        val data = runCatching { Base64.decode(sealedB64, FLAGS) }.getOrNull() ?: return null
        if (data.size <= NONCE_SIZE) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyFor(endpoint),
                GCMParameterSpec(TAG_BITS, data.copyOfRange(0, NONCE_SIZE))
            )
            String(cipher.doFinal(data.copyOfRange(NONCE_SIZE, data.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Seals [plaintext] into a base64url body for [endpoint] (test/debug helper). */
    fun seal(endpoint: String, plaintext: String): String {
        val nonce = ByteArray(NONCE_SIZE).also(rng::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(endpoint), GCMParameterSpec(TAG_BITS, nonce))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(nonce.size + ct.size)
        System.arraycopy(nonce, 0, out, 0, nonce.size)
        System.arraycopy(ct, 0, out, nonce.size, ct.size)
        return Base64.encodeToString(out, FLAGS)
    }

    private fun keyFor(endpoint: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest((KEY_SALT + endpoint.trim()).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(key, "AES")
    }
}
