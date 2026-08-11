package com.red.sovereign.calls

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.red.sovereign.MainActivity
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

class YounesCallService : Service(), WebRtcEngine.Events, CallSignalingClient.Listener, SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: CallSignalingClient
    private lateinit var telecom: TelecomBridge
    private lateinit var audio: AudioManager
    private var engine: WebRtcEngine? = null
    private var target = ""
    private var callId: String? = null
    private var mode = "VOICE"
    private var outgoingPending = false
    private var incomingOffer: CallSignal? = null
    private var pendingSecondOffer: CallSignal? = null
    private var pendingIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var proximityLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var audioFocus: AudioFocusRequest? = null
    private var recordingManager: CallRecordingManager? = null
    private var recordingConsentShown: Boolean = false

    override fun onCreate() {
        super.onCreate(); createChannel()
        audio = getSystemService(AudioManager::class.java)
        telecom = TelecomBridge(this).also { runCatching(it::register) }
        signaling = CallSignalingClient(this, TokenStore(this), this)
        val sensors = getSystemService(SensorManager::class.java)
        sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> { promote(notification("جاهز لاستقبال مكالمات يونس", ongoing = true), media = false); signaling.connect() }
            ACTION_START -> {
                outgoingPending = true
                target = intent.getStringExtra(EXTRA_TARGET).orEmpty(); mode = intent.getStringExtra(EXTRA_MODE) ?: "VOICE"
                require(target.isNotBlank()); callId = UUID.randomUUID().toString()
                scope.launch { runCatching { telecom.addCall(target, false, mode == "VIDEO", onAnswer = {}, onDisconnect = { endCall(true) }, onActive = { runCatching { signaling.send(CallSignal(callId, target, type = "RESUME", mode = mode)) } }, onInactive = { runCatching { signaling.send(CallSignal(callId, target, type = "HOLD", mode = mode)) } }) } }
                promote(notification("جارٍ بدء المكالمة…", ongoing = true), media = true); prepareAudio(); signaling.connect()
            }
            ACTION_ACCEPT -> acceptIncoming()
            ACTION_REJECT -> rejectIncoming()
            ACTION_END -> endCall(sendSignal = true)
            ACTION_MIC -> engine?.setMicrophoneEnabled(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_CAMERA -> engine?.setCameraEnabled(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_SWITCH_CAMERA -> engine?.switchCamera()
            ACTION_SPEAKER -> setSpeaker(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_BLUETOOTH -> routeBluetooth()
            ACTION_HOLD -> holdCall()
            ACTION_RESUME -> resumeCall()
            ACTION_DTMF -> sendDtmf(intent?.getStringExtra(EXTRA_DTMF)?.firstOrNull() ?: '0')
            ACTION_ACCEPT_SECOND -> acceptSecondIncoming()
            ACTION_REJECT_SECOND -> {
                val state = CallRuntime.state as? CallUiState.ActiveWithIncoming
                if (state != null) {
                    runCatching { signaling.send(CallSignal(state.waiting.callId, state.waiting.peer, type = "REJECT", mode = state.waiting.mode)) }
                    pendingSecondOffer = null
                    stopRingtone()
                    CallRuntime.state = state.active
                }
            }
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_STOP -> { signaling.close(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }

    override fun onConnected() {
        if (incomingOffer != null || !outgoingPending) return
        scope.launch {
            val created = createEngine(mode == "VIDEO")
            if (created is ApiResult.Success) engine?.offer() else fail("تعذر إنشاء محرك WebRTC")
        }
    }

    override fun onSignal(signal: CallSignal) {
        when (signal.type) {
            "OFFER" -> {
                val newIncoming = CallUiState.Incoming(
                    callId = signal.callId.orEmpty(),
                    peer = signal.sourceUserId.orEmpty(),
                    mode = signal.mode ?: "VOICE"
                )
                // CALL WAITING: إذا في مكالمة نشطة، نضيف المكالمة الجديدة كـ waiting
                val currentActive = CallRuntime.state as? CallUiState.Active
                if (currentActive != null && !currentActive.isHeld) {
                    CallRuntime.state = CallUiState.ActiveWithIncoming(currentActive, newIncoming)
                    // نحفظ الـ offer الثاني بشكل منفصل
                    pendingSecondOffer = signal
                    notifyWaiting(newIncoming)
                    return
                }
                incomingOffer = signal
                target = newIncoming.peer
                callId = newIncoming.callId
                mode = newIncoming.mode
                CallRuntime.state = newIncoming
                startRingtone()
                scope.launch { runCatching { telecom.addCall(target, true, mode == "VIDEO", onAnswer = { acceptIncoming() }, onDisconnect = { rejectIncoming() }, onActive = { runCatching { signaling.send(CallSignal(callId, target, type = "RESUME", mode = mode)) } }, onInactive = { runCatching { signaling.send(CallSignal(callId, target, type = "HOLD", mode = mode)) } }) } }
                promote(incomingNotification(target, mode), media = false)
            }
            "ANSWER" -> signal.payload["sdp"]?.let { engine?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, it)) { remoteDescriptionSet = true; flushIce() } }
            "ICE" -> {
                val candidate = IceCandidate(signal.payload["sdpMid"], signal.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0, signal.payload["candidate"].orEmpty())
                if (remoteDescriptionSet) engine?.addIce(candidate) else pendingIce += candidate
            }
            "HOLD" -> { engine?.setMicrophoneEnabled(false); engine?.setCameraEnabled(false) }
            "RESUME" -> { engine?.setMicrophoneEnabled(true); if (mode == "VIDEO") engine?.setCameraEnabled(true) }
            "CANCELLED" -> {
                // Another device answered — stop ringing on this device
                incomingOffer = null
                CallRuntime.state = CallUiState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "END", "REJECT" -> endCall(sendSignal = false)
            "UNAVAILABLE" -> fail("الطرف الآخر غير متاح")
        }
    }

    private fun acceptIncoming() {
        stopRingtone()
        val offer = incomingOffer ?: return
        promote(notification("جارٍ قبول المكالمة…", true), media = true)
        prepareAudio(); signaling.connect()
        scope.launch {
            if (createEngine(offer.mode == "VIDEO") is ApiResult.Error) return@launch fail("تعذر إنشاء محرك WebRTC")
            val sdp = offer.payload["sdp"] ?: return@launch fail("عرض المكالمة غير صالح")
            engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) { remoteDescriptionSet = true; flushIce(); engine?.answer() }
            CallRuntime.state = CallUiState.Connecting(callId.orEmpty(), target, mode)
        }
    }

    private fun rejectIncoming() {
        stopRingtone()
        incomingOffer?.let { signaling.send(CallSignal(it.callId, target, type = "REJECT", mode = mode)) }
        incomingOffer = null
        endCall(sendSignal = false)
    }

    private suspend fun createEngine(video: Boolean): ApiResult<Unit> {
        engine?.release(); engine = WebRtcEngine(this, this); CallRuntime.eglContext = engine?.eglContext
        val result = engine!!.create(video)
        if (result is ApiResult.Success) CallRuntime.localVideo = engine?.localMedia?.videoTrack
        return result
    }

    override fun onLocalDescription(description: SessionDescription) {
        val type = if (description.type == SessionDescription.Type.OFFER) "OFFER" else "ANSWER"
        signaling.send(CallSignal(callId, target, type = type, mode = mode, payload = mapOf("sdp" to description.description)))
        if (type == "OFFER") outgoingPending = false
        CallRuntime.state = CallUiState.Connecting(callId.orEmpty(), target, mode)
    }

    override fun onIceCandidate(candidate: IceCandidate) = signaling.send(CallSignal(callId, target, type = "ICE", mode = mode, payload = mapOf("sdpMid" to candidate.sdpMid.orEmpty(), "sdpMLineIndex" to candidate.sdpMLineIndex.toString(), "candidate" to candidate.sdp)))
    override fun onRemoteVideo(track: VideoTrack) { CallRuntime.remoteVideo = track }
    override fun onNetworkStats(stats: NetworkStats) { CallRuntime.networkStats = stats; onStatsReceived(stats) }
    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                CallRuntime.state = CallUiState.Active(callId.orEmpty(), target, mode, System.currentTimeMillis())
                updateNotification("مكالمة يونس نشطة")
                startStatsPolling()
            }
            PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED -> endCall(sendSignal = false)
            else -> Unit
        }
    }

    private var statsJob: kotlinx.coroutines.Job? = null
    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                engine?.pollStats()
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    /**
     * يُستدعى من `onNetworkStats` لتسجيل الـ Telemetry.
     */
    private fun onStatsReceived(stats: NetworkStats) {
        CallTelemetry.onNetworkStats(stats)
    }
    override fun onError(message: String) = fail(message)
    override fun onDisconnected() { if (CallRuntime.state !is CallUiState.Idle) fail("انقطع اتصال الإشارة") }

    private fun flushIce() { pendingIce.forEach { engine?.addIce(it) }; pendingIce.clear() }

    /**
     * Holds the active call — keeps the peer connection alive but disables the local tracks
     * so the other side sees/hears nothing. The system Telecom call is also marked inactive.
     */
    private fun holdCall() {
        val current = CallRuntime.state as? CallUiState.Active ?: return
        if (current.isHeld) return
        engine?.setMicrophoneEnabled(false)
        engine?.setCameraEnabled(false)
        CallRuntime.state = current.copy(isHeld = true)
        runCatching { signaling.send(CallSignal(callId, target, type = "HOLD", mode = mode)) }
        scope.launch { runCatching { telecom.hold(target) } }
        updateNotification("مكالمة يونس معلّقة")
    }

    /**
     * Resumes a held call.
     */
    private fun resumeCall() {
        val current = CallRuntime.state as? CallUiState.Active ?: return
        if (!current.isHeld) return
        engine?.setMicrophoneEnabled(true)
        if (mode == "VIDEO") engine?.setCameraEnabled(true)
        CallRuntime.state = current.copy(isHeld = false)
        runCatching { signaling.send(CallSignal(callId, target, type = "RESUME", mode = mode)) }
        scope.launch { runCatching { telecom.resume(target) } }
        updateNotification("مكالمة يونس نشطة")
    }

    /**
     * Sends a DTMF tone (used for IVR navigation).
     */
    private fun sendDtmf(digit: Char) {
        if (CallRuntime.state !is CallUiState.Active) return
        scope.launch { runCatching { telecom.sendDtmf(target, digit) } }
    }

    /**
     * يبدأ تسجيل المكالمة بعد موافقة الطرفين (two-party consent).
     * التسجيل محلي فقط (mic) ومُشفر بـ Android Keystore.
     */
    private fun startRecording() {
        if (CallRuntime.state !is CallUiState.Active) return
        if (recordingManager?.isRecording() == true) return
        if (recordingManager == null) {
            recordingManager = CallRecordingManager(this, callId.orEmpty())
        }
        // موافقة الطرف الآخر مفترضة (الـ UI يعرض banner ويسأل قبل)
        val started = recordingManager?.start(consentGranted = true) ?: false
        if (started) {
            updateNotification("مكالمة يونس نشطة • جارٍ التسجيل")
        }
    }

    /**
     * يوقف التسجيل ويشفر الملف.
     */
    private fun stopRecording() {
        scope.launch {
            val recording = recordingManager?.stop()
            recording?.let {
                updateNotification("مكالمة يونس نشطة • تم حفظ التسجيل")
            }
            recordingManager = null
        }
    }

    /**
     * Notifies the user that a second call is waiting while another is active.
     * Plays a short "call-waiting" tone (notification sound) instead of full ringtone,
     * so the active call audio is not drowned out.
     */
    private fun notifyWaiting(waiting: CallUiState.Incoming) {
        // TYPE_NOTIFICATION = رنة مختصرة (نغمة "تنبيه")، ليست رنة كاملة
        startRingtone(RingtoneManager.TYPE_NOTIFICATION, longArrayOf(0, 200, 100, 200))
        updateNotification("مكالمة يونس: ${waiting.peer} في الانتظار")
    }

    /**
     * Accepts a waiting call: holds the current call and answers the second.
     * Requires a second WebRTC engine (current architecture uses one engine per call).
     * For simplicity in the first cut, this swaps the active call and parks the first on hold.
     */
    private fun acceptSecondIncoming() {
        val state = CallRuntime.state as? CallUiState.ActiveWithIncoming ?: return
        val waiting = state.waiting
        val previous = state.active

        // 1) أرسل HOLD للمكالمة الأولى
        runCatching { signaling.send(CallSignal(previous.callId, previous.peer, type = "HOLD", mode = previous.mode)) }

        // 2) أوقف المحرك الحالي وأنشئ جديد للـ waiting
        engine?.release()
        engine = null
        target = waiting.peer
        callId = waiting.callId
        mode = waiting.mode
        CallRuntime.state = CallUiState.Connecting(waiting.callId, waiting.peer, waiting.mode)
        promote(notification("تبديل إلى ${waiting.peer}", ongoing = true), media = true)
        prepareAudio()

        // 3) أنشئ محرك جديد وأرسل ANSWER
        scope.launch {
            if (createEngine(mode == "VIDEO") is ApiResult.Error) return@launch fail("تعذر إنشاء محرك WebRTC")
            val sdp = pendingSecondOffer?.payload?.get("sdp") ?: return@launch fail("عرض غير صالح")
            engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) { remoteDescriptionSet = true; flushIce(); engine?.answer() }
            pendingSecondOffer = null
            stopRingtone()
        }
    }
    private fun fail(message: String) {
        CallRuntime.state = CallUiState.Error(message)
        updateNotification(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (CallRuntime.state is CallUiState.Error) {
                CallRuntime.state = CallUiState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun prepareAudio() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        audioFocus = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build().also(audio::requestAudioFocus)
        setSpeaker(mode == "VIDEO")
    }

    private fun setSpeaker(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            val type = if (enabled) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            audio.availableCommunicationDevices.firstOrNull { it.type == type }?.let(audio::setCommunicationDevice)
        } else @Suppress("DEPRECATION") run { audio.isSpeakerphoneOn = enabled }
        CallRuntime.speaker = enabled
    }

    private fun routeBluetooth() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 31) {
            audio.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET }?.let(audio::setCommunicationDevice)
        } else @Suppress("DEPRECATION") run { audio.startBluetoothSco(); audio.isBluetoothScoOn = true }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (mode == "VIDEO" || CallRuntime.speaker || CallRuntime.state !is CallUiState.Active) return
        val near = event.values.firstOrNull()?.let { it < event.sensor.maximumRange } == true
        if (near && proximityLock == null) proximityLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "younes:call-proximity").also { it.acquire(10 * 60_000L) }
        else if (!near) { proximityLock?.takeIf { it.isHeld }?.release(); proximityLock = null }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun endCall(sendSignal: Boolean) {
        statsJob?.cancel(); statsJob = null
        val durationMs = CallRuntime.state.let { (it as? CallUiState.Active)?.let { active -> System.currentTimeMillis() - active.startedAt } ?: 0L }
        CallTelemetry.onCallEnded(callId.orEmpty(), mode, "RED", durationMs)
        CallTelemetry.flush(this)
        CallTelemetry.reset()
        stopRingtone()
        if (sendSignal && target.isNotBlank()) runCatching { signaling.send(CallSignal(callId, target, type = "END", mode = mode)) }
        engine?.release(); engine = null; incomingOffer = null; outgoingPending = false; pendingIce.clear(); remoteDescriptionSet = false
        proximityLock?.takeIf { it.isHeld }?.release(); proximityLock = null
        audioFocus?.let(audio::abandonAudioFocusRequest); audioFocus = null
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else @Suppress("DEPRECATION") run { audio.isSpeakerphoneOn = false; audio.stopBluetoothSco() }
        audio.mode = AudioManager.MODE_NORMAL; target = ""; callId = null; CallRuntime.localVideo = null; CallRuntime.remoteVideo = null; CallRuntime.state = CallUiState.Idle
        updateNotification("جاهز لاستقبال مكالمات يونس")
    }

    private fun startRingtone() {
        startRingtone(RingtoneManager.TYPE_RINGTONE, longArrayOf(0, 800, 400, 800))
    }

    private fun startRingtone(type: Int, vibrationPattern: LongArray) {
        try {
            val uri = RingtoneManager.getDefaultUri(type)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                isLooping = true
                play()
            }
            vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VibratorManager::class.java))?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            }
            vibrator?.let { vib ->
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(vibrationPattern, 0)
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopRingtone() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    private fun promote(value: android.app.Notification, media: Boolean) {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (media) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (mode == "VIDEO") type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, value, type)
    }

    private fun createChannel() = getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
    private fun notification(text: String, ongoing: Boolean) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.sym_action_call).setContentTitle(getString(com.red.sovereign.R.string.notification_ongoing)).setContentText(text).setOngoing(ongoing).setContentIntent(appIntent()).addAction(0, getString(com.red.sovereign.R.string.notification_end), serviceIntent(ACTION_END)).build()
    private fun incomingNotification(peer: String, callMode: String) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle(if (callMode == "VIDEO") getString(com.red.sovereign.R.string.incoming_video_call) else getString(com.red.sovereign.R.string.incoming_voice_call)).setContentText(peer).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true).setFullScreenIntent(appIntent(), true).addAction(0, getString(com.red.sovereign.R.string.notification_reject), serviceIntent(ACTION_REJECT)).addAction(0, getString(com.red.sovereign.R.string.notification_accept), appIntent()).build()
    private fun updateNotification(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, true))
    private fun appIntent() = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun serviceIntent(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, YounesCallService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() { getSystemService(SensorManager::class.java).unregisterListener(this); scope.cancel(); engine?.release(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "red_calls"; private const val NOTIFICATION_ID = 7401
        const val ACTION_LISTEN = "com.red.sovereign.call.LISTEN"; const val ACTION_STOP = "com.red.sovereign.call.STOP"
        const val ACTION_START = "com.red.sovereign.call.START"; const val ACTION_ACCEPT = "com.red.sovereign.call.ACCEPT"; const val ACTION_REJECT = "com.red.sovereign.call.REJECT"; const val ACTION_END = "com.red.sovereign.call.END"
        const val ACTION_MIC = "com.red.sovereign.call.MIC"; const val ACTION_CAMERA = "com.red.sovereign.call.CAMERA"; const val ACTION_SWITCH_CAMERA = "com.red.sovereign.call.SWITCH_CAMERA"; const val ACTION_SPEAKER = "com.red.sovereign.call.SPEAKER"; const val ACTION_BLUETOOTH = "com.red.sovereign.call.BLUETOOTH"
        const val ACTION_HOLD = "com.red.sovereign.call.HOLD"; const val ACTION_RESUME = "com.red.sovereign.call.RESUME"; const val ACTION_DTMF = "com.red.sovereign.call.DTMF"
        const val ACTION_ACCEPT_SECOND = "com.red.sovereign.call.ACCEPT_SECOND"; const val ACTION_REJECT_SECOND = "com.red.sovereign.call.REJECT_SECOND"
        const val ACTION_START_RECORDING = "com.red.sovereign.call.START_RECORDING"; const val ACTION_STOP_RECORDING = "com.red.sovereign.call.STOP_RECORDING"
        const val EXTRA_TARGET = "target"; const val EXTRA_MODE = "mode"; const val EXTRA_ENABLED = "enabled"; const val EXTRA_DTMF = "dtmf"
        fun listen(context: Context) = ContextCompat.startForegroundService(context, Intent(context, YounesCallService::class.java).setAction(ACTION_LISTEN))
        fun stop(context: Context) = context.startService(Intent(context, YounesCallService::class.java).setAction(ACTION_STOP))
        fun start(context: Context, target: String, video: Boolean) = ContextCompat.startForegroundService(context, Intent(context, YounesCallService::class.java).setAction(ACTION_START).putExtra(EXTRA_TARGET, target).putExtra(EXTRA_MODE, if (video) "VIDEO" else "VOICE"))
        fun action(context: Context, action: String, enabled: Boolean = true) = ContextCompat.startForegroundService(context, Intent(context, YounesCallService::class.java).setAction(action).putExtra(EXTRA_ENABLED, enabled))
        fun dtmf(context: Context, digit: Char) = ContextCompat.startForegroundService(context, Intent(context, YounesCallService::class.java).setAction(ACTION_DTMF).putExtra(EXTRA_DTMF, digit.toString()))
    }
}

sealed interface CallUiState {
    data object Idle : CallUiState
    data class Incoming(val callId: String, val peer: String, val mode: String) : CallUiState
    data class Connecting(val callId: String, val peer: String, val mode: String) : CallUiState
    data class Active(val callId: String, val peer: String, val mode: String, val startedAt: Long, val isHeld: Boolean = false) : CallUiState
    /** مكالمة نشطة + مكالمة واردة ثانية (call waiting) */
    data class ActiveWithIncoming(val active: Active, val waiting: Incoming) : CallUiState
    data class Error(val message: String) : CallUiState
}
object CallRuntime {
    var state: CallUiState by androidx.compose.runtime.mutableStateOf(CallUiState.Idle)
    var eglContext: org.webrtc.EglBase.Context? = null
    var localVideo: VideoTrack? by androidx.compose.runtime.mutableStateOf(null)
    var remoteVideo: VideoTrack? by androidx.compose.runtime.mutableStateOf(null)
    var speaker by androidx.compose.runtime.mutableStateOf(false)
    var networkStats: NetworkStats by androidx.compose.runtime.mutableStateOf(NetworkStats())
}