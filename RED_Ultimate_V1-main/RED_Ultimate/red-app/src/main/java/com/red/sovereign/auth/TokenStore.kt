package com.red.sovereign.auth

import android.content.Context
import com.red.sovereign.core.SecureStore

class TokenStore(val context: Context) {
    private val store = SecureStore(context, "red_session")
    val accessToken get() = store.get("access")
    val refreshToken get() = store.get("refresh")
    val deviceId get() = store.get("device_id")
    val redId get() = store.get("red_id")
    val username get() = store.get("username")
    val pstnEnabled get() = store.get("pstn_enabled") == "true"

    fun rememberDevice(value: String) = store.put("device_id", value)
    fun save(response: AuthResponse) {
        store.put("access", response.accessToken); store.put("refresh", response.refreshToken)
        response.deviceId?.let(::rememberDevice)
        store.put("red_id", response.user.redId); store.put("username", response.user.username)
        store.put("pstn_enabled", response.user.pstnEnabled.toString())
    }
    fun updateTokens(response: RefreshResponse) { store.put("access", response.accessToken); store.put("refresh", response.refreshToken) }
    /** Remove every value derived from an authenticated account, not only bearer credentials. */
    fun clearSession() = store.remove("access", "refresh", "device_id", "red_id", "username", "pstn_enabled")
}
