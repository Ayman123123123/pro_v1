package com.red.sovereign.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Self-managed ConnectionService — يسجّل RED كـ VoIP app في Android Telecom.
 * يتيح:
 * - ظهور مكالمات RED في dialer النظام (الرد من Bluetooth car, watch, etc.)
 * - Hold/Resume من النظام (بدون تطبيق RED)
 * - Routing audio للـ headset/earpiece/speaker تلقائياً
 *
 * متوافق مع [androidx.core.telecom.CallsManager] API 35+.
 */
class YounesConnectionService : ConnectionService() {

    private val activeConnections = mutableMapOf<String, YounesConnection>()

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
        return conn
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        // المستخدم ألغى المكالمة قبل إنشائها
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
        return conn
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        // فشل في إنشاء المكالمة الواردة
    }

    fun getConnection(redId: String): YounesConnection? = activeConnections[redId]

    /**
     * Connection مخصص لـ Younes. يستجيب لأحداث النظام (Hold, Unhold, Answer, Reject, Disconnect).
     */
    inner class YounesConnection(
        private val redId: String,
        val incoming: Boolean
    ) : Connection() {
        private val serviceContext: Context
            get() = this@YounesConnectionService

        init {
            connectionProperties = PROPERTY_SELF_MANAGED
            connectionCapabilities = CAPABILITY_SUPPORT_HOLD or CAPABILITY_HOLD
            audioModeIsVoip = true
        }

        override fun onAnswer() {
            // يطلب من YounesCallService قبول المكالمة
            setActive()
            val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_ACCEPT)
            ContextCompat.startForegroundService(serviceContext, intent)
        }

        override fun onReject() {
            val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_REJECT)
            ContextCompat.startForegroundService(serviceContext, intent)
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        }

        override fun onDisconnect() {
            val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_END)
            ContextCompat.startForegroundService(serviceContext, intent)
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        override fun onHold() {
            // النظام يطلب hold — نرسل HOLD signal للطرف الآخر
            val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_HOLD)
            ContextCompat.startForegroundService(serviceContext, intent)
            setOnHold()
        }

        override fun onUnhold() {
            val intent = Intent(serviceContext, YounesCallService::class.java).setAction(YounesCallService.ACTION_RESUME)
            ContextCompat.startForegroundService(serviceContext, intent)
            setActive()
        }

        override fun onSeparate() {
            // Conference call: نخبر المستخدم أن مكالمته انفصلت
        }

        override fun onShowIncomingCallUi() {
            // يفتح MainActivity لاستقبال المكالمة
            val ui = Intent(serviceContext, com.red.sovereign.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            serviceContext.startActivity(ui)
        }

        override fun onPlayDtmfTone(c: Char) {
            // DTMF من النظام (مثلاً dial pad في car mode)
            YounesCallService.dtmf(serviceContext, c)
        }
    }

    companion object {
        /**
         * يسجّل PhoneAccount مع النظام. يجب استدعاؤها عند launch التطبيق.
         * Idempotent — آمن للاستدعاء المتعدد.
         */
        fun register(context: Context) {
            val telecomManager = context.getSystemService(TELECOM_SERVICE) as TelecomManager
            val componentName = android.content.ComponentName(context, YounesConnectionService::class.java)
            val accountHandle = PhoneAccountHandle(componentName, "younes-self-managed")
            val capabilities = PhoneAccount.CAPABILITY_SELF_MANAGED or
                PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                PhoneAccount.CAPABILITY_VIDEO_CALLING
            val account = PhoneAccount.builder(accountHandle, "يونس RED VoIP")
                .setCapabilities(capabilities)
                .setShortDescription("مكالمات يونس المشفرة")
                .addSupportedUriScheme("younes")
                .build()
            try {
                telecomManager.registerPhoneAccount(account)
            } catch (e: SecurityException) {
                // MANAGE_OWN_CALLS permission not granted yet
                android.util.Log.w("YounesConnectionService", "registerPhoneAccount failed: ${e.message}")
            }
        }
    }
}
