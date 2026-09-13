package com.red.sovereign.features.chat

/**
 * P1-E: سياسات المشرف للمجموعات (AdminPolicy).
 *
 * ربط الواجهة:
 * - SovereignGroupSystem.kt -> SovereignGroupInfoScreen:
 *   - نافذة الوضع البطيء (showSlowMode) تفحص قبل الإرسال عبر [canSendInSlowMode] /
 *     [SlowModeGuard] بدل الفحص المباشر.
 *   - زر "حذف المشرف للجميع" (Toast + ضغط مطول) يُبوَّب عبر [canAdminDelete].
 *   - زر "إنهاء المجموعة" (confirmEndGroup) يظهر للمالك فقط عبر [canEndGroup].
 * - لا يعدّل هذا الملف SovereignGroupSystem.kt — فقط دوال مساعدة خالصة للربط التدريجي.
 */

/**
 * هل يُسمح بالإرسال في الوضع البطيء؟
 * @param lastSentMillis طابع آخر إرسال (0 = لا رسالة سابقة -> مسموح).
 * @param nowMillis الوقت الحالي بالمللي.
 * @param slowSeconds الفاصل المطلوب بالثواني (0 أو أقل = معطّل -> مسموح).
 */
fun canSendInSlowMode(lastSentMillis: Long, nowMillis: Long, slowSeconds: Int): Boolean {
    if (slowSeconds <= 0) {
        return true
    }
    if (lastSentMillis <= 0L) {
        return true
    }
    return nowMillis - lastSentMillis >= slowSeconds * 1000L
}

/**
 * هل يستطيع المشرف حذف رسالة للجميع؟
 * الشرط: الدور OWNER أو ADMIN (غير حساسة لحالة الأحرف) وعمر الرسالة خلال 24 ساعة.
 * @param actorRole دور الفاعل ("OWNER" / "ADMIN" / ...).
 * @param messageAgeHours عمر الرسالة بالساعات.
 */
fun canAdminDelete(actorRole: String, messageAgeHours: Long): Boolean {
    if (messageAgeHours < 0L || messageAgeHours > 24L) {
        return false
    }
    return when (actorRole.trim().uppercase()) {
        "OWNER", "ADMIN" -> true
        else -> false
    }
}

/**
 * هل يستطيع إنهاء/أرشفة المجموعة؟ المالك فقط.
 * @param myRole دور المستخدم الحالي.
 */
fun canEndGroup(myRole: String): Boolean {
    return myRole.trim().uppercase() == "OWNER"
}

/**
 * حارس بسيط للوضع البطيء داخل الجلسة (بدون Context/SharedPreferences).
 * للبقاء الدائم استخدم getGroupSlowModeSeconds/recordGroupSentNow في SovereignGroupSystem.kt.
 */
object SlowModeGuard {
    var lastSent: Long = 0L

    fun canSend(nowMillis: Long, slowSeconds: Int): Boolean {
        return canSendInSlowMode(lastSent, nowMillis, slowSeconds)
    }

    fun record(nowMillis: Long) {
        lastSent = nowMillis
    }

    fun reset() {
        lastSent = 0L
    }
}
