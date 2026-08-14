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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// حالات واجهة المستخدم
// ─────────────────────────────────────────────────────────────────────────────

/** حالة كل مدعو في المكالمة الجماعية */
enum class GroupCallMemberStatus {
    RINGING,   // جاري الرنين
    JOINED,    // انضم
    DECLINED,  // رفض
    NO_ANSWER, // لم يرد
    LEFT       // غادر
}

data class GroupCallMember(
    val userId: String,
    val displayName: String,
    val status: GroupCallMemberStatus = GroupCallMemberStatus.RINGING,
    val isMuted: Boolean = false,
    val hasVideo: Boolean = false
)

sealed interface GroupCallUiState {
    data object Idle : GroupCallUiState
    /** المضيف يرن الأعضاء — ينتظر من يقبل */
    data class Ringing(
        val groupCallId: String,
        val isVideo: Boolean,
        val members: List<GroupCallMember>
    ) : GroupCallUiState
    /** دعوة واردة من مجموعة — مكالمة جماعية يرن فيها هاتفك */
    data class IncomingGroup(
        val groupCallId: String,
        val hostId: String,
        val hostName: String,
        val isVideo: Boolean,
        val otherMembers: List<String>
    ) : GroupCallUiState
    /** مكالمة نشطة — على الأقل شخص واحد انضم */
    data class Active(
        val groupCallId: String,
        val isVideo: Boolean,
        val members: List<GroupCallMember>,
        val startedAt: Long
    ) : GroupCallUiState
    data object Ended : GroupCallUiState
}

/** بيانات الـ Runtime المشتركة بين الـ Service والـ Composable */
object GroupCallRuntime {
    var state: GroupCallUiState by mutableStateOf(GroupCallUiState.Idle)
    var localVideo: VideoTrack? by mutableStateOf(null)
    var remoteVideos: Map<String, VideoTrack> by mutableStateOf(emptyMap())
    var eglContext: org.webrtc.EglBase.Context? = null
    var isMuted by mutableStateOf(false)
    var isVideoEnabled by mutableStateOf(false)
    var isHost by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
}

// ─────────────────────────────────────────────────────────────────────────────
// الخدمة الرئيسية
// ─────────────────────────────────────────────────────────────────────────────

/**
 * خدمة المكالمات الجماعية — نمط iMO/Zoom.
 *
 * السلوك:
 * • المضيف يختار أصدقاء → يرن هاتف كل واحد منهم.
 * • أول شخص يقبل → تبدأ المكالمة فعلياً (Mesh P2P).
 * • البقية يمكنهم الانضمام تدريجياً.
 * • المضيف يمكنه رؤية من قَبِل، من رفض، من لم يرد.
 * • حتى 8 مشاركين (Mesh WebRTC، مثالي للمجموعات الصغيرة).
 */
class GroupCallService : Service(), WebRtcEngine.Events, MeshRtcSession.Events, CallSignalingClient.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: CallSignalingClient
    private var engine: WebRtcEngine? = null
    private var mesh: MeshRtcSession? = null
    private var recordingManager: CallRecordingManager? = null

    private var groupCallId = ""
    private var myUserId = ""
    private var hostDisplayName = ""
    private var isHost = false
    private var isVideo = false
    private var stopping = false
    private var cleanedUp = false

    // مهلة الرنين — 45 ثانية قبل اعتبار الأعضاء "لم يردوا"
    private var ringTimeout: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH)
        )
        signaling = CallSignalingClient(this, TokenStore(this), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GROUP_CALL -> {
                stopping = false; cleanedUp = false
                groupCallId = intent.getStringExtra(EXTRA_GROUP_CALL_ID) ?: UUID.randomUUID().toString()
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID).orEmpty()
                hostDisplayName = intent.getStringExtra(EXTRA_HOST_NAME).orEmpty()
                isHost = true
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val invitees = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                val names = intent.getStringArrayListExtra(EXTRA_INVITEE_NAMES) ?: arrayListOf()

                GroupCallRuntime.isVideoEnabled = isVideo
                GroupCallRuntime.isMuted = false

                val members = invitees.mapIndexed { i, id ->
                    GroupCallMember(userId = id, displayName = names.getOrElse(i) { id }, status = GroupCallMemberStatus.RINGING)
                }
                GroupCallRuntime.state = GroupCallUiState.Ringing(groupCallId, isVideo, members)

                promoteToForeground()
                scope.launch { signaling.connect() }

                // مهلة الرنين 45 ثانية
                ringTimeout = scope.launch {
                    delay(45_000)
                    val current = GroupCallRuntime.state
                    if (current is GroupCallUiState.Ringing) {
                        val updated = current.members.map {
                            if (it.status == GroupCallMemberStatus.RINGING) it.copy(status = GroupCallMemberStatus.NO_ANSWER) else it
                        }
                        withContext(Dispatchers.Main.immediate) {
                            GroupCallRuntime.state = current.copy(members = updated)
                        }
                        if (updated.none { it.status == GroupCallMemberStatus.JOINED }) stopGroupCall()
                    }
                }
            }

            ACTION_INCOMING_GROUP_CALL -> {
                stopping = false; cleanedUp = false
                groupCallId = intent.getStringExtra(EXTRA_GROUP_CALL_ID).orEmpty()
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID).orEmpty()
                isHost = false
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val hostId = intent.getStringExtra(EXTRA_HOST_ID).orEmpty()
                val hostName = intent.getStringExtra(EXTRA_HOST_NAME).orEmpty()
                val others = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                GroupCallRuntime.state = GroupCallUiState.IncomingGroup(groupCallId, hostId, hostName, isVideo, others)
                showIncomingGroupCallNotification(groupCallId, hostName, others.size, isVideo)
            }

            ACTION_ACCEPT_GROUP_CALL -> {
                ringTimeout?.cancel()
                val gId = intent.getStringExtra(EXTRA_GROUP_CALL_ID) ?: groupCallId
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID) ?: myUserId
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, isVideo)
                groupCallId = gId
                GroupCallRuntime.isVideoEnabled = isVideo
                promoteToForeground()
                scope.launch {
                    signaling.connect()
                    signaling.sendGroupCallResponse(gId, accepted = true)
                }
            }

            ACTION_DECLINE_GROUP_CALL -> {
                val gId = intent.getStringExtra(EXTRA_GROUP_CALL_ID) ?: groupCallId
                scope.launch {
                    runCatching { signaling.connect() }
                    signaling.sendGroupCallResponse(gId, accepted = false)
                }
                stopGroupCall()
            }

            ACTION_END_GROUP_CALL -> stopGroupCall()

            ACTION_TOGGLE_MIC -> {
                GroupCallRuntime.isMuted = !GroupCallRuntime.isMuted
                val on = !GroupCallRuntime.isMuted
                engine?.setMicrophoneEnabled(on)
                mesh?.setMicrophoneEnabled(on)
            }

            ACTION_TOGGLE_VIDEO -> {
                val isOn = GroupCallRuntime.localVideo?.enabled() == true
                engine?.setCameraEnabled(!isOn)
                mesh?.setCameraEnabled(!isOn)
                GroupCallRuntime.isVideoEnabled = !isOn
            }

            ACTION_SWITCH_CAMERA -> {
                engine?.switchCamera()
                mesh?.switchCamera()
            }

            ACTION_START_RECORDING -> {
                if (recordingManager == null && groupCallId.isNotBlank()) {
                    recordingManager = CallRecordingManager(this, groupCallId)
                }
                recordingManager?.start(consentGranted = true)
            }

            ACTION_STOP_RECORDING -> {
                scope.launch { recordingManager?.stop(); recordingManager = null }
            }
        }
        return START_STICKY
    }

    // ─────────────── WebRTC Events ────────────────────────────────────────────

    override fun onConnected() {
        scope.launch {
            engine = WebRtcEngine(this@GroupCallService, this@GroupCallService)
            GroupCallRuntime.eglContext = engine?.eglContext
            val kind = if (isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
            engine?.create(kind)
            if (isVideo) GroupCallRuntime.localVideo = engine?.localMedia?.videoTrack
            mesh = MeshRtcSession(this@GroupCallService, myUserId, this@GroupCallService)

            if (isHost) {
                val state = GroupCallRuntime.state
                if (state is GroupCallUiState.Ringing) {
                    signaling.sendGroupCallInvite(groupCallId, state.members.map { it.userId }, isVideo, hostDisplayName)
                }
            }
        }
    }

    override fun onSignal(signal: CallSignal) {
        when (signal.type) {
            "GROUP_CALL_ACCEPT" -> {
                val joinerId = signal.sourceUserId.orEmpty()
                if (joinerId.isNotBlank()) {
                    mesh?.attachPeer(joinerId)
                    mesh?.offerTo(joinerId)
                }
                val cur = GroupCallRuntime.state
                scope.launch(Dispatchers.Main.immediate) {
                    when (cur) {
                        is GroupCallUiState.Ringing -> {
                            val updated = cur.members.map {
                                if (it.userId == joinerId) it.copy(status = GroupCallMemberStatus.JOINED) else it
                            }
                            GroupCallRuntime.state = GroupCallUiState.Active(cur.groupCallId, cur.isVideo, updated, System.currentTimeMillis())
                        }
                        is GroupCallUiState.Active -> {
                            GroupCallRuntime.state = cur.copy(
                                members = cur.members.map {
                                    if (it.userId == joinerId) it.copy(status = GroupCallMemberStatus.JOINED) else it
                                }
                            )
                        }
                        else -> {}
                    }
                }
            }

            "GROUP_CALL_DECLINE" -> {
                updateMemberStatus(signal.sourceUserId.orEmpty(), GroupCallMemberStatus.DECLINED)
                checkIfAllDone()
            }

            "GROUP_CALL_END" -> stopGroupCall()

            "GROUP_CALL_STATUS" -> {
                val status = when (signal.memberStatus) {
                    "ringing"   -> GroupCallMemberStatus.RINGING
                    "joined"    -> GroupCallMemberStatus.JOINED
                    "declined"  -> GroupCallMemberStatus.DECLINED
                    "no_answer" -> GroupCallMemberStatus.NO_ANSWER
                    "left"      -> GroupCallMemberStatus.LEFT
                    else -> null
                }
                val uid = signal.sourceUserId.orEmpty()
                if (status != null && uid.isNotBlank()) updateMemberStatus(uid, status)
            }

            "OFFER" -> signal.payload["sdp"]?.let { sdp ->
                val from = signal.sourceUserId.orEmpty()
                if (from.isNotBlank()) mesh?.handleOffer(from, sdp)
                else engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) { engine?.answer() }
            }

            "ANSWER" -> signal.payload["sdp"]?.let { sdp ->
                val from = signal.sourceUserId.orEmpty()
                if (from.isNotBlank()) mesh?.handleAnswer(from, sdp)
                else engine?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }

            "ICE" -> {
                val from = signal.sourceUserId.orEmpty()
                val candidate = IceCandidate(
                    signal.payload["sdpMid"],
                    signal.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0,
                    signal.payload["candidate"].orEmpty()
                )
                if (from.isNotBlank()) mesh?.handleIce(from, candidate) else engine?.addIce(candidate)
            }

            "PARTICIPANT_LEFT" -> {
                val leftId = signal.sourceUserId.orEmpty()
                updateMemberStatus(leftId, GroupCallMemberStatus.LEFT)
                if (leftId.isNotBlank()) mesh?.detachPeer(leftId)
                checkIfAllDone()
            }
        }
    }

    override fun onLocalDescription(description: SessionDescription) {
        val type = if (description.type == SessionDescription.Type.ANSWER) "ANSWER" else "OFFER"
        signaling.send(CallSignal(callId = groupCallId, type = type, groupCallId = groupCallId, payload = mapOf("sdp" to description.description)))
    }

    override fun onLocalDescription(peerId: String, description: SessionDescription) {
        val type = if (description.type == SessionDescription.Type.ANSWER) "ANSWER" else "OFFER"
        signaling.send(CallSignal(callId = groupCallId, targetUserId = peerId, type = type, groupCallId = groupCallId, payload = mapOf("sdp" to description.description)))
    }

    override fun onIceCandidate(candidate: IceCandidate) {
        signaling.send(CallSignal(callId = groupCallId, type = "ICE", groupCallId = groupCallId,
            payload = mapOf("sdpMid" to (candidate.sdpMid ?: ""), "sdpMLineIndex" to candidate.sdpMLineIndex.toString(), "candidate" to (candidate.sdp ?: ""))))
    }

    override fun onIceCandidate(peerId: String, candidate: IceCandidate) {
        signaling.send(CallSignal(callId = groupCallId, targetUserId = peerId, type = "ICE", groupCallId = groupCallId,
            payload = mapOf("sdpMid" to (candidate.sdpMid ?: ""), "sdpMLineIndex" to candidate.sdpMLineIndex.toString(), "candidate" to (candidate.sdp ?: ""))))
    }

    override fun onRemoteVideo(track: VideoTrack) {
        GroupCallRuntime.remoteVideos = GroupCallRuntime.remoteVideos + ("remote" to track)
    }

    override fun onRemoteVideo(peerId: String, track: VideoTrack) {
        GroupCallRuntime.remoteVideos = GroupCallRuntime.remoteVideos + (peerId to track)
    }

    override fun onNetworkStats(stats: NetworkStats) { GroupCallRuntime.networkStats = stats }
    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {}
    override fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.DISCONNECTED || state == PeerConnection.PeerConnectionState.FAILED) {
            scope.launch { delay(1500); mesh?.offerTo(peerId) }
        }
    }

    override fun onDisconnected() { if (!stopping) runCatching { signaling.reconnect() } }
    override fun onError(message: String) { if (message == "UNAUTHORIZED") stopGroupCall() }

    // ─────────────── Helpers ──────────────────────────────────────────────────

    private fun updateMemberStatus(userId: String, status: GroupCallMemberStatus) {
        if (userId.isBlank()) return
        scope.launch(Dispatchers.Main.immediate) {
            when (val cur = GroupCallRuntime.state) {
                is GroupCallUiState.Ringing -> GroupCallRuntime.state = cur.copy(
                    members = cur.members.map { if (it.userId == userId) it.copy(status = status) else it }
                )
                is GroupCallUiState.Active -> GroupCallRuntime.state = cur.copy(
                    members = cur.members.map { if (it.userId == userId) it.copy(status = status) else it }
                )
                else -> {}
            }
        }
    }

    private fun checkIfAllDone() {
        val members = when (val cur = GroupCallRuntime.state) {
            is GroupCallUiState.Ringing -> cur.members
            is GroupCallUiState.Active -> cur.members
            else -> return
        }
        val terminal = setOf(GroupCallMemberStatus.DECLINED, GroupCallMemberStatus.NO_ANSWER, GroupCallMemberStatus.LEFT)
        if (members.all { it.status in terminal }) stopGroupCall()
    }

    private fun stopGroupCall() {
        if (stopping) return
        stopping = true
        ringTimeout?.cancel()
        if (isHost && groupCallId.isNotBlank()) runCatching { signaling.sendGroupCallEnd(groupCallId) }
        scope.launch(Dispatchers.Main.immediate) { finishStop() }
    }

    private fun finishStop() {
        if (cleanedUp) return
        cleanedUp = true
        recordingManager?.let { scope.launch { it.stop() } }
        recordingManager = null
        engine?.release(); engine = null
        mesh?.release(); mesh = null
        signaling.close()
        GroupCallRuntime.state = GroupCallUiState.Ended
        GroupCallRuntime.localVideo = null
        GroupCallRuntime.remoteVideos = emptyMap()
        GroupCallRuntime.eglContext = null
        GroupCallRuntime.isMuted = false
        GroupCallRuntime.isVideoEnabled = false
        GroupCallRuntime.networkStats = NetworkStats()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ─────────────── Notifications ────────────────────────────────────────────

    private fun promoteToForeground() {
        val label = if (isVideo) "مكالمة فيديو جماعية" else "مكالمة صوتية جماعية"
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val endIntent = PendingIntent.getService(this, 10,
            Intent(this, GroupCallService::class.java).setAction(ACTION_END_GROUP_CALL), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(label)
            .setContentText("مكالمة جماعية جارية...")
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true).setSilent(true)
            .addAction(0, "إنهاء", endIntent)
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isVideo) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        ServiceCompat.startForeground(this, NOTIF_ID_ACTIVE, notif, type)
    }

    private fun showIncomingGroupCallNotification(gId: String, hostName: String, otherCount: Int, video: Boolean) {
        val label = if (video) "مكالمة فيديو جماعية" else "مكالمة صوتية جماعية"
        val body = if (otherCount > 0) "من $hostName ومعه $otherCount آخرون" else "من $hostName"
        val acceptIntent = PendingIntent.getService(this, 20,
            Intent(this, GroupCallService::class.java).setAction(ACTION_ACCEPT_GROUP_CALL)
                .putExtra(EXTRA_GROUP_CALL_ID, gId).putExtra(EXTRA_IS_VIDEO, video),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val declineIntent = PendingIntent.getService(this, 21,
            Intent(this, GroupCallService::class.java).setAction(ACTION_DECLINE_GROUP_CALL)
                .putExtra(EXTRA_GROUP_CALL_ID, gId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fullScreenIntent = PendingIntent.getActivity(this, 22, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(label).setContentText(body)
            .setFullScreenIntent(fullScreenIntent, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true).setAutoCancel(false)
            .addAction(0, "رفض", declineIntent)
            .addAction(0, "قبول", acceptIntent)
            .build()
        ServiceCompat.startForeground(this, NOTIF_ID_INCOMING, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    override fun onDestroy() { finishStop(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────── Companion ────────────────────────────────────────────────

    companion object {
        const val ACTION_START_GROUP_CALL    = "com.red.sovereign.groupcall.START"
        const val ACTION_INCOMING_GROUP_CALL = "com.red.sovereign.groupcall.INCOMING"
        const val ACTION_ACCEPT_GROUP_CALL   = "com.red.sovereign.groupcall.ACCEPT"
        const val ACTION_DECLINE_GROUP_CALL  = "com.red.sovereign.groupcall.DECLINE"
        const val ACTION_END_GROUP_CALL      = "com.red.sovereign.groupcall.END"
        const val ACTION_TOGGLE_MIC          = "com.red.sovereign.groupcall.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO        = "com.red.sovereign.groupcall.TOGGLE_VIDEO"
        const val ACTION_SWITCH_CAMERA       = "com.red.sovereign.groupcall.SWITCH_CAMERA"
        const val ACTION_START_RECORDING     = "com.red.sovereign.groupcall.START_RECORDING"
        const val ACTION_STOP_RECORDING      = "com.red.sovereign.groupcall.STOP_RECORDING"

        const val EXTRA_GROUP_CALL_ID = "group_call_id"
        const val EXTRA_MY_USER_ID    = "my_user_id"
        const val EXTRA_HOST_ID       = "host_id"
        const val EXTRA_HOST_NAME     = "host_name"
        const val EXTRA_INVITEE_IDS   = "invitee_ids"
        const val EXTRA_INVITEE_NAMES = "invitee_names"
        const val EXTRA_IS_VIDEO      = "is_video"

        private const val NOTIF_ID_ACTIVE   = 8100
        private const val NOTIF_ID_INCOMING = 8101

        fun startGroupCall(
            context: Context, myUserId: String,
            inviteeIds: List<String>, inviteeNames: List<String>,
            isVideo: Boolean, groupCallId: String = UUID.randomUUID().toString(),
            hostName: String = ""
        ) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).apply {
                    action = ACTION_START_GROUP_CALL
                    putExtra(EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(EXTRA_MY_USER_ID, myUserId)
                    putExtra(EXTRA_HOST_NAME, hostName)
                    putExtra(EXTRA_IS_VIDEO, isVideo)
                    putStringArrayListExtra(EXTRA_INVITEE_IDS, ArrayList(inviteeIds))
                    putStringArrayListExtra(EXTRA_INVITEE_NAMES, ArrayList(inviteeNames))
                })
        }

        fun notifyIncoming(
            context: Context, groupCallId: String, myUserId: String,
            hostId: String, hostName: String, isVideo: Boolean,
            otherMemberIds: List<String> = emptyList()
        ) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).apply {
                    action = ACTION_INCOMING_GROUP_CALL
                    putExtra(EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(EXTRA_MY_USER_ID, myUserId)
                    putExtra(EXTRA_HOST_ID, hostId)
                    putExtra(EXTRA_HOST_NAME, hostName)
                    putExtra(EXTRA_IS_VIDEO, isVideo)
                    putStringArrayListExtra(EXTRA_INVITEE_IDS, ArrayList(otherMemberIds))
                })
        }

        fun accept(context: Context, groupCallId: String, myUserId: String, isVideo: Boolean) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).apply {
                    action = ACTION_ACCEPT_GROUP_CALL
                    putExtra(EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(EXTRA_MY_USER_ID, myUserId)
                    putExtra(EXTRA_IS_VIDEO, isVideo)
                })
        }

        fun decline(context: Context, groupCallId: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).setAction(ACTION_DECLINE_GROUP_CALL)
                    .putExtra(EXTRA_GROUP_CALL_ID, groupCallId))
        }

        fun end(context: Context) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).setAction(ACTION_END_GROUP_CALL))
        }

        fun action(context: Context, act: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).setAction(act))
        }
    }
}
