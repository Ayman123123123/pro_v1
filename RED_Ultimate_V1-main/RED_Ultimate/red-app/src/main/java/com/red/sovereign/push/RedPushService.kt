package com.red.sovereign.push

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.CallRingRegistry
import com.red.sovereign.calls.IncomingCallActivity
import com.red.sovereign.calls.PendingOfferPoller
import com.red.sovereign.calls.VoipPushRegistrar
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.RedConnectionService
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Sovereign push receiver - UnifiedPush connector (self-hosted distributor, no Google push).
 *
 * Declared in the manifest with action `org.unifiedpush.android.connector.PUSH_EVENT`.
 * The distributor wakes this service even when the app process is dead:
 * - [onNewEndpoint]: persist the URL + POST it to /api/devices/push-token.
 * - [onMessage]: wake payload from our own server (sealed envelope, see [handlePush]).
 *
 * Wake payload contract (server: com.red.server.notification.UnifiedPushSender):
 * - sealed v2 envelope (preferred):
 *     {"v":2,"e":"<base64url(nonce||AES-GCM(plaintext))>"}
 *   whose plaintext carries identifiers only - no names, no previews:
 *     {"t":"CALL","i":"<callId>","c":"<callType>","f":"<callerId>","m":"VOICE|VIDEO"}
 *     {"t":"CANCEL","i":"<callId>"}
 *     {"t":"MESSAGE","i":"<senderId>"}
 * - v1 plaintext wake (legacy tolerance): {"type":..,"callId":..,...} - still accepted.
 *
 * Every callback is total - it must never throw: a crash here would drop the wake.
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
        // Transient (no network / distributor down): VoipPushRegistrar owns the single
        // exponential-backoff reconnect loop - no tight retry loop here (battery).
        Log.w(TAG, "UnifiedPush registration failed: $reason")
        VoipPushRegistrar.onRegistrationFailed(applicationContext)
    }

    override fun onUnregistered(instance: String) {
        TokenStore(applicationContext).clearPushEndpoint()
        PushWakePresenter.clearDedup()
        Log.i(TAG, "unregistered by distributor - endpoint cleared")
    }

    companion object {
        private const val TAG = "RedPushService"

        fun handlePush(context: Context, text: String) {
            val json = decodeWake(context, text) ?: return
            val type = json.optString("t").ifBlank { json.optString("type") }
            when (type) {
                "CALL" -> handleCallPush(context, json)
                "MESSAGE" -> handleMessagePush(context)
                "CANCEL" -> handleCancelPush(context, json)
                else -> Log.i(TAG, "unknown push type '$type' ignored")
            }
        }

        /**
         * Opens a v2 sealed envelope with the stored endpoint, falling back to the
         * legacy plaintext JSON. Returns null when nothing usable could be decoded;
         * in that case we still nudge a sync so the socket/mailbox can recover.
         */
        private fun decodeWake(context: Context, text: String): JSONObject? {
            val body = SovereignPushCipher.envelopeBody(text)
            if (body != null) {
                val endpoint = VoipPushRegistrar.currentEndpoint(context)
                val plain = endpoint?.let { SovereignPushCipher.open(it, body) }
                if (plain != null) return runCatching { JSONObject(plain) }.getOrNull()
                Log.w(TAG, "sealed wake could not be opened (endpoint rotated?) - blind sync")
                nudgeSync(context)
                return null
            }
            return runCatching { JSONObject(text) }.getOrNull()
        }

        private fun handleCallPush(context: Context, json: JSONObject) {
            val callId = json.optString("i").ifBlank { json.optString("callId") }
                .takeIf { it.isNotBlank() } ?: return
            val callType = json.optString("c").ifBlank { json.optString("callType") }
                .takeIf { it.isNotBlank() } ?: IncomingCallActivity.CALL_TYPE_1TO1
            val mode = json.optString("m").ifBlank { json.optString("mode", "VOICE") }
            val from = json.optString("f").ifBlank { json.optString("callerId") }
            val myId = TokenStore(context).redId.orEmpty()

            // 1) Ring + open the full-screen incoming activity (works from a dead process).
            runCatching {
                PushWakePresenter.present(context, callType, callId, from, mode, myId)
            }.onFailure { Log.w(TAG, "present incoming failed for $callId", it) }

            // 2) Bring signaling up so the full OFFER/session arrives over our socket.
            runCatching {
                if (callType == IncomingCallActivity.CALL_TYPE_1TO1) YounesCallService.listen(context)
                else RedConnectionService.start(context)
            }.onFailure { Log.w(TAG, "signaling start failed for $callId", it) }

            // 3) Sync on open: pull any pending offer if the socket is slow to connect.
            runCatching { PendingOfferPoller.pollNow(context) }
        }

        private fun handleMessagePush(context: Context) {
            runCatching { RedConnectionService.start(context) }
            runCatching { PendingOfferPoller.pollNow(context) }
        }

        private fun handleCancelPush(context: Context, json: JSONObject) {
            val callId = json.optString("i").ifBlank { json.optString("callId") }
            if (callId.isBlank()) return
            runCatching { PushWakePresenter.cancel(context, callId) }
            runCatching { CallRingRegistry.cancel(context, callId) }
        }

        private fun nudgeSync(context: Context) {
            runCatching { RedConnectionService.start(context) }
            runCatching { PendingOfferPoller.pollNow(context) }
        }
    }
}
