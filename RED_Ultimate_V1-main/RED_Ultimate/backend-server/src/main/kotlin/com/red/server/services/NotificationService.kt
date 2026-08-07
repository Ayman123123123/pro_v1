package com.red.server.services

import com.red.server.auth.AuthDtos
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Properties
import javax.mail.*
import javax.mail.internet.*

/**
 * Notification service for sending email and SMS notifications.
 * Supports multiple channels: Email, SMS (via gateway), Push notifications.
 */
@Service
class NotificationService(
    private val emailProperties: EmailProperties
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * Send welcome email to newly registered user.
     */
    fun sendWelcomeEmail(user: AuthDtos.UserResponse, tempPassword: String? = null) {
        val subject = "welcome_email"
        val body = buildWelcomeEmailBody(user, tempPassword)

        sendEmail(
            to = user.email,
            subject = subject,
            body = body,
            isHtml = true
        )
    }

    /**
     * Send account approval notification.
     */
    fun sendAccountApproved(user: AuthDtos.UserResponse) {
        val subject = "account_approved"
        val body = buildAccountApprovedBody(user)

        sendEmail(
            to = user.email,
            subject = subject,
            body = body,
            isHtml = true
        )
    }

    /**
     * Send account rejection notification.
     */
    fun sendAccountRejected(user: AuthDtos.UserResponse, reason: String) {
        val subject = "account_rejected"
        val body = buildAccountRejectedBody(user, reason)

        sendEmail(
            to = user.email,
            subject = subject,
            body = body,
            isHtml = true
        )
    }

    /**
     * Send password recovery email.
     */
    fun sendPasswordRecovery(user: AuthDtos.UserResponse, recoveryCode: String) {
        val subject = "password_recovery"
        val body = buildPasswordRecoveryBody(user, recoveryCode)

        sendEmail(
            to = user.email,
            subject = subject,
            body = body,
            isHtml = true
        )
    }

    /**
     * Send device verification email.
     */
    fun sendDeviceVerification(user: AuthDtos.UserResponse, deviceId: String, fingerprint: String) {
        val subject = "device_verification"
        val body = buildDeviceVerificationBody(user, deviceId, fingerprint)

        sendEmail(
            to = user.email,
            subject = subject,
            body = body,
            isHtml = true
        )
    }

    /**
     * Send account security alert.
     */
    fun sendSecurityAlert(user: AuthDtos.UserResponse, alertType: String, details: String) {
        val subject = "security_alert"
        val body = buildSecurityAlertBody(user, alertType, details)

        sendEmail(
            to = user.email,
            subject = subject,
            body = body,
            isHtml = true
        )
    }

    /**
     * Send SMS via configured gateway.
     */
    fun sendSms(phoneNumber: String, message: String) {
        // In production, integrate with SMS gateway (Twilio, Vonage, etc.)
        logger.info("SMS to $phoneNumber: $message")
    }

    /**
     * Send push notification (FCM/APNs).
     */
    fun sendPushNotification(deviceToken: String, title: String, body: String) {
        // In production, integrate with FCM/APNs
        logger.info("Push to $deviceToken: $title - $body")
    }

    /**
     * Send bulk notification.
     */
    fun sendBulkEmail(
        recipients: List<String>,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ) {
        recipients.forEach { email ->
            sendEmail(email, subject, body, isHtml)
        }
    }

    /**
     * Send templated email.
     */
    fun sendTemplatedEmail(
        to: String,
        templateName: String,
        variables: Map<String, String>
    ) {
        val subject = getTemplateSubject(templateName, variables)
        val body = getTemplateBody(templateName, variables)

        sendEmail(to, subject, body, true)
    }

    // ===== Private helper methods =====

    private fun sendEmail(to: String, subject: String, body: String, isHtml: Boolean) {
        try {
            val properties = Properties()
            properties["mail.smtp.host"] = emailProperties.smtpHost
            properties["mail.smtp.port"] = emailProperties.smtpPort.toString()
            properties["mail.smtp.auth"] = emailProperties.smtpAuth.toString()
            properties["mail.smtp.starttls.enable"] = emailProperties.startTls.toString()

            val session = Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(
                        emailProperties.username,
                        emailProperties.password
                    )
                }
            })

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(emailProperties.fromAddress))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.subject = subject

            val mimeBodyPart = MimeBodyPart()
            mimeBodyPart.setContent(body, if (isHtml) "text/html; charset=utf-8" else "text/plain; charset=utf-8")

            val multipart = MimeMultipart()
            multipart.addBodyPart(mimeBodyPart)
            message.content = multipart

            Transport.send(message)
            logger.info("Email sent to $to: $subject")

        } catch (e: MessagingException) {
            logger.error("Failed to send email to $to", e)
        } catch (e: Exception) {
            logger.error("Unexpected error sending email to $to", e)
        }
    }

    private fun buildWelcomeEmailBody(user: AuthDtos.UserResponse, tempPassword: String?): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: 'Cairo', Arial, sans-serif; background: #050A16; color: #EDF7FB; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #112240; padding: 30px; border-radius: 12px; border: 1px solid #1E3A5F;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #00C98C, #35CBE0); border-radius: 12px; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center; font-size: 28px; font-weight: bold; color: #050A16;">يونس</div>
                        <h1 style="color: #E8B84A; margin: 0;">مرحباً بك في يونس!</h1>
                        <p style="color: #8892B0; margin: 10px 0;">حسابك تم إنشاؤه بنجاح</p>
                    </div>

                    <div style="background: #0A1628; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                        <p style="margin: 0 0 15px;">مرحباً <strong>${user.displayName}</strong>،</p>
                        <p style="color: #8892B0; margin: 0 0 15px;">
                            حسابك جديد وقد تم إنشاؤه بنجاح. يمكنك الآن البدء في استخدام تطبيق يونس للتواصل بشكل آمن.
                        </p>

                        ${if (tempPassword != null) """
                        <div style="background: #2A1A1A; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                            <p style="color: #FF6B6B; margin: 0 0 10px;">كلمة المرور المؤقتة:</p>
                            <p style="color: #EDF7FB; font-size: 18px; font-weight: bold; letter-spacing: 2px;">${tempPassword}</p>
                            <p style="color: #8892B0; font-size: 12px; margin-top: 10px;">يُنصح بتغييرها بعد الدخول</p>
                        </div>
                        """ else ""}

                        <div style="background: #1A2F4A; padding: 15px; border-radius: 8px; margin-bottom: 20px;">
                            <p style="color: #00C98C; margin: 0 0 10px;">بيانات دخولك:</p>
                            <p style="color: #EDF7FB; margin: 5px 0;">اسم المستخدم: ${user.username}</p>
                            <p style="color: #EDF7FB; margin: 5px 0;">معرّف يونس: ${user.redId}</p>
                        </div>
                    </div>

                    <div style="text-align: center; color: #8892B0; font-size: 12px; margin-top: 20px;">
                        <p>سيتم إلغاء صلاحية هذا الرابط تلقائياً بعد 24 ساعة</p>
                        <p>إذا لم تطلب هذا الحساب، يرجى تجاهل هذه الرسالة</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildAccountApprovedBody(user: AuthDtos.UserResponse): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: 'Cairo', Arial, sans-serif; background: #050A16; color: #EDF7FB; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #0A1A1A; padding: 30px; border-radius: 12px; border: 1px solid #1E3A5F;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #00C98C, #35CBE0); border-radius: 12px; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #050A16;">✓</div>
                        <h1 style="color: #00C98C; margin: 0;">تمت الموافقة!</h1>
                        <p style="color: #8892B0; margin: 10px 0;">حسابك معتمد الآن</p>
                    </div>

                    <div style="background: #1A2F4A; padding: 20px; border-radius: 8px;">
                        <p style="margin: 0 0 15px;">مرحباً <strong>${user.displayName}</strong>،</p>
                        <p style="color: #8892B0; margin: 0;">
                            تم approve حسابك بنجاح! يمكنك الآن الدخول واستخدام جميع ميزات يونس.
                        </p>
                    </div>

                    <div style="text-align: center; margin-top: 20px;">
                        <a href="yns://login" style="display: inline-block; background: #00C98C; color: #050A16; padding: 12px 30px; border-radius: 8px; text-decoration: none; font-weight: bold;">
                            فتح التطبيق
                        </a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildAccountRejectedBody(user: AuthDtos.UserResponse, reason: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: 'Cairo', Arial, sans-serif; background: #050A16; color: #EDF7FB; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #1A1A1A; padding: 30px; border-radius: 12px; border: 1px solid #3A1A1A;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #FF6B6B, #D9534F); border-radius: 12px; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #fff;">✕</div>
                        <h1 style="color: #FF6B6B; margin: 0;">للأسف، لم يتم approve حسابك</h1>
                    </div>

                    <div style="background: #2A1A1A; padding: 20px; border-radius: 8px;">
                        <p style="margin: 0 0 15px;">مرحباً <strong>${user.displayName}</strong>،</p>
                        <p style="color: #8892B0; margin: 0;">
                            تم رفض طلب حسابك لأسباب التالية:
                        </p>
                        <div style="background: #3A1A1A; padding: 15px; border-radius: 8px; margin-top: 15px; color: #FFB347;">
                            ${reason}
                        </div>
                        <p style="color: #8892B0; margin: 15px 0 0;">
                            يمكنك التواصل مع الإدارة إذا كنت تعتقد أن هذا خطأ.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildPasswordRecoveryBody(user: AuthDtos.UserResponse, recoveryCode: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: 'Cairo', Arial, sans-serif; background: #050A16; color: #EDF7FB; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #112240; padding: 30px; border-radius: 12px; border: 1px solid #1E3A5F;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #FFB347, #E8B84A); border-radius: 12px; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #050A16;">🔐</div>
                        <h1 style="color: #E8B84A; margin: 0;">استعادة كلمة المرور</h1>
                    </div>

                    <div style="background: #0A1628; padding: 20px; border-radius: 8px;">
                        <p style="margin: 0 0 15px;">مرحباً <strong>${user.displayName}</strong>،</p>
                        <p style="color: #8892B0; margin: 0 0 15px;">
                            رمز الاستعادة الخاص بك هو:
                        </p>
                        <div style="background: #2A1A1A; padding: 15px; border-radius: 8px; text-align: center;">
                            <p style="color: #E8B84A; font-size: 24px; font-weight: bold; letter-spacing: 4px; margin: 0;">${recoveryCode}</p>
                        </div>
                        <p style="color: #8892B0; margin: 20px 0 0;">
                            يُرجى حفظ هذا الرمز في مكان آمن. سيُستخدم لاستعادة حسابك في حالة نسيان كلمة المرور.
                        </p>
                        <p style="color: #8892B0; margin: 10px 0 0;">
                            إذا لم تطلب هذا، يمكنك تجاهل هذه الرسالة.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildDeviceVerificationBody(user: AuthDtos.UserResponse, deviceId: String, fingerprint: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: 'Cairo', Arial, sans-serif; background: #050A16; color: #EDF7FB; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #112240; padding: 30px; border-radius: 12px; border: 1px solid #1E3A5F;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #35CBE0, #00C98C); border-radius: 12px; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #050A16;">📱</div>
                        <h1 style="color: #35CBE0; margin: 0;">جهاز جديد تم تسجيله</h1>
                    </div>

                    <div style="background: #0A1628; padding: 20px; border-radius: 8px;">
                        <p style="margin: 0 0 15px;">مرحباً <strong>${user.displayName}</strong>،</p>
                        <p style="color: #8892B0; margin: 0 0 15px;">
                            تم تسجيل جهاز جديد على حسابك. يُرجى التحقق من أن هذا الجهاز خاص بك.
                        </p>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 20px;">
                            <div style="background: #1A2F4A; padding: 10px; border-radius: 6px;">
                                <p style="color: #8892B0; font-size: 12px; margin: 0 0 5px;">معرّف الجهاز</p>
                                <p style="color: #EDF7FB; font-weight: bold; word-break: break-all;">${deviceId}</p>
                            </div>
                            <div style="background: #1A2F4A; padding: 10px; border-radius: 6px;">
                                <p style="color: #8892B0; font-size: 12px; margin: 0 0 5px;">البصمة</p>
                                <p style="color: #EDF7FB; font-weight: bold; word-break: break-all; font-size: 12px;">${fingerprint.take(16)}...</p>
                            </div>
                        </div>

                        <div style="background: #2A1A1A; padding: 15px; border-radius: 8px; color: #FF6B6B;">
                            <p style="margin: 0;">⚠️ إذا لم تكن أنت من سجل هذا الجهاز، يُرجى الاتصال بالإدارة فوراً.</p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSecurityAlertBody(user: AuthDtos.UserResponse, alertType: String, details: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: 'Cairo', Arial, sans-serif; background: #050A16; color: #EDF7FB; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background: #1A1A2A; padding: 30px; border-radius: 12px; border: 1px solid #3A1A3A;">
                    <div style="text-align: center; margin-bottom: 30px;">
                        <div style="width: 60px; height: 60px; background: linear-gradient(135deg, #FF6B6B, #FFB347); border-radius: 12px; margin: 0 auto 15px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #050A16;">⚠️</div>
                        <h1 style="color: #FF6B6B; margin: 0;">تنبيه أمان</h1>
                    </div>

                    <div style="background: #1A1A2A; padding: 20px; border-radius: 8px;">
                        <p style="margin: 0 0 15px;">مرحباً <strong>${user.displayName}</strong>،</p>
                        <p style="color: #8892B0; margin: 0 0 15px;">
                            تم اكتشاف نشاط غير معتاد على حسابك:
                        </p>
                        <div style="background: #2A1A3A; padding: 15px; border-radius: 8px; margin-bottom: 15px;">
                            <p style="color: #FFB347; font-weight: bold; margin: 0 0 10px;">نوع التنبيه: ${alertType}</p>
                            <p style="color: #EDF7FB; margin: 0;">${details}</p>
                        </div>
                        <p style="color: #8892B0; margin: 0;">
                            إذا لم تكن أنت من قام بهذه العملية، يُرجى تغيير كلمة المرور فوراً.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getTemplateSubject(templateName: String, variables: Map<String, String>): String {
        return when (templateName) {
            "welcome_email" -> "مرحباً بك في يونس!"
            "account_approved" -> "تم approve حسابك!"
            "account_rejected" -> "حسابك لم يتم approve"
            "password_recovery" -> "رمز استعادة كلمة المرور"
            "device_verification" -> "جهاز جديد تم تسجيله"
            "security_alert" -> "تنبيه أمان لحسابك"
            else -> "إشعار من يونس"
        }
    }

    private fun getTemplateBody(templateName: String, variables: Map<String, String>): String {
        return when (templateName) {
            "welcome_email" -> buildWelcomeEmailBody(
                AuthDtos.UserResponse(
                    id = variables["id"] ?: "",
                    redId = variables["redId"] ?: "",
                    username = variables["username"] ?: "",
                    displayName = variables["displayName"] ?: "",
                    status = "PENDING",
                    role = "USER"
                ),
                variables["tempPassword"]
            )
            else -> "نص الإشعار"
        }
    }
}

data class EmailProperties(
    val smtpHost: String = "smtp.gmail.com",
    val smtpPort: Int = 587,
    val smtpAuth: Boolean = true,
    val startTls: Boolean = true,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "noreply@red.local"
)
