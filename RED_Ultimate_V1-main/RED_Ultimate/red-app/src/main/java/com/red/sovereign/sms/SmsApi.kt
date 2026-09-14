package com.red.sovereign.sms

/**
 * توحيد SMS API (2026-09-10):
 * كانت هناك نسختان مكررتان — `com.red.sovereign.sms.SmsApi` (هنا) و
 * `com.red.sovereign.features.sms.SmsApi` (النسخة الموحدة مع البحث والمقبس
 * الحي، انظر `features/sms/SmsViewModel.kt`).
 * النسخة القانونية هي `features.sms` (الأكبر: عميل + نماذج + مقبس + شاشات).
 * هذا الملف أصبح مجرد إعادة تصدير مهجورة حفاظاً على التوافق —
 * لا تضف منطقاً هنا، استخدم الحزمة الموحدة مباشرة.
 */

@Deprecated(
    "Use com.red.sovereign.features.sms.SmsApi",
    ReplaceWith(
        "com.red.sovereign.features.sms.SmsApi",
        "com.red.sovereign.features.sms.SmsApi"
    )
)
typealias SmsApi = com.red.sovereign.features.sms.SmsApi

@Deprecated(
    "Use com.red.sovereign.features.sms.SmsSendRequest",
    ReplaceWith(
        "com.red.sovereign.features.sms.SmsSendRequest",
        "com.red.sovereign.features.sms.SmsSendRequest"
    )
)
typealias SendSmsRequest = com.red.sovereign.features.sms.SmsSendRequest

@Deprecated(
    "Use com.red.sovereign.features.sms.SmsSendResponse",
    ReplaceWith(
        "com.red.sovereign.features.sms.SmsSendResponse",
        "com.red.sovereign.features.sms.SmsSendResponse"
    )
)
typealias SmsSendResponse = com.red.sovereign.features.sms.SmsSendResponse

@Deprecated(
    "Use com.red.sovereign.features.sms.SmsConversationDto",
    ReplaceWith(
        "com.red.sovereign.features.sms.SmsConversationDto",
        "com.red.sovereign.features.sms.SmsConversationDto"
    )
)
typealias SmsConversation = com.red.sovereign.features.sms.SmsConversationDto

@Deprecated(
    "Use com.red.sovereign.features.sms.SmsMessageDto",
    ReplaceWith(
        "com.red.sovereign.features.sms.SmsMessageDto",
        "com.red.sovereign.features.sms.SmsMessageDto"
    )
)
typealias SmsMessageDto = com.red.sovereign.features.sms.SmsMessageDto
