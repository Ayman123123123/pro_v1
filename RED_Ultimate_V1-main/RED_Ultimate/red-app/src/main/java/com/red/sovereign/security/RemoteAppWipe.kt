package com.red.sovereign.security

import android.content.Context
import com.red.sovereign.auth.TokenStore
import java.security.KeyStore

/**
 * Erases YOUNES-owned data only; unmanaged Android factory reset is intentionally unsupported.
 *
 * ⚠️ القاعدة الذهبية: كل مخزن يُنشأ في التطبيق يجب أن يُذكر هنا — وإلا بقي بعد المسح.
 * المخازن المؤكدة (تمت مطابقتها مع الكود الفعلي):
 *  - `red_sovereign.db`  ← Room + SQLCipher (الاسم الفعلي في RedDatabase)
 *  - `red_messages.db`   ← MessageStore (SQLiteOpenHelper)
 *  - `red_signal_protocol.db` ← مخزن جلسات libsignal الدائم
 *  - SharedPreferences/SecureStore: red_session, red_device_keys, red_server_endpoint,
 *    red_database_security (عبارة مرور SQLCipher!), younes_safety_verification,
 *    younes_group_sender_keys, younes_user_preferences
 *  - AndroidKeyStore: كل الأسماء المستعارة التي تبدأ بـ red.secure. (مفاتيح SecureStore)
 */
object RemoteAppWipe {

    /** قواعد البيانات الفعلية في التطبيق — الأسماء مطابقة حرفيًا لملفات الإنشاء. */
    private val DATABASES = listOf(
        "red_sovereign.db",        // Room/SQLCipher — RedDatabase.kt
        "red_messages.db",         // MessageStore.kt
        "red_signal_protocol.db"   // PersistentSignalProtocolStore.kt
    )

    /** مخازن SharedPreferences/SecureStore الفعلية — مطابقة لأسماء الإنشاء في الكود. */
    private val STORES = listOf(
        "red_session",              // TokenStore
        "red_device_keys",          // DeviceKeyManager
        "red_server_endpoint",      // ServerEndpoint
        "red_database_security",    // عبارة مرور SQLCipher — بدون مسحها يبقى مفتاح القاعدة!
        "younes_safety_verification", // SafetyViewModel
        "younes_group_sender_keys",   // GroupCryptoManager
        "younes_user_preferences"     // SettingsViewModel
    )

    fun execute(context: Context) {
        val app = context.applicationContext
        // 1) الجلسة أولًا — يُبطل التوكنات قبل أي شيء آخر
        TokenStore(app).clearSession()
        // 2) قواعد البيانات الثلاث الحقيقية
        DATABASES.forEach(app::deleteDatabase)
        // 3) كل المخازن المشفرة — بما فيها عبارة مرور قاعدة SQLCipher
        STORES.forEach { name ->
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        // 4) الذاكرات المؤقتة والملفات
        app.cacheDir.deleteRecursively()
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        // 5) مفاتيح Keystore المملوكة ليونس فقط (red.secure.*)
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.let { store ->
                val aliases = store.aliases()
                val owned = mutableListOf<String>()
                while (aliases.hasMoreElements()) aliases.nextElement().takeIf { it.startsWith("red.") }?.let(owned::add)
                owned.forEach(store::deleteEntry)
            }
        }
    }
}
