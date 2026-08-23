package com.red.sovereign.calls

/**
 * يفصل حدث disconnect الذي بدأه التطبيق عن event يعود من Android Telecom.
 * من دون هذا الحارس يعيد disconnect المحلي استدعاء ACTION_END بينما التنظيف
 * الأول ما زال يحرر WebRTC وواجهة المكالمة.
 */
internal class TelecomDisconnectGuard {
    private var locallyDisconnectedCallId: String? = null

    @Synchronized
    fun markLocalDisconnect(callId: String) {
        locallyDisconnectedCallId = callId
    }

    @Synchronized
    fun consumeIfLocal(callId: String?): Boolean {
        if (callId.isNullOrBlank() || callId != locallyDisconnectedCallId) return false
        locallyDisconnectedCallId = null
        return true
    }

    @Synchronized
    fun clear(callId: String?) {
        if (callId == locallyDisconnectedCallId) locallyDisconnectedCallId = null
    }
}
