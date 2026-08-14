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
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

sealed interface ConferenceUiState {
    data object Idle : ConferenceUiState
    /** دعوة انضمام لمكالمة جماعية أو مساحة */
    data class Incoming(val roomId: String, val inviter: String, val video: Boolean, val userId: String = "") : ConferenceUiState
    data class Connecting(val roomId: String) : ConferenceUiState
    data class Active(val roomId: String, val startedAt: Long) : ConferenceUiState
    data class Error(val message: String) : ConferenceUiState
}

data class SpaceReaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userId: String,
    val emoji: String = "👏",
    val timestamp: Long = System.currentTimeMillis()
)

object ConferenceRuntime {
    var state: ConferenceUiState by mutableStateOf(ConferenceUiState.Idle)
    var participants by mutableStateOf(emptyList<ConferenceParticipant>())
    var localVideo: VideoTrack? by mutableStateOf(null)
    var eglContext: org.webrtc.EglBase.Context? = null
    val remoteVideos = androidx.compose.runtime.mutableStateMapOf<String, VideoTrack>()
    var isMuted by mutableStateOf(false)
    var isVideoEnabled by mutableStateOf(false)
    var isSpeaker by mutableStateOf(true)
    var selfRole by mutableStateOf("LISTENER")
    var mediaPath by mutableStateOf("MESH")
    var isRecording by mutableStateOf(false)
    var pinnedMessage by mutableStateOf("")
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    var reactions: List<SpaceReaction> by mutableStateOf(emptyList())
}

class ConferenceService : Service(), MeshRtcSession.Events, ConferenceSignalingClient.Listener, SfuMediaClient.Events {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: ConferenceSignalingClient
    private var mesh: MeshRtcSession? = null
    private var sfu: SfuMediaClient? = null
    private var mediaStarted = false
    private var recordingManager: CallRecordingManager? = null
    private var roomId = ""
    private var userId = ""
    private var startedAsHost = false
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
        manager?.createNotificationChannel(NotificationChannel("red_calls_incoming", getString(com.red.sovereign.R.string.channel_calls_incoming_name), NotificationManager.IMPORTANCE_MAX).apply {
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        })
        signaling = ConferenceSignalingClient(this, TokenStore(this), this)
    }

    private fun startRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                isLooping = true
                play()
            }
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            }
            vibrator?.let { vib ->
                val pattern = longArrayOf(0, 800, 400, 800)
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(pattern, 0)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INVITE -> {
                roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val inviter = intent.getStringExtra(EXTRA_INVITER).orEmpty()
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                ConferenceRuntime.isVideoEnabled = hasVideo
                // مساحة صوتية: المدعو مستمع. مؤتمر فيديو: الجميع مشاركون.
                ConferenceRuntime.isSpeaker = hasVideo
                ConferenceRuntime.selfRole = if (hasVideo) "SPEAKER" else "LISTENER"
                ConferenceRuntime.isMuted = !hasVideo
                ConferenceRuntime.state = ConferenceUiState.Incoming(roomId, inviter, hasVideo, userId)
                showIncomingInvitationNotification(roomId, userId, inviter, hasVideo)
            }
            ACTION_JOIN -> {
                roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                val invitees = intent.getStringArrayExtra(EXTRA_INVITEES)?.toList().orEmpty()
                val asHost = intent.getBooleanExtra(EXTRA_HOST, invitees.isNotEmpty())
                ConferenceRuntime.isVideoEnabled = hasVideo
                startedAsHost = asHost
                ConferenceRuntime.isSpeaker = asHost || hasVideo
                ConferenceRuntime.selfRole = if (asHost) "HOST" else if (hasVideo) "SPEAKER" else "LISTENER"
                ConferenceRuntime.isMuted = !ConferenceRuntime.isSpeaker
                ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                promote()
                scope.launch {
                    registerRoom(!hasVideo, invitees, asHost)
                    signaling.connect(roomId)
                }
            }
            ACTION_LEAVE -> leave()
            ACTION_TOGGLE_MIC -> {
                if (!ConferenceRuntime.isSpeaker) return START_STICKY
                ConferenceRuntime.isMuted = !ConferenceRuntime.isMuted
                mesh?.setMicrophoneEnabled(!ConferenceRuntime.isMuted)
                sfu?.setMicrophoneEnabled(!ConferenceRuntime.isMuted)
            }
            ACTION_TOGGLE_VIDEO -> {
                val enabling = !ConferenceRuntime.isVideoEnabled
                ConferenceRuntime.isVideoEnabled = enabling
                if (enabling && ConferenceRuntime.localVideo == null) {
                    // إعادة محاولة فتح الكاميرا (إذن مُنح لاحقاً أو خلل مؤقت)
                    scope.launch {
                        val ok = if (sfu != null) {
                            sfu!!.retryCamera()
                        } else {
                            mesh?.retryCamera() == true
                        }
                        if (ok) {
                            ConferenceRuntime.localVideo = sfu?.localVideo ?: mesh?.localVideo
                        }
                    }
                } else {
                    mesh?.setCameraEnabled(enabling)
                    sfu?.setCameraEnabled(enabling)
                }
            }
            ACTION_RAISE_HAND -> signaling.raiseHand(roomId, userId)
            ACTION_APPROVE_SPEAKER -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.approveSpeaker(roomId, userId, target)
            }
            ACTION_DEMOTE_LISTENER -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.demoteListener(roomId, userId, target)
            }
            ACTION_SEND_REACTION -> {
                val emoji = intent?.getStringExtra(EXTRA_EMOJI) ?: "👏"
                signaling.sendReaction(roomId, userId, emoji)
                val localReaction = SpaceReaction(userId = userId, emoji = emoji)
                ConferenceRuntime.reactions = (ConferenceRuntime.reactions + localReaction).takeLast(25)
            }
            ACTION_PIN_MESSAGE -> {
                val text = intent?.getStringExtra(EXTRA_PIN_TEXT).orEmpty()
                signaling.pinMessage(roomId, userId, text)
                ConferenceRuntime.pinnedMessage = text
            }
            ACTION_GRANT_COHOST -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.grantCoHost(roomId, userId, target)
            }
            ACTION_REVOKE_COHOST -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.revokeCoHost(roomId, userId, target)
            }
            ACTION_KICK_USER -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.kickUser(roomId, userId, target)
            }
            ACTION_MUTE_USER -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.muteUser(roomId, userId, target)
            }
            ACTION_SET_QUALITY -> {
                val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "AUTO"
                if (quality == "AUDIO") {
                    mesh?.setCameraEnabled(false)
                    sfu?.setCameraEnabled(false)
                } else {
                    mesh?.setCameraEnabled(ConferenceRuntime.isVideoEnabled)
                    sfu?.setCameraEnabled(ConferenceRuntime.isVideoEnabled)
                }
            }
            ACTION_START_RECORDING -> {
                // موافقة صريحة من واجهة المستخدم — لا تُفترض أبداً
                val consent = intent.getBooleanExtra(YounesCallService.EXTRA_CONSENT, false)
                if (recordingManager == null && roomId.isNotBlank()) {
                    recordingManager = CallRecordingManager(this, roomId)
                }
                ConferenceRuntime.isRecording = recordingManager?.start(consentGranted = consent) == true
            }
            ACTION_STOP_RECORDING -> {
                scope.launch { recordingManager?.stop(); recordingManager = null }
                ConferenceRuntime.isRecording = false
            }
        }
        return START_STICKY
    }

    override fun onConnected() {
        scope.launch {
            if (!mediaStarted) {
                mediaStarted = true
                val kind = if (ConferenceRuntime.isVideoEnabled) CallMediaKind.CONFERENCE else CallMediaKind.SPACE
                // SFU أولاً (mediasoup): أفضل للأداء عند نمو الحضور، مع fallback تلقائي للميش
                if (roomId.isNotBlank() && roomId.length in 4..128) {
                    sfu = SfuMediaClient(this@ConferenceService, TokenStore(this@ConferenceService), this@ConferenceService)
                    if (attachSfuWithRetry(sfu!!, roomId)) {
                        sfu?.publish(kind)
                        ConferenceRuntime.mediaPath = "SFU"
                        ConferenceRuntime.eglContext = sfu?.eglContext
                        ConferenceRuntime.localVideo = sfu?.localVideo
                        applyListenerMute()
                        markConferenceReady()
                    } else {
                        sfu?.release(); sfu = null
                        startMesh(kind)
                    }
                } else {
                    startMesh(kind)
                }
            }
            signaling.join(roomId, userId, ConferenceRuntime.isVideoEnabled, ConferenceRuntime.isSpeaker)
        }
    }

    /** attach مع إعادة محاولة قصيرة — يعالج تأخيرات الشبكة عند إصدار التذكرة. */
    private suspend fun attachSfuWithRetry(sfu: SfuMediaClient, roomId: String): Boolean {
        repeat(4) { attempt ->
            if (sfu.attach(roomId)) return true
            if (attempt < 3) kotlinx.coroutines.delay(350)
        }
        return false
    }

    private fun startMesh(kind: CallMediaKind) {
        mesh = MeshRtcSession(this@ConferenceService, userId, this@ConferenceService)
        ConferenceRuntime.mediaPath = "MESH"
        ConferenceRuntime.eglContext = mesh?.eglContext
        scope.launch { mesh?.start(kind) }
        ConferenceRuntime.localVideo = mesh?.localVideo
        applyListenerMute()
        markConferenceReady()
    }

    private fun markConferenceReady() {
        if (ConferenceRuntime.state is ConferenceUiState.Connecting) {
            ConferenceRuntime.state = ConferenceUiState.Active(roomId, System.currentTimeMillis())
            startStatsPolling()
        }
    }

    private fun applyListenerMute() {
        if (ConferenceRuntime.isSpeaker) return
        ConferenceRuntime.isMuted = true
        mesh?.setMicrophoneEnabled(false)
        sfu?.setMicrophoneEnabled(false)
    }

    override fun onSignal(signal: ConferenceSignal) {
        when (signal.type) {
            "OFFER" -> {
                val from = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                signal.payload["sdp"]?.let { mesh?.handleOffer(from, it) }
            }
            "ANSWER" -> {
                val from = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                signal.payload["sdp"]?.let { mesh?.handleAnswer(from, it) }
            }
            "ICE" -> {
                val from = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                mesh?.handleIce(
                    from,
                    IceCandidate(
                        signal.payload["sdpMid"],
                        signal.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0,
                        signal.payload["candidate"].orEmpty()
                    )
                )
            }
            "RAISE_HAND" -> {
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == signal.userId) p.copy(raisedHand = true) else p
                }
                ConferenceRuntime.participants = participantList
            }
            "HOST_CHANGED" -> {
                val next = signal.payload["userId"].orEmpty()
                if (next == userId) {
                    ConferenceRuntime.selfRole = "HOST"
                    ConferenceRuntime.isSpeaker = true
                }
                ConferenceRuntime.participants = ConferenceRuntime.participants.map { p ->
                    if (p.userId == next) p.copy(role = "HOST", isHost = true) else p
                }
            }
            "APPROVE_SPEAKER" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    ConferenceRuntime.isSpeaker = true
                    ConferenceRuntime.selfRole = "SPEAKER"
                    ConferenceRuntime.isMuted = false
                    mesh?.setMicrophoneEnabled(true)
                }
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == target) p.copy(role = "SPEAKER", raisedHand = false) else p
                }
                ConferenceRuntime.participants = participantList
            }
            "DEMOTE_LISTENER" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    ConferenceRuntime.isSpeaker = false
                    ConferenceRuntime.selfRole = "LISTENER"
                    ConferenceRuntime.isMuted = true
                    mesh?.setMicrophoneEnabled(false)
                }
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == target) p.copy(role = "LISTENER", raisedHand = false) else p
                }
                ConferenceRuntime.participants = participantList
            }
            "GRANT_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == target) p.copy(role = "CO_HOST") else p
                }
                ConferenceRuntime.participants = participantList
            }
            "REVOKE_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == target) p.copy(role = "SPEAKER") else p
                }
                ConferenceRuntime.participants = participantList
            }
            "KICK_USER" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    leave()
                } else {
                    ConferenceRuntime.participants = ConferenceRuntime.participants.filter { it.userId != target }
                    ConferenceRuntime.remoteVideos.remove(target)
                    mesh?.detachPeer(target)
                }
            }
            "MUTE_USER" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    ConferenceRuntime.isMuted = true
                    mesh?.setMicrophoneEnabled(false)
                }
            }
            "REACTION" -> {
                val emoji = signal.payload["emoji"] ?: "👏"
                val reaction = SpaceReaction(userId = signal.userId, emoji = emoji)
                ConferenceRuntime.reactions = (ConferenceRuntime.reactions + reaction).takeLast(25)
            }
            "PIN_MESSAGE" -> {
                ConferenceRuntime.pinnedMessage = signal.payload["text"].orEmpty()
            }
        }
    }

    override fun onRoomState(participants: List<ConferenceParticipant>, selfRole: String) {
        ConferenceRuntime.participants = participants
        val role = selfRole.ifBlank { if (startedAsHost) "HOST" else "LISTENER" }
        applySelfRole(role)
        val remotes = participants.map { it.userId }.filter { it.isNotBlank() && it != userId }
        remotes.forEach { mesh?.attachPeer(it) }
        if (!startedAsHost) remotes.forEach { peer ->
            if (MeshNegotiation.shouldOfferTo(peer, userId, isNewcomer = true)) mesh?.offerTo(peer)
        }
    }

    override fun onSelfRole(role: String) {
        if (role.isBlank()) return
        applySelfRole(role)
    }

    private fun applySelfRole(role: String) {
        ConferenceRuntime.selfRole = role
        val speaker = role in setOf("HOST", "CO_HOST", "SPEAKER")
        ConferenceRuntime.isSpeaker = speaker
        if (!speaker) applyListenerMute()
    }

    override fun onParticipantJoined(participant: ConferenceParticipant) {
        val list = ConferenceRuntime.participants.toMutableList()
        list.removeAll { it.userId == participant.userId }
        list.add(participant)
        ConferenceRuntime.participants = list
        if (participant.userId.isNotBlank() && participant.userId != userId) {
            mesh?.attachPeer(participant.userId)
        }
    }

    override fun onParticipantLeft(leftUserId: String) {
        ConferenceRuntime.participants = ConferenceRuntime.participants.filter { it.userId != leftUserId }
        ConferenceRuntime.remoteVideos.remove(leftUserId)
        mesh?.detachPeer(leftUserId)
    }

    /** SFU: غادر العضو غرفة الوسائط — نفس معالجة onParticipantLeft (من صافي أحداث الخادم). */
    override fun onPeerLeft(peerId: String) {
        onParticipantLeft(peerId)
    }

    override fun onDisconnected() {
        when (ConferenceRuntime.state) {
            is ConferenceUiState.Active, is ConferenceUiState.Connecting -> {
                ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                runCatching { signaling.reconnect(roomId) }
            }
            else -> leave()
        }
    }
    override fun onError(message: String) {
        if (message == "UNAUTHORIZED") {
            ConferenceRuntime.state = ConferenceUiState.Error(message)
            scope.launch {
                kotlinx.coroutines.delay(1500)
                leave()
            }
            return
        }
        if (ConferenceRuntime.state is ConferenceUiState.Active || ConferenceRuntime.state is ConferenceUiState.Connecting) {
            runCatching { signaling.reconnect(roomId) }
            return
        }
        ConferenceRuntime.state = ConferenceUiState.Error(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (ConferenceRuntime.state is ConferenceUiState.Error) leave()
        }
    }

    override fun onLocalDescription(peerId: String, description: SessionDescription) {
        if (description.type == SessionDescription.Type.ANSWER) {
            signaling.sendAnswer(roomId, userId, description.description, peerId)
        } else {
            signaling.sendOffer(roomId, userId, description.description, peerId)
        }
    }

    override fun onIceCandidate(peerId: String, candidate: IceCandidate) {
        signaling.sendIce(roomId, userId, candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp ?: "", peerId)
    }

    override fun onRemoteVideo(peerId: String, track: VideoTrack) {
        if (peerId.isNotBlank()) ConferenceRuntime.remoteVideos[peerId] = track
    }

    override fun onNetworkStats(stats: NetworkStats) { ConferenceRuntime.networkStats = stats }

    override fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> markConferenceReady()
            PeerConnection.PeerConnectionState.FAILED -> if (!leaving) mesh?.restartIce()
            else -> Unit
        }
    }

    private suspend fun registerRoom(isSpace: Boolean, invitees: List<String>, asHost: Boolean) {
        if (roomId.isBlank()) return
        val api = AuthorizedApiClient(TokenStore(this))
        if (asHost) {
            val create = org.json.JSONObject()
                .put("roomId", roomId)
                .put("title", if (isSpace) "مساحة صوتية" else "مؤتمر فيديو")
                .put("isSpace", isSpace)
                .put("isPrivate", true)
                .toString()
            api.request("POST", "/api/conference/create", create)
        }
        api.request("POST", "/api/conference/$roomId/join", "{}")
        if (!asHost) return
        val others = invitees.filter { it.isNotBlank() && it != userId }
        if (others.isNotEmpty()) {
            val ids = org.json.JSONArray()
            others.forEach { ids.put(it) }
            api.request("POST", "/api/conference/$roomId/invite", org.json.JSONObject().put("memberIds", ids).toString())
        }
    }

    private var statsJob: kotlinx.coroutines.Job? = null
    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (true) {
                mesh?.pollStats()
                sfu?.pollStats()
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private var leaving = false
    private fun leave() {
        if (leaving) return
        leaving = true
        statsJob?.cancel(); statsJob = null
        val closingRoomId = roomId
        val closingUserId = userId
        if (closingRoomId.isNotBlank()) {
            runCatching { signaling.leave(closingRoomId, closingUserId) }
        }
        runCatching { signaling.close() }
        recordingManager?.let { scope.launch { it.stop() } }
        recordingManager = null
        mesh?.release(); mesh = null
        sfu?.release(); sfu = null
        mediaStarted = false
        ConferenceRuntime.mediaPath = "MESH"
        ConferenceRuntime.isRecording = false
        ConferenceRuntime.selfRole = "LISTENER"
        ConferenceRuntime.state = ConferenceUiState.Idle
        ConferenceRuntime.participants = emptyList()
        ConferenceRuntime.remoteVideos.clear()
        ConferenceRuntime.localVideo = null
        ConferenceRuntime.eglContext = null
        ConferenceRuntime.networkStats = NetworkStats()
        stopRingtone()
        ConferenceRuntime.state = ConferenceUiState.Idle
        ConferenceRuntime.participants = emptyList()
        ConferenceRuntime.localVideo = null
        ConferenceRuntime.eglContext = null
        ConferenceRuntime.remoteVideos.clear()
        ConferenceRuntime.reactions = emptyList()
        ConferenceRuntime.pinnedMessage = ""
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        if (closingRoomId.isNotBlank()) {
            scope.launch {
                withTimeoutOrNull(3000) {
                    runCatching {
                        val api = AuthorizedApiClient(TokenStore(this@ConferenceService))
                        api.request("POST", "/api/conference/$closingRoomId/leave", "{}")
                        if (startedAsHost) {
                            runCatching { api.request("POST", "/api/conference/$closingRoomId/close", "{}") }
                        }
                    }
                }
                withContext(Dispatchers.Main.immediate) { stopSelf() }
            }
        } else {
            stopSelf()
        }
    }

    private fun showIncomingInvitationNotification(roomId: String, userId: String, inviter: String, isVideo: Boolean) {
        // إذا كان المستخدم عطّل إشعارات المكالمات: إشعار صامت فقط (لإبقاء الخدمة حية) دون رنين/تنبيه
        if (!com.red.sovereign.settings.SettingsRuntime.current.callNotifications) {
            val silent = NotificationCompat.Builder(this, "red_calls")
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle("دعوة إلى مؤتمر " + (if (isVideo) "فيديو" else "صوتي"))
                .setContentText(inviter.ifBlank { "مجموعة يونس" })
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .build()
            ServiceCompat.startForeground(this, 7402, silent, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            return
        }

        startRingtone()

        val acceptIntent = Intent(this, ConferenceService::class.java).apply {
            action = ACTION_JOIN
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_VIDEO, isVideo)
            putExtra(EXTRA_HOST, false)
        }
        val acceptPending = PendingIntent.getService(this, 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val rejectIntent = Intent(this, ConferenceService::class.java).apply {
            action = ACTION_LEAVE
        }
        val rejectPending = PendingIntent.getService(this, 2, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notif = NotificationCompat.Builder(this, "red_calls_incoming")
            .setSmallIcon(if (isVideo) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle("مكالمة جماعية واردة • " + (if (isVideo) "فيديو" else "صوت"))
            .setContentText("دعوة للانضمام من: ${inviter.ifBlank { "مجموعة يونس" }}")
            .setContentIntent(mainIntent)
            .setFullScreenIntent(mainIntent, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true)
            .addAction(0, "انضمام", acceptPending)
            .addAction(0, "رفض", rejectPending)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isVideo) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        ServiceCompat.startForeground(this, 7402, notif, type)
    }

    private fun promote() {
        stopRingtone()
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val isVideo = ConferenceRuntime.isVideoEnabled
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(if (isVideo) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle(if (isVideo) "مؤتمر فيديو يونس" else "مؤتمر يونس")
            .setContentText("${ConferenceRuntime.participants.size} مشارك • جارٍ الاتصال بالمؤتمر...")
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "مغادرة", PendingIntent.getService(this, 1, Intent(this, ConferenceService::class.java).setAction(ACTION_LEAVE), PendingIntent.FLAG_IMMUTABLE))
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isVideo) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, 7402, notif, type)
    }

    override fun onDestroy() {
        stopRingtone()
        leave()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_INVITE = "com.red.sovereign.conference.INVITE"
        const val ACTION_JOIN = "com.red.sovereign.conference.JOIN"
        const val ACTION_LEAVE = "com.red.sovereign.conference.LEAVE"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.conference.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.conference.TOGGLE_VIDEO"
        const val ACTION_SET_QUALITY = "com.red.sovereign.conference.SET_QUALITY"
        const val ACTION_RAISE_HAND = "com.red.sovereign.conference.RAISE_HAND"
        const val ACTION_APPROVE_SPEAKER = "com.red.sovereign.conference.APPROVE_SPEAKER"
        const val ACTION_DEMOTE_LISTENER = "com.red.sovereign.conference.DEMOTE_LISTENER"
        const val ACTION_GRANT_COHOST = "com.red.sovereign.conference.GRANT_COHOST"
        const val ACTION_REVOKE_COHOST = "com.red.sovereign.conference.REVOKE_COHOST"
        const val ACTION_KICK_USER = "com.red.sovereign.conference.KICK_USER"
        const val ACTION_MUTE_USER = "com.red.sovereign.conference.MUTE_USER"
        const val ACTION_SEND_REACTION = "com.red.sovereign.conference.SEND_REACTION"
        const val ACTION_PIN_MESSAGE = "com.red.sovereign.conference.PIN_MESSAGE"
        const val ACTION_START_RECORDING = "com.red.sovereign.conference.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.red.sovereign.conference.STOP_RECORDING"

        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_INVITER = "inviter"
        const val EXTRA_VIDEO = "video"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_TARGET_USER_ID = "target_user_id"
        const val EXTRA_EMOJI = "emoji"
        const val EXTRA_PIN_TEXT = "pin_text"
        const val EXTRA_INVITEES = "invitees"
        const val EXTRA_HOST = "as_host"

        fun grantCoHost(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_GRANT_COHOST
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun kickUser(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_KICK_USER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun muteUser(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_MUTE_USER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun raiseHand(context: Context) {
            val intent = Intent(context, ConferenceService::class.java).setAction(ACTION_RAISE_HAND)
            ContextCompat.startForegroundService(context, intent)
        }

        fun approveSpeaker(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_APPROVE_SPEAKER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendReaction(context: Context, emoji: String = "👏") {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_SEND_REACTION
                putExtra(EXTRA_EMOJI, emoji)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pinMessage(context: Context, text: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_PIN_MESSAGE
                putExtra(EXTRA_PIN_TEXT, text)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun invite(context: Context, roomId: String, userId: String, inviterName: String, video: Boolean) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_INVITE
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_INVITER, inviterName)
                putExtra(EXTRA_VIDEO, video)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun join(context: Context, roomId: String, userId: String, video: Boolean, inviteRedIds: List<String> = emptyList(), asHost: Boolean = inviteRedIds.isNotEmpty()) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_JOIN
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_VIDEO, video)
                putExtra(EXTRA_HOST, asHost)
                if (inviteRedIds.isNotEmpty()) putExtra(EXTRA_INVITEES, inviteRedIds.toTypedArray())
            }
            ContextCompat.startForegroundService(context, intent)
        }
        fun leave(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ConferenceService::class.java).setAction(ACTION_LEAVE))
        }
        fun action(context: Context, act: String, consent: Boolean = false) {
            val intent = Intent(context, ConferenceService::class.java).setAction(act)
            if (consent) intent.putExtra(YounesCallService.EXTRA_CONSENT, true)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
