package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Registers an FCM / VoIP wake token with the sovereign backend.
 * Firebase is optional — if the SDK is not on the classpath we keep any
 * token already stored and still POST it when present.
 */
object VoipPushRegistrar {
    fun rememberToken(context: Context, token: String) {
        if (token.isBlank()) return
        TokenStore(context).saveFcmToken(token)
        register(context)
    }

    fun register(context: Context) {
        val tokens = TokenStore(context)
        val stored = tokens.fcmToken?.takeIf { it.isNotBlank() } ?: discoverFirebaseToken() ?: return
        tokens.saveFcmToken(stored)
        if (tokens.accessToken.isNullOrBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            val body = JSONObject()
                .put("token", stored)
                .put("platform", "ANDROID")
                .toString()
            runCatching { AuthorizedApiClient(tokens).request("POST", "/api/devices/push-token", body) }
        }
    }

    private fun discoverFirebaseToken(): String? = runCatching {
        val messaging = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
        val instance = messaging.getMethod("getInstance").invoke(null)
        val task = messaging.getMethod("getToken").invoke(instance)
        val resultMethod = task.javaClass.methods.firstOrNull { it.name == "getResult" && it.parameterCount == 0 }
        resultMethod?.invoke(task) as? String
    }.getOrNull()?.takeIf { !it.isNullOrBlank() }
}
