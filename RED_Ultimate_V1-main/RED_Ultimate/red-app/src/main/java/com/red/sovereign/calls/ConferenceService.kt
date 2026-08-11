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

sealed interface ConferenceUiState {
    data object Idle : ConferenceUiState
    data class Connecting(val roomId: String) : ConferenceUiState
    data class Active(val roomId: String, val startedAt: Long) : ConferenceUiState
    data class Error(val message: String) : ConferenceUiState
}

object ConferenceRuntime {
    var state: ConferenceUiState by mutableStateOf(ConferenceUiState.Idle)
    var participants by mutableStateOf(emptyList<ConferenceParticipant>())
    var localVideo: VideoTrack? by mutableStateOf(null)
    var eglContext: org.webrtc.EglBase.Context? = null
    val remoteVideos = androidx.compose.runtime.mutableStateMapOf<String, VideoTrack>()
    var isMuted by mutableStateOf(false)
    var isVideoEnabled by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
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
            engine = WebRtcEngine(this@ConferenceService, this@ConferenceService)
            ConferenceRuntime.eglContext = engine?.eglContext
            engine?.create(ConferenceRuntime.isVideoEnabled, simulcastEnabled = true, svc = true)
            ConferenceRuntime.localVideo = engine?.localMedia?.videoTrack
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

    override fun onDisconnected() { leave() }
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

    private fun leave() {
        statsJob?.cancel(); statsJob = null
        if (roomId.isNotBlank()) signaling.leave(roomId, userId)
        signaling.close()
        engine?.release()
        engine = null
        ConferenceRuntime.state = ConferenceUiState.Idle
        ConferenceRuntime.participants = emptyList()
        ConferenceRuntime.remoteVideos.clear()
        ConferenceRuntime.localVideo = null
        ConferenceRuntime.eglContext = null
        ConferenceRuntime.networkStats = NetworkStats()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        const val ACTION_JOIN = "com.red.sovereign.conference.JOIN"
        const val ACTION_LEAVE = "com.red.sovereign.conference.LEAVE"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.conference.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.conference.TOGGLE_VIDEO"
        const val ACTION_SET_QUALITY = "com.red.sovereign.conference.SET_QUALITY"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_VIDEO = "video"
        const val EXTRA_QUALITY = "quality"

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
