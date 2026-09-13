package com.red.sovereign.sms

/**
 * توحيد SmsViewModel (2026-09-10):
 * كانت هناك نسختان متنافستان — `features.sms` (المقبس المعاد الاتصال +
 * البحث + توطين الأخطاء + شارة الإجمالي + إيقاف الاستقصاء + حذف جماعي
 * متوازٍ مع Undo) و`sms` (الأقدم: thread/openNumber/startPolling).
 * النسخة القانونية هي `features.sms` (الأكبر والأحدث، وتخدمها شاشات
 * الإنتاج في RedDashboard). هذا الملف أصبح مجرد إعادة تصدير مهجورة —
 * لا تضف منطقاً هنا، استخدم الحزمة الموحدة مباشرة.
 *
 * شاشة `features/pstn/SmsScreens.kt` (القديمة، حزمة `com.red.sovereign.sms`)
 * هُجّرت للـ API الموحد: chatNumber/chatMessages/start/stop/openChat/
 * closeChat/refresh/send(text)/deleteMessage — انظر SmsFormat.kt للتنسيق.
 */

@Deprecated(
    "Use com.red.sovereign.features.sms.SmsViewModel (single source of truth)",
    ReplaceWith(
        "com.red.sovereign.features.sms.SmsViewModel",
        "com.red.sovereign.features.sms.SmsViewModel"
    )
)
typealias SmsViewModel = com.red.sovereign.features.sms.SmsViewModel
