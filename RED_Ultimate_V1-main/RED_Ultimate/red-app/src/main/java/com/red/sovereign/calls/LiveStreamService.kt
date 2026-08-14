package com.red.sovereign.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.red.sovereign.MainActivity
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

sealed interface LiveStreamUiState {
    data object Idle : LiveStreamUiState
    /** إشعار "بدأ البث" — مشاهدة اختيارية، ليست رنة هاتف */
    data class Incoming(val streamId: String, val broadcasterName: String, val userId: String) : LiveStreamUiState
    data class Connecting(val streamId: String, val isBroadcaster: Boolean) : LiveStreamUiState
    data class Active(val streamId: String, val isBroadcaster: Boolean, val startedAt: Long) : LiveStreamUiState
    data class Error(val message: String) : LiveStreamUiState
}

data class LiveChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class LiveStreamReaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val emoji: String = "❤️",
    val timestamp: Long = System.currentTimeMillis()
)

data class RaisedHandUser(
    val userId: String,
    val userName: String
)

object LiveStreamRuntime {
    var state: LiveStreamUiState by mutableStateOf(LiveStreamUiState.Idle)
    var localVideo: VideoTrack? by mutableStateOf(null)
    var remoteVideo: VideoTrack? by mutableStateOf(null)
    var coHostVideo: VideoTrack? by mutableStateOf(null)
    var eglContext: org.webrtc.EglBase.Context? = null
    var isMuted by mutableStateOf(false)
    var isAudioOnly by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    /** عدد المشاهدين النشطين — محدّث عبر signaling (PARTICIPANT_JOINED/LEFT) */
    var viewerCount: Int by mutableStateOf(0)
    var chatMessages: List<LiveChatMessage> by mutableStateOf(emptyList())
    var reactions: List<LiveStreamReaction> by mutableStateOf(emptyList())
    var raisedHands: List<RaisedHandUser> by mutableStateOf(emptyList())
    var isCoHost by mutableStateOf(false)
}

class LiveStreamService : Service(), WebRtcEngine.Events, MeshRtcSession.Events, LiveStreamSignalingClient.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: LiveStreamSignalingClient
    private var engine: WebRtcEngine? = null
    private var mesh: MeshRtcSession? = null
    private var recordingManager: CallRecordingManager? = null
    private var streamId = ""
    private var userId = ""
    private var streamTitle = ""
    private var isBroadcaster = false
    private var stopping = false
    private var cleanedUp = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
        signaling = LiveStreamSignalingClient(this, TokenStore(this), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INVITE -> {
                streamId = intent.getStringExtra(EXTRA_STREAM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val broadcasterName = intent.getStringExtra(EXTRA_BROADCASTER_NAME).orEmpty()
                LiveStreamRuntime.state = LiveStreamUiState.Incoming(streamId, broadcasterName, userId)
                showIncomingLiveStreamNotification(streamId, userId, broadcasterName)
            }
            ACTION_START -> {
                stopping = false
                cleanedUp = false
                streamId = intent.getStringExtra(EXTRA_STREAM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                streamTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "بث مباشر يونس" }
                isBroadcaster = intent.getBooleanExtra(EXTRA_BROADCASTER, false)
                LiveStreamRuntime.state = LiveStreamUiState.Connecting(streamId, isBroadcaster)
                promote()
                scope.launch {
                    val ready = if (isBroadcaster) registerBroadcaster() else joinAsViewer()
                    if (ready) signaling.connect(streamId) else onError("LIVE_STREAM_REGISTRATION_FAILED")
                }
            }
            ACTION_STOP -> stopStream()
            ACTION_TOGGLE_MIC -> {
                LiveStreamRuntime.isMuted = !LiveStreamRuntime.isMuted
                val micOn = !LiveStreamRuntime.isMuted
                engine?.setMicrophoneEnabled(micOn)
                mesh?.setMicrophoneEnabled(micOn)
            }
            ACTION_TOGGLE_VIDEO -> {
                val isVideoOn = LiveStreamRuntime.localVideo?.enabled() == true
                if (!isVideoOn && LiveStreamRuntime.localVideo == null) {
                    // إعادة محاولة فتح الكاميرا (إذن مُنح لاحقاً أو خلل مؤقت)
                    scope.launch {
                        val ok = engine?.retryCamera() == true || mesh?.retryCamera() == true
                        if (ok) {
                            LiveStreamRuntime.localVideo = engine?.localMedia?.videoTrack ?: mesh?.localVideo
                        }
                    }
                } else {
                    engine?.setCameraEnabled(!isVideoOn)
                    mesh?.setCameraEnabled(!isVideoOn)
                }
            }
            ACTION_SWITCH_CAMERA -> {
                engine?.switchCamera()
                mesh?.switchCamera()
            }
            ACTION_TOGGLE_AUDIO_ONLY -> {
                LiveStreamRuntime.isAudioOnly = !LiveStreamRuntime.isAudioOnly
                val cameraOn = !LiveStreamRuntime.isAudioOnly
                engine?.setCameraEnabled(cameraOn)
                mesh?.setCameraEnabled(cameraOn)
            }
            ACTION_START_RECORDING -> {
                // موافقة صريحة من واجهة المستخدم — لا تُفترض أبداً (خصوصية الطرفين)
                val consent = intent.getBooleanExtra(YounesCallService.EXTRA_CONSENT, false)
                if (consent && recordingManager == null && streamId.isNotBlank()) {
                    recordingManager = CallRecordingManager(this, streamId)
                }
                recordingManager?.start(consentGranted = consent)
                LiveStreamRuntime.isRecording = consent
            }
            ACTION_STOP_RECORDING -> {
                scope.launch {
                    recordingManager?.stop()
                    LiveStreamRuntime.isRecording = false
                    recordingManager = null
                }
            }
            ACTION_SEND_CHAT -> {
                val text = intent.getStringExtra(EXTRA_CHAT_TEXT).orEmpty()
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
                if (text.isNotBlank()) {
                    signaling.sendChatMessage(streamId, userId, senderName, text)
                    val localMsg = LiveChatMessage(senderId = userId, senderName = senderName.ifBlank { "أنا" }, text = text)
                    LiveStreamRuntime.chatMessages = (LiveStreamRuntime.chatMessages + localMsg).takeLast(50)
                }
            }
            ACTION_SEND_REACTION -> {
                val emoji = intent.getStringExtra(EXTRA_REACTION_EMOJI) ?: "❤️"
                signaling.sendReaction(streamId, userId, emoji)
                val localReaction = LiveStreamReaction(emoji = emoji)
                LiveStreamRuntime.reactions = (LiveStreamRuntime.reactions + localReaction).takeLast(25)
            }
            ACTION_RAISE_HAND -> {
                val userName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: userId
                signaling.raiseHand(streamId, userId, userName)
            }
            ACTION_APPROVE_COHOST -> {
                val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (targetUserId.isNotBlank()) {
                    signaling.approveCoHost(streamId, userId, targetUserId)
                    LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != targetUserId }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun registerBroadcaster(): Boolean {
        if (streamId.isBlank() || userId.isBlank()) return false
        val payload = org.json.JSONObject()
            .put("streamId", streamId)
            .put("title", streamTitle)
            .put("isPrivate", false)
            .toString()
        return when (AuthorizedApiClient(TokenStore(this)).request("POST", "/api/livestream/create", payload)) {
            is ApiResult.Success -> true
            is ApiResult.Error -> false
        }
    }

    private suspend fun joinAsViewer(): Boolean {
        if (streamId.isBlank()) return false
        return when (AuthorizedApiClient(TokenStore(this)).request("POST", "/api/livestream/$streamId/join", "{}")) {
            is ApiResult.Success -> true
            is ApiResult.Error -> false
        }
    }

    override fun onConnected() {
        scope.launch {
            engine = WebRtcEngine(this@LiveStreamService, this@LiveStreamService)
            LiveStreamRuntime.eglContext = engine?.eglContext
            // Live streaming needs HD + simulcast for adaptive quality to viewers
            if (isBroadcaster) engine?.create(CallMediaKind.LIVE) else engine?.create(CallMediaKind.VIDEO, simulcastEnabled = false, svc = false)
            if (isBroadcaster) {
                LiveStreamRuntime.localVideo = engine?.localMedia?.videoTrack
                LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, true, System.currentTimeMillis())
                startStatsPolling()
            }
            signaling.join(streamId, userId, if (isBroadcaster) "broadcaster" else "viewer")
        }
    }

    override fun onSignal(signal: LiveStreamSignal) {
        when (signal.type) {
            "OFFER" -> {
                signal.payload["sdp"]?.let {
                    if (isBroadcaster) {
                        mesh?.handleOffer(signal.userId.ifBlank { signal.payload["userId"].orEmpty() }, it)
                    } else {
                        engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, it)) {
                            engine?.answer()
                        }
                    }
                }
            }
            "ANSWER" -> {
                signal.payload["sdp"]?.let {
                    if (isBroadcaster) {
                        mesh?.handleAnswer(signal.userId.ifBlank { signal.payload["userId"].orEmpty() }, it)
                    } else {
                        engine?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, it))
                    }
                }
            }
            "ICE" -> {
                val from = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                val candidate = IceCandidate(
                    signal.payload["sdpMid"],
                    signal.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0,
                    signal.payload["candidate"].orEmpty()
                )
                if (isBroadcaster) mesh?.handleIce(from, candidate) else engine?.addIce(candidate)
            }
            "STREAM_ENDED" -> {
                stopStream()
                return
            }
            "VIEWER_JOINED" -> {
                val viewerId = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                if (isBroadcaster && viewerId.isNotBlank()) {
                    LiveStreamRuntime.viewerCount += 1
                    mesh?.attachPeer(viewerId)
                    mesh?.offerTo(viewerId)
                }
            }
            "VIEWER_LEFT", "PARTICIPANT_LEFT" -> {
                val leftId = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                if (isBroadcaster) {
                    if (leftId.isNotBlank()) mesh?.detachPeer(leftId)
                    LiveStreamRuntime.viewerCount = (LiveStreamRuntime.viewerCount - 1).coerceAtLeast(0)
                }
            }
            "CHAT" -> {
                val senderId = signal.userId
                val senderName = signal.payload["senderName"].orEmpty()
                val text = signal.payload["text"].orEmpty()
                if (text.isNotBlank()) {
                    val msg = LiveChatMessage(senderId = senderId, senderName = senderName, text = text)
                    LiveStreamRuntime.chatMessages = (LiveStreamRuntime.chatMessages + msg).takeLast(50)
                }
            }
            "REACTION" -> {
                val emoji = signal.payload["emoji"] ?: "❤️"
                val reaction = LiveStreamReaction(emoji = emoji)
                LiveStreamRuntime.reactions = (LiveStreamRuntime.reactions + reaction).takeLast(25)
            }
            "RAISE_HAND" -> {
                val userName = signal.payload["userName"] ?: signal.userId
                if (!LiveStreamRuntime.raisedHands.any { it.userId == signal.userId }) {
                    LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands + RaisedHandUser(signal.userId, userName)
                }
            }
            "LOWER_HAND" -> {
                LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != signal.userId }
            }
            "APPROVE_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    LiveStreamRuntime.isCoHost = true
                }
            }
        }
    }

    override fun onDisconnected() {
        when (LiveStreamRuntime.state) {
            is LiveStreamUiState.Active, is LiveStreamUiState.Connecting -> {
                LiveStreamRuntime.state = LiveStreamUiState.Connecting(streamId, isBroadcaster)
                runCatching { signaling.reconnect(streamId) }
            }
            else -> stopStream()
        }
    }
    override fun onCameraUnavailable() {}
    override fun onError(message: String) {
        if (message == "UNAUTHORIZED" || message == "LIVE_STREAM_REGISTRATION_FAILED") {
            scope.launch {
                kotlinx.coroutines.delay(1500)
                stopStream()
            }
            return
        }
        if (LiveStreamRuntime.state is LiveStreamUiState.Active || LiveStreamRuntime.state is LiveStreamUiState.Connecting) {
            runCatching { signaling.reconnect(streamId) }
            return
        }
        LiveStreamRuntime.state = LiveStreamUiState.Error(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (LiveStreamRuntime.state is LiveStreamUiState.Error) stopStream()
        }
    }

    override fun onLocalDescription(description: SessionDescription) {
        if (description.type == SessionDescription.Type.ANSWER) {
            signaling.sendAnswer(streamId, userId, description.description)
        } else {
            signaling.sendOffer(streamId, userId, description.description)
        }
    }

    override fun onLocalDescription(peerId: String, description: SessionDescription) {
        if (description.type == SessionDescription.Type.ANSWER) {
            signaling.sendAnswer(streamId, userId, description.description, peerId)
        } else {
            signaling.sendOffer(streamId, userId, description.description, peerId)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        signaling.sendIce(streamId, userId, candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp ?: "")
    }

    override fun onIceCandidate(peerId: String, candidate: IceCandidate) {
        signaling.sendIce(streamId, userId, candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp ?: "", peerId)
    }

    override fun onRemoteVideo(track: VideoTrack) {
        if (!isBroadcaster) {
            LiveStreamRuntime.remoteVideo = track
        }
    }

    override fun onRemoteVideo(peerId: String, track: VideoTrack) {
        if (!isBroadcaster) LiveStreamRuntime.remoteVideo = track
        else LiveStreamRuntime.coHostVideo = track
    }

    override fun onNetworkStats(stats: NetworkStats) { LiveStreamRuntime.networkStats = stats }

    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, isBroadcaster, System.currentTimeMillis())
            startStatsPolling()
        }
    }

    override fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, isBroadcaster, System.currentTimeMillis())
            startStatsPolling()
        }
    }

    private var statsJob: kotlinx.coroutines.Job? = null
    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                engine?.pollStats()
                mesh?.pollStats()
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun stopStream() {
        if (stopping) return
        stopping = true
        val closingStreamId = streamId
        val endpoint = if (isBroadcaster) "stop" else "leave"
        scope.launch {
            if (closingStreamId.isNotBlank()) {
                withTimeoutOrNull(3_000) {
                    runCatching {
                        AuthorizedApiClient(TokenStore(this@LiveStreamService))
                            .request("POST", "/api/livestream/$closingStreamId/$endpoint", "{}")
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) { finishStop() }
        }
    }

    private fun finishStop(terminateService: Boolean = true) {
        if (cleanedUp) return
        cleanedUp = true
        statsJob?.cancel(); statsJob = null
        if (streamId.isNotBlank()) signaling.leave(streamId, userId)
        signaling.close()
        engine?.release()
        engine = null
        mesh?.release()
        mesh = null
        LiveStreamRuntime.state = LiveStreamUiState.Idle
        LiveStreamRuntime.localVideo = null
        LiveStreamRuntime.remoteVideo = null
        LiveStreamRuntime.coHostVideo = null
        LiveStreamRuntime.eglContext = null
        LiveStreamRuntime.networkStats = NetworkStats()
        LiveStreamRuntime.viewerCount = 0
        LiveStreamRuntime.chatMessages = emptyList()
        LiveStreamRuntime.reactions = emptyList()
        LiveStreamRuntime.raisedHands = emptyList()
        LiveStreamRuntime.isCoHost = false
        LiveStreamRuntime.isMuted = false
        LiveStreamRuntime.isAudioOnly = false
        LiveStreamRuntime.isRecording = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (terminateService) stopSelf()
    }

    private fun showIncomingLiveStreamNotification(streamId: String, userId: String, broadcasterName: String) {
        val watchIntent = Intent(this, LiveStreamService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_STREAM_ID, streamId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_BROADCASTER, false)
        }
        val watchPending = PendingIntent.getService(this, 1, watchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val dismissIntent = Intent(this, LiveStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val dismissPending = PendingIntent.getService(this, 2, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("بث مباشر يونس")
            .setContentText("بدأ ${broadcasterName.ifBlank { "المُبث" }} بثاً مباشراً")
            .setContentIntent(mainIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(false)
            .setColor(0xFFE53935.toInt())
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(0, "مشاهدة", watchPending)
            .addAction(0, "تجاهل", dismissPending)
            .build()

        ServiceCompat.startForeground(this, 7403, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun promote() {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = if (isBroadcaster) "بث مباشر يونس" else "مشاهدة بث يونس"
        val text = if (isBroadcaster) "أنت تبث الآن مباشرة..." else "أنت تشاهد البث المباشر..."
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(if (isBroadcaster) 0xFFE53935.toInt() else 0xFF00C98C.toInt())
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "إيقاف", PendingIntent.getService(this, 1, Intent(this, LiveStreamService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isBroadcaster) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, 7403, notif, type)
    }

    override fun onDestroy() {
        finishStop(terminateService = false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_INVITE = "com.red.sovereign.livestream.INVITE"
        const val ACTION_START = "com.red.sovereign.livestream.START"
        const val ACTION_STOP = "com.red.sovereign.livestream.STOP"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.livestream.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.livestream.TOGGLE_VIDEO"
        const val ACTION_SWITCH_CAMERA = "com.red.sovereign.livestream.SWITCH_CAMERA"
        const val ACTION_TOGGLE_AUDIO_ONLY = "com.red.sovereign.livestream.TOGGLE_AUDIO_ONLY"
        const val ACTION_START_RECORDING = "com.red.sovereign.livestream.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.red.sovereign.livestream.STOP_RECORDING"
        const val ACTION_SEND_CHAT = "com.red.sovereign.livestream.SEND_CHAT"
        const val ACTION_SEND_REACTION = "com.red.sovereign.livestream.SEND_REACTION"
        const val ACTION_RAISE_HAND = "com.red.sovereign.livestream.RAISE_HAND"
        const val ACTION_APPROVE_COHOST = "com.red.sovereign.livestream.APPROVE_COHOST"

        const val EXTRA_STREAM_ID = "stream_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_BROADCASTER = "broadcaster"
        const val EXTRA_BROADCASTER_NAME = "broadcaster_name"
        const val EXTRA_TITLE = "stream_title"
        const val EXTRA_CHAT_TEXT = "chat_text"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_REACTION_EMOJI = "reaction_emoji"
        const val EXTRA_TARGET_USER_ID = "target_user_id"

        fun sendChat(context: Context, text: String, senderName: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_SEND_CHAT
                putExtra(EXTRA_CHAT_TEXT, text)
                putExtra(EXTRA_SENDER_NAME, senderName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendReaction(context: Context, emoji: String = "❤️") {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_SEND_REACTION
                putExtra(EXTRA_REACTION_EMOJI, emoji)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun raiseHand(context: Context, userName: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_RAISE_HAND
                putExtra(EXTRA_SENDER_NAME, userName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun approveCoHost(context: Context, targetUserId: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_APPROVE_COHOST
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun invite(context: Context, streamId: String, userId: String, broadcasterName: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_INVITE
                putExtra(EXTRA_STREAM_ID, streamId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_BROADCASTER_NAME, broadcasterName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun start(
            context: Context,
            streamId: String,
            userId: String,
            isBroadcaster: Boolean,
            title: String = "بث مباشر يونس"
        ) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STREAM_ID, streamId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_BROADCASTER, isBroadcaster)
                putExtra(EXTRA_TITLE, title)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(ACTION_STOP))
        }
        fun action(context: Context, act: String) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(act))
        }
    }
}
