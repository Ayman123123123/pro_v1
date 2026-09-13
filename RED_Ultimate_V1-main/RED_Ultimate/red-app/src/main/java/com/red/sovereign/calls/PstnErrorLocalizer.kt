package com.red.sovereign.calls

/**
 * 🗣️ توطين رسائل أخطاء PSTN/SMS القادمة من الخادم إلى عربية مفهومة.
 *
 * تُستخدم من [com.red.sovereign.auth.AuthViewModel] وشاشات الهاتف والرسائل
 * حتى لا يرى المستخدم رموزاً إنجليزية خام مثل PSTN_SIM_NOT_BOUND.
 */
object PstnErrorLocalizer {

    fun arabic(raw: String?): String {
        val code = raw?.trim()?.uppercase()?.substringBefore(':') ?: return "خطأ غير معروف"
        return when (code) {
            "PSTN_NOT_ENABLED", "SMS_NOT_ENABLED" ->
                "خدمة المكالمات/الرسائل غير مفعّلة لحسابك — اطلب من المسؤول تفعيلها"
            "PSTN_SIM_NOT_BOUND", "SIM_NOT_BOUND" ->
                "لا توجد شريحة مربوطة بحسابك — اطلب من المسؤول ربط منفذ/شريحة"
            "ALREADY_IN_PSTN_CALL" ->
                "لديك مكالمة PSTN جارية — أنهِها أولاً"
            "DAILY_LIMIT_REACHED" ->
                "انتهت حصتك اليومية من المكالمات/الرسائل"
            "ACCOUNT_NOT_APPROVED" ->
                "الحساب غير معتمد بعد"
            "NO_USABLE_PORT" ->
                "لا يوجد منفذ بإشارة صالحة حالياً — تحقق من الشرائح والتغطية"
            "MIC_PERMISSION_REQUIRED" ->
                "إذن الميكروفون مطلوب للمكالمة — امنحه من إعدادات التطبيق ثم أعد المحاولة"
            "BRIDGE_FAILED" ->
                "تعذر تجهيز جسر الصوت — تحقق من اتصال الخادم"
            "PEER_CONNECTION_FAILED" ->
                "تعذر تهيئة قناة الصوت على الجهاز"
            "SIP_REGISTER_FAILED_ALL" ->
                "تعذر الوصول لبوابة الصوت على أي عنوان — تحقق من الشبكة والخادم"
            "SIP_WEBSOCKET_ERROR", "SIP_CONNECTION_CLOSED" ->
                "انقطع اتصال بوابة الصوت"
            "PSTN_CALL_FAILED" ->
                "فشل بدء المكالمة"
            "PSTN_NOT_ENABLED" -> "خدمة الهاتف غير مفعّلة لحسابك"
            "INVALID_REQUEST" -> "طلب غير صالح"
            "INVALID_SERVER_RESPONSE" -> "رد غير مفهوم من الخادم"
            "NETWORK_ERROR" -> "تعذر الاتصال بالخادم — تحقق من الشبكة"
            "OFFLINE" -> "لا توجد شبكة إطلاقاً — فعّل Wi-Fi أو البيانات ثم أعد المحاولة"
            "UNAUTHENTICATED" -> "انتهت الجلسة — أعد تسجيل الدخول"
            "RATE_LIMIT_EXCEEDED" -> "تجاوزت حد المحاولات — انتظر قليلاً"
            "FORBIDDEN" -> "غير مصرح لك بهذا الإجراء"
            "NOT_FOUND" -> "العنصر غير موجود"
            else -> raw ?: "خطأ غير معروف"
        }
    }
}
