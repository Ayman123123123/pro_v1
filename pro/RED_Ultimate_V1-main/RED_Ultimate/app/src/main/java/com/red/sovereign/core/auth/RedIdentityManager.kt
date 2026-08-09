package com.red.sovereign.core.auth

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedIdentityManager @Inject constructor(private val context: Context) : IdentityManager {
    private val prefs = context.getSharedPreferences("red_sovereign_identity", Context.MODE_PRIVATE)

    /**
     * يتم استدعاؤه فور استلام إشارة APPROVED من السيرفر
     * يقوم بربط المعرف السيادي (مثلاً: @RED_967_77) ورقم الـ GSM المخصص
     */
    fun finalizeIdentity(redId: String, gsmNumber: String, token: String) {
        prefs.edit().apply {
            putString("RED_ID", redId)
            putString("GSM_NUMBER", gsmNumber)
            putString("AUTH_TOKEN", token)
            putBoolean("IS_APPROVED", true)
            apply()
        }
    }

    fun isApproved(): Boolean = prefs.getBoolean("IS_APPROVED", false)
    fun getRedId(): String = prefs.getString("RED_ID", "") ?: ""
}
