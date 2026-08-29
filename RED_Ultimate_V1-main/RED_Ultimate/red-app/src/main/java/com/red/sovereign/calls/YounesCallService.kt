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
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.util.Range
import android.util.Size
import android.app.PictureInPictureParams
import android.view.WindowManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioFormat
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

class YounesCallService : Service(), WebRtcEngine.Events, CallSignalingClient.Listener, SensorEventListener,
        CallPresenceMonitor.Listener, CallDeliveryEngine.Listener {
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
    /** آخر عرض وارد (يبقى محفوظاً حتى بعد مسح incomingOffer عند الإلغاء) — لتسجيل الاتجاه الصحيح في السجل المحلي */
    private var lastIncomingOffer: CallSignal? = null
    private var pendingSecondOffer: CallSignal? = null
    private val pendingIce = java.util.concurrent.CopyOnWriteArrayList<IceCandidate>()
    @Volatile
    private var remoteDescriptionSet = false
    private var proximityLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var reconnect: CallReconnectManager? = null
    private var networkWatcher: NetworkChangeWatcher? = null
    private var vibrator: Vibrator? = null
    private var audioFocus: AudioFocusRequest? = null
    private var recordingManager: CallRecordingManager? = null
    private var recordingConsentShown: Boolean = false
    private var ringTimeoutJob: kotlinx.coroutines.Job? = null
    private var ringback: ToneGenerator? = null
    private var ringStartedAt: Long = 0L
    /** هل انتهت مهلة الرنين الواردة دون رد — لتمييز MISSED عن REJECTED في السجل المحلي */
    private var ringTimedOut = false

    // ── منظومة ضمان وصول المكالمة (Multi-Path Delivery) ─────────────────
    private lateinit var deliveryEngine: CallDeliveryEngine
    private lateinit var presenceMonitor: CallPresenceMonitor

    override fun onCreate() {
        super.onCreate(); createChannel()
        audio = getSystemService(AudioManager::class.java)
        telecom = TelecomBridge(this).also { runCatching(it::register) }
        signaling = CallSignalingClient(this, TokenStore(this), this)
        val httpClient = com.red.sovereign.security.SecureOkHttpClient.buildWebSocketClient(this)
        deliveryEngine = CallDeliveryEngine(this, TokenStore(this), signaling, httpClient)
        presenceMonitor = CallPresenceMonitor(deliveryEngine)
        val sensors = getSystemService(SensorManager::class.java)
        sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // إعادة اتصال تلقائية عند انقطاع الإشارة (بدل إنهاء المكالمة)
        reconnect = CallReconnectManager(
            scope = scope,
            onReconnect = {
                runCatching { signaling.reconnect() }
                signaling.isConnected()
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
            ACTION_ACCEPT_VIDEO -> acceptIncoming(
                cameraOn = true,
                micOn = intent.getBooleanExtra(EXTRA_ENABLED, true)
            )
            ACTION_REJECT -> rejectIncoming()
            ACTION_END -> endCall(sendSignal = true)
            ACTION_MIC -> engine?.setMicrophoneEnabled(intent.getBooleanExtra(EXTRA_ENABLED, true))
            ACTION_CAMERA -> {
                val enable = intent.getBooleanExtra(EXTRA_ENABLED, true)
                if (enable && CallRuntime.localVideo == null) {
                    // إعادة محاولة فتح الكاميرا (بعد منح الإذن من الإعدادات، أو خلل مؤقت)
                    scope.launch {
                        if (engine?.retryCamera() == true) {
                            CallRuntime.localVideo = engine?.localMedia?.videoTrack
                            CallRuntime.cameraNotice = false
                            updateNotification("الكاميرا نشطة")
                        }
                    }
                } else {
                    engine?.setCameraEnabled(enable)
                    CallRuntime.cameraNotice = false
                }
            }
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
            ACTION_STOP -> {
                // تنظيف كامل عند إيقاف الخدمة يدوياً (من الإشعار أو النظام) —
                // يضمن عدم بقاء مكالمة وهمية أو تسجيل أو رنة في الخلفية.
                clearRingTimeout()
                stopRingback()
                stopRingtone()
                failedIceJob?.cancel(); failedIceJob = null
                statsJob?.cancel(); statsJob = null
                CallRuntime.state = CallUiState.Idle
                endCallCore(sendSignal = false)
                signaling.close()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // لا تعِد تشغيل الخدمة بعد إيقافها يدوياً — تفادياً لدورة إعادة إحياء لا نهائية
        return if (intent?.action == ACTION_STOP) START_NOT_STICKY else START_STICKY
    }

    override fun onConnected() {
        reconnect?.stop()
        // مراقبة تبديل الشبكة (Wi-Fi ↔ بيانات) — عند التعافي نعيد IceRestart
        if (networkWatcher == null) {
            networkWatcher = NetworkChangeWatcher(this) {
                if (CallRuntime.state is CallUiState.Active || CallRuntime.state is CallUiState.ActiveWithIncoming) {
                    engine?.restartIce()
                    updateNotification("تبديل الشبكة — إعادة ضبط المسار…")
                }
            }.also { it.start() }
        }
        // نجحت إعادة الاتصال أثناء مكالمة نشطة — استعد واجهة المكالمة مع مدة غير منكسرة
        val wasReconnecting = CallRuntime.state as? CallUiState.Reconnecting
        if (wasReconnecting != null) {
            CallRuntime.state = CallUiState.Active(
                wasReconnecting.callId, wasReconnecting.peer, wasReconnecting.mode,
                wasReconnecting.startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            updateNotification("تم استعادة الاتصال — مكالمة يونس نشطة")
        }
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
        // ZOOM_INVITE استثناء: مثل GROUP_CALL_INVITE يحمل groupCallId لكنه يجب
        // أن يُطلق رنين اجتماع Zoom الوارد، وإلا بقي المدعوّون لا يُخطَرون أبداً.
        if (signal.groupCallId != null && signal.type != "GROUP_CALL_INVITE" && signal.type != "ZOOM_INVITE") return
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
                lastIncomingOffer = signal
                target = newIncoming.peer
                callId = newIncoming.callId
                mode = newIncoming.mode
                CallRuntime.state = newIncoming
                if (com.red.sovereign.settings.SettingsRuntime.current.callNotifications) startRingtone()
                armRingTimeout(outgoing = false)
                runCatching {
                    IncomingCallActivity.launch1to1(
                        this,
                        callId = newIncoming.callId,
                        peer = newIncoming.peer,
                        mode = newIncoming.mode,
                        inviter = newIncoming.peer
                    )
                }
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
            "RINGING" -> {
                // المستلم بدأ يرن — أبلغ نظام التسليم والـ presenceMonitor
                presenceMonitor.onSignalReceived(callId.orEmpty(), "RINGING", this)
                val cur = CallRuntime.state as? CallUiState.Connecting
                if (cur != null) CallRuntime.state = cur.withPresence(CallPresenceMonitor.PresenceState.RINGING)
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
                    val missedPeer = (CallRuntime.state as? CallUiState.Incoming)?.peer.orEmpty()
                    // نحن المستلم ولم نرد ⇒ مكالمة فائتة، لا «لم يتم الرد».
                    CallRuntime.state = CallUiState.NoAnswer(missedPeer, mode, outgoing = false)
                    updateNotification("${CallRingPolicy.unansweredMessage(outgoing = false)} من $missedPeer")
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
            "ZOOM_INVITE" -> {
                // دعوة اجتماع Zoom وارد — يُطلق رنين ZoomGroupCallService (كان الحدث
                // يُرسله المضيف لكن لا مستقبِل له، فلم يُخطَر المدعوّون قط).
                val gId = signal.groupCallId ?: signal.callId.orEmpty()
                val myId = TokenStore(this).redId.orEmpty()
                ZoomGroupCallService.notifyIncoming(
                    this,
                    gId,
                    myId,
                    signal.sourceUserId.orEmpty(),
                    signal.payload["hostName"] ?: signal.sourceUserId.orEmpty(),
                    (signal.payload["isVideo"] == "true") || signal.mode == "VIDEO",
                    signal.payload["title"] ?: "",
                    signal.inviteeIds.filter { it != myId }
                )
            }
            "ZOOM_END" -> {
                val gId = signal.groupCallId ?: signal.callId.orEmpty()
                val current = ZoomRuntime.state
                val incomingMatches = current is ZoomUiState.Incoming && current.meetingId == gId
                val ringingMatches = current is ZoomUiState.Ringing && current.meetingId == gId
                if (incomingMatches || ringingMatches) {
                    ZoomGroupCallService.decline(this, gId)
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
        val eng = engine ?: return ApiResult.Error(500, "ENGINE_NOT_CREATED")
        val result = eng.create(kind)
        if (result is ApiResult.Success) {
            CallRuntime.localVideo = eng.localMedia?.videoTrack
            if (CallRuntime.localVideo != null) CallRuntime.cameraNotice = false
        }
        return result
    }

    override fun onLocalDescription(description: SessionDescription) {
        val type = when {
            description.type == SessionDescription.Type.ANSWER -> "ANSWER"
            isActiveCall() -> "RENEGOTIATE"
            else -> "OFFER"
        }
        val signal = CallSignal(callId, target, type = type, mode = mode, payload = mapOf("sdp" to description.description))
        if (type == "OFFER") {
            // ── ضمان وصول المكالمة للمستلم (Multi-Path Delivery) ──
            outgoingPending = false
            presenceMonitor.start(callId.orEmpty(), this)
            deliveryEngine.deliverCallOffer(signal, target, this)
        } else {
            signaling.send(signal)
        }
        if (!isActiveCall()) {
            CallRuntime.state = CallUiState.Connecting(
                callId.orEmpty(), target, mode,
                presenceState = CallPresenceMonitor.PresenceState.CONNECTING
            )
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        // استخدم CallDeliveryEngine للـ Trickle ICE مع إعادة الإرسال عند ICE restart
        deliveryEngine.onLocalIceCandidate(callId.orEmpty(), candidate, target)
    }
    override fun onRemoteVideo(track: VideoTrack) { CallRuntime.remoteVideo = track }
    override fun onNetworkStats(stats: NetworkStats) { CallRuntime.networkStats = stats; onStatsReceived(stats) }
    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                clearRingTimeout()
                stopRingback()
                stopRingtone()
                failedIceJob?.cancel()
                CallRuntime.state = CallUiState.Active(callId.orEmpty(), target, mode, System.currentTimeMillis())
                updateNotification("مكالمة يونس نشطة")
                startStatsPolling()
            }
            PeerConnection.PeerConnectionState.FAILED -> {
                // انقطاع مؤقت (تبديل شبكة، إغلاق مكتمل) — لا ننهي المكالمة فوراً:
                // نحاول ICE restart، وننهي فقط إذا بقي الاتصال فاشلاً بعد المهلة.
                if (CallRuntime.state !is CallUiState.Idle && CallRuntime.state !is CallUiState.Incoming) {
                    failedIceJob?.cancel()
                    engine?.restartIce()
                    failedIceJob = scope.launch {
                        kotlinx.coroutines.delay(ICE_RESTART_GRACE_MS)
                        // لم يتعافَ الاتصال خلال المهلة → ننهي المكالمة نهائياً
                        if (CallRuntime.state is CallUiState.Active ||
                            CallRuntime.state is CallUiState.ActiveWithIncoming ||
                            CallRuntime.state is CallUiState.Reconnecting
                        ) {
                            updateNotification("انقطع الاتصال — جارٍ الإنهاء…")
                            endCall(sendSignal = false)
                        }
                    }
                } else {
                    endCall(sendSignal = false)
                }
            }
            PeerConnection.PeerConnectionState.CLOSED -> endCall(sendSignal = false)
            else -> Unit
        }
    }

    private var statsJob: kotlinx.coroutines.Job? = null
    private var failedIceJob: kotlinx.coroutines.Job? = null
    // مهلة أطول من نافذة إعادة الاتصال في CallReconnectManager (30 ثانية) —
    // حتى لا يُقتل ICE restart قبل أن تتعافى شبكة الهاتف من الانقطاع المؤقت.
    private val ICE_RESTART_GRACE_MS: Long = 45_000L
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
        // ملاحظة: لا نستدعي engine.adjustQuality هنا — التكييف النشط يتم عبر
        // WebRtcEngine.applyAdaptiveBitrate (profiles + simulcast) داخل pollStats.
        // الجمع بين النظامين كان يتعارض كل دورة (أحدهما يخفض والآخر يرفع).
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
    override fun onCameraUnavailable() {
        // لا ننهي المكالمة — نعلم المستخدم ونكمل صوتياً
        CallRuntime.cameraNotice = true
        updateNotification("تعذر فتح الكاميرا — المكالمة صوتية")
    }
    override fun onDisconnected() {
        // مكالمة نشطة → إعادة اتصال بدل إنهاء المكالمة فوراً، مع عرض حالة Reconnecting
        val current = CallRuntime.state
        if (current !is CallUiState.Idle) {
            if (current is CallUiState.Active || current is CallUiState.ActiveWithIncoming || current is CallUiState.Reconnecting) {
                val active = (current as? CallUiState.Active) ?: (current as? CallUiState.ActiveWithIncoming)?.active
                CallRuntime.state = CallUiState.Reconnecting(
                    active?.callId ?: callId.orEmpty(),
                    active?.peer ?: target,
                    active?.mode ?: mode,
                    startedAt = active?.startedAt ?: 0L
                )
            }
            updateNotification("انقطع الاتصال — جارٍ إعادة الاتصال…")
            val activeCallId = (CallRuntime.state as? CallUiState.Reconnecting)?.callId ?: callId.orEmpty()
            if (activeCallId.isNotBlank() && target.isNotBlank()) {
                deliveryEngine.retransmitIceCandidates(activeCallId, target)
            }
            reconnect?.start()
        }
    }

    private fun flushIce() { pendingIce.forEach { engine?.addIce(it) }; pendingIce.clear() }

    // ── CallPresenceMonitor.Listener ─────────────────────────────────────
    override fun onPresenceState(callId: String, state: CallPresenceMonitor.PresenceState) {
        val current = CallRuntime.state as? CallUiState.Connecting ?: return
        if (current.callId != callId) return
        CallRuntime.state = current.withPresence(state)
        val label = when (state) {
            CallPresenceMonitor.PresenceState.CONNECTING -> "جارٍ الاتصال…"
            CallPresenceMonitor.PresenceState.RINGING -> "يرن على جهاز المستلم…"
            CallPresenceMonitor.PresenceState.WAKING_UP -> "جارٍ إيقاظ الجهاز…"
            CallPresenceMonitor.PresenceState.NO_ANSWER -> "لا يوجد رد"
            else -> return
        }
        updateNotification(label)
    }

    // ── CallDeliveryEngine.Listener ──────────────────────────────────────
    override fun onDeliveryProgress(callId: String, path: CallDeliveryEngine.DeliveryPath, attempt: Int) {
        val label = when (path) {
            CallDeliveryEngine.DeliveryPath.WEBSOCKET -> "جارٍ الاتصال عبر القناة المباشرة…"
            CallDeliveryEngine.DeliveryPath.FCM_PUSH -> "جارٍ إيقاظ جهاز المستلم…"
            CallDeliveryEngine.DeliveryPath.HTTP_WEBHOOK -> "جارٍ محاولة الوصول عبر قناة احتياطية ($attempt)…"
            CallDeliveryEngine.DeliveryPath.UNKNOWN -> "جارٍ الاتصال…"
        }
        if (CallRuntime.state is CallUiState.Connecting) updateNotification(label)
    }
    override fun onDeliveryConfirmed(callId: String, via: CallDeliveryEngine.DeliveryPath) {
        android.util.Log.d("YounesCallService", "[$callId] Delivery confirmed via $via")
    }
    override fun onDeliveryFailed(callId: String, reason: String) {
        if (CallRuntime.state is CallUiState.Connecting) fail(reason)
    }

    private fun isActiveCall(): Boolean =
        CallRuntime.state is CallUiState.Active || CallRuntime.state is CallUiState.ActiveWithIncoming ||
            CallRuntime.state is CallUiState.Reconnecting

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
        CallTelemetry.onHold()
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
            CallTelemetry.onRecordingStart()
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
            recording?.let { rec ->
                updateNotification("مكالمة يونس نشطة • تم حفظ التسجيل محلياً. جارٍ الرفع السحابي...")

                try {
                    val file = java.io.File(rec.filePath)
                    if (file.exists()) {
                        val tokens = com.red.sovereign.auth.TokenStore(this@YounesCallService)
                        val client = com.red.sovereign.auth.AuthorizedApiClient(tokens)
                        val mediaApi = com.red.sovereign.media.MediaApi(this@YounesCallService, client)

                        val res = mediaApi.uploadEncrypted(file, "record_${rec.callId}")
                        if (res is com.red.sovereign.auth.ApiResult.Success) {
                            android.util.Log.d("CallRecording", "Uploaded Encrypted Backup: ${res.value.objectKey}")
                            updateNotification("مكالمة يونس نشطة • اكتمل النسخ الاحتياطي المشفر")
                        } else {
                            android.util.Log.e("CallRecording", "Failed to upload recording backup")
                            updateNotification("مكالمة يونس نشطة • فشل النسخ الاحتياطي السحابي")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallRecording", "Error uploading recording: ${e.message}")
                }
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
     * يقبل المكالمة الثانية ويُوقف الأولى فعلياً (Real Park) بدل تبديل بسيط:
     * يحفظ حالة المكالمة الأولى (الهدف والـ callId والوضع وزمن البدء)،
     * وعند انتهاء المكالمة الثانية يعيد بناء المحرك ويعيد التفاوض مع الطرف الأول.
     */
    private var parkedCall: ParkedCall? = null

    data class ParkedCall(
        val callId: String,
        val peer: String,
        val mode: String,
        val startedAt: Long,
        val video: Boolean
    )

    private fun acceptSecondIncoming() {
        val state = CallRuntime.state as? CallUiState.ActiveWithIncoming ?: return
        val waiting = state.waiting
        val previous = state.active

        // 1) أرسل HOLD للمكالمة الأولى واحفظها كـ ParkedCall
        runCatching { signaling.send(CallSignal(previous.callId, previous.peer, type = "HOLD", mode = previous.mode)) }
        parkedCall = ParkedCall(
            callId = previous.callId,
            peer = previous.peer,
            mode = previous.mode,
            startedAt = previous.startedAt,
            video = previous.mode == "VIDEO"
        )

        // 2) حرر المحرك الحالي وأنشئ جديداً للمكالمة الثانية
        engine?.release()
        engine = null
        target = waiting.peer
        callId = waiting.callId
        mode = waiting.mode
        CallRuntime.state = CallUiState.Connecting(waiting.callId, waiting.peer, waiting.mode)
        promote(notification("تبديل إلى ${waiting.peer}", ongoing = true), media = true)
        prepareAudio()

        // 3) أنشئ محركاً جديداً وأرسل ANSWER
        scope.launch {
            if (createEngine(mode == "VIDEO") is ApiResult.Error) return@launch fail("تعذر إنشاء محرك WebRTC")
            val sdp = pendingSecondOffer?.payload?.get("sdp") ?: return@launch fail("عرض غير صالح")
            engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) { remoteDescriptionSet = true; flushIce(); engine?.answer() }
            pendingSecondOffer = null
            stopRingtone()
        }
    }

    /**
     * يعيد تشغيل المكالمة الموقوفة بعد انتهاء المكالمة الثانية:
     * يعيد بناء المحرك ويعيد التفاوض (RENEGOTIATE) مع الطرف الأول لاستعادة المسار.
     */
    private fun resumeParkedCall() {
        val parked = parkedCall ?: return
        parkedCall = null
        target = parked.peer
        callId = parked.callId
        mode = parked.mode
        // الحالة Active قبل offer() حتى يُصنَّف الوصف المحلي RENEGOTIATE (وليس OFFER جديد)
        CallRuntime.state = CallUiState.Active(parked.callId, parked.peer, parked.mode, parked.startedAt)
        promote(notification("استئناف مكالمة ${parked.peer}", ongoing = true), media = true)
        prepareAudio()
        scope.launch {
            if (createEngine(parked.video) is ApiResult.Error) return@launch fail("تعذر استئناف المكالمة")
            engine?.offer()
            updateNotification("مكالمة يونس نشطة")
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
        if (CallSystemSurfacePolicy.usesAndroidTelecom(mode)) {
            android.util.Log.d("YounesCallService", "prepareAudio skipped — Telecom manages audio for mode $mode")
            return
        }
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                android.util.Log.w("YounesCallService", "AudioFocus lost: $focusChange")
            }
        }.build()
        val focusResult = audio.requestAudioFocus(audioFocus!!)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            android.util.Log.w("YounesCallService", "AudioFocus not granted: $focusResult — audio may be muted")
        }
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
                ringTimedOut = true
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
        CallRuntime.state = CallUiState.Busy(busyPeer, mode)
        updateNotification("الطرف الآخر مشغول")
        scheduleCleanupAndReset()
    }

    private fun handleDeclined() {
        if (CallRuntime.state !is CallUiState.Connecting && CallRuntime.state !is CallUiState.Active) return
        clearRingTimeout()
        stopRingback()
        startSpecialTone(ToneGenerator.TONE_SUP_BUSY, 1500)
        val declinedPeer = target
        CallRuntime.state = CallUiState.Declined(declinedPeer, mode)
        updateNotification("تم رفض المكالمة")
        scheduleCleanupAndReset()
    }

    private fun handleNoAnswer() {
        if (CallRuntime.state !is CallUiState.Connecting) return
        clearRingTimeout()
        stopRingback()
        val noAnswerPeer = target
        // نحن المتصل هنا (الحالة كانت Connecting) ⇒ «لم يتم الرد» لا «مكالمة فائتة».
        CallRuntime.state = CallUiState.NoAnswer(noAnswerPeer, mode, outgoing = true)
        updateNotification(CallRingPolicy.unansweredMessage(outgoing = true))
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

    private fun handleCallEnded(sendSignal: Boolean = false) {
        val durationMs = (CallRuntime.state as? CallUiState.Active)?.let { System.currentTimeMillis() - it.startedAt } ?: 0L
        val endedPeer = target
        val endedCallId = callId.orEmpty()
        val endedMode = mode
        endCallCore(sendSignal = sendSignal)
        // كانت هناك مكالمة موقوفة (Call Waiting) → استأنفها فوراً بدل شاشة CallEnded
        if (parkedCall != null) {
            resumeParkedCall()
            return
        }
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
        failedIceJob?.cancel(); failedIceJob = null
        networkWatcher?.stop(); networkWatcher = null
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
            incoming = (incomingOffer ?: lastIncomingOffer) != null
        )
        if (sendSignal && target.isNotBlank()) runCatching { signaling.send(CallSignal(callId, target, type = "END", mode = mode)) }
        // أوقف تسجيل المكالمة إن كان يعمل — كان التسجيل يستمر في الخلفية بعد انتهاء المكالمة
        if (CallRuntime.isRecording || recordingManager?.isRecording() == true) {
            val manager = recordingManager
            recordingManager = null
            scope.launch { runCatching { manager?.stop() } }
        }
        engine?.release(); engine = null; incomingOffer = null; lastIncomingOffer = null; outgoingPending = false; pendingIce.clear(); remoteDescriptionSet = false
        presenceMonitor.stop(callId.orEmpty())
        deliveryEngine.clearCandidates(callId.orEmpty())
        ringTimedOut = false
        proximityLock?.takeIf { it.isHeld }?.release(); proximityLock = null
        audioFocus?.let(audio::abandonAudioFocusRequest); audioFocus = null
        if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else @Suppress("DEPRECATION") run { audio.isSpeakerphoneOn = false; audio.stopBluetoothSco() }
        audio.mode = AudioManager.MODE_NORMAL; target = ""; callId = null; CallRuntime.localVideo = null; CallRuntime.remoteVideo = null; CallRuntime.isRecording = false; CallRuntime.cameraNotice = false
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
                    CallRuntime.state is CallUiState.Declined || (CallRuntime.state is CallUiState.Incoming && !ringTimedOut) -> "REJECTED"
                    CallRuntime.state is CallUiState.Incoming && ringTimedOut -> "MISSED"
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
        // إذا كانت هناك مكالمة موقوفة، أنهِ الحالية واستأنف الموقوفة
        if (parkedCall != null) {
            endCallCore(sendSignal)
            resumeParkedCall()
            return
        }
        if (CallRuntime.state !is CallUiState.CallEnded && CallRuntime.state !is CallUiState.Idle) {
            handleCallEnded(sendSignal)
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
            .setFullScreenIntent(incomingFullScreenIntent(peer, callMode), true)
            // رد فعلًا (يقبل المكالمة) — لا يفتح التطبيق فقط
            .addAction(0, getString(com.red.sovereign.R.string.notification_accept), serviceIntent(ACTION_ACCEPT))
            .addAction(0, getString(com.red.sovereign.R.string.notification_reject), serviceIntent(ACTION_REJECT))
            .build()
    private fun updateNotification(text: String) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, true))
    private fun appIntent() = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun incomingFullScreenIntent(peer: String, callMode: String): PendingIntent {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("peer", peer)
            putExtra("mode", callMode)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(this, callId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun serviceIntent(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, YounesCallService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun onDestroy() {
        getSystemService(SensorManager::class.java).unregisterListener(this)
        clearRingTimeout()
        stopRingback()
        stopRingtone()
        scope.cancel()
        val engineToRelease = engine
        engine = null
        if (engineToRelease != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                runCatching { engineToRelease.release() }
            }
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "red_calls"; private const val NOTIFICATION_ID = 7401
        const val ACTION_LISTEN = "com.red.sovereign.call.LISTEN"; const val ACTION_STOP = "com.red.sovereign.call.STOP"
        const val ACTION_START = "com.red.sovereign.call.START"; const val ACTION_ACCEPT = "com.red.sovereign.call.ACCEPT"; const val ACTION_ACCEPT_VIDEO = "com.red.sovereign.call.ACCEPT_VIDEO"; const val ACTION_REJECT = "com.red.sovereign.call.REJECT"; const val ACTION_END = "com.red.sovereign.call.END"
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
// CallUiState and CallRuntime are defined in CallRuntime.kt
