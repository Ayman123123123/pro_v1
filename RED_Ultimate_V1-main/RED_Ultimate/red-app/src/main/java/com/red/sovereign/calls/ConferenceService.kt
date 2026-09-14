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
import kotlinx.coroutines.isActive
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
    /** غرفة الانتظار: المضيف فعّل اللوبي وننتظر موافقته على الدخول (Zoom-style). */
    data class WaitingApproval(val roomId: String) : ConferenceUiState
}

data class SpaceReaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userId: String,
    val emoji: String = "👏",
    val timestamp: Long = System.currentTimeMillis()
)

object ConferenceRuntime {
    /** DoD (المرحلة 7): سقف بلاطات الفيديو — 12 بدل 25 (تبذير نطاق/ذاكرة على الجوال). */
    const val MAX_VIDEO_TILES = 12
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
    /** قفل الغرفة (المضيف): لا انضمام جديد — يُزامَن مع الخادم عبر ACTION_TOGGLE_LOCK. */
    var isRoomLocked by mutableStateOf(false)
    var pinnedMessage by mutableStateOf("")
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    var reactions: List<SpaceReaction> by mutableStateOf(emptyList())
    var myUserId by mutableStateOf("")
    /** المتكلمون الآن (userIds) — من SFU activeSpeaker أو مستويات Mesh. */
    var speakingPeers: Set<String> by mutableStateOf(emptySet())
    /** العضو/البث المثبت (Spotlight / Pinned Stream) */
    var pinnedParticipantId: String? by mutableStateOf(null)
    /** حالة مشاركة الشاشة */
    var isScreenSharing by mutableStateOf(false)
    var remoteScreenShareTrack: VideoTrack? by mutableStateOf(null)
    var remoteScreenSharePeerId by mutableStateOf("")
}

class ConferenceService : Service(), MeshRtcSession.Events, ConferenceSignalingClient.Listener, SfuMediaClient.Events {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectAttempt = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null
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

    /** G13 فصل الغرف: المؤتمر — فارغ يبقى فارغاً، legacy يُقبل، بادئة مخالفة تُطبَّع CONF_ بلا كسر. */
    private fun resolveRoomIdForJoin(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        val k = RoomSeparationPolicy.kindOf(v)
        if (k == RoomSeparationPolicy.RoomKind.CONF || k == RoomSeparationPolicy.RoomKind.LEGACY) {
            return RoomSeparationPolicy.normalizeRoomId(v)
        }
        val core = v.substringAfter("_").ifBlank { v }.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
        if (core.length < 4) return RoomSeparationPolicy.normalizeRoomId(null)
        if (RoomSeparationPolicy.isValidRoomId(core)) return RoomSeparationPolicy.PREFIX_CONF + core
        return RoomSeparationPolicy.normalizeRoomId(core)
    }

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
        } catch (e: Exception) { android.util.Log.w("ConferenceService", "startRingtone op failed", e) }
    }

    private fun stopRingtone() {
        try { ringtone?.stop() } catch (e: Exception) { android.util.Log.w("ConferenceService", "stop ringtone op failed", e) }
        ringtone = null
        try { vibrator?.cancel() } catch (e: Exception) { android.util.Log.w("ConferenceService", "cancel vibrator op failed", e) }
        vibrator = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INVITE -> {
                roomId = resolveRoomIdForJoin(intent.getStringExtra(EXTRA_ROOM_ID))
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val inviter = intent.getStringExtra(EXTRA_INVITER).orEmpty()
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                ConferenceRuntime.isVideoEnabled = hasVideo
                ConferenceRuntime.isSpeaker = hasVideo
                ConferenceRuntime.selfRole = if (hasVideo) "SPEAKER" else "LISTENER"
                ConferenceRuntime.isMuted = !hasVideo
                ConferenceRuntime.state = ConferenceUiState.Incoming(roomId, inviter, hasVideo, userId)
                showIncomingInvitationNotification(roomId, userId, inviter, hasVideo)
            }
            ACTION_JOIN -> {
                roomId = resolveRoomIdForJoin(intent.getStringExtra(EXTRA_ROOM_ID))
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                ConferenceRuntime.myUserId = userId
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                val invitees = intent.getStringArrayExtra(EXTRA_INVITEES)?.toList().orEmpty()
                val asHost = intent.getBooleanExtra(EXTRA_HOST, invitees.isNotEmpty())
                val roomTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val roomPrivate = intent.getBooleanExtra(EXTRA_PRIVATE, true)
                val roomDesc = intent.getStringExtra(EXTRA_DESC).orEmpty()
                val roomPass = intent.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.isNotBlank() }
                val joinPass = intent.getStringExtra(EXTRA_JOIN_PASSWORD)?.takeIf { it.isNotBlank() }
                ConferenceRuntime.isVideoEnabled = hasVideo
                startedAsHost = asHost
                ConferenceRuntime.isSpeaker = asHost || hasVideo
                ConferenceRuntime.selfRole = if (asHost) "HOST" else if (hasVideo) "SPEAKER" else "LISTENER"
                ConferenceRuntime.isMuted = !ConferenceRuntime.isSpeaker
                ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                promote()
                scope.launch {
                    registerRoom(!hasVideo, invitees, asHost, roomTitle, roomPrivate, roomDesc, roomPass, joinPass)
                    signaling.connect(roomId)
                }
            }
            ACTION_LEAVE -> leave()
            ACTION_ACCEPT_INVITE -> {
                roomId = resolveRoomIdForJoin(intent.getStringExtra(EXTRA_ROOM_ID))
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, true)
                if (intent.hasExtra(EXTRA_USER_ID)) {
                    userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                    ConferenceRuntime.myUserId = userId
                }
                if (roomId.isNotBlank()) {
                    stopRingtone()
                    startedAsHost = false
                    ConferenceRuntime.isVideoEnabled = hasVideo
                    ConferenceRuntime.isSpeaker = hasVideo
                    ConferenceRuntime.selfRole = if (hasVideo) "SPEAKER" else "LISTENER"
                    ConferenceRuntime.isMuted = !hasVideo
                    ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                    promote()
                    scope.launch {
                        registerRoom(!hasVideo, emptyList(), false)
                        signaling.connect(roomId)
                    }
                }
            }
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
                    scope.launch {
                        val ok = sfu?.retryCamera() ?: mesh?.retryCamera() == true
                        if (ok) {
                            ConferenceRuntime.localVideo = sfu?.localVideo ?: mesh?.localVideo
                        }
                    }
                } else {
                    mesh?.setCameraEnabled(enabling)
                    sfu?.setCameraEnabled(enabling)
                    if (enabling) {
                        ConferenceRuntime.localVideo = sfu?.localVideo ?: mesh?.localVideo
                    }
                }
            }
            ACTION_START_SCREEN_SHARE -> {
                val projectionData = intent.getParcelableExtra<Intent>(EXTRA_MEDIA_PROJECTION_DATA)
                if (projectionData != null && !ConferenceRuntime.isScreenSharing) {
                    scope.launch {
                        val ok = runCatching {
                            sfu?.startScreenShare(projectionData)
                                ?: mesh?.startScreenShare(projectionData)?.let { true }
                                ?: false
                        }.getOrDefault(false)
                        if (ok) {
                            ConferenceRuntime.isScreenSharing = true
                            runCatching { signaling.sendScreenShare(roomId, userId, true) }
                        }
                    }
                }
            }
            ACTION_STOP_SCREEN_SHARE -> {
                if (ConferenceRuntime.isScreenSharing) {
                    ConferenceRuntime.isScreenSharing = false
                    scope.launch {
                        runCatching { sfu?.stopScreenShare() }
                        runCatching { mesh?.stopScreenShare() }
                        runCatching { signaling.sendScreenShare(roomId, userId, false) }
                    }
                }
            }
            ACTION_PIN_PARTICIPANT -> {
                val targetId = intent.getStringExtra(EXTRA_PINNED_PARTICIPANT)
                ConferenceRuntime.pinnedParticipantId = if (ConferenceRuntime.pinnedParticipantId == targetId) null else targetId
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
            ACTION_MUTE_ALL -> signaling.muteAll(roomId, userId)
            // ─────────── غرفة الانتظار (Lobby) ───────────
            ACTION_LOBBY_SET -> signaling.setLobby(roomId, userId, intent?.getBooleanExtra(EXTRA_LOBBY_ENABLED, false) == true)
            ACTION_LOBBY_APPROVE -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.approveLobby(roomId, userId, target)
            }
            ACTION_LOBBY_DENY -> {
                val target = intent?.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (target.isNotBlank()) signaling.denyLobby(roomId, userId, target)
            }
            ACTION_LOBBY_APPROVE_ALL -> signaling.approveAllLobby(roomId, userId)
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
            ACTION_TOGGLE_LOCK -> {
                if (roomId.isBlank()) return START_STICKY
                scope.launch {
                    val target = !ConferenceRuntime.isRoomLocked
                    val ok = runCatching {
                        val api = AuthorizedApiClient(TokenStore(this@ConferenceService))
                        val res = api.request("POST", "/api/conference/$roomId/lock",
                            org.json.JSONObject().put("locked", target).toString())
                        res is com.red.sovereign.auth.ApiResult.Success
                    }.getOrDefault(false)
                    if (ok) ConferenceRuntime.isRoomLocked = target
                }
            }
        }
        return START_STICKY
    }

    override fun onConnected() {
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch {
            if (!mediaStarted) {
                mediaStarted = true
                val kind = if (ConferenceRuntime.isVideoEnabled) CallMediaKind.CONFERENCE else CallMediaKind.SPACE
                if (roomId.isNotBlank() && roomId.length in 4..128) {
                    sfu = SfuMediaClient(this@ConferenceService, TokenStore(this@ConferenceService), this@ConferenceService)
                    val sfuClient = sfu
                    if (sfuClient != null && attachSfuWithRetry(sfuClient, roomId)) {
                        sfu?.publish(kind)
                        ConferenceRuntime.mediaPath = "SFU"
                        ConferenceRuntime.eglContext = sfu?.eglContext
                        ConferenceRuntime.localVideo = sfu?.localVideo
                        applyListenerMute()
                        markConferenceReady()
                    } else {
                        android.util.Log.w("ConferenceService", "SFU_UNAVAILABLE — fallback to MESH")
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
        scope.launch {
            mesh?.start(kind)
            ConferenceRuntime.eglContext = mesh?.eglContext
            ConferenceRuntime.localVideo = mesh?.localVideo
            applyListenerMute()
            markConferenceReady()
        }
    }

    private fun markConferenceReady() {
        if (ConferenceRuntime.state is ConferenceUiState.Connecting) {
            ConferenceRuntime.state = ConferenceUiState.Active(roomId, System.currentTimeMillis())
            startStatsPolling()
            ensureNetworkWatcher()
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
                // الخادم يُبثّ الرفع والخفض على النوع نفسه (payload lowered) —
                // كان الخفض يُعامَل كورفع فيبقى اليد مرفوعة في واجهة الجميع.
                val raised = signal.payload["lowered"] != "true"
                ConferenceRuntime.participants = ConferenceRuntime.participants.map { p ->
                    if (p.userId == signal.userId) p.copy(raisedHand = raised) else p
                }
            }
            "CLEAR_ALL_HANDS" -> {
                // المضيف يمسح كل الأيدي المرفوعة — النوع أصبح مدعوماً خادمياً.
                ConferenceRuntime.participants = ConferenceRuntime.participants.map { it.copy(raisedHand = false) }
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
                    if (sfu != null) {
                        // LEGENDARY Phase 7: ترقية على SFU — عميل جديد بتذكرة SPEAKER (حق نشر)
                        // ثم نشر صوتي (SPACE بلا كاميرا — المستمع انضم بلا فيديو أصلاً).
                        scope.launch {
                            val fresh = SfuMediaClient(this@ConferenceService, TokenStore(this@ConferenceService), this@ConferenceService)
                            runCatching { sfu?.release() }
                            sfu = fresh
                            ConferenceRuntime.eglContext = fresh.eglContext
                            val kind = if (ConferenceRuntime.isVideoEnabled) CallMediaKind.CONFERENCE else CallMediaKind.SPACE
                            if (attachSfuWithRetry(fresh, roomId) && fresh.publish(kind)) {
                                ConferenceRuntime.localVideo = fresh.localVideo
                                fresh.setMicrophoneEnabled(true)
                            }
                        }
                    }
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
                    if (sfu != null) {
                        // LEGENDARY Phase 7: إسقاط النشر — عميل جديد بتذكرة مستمع (استهلاك فقط)؛
                        // فشل SFU ← mesh مستمع + إعادة إعلان (ROOM_STATE يستدر العروض بقاعدة ثابتة).
                        scope.launch {
                            val fresh = SfuMediaClient(this@ConferenceService, TokenStore(this@ConferenceService), this@ConferenceService)
                            runCatching { sfu?.release() }
                            sfu = fresh
                            ConferenceRuntime.eglContext = fresh.eglContext
                            ConferenceRuntime.localVideo = null
                            if (!attachSfuWithRetry(fresh, roomId)) {
                                sfu = null
                                val kind = if (ConferenceRuntime.isVideoEnabled) CallMediaKind.CONFERENCE else CallMediaKind.SPACE
                                startMesh(kind)
                                signaling.join(roomId, userId, ConferenceRuntime.isVideoEnabled, false)
                            }
                        }
                    }
                }
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == target) p.copy(role = "LISTENER", raisedHand = false) else p
                }
                ConferenceRuntime.participants = participantList
            }
            "GRANT_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                // LEGENDARY Phase 7: كان الدور الذاتي لا يتحدث — أدوات المضيف (قفل/كتم الكل) لا تظهر.
                if (target == userId) {
                    ConferenceRuntime.selfRole = "CO_HOST"
                    ConferenceRuntime.isSpeaker = true
                }
                val participantList = ConferenceRuntime.participants.map { p ->
                    if (p.userId == target) p.copy(role = "CO_HOST") else p
                }
                ConferenceRuntime.participants = participantList
            }
            "REVOKE_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                // LEGENDARY Phase 7 (مرآة GRANT_COHOST): إسقاط الدور الذاتي للمضيف المشارك.
                if (target == userId && ConferenceRuntime.selfRole == "CO_HOST") {
                    ConferenceRuntime.selfRole = "SPEAKER"
                }
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
                    // LEGENDARY Phase 7: كان المتكلم على SFU يبقى حياً بعد كتم المضيف.
                    sfu?.setMicrophoneEnabled(false)
                }
            }
            "MUTE_ALL" -> {
                // الخادم يستثني المرسل من roomMuted لكن يبثّ للجميع بمن فيهم هو.
                if (ConferenceRuntime.selfRole != "HOST") {
                    ConferenceRuntime.isMuted = true
                    mesh?.setMicrophoneEnabled(false)
                    sfu?.setMicrophoneEnabled(false)
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
            "SCREEN_SHARE_START" -> {
                if (signal.userId.isNotBlank() && signal.userId != userId) {
                    ConferenceRuntime.remoteScreenSharePeerId = signal.userId
                }
            }
            "SCREEN_SHARE_STOP" -> {
                if (ConferenceRuntime.remoteScreenSharePeerId == signal.userId) {
                    ConferenceRuntime.remoteScreenSharePeerId = ""
                }
            }
            // ─────────── غرفة الانتظار (Lobby) ───────────
            "LOBBY_WAITING" -> {
                // الخادم احتجزنا في اللوبي — لا ROOM_STATE قبل الموافقة فلا وسائط تبدأ.
                ConferenceRuntime.state = ConferenceUiState.WaitingApproval(roomId)
            }
            "LOBBY_APPROVED" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId && ConferenceRuntime.state is ConferenceUiState.WaitingApproval) {
                    // ROOM_STATE الكامل يصل بعدها مباشرة فيكمل مسار الدخول القياسي.
                    ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                }
                ConferenceRuntime.waitingUsers = ConferenceRuntime.waitingUsers - target
            }
            "LOBBY_DENIED" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    ConferenceRuntime.state = ConferenceUiState.Error("رفض المضيف طلب الدخول")
                    scope.launch {
                        kotlinx.coroutines.delay(2500)
                        if (ConferenceRuntime.state is ConferenceUiState.Error) leave()
                    }
                }
                ConferenceRuntime.waitingUsers = ConferenceRuntime.waitingUsers - target
            }
            "LOBBY_LEFT" -> {
                // منتظر قطع اتصاله بنفسه — أسقط سطره من قائمة المضيف.
                val gone = signal.payload["targetUserId"].orEmpty().ifBlank { signal.userId }
                ConferenceRuntime.waitingUsers = ConferenceRuntime.waitingUsers - gone
            }
            "LOBBY_REQUEST" -> {
                val waitingId = signal.payload["userId"].orEmpty().ifBlank { signal.userId }
                if (waitingId.isNotBlank() &&
                    ConferenceRuntime.selfRole in setOf("HOST", "CO_HOST") &&
                    waitingId !in ConferenceRuntime.waitingUsers
                ) {
                    ConferenceRuntime.waitingUsers = ConferenceRuntime.waitingUsers + waitingId
                }
            }
            "LOBBY_STATE" -> {
                ConferenceRuntime.lobbyEnabled = signal.payload["enabled"] == "true"
            }
            "ERROR", "ROOM_STATE" -> Unit
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
        if (ConferenceRuntime.pinnedParticipantId == leftUserId) {
            ConferenceRuntime.pinnedParticipantId = null
        }
        mesh?.detachPeer(leftUserId)
    }

    override fun onPeerLeft(peerId: String) {
        onParticipantLeft(peerId)
        ConferenceRuntime.speakingPeers = ConferenceRuntime.speakingPeers - peerId
    }

    override fun onActiveSpeaker(peerId: String) {
        ConferenceRuntime.speakingPeers = if (peerId.isBlank()) emptySet() else setOf(peerId)
    }

    override fun onPeerAudioLevel(peerId: String, level: Float) {
        if (peerId.isBlank()) return
        val current = ConferenceRuntime.speakingPeers
        val shouldSpeak = level >= GroupCallService.SPEAKING_LEVEL_THRESHOLD
        val next = if (shouldSpeak) current + peerId else current - peerId
        if (next != current) {
            ConferenceRuntime.speakingPeers = next
        }
    }

    override fun onRemoteAudio(track: org.webrtc.AudioTrack) {
        runCatching { track.setEnabled(true) }
    }

    override fun onDisconnected() {
        when (ConferenceRuntime.state) {
            is ConferenceUiState.Active, is ConferenceUiState.Connecting -> {
                ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                scheduleSignalingReconnect()
            }
            // منتظر في اللوبي وانقطع الاتصال؟ أعد المحاولة — سيعود لقائمة الانتظار
            // ويصل المضيف طلب جديد (الخادم أسقط جلسته القديمة بـLOBBY_LEFT).
            is ConferenceUiState.WaitingApproval -> scheduleSignalingReconnect()
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
            scheduleSignalingReconnect()
            return
        }
        ConferenceRuntime.state = ConferenceUiState.Error(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (ConferenceRuntime.state is ConferenceUiState.Error) leave()
        }
    }

    private fun scheduleSignalingReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (reconnectAttempt < 5 &&
                (ConferenceRuntime.state is ConferenceUiState.Active || ConferenceRuntime.state is ConferenceUiState.Connecting)) {
                val base = (1000L * (1 shl reconnectAttempt.coerceAtMost(5))).coerceAtMost(30_000L)
                reconnectAttempt++
                kotlinx.coroutines.delay((Math.random() * base).toLong().coerceAtLeast(300L))
                if (ConferenceRuntime.state !is ConferenceUiState.Active &&
                    ConferenceRuntime.state !is ConferenceUiState.Connecting) break
                runCatching { signaling.reconnect(roomId) }
                kotlinx.coroutines.delay(4_000)
            }
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

    override fun onNetworkStats(stats: NetworkStats) {
        ConferenceRuntime.networkStats = stats
        runCatching { CallTelemetry.onNetworkStats(stats) }
        runCatching {
            CallQualityManager.update(stats.rttMs.toInt(), stats.packetLossPercent.toFloat(), stats.availableBitrateKbps.toInt().coerceAtLeast(stats.bandwidthKbps.toInt()), stats.framesPerSecond)
        }
    }

    override fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> markConferenceReady()
            PeerConnection.PeerConnectionState.FAILED -> if (!leaving) {
                updateNetworkNotification("انقطع مسار عضو — جارٍ إعادة الضبط…")
                mesh?.restartIce()
            }
            else -> Unit
        }
    }

    private suspend fun registerRoom(isSpace: Boolean, invitees: List<String>, asHost: Boolean, title: String = "", isPrivate: Boolean = true, description: String = "", password: String? = null, joinPassword: String? = null) {
        if (roomId.isBlank()) return
        val api = AuthorizedApiClient(TokenStore(this))
        if (asHost) {
            val safeTitle = title.trim().ifBlank { if (isSpace) "مساحة صوتية" else "مؤتمر فيديو" }
            val create = org.json.JSONObject()
                .put("roomId", roomId)
                .put("title", safeTitle.take(60))
                .put("description", description.trim().take(200))
                .put("isSpace", isSpace)
                .put("isPrivate", isPrivate)
                .apply { if (!password.isNullOrBlank()) put("password", password) }
                .toString()
            api.request("POST", "/api/conference/create", create)
        }
        val joinBody = if (!asHost && !joinPassword.isNullOrBlank()) {
            org.json.JSONObject().put("password", joinPassword).toString()
        } else "{}"
        api.request("POST", "/api/conference/$roomId/join", joinBody)
        if (!asHost) return
        val others = invitees.filter { it.isNotBlank() && it != userId }
        if (others.isNotEmpty()) {
            val ids = org.json.JSONArray()
            others.forEach { ids.put(it) }
            api.request("POST", "/api/conference/$roomId/invite", org.json.JSONObject().put("memberIds", ids).toString())
        }
    }

    private var statsJob: kotlinx.coroutines.Job? = null
    private var networkWatcher: NetworkChangeWatcher? = null
    private fun ensureNetworkWatcher() {
        if (networkWatcher == null) {
            networkWatcher = NetworkChangeWatcher(this) {
                if (!leaving && ConferenceRuntime.state is ConferenceUiState.Active) {
                    mesh?.restartIce()
                    updateNetworkNotification("تبديل الشبكة — إعادة ضبط المسار…")
                }
            }.also { it.start() }
        }
    }

    private fun updateNetworkNotification(text: String) {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val isVideo = ConferenceRuntime.isVideoEnabled
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(if (isVideo) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle(if (isVideo) "مؤتمر فيديو يونس" else "مؤتمر يونس")
            .setContentText(text)
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "مغادرة", CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_CONFERENCE_LEAVE, CallNotificationActionReceiver.CALL_TYPE_CONFERENCE, 7402, callId = roomId, myUserId = userId, hostId = "", isVideo = isVideo))
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(7402, notif) }
    }

    private fun stopNetworkWatcher() {
        networkWatcher?.stop(); networkWatcher = null
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                runCatching { mesh?.pollStats() }
                runCatching { sfu?.pollStats() }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private var leaving = false
    private fun leave() {
        if (leaving) return
        leaving = true
        statsJob?.cancel(); statsJob = null
        stopNetworkWatcher()
        val closingRoomId = roomId
        val closingUserId = userId
        saveConferenceCallLogLocally()
        if (closingRoomId.isNotBlank()) {
            runCatching { signaling.leave(closingRoomId, closingUserId) }
        }
        runCatching { signaling.close() }
        recordingManager?.let { scope.launch { it.stop() } }
        recordingManager = null
        mesh?.release(); mesh = null
        sfu?.release(); sfu = null
        mediaStarted = false
        stopRingtone()

        ConferenceRuntime.state = ConferenceUiState.Idle
        ConferenceRuntime.participants = emptyList()
        ConferenceRuntime.localVideo = null
        ConferenceRuntime.eglContext = null
        ConferenceRuntime.remoteVideos.clear()
        ConferenceRuntime.reactions = emptyList()
        ConferenceRuntime.speakingPeers = emptySet()
        ConferenceRuntime.pinnedMessage = ""
        ConferenceRuntime.pinnedParticipantId = null
        ConferenceRuntime.isScreenSharing = false
        ConferenceRuntime.remoteScreenShareTrack = null
        ConferenceRuntime.remoteScreenSharePeerId = ""
        ConferenceRuntime.isRecording = false
        ConferenceRuntime.selfRole = "LISTENER"

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

    private fun saveConferenceCallLogLocally() {
        if (roomId.isBlank()) return
        val startedAt = (ConferenceRuntime.state as? ConferenceUiState.Active)?.startedAt ?: 0L
        val durationMs = if (startedAt > 0L) (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) else 0L
        val cipher = CallLogCipher()
        val log = com.red.sovereign.core.database.CallLogEntity(
            id = "$roomId-${startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()}",
            peerId = cipher.encryptPeerId(roomId),
            peerLabel = cipher.encryptLabel(roomId),
            type = if (ConferenceRuntime.isVideoEnabled) "CONFERENCE" else "SPACE",
            direction = if (startedAsHost) "OUTGOING" else "INCOMING",
            route = "RED",
            status = if (durationMs > 0L) "ENDED" else "FAILED",
            timestamp = startedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            durationMs = durationMs,
            answeredAt = startedAt.takeIf { it > 0L && durationMs > 0L },
            endedAt = (startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()) + durationMs
        )
        scope.launch { runCatching { com.red.sovereign.core.database.LocalRepository(this@ConferenceService).saveCallLog(log) } }
    }

    private fun showIncomingInvitationNotification(roomId: String, userId: String, inviter: String, isVideo: Boolean) {
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

        val acceptPi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_ACCEPT, CallNotificationActionReceiver.CALL_TYPE_CONFERENCE, 7402, callId = roomId, myUserId = userId, hostId = "", isVideo = isVideo)
        val rejectPi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_DECLINE, CallNotificationActionReceiver.CALL_TYPE_CONFERENCE, 7402, callId = roomId, myUserId = userId, hostId = "", isVideo = isVideo)

        val fullScreen = PendingIntent.getActivity(
            this, 7402,
            Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_CONFERENCE)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_INVITER, inviter)
                putExtra(EXTRA_VIDEO, isVideo)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching { IncomingCallActivity.launchConference(this, roomId, userId, inviter, isVideo) }

        val notif = NotificationCompat.Builder(this, "red_calls_incoming")
            .setSmallIcon(if (isVideo) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle("مكالمة جماعية واردة • " + (if (isVideo) "فيديو" else "صوت"))
            .setContentText("دعوة للانضمام من: ${inviter.ifBlank { "مجموعة يونس" }}")
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true)
            .addAction(NotificationCompat.Action.Builder(0, "رفض", rejectPi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).build())
            .addAction(NotificationCompat.Action.Builder(0, "انضمام", acceptPi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL).build())
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
            .addAction(0, "مغادرة", CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_CONFERENCE_LEAVE, CallNotificationActionReceiver.CALL_TYPE_CONFERENCE, 7402, callId = roomId, myUserId = userId, hostId = "", isVideo = isVideo))
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
        const val ACTION_ACCEPT_INVITE = "com.red.sovereign.conference.ACCEPT_INVITE"
        const val ACTION_INVITE = "com.red.sovereign.conference.INVITE"
        const val ACTION_JOIN = "com.red.sovereign.conference.JOIN"
        const val ACTION_LEAVE = "com.red.sovereign.conference.LEAVE"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.conference.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.conference.TOGGLE_VIDEO"
        const val ACTION_START_SCREEN_SHARE = "com.red.sovereign.conference.START_SCREEN_SHARE"
        const val ACTION_STOP_SCREEN_SHARE = "com.red.sovereign.conference.STOP_SCREEN_SHARE"
        const val ACTION_PIN_PARTICIPANT = "com.red.sovereign.conference.PIN_PARTICIPANT"
        const val ACTION_SET_QUALITY = "com.red.sovereign.conference.SET_QUALITY"
        const val ACTION_RAISE_HAND = "com.red.sovereign.conference.RAISE_HAND"
        const val ACTION_APPROVE_SPEAKER = "com.red.sovereign.conference.APPROVE_SPEAKER"
        const val ACTION_DEMOTE_LISTENER = "com.red.sovereign.conference.DEMOTE_LISTENER"
        const val ACTION_GRANT_COHOST = "com.red.sovereign.conference.GRANT_COHOST"
        const val ACTION_REVOKE_COHOST = "com.red.sovereign.conference.REVOKE_COHOST"
        const val ACTION_KICK_USER = "com.red.sovereign.conference.KICK_USER"
        const val ACTION_MUTE_USER = "com.red.sovereign.conference.MUTE_USER"
        const val ACTION_MUTE_ALL = "com.red.sovereign.conference.MUTE_ALL"
        const val ACTION_SEND_REACTION = "com.red.sovereign.conference.SEND_REACTION"
        const val ACTION_PIN_MESSAGE = "com.red.sovereign.conference.PIN_MESSAGE"
        const val ACTION_START_RECORDING = "com.red.sovereign.conference.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.red.sovereign.conference.STOP_RECORDING"
        const val ACTION_TOGGLE_LOCK = "com.red.sovereign.conference.TOGGLE_LOCK"

        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_INVITER = "inviter"
        const val EXTRA_VIDEO = "video"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_TARGET_USER_ID = "target_user_id"
        const val EXTRA_EMOJI = "emoji"
        const val EXTRA_PIN_TEXT = "pin_text"
        const val EXTRA_PINNED_PARTICIPANT = "pinned_participant"
        const val EXTRA_MEDIA_PROJECTION_DATA = "media_projection_data"
        const val EXTRA_INVITEES = "invitees"
        const val EXTRA_HOST = "as_host"
        const val EXTRA_TITLE = "room_title"
        const val EXTRA_PRIVATE = "room_private"
        const val EXTRA_DESC = "room_desc"
        const val EXTRA_PASSWORD = "room_password"
        const val EXTRA_JOIN_PASSWORD = "join_password"

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

        fun muteAll(context: Context) {
            val intent = Intent(context, ConferenceService::class.java).setAction(ACTION_MUTE_ALL)
            ContextCompat.startForegroundService(context, intent)
        }

        /** غرفة الانتظار: تفعيل/إيقاف (مضيف) — الإيقاف يقبل كل المنتظرين تلقائياً. */
        fun setLobby(context: Context, enabled: Boolean) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_LOBBY_SET
                putExtra(EXTRA_LOBBY_ENABLED, enabled)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun approveWaiting(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_LOBBY_APPROVE
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun denyWaiting(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_LOBBY_DENY
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun approveAllWaiting(context: Context) {
            val intent = Intent(context, ConferenceService::class.java).setAction(ACTION_LOBBY_APPROVE_ALL)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pinParticipant(context: Context, participantId: String?) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_PIN_PARTICIPANT
                if (participantId != null) putExtra(EXTRA_PINNED_PARTICIPANT, participantId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startScreenShare(context: Context, projectionData: Intent) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_START_SCREEN_SHARE
                putExtra(EXTRA_MEDIA_PROJECTION_DATA, projectionData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopScreenShare(context: Context) {
            val intent = Intent(context, ConferenceService::class.java).setAction(ACTION_STOP_SCREEN_SHARE)
            ContextCompat.startForegroundService(context, intent)
        }

        fun toggleLock(context: Context) {
            val intent = Intent(context, ConferenceService::class.java).setAction(ACTION_TOGGLE_LOCK)
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

        fun demoteListener(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_DEMOTE_LISTENER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun revokeCoHost(context: Context, targetUserId: String) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_REVOKE_COHOST
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
            val safeId = if (roomId.isBlank()) "" else RoomSeparationPolicy.normalizeRoomId(roomId)
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_INVITE
                putExtra(EXTRA_ROOM_ID, safeId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_INVITER, inviterName)
                putExtra(EXTRA_VIDEO, video)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun join(context: Context, roomId: String, userId: String, video: Boolean, inviteRedIds: List<String> = emptyList(), asHost: Boolean = inviteRedIds.isNotEmpty(), title: String = "", isPrivate: Boolean = true, description: String = "", password: String? = null, joinPassword: String? = null) {
            val safeId = if (roomId.isBlank()) "" else RoomSeparationPolicy.normalizeRoomId(roomId)
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_JOIN
                putExtra(EXTRA_ROOM_ID, safeId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_VIDEO, video)
                putExtra(EXTRA_HOST, asHost)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PRIVATE, isPrivate)
                putExtra(EXTRA_DESC, description)
                if (!password.isNullOrBlank()) putExtra(EXTRA_PASSWORD, password)
                if (!joinPassword.isNullOrBlank()) putExtra(EXTRA_JOIN_PASSWORD, joinPassword)
                if (inviteRedIds.isNotEmpty()) putExtra(EXTRA_INVITEES, inviteRedIds.toTypedArray())
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun leave(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ConferenceService::class.java).setAction(ACTION_LEAVE))
        }

        fun accept(context: Context, roomId: String, myUserId: String = "", video: Boolean = true) {
            val safeId = if (roomId.isBlank()) "" else RoomSeparationPolicy.normalizeRoomId(roomId)
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_ACCEPT_INVITE
                putExtra(EXTRA_ROOM_ID, safeId)
                putExtra(EXTRA_VIDEO, video)
                if (myUserId.isNotEmpty()) putExtra(EXTRA_USER_ID, myUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun action(context: Context, act: String, consent: Boolean = false) {
            val intent = Intent(context, ConferenceService::class.java).setAction(act)
            if (consent) intent.putExtra(YounesCallService.EXTRA_CONSENT, true)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
