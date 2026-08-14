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
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
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
    private val pendingIce = java.util.concurrent.CopyOnWriteArrayList<IceCandidate>()
    @Volatile
    private var remoteDescriptionSet = false
    private var proximityLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var reconnect: CallReconnectManager? = null
    private var vibrator: Vibrator? = null
    private var audioFocus: AudioFocusRequest? = null
    private var recordingManager: CallRecordingManager? = null
    private var recordingConsentShown: Boolean = false
    private var ringTimeoutJob: kotlinx.coroutines.Job? = null
    private var ringback: ToneGenerator? = null
    private var ringStartedAt: Long = 0L

    override fun onCreate() {
        super.onCreate(); createChannel()
        audio = getSystemService(AudioManager::class.java)
        telecom = TelecomBridge(this).also { runCatching(it::register) }
        signaling = CallSignalingClient(this, TokenStore(this), this)
        val sensors = getSystemService(SensorManager::class.java)
        sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // إعادة اتصال تلقائية عند انقطاع الإشارة (بدل إنهاء المكالمة)
        reconnect = CallReconnectManager(
            scope = scope,
            onReconnect = {
                runCatching { signaling.reconnect() }
            },
            onFailure = { fail("انقطع اتصال الإشارة") }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LISTEN -> { promote(notification("جاهز لاستقبال مكالمات يونس", ongoing = true), media = false); signaling.connect() }
            ACTION_START -> {
                outgoingPending = true
                target = intent.getStringExtra(EXTRA_TARGET).orEmpty(); mode = intent.getStringExtra(EXTRA_MODE) ?: "VOICE"
                // لا تقصف الخدمة إذا وصل intent بلا هدف (PendingIntent قديم/إشعار) — أظهر حالة خطأ بأمان
                if (target.isBlank()) {
                    CallRuntime.state = CallUiState.Error("معرّف المكالمة غير صالح")
                    updateNotification("تعذر بدء المكالمة: المعرّف غير صالح")
                    return START_STICKY
                }
                callId = UUID.randomUUID().toString()
                // ضبط الحالة فوراً ليظهر الـ overlay والتبويب الصحيح بلا تأخير
                CallRuntime.state = CallUiState.Connecting(callId ?: UUID.randomUUID().toString(), target, mode)
                scope.launch { runCatching { telecom.addCall(target, false, mode == "VIDEO", onAnswer = {}, onDisconnect = { endCall(true) }, onActive = { runCatching { signaling.send(CallSignal(callId, target, type = "RESUME", mode = mode)) } }, onInactive = { runCatching { signaling.send(CallSignal(callId, target, type = "HOLD", mode = mode)) } }) } }
                promote(notification("جارٍ بدء المكالمة…", ongoing = true), media = true); prepareAudio(); startRingback(); armRingTimeout(outgoing = true); signaling.connect()
            }
            ACTION_ACCEPT -> acceptIncoming(
                cameraOn = intent.getBooleanExtra(EXTRA_CAMERA, true),
                micOn = intent.getBooleanExtra(EXTRA_ENABLED, true)
            )
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
            ACTION_START_RECORDING -> startRecording(consentGranted = intent.getBooleanExtra(EXTRA_CONSENT, false))
            ACTION_STOP_RECORDING -> stopRecording()
            // PSTN interop: silence/hold/resume RED call from PhoneStateReceiver
            ACTION_SILENCE_RINGER -> stopRingtone()
            ACTION_HOLD_ACTIVE -> holdCall()
            ACTION_RESUME_RINGER -> {
                // أعد رنة RED إن كانت مكالمة RED واردة عند انتهاء PSTN
                if (CallRuntime.state is CallUiState.Incoming) startRingtone()
            }
            ACTION_STOP -> { signaling.close(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_STICKY
    }

    override fun onConnected() {
        reconnect?.stop()
        if (incomingOffer != null || !outgoingPending) {
            // عند نجاح إعادة الاتصال بالشبكة والإشارة أثناء مكالمة قائمة، نقوم بإجراء ICE restart
            // لإعادة استكشاف المسار التلقائي وتمرير الحزم عبر الشبكة الجديدة دون انقطاع الصوت أو الفيديو
            if (CallRuntime.state is CallUiState.Active || CallRuntime.state is CallUiState.ActiveWithIncoming) {
                engine?.restartIce()
            }
            return
        }
        scope.launch {
            val created = createEngine(mode == "VIDEO")
            if (created is ApiResult.Success) engine?.offer() else fail("تعذر إنشاء محرك WebRTC")
        }
    }

    override fun onSignal(signal: CallSignal) {
        // إشارات المكالمة الجماعية تُوجّه إلى GroupCallService عبر مقبسه الخاص —
        // يتجاهلها مستمع المكالمات الفردية حتى لا يعتبرها مكالمة واردة 1:1.
        if (signal.groupCallId != null && signal.type != "GROUP_CALL_INVITE") return
        when (signal.type) {
            "OFFER", "RENEGOTIATE" -> {
                if (isRenegotiation(signal)) {
                    applyRemoteOffer(signal)
                    return
                }
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
                if (com.red.sovereign.settings.SettingsRuntime.current.callNotifications) startRingtone()
                armRingTimeout(outgoing = false)
                scope.launch { runCatching { telecom.addCall(target, true, mode == "VIDEO", onAnswer = { acceptIncoming() }, onDisconnect = { rejectIncoming() }, onActive = { runCatching { signaling.send(CallSignal(callId, target, type = "RESUME", mode = mode)) } }, onInactive = { runCatching { signaling.send(CallSignal(callId, target, type = "HOLD", mode = mode)) } }) } }
                promote(
                    if (com.red.sovereign.settings.SettingsRuntime.current.callNotifications) incomingNotification(target, mode)
                    else incomingNotification(target, mode).let { builder ->
                        NotificationCompat.Builder(this, "red_calls_incoming")
                            .setSmallIcon(android.R.drawable.sym_action_call)
                            .setContentTitle(getString(com.red.sovereign.R.string.incoming_voice_call))
                            .setContentText(target)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setSilent(true)
                            .setOngoing(true)
                            .setContentIntent(appIntent())
                            .build()
                    },
                    media = false
                )
            }
            "ANSWER" -> signal.payload["sdp"]?.let { engine?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, it)) { remoteDescriptionSet = true; flushIce() } }
            "ICE" -> {
                val candidate = IceCandidate(signal.payload["sdpMid"], signal.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0, signal.payload["candidate"].orEmpty())
                if (remoteDescriptionSet) engine?.addIce(candidate) else pendingIce += candidate
            }
            "HOLD" -> { engine?.setMicrophoneEnabled(false); engine?.setCameraEnabled(false) }
            "RESUME" -> { engine?.setMicrophoneEnabled(true); if (mode == "VIDEO") engine?.setCameraEnabled(true) }
            "CANCELLED" -> {
                // Another device on this account answered or caller cancelled.
                incomingOffer = null
                clearRingTimeout()
                stopRingtone()
                if (CallRuntime.state is CallUiState.Incoming) {
                    val missedPeer = (CallRuntime.state as CallUiState.Incoming).peer
                    CallRuntime.state = CallUiState.CallEnded(missedPeer, mode, 0L, callId.orEmpty())
                    updateNotification("مكالمة فائتة من $missedPeer")
                    scheduleCleanupAndReset()
                }
            }
            "END" -> handleCallEnded()
            "REJECT" -> handleDeclined()
            "BUSY" -> handleBusy()
            "UNAVAILABLE" -> handleUnavailable()
            "GROUP_CALL_INVITE" -> {
                // دعوة مكالمة جماعية (iMO/Zoom): اعرض الإشعار الوارد — سيقبل/يرفض عبر GroupCallService
                val gId = signal.groupCallId ?: signal.callId.orEmpty()
                val myId = TokenStore(this).redId.orEmpty()
                GroupCallService.notifyIncoming(
                    this,
                    gId,
                    myId,
                    signal.sourceUserId.orEmpty(),
                    signal.payload["hostName"] ?: signal.sourceUserId.orEmpty(),
                    signal.mode == "VIDEO",
                    signal.inviteeIds.filter { it != myId }
                )
            }
            "GROUP_CALL_END" -> {
                val gId = signal.groupCallId ?: signal.callId.orEmpty()
                val current = GroupCallRuntime.state
                if (current is GroupCallUiState.IncomingGroup && current.groupCallId == gId) {
                    GroupCallRuntime.state = GroupCallUiState.Ended
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
            "CONFERENCE_INVITE" -> {
                val myId = TokenStore(this).redId.orEmpty()
                val video = signal.mode != "SPACE"
                ConferenceService.invite(
                    this,
                    signal.callId.orEmpty(),
                    myId,
                    signal.payload["inviter"] ?: signal.sourceUserId.orEmpty(),
                    video
                )
            }
            "LIVE_INVITE" -> {
                val myId = TokenStore(this).redId.orEmpty()
                LiveStreamService.invite(
                    this,
                    signal.callId.orEmpty(),
                    myId,
                    signal.payload["inviter"] ?: signal.sourceUserId.orEmpty()
                )
            }
        }
    }

    private fun acceptIncoming(cameraOn: Boolean = true, micOn: Boolean = true) {
        clearRingTimeout()
        stopRingtone()
        val offer = incomingOffer ?: return
        // فحص الأذونات قبل القبول — إن لم يُمنح الميكروفون لا نقبل بصمت (يسبب فشل WebRTC لاحقاً)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("امنح إذن الميكروفون من إعدادات التطبيق لاستقبال المكالمات")
            return
        }
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        promote(notification("جارٍ قبول المكالمة…", true), media = true)
        prepareAudio(); signaling.connect()
        scope.launch {
            if (createEngine(offer.mode == "VIDEO") is ApiResult.Error) return@launch fail("تعذر إنشاء محرك WebRTC")
            engine?.setMicrophoneEnabled(micOn)
            if (offer.mode == "VIDEO") engine?.setCameraEnabled(cameraOn && cameraGranted)
            val sdp = offer.payload["sdp"] ?: return@launch fail("عرض المكالمة غير صالح")
            engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) { remoteDescriptionSet = true; flushIce(); engine?.answer() }
            CallRuntime.state = CallUiState.Connecting(callId.orEmpty(), target, mode)
        }
    }

    private fun rejectIncoming() {
        clearRingTimeout()
        stopRingtone()
        incomingOffer?.let { signaling.send(CallSignal(it.callId, target, type = "REJECT", mode = mode)) }
        // لا نمسح incomingOffer هنا — endCallCore يحتاجه لتحديد اتجاه السجل (INCOMING)
        endCall(sendSignal = false)
    }

    private suspend fun createEngine(video: Boolean): ApiResult<Unit> {
        engine?.release(); engine = WebRtcEngine(this, this); CallRuntime.eglContext = engine?.eglContext
        val kind = if (video) CallMediaKind.VIDEO else CallMediaKind.VOICE
        val result = engine!!.create(kind)
        if (result is ApiResult.Success) CallRuntime.localVideo = engine?.localMedia?.videoTrack
        return result
    }

    override fun onLocalDescription(description: SessionDescription) {
        val type = when {
            description.type == SessionDescription.Type.ANSWER -> "ANSWER"
            isActiveCall() -> "RENEGOTIATE"
            else -> "OFFER"
        }
        signaling.send(CallSignal(callId, target, type = type, mode = mode, payload = mapOf("sdp" to description.description)))
        if (type == "OFFER") outgoingPending = false
        if (!isActiveCall()) {
            CallRuntime.state = CallUiState.Connecting(callId.orEmpty(), target, mode)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) = signaling.send(CallSignal(callId, target, type = "ICE", mode = mode, payload = mapOf("sdpMid" to candidate.sdpMid.orEmpty(), "sdpMLineIndex" to candidate.sdpMLineIndex.toString(), "candidate" to candidate.sdp)))
    override fun onRemoteVideo(track: VideoTrack) { CallRuntime.remoteVideo = track }
    override fun onNetworkStats(stats: NetworkStats) { CallRuntime.networkStats = stats; onStatsReceived(stats) }
    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                clearRingTimeout()
                stopRingback()
                stopRingtone()
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
        engine?.adjustQuality(stats)
        CallTelemetry.onNetworkStats(stats)
    }
    override fun onError(message: String) {
        if (isActiveCall()) {
            updateNotification("انقطع اتصال الإشارة — جارٍ إعادة الاتصال…")
            reconnect?.start()
            return
        }
        if (CallRuntime.state is CallUiState.Incoming) return
        fail(message)
    }
    override fun onDisconnected() {
        // مكالمة نشطة → إعادة اتصال بدل إنهاء المكالمة فوراً
        if (CallRuntime.state !is CallUiState.Idle) {
            updateNotification("انقطع الاتصال — جارٍ إعادة الاتصال…")
            reconnect?.start()
        }
    }

    private fun flushIce() { pendingIce.forEach { engine?.addIce(it) }; pendingIce.clear() }

    private fun isActiveCall(): Boolean =
        CallRuntime.state is CallUiState.Active || CallRuntime.state is CallUiState.ActiveWithIncoming

    private fun isRenegotiation(signal: CallSignal): Boolean {
        if (signal.type == "RENEGOTIATE") return engine != null
        val incomingId = signal.callId.orEmpty()
        return incomingId.isNotBlank() && incomingId == callId && (isActiveCall() || CallRuntime.state is CallUiState.Connecting)
    }

    private fun applyRemoteOffer(signal: CallSignal) {
        val sdp = signal.payload["sdp"] ?: return
        engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) {
            remoteDescriptionSet = true
            flushIce()
            engine?.answer()
        }
    }

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
     * يُولَّد النغم فعلياً على قناة المكالمة (In-band) عبر ToneGenerator،
     * ويُحاول أيضاً تمريره لنظام Telecom إن دعمه الجهاز.
     */
    private fun sendDtmf(digit: Char) {
        if (CallRuntime.state !is CallUiState.Active) return
        scope.launch { runCatching { telecom.sendDtmf(target, digit) } }
        // توليد نغمة DTMF حقيقية محلياً — مدة قياسية 100-120ms
        runCatching {
            val tone = when (digit) {
                '1' -> ToneGenerator.TONE_DTMF_1; '2' -> ToneGenerator.TONE_DTMF_2; '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4; '5' -> ToneGenerator.TONE_DTMF_5; '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7; '8' -> ToneGenerator.TONE_DTMF_8; '9' -> ToneGenerator.TONE_DTMF_9
                '0' -> ToneGenerator.TONE_DTMF_0
                '*' -> ToneGenerator.TONE_DTMF_S; '#' -> ToneGenerator.TONE_DTMF_P
                else -> return@runCatching
            }
            val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
            tg.startTone(tone, 120)
            scope.launch { kotlinx.coroutines.delay(160); runCatching { tg.release() } }
        }
    }

    /**
     * يبدأ تسجيل المكالمة بعد موافقة الطرفين (two-party consent).
     * التسجيل محلي فقط (mic) ومُشفر بـ Android Keystore.
     * consentGranted تُمرَّر من واجهة المستخدم بعد حوار تأكيد صريح — لا تُفترض أبداً.
     */
    private fun startRecording(consentGranted: Boolean = false) {
        if (CallRuntime.state !is CallUiState.Active) return
        if (recordingManager?.isRecording() == true) return
        if (!consentGranted) {
            recordingConsentShown = true
            updateNotification("مكالمة يونس نشطة • انتظر موافقة التسجيل")
            return
        }
        if (recordingManager == null) {
            recordingManager = CallRecordingManager(this, callId.orEmpty())
        }
        val started = recordingManager?.start(consentGranted = true) ?: false
        if (started) {
            CallRuntime.isRecording = true
            updateNotification("مكالمة يونس نشطة • جارٍ التسجيل")
        }
    }

    /**
     * يوقف التسجيل ويشفر الملف.
     */
    private fun stopRecording() {
        scope.launch {
            val recording = recordingManager?.stop()
            CallRuntime.isRecording = false
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
        clearRingTimeout()
        stopRingback()
        stopRingtone()
        CallRuntime.state = CallUiState.Error(message)
        updateNotification(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (CallRuntime.state is CallUiState.Error) {
                endCall(sendSignal = false)
                promote(notification("جاهز لاستقبال مكالمات يونس", ongoing = true), media = false)
                runCatching { signaling.connect() }
            }
        }
    }

    private fun prepareAudio() {
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build().also(audio::requestAudioFocus)
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

    private fun armRingTimeout(outgoing: Boolean) {
        clearRingTimeout()
        ringStartedAt = System.currentTimeMillis()
        ringTimeoutJob = scope.launch {
            kotlinx.coroutines.delay(CallRingPolicy.UNANSWERED_TIMEOUT_MS)
            if (!CallRingPolicy.isOneToOneRingState(CallRuntime.state)) return@launch
            if (!CallRingPolicy.shouldExpireUnanswered(System.currentTimeMillis() - ringStartedAt, true)) return@launch
            if (outgoing) {
                if (target.isNotBlank()) runCatching { signaling.send(CallSignal(callId, target, type = "END", mode = mode)) }
                handleNoAnswer()
            } else {
                rejectIncoming()
            }
        }
    }

    private fun handleBusy() {
        if (CallRuntime.state !is CallUiState.Connecting) return
        clearRingTimeout()
        stopRingback()
        startSpecialTone(ToneGenerator.TONE_SUP_BUSY, 2000)
        val busyPeer = target
        CallRuntime.state = CallUiState.Busy(busyPeer)
        updateNotification("الطرف الآخر مشغول")
        scheduleCleanupAndReset()
    }

    private fun handleDeclined() {
        if (CallRuntime.state !is CallUiState.Connecting && CallRuntime.state !is CallUiState.Active) return
        clearRingTimeout()
        stopRingback()
        startSpecialTone(ToneGenerator.TONE_SUP_BUSY, 1500)
        val declinedPeer = target
        CallRuntime.state = CallUiState.Declined(declinedPeer)
        updateNotification("تم رفض المكالمة")
        scheduleCleanupAndReset()
    }

    private fun handleNoAnswer() {
        if (CallRuntime.state !is CallUiState.Connecting) return
        clearRingTimeout()
        stopRingback()
        val noAnswerPeer = target
        CallRuntime.state = CallUiState.NoAnswer(noAnswerPeer)
        updateNotification("لا يوجد رد")
        scheduleCleanupAndReset()
    }

    private fun handleUnavailable() {
        clearRingTimeout()
        stopRingback()
        startSpecialTone(ToneGenerator.TONE_SUP_CONGESTION, 2000)
        CallRuntime.state = CallUiState.Error("الطرف الآخر غير متاح أو لا يوجد اتصال")
        updateNotification("الطرف الآخر غير متاح")
        scheduleCleanupAndReset()
    }

    private fun handleCallEnded() {
        val durationMs = (CallRuntime.state as? CallUiState.Active)?.let { System.currentTimeMillis() - it.startedAt } ?: 0L
        val endedPeer = target
        val endedCallId = callId.orEmpty()
        val endedMode = mode
        endCallCore(sendSignal = false)
        CallRuntime.state = CallUiState.CallEnded(endedPeer, endedMode, durationMs, endedCallId)
        updateNotification("انتهت المكالمة")
        scheduleCleanupAndReset(4000) // Keep the CallEnded screen for 4 seconds
    }

    private fun startSpecialTone(toneType: Int, durationMs: Int) {
        runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also {
                it.startTone(toneType, durationMs)
                scope.launch { kotlinx.coroutines.delay(durationMs.toLong() + 200); it.release() }
            }
        }
    }

    private fun scheduleCleanupAndReset(delayMs: Long = 3000) {
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            endCallCore(sendSignal = false)
            CallRuntime.state = CallUiState.Idle
            updateNotification("جاهز لاستقبال مكالمات يونس")
            runCatching { signaling.connect() }
        }
    }

    private fun endCallCore(sendSignal: Boolean) {
        statsJob?.cancel(); statsJob = null
        clearRingTimeout()
        stopRingback()
        val endedPeer = target
        val endedCallId = callId
        val endedMode = mode
        val durationMs = CallRuntime.state.let { (it as? CallUiState.Active)?.let { active -> System.currentTimeMillis() - active.startedAt } ?: 0L }
        val startedAt = (CallRuntime.state as? CallUiState.Active)?.startedAt
            ?: ringStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis() - durationMs
        if (durationMs > 0 && endedCallId != null) CallTelemetry.onCallEnded(endedCallId, endedMode, "RED", durationMs)
        CallTelemetry.flush(this)
        CallTelemetry.reset()
        stopRingtone()
        // أغلق مكالمة النظام (CallsManager) — كان هذا مفقوداً ويترك مكالمات وهمية عالقة في شاشة النظام
        if (endedPeer.isNotBlank()) scope.launch { runCatching { telecom.disconnect(endedPeer) } }
        // سجّل المكالمة محلياً فوراً (offline-first) — كان السجل يعتمد على السيرفر فقط
        if (endedCallId != null) saveCallLogLocally(
            callId = endedCallId,
            peer = endedPeer,
            mode = endedMode,
            startedAt = startedAt,
            durationMs = durationMs,
            incoming = incomingOffer != null
        )
        if (sendSignal && target.isNotBlank()) runCatching { signaling.send(CallSignal(callId, target, type = "END", mode = mode)) }
        // أوقف تسجيل المكالمة إن كان يعمل — كان التسجيل يستمر في الخلفية بعد انتهاء المكالمة
        if (CallRuntime.isRecording || recordingManager?.isRecording() == true) {
            val manager = recordingManager
            recordingManager = null
            scope.launch { runCatching { manager?.stop() } }
        }
        engine?.release(); engine = null; incomingOffer = null; outgoingPending = false; pendingIce.clear(); remoteDescriptionSet = false
        proximityLock?.takeIf { it.isHeld }?.release(); proximityLock = null
        audioFocus?.let(audio::abandonAudioFocusRequest); audioFocus = null
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else @Suppress("DEPRECATION") run { audio.isSpeakerphoneOn = false; audio.stopBluetoothSco() }
        audio.mode = AudioManager.MODE_NORMAL; target = ""; callId = null; CallRuntime.localVideo = null; CallRuntime.remoteVideo = null; CallRuntime.isRecording = false
    }

    /**
     * يكتب سجل المكالمة في قاعدة التطبيق المحلية (مشفّر) فور انتهاء المكالمة،
     * ليبقى السجل متاحاً حتى بدون اتصال بالخادم. البيانات تتزامن لاحقاً مع السيرفر.
     */
    private fun saveCallLogLocally(callId: String, peer: String, mode: String, startedAt: Long, durationMs: Long, incoming: Boolean) {
        scope.launch {
            runCatching {
                // الحالة تُشتق من الحالة النهائية الفعلية للمكالمة
                val status = when {
                    durationMs > 0 -> "ENDED"
                    CallRuntime.state is CallUiState.Busy -> "FAILED"
                    CallRuntime.state is CallUiState.Declined || CallRuntime.state is CallUiState.Incoming -> "REJECTED"
                    CallRuntime.state is CallUiState.Error -> "FAILED"
                    else -> "MISSED"
                }
                val cipher = CallLogCipher()
                val log = com.red.sovereign.core.database.CallLogEntity(
                    id = callId,
                    peerId = cipher.encryptPeerId(peer),
                    peerLabel = cipher.encryptLabel(peer),
                    type = if (mode == "VIDEO") "VIDEO" else "VOICE",
                    direction = if (incoming) "INCOMING" else "OUTGOING",
                    route = "RED",
                    status = status,
                    timestamp = startedAt,
                    durationMs = durationMs,
                    answeredAt = if (durationMs > 0) startedAt else null,
                    endedAt = if (durationMs > 0) startedAt + durationMs else startedAt
                )
                com.red.sovereign.core.database.LocalRepository(this@YounesCallService).saveCallLog(log)
            }
        }
    }

    private fun clearRingTimeout() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
    }

    private fun startRingback() {
        stopRingback()
        runCatching {
            ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE, CallRingPolicy.UNANSWERED_TIMEOUT_MS.toInt())
            }
        }
    }

    private fun stopRingback() {
        runCatching { ringback?.stopTone(); ringback?.release() }
        ringback = null
    }

    private fun endCall(sendSignal: Boolean) {
        if (CallRuntime.state !is CallUiState.CallEnded && CallRuntime.state !is CallUiState.Idle) {
            handleCallEnded()
        } else {
            endCallCore(sendSignal)
            CallRuntime.state = CallUiState.Idle
            updateNotification("جاهز لاستقبال مكالمات يونس")
        }
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
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, value, type)
        } catch (e: SecurityException) {
            var fallbackType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (mode == "VIDEO") fallbackType = fallbackType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            ServiceCompat.startForeground(this, NOTIFICATION_ID, value, fallbackType)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
        // قناة المكالمات الواردة — أولوية قصوى مع رنين
        manager.createNotificationChannel(NotificationChannel("red_calls_incoming", getString(com.red.sovereign.R.string.channel_calls_incoming_name), NotificationManager.IMPORTANCE_MAX).apply {
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        })
    }
    private fun notification(text: String, ongoing: Boolean) = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.sym_action_call).setContentTitle(getString(com.red.sovereign.R.string.notification_ongoing)).setContentText(text).setOngoing(ongoing).setContentIntent(appIntent()).addAction(0, getString(com.red.sovereign.R.string.notification_end), serviceIntent(ACTION_END)).build()
    private fun incomingNotification(peer: String, callMode: String) =
        NotificationCompat.Builder(this, "red_calls_incoming")
            .setSmallIcon(if (callMode == "VIDEO") android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle(if (callMode == "VIDEO") getString(com.red.sovereign.R.string.incoming_video_call) else getString(com.red.sovereign.R.string.incoming_voice_call))
            .setContentText(peer)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00C98C.toInt())
            .setOnlyAlertOnce(false)
            .setOngoing(true)
            .setFullScreenIntent(appIntent(), true)
            // رد فعلًا (يقبل المكالمة) — لا يفتح التطبيق فقط
            .addAction(0, getString(com.red.sovereign.R.string.notification_accept), serviceIntent(ACTION_ACCEPT))
            .addAction(0, getString(com.red.sovereign.R.string.notification_reject), serviceIntent(ACTION_REJECT))
            .build()
    private fun updateNotification(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, true))
    private fun appIntent() = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun serviceIntent(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, YounesCallService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() {
        getSystemService(SensorManager::class.java).unregisterListener(this)
        clearRingTimeout()
        stopRingback()
        stopRingtone()
        scope.cancel()
        engine?.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "red_calls"; private const val NOTIFICATION_ID = 7401
        const val ACTION_LISTEN = "com.red.sovereign.call.LISTEN"; const val ACTION_STOP = "com.red.sovereign.call.STOP"
        const val ACTION_START = "com.red.sovereign.call.START"; const val ACTION_ACCEPT = "com.red.sovereign.call.ACCEPT"; const val ACTION_REJECT = "com.red.sovereign.call.REJECT"; const val ACTION_END = "com.red.sovereign.call.END"
        const val ACTION_MIC = "com.red.sovereign.call.MIC"; const val ACTION_CAMERA = "com.red.sovereign.call.CAMERA"; const val ACTION_SWITCH_CAMERA = "com.red.sovereign.call.SWITCH_CAMERA"; const val ACTION_SPEAKER = "com.red.sovereign.call.SPEAKER"; const val ACTION_BLUETOOTH = "com.red.sovereign.call.BLUETOOTH"
        const val ACTION_HOLD = "com.red.sovereign.call.HOLD"; const val ACTION_RESUME = "com.red.sovereign.call.RESUME"; const val ACTION_DTMF = "com.red.sovereign.call.DTMF"
        const val ACTION_ACCEPT_SECOND = "com.red.sovereign.call.ACCEPT_SECOND"; const val ACTION_REJECT_SECOND = "com.red.sovereign.call.REJECT_SECOND"
        const val ACTION_START_RECORDING = "com.red.sovereign.call.START_RECORDING"; const val ACTION_STOP_RECORDING = "com.red.sovereign.call.STOP_RECORDING"
        const val EXTRA_CONSENT = "consent"
        // PSTN interop actions — تُرسل من PhoneStateReceiver عند ورود/انتهاء مكالمة هاتفية
        const val ACTION_SILENCE_RINGER = "com.red.sovereign.call.SILENCE_RINGER"; const val ACTION_HOLD_ACTIVE = "com.red.sovereign.call.HOLD_ACTIVE"; const val ACTION_RESUME_RINGER = "com.red.sovereign.call.RESUME_RINGER"
        const val EXTRA_TARGET = "target"; const val EXTRA_MODE = "mode"; const val EXTRA_ENABLED = "enabled"; const val EXTRA_DTMF = "dtmf"; const val EXTRA_CAMERA = "camera"
        
        private fun safeStartService(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try { context.startService(intent) } catch (ex: Exception) { /* ignored */ }
            }
        }
        
        fun listen(context: Context) = safeStartService(context, Intent(context, YounesCallService::class.java).setAction(ACTION_LISTEN))
        fun stop(context: Context) = context.startService(Intent(context, YounesCallService::class.java).setAction(ACTION_STOP))
        fun start(context: Context, target: String, video: Boolean) = safeStartService(context, Intent(context, YounesCallService::class.java).setAction(ACTION_START).putExtra(EXTRA_TARGET, target).putExtra(EXTRA_MODE, if (video) "VIDEO" else "VOICE"))
        fun accept(context: Context, cameraOn: Boolean = true, micOn: Boolean = true) = safeStartService(
            context,
            Intent(context, YounesCallService::class.java).setAction(ACTION_ACCEPT).putExtra(EXTRA_CAMERA, cameraOn).putExtra(EXTRA_ENABLED, micOn)
        )
        fun action(context: Context, action: String, enabled: Boolean = true) = safeStartService(context, Intent(context, YounesCallService::class.java).setAction(action).putExtra(EXTRA_ENABLED, enabled))
        fun dtmf(context: Context, digit: Char) = safeStartService(context, Intent(context, YounesCallService::class.java).setAction(ACTION_DTMF).putExtra(EXTRA_DTMF, digit.toString()))
        // PSTN interop: تُرسل كـ startService (لا foreground) لأن الخدمة تعمل مسبقًا أثناء المكالمة.
        // startForegroundService هنا يرمي ForegroundServiceStartNotAllowedException على Android 12+.
        fun silenceRinger(context: Context) = runCatching { context.startService(Intent(context, YounesCallService::class.java).setAction(ACTION_SILENCE_RINGER)) }
        fun holdActiveCall(context: Context) = runCatching { context.startService(Intent(context, YounesCallService::class.java).setAction(ACTION_HOLD_ACTIVE)) }
        fun resumeRinger(context: Context) = runCatching { context.startService(Intent(context, YounesCallService::class.java).setAction(ACTION_RESUME_RINGER)) }
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
    // ── حالات جديدة كاملة ──────────────────────────────────────────────────────
    /** الطرف الآخر مشغول — تُشغَّل نغمة مشغول */
    data class Busy(val peer: String) : CallUiState
    /** الطرف الآخر رفض المكالمة صراحةً */
    data class Declined(val peer: String) : CallUiState
    /** انتهت مهلة الرنين بلا رد */
    data class NoAnswer(val peer: String) : CallUiState
    /** انتهت المكالمة بشكل طبيعي */
    data class CallEnded(
        val peer: String,
        val mode: String,
        val durationMs: Long,
        val callId: String,
        val canRedial: Boolean = true
    ) : CallUiState
    /** جاري إعادة الاتصال بعد انقطاع مؤقت */
    data class Reconnecting(val callId: String, val peer: String, val mode: String, val attempt: Int = 1) : CallUiState
}
object CallRuntime {
    var state: CallUiState by androidx.compose.runtime.mutableStateOf(CallUiState.Idle)
    var eglContext: org.webrtc.EglBase.Context? = null
    var localVideo: VideoTrack? by androidx.compose.runtime.mutableStateOf(null)
    var remoteVideo: VideoTrack? by androidx.compose.runtime.mutableStateOf(null)
    var speaker by androidx.compose.runtime.mutableStateOf(false)
    var isMinimized by androidx.compose.runtime.mutableStateOf(false)
    var networkStats: NetworkStats by androidx.compose.runtime.mutableStateOf(NetworkStats())
    /** هل تسجيل المكالمة يعمل الآن — تُعرض كشارة حمراء في واجهة المكالمة */
    var isRecording by androidx.compose.runtime.mutableStateOf(false)
}