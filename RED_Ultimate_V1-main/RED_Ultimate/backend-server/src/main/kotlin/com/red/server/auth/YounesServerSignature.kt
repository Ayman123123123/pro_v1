package com.red.server.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * بصمة خادم يونس كما تظهر على السلك — نسخة الخادم.
 *
 * SERVER_FINGERPRINT: بصمة فريدة للخادم تُولّد من مفتاح الهوية عند الإقلاع الأول
 * وتخزن في إعدادات الخادم. يستخدمها التطبيق للمطابقة أثناء الاكتشاف التلقائي.
 */
object YounesServerSignature {
    // بصمة فريدة للخادم - تُولّد من مفتاح الهوية عند الإقلاع الأول
    // يجب أن تكون متطابقة مع ما يتوقعه التطبيق
    const val SERVER_FINGERPRINT = "younes-server-v1"

    fun generateFingerprintFromIdentityKey(identityKey: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(identityKey.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(digest).take(32)
        } catch (_: Exception) {
            SERVER_FINGERPRINT
        }
    }
}