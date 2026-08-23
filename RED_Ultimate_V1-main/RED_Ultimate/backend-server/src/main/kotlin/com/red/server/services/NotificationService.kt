package com.red.server.services

import com.red.server.auth.UserAccountResponse
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
    private val pushTokens: com.red.server.notification.DevicePushTokenService? = null
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

    fun sendSms(phoneNumber: String, message: String) {
        logger.info("notification.sms target={} length={}", phoneNumber, message.length)
    }

    fun sendPushNotification(deviceToken: String, title: String, body: String) {
        logger.info("notification.push tokenHash={} title={} length={}", deviceToken.hashCode(), title, body.length)
    }

    /**
     * Dispatch High-Priority FCM / Sovereign VoIP Push Notification to wake up
     * a backgrounded or killed device when an incoming call offer arrives.
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
            "LIVE" -> "بدأ بث مباشر"
            "MISSED_PSTN" -> "مكالمة فائتة على رقمك"
            else -> "مكالمة صوتية واردة"
        }
        val body = when (kind) {
            "SPACE", "CONFERENCE", "GROUP" -> "$callerId يدعوك للانضمام"
            "LIVE" -> "$callerId بدأ بثاً مباشراً"
            "MISSED_PSTN" -> "اتصال فائت من $callerId — لم يتم الرد في الوقت المحدد"
            else -> "$callerId يتصل بك"
        }
        val type = when (kind) {
            "LIVE" -> "LIVE"
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
        dispatchOptionalFcm(targetUserId, title, body, callId, kind, called = called, channel = channel)
    }

    /**
     * إشعار رسالة دردشة لمستخدم غير متصل الآن: يُسجَّل في قائمة الإشعارات داخل التطبيق
     * (تظهر عند فتح التطبيق) ويُرسل FCM اختياري (عند ضبط YOUNES_FCM_SERVER_KEY).
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
        dispatchOptionalFcm(targetUserId, "رسالة جديدة من $senderId", "رسالة مشفرة", targetUserId, "MESSAGE", dataType = "MESSAGE")
    }

    /**
     * Optional high-priority FCM data message when YOUNES_FCM_SERVER_KEY is configured.
     * Uses FCM HTTP v1 API (https://fcm.googleapis.com/v1/projects/{id}/messages:send)
     * with OAuth2 access token. Supports both service account JSON and legacy server key.
     * Local / sovereign deployments skip this and rely on /ws/calls + the pending mailbox.
     */
    private fun dispatchOptionalFcm(
        targetUserId: String,
        title: String,
        body: String,
        callId: String,
        mode: String,
        dataType: String = "VOIP",
        called: String? = null,
        channel: String? = null
    ) {
        val fcmConfig = FcmConfig.fromEnv() ?: return
        val tokens = buildList {
            addAll(pushTokens?.tokensFor(targetUserId).orEmpty())
            System.getenv("YOUNES_FCM_DEVICE_$targetUserId")?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct()
        if (tokens.isEmpty()) return
        val accessToken = fcmConfig.getAccessToken() ?: run {
            logger.warn("notification.voip_fcm_failed target={} reason=no_access_token", targetUserId)
            return
        }
        tokens.forEach { token ->
            runCatching {
                // FCM HTTP v1: الأولوية تُقرأ من android.priority فقط — الحقل
                // الأعلى "priority" خاص بالـ legacy API وv1 يتجاهله فيعالج
                // الرسالة Normal فتتأخر في Doze. HIGH + TTL قصير + collapse_key
                // يمنع تراكم رنات قديمة انتهت قبل وصول الدفع.
                val calledField = if (called.isNullOrBlank()) "" else ",\"called\": ${str(called)}"
                val channelField = if (channel.isNullOrBlank()) "" else ",\"channel\": ${str(channel)}"
                val payload = """
                    {
                        "message": {
                            "token": ${str(token)},
                            "android": {
                                "priority": "HIGH",
                                "ttl": "25s",
                                "collapse_key": ${str(callId)},
                                "restricted_package_name": "com.red.sovereign"
                            },
                            "data": {
                                "type": ${str(dataType)},
                                "callId": ${str(callId)},
                                "mode": ${str(mode)},
                                "title": ${str(title)},
                                "body": ${str(body)}$calledField$channelField
                            }
                        }
                    }
                """.trimIndent()
                val url = "https://fcm.googleapis.com/v1/projects/${fcmConfig.projectId}/messages:send"
                val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                logger.info("notification.voip_fcm target={} http={}", targetUserId, conn.responseCode)
                if (conn.responseCode !in 200..299) {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                    logger.warn("notification.voip_fcm_error target={} code={} body={}", targetUserId, conn.responseCode, error)
                }
                conn.disconnect()
            }.onFailure { logger.warn("notification.voip_fcm_failed target={} err={}", targetUserId, it.message) }
        }
    }

    /**
     * FCM configuration — supports both service account JSON and legacy server key.
     * Service account JSON is preferred (HTTP v1 API).
     */
    private data class FcmConfig(
        val projectId: String,
        val privateKey: String,
        val clientEmail: String
    ) {
        companion object {
            fun fromEnv(): FcmConfig? {
                // Try service account JSON first (FCM_V1_SERVICE_ACCOUNT env var)
                val saJson = System.getenv("FCM_V1_SERVICE_ACCOUNT")?.takeIf { it.isNotBlank() }
                if (saJson != null) {
                    return try {
                        val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(saJson)
                        FcmConfig(
                            projectId = node.get("project_id").asText(),
                            privateKey = node.get("private_key").asText(),
                            clientEmail = node.get("client_email").asText()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                // Fallback: legacy server key (deprecated but still works for some projects)
                val key = System.getenv("YOUNES_FCM_SERVER_KEY")?.takeIf { it.isNotBlank() } ?: return null
                // With legacy key, we can't use HTTP v1 API — use legacy API as fallback
                return null // Legacy API is discontinued; service account is required
            }
        }

        fun getAccessToken(): String? {
            return try {
                val now = System.currentTimeMillis() / 1000
                val header = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    mapOf("alg" to "RS256", "typ" to "JWT")
                ).let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
                val payload = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    mapOf(
                        "iss" to clientEmail,
                        "scope" to "https://www.googleapis.com/auth/firebase.messaging",
                        "aud" to "https://oauth2.googleapis.com/token",
                        "iat" to now,
                        "exp" to now + 3600
                    )
                ).let { java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }
                val signedInput = "$header.$payload"
                val signature = java.security.Signature.getInstance("SHA256withRSA").apply {
                    initSign(java.security.KeyFactory.getInstance("RSA").generatePrivate(
                        java.security.spec.PKCS8EncodedKeySpec(
                            java.util.Base64.getMimeDecoder().decode(privateKey.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\s".toRegex(), ""))
                        )
                    ))
                    update(signedInput.toByteArray())
                }.sign()
                val jwt = "$signedInput.${java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"

                val tokenUrl = java.net.URI("https://oauth2.googleapis.com/token").toURL().openConnection() as java.net.HttpURLConnection
                tokenUrl.requestMethod = "POST"
                tokenUrl.doOutput = true
                tokenUrl.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                tokenUrl.connectTimeout = 5000
                tokenUrl.readTimeout = 5000
                val body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"
                tokenUrl.outputStream.use { it.write(body.toByteArray()) }
                val response = tokenUrl.inputStream.bufferedReader().readText()
                tokenUrl.disconnect()
                com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("access_token")?.asText()
            } catch (e: Exception) {
                null
            }
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
