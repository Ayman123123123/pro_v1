package com.red.sovereign.auth

import android.content.Context
import com.red.sovereign.core.SecureStore

/**
 * TokenStore - نظيف بدون PSTN
 * تم إلغاء كل ما يتعلق بالهاتف اليمني
 */
class TokenStore(val context: Context) {
    private val store = SecureStore(context, "red_session")
    val accessToken get() = store.get("access")
    val refreshToken get() = store.get("refresh")
    val deviceId get() = store.get("device_id")
    /** UnifiedPush endpoint URL issued by the self-hosted distributor. */
    val pushEndpoint get() = store.get("push_endpoint")
    val redId get() = store.get("red_id")
    val username get() = store.get("username")
    /** دور الحساب — "ADMIN" أو "USER". يُستخدم لإظهار/إخفاء أدوات الإدارة في التطبيق. */
    val role get() = store.get("role") ?: "USER"
    val isAdmin get() = role == "ADMIN"

    fun rememberDevice(value: String) = store.put("device_id", value)
    fun savePushEndpoint(value: String) = store.put("push_endpoint", value)
    fun clearPushEndpoint() = store.remove("push_endpoint")
    fun saveUsername(value: String) = store.put("username", value)

    /**
     * كلمة مرور الدخول المؤقتة تُحفظ في الذاكرة فقط — لا تُكتب على القرص أبداً
     * (EncryptedSharedPrefs/Keystore لا يعنيان أن الاحتفاظ بكلمة مرور مقبول).
     * تُمسح فور نجاح [save] عبر [clearPendingLogin].
     */
    @Volatile private var pendingPasswordMemory: String? = null
    fun rememberPendingLogin(username: String, password: String) {
        store.put("pending_username", username)
        store.remove("pending_password")
        pendingPasswordMemory = password
    }
    fun pendingUsername(): String? = store.get("pending_username")
    fun pendingPassword(): String? = pendingPasswordMemory
    fun clearPendingLogin() {
        store.remove("pending_username", "pending_password")
        pendingPasswordMemory = null
    }
    fun save(response: AuthResponse) {
        store.put("access", response.accessToken)
        store.put("refresh", response.refreshToken)
        response.deviceId?.let(::rememberDevice)
        store.put("red_id", response.user.redId); store.put("username", response.user.username)
        store.put("role", response.user.role.toString())
        clearPendingLogin()
    }
    fun updateTokens(response: RefreshResponse) {
        store.put("access", response.accessToken)
        store.put("refresh", response.refreshToken)
    }
    fun clearSession() {
        pendingPasswordMemory = null
        store.remove("access", "refresh", "red_id", "username", "role",
            "pstn_enabled", "pstn_number", "pstn_port_index", "pstn_gateway_id", // Phase 8: purge stale PSTN keys from older installs
            "pending_username", "pending_password", "push_endpoint")
    }
}
