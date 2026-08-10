package com.red.sovereign.crypto

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.DeviceKeyManager
import com.red.sovereign.auth.TokenStore
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.security.MessageDigest
import java.util.Base64

class SignalSessionManager(context: Context) {
    private val tokens = TokenStore(context)
    private val keys = DeviceKeyManager(context)
    private val store = PersistentSignalProtocolStore(context, keys)
    private val directory = IdentityDirectoryApi(AuthorizedApiClient(tokens))
    private val preKeyPool = PreKeyPoolManager(context)
    private val decoder = Base64.getDecoder()

    suspend fun replenishPreKeys(): ApiResult<PreKeyStock> = preKeyPool.replenishIfNeeded()

    suspend fun encrypt(remoteRedId: String, plaintext: ByteArray): ApiResult<List<EncryptedEnvelope>> {
        require(plaintext.isNotEmpty() && plaintext.size <= 256 * 1024)
        val result = directory.get(remoteRedId)
        if (result is ApiResult.Error) return result
        result as ApiResult.Success
        if (result.value.devices.isEmpty()) return ApiResult.Error(404, "NO_APPROVED_REMOTE_DEVICE")
        val envelopes = mutableListOf<EncryptedEnvelope>()
        for (directoryDevice in result.value.devices) {
            val remote = SignalProtocolAddress(remoteRedId, directoryDevice.protocolDeviceId)
            if (!store.containsSession(remote)) {
                val consumed = directory.consumePreKey(remoteRedId, directoryDevice.deviceId)
                if (consumed is ApiResult.Error) return consumed
                val device = (consumed as ApiResult.Success).value
                val oneTimeKey = device.oneTimePreKey?.let { ECPublicKey(decoder.decode(it)) }
                val bundle = PreKeyBundle(
                    device.registrationId, device.protocolDeviceId,
                    device.oneTimePreKeyId ?: PreKeyBundle.NULL_PRE_KEY_ID, oneTimeKey,
                    device.signedPreKeyId, ECPublicKey(decoder.decode(device.signedPreKey)), decoder.decode(device.signedPreKeySignature),
                    IdentityKey(decoder.decode(device.identityKey)), device.kyberPreKeyId,
                    KEMPublicKey(decoder.decode(device.kyberPreKey)), decoder.decode(device.kyberPreKeySignature)
                )
                SessionBuilder(store, remote).process(bundle)
            }
            val ciphertext = SessionCipher(store, remote).encrypt(plaintext)
            envelopes += EncryptedEnvelope(directoryDevice.protocolDeviceId, ciphertext.type, ciphertext.serialize())
        }
        return ApiResult.Success(result.code, envelopes)
    }

    fun decrypt(senderRedId: String, senderDeviceId: Int, ciphertextType: Int, ciphertext: ByteArray): ByteArray {
        val remote = SignalProtocolAddress(senderRedId, senderDeviceId)
        val cipher = SessionCipher(store, remote)
        return when (ciphertextType) {
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(ciphertext))
            CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(ciphertext))
            else -> throw IllegalArgumentException("Unsupported ciphertext type")
        }
    }

    fun safetyNumber(remoteRedId: String, remoteIdentityKey: ByteArray): String {
        val local = keys.identityKeyPair().publicKey.serialize()
        val ordered = if (tokens.redId.orEmpty() <= remoteRedId) local + remoteIdentityKey else remoteIdentityKey + local
        val digest = MessageDigest.getInstance("SHA-256").digest(ordered)
        val digits = digest.joinToString("") { ((it.toInt() and 0xff) % 100).toString().padStart(2, '0') }.take(60)
        return digits.chunked(5).joinToString(" ")
    }

}

data class EncryptedEnvelope(val receiverDeviceId: Int, val ciphertextType: Int, val bytes: ByteArray)
