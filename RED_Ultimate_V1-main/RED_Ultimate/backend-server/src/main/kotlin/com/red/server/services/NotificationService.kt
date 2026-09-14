package com.red.server.services

import com.red.server.auth.UserAccountResponse
import com.red.server.notification.UnifiedPushSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Notification facade for account, device and security events.
 *
 * The default local-first implementation is intentionally side-effect safe: it logs
 * structured notification events and can later be connected to SMTP/SMS/Push adapters
 * without changing controllers/services.
 */
@Service
class NotificationService(
    private val emailProperties: EmailProperties,
    @Autowired(required = false)
    @Qualifier("inAppNotificationService")
    private val inApp: com.red.server.notification.NotificationService? = null,
    @Autowired(required = false)
    private val pushTokens: com.red.server.notification.DevicePushTokenService? = null,
    private val unifiedPush: UnifiedPushSender
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun sendWelcomeEmail(user: UserAccountResponse, tempPassword: String? = null) {
        emitEmail(user.username, "مرحباً بك في يونس", buildWelcomeEmailBody(user, tempPassword))
    }

    fun sendAccountApproved(user: UserAccountResponse) {
        emitEmail(user.username, "تمت الموافقة على حسابك", buildAccountApprovedBody(user))
    }

    fun sendAccountRejected(user: UserAccountResponse, reason: String) {
        emitEmail(user.username, "تعذر اعتماد الحساب", buildAccountRejectedBody(user, reason))
    }

    fun sendPasswordRecovery(user: UserAccountResponse, recoveryCode: String) {
        emitEmail(user.username, "رمز استعادة كلمة المرور", buildPasswordRecoveryBody(user, recoveryCode))
    }

    fun sendDeviceVerification(user: UserAccountResponse, deviceId: String, fingerprint: String) {
        emitEmail(user.username, "جهاز جديد على حسابك", buildDeviceVerificationBody(user, deviceId, fingerprint))
    }

    fun sendSecurityAlert(user: UserAccountResponse, alertType: String, details: String) {
        emitEmail(user.username, "تنبيه أمان", buildSecurityAlertBody(user, alertType, details))
    }

    fun sendPushNotification(deviceToken: String, title: String, body: String) {
        logger.info("notification.push tokenHash={} title={} length={}", deviceToken.hashCode(), title, body.length)
    }

    /**
     * Sovereign VoIP wake: records the in-app notification and POSTs a CALL
     * payload to every stored UnifiedPush endpoint of the target device(s).
     * The distributor wakes the app even when it is killed (see RedPushService).
     */
    fun sendVoipPushNotification(
        targetUserId: String,
        callerId: String,
        callId: String,
        mode: String,
        called: String? = null,
        channel: String? = null
    ) {
        logger.info("notification.voip_push targetUser={} caller={} called={} callId={} mode={}", targetUserId, callerId, called ?: "-", callId, mode)
        val kind = mode.uppercase()
        val title = when (kind) {
            "VIDEO" -> "مكالمة فيديو واردة"
            "SPACE" -> "مساحة صوتية"
            "CONFERENCE", "GROUP" -> "دعوة مؤتمر"
            "LIVE", "LIVESTREAM" -> "بدأ بث مباشر"
            else -> "مكالمة صوتية واردة"
        }
        val body = when (kind) {
            "SPACE", "CONFERENCE", "GROUP" -> "$callerId يدعوك للانضمام"
            "LIVE", "LIVESTREAM" -> "$callerId بدأ بثاً مباشراً"
            else -> "$callerId يتصل بك"
        }
        val type = when (kind) {
            "LIVE", "LIVESTREAM" -> "LIVE"
            "SPACE", "CONFERENCE", "GROUP" -> "GROUPS"
            else -> "CALL"
        }
        runCatching {
            inApp?.createNotification(
                userId = targetUserId,
                type = type,
                title = title,
                body = body,
                senderId = callerId,
                senderName = callerId,
                threadId = callId
            )
        }
        // Wire format shared with RedPushService.handlePush (IncomingCallActivity call types).
        val callType = when (kind) {
            "LIVE", "LIVESTREAM" -> "livestream"
            "GROUP" -> "group"
            "SPACE", "CONFERENCE" -> "conference"
            else -> "1to1"
        }
        val wireMode = if (kind == "VIDEO") "VIDEO" else "VOICE"
        val payload = buildString {
            append("{\"v\":1,\"type\":\"CALL\",")
            append("\"callId\":${str(callId)},\"mode\":${str(wireMode)},")
            append("\"callerId\":${str(callerId)},\"callerName\":${str(callerId)},")
            append("\"callType\":${str(callType)},\"ts\":${System.currentTimeMillis()}}")
        }
        dispatchSovereignPush(targetUserId, payload)
    }

    /**
     * إشعار رسالة دردشة لمستخدم غير متصل الآن: يُسجَّل في قائمة الإشعارات داخل التطبيق
     * (تظهر عند فتح التطبيق) ويُرسل تنبيه إيقاظ عبر UnifiedPush السيادي — بلا أي
     * محتوى (E2EE يبقى مختوماً؛ الجهاز يسحب الرسائل عبر السوكت بعد الاستيقاظ).
     */
    fun sendChatMessagePush(targetUserId: String, senderId: String) {
        runCatching {
            inApp?.createNotification(
                userId = targetUserId,
                type = "MESSAGE",
                title = "رسالة جديدة من $senderId",
                body = "رسالة مشفرة",
                senderId = senderId,
                senderName = senderId,
                threadId = null
            )
        }
        val payload = "{\"v\":1,\"type\":\"MESSAGE\",\"senderId\":${str(senderId)},\"ts\":${System.currentTimeMillis()}}"
        dispatchSovereignPush(targetUserId, payload)
    }

    /**
     * Sovereign wake delivery: POSTs [payload] to every stored UnifiedPush
     * endpoint of the target user. Best-effort — the socket + pending mailbox
     * remain the delivery backbone; push is only the wake-up knock.
     */
    private fun dispatchSovereignPush(targetUserId: String, payload: String) {
        val body = payload.toByteArray(Charsets.UTF_8)
        val endpoints = pushTokens?.tokensFor(targetUserId).orEmpty().distinct()
            .filter { it.startsWith("https://") || it.startsWith("http://") }
        if (endpoints.isEmpty()) return
        endpoints.forEach { endpoint ->
            val code = runCatching { unifiedPush.send(endpoint, body) }.getOrElse { -1 }
            if (code in 200..299) logger.info("notification.push_sent target={} http={}", targetUserId, code)
            else logger.warn("notification.push_failed target={} http={}", targetUserId, code)
            // Self-healing: the distributor dropped this subscription — prune it so
            // the next wake doesn't pay for a dead endpoint (client re-registers anyway).
            if (code == 404 || code == 410) runCatching { pushTokens?.remove(targetUserId, endpoint) }
        }
    }

    private fun str(value: String): String {
        val escaped = buildString(value.length + 8) {
            for (ch in value) when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        return "\"$escaped\""
    }

    fun sendBulkEmail(recipients: List<String>, subject: String, body: String, isHtml: Boolean = false) {
        recipients.forEach { emitEmail(it, subject, body, isHtml) }
    }

    fun sendTemplatedEmail(to: String, templateName: String, variables: Map<String, String>) {
        emitEmail(to, getTemplateSubject(templateName), getTemplateBody(templateName, variables), isHtml = true)
    }

    private fun emitEmail(to: String, subject: String, body: String, isHtml: Boolean = true) {
        logger.info(
            "notification.email from={} to={} subject={} html={} length={}",
            emailProperties.fromAddress,
            to,
            subject,
            isHtml,
            body.length
        )
    }

    private fun buildWelcomeEmailBody(user: UserAccountResponse, tempPassword: String?): String = emailShell(
        title = "مرحباً بك في يونس",
        subtitle = "حسابك تم إنشاؤه بنجاح وينتظر الموافقة المحلية",
        body = "مرحباً ${user.displayName}. معرّف يونس: ${user.redId}." +
            (tempPassword?.let { " كلمة مرور مؤقتة: $it" } ?: "")
    )

    private fun buildAccountApprovedBody(user: UserAccountResponse): String = emailShell(
        title = "تمت الموافقة",
        subtitle = "حسابك معتمد الآن",
        body = "مرحباً ${user.displayName}. يمكنك تسجيل الدخول إلى يونس واستخدام الميزات المتاحة."
    )

    private fun buildAccountRejectedBody(user: UserAccountResponse, reason: String): String = emailShell(
        title = "لم يتم اعتماد الحساب",
        subtitle = "راجع الإدارة المحلية",
        body = "مرحباً ${user.displayName}. سبب الرفض: $reason"
    )

    private fun buildPasswordRecoveryBody(user: UserAccountResponse, recoveryCode: String): String = emailShell(
        title = "استعادة كلمة المرور",
        subtitle = "استخدم الرمز داخل التطبيق فقط",
        body = "مرحباً ${user.displayName}. رمز الاستعادة: $recoveryCode"
    )

    private fun buildDeviceVerificationBody(user: UserAccountResponse, deviceId: String, fingerprint: String): String = emailShell(
        title = "جهاز جديد",
        subtitle = "تم تسجيل جهاز على حسابك",
        body = "المستخدم: ${user.displayName}. الجهاز: $deviceId. البصمة: ${fingerprint.take(24)}."
    )

    private fun buildSecurityAlertBody(user: UserAccountResponse, alertType: String, details: String): String = emailShell(
        title = "تنبيه أمان",
        subtitle = alertType,
        body = "مرحباً ${user.displayName}. التفاصيل: $details"
    )

    private fun getTemplateSubject(templateName: String): String = when (templateName) {
        "welcome_email" -> "مرحباً بك في يونس"
        "account_approved" -> "تمت الموافقة على حسابك"
        "account_rejected" -> "لم يتم اعتماد حسابك"
        "password_recovery" -> "رمز استعادة كلمة المرور"
        "device_verification" -> "جهاز جديد"
        "security_alert" -> "تنبيه أمان"
        else -> "إشعار من يونس"
    }

    private fun getTemplateBody(templateName: String, variables: Map<String, String>): String = emailShell(
        title = getTemplateSubject(templateName),
        subtitle = variables["subtitle"].orEmpty(),
        body = variables.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    )

    private fun emailShell(title: String, subtitle: String, body: String): String = """
        <!doctype html>
        <html lang="ar" dir="rtl">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;background:#050A16;color:#EDF7FB;font-family:Arial,sans-serif;padding:24px">
          <main style="max-width:640px;margin:auto;background:#112240;border:1px solid #1E3A5F;border-radius:18px;padding:28px">
            <h1 style="color:#E8B84A;margin:0 0 8px">$title</h1>
            <p style="color:#35CBE0;margin:0 0 20px">$subtitle</p>
            <section style="background:#0A1628;border-radius:12px;padding:18px;line-height:1.8">$body</section>
            <p style="color:#8892B0;font-size:12px;margin-top:18px">يونس — منظومة محلية آمنة</p>
          </main>
        </body>
        </html>
    """.trimIndent()

}

data class EmailProperties(
    val smtpHost: String = "localhost",
    val smtpPort: Int = 25,
    val smtpAuth: Boolean = false,
    val startTls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "noreply@red.local"
)
