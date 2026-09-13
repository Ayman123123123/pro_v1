package com.red.sovereign.crypto

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

/** تطبيق SenderKeys الحالي (يلف GroupCryptoManager). */
class SenderKeysGroupCrypto(
    private val prepare: suspend (groupId: String, plaintext: ByteArray) -> ByteArray? = { _, _ -> null },
) : GroupCrypto {
    override suspend fun create(groupId: String) { /* distributionId يُنشأ عند أول prepare */ }
    override suspend fun distribute(groupId: String, memberRedIds: List<String>): Boolean = true
    override suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray? = prepare(groupId, plaintext)
    override suspend fun decrypt(groupId: String, ciphertext: ByteArray): ByteArray? = null // TODO: ربط GroupCipher.decrypt
    override suspend fun rotate(groupId: String) { /* تدوير distributionId + membershipHash */ }
}

/** stub لمستقبل MLS — TODO: OpenMLS (Rust/JNI) + SFrame للمكالمات + SealedSender. */
object MlsStub : GroupCrypto {
    override suspend fun create(groupId: String) = Unit
    override suspend fun distribute(groupId: String, memberRedIds: List<String>): Boolean = false
    override suspend fun encrypt(groupId: String, plaintext: ByteArray): ByteArray? = null
    override suspend fun decrypt(groupId: String, ciphertext: ByteArray): ByteArray? = null
    override suspend fun rotate(groupId: String) = Unit
}
