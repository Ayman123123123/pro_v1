package com.red.sovereign.auth

import android.content.Context
import android.os.Build
import com.red.sovereign.core.SecureStore
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom
import java.util.Base64

/** Generates real libsignal identity, signed EC pre-key and Kyber-1024 pre-key locally. */
class DeviceKeyManager(context: Context) {
    private val store = SecureStore(context, "red_device_keys")
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    @Synchronized
    fun enrollment(): DeviceEnrollmentRequest {
        if (!store.contains(IDENTITY_PUBLIC)) generate()
        val signed = signedPreKeyRecord()
        val kyber = kyberPreKeyRecord()
        val registration = store.get(REGISTRATION_ID)?.toInt() ?: (SecureRandom().nextInt(16_380) + 1).also { store.put(REGISTRATION_ID, it.toString()) }
        val deviceId = store.get(PROTOCOL_DEVICE_ID)?.toInt() ?: (SecureRandom().nextInt(127) + 1).also { store.put(PROTOCOL_DEVICE_ID, it.toString()) }
        return DeviceEnrollmentRequest(
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            platform = "ANDROID",
            registrationId = registration,
            protocolDeviceId = deviceId,
            signedPreKeyId = signed.id,
            kyberPreKeyId = kyber.id,
            identityKey = requireValue(IDENTITY_PUBLIC),
            signedPreKey = encode(signed.keyPair.publicKey.serialize()),
            kyberPreKey = encode(kyber.keyPair.publicKey.serialize()),
            signedPreKeySignature = requireValue(SIGNED_SIGNATURE),
            kyberPreKeySignature = requireValue(KYBER_SIGNATURE)
        )
    }

    fun identityKeyPair(): IdentityKeyPair {
        enrollment()
        return IdentityKeyPair(IdentityKey(decoder.decode(requireValue(IDENTITY_PUBLIC))), ECPrivateKey(decoder.decode(requireValue(IDENTITY_PRIVATE))))
    }

    fun registrationId(): Int {
        enrollment()
        store.get(REGISTRATION_ID)?.let { return it.toInt() }
        return (SecureRandom().nextInt(16_380) + 1).also { store.put(REGISTRATION_ID, it.toString()) }
    }

    fun protocolDeviceId(): Int {
        enrollment()
        return requireValue(PROTOCOL_DEVICE_ID).toInt()
    }

    fun signedPreKeyRecord() = SignedPreKeyRecord(decoder.decode(requireValue(SIGNED_PRE_KEY)))
    fun kyberPreKeyRecord() = KyberPreKeyRecord(decoder.decode(requireValue(KYBER_PRE_KEY)))
    fun sign(message: ByteArray): ByteArray = identityKeyPair().privateKey.calculateSignature(message)

    private fun generate() {
        val identity = IdentityKeyPair.generate()
        val random = SecureRandom()
        val signedPair = ECKeyPair.generate()
        val signedSignature = identity.privateKey.calculateSignature(signedPair.publicKey.serialize())
        val signed = SignedPreKeyRecord(random.nextInt(Int.MAX_VALUE), System.currentTimeMillis(), signedPair, signedSignature)

        val kyberPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = identity.privateKey.calculateSignature(kyberPair.publicKey.serialize())
        val kyber = KyberPreKeyRecord(random.nextInt(Int.MAX_VALUE), System.currentTimeMillis(), kyberPair, kyberSignature)

        store.put(IDENTITY_PUBLIC, encode(identity.publicKey.serialize()))
        store.put(IDENTITY_PRIVATE, encode(identity.privateKey.serialize()))
        store.put(SIGNED_PRE_KEY, encode(signed.serialize()))
        store.put(KYBER_PRE_KEY, encode(kyber.serialize()))
        store.put(SIGNED_SIGNATURE, encode(signedSignature))
        store.put(KYBER_SIGNATURE, encode(kyberSignature))
        store.put(REGISTRATION_ID, (random.nextInt(16_380) + 1).toString())
    }

    private fun encode(value: ByteArray) = encoder.encodeToString(value)
    private fun requireValue(key: String) = requireNotNull(store.get(key)) { "Missing local device key: $key" }

    private companion object {
        const val IDENTITY_PUBLIC = "identity_public"
        const val IDENTITY_PRIVATE = "identity_private"
        const val SIGNED_PRE_KEY = "signed_pre_key"
        const val KYBER_PRE_KEY = "kyber_pre_key"
        const val SIGNED_SIGNATURE = "signed_signature"
        const val KYBER_SIGNATURE = "kyber_signature"
        const val REGISTRATION_ID = "registration_id"
        const val PROTOCOL_DEVICE_ID = "protocol_device_id"
    }
}
