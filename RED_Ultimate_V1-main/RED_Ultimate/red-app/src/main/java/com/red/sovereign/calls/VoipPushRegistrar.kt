package com.red.sovereign.calls

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Registers an FCM / VoIP wake token with the sovereign backend.
 * Firebase is optional — if the app was built without google-services.json
 * (no FirebaseApp) we keep any token already stored and still POST it when present.
 *
 * The token enables HIGH-priority DATA messages (server: NotificationService
 * android.priority=HIGH + data type=VOIP) which wake the process via
 * [PstnFcmListenerService.onMessageReceived] even when the app is killed —
 * notification-payload messages would be throttled in Doze instead.
 */
object VoipPushRegistrar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun rememberToken(context: Context, token: String) {
        if (token.isBlank()) return
        TokenStore(context).saveFcmToken(token)
        register(context)
    }

    fun register(context: Context) {
        scope.launch {
            val tokens = TokenStore(context)
            val stored = tokens.fcmToken?.takeIf { it.isNotBlank() }
            val cached = readCachedListenerToken(context)
            val token = stored ?: cached ?: discoverFirebaseToken(context) ?: return@launch
            tokens.saveFcmToken(token)
            if (tokens.accessToken.isNullOrBlank()) return@launch
            val body = JSONObject()
                .put("token", token)
                .put("platform", "ANDROID")
                .toString()
            runCatching { AuthorizedApiClient(tokens).request("POST", "/api/devices/push-token", body) }
        }
    }

    /** توكن خزّنه PstnFcmListenerService.onNewToken أثناء موت التطبيق (prefs) قبل أي إطلاق لاحق. */
    private fun readCachedListenerToken(context: Context): String? = runCatching {
        context.getSharedPreferences("pstn_fcm", Context.MODE_PRIVATE)
            .getString("last_token", null)
    }.getOrNull()?.takeIf { !it.isNullOrBlank() }

    /**
     * جلب توكن FCM دون حجب: FirebaseMessaging.getInstance().token.await()
     * (kotlinx-coroutines-play-services) بدل getResult() التزامني الذي كان
     * يرمي IllegalStateException قبل اكتمال الـ Task فيعيد null دائماً —
     * فلا يُسجَّل أي توكن ولا تصل رسائل DATA عالية الأولوية أبداً.
     */
    private suspend fun discoverFirebaseToken(context: Context): String? = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        FirebaseMessaging.getInstance().token.await()
    }.getOrNull()?.takeIf { !it.isNullOrBlank() }
}
