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

sealed interface LiveStreamUiState {
    data object Idle : LiveStreamUiState
    data class Connecting(val streamId: String, val isBroadcaster: Boolean) : LiveStreamUiState
    data class Active(val streamId: String, val isBroadcaster: Boolean, val startedAt: Long) : LiveStreamUiState
    data class Error(val message: String) : LiveStreamUiState
}

object LiveStreamRuntime {
    var state: LiveStreamUiState by mutableStateOf(LiveStreamUiState.Idle)
    var localVideo: VideoTrack? by mutableStateOf(null)
    var remoteVideo: VideoTrack? by mutableStateOf(null)
    var eglContext: org.webrtc.EglBase.Context? = null
    var isMuted by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    /** عدد المشاهدين النشطين — محدّث عبر signaling (PARTICIPANT_JOINED/LEFT) */
    var viewerCount: Int by mutableStateOf(0)
}

class LiveStreamService : Service(), WebRtcEngine.Events, LiveStreamSignalingClient.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: LiveStreamSignalingClient
    private var engine: WebRtcEngine? = null
    private var streamId = ""
    private var userId = ""
    private var isBroadcaster = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
        signaling = LiveStreamSignalingClient(this, TokenStore(this), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                streamId = intent.getStringExtra(EXTRA_STREAM_ID).orEmpty()
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                isBroadcaster = intent.getBooleanExtra(EXTRA_BROADCASTER, false)
                LiveStreamRuntime.state = LiveStreamUiState.Connecting(streamId, isBroadcaster)
                promote()
                signaling.connect(streamId)
            }
            ACTION_STOP -> stopStream()
            ACTION_TOGGLE_MIC -> {
                LiveStreamRuntime.isMuted = !LiveStreamRuntime.isMuted
                engine?.setMicrophoneEnabled(!LiveStreamRuntime.isMuted)
            }
            ACTION_TOGGLE_VIDEO -> {
                // للبث المباشر، التبديل يعني إيقاف/استئناف إرسال الفيديو (الكاميرا المحلية)
                val isVideoOn = LiveStreamRuntime.localVideo?.enabled() == true
                engine?.setCameraEnabled(!isVideoOn)
            }
        }
        return START_NOT_STICKY
    }

    override fun onConnected() {
        scope.launch {
            engine = WebRtcEngine(this@LiveStreamService, this@LiveStreamService)
            LiveStreamRuntime.eglContext = engine?.eglContext
            // Live streaming needs HD + simulcast for adaptive quality to viewers
            engine?.create(isBroadcaster, simulcastEnabled = isBroadcaster)
            if (isBroadcaster) {
                LiveStreamRuntime.localVideo = engine?.localMedia?.videoTrack
            }
            signaling.join(streamId, userId, if (isBroadcaster) "broadcaster" else "viewer")
        }
    }

    override fun onSignal(signal: LiveStreamSignal) {
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
            "VIEWER_JOINED" -> {
                if (isBroadcaster) LiveStreamRuntime.viewerCount += 1
            }
            "VIEWER_LEFT" -> {
                if (isBroadcaster) LiveStreamRuntime.viewerCount = (LiveStreamRuntime.viewerCount - 1).coerceAtLeast(0)
            }
        }
    }

    override fun onDisconnected() { stopStream() }
    override fun onError(message: String) {
        LiveStreamRuntime.state = LiveStreamUiState.Error(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (LiveStreamRuntime.state is LiveStreamUiState.Error) LiveStreamRuntime.state = LiveStreamUiState.Idle
        }
        scope.launch {
            kotlinx.coroutines.delay(500)
            stopStream()
        }
    }

    override fun onLocalDescription(description: SessionDescription) {
        if (description.type == SessionDescription.Type.ANSWER) {
            signaling.sendAnswer(streamId, userId, description.description)
        } else {
            signaling.sendOffer(streamId, userId, description.description)
        }
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        signaling.sendIce(streamId, userId, candidate.sdpMid ?: "", candidate.sdpMLineIndex, candidate.sdp ?: "")
    }

    override fun onRemoteVideo(track: VideoTrack) {
        if (!isBroadcaster) {
            LiveStreamRuntime.remoteVideo = track
        }
    }

    override fun onNetworkStats(stats: NetworkStats) { LiveStreamRuntime.networkStats = stats }

    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
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
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun stopStream() {
        statsJob?.cancel(); statsJob = null
        if (streamId.isNotBlank()) signaling.leave(streamId, userId)
        signaling.close()
        engine?.release()
        engine = null
        LiveStreamRuntime.state = LiveStreamUiState.Idle
        LiveStreamRuntime.localVideo = null
        LiveStreamRuntime.remoteVideo = null
        LiveStreamRuntime.eglContext = null
        LiveStreamRuntime.networkStats = NetworkStats()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun promote() {
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = if (isBroadcaster) "بث مباشر يونس" else "مشاهدة بث يونس"
        val text = if (isBroadcaster) "أنت تبث الآن مباشرة..." else "أنت تشاهد البث المباشر..."
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .addAction(0, "إيقاف", PendingIntent.getService(this, 1, Intent(this, LiveStreamService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (isBroadcaster) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, 7403, notif, type)
    }

    override fun onDestroy() {
        scope.cancel()
        stopStream()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.red.sovereign.livestream.START"
        const val ACTION_STOP = "com.red.sovereign.livestream.STOP"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.livestream.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.livestream.TOGGLE_VIDEO"
        const val EXTRA_STREAM_ID = "stream_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_BROADCASTER = "broadcaster"

        fun start(context: Context, streamId: String, userId: String, isBroadcaster: Boolean) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STREAM_ID, streamId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_BROADCASTER, isBroadcaster)
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
