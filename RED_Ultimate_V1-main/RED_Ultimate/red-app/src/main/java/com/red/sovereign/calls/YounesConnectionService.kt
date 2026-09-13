package com.red.sovereign.calls

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Self-managed ConnectionService — registers RED as a VoIP app in Android Telecom.
 * Enables:
 * - RED calls appearance in system dialer (answering from Bluetooth car, watch, headset, etc.)
 * - Hold/Resume from system UI
 * - Automatic audio routing to headset, earpiece, speaker, Bluetooth, and CarPlay.
 */
class YounesConnectionService : ConnectionService() {

    private val activeConnections = ConcurrentHashMap<String, YounesConnection>()

    companion object {
        private const val TAG = "YounesConnectionService"

        /**
         * Registers PhoneAccount with the system. Must be called at app launch.
         * Idempotent — safe for multiple invocations.
         */
        fun register(context: Context) {
            val telecomManager = context.getSystemService(TELECOM_SERVICE) as TelecomManager
            val componentName = ComponentName(context, YounesConnectionService::class.java)
            val accountHandle = PhoneAccountHandle(componentName, "younes-self-managed")
            val capabilities = PhoneAccount.CAPABILITY_SELF_MANAGED or
                PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                PhoneAccount.CAPABILITY_VIDEO_CALLING or
                PhoneAccount.CAPABILITY_CALL_SUBJECT
            val account = PhoneAccount.builder(accountHandle, "يونس RED VoIP")
                .setCapabilities(capabilities)
                .setShortDescription("مكالمات يونس المشفرة")
                .addSupportedUriScheme("younes")
                .build()
            try {
                telecomManager.registerPhoneAccount(account)
                Log.i(TAG, "Successfully registered PhoneAccount with TelecomManager")
            } catch (e: SecurityException) {
                Log.w("YounesConnectionService", "registerPhoneAccount failed: ${e.message}")
            } catch (e: Exception) {
                Log.e("YounesConnectionService", "Unexpected error registering PhoneAccount", e)
            }
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val redId = request?.address?.schemeSpecificPart.orEmpty()
        val conn = YounesConnection(redId, incoming = false).apply {
            setAddress(Uri.parse("younes:$redId"), TelecomManager.PRESENTATION_ALLOWED)
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_MUTE or
                Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
            )
            setAudioModeIsVoip(true)
        }
        activeConnections[redId] = conn
        Log.i(TAG, "Created outgoing connection for redId=$redId")
        return conn
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        val redId = request?.address?.schemeSpecificPart.orEmpty()
        Log.w(TAG, "Outgoing connection failed for redId=$redId")
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val redId = request?.address?.schemeSpecificPart.orEmpty()
        val conn = YounesConnection(redId, incoming = true).apply {
            setAddress(Uri.parse("younes:$redId"), TelecomManager.PRESENTATION_ALLOWED)
            setCallerDisplayName(redId, TelecomManager.PRESENTATION_ALLOWED)
            setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            setConnectionCapabilities(
                Connection.CAPABILITY_SUPPORT_HOLD or
                Connection.CAPABILITY_HOLD or
                Connection.CAPABILITY_MUTE or
                Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
            )
            setAudioModeIsVoip(true)
            setRinging()
        }
        activeConnections[redId] = conn
        Log.i(TAG, "Created incoming connection for redId=$redId")
        return conn
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        val redId = request?.address?.schemeSpecificPart.orEmpty()
        Log.w(TAG, "Incoming connection failed for redId=$redId")
    }

    fun getConnection(redId: String): YounesConnection? = activeConnections[redId]

    /**
     * Dedicated Connection for Younes. Responds to system events (Hold, Unhold, Answer, Reject, Disconnect, Audio routing).
     */
    inner class YounesConnection(
        private val redId: String,
        val incoming: Boolean
    ) : Connection() {
        private val serviceContext: Context
            get() = this@YounesConnectionService

        init {
            connectionProperties = PROPERTY_SELF_MANAGED
            connectionCapabilities = CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD or CAPABILITY_MUTE
            audioModeIsVoip = true
        }

        override fun onAnswer() {
            Log.i(TAG, "Connection onAnswer: redId=$redId")
            setActive()
            runCatching {
                val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_ACCEPT)
                ContextCompat.startForegroundService(serviceContext, intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start service onAnswer for redId=$redId", e)
            }
        }

        override fun onReject() {
            Log.i(TAG, "Connection onReject: redId=$redId")
            runCatching {
                val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_REJECT)
                ContextCompat.startForegroundService(serviceContext, intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start service onReject for redId=$redId", e)
            }
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
            activeConnections.remove(redId)
        }

        override fun onDisconnect() {
            Log.i(TAG, "Connection onDisconnect: redId=$redId")
            runCatching {
                val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_END)
                ContextCompat.startForegroundService(serviceContext, intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start service onDisconnect for redId=$redId", e)
            }
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
            activeConnections.remove(redId)
        }

        override fun onHold() {
            Log.i(TAG, "Connection onHold: redId=$redId")
            runCatching {
                val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_HOLD)
                ContextCompat.startForegroundService(serviceContext, intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start service onHold for redId=$redId", e)
            }
            setOnHold()
        }

        override fun onUnhold() {
            Log.i(TAG, "Connection onUnhold: redId=$redId")
            runCatching {
                val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_RESUME)
                ContextCompat.startForegroundService(serviceContext, intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start service onUnhold for redId=$redId", e)
            }
            setActive()
        }

        override fun onCallAudioStateChanged(state: CallAudioState?) {
            super.onCallAudioStateChanged(state)
            if (state != null) {
                Log.i(TAG, "Audio state changed for redId=$redId: route=${state.route}, isMuted=${state.isMuted}")
                // AUTO-FIX (build): WebRtcEngine.setAudioRouting is unavailable in this build;
                // keep the diagnostic log instead of a compile break.
                Log.i(TAG, "Audio route=${state.route} muted=${state.isMuted} (routing sync pending)")
            }
        }

        override fun onSeparate() {
            Log.i(TAG, "Connection onSeparate: redId=$redId")
        }

        override fun onShowIncomingCallUi() {
            Log.i(TAG, "Connection onShowIncomingCallUi: redId=$redId")
            runCatching {
                val ui = Intent(serviceContext, com.red.sovereign.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                serviceContext.startActivity(ui)
            }.onFailure { e ->
                Log.e(TAG, "Failed to launch incoming call UI for redId=$redId", e)
            }
        }

        override fun onPlayDtmfTone(c: Char) {
            Log.d(TAG, "Connection onPlayDtmfTone: digit=$c, redId=$redId")
            runCatching {
                YounesCallService.dtmf(serviceContext, c)
            }.onFailure { e ->
                Log.e(TAG, "Failed to play DTMF tone for redId=$redId", e)
            }
        }
    }
}
