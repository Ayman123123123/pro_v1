package com.red.sovereign.security

import android.content.Context
import com.red.sovereign.core.SecureStore
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * مخزنان مشفّران (Android Keystore عبر SecureStore AES/GCM):
 * - AppLockStore: رمز PIN (بصمة SHA-256 فقط، لا PIN خام) لقفل التطبيق.
 * - RecoveryCodesStore: رموز الاستعادة الورقية مشفرة على القرص.
 */

/** قفل التطبيق: Biometric (نظام) + PIN محلي مشفر — يُقفل عند الدخول عبر MainActivity. */
object AppLockStore {
    private const val STORE = "red_app_lock"
    private const val KEY_PIN_HASH = "pin_sha256"

    fun hasPin(context: Context): Boolean =
        SecureStore(context, STORE).get(KEY_PIN_HASH) != null

    /** يحفظ PIN من 4–8 أرقام. يعيد false إن كان التنسيق مرفوضاً. */
    fun setPin(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("^[0-9]{4,8}$"))) return false
        SecureStore(context, STORE).put(KEY_PIN_HASH, sha256Hex(pin))
        return true
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = SecureStore(context, STORE).get(KEY_PIN_HASH) ?: return false
        if (!pin.matches(Regex("^[0-9]{4,8}$"))) return false
        val candidate = sha256Hex(pin)
        return MessageDigest.isEqual(
            stored.toByteArray(Charsets.UTF_8),
            candidate.toByteArray(Charsets.UTF_8)
        )
    }

    fun clearPin(context: Context) {
        SecureStore(context, STORE).remove(KEY_PIN_HASH)
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** رموز الاستعادة الورقية: توليد/حفظ مشفر/عرض/نسخ/تحقق — تُستخدم في RecoveryHub + شاشة الدخول. */
object RecoveryCodesStore {
    private const val STORE = "red_recovery_codes"
    private const val KEY_CODES = "codes_csv"
    const val COUNT = 8

    private val random = SecureRandom()
    // بدون الأحرف الملتبسة (0/O و 1/I) لتفادي أخطاء النسخ الورقي.
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun load(context: Context): List<String> {
        val raw = SecureStore(context, STORE).get(KEY_CODES) ?: return emptyList()
        return raw.split(",").map { it.trim().uppercase() }.filter { it.matches(Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$")) }
    }

    fun save(context: Context, codes: List<String>) {
        val clean = codes.map { it.trim().uppercase() }
            .filter { it.matches(Regex("^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$")) }
        SecureStore(context, STORE).put(KEY_CODES, clean.joinToString(","))
    }

    /** توليد حقيقي: 8 رموز × 12 حرفاً (3 مجموعات) من SecureRandom — تُحفظ مشفرة فوراً. */
    fun generateFresh(context: Context): List<String> {
        val codes = List(COUNT) { single() }
        save(context, codes)
        return codes
    }

    /** استيراد رموز الخادم (من شاشة التسجيل) إلى المخزن المشفر — دمج لا استبدال أعمى. */
    fun importServerCodes(context: Context, serverCodes: List<String>) {
        val existing = load(context).toMutableSet()
        serverCodes.map { it.trim().uppercase() }
            .filter { it.matches(Regex("^[A-Z2-9-]{6,64}$")) }
            .forEach { existing.add(it) }
        SecureStore(context, STORE).put(KEY_CODES, existing.joinToString(","))
    }

    fun contains(context: Context, code: String): Boolean {
        val needle = code.trim().uppercase()
        if (needle.isBlank()) return false
        return load(context).any { it.equals(needle, ignoreCase = true) }
    }

    fun clear(context: Context) {
        SecureStore(context, STORE).remove(KEY_CODES)
    }

    private fun single(): String {
        fun group() = (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return "${group()}-${group()}-${group()}"
    }
}
