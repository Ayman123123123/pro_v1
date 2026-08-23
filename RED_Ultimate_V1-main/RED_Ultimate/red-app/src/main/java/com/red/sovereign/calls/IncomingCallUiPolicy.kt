package com.red.sovereign.calls

/**
 * يختار مصدر واجهة الرنين الوحيد:
 * واجهة RED الموحدة عند ظهور التطبيق، أو Activity مخصصة فقط عند الخلفية/القفل.
 */
object IncomingCallUiPolicy {
    fun shouldLaunchIncomingActivity(hasResumedRedActivity: Boolean): Boolean = !hasResumedRedActivity
}
