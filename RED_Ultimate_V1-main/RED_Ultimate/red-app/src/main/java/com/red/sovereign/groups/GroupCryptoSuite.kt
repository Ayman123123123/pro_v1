package com.red.sovereign.groups

import android.content.Context
import com.red.sovereign.auth.ApiResult

// LEGENDARY: تجريد تشفير المجموعات — SenderKey اليوم، MLS (RFC 9420 TreeKEM) غداً بلا تغيير API الرسائل.
// القاعدة: المجموعات الصغيرة/متوسطة → Signal SenderKey (مثبت بمليارات المستخدمين).
// المجموعات الكبيرة/المجتمعات (>256 أو متغيرة بكثافة) → MLS عند توفر مكتبة ناضجة.

enum class GroupCryptoSuiteType { SENDER_KEY, MLS }

interface GroupCryptoSuite {
    val type: GroupCryptoSuiteType
    suspend fun prepare(group: Group, plaintext: ByteArray): ApiResult<PreparedGroupSend>
    fun decrypt(senderRedId: String, senderDeviceId: Int, ciphertext: ByteArray, groupId: String = ""): ByteArray
    fun rotate(groupId: String)
}

/** الموجود الإنتاجي: SenderKey عبر GroupCryptoManager الحالي. */
class SenderKeySuite(private val delegate: GroupCryptoManager) : GroupCryptoSuite {
    override val type = GroupCryptoSuiteType.SENDER_KEY
    override suspend fun prepare(group: Group, plaintext: ByteArray): ApiResult<PreparedGroupSend> =
        delegate.prepare(group, plaintext)
    override fun decrypt(senderRedId: String, senderDeviceId: Int, ciphertext: ByteArray, groupId: String): ByteArray =
        delegate.decrypt(senderRedId, senderDeviceId, ciphertext, groupId)
    override fun rotate(groupId: String) = delegate.rotate(groupId)
}

/** MLS لاحقاً: واجهة جاهزة + رفض صريح بدل كسر صامت — تُستبدل بتطبيق TreeKEM حقيقي. */
class MlsStubSuite : GroupCryptoSuite {
    override val type = GroupCryptoSuiteType.MLS
    override suspend fun prepare(group: Group, plaintext: ByteArray): ApiResult<PreparedGroupSend> =
        ApiResult.Error(null, "MLS_NOT_YET_AVAILABLE")
    override fun decrypt(senderRedId: String, senderDeviceId: Int, ciphertext: ByteArray, groupId: String): ByteArray =
        throw UnsupportedOperationException("MLS_NOT_YET_AVAILABLE")
    override fun rotate(groupId: String) = Unit
}

object GroupCryptoSuites {
    /** عتبة التبديل المستقبلية: >256 عضو أو مجتمع → MLS (تُرجع Stub حالياً مع تسجيل واضح). */
    const val MLS_MEMBER_THRESHOLD = 256

    fun forGroup(context: Context, group: Group, manager: GroupCryptoManager): GroupCryptoSuite {
        val large = group.members.size > MLS_MEMBER_THRESHOLD
        return if (large) {
            android.util.Log.i("GroupCrypto", "group ${group.id} large (${group.members.size}) — MLS planned, using SenderKey meanwhile")
            SenderKeySuite(manager)
        } else {
            SenderKeySuite(manager)
        }
    }

    /** فحص صريح: هل هذه المجموعة مرشحة MLS مستقبلاً؟ (للبوابة والقبول) */
    fun isMlsCandidate(group: Group): Boolean = group.members.size > MLS_MEMBER_THRESHOLD
}

// Re-export الأنواع الحالية حتى لا تتكسر الاستيرادات.
typealias PreparedGroupSendAlias = PreparedGroupSend
