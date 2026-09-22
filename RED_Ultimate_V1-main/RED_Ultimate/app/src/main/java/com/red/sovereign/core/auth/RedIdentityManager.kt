package com.red.sovereign.core.auth

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedIdentityManager @Inject constructor(private val context: Context) : IdentityManager {
    private val prefs = context.getSharedPreferences("red_sovereign_identity", Context.MODE_PRIVATE)

    /** Stores the approved RED identity and its authentication token. */
    fun finalizeIdentity(redId: String, token: String) {
        prefs.edit().apply {
            putString("RED_ID", redId)
            putString("AUTH_TOKEN", token)
            putBoolean("IS_APPROVED", true)
            apply()
        }
    }

    fun isApproved(): Boolean = prefs.getBoolean("IS_APPROVED", false)
    fun getRedId(): String = prefs.getString("RED_ID", "") ?: ""
}
