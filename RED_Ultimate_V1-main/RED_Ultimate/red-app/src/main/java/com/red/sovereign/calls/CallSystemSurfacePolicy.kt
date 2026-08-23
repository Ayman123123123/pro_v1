package com.red.sovereign.calls

/**
 * RED يعرض مكالماته 1:1 داخل MainActivity. بعض إصدارات Android Telecom الذاتية
 * الإدارة تنهي آخر مهمة ظاهرة للتطبيق عند فصل الاتصال؛ لذلك لا نسجل مكالمات RED
 * الداخلية في ذلك السطح. تظل مكالمات PSTN/DINSTAR منفصلة في مسارها الخاص.
 */
internal object CallSystemSurfacePolicy {
    fun usesAndroidTelecom(mode: String): Boolean =
        mode.equals("PSTN", ignoreCase = true) || mode.equals("DINSTAR", ignoreCase = true)
}
