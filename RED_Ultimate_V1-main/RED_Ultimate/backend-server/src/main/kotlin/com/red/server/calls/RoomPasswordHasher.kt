package com.red.server.calls

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hasher كلمات مرور الغرف (PBKDF2-SHA256) — أمان احترافي لـ Conference/LiveStream.
 * الصيغة: pbkdf2-sha256-v1$iterations$saltBase64$hashBase64
 *
 * ─── تحصينات الدمج 2026-08-26 (اتحاد نسختنا + origin/main) ───
 * نسختنا كانت أقوى من origin في مكان واحد مهم: `LiveStreamService` عند origin كان
 * يهشّ كلمات مرور البث بـ SHA-256 عارية بلا ملح ولا تكرار، بينما نسختنا تمرّرها
 * عبر هذا الـ bean (PBKDF2 بـ 210 ألف تكرار). أُبقيت نسختنا كالمسار الأساسي.
 * وفي المقابل حُمِلت من origin التحصينات الأربعة التي كانت ناقصة عندنا:
 *   1. تقييد عدد التكرارات المقروء من الهاش المخزَّن (100k..1M): بدونه يكفي أن
 *      يُحقن صفٌّ بـ iterations=1 ليصبح التحقق رخيصاً وقابلاً للتكسير.
 *   2. التحقق من أطوال الملح والمفتاح قبل المقارنة.
 *   3. تغليف فكّ Base64 بـ runCatching: هاش مشوَّه كان يرفع استثناءً (500) بدل false.
 *   4. `spec.clearPassword()` حتى لا تبقى كلمة المرور في الذاكرة بعد الاشتقاق.
 *
 * وأُضيف مسار توافق للخلف (`legacySha256`) لأن الغرف/البثوث التي أُنشئت بكود
 * origin مخزَّنة بـ SHA-256 سداسي عارٍ (64 محرفاً)؛ بلا هذا المسار يُقفل أصحابها
 * خارج غرفهم بعد الدمج. يُقبل القديم للتحقق فقط — ولا يُكتب أبداً من جديد.
 */
@Component
class RoomPasswordHasher {

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 210_000

        /** حدود التكرارات المقبولة من هاش مخزَّن — تحصين محمول من origin/main. */
        private const val MIN_ITERATIONS = 100_000
        private const val MAX_ITERATIONS = 1_000_000

        private const val SALT_LENGTH = 16
        private const val HASH_LENGTH = 32
        private const val KEY_BITS = HASH_LENGTH * 8
        private const val FORMAT_VERSION = "pbkdf2-sha256-v1"

        private const val MIN_PASSWORD_LENGTH = 4
        private const val MAX_PASSWORD_LENGTH = 128

        /** SHA-256 سداسي عارٍ = صيغة الهاش القديمة قبل ترقية PBKDF2. */
        private val LEGACY_SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")
    }

    private val random = SecureRandom()

    /**
     * يهش كلمة المرور مع salt عشوائي.
     * @return سلسلة بصيغة: FORMAT_VERSION$iterations$saltBase64$hashBase64
     */
    fun hash(password: String): String {
        require(password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Room password must contain $MIN_PASSWORD_LENGTH-$MAX_PASSWORD_LENGTH characters"
        }
        val salt = generateSalt()
        val derived = derive(password, salt, ITERATIONS)
        val encoder = Base64.getEncoder()
        return listOf(
            FORMAT_VERSION,
            ITERATIONS.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(derived),
        ).joinToString(separator = "\$")
    }

    /**
     * يتحقق من كلمة مرور ضد هاش مخزن — PBKDF2 أولاً، ثم SHA-256 القديمة للتوافق.
     * يقارن بطريقة ثابتة الزمن (MessageDigest.isEqual) لمنع هجوم التوقيت.
     */
    fun verify(password: String?, storedHash: String?): Boolean {
        if (password.isNullOrEmpty() || storedHash.isNullOrBlank()) return false

        // غرف/بثوث أُنشئت بكود origin قبل الدمج — تحقق فقط، لا كتابة.
        if (LEGACY_SHA256_HEX.matches(storedHash)) return verifyLegacySha256(password, storedHash)

        val parts = storedHash.split('$', limit = 4)
        if (parts.size != 4 || parts[0] != FORMAT_VERSION) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in MIN_ITERATIONS..MAX_ITERATIONS } ?: return false

        return runCatching {
            val salt = decodeBase64(parts[2])
            val expectedHash = decodeBase64(parts[3])
            salt.size >= SALT_LENGTH && expectedHash.size == HASH_LENGTH &&
                MessageDigest.isEqual(expectedHash, derive(password, salt, iterations))
        }.getOrDefault(false)
    }

    /**
     * هل يستحق هذا الهاش إعادة تهشير عند نجاح تسجيل الدخول؟
     * صحيح للصيغة القديمة أو لتكرارات أقل من المعيار الحالي — يستدعيها المُضيف
     * ليُرقّي الصف بهدوء بعد أول تحقق ناجح.
     */
    fun needsRehash(storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        if (LEGACY_SHA256_HEX.matches(storedHash)) return true
        val parts = storedHash.split('$', limit = 4)
        if (parts.size != 4 || parts[0] != FORMAT_VERSION) return true
        return (parts[1].toIntOrNull() ?: 0) < ITERATIONS
    }

    /**
     * يولد salt عشوائي جديد (للاستخدامات التي تحتاج salt يدوي).
     */
    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }

    /**
     * نسخة جديدة لكل اشتقاق: `SecretKeyFactory` ليست مضمونة الأمان الخيطي، وحقلٌ
     * مشترك منها كان يُستخدم من عدة طلبات تحقق متزامنة.
     */
    private fun keyFactory(): SecretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM)

    /** اشتقاق PBKDF2 — يمحو كلمة المرور من الذاكرة بعد الانتهاء. */
    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            keyFactory().generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** يقبل الأبجدية القياسية وأبجدية URL: origin كان يكتب Base64 آمن-للمسار. */
    private fun decodeBase64(value: String): ByteArray =
        runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { Base64.getUrlDecoder().decode(value) }

    /** مقارنة ثابتة الزمن مع الهاش القديم — للتحقق من الصفوف قبل ترقية PBKDF2 فقط. */
    private fun verifyLegacySha256(password: String, storedHash: String): Boolean =
        MessageDigest.isEqual(
            legacySha256(password).toByteArray(Charsets.US_ASCII),
            storedHash.lowercase().toByteArray(Charsets.US_ASCII),
        )

    /** تجزئة SHA-256 القديمة — لا تُستخدم للكتابة أبداً، فهي بلا ملح ولا تكرار. */
    private fun legacySha256(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(password.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
