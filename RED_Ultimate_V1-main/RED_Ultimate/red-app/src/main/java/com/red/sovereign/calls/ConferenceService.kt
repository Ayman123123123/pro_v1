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
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

sealed interface ConferenceUiState {
    data object Idle : ConferenceUiState
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
    var pinnedMessage by mutableStateOf("")
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    var reactions: List<SpaceReaction> by mutableStateOf(emptyList())
}

class ConferenceService : Service(), WebRtcEngine.Events, ConferenceSignalingClient.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: ConferenceSignalingClient
    private var engine: WebRtcEngine? = null
    private var roomId = ""
    private var userId = ""

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
        signaling = ConferenceSignalingClient(this, TokenStore(this), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INVITE -> {
                roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val inviter = intent.getStringExtra(EXTRA_INVITER).orEmpty()
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                ConferenceRuntime.isVideoEnabled = hasVideo
                showIncomingInvitationNotification(roomId, userId, inviter, hasVideo)
            }
            ACTION_JOIN -> {
                roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val hasVideo = intent.getBooleanExtra(EXTRA_VIDEO, false)
                ConferenceRuntime.isVideoEnabled = hasVideo
                ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
                promote()
                signaling.connect(roomId)
            }
            ACTION_LEAVE -> leave()
            ACTION_TOGGLE_MIC -> {
                ConferenceRuntime.isMuted = !ConferenceRuntime.isMuted
                engine?.setMicrophoneEnabled(!ConferenceRuntime.isMuted)
            }
            ACTION_TOGGLE_VIDEO -> {
                ConferenceRuntime.isVideoEnabled = !ConferenceRuntime.isVideoEnabled
                engine?.setCameraEnabled(ConferenceRuntime.isVideoEnabled)
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
                // Override adaptive bitrate — set by user manually
                val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "AUTO"
                engine?.let { eng ->
                    val profile = when (quality) {
                        "LOW" -> NetworkStats.BitrateProfile.LOW
                        "HD" -> NetworkStats.BitrateProfile.HD
                        "AUDIO" -> NetworkStats.BitrateProfile.AUDIO_ONLY
                        else -> NetworkStats.BitrateProfile.STANDARD
                    }
                    eng.applyAdaptiveBitrate(ConferenceRuntime.networkStats.copy(quality = ConferenceRuntime.networkStats.quality))
                    // force re-apply chosen profile
                    if (profile == NetworkStats.BitrateProfile.AUDIO_ONLY) {
                        eng.setCameraEnabled(false)
                    } else {
                        eng.setCameraEnabled(true)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onConnected() {
        scope.launch {
            if (engine == null) {
                engine = WebRtcEngine(this@ConferenceService, this@ConferenceService)
                ConferenceRuntime.eglContext = engine?.eglContext
                engine?.create(ConferenceRuntime.isVideoEnabled, simulcastEnabled = true, svc = true)
                ConferenceRuntime.localVideo = engine?.localMedia?.videoTrack
            }
            signaling.join(roomId, userId, ConferenceRuntime.isVideoEnabled)
        }
    }

    override fun onSignal(signal: ConferenceSignal) {
        when (signal.type) {
            "OFFER" -> {
                signal.payload["sdp"]?.let {
                    engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, it)) {
                        engine?.answer()
                    }
                }
            }
            "ANSWER" -> {
                signal.payload["sdp"]?.let {
                    engine?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, it))
                }
            }
            "ICE" -> {
                engine?.addIce(
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
            "APPROVE_SPEAKER" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    ConferenceRuntime.isSpeaker = true
                    engine?.setMicrophoneEnabled(true)
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
                    engine?.setMicrophoneEnabled(false)
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
                }
            }
            "MUTE_USER" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    ConferenceRuntime.isMuted = true
                    engine?.setMicrophoneEnabled(false)
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

    override fun onRoomState(participants: List<ConferenceParticipant>) {
        ConferenceRuntime.participants = participants
    }

    override fun onParticipantJoined(participant: ConferenceParticipant) {
        val list = ConferenceRuntime.participants.toMutableList()
        list.removeAll { it.userId == participant.userId }
        list.add(participant)
        ConferenceRuntime.participants = list
    }

    override fun onParticipantLeft(userId: String) {
        ConferenceRuntime.participants = ConferenceRuntime.participants.filter { it.userId != userId }
        ConferenceRuntime.remoteVideos.remove(userId)
    }

    override fun onDisconnected() {
        if (ConferenceRuntime.state is ConferenceUiState.Active) {
            ConferenceRuntime.state = ConferenceUiState.Connecting(roomId)
            runCatching { signaling.connect(roomId) }
        } else {
            leave()
        }
    }
    override fun onError(message: String) {
        ConferenceRuntime.state = ConferenceUiState.Error(message)
        // Auto-dismiss after 3s like YounesCallService
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (ConferenceRuntime.state is ConferenceUiState.Error) {
                ConferenceRuntime.state = ConferenceUiState.Idle
            }
        }
        // تأخير مغلق طفيف حتى يرى المستخدم رسالة الخطأ
        scope.launch {
            kotlinx.coroutines.delay(500)
            leave()
        }
    }

    override fun onLocalDescription(description: SessionDescription) {
        if (description.type == SessionDescription.Type.ANSWER) {
            signaling.send(ConferenceSignal(type = "ANSWER", roomId = roomId, userId = userId, payload = mapOf("sdp" to description.description)))
        } else {
            signaling.sendOffer(roomId, userId, description.description)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        signaling.sendIce(roomId, userId, candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp ?: "")
    }

    override fun onRemoteVideo(track: VideoTrack) {
        val owner = ConferenceRuntime.participants.firstOrNull { it.hasVideo && it.userId != userId && !ConferenceRuntime.remoteVideos.containsKey(it.userId) }
        if (owner != null) {
            ConferenceRuntime.remoteVideos[owner.userId] = track
        }
    }

    override fun onNetworkStats(stats: NetworkStats) { ConferenceRuntime.networkStats = stats }

    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            ConferenceRuntime.state = ConferenceUiState.Active(roomId, System.currentTimeMillis())
            startStatsPolling()
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

    private var leaving = false
    private fun leave() {
        if (leaving) return
        leaving = true
        statsJob?.cancel(); statsJob = null
        val closingRoomId = roomId
        val closingUserId = userId
        // 1) إشارة WebSocket للمغادرة الفورية للغرفة (إبلاغ باقي المشاركين)
        if (closingRoomId.isNotBlank()) {
            runCatching { signaling.leave(closingRoomId, closingUserId) }
        }
        // 2) إبلاغ الخادم عبر REST لتصفية العداد وتحديث isSpace/public listing
        //    وتجنب غرف عالقة بعد انقطاع WebSocket. نفذها بمهلة قصيرة لا تعطل إغلاق الخدمة.
        scope.launch {
            if (closingRoomId.isNotBlank()) {
                withTimeoutOrNull(3000) {
                    runCatching {
                        val api = AuthorizedApiClient(TokenStore(this@ConferenceService))
                        // مغادرة عادية
                        api.request("POST", "/api/conference/$closingRoomId/leave", "{}")
                        // إذا كان المضيف هو من يغادر، حاول إغلاق الغرفة أيضًا (may fail if not host, ignore)
                        runCatching { api.request("POST", "/api/conference/$closingRoomId/close", "{}") }
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                // 3) تنظيف محلي نهائي
                signaling.close()
                engine?.release()
                engine = null
                ConferenceRuntime.state = ConferenceUiState.Idle
                ConferenceRuntime.participants = emptyList()
                ConferenceRuntime.remoteVideos.clear()
                ConferenceRuntime.localVideo = null
                ConferenceRuntime.eglContext = null
                ConferenceRuntime.networkStats = NetworkStats()
                ConferenceRuntime.reactions = emptyList()
                ConferenceRuntime.pinnedMessage = ""
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun showIncomingInvitationNotification(roomId: String, userId: String, inviter: String, isVideo: Boolean) {
        val acceptIntent = Intent(this, ConferenceService::class.java).apply {
            action = ACTION_JOIN
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_USER_ID, userId)
            putExtra(EXTRA_VIDEO, isVideo)
        }
        val acceptPending = PendingIntent.getService(this, 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val rejectIntent = Intent(this, ConferenceService::class.java).apply {
            action = ACTION_LEAVE
        }
        val rejectPending = PendingIntent.getService(this, 2, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val mainIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(if (isVideo) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle("دعوة إلى مؤتمر " + (if (isVideo) "فيديو" else "صوتي"))
            .setContentText("دعوة للانضمام للمؤتمر من: ${inviter.ifBlank { "مجموعة يونس" }}")
            .setContentIntent(mainIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(mainIntent, true)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
            .addAction(0, "انضمام", acceptPending)
            .addAction(0, "رفض", rejectPending)
            .build()

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (isVideo) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, 7402, notif, type)
    }

    private fun promote() {
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
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isVideo) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, 7402, notif, type)
    }

    override fun onDestroy() {
        scope.cancel()
        leave()
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

        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_INVITER = "inviter"
        const val EXTRA_VIDEO = "video"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_TARGET_USER_ID = "target_user_id"
        const val EXTRA_EMOJI = "emoji"
        const val EXTRA_PIN_TEXT = "pin_text"

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

        fun join(context: Context, roomId: String, userId: String, video: Boolean) {
            val intent = Intent(context, ConferenceService::class.java).apply {
                action = ACTION_JOIN
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_VIDEO, video)
            }
            ContextCompat.startForegroundService(context, intent)
        }
        fun leave(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ConferenceService::class.java).setAction(ACTION_LEAVE))
        }
        fun action(context: Context, act: String) {
            ContextCompat.startForegroundService(context, Intent(context, ConferenceService::class.java).setAction(act))
        }
    }
}
