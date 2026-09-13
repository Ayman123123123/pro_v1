package com.red.sovereign.calls

object PstnErrorLocalizer {
    fun arabic(raw: String?): String {
        val code = raw?.trim()?.uppercase()?.substringBefore(':') ?: return "خطأ غير معروف"
        return when (code) {
            "PSTN_NOT_ENABLED", "SMS_NOT_ENABLED" -> "خدمة المكالمات/الرسائل غير مفعّلة لحسابك"
            "PSTN_SIM_NOT_BOUND", "SIM_NOT_BOUND" -> "لا توجد شريحة مربوطة بحسابك"
            "ALREADY_IN_PSTN_CALL" -> "لديك مكالمة PSTN جارية"
            "DAILY_LIMIT_REACHED" -> "انتهت حصتك اليومية"
            "ACCOUNT_NOT_APPROVED" -> "الحساب غير معتمد بعد"
            "NO_USABLE_PORT" -> "لا يوجد منفذ بإشارة صالحة حالياً"
            "MIC_PERMISSION_REQUIRED" -> "إذن الميكروفون مطلوب للمكالمة"
            "BRIDGE_FAILED" -> "تعذر تجهيز جسر الصوت"
            "PEER_CONNECTION_FAILED" -> "تعذر تهيئة قناة الصوت على الجهاز"
            "SIP_REGISTER_FAILED_ALL" -> "تعذر الوصول لبوابة الصوت على أي عنوان"
            "SIP_WEBSOCKET_ERROR", "SIP_CONNECTION_CLOSED" -> "انقطع اتصال بوابة الصوت"
            "PSTN_CALL_FAILED" -> "فشل بدء المكالمة"
            "INVALID_REQUEST" -> "طلب غير صالح"
            "INVALID_SERVER_RESPONSE" -> "رد غير مفهوم من الخادم"
            "NETWORK_ERROR" -> "تعذر الاتصال بالخادم"
            "OFFLINE" -> "لا توجد شبكة إطلاقاً"
            "UNAUTHENTICATED" -> "انتهت الجلسة — أعد تسجيل الدخول"
            "RATE_LIMIT_EXCEEDED" -> "تجاوزت حد المحاولات"
            "FORBIDDEN" -> "غير مصرح لك بهذا الإجراء"
            "NOT_FOUND" -> "العنصر غير موجود"
            else -> raw ?: "خطأ غير معروف"
        }
    }
}
