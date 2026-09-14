package com.red.sovereign.push

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.CallNotificationManager
import com.red.sovereign.calls.IncomingCallActivity
import com.red.sovereign.calls.VoipPushRegistrar
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.RedConnectionService
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Sovereign push receiver — UnifiedPush connector (self-hosted distributor, no Google push).
 *
 * Declared in the manifest with action `org.unifiedpush.android.connector.PUSH_EVENT`.
 * The distributor wakes this service even when the app process is dead:
 * - [onNewEndpoint]: persist the URL + POST it to /api/devices/push-token.
 * - [onMessage]: wake payload from our own server (JSON, see [handlePush]).
 *
 * Wake payload contract (server: UnifiedPushSender):
 * - `{"type":"CALL","callId","mode":"VOICE|VIDEO","callerId","callerName","callType","ts"}`
 * - `{"type":"MESSAGE","conversationId","senderId","ts"}` (no preview — E2EE stays sealed)
 * - `{"type":"CANCEL","callId","ts"}` (caller hung up before accept)
 *
 * Every callback is total — it must never throw: a crash here would drop the wake.
 */
class RedPushService : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val url = endpoint.url.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Log.w(TAG, "ignoring non-http push endpoint")
            return
        }
        VoipPushRegistrar.uploadEndpointNow(applicationContext, url)
        Log.i(TAG, "push endpoint saved (temporary=${endpoint.temporary})")
    }

    override fun onMessage(message: PushMessage, instance: String) {
        val text = runCatching { message.content.toString(Charsets.UTF_8) }.getOrDefault("").trim()
        if (text.isEmpty()) return
        handlePush(applicationContext, text)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        // Transient (no network / distributor down): register() re-runs on every app
        // start and BOOT_COMPLETED, so no retry loop here — it would drain battery.
        Log.w(TAG, "UnifiedPush registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        TokenStore(applicationContext).clearPushEndpoint()
        Log.i(TAG, "unregistered by distributor — endpoint cleared")
    }

    companion object {
        private const val TAG = "RedPushService"

        fun handlePush(context: Context, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("type")) {
                "CALL" -> handleCallPush(context, json)
                "MESSAGE" -> runCatching { RedConnectionService.start(context) }
                "CANCEL" -> {
                    val callId = json.optString("callId")
                    if (callId.isNotBlank()) runCatching { CallNotificationManager.cancel(context, callId) }
                }
                else -> Log.i(TAG, "unknown push type ignored")
            }
        }

        private fun handleCallPush(context: Context, json: JSONObject) {
            val callId = json.optString("callId").takeIf { it.isNotBlank() } ?: return
            val mode = json.optString("mode", "VOICE")
            val callType = json.optString("callType", IncomingCallActivity.CALL_TYPE_1TO1)
            val peer = json.optString("callerName").takeIf { it.isNotBlank() }
                ?: json.optString("callerId").takeIf { it.isNotBlank() }
                ?: "RED"
            val myId = TokenStore(context).redId.orEmpty()
            // Ring instantly from the push (works from a dead process); showIncoming
            // dedups against the socket OFFER arriving a moment later — no double ring.
            runCatching {
                CallNotificationManager.showIncoming(context, callId, peer, mode == "VIDEO", callType, myId)
            }.onFailure { Log.w(TAG, "showIncoming failed for $callId", it) }
            // Connect signaling so the full OFFER/session arrives over our socket.
            runCatching {
                if (callType == IncomingCallActivity.CALL_TYPE_1TO1) YounesCallService.listen(context)
                else RedConnectionService.start(context)
            }
        }
    }
}
