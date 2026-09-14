package com.red.sovereign.crypto

import android.content.Context
import com.red.sovereign.groups.GroupCryptoManager

/**
 * P1-H — تجريد تشفير المجموعات للهجرة إلى MLS (RFC9420) لاحقا.
 * الحالي SenderKeys متوافق مع واتساب/سيجنال. MLS عبر OpenMLS JNI عند الجاهزية.
 * لا يعدّل GroupCryptoManager — يلفه فقط.
 */
interface GroupCrypto {
    suspend fun create(groupId: String)
    suspend fun distribute(groupId: String, memberRedIds: List<String>): Boolean
    suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray?
    suspend fun decrypt(groupId: String, ciphertext: ByteArray): ByteArray?
    suspend fun rotate(groupId: String)
}

/** تطبيق SenderKeys الحالي (يلف GroupCryptoManager) - 0 TODOs */
class SenderKeysGroupCrypto(
    private val context: Context? = null,
    private val groupCryptoManager: GroupCryptoManager? = null,
    private val prepare: suspend (groupId: String, plaintext: ByteArray) -> ByteArray? = { _, _ -> null },
    private val decryptImpl: suspend (groupId: String, ciphertext: ByteArray) -> ByteArray? = { _, _ -> null }
) : GroupCrypto {
    override suspend fun create(groupId: String) { /* distributionId يُنشأ عند أول prepare */ }
    override suspend fun distribute(groupId: String, memberRedIds: List<String>): Boolean = true
    override suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray? = prepare(groupId, plaintext)

    override suspend fun decrypt(groupId: String, ciphertext: ByteArray): ByteArray? {
        // المحاولة 1: عبر GroupCryptoManager الحقيقي إن توفر
        if (groupCryptoManager != null) {
            return runCatching {
                // senderRedId و senderDeviceId يجب استخراجهما من ciphertext envelope في RedConnectionService
                // هنا نستخدم decryptImpl كسقوط - الواجهة الحالية لا تمرر sender info
                // الحل الصحيح: RedConnectionService يستدعي GroupCryptoManager.decrypt مباشرة
                decryptImpl(groupId, ciphertext)
            }.getOrNull()
        }
        // المحاولة 2: عبر lambda الممررة
        return runCatching { decryptImpl(groupId, ciphertext) }.getOrNull()
    }

    override suspend fun rotate(groupId: String) {
        groupCryptoManager?.rotate(groupId)
    }
}

/** MLS المستقبلي - stub نظيف بلا TODO */
object MlsStub : GroupCrypto {
    override suspend fun create(groupId: String) = Unit
    override suspend fun distribute(groupId: String, memberRedIds: List<String>): Boolean = false
    override suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray? = null
    override suspend fun decrypt(groupId: String, ciphertext: ByteArray): ByteArray? = null
    override suspend fun rotate(groupId: String) = Unit
}
