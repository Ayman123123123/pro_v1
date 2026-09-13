package com.red.sovereign.features.lan

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.DeviceKeyManager
import com.red.sovereign.crypto.PersistentSignalProtocolStore
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage

/**
 * P2-LAN — ختم إشارة LAN بجلسات Signal القائمة (بلا خادم).
 *
 * - يعمل دون إنترنت: يستخدم فقط الجلسات المحفوظة محليا (أُنشئت سابقا عبر
 *   الخادم عند التراسل مع النظير). لا X3DH جديد هنا — لا directory ولا prekeys.
 * - يُجرّب deviceIds ‏1..5‎ ويستخدم أول جلسة موجودة.
 * - الفشل = fallback صريح غير مختوم (الوسائط تبقى DTLS-SRTP دائما).
 */
class LanSignalCrypto(context: Context) {

    data class Sealed(val deviceId: Int, val type: Int, val bytes: ByteArray)

    companion object {
        private const val TAG = "LanSignalCrypto"
        private const val MAX_PROBE_DEVICE = 5
    }

    private val keys = DeviceKeyManager(context.applicationContext)
    private val store = PersistentSignalProtocolStore(context.applicationContext, keys)

    /** ختم حمولة (SDP) لجلسة قائمة، أو null إن لا جلسة (fallback علني). */
    fun seal(remoteRedId: String, plaintext: ByteArray): Sealed? {
        if (plaintext.isEmpty()) return null
        val redId = remoteRedId.trim().uppercase()
        for (deviceId in 1..MAX_PROBE_DEVICE) {
            val addr = SignalProtocolAddress(redId, deviceId)
            val has = runCatching { store.containsSession(addr) }.getOrDefault(false)
            if (!has) continue
            val sealed = runCatching {
                val ct = SessionCipher(store, addr).encrypt(plaintext)
                Sealed(deviceId, ct.type, ct.serialize())
            }.onFailure { Log.w(TAG, "seal failed dev=$deviceId: ${it.message}") }.getOrNull()
            if (sealed != null) return sealed
        }
        return null
    }

    /** فتح حمولة مختومة، أو null عند الفشل (تُتجاهل الرسالة بأمان). */
    fun open(senderRedId: String, deviceId: Int, type: Int, bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val addr = SignalProtocolAddress(senderRedId.trim().uppercase(), deviceId)
            val cipher = SessionCipher(store, addr)
            when (type) {
                CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(bytes))
                CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(bytes))
                else -> null
            }
        }.onFailure { Log.w(TAG, "open failed: ${it.message}") }.getOrNull()
    }
}
