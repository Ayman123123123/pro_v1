package com.red.sovereign.features.chat

import android.content.Context

/**
 * P1-D — كتم @all المنفصل (واتساب 8/2026: @all يرن حتى في المكتومة إلا عند كتمه).
 * SharedPreferences محلية لكل محادثة — بلا تغيير سكيمة DB.
 */
object MuteAtAllPrefs {
    private const val PREFS = "younes_mute_atall"

    fun isMuted(context: Context, conversationId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(conversationId), false)

    fun setMuted(context: Context, conversationId: String, muted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(key(conversationId), muted).apply()
    }

    private fun key(conversationId: String): String =
        "mute_all_" + conversationId.trim().uppercase()
}
