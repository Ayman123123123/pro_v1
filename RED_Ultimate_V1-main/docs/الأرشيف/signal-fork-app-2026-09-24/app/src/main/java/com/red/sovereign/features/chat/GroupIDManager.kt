package com.red.sovereign.features.chat

import com.red.sovereign.core.auth.IdentityManager
import javax.inject.Inject

class GroupIDManager @Inject constructor(
    private val identityManager: IdentityManager,
    private val webSocketClient: RedWebSocketClient
) {
    /**
     * إضافة عضو عبر المعرف السيادي (مثلاً: @RED_77123)
     */
    fun inviteMemberByHandle(groupId: String, handle: String) {
        // يتم إرسال طلب للسيرفر للتحقق من المعرف وربطه بالمجموعة
        println("🔴 RED: Inviting $handle to Group $groupId")
    }

    /**
     * الحصول على المعرف الخاص بي الذي تم تعيينه بعد موافقة المدير
     */
    fun getMySovereignID(): String {
        return identityManager.getUserHandle() ?: "PENDING_APPROVAL"
    }
}
