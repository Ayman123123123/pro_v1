package com.red.sovereign.auth

import android.content.Context
import com.red.sovereign.core.SecureStore

class TokenStore(val context: Context) {
    private val store = SecureStore(context, "red_session")
    val accessToken get() = store.get("access")
    val refreshToken get() = store.get("refresh")
    val deviceId get() = store.get("device_id")
    val fcmToken get() = store.get("fcm_token")
    val redId get() = store.get("red_id")
    val username get() = store.get("username")
    val pstnEnabled get() = store.get("pstn_enabled") == "true"
    /** دور الحساب — "ADMIN" أو "USER". يُستخدم لإظهار/إخفاء أدوات الإدارة في التطبيق. */
    val role get() = store.get("role") ?: "USER"
    val isAdmin get() = role == "ADMIN"

    fun rememberDevice(value: String) = store.put("device_id", value)
    fun saveFcmToken(value: String) = store.put("fcm_token", value)
    fun saveUsername(value: String) = store.put("username", value)
    /** اسم الحساب غير حساس؛ لا نخزن كلمة المرور لاستئناف موافقة الحساب. */
    fun rememberPendingLogin(username: String) = store.put("pending_username", username)
    fun pendingUsername(): String? = store.get("pending_username")
    /** يمحو أيضًا الاسم القديم الموروث لكلمة المرور من أي إصدار سابق. */
    fun clearPendingLogin() = store.remove("pending_username", "pending_password")
    fun save(response: AuthResponse) {
        store.put("access", response.accessToken); store.put("refresh", response.refreshToken)
        response.deviceId?.let(::rememberDevice)
        store.put("red_id", response.user.redId); store.put("username", response.user.username)
        store.put("pstn_enabled", response.user.pstnEnabled.toString())
        store.put("role", response.user.role)
        clearPendingLogin()
    }
    fun updateTokens(response: RefreshResponse) { store.put("access", response.accessToken); store.put("refresh", response.refreshToken) }
    fun clearSession() = store.remove("access", "refresh", "red_id", "username", "role", "pstn_enabled", "pending_username", "pending_password")
}
