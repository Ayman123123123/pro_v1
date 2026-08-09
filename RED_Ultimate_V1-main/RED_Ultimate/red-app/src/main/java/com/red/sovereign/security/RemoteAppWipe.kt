package com.red.sovereign.security

import android.content.Context
import com.red.sovereign.auth.TokenStore
import java.security.KeyStore

/** Erases YOUNES-owned data only; unmanaged Android factory reset is intentionally unsupported. */
object RemoteAppWipe {
    fun execute(context: Context) {
        val app = context.applicationContext
        TokenStore(app).clearSession()
        listOf("red_messages.db", "red_signal_protocol.db").forEach(app::deleteDatabase)
        listOf("red_session", "red_device_keys", "red_server_endpoint").forEach { name ->
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        app.cacheDir.deleteRecursively()
        app.filesDir.listFiles()?.forEach { it.deleteRecursively() }
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
