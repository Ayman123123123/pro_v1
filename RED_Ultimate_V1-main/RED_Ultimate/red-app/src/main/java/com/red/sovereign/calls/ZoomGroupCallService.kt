package com.red.sovereign.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.*
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/** Zoom: مكالمات جماعية مستقلة — 100 مشارك، رابط قصير 8 أحرف، قاعة انتظار اختيارية */
const val ZOOM_LIMIT = 100

enum class ZoomMemberStatus { RINGING, JOINED, DECLINED, NO_ANSWER, LEFT, BUSY, WAITING }

data class ZoomMember(
    val userId: String,
    val displayName: String,
    val status: ZoomMemberStatus = ZoomMemberStatus.RINGING,
    val isMuted: Boolean = false,
    val hasVideo: Boolean = false,
    val isHandRaised: Boolean = false
)

sealed interface ZoomUiState {
    data object Idle : ZoomUiState
    data class Ringing(val meetingId: String, val isVideo: Boolean, val members: List<ZoomMember>) : ZoomUiState
    data class Incoming(val meetingId: String, val hostId: String, val hostName: String, val isVideo: Boolean, val otherIds: List<String>, val title: String) : ZoomUiState
    data class Active(val meetingId: String, val isVideo: Boolean, val members: List<ZoomMember>, val startedAt: Long, val title: String = "") : ZoomUiState
    data class WaitingRoom(val meetingId: String, val hostName: String) : ZoomUiState
    data object Ended : ZoomUiState
}

object ZoomRuntime {
    var state: ZoomUiState by mutableStateOf(ZoomUiState.Idle)
    var localVideo: VideoTrack? by mutableStateOf(null)
    var remoteVideos: Map<String, VideoTrack> by mutableStateOf(emptyMap())
    var eglContext: org.webrtc.EglBase.Context? by mutableStateOf(null)
    var isMuted by mutableStateOf(false)
    var isVideoEnabled by mutableStateOf(false)
    var isHost by mutableStateOf(false)
    var isScreenSharing by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var isHandRaised by mutableStateOf(false)
    var isMinimized by mutableStateOf(false)
    var isLocked by mutableStateOf(false)
    var isWaitingRoomEnabled by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    /** مرجع الإشارة النشطة — يُحدَّث من ZoomGroupCallService عند onCreate ويُفرَّغ عند stop.
     *  يتيح للـ Compose (مثل BreakoutRoomsSheet) إرسال إشارات دون الاحتفاظ بـ service instance. */
    @Volatile var activeSignaling: CallSignalingClient? = null
    /** علم فتح/إغلاق BreakoutRoomsSheet — مشترك بين ZoomActivePanel و ZoomParticipantsSheet. */
    var showBreakoutSheet: Boolean by mutableStateOf(false)
    var meetingTitle: String by mutableStateOf("")
    var meetingId: String by mutableStateOf("")
    var activePoll: ZoomPoll? by mutableStateOf(null)
    var breakoutRooms: List<ZoomBreakoutRoom> by mutableStateOf(emptyList())
}

data class ZoomPoll(val id: String = UUID.randomUUID().toString(), val question: String, val options: List<String>, val votes: Map<String, Int> = emptyMap(), val isClosed: Boolean = false)
data class ZoomBreakoutRoom(val id: String, val name: String, val participantIds: List<String>)

class ZoomGroupCallService : Service(), WebRtcEngine.Events, MeshRtcSession.Events, CallSignalingClient.Listener, SfuMediaClient.Events {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var signaling: CallSignalingClient
    private lateinit var audio: AudioManager
    private var engine: WebRtcEngine? = null
    private var mesh: MeshRtcSession? = null
    private var sfu: SfuMediaClient? = null
    private var recordingManager: CallRecordingManager? = null
    private var ringback: ToneGenerator? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var audioFocus: android.media.AudioFocusRequest? = null

    private var meetingId = ""
    private var myUserId = ""
    private var hostId = ""
    private var hostDisplayName = ""
    private var isHost = false
    private var isVideo = false
    private var stopping = false
    private var cleanedUp = false
    private var ringTimeout: Job? = null
    private var incomingTimeout: Job? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH)
        )
        signaling = CallSignalingClient(this, TokenStore(this), this)
        ZoomRuntime.activeSignaling = signaling
    }

    private fun prepareAudio(isVideo: Boolean) {
        try {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            audio.isSpeakerphoneOn = isVideo || ZoomRuntime.isVideoEnabled
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
            audioFocus = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT.let {
                android.media.AudioFocusRequest.Builder(it).setAudioAttributes(attrs).setOnAudioFocusChangeListener {}.build()
            }
            audio.requestAudioFocus(audioFocus!!)
        } catch (_: Exception) {}
    }

    private fun releaseAudio() {
        try { audioFocus?.let { audio.abandonAudioFocusRequest(it) }; audio.mode = AudioManager.MODE_NORMAL; audio.isSpeakerphoneOn = false } catch (_: Exception) {}
        audioFocus = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ZOOM -> {
                stopping = false; cleanedUp = false
                meetingId = intent.getStringExtra(EXTRA_MEETING_ID) ?: generateMeetingId()
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID).orEmpty()
                hostDisplayName = intent.getStringExtra(EXTRA_HOST_NAME).orEmpty()
                isHost = true
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val invitees = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                val names = intent.getStringArrayListExtra(EXTRA_INVITEE_NAMES) ?: arrayListOf()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "اجتماع Zoom" }
                ZoomRuntime.meetingId = meetingId
                ZoomRuntime.meetingTitle = title
                ZoomRuntime.isHost = true
                ZoomRuntime.isVideoEnabled = isVideo
                ZoomRuntime.isMuted = false
                val members = invitees.mapIndexed { i, id -> ZoomMember(id, names.getOrElse(i){id}, ZoomMemberStatus.RINGING, hasVideo = isVideo) }
                ZoomRuntime.state = ZoomUiState.Ringing(meetingId, isVideo, members)
                prepareAudio(isVideo)
                promoteToForeground()
                scope.launch {
                    // تسجيل الغرفة في الخادم أولاً ليُقبل SFU ticket
                    registerZoomRoom(meetingId, title, isVideo)
                    signaling.connect()
                }
                ringTimeout = scope.launch {
                    delay(45_000)
                    val cur = ZoomRuntime.state
                    if (cur is ZoomUiState.Ringing) {
                        val updated = cur.members.map { if (it.status==ZoomMemberStatus.RINGING) it.copy(status=ZoomMemberStatus.NO_ANSWER) else it }
                        withContext(Dispatchers.Main.immediate){ ZoomRuntime.state = cur.copy(members = updated) }
                        if (updated.none{ it.status==ZoomMemberStatus.JOINED }) stopZoom()
                    }
                }
            }
            ACTION_INCOMING_ZOOM -> {
                stopping = false; cleanedUp = false
                meetingId = intent.getStringExtra(EXTRA_MEETING_ID).orEmpty()
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID).orEmpty()
                isHost = false
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                hostId = intent.getStringExtra(EXTRA_HOST_ID).orEmpty()
                hostDisplayName = intent.getStringExtra(EXTRA_HOST_NAME).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val others = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                ZoomRuntime.meetingId = meetingId
                ZoomRuntime.meetingTitle = title
                ZoomRuntime.isHost = false
                ZoomRuntime.state = ZoomUiState.Incoming(meetingId, hostId, hostDisplayName, isVideo, others, title)
                prepareAudio(isVideo)
                showIncomingZoomNotification(meetingId, hostDisplayName, others.size, isVideo, title)
                if (com.red.sovereign.settings.SettingsRuntime.current.callNotifications) startRingtone() else prepareAudio(isVideo)
                incomingTimeout?.cancel()
                incomingTimeout = scope.launch {
                    delay(30_000)
                    val cur = ZoomRuntime.state
                    if (cur is ZoomUiState.Incoming && cur.meetingId==meetingId) {
                        runCatching { signaling.send(CallSignal(callId=meetingId, type="ZOOM_DECLINE", groupCallId=meetingId)) }
                        stopZoom()
                    }
                }
            }
            ACTION_ACCEPT_ZOOM -> {
                ringTimeout?.cancel(); incomingTimeout?.cancel()
                val mId = intent.getStringExtra(EXTRA_MEETING_ID) ?: meetingId
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID) ?: myUserId
                intent.getStringExtra(EXTRA_HOST_ID)?.takeIf{it.isNotBlank()}?.let{ hostId = it }
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, isVideo)
                val hasAudioPerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCameraPerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED
                if (isVideo && !hasCameraPerm) isVideo = false
                meetingId = mId
                ZoomRuntime.isVideoEnabled = isVideo
                ZoomRuntime.state = ZoomUiState.Active(mId, isVideo, listOf(ZoomMember(myUserId, myUserId, ZoomMemberStatus.JOINED, hasVideo=isVideo)), System.currentTimeMillis(), ZoomRuntime.meetingTitle)
                ensureNetworkWatcher()
                stopRingtone(); prepareAudio(isVideo); stopForeground(STOP_FOREGROUND_REMOVE); promoteToForeground()
                scope.launch { signaling.connect(); signaling.send(CallSignal(callId=mId, type="ZOOM_ACCEPT", groupCallId=mId)) }
            }
            ACTION_DECLINE_ZOOM -> {
                ringTimeout?.cancel(); incomingTimeout?.cancel(); stopRingtone()
                val mId = intent.getStringExtra(EXTRA_MEETING_ID) ?: meetingId
                scope.launch { runCatching{ signaling.connect() }; signaling.send(CallSignal(callId=mId, type="ZOOM_DECLINE", groupCallId=mId)) }
                stopZoom()
            }
            ACTION_END_ZOOM -> stopZoom()
            ACTION_TOGGLE_MIC -> {
                ZoomRuntime.isMuted = !ZoomRuntime.isMuted
                val on = !ZoomRuntime.isMuted
                engine?.setMicrophoneEnabled(on); mesh?.setMicrophoneEnabled(on); sfu?.setMicrophoneEnabled(on)
            }
            ACTION_TOGGLE_VIDEO -> {
                val isOn = ZoomRuntime.localVideo?.enabled() == true
                if (!isOn && ZoomRuntime.localVideo==null) {
                    scope.launch {
                        val ok = sfu?.retryCamera() ?: run { val m=mesh?.retryCamera(); val e=engine?.retryCamera(); m==true||e==true }
                        if (ok) { ZoomRuntime.localVideo = sfu?.localVideo ?: mesh?.localVideo ?: engine?.localMedia?.videoTrack; ZoomRuntime.isVideoEnabled=true }
                    }
                } else {
                    engine?.setCameraEnabled(!isOn); mesh?.setCameraEnabled(!isOn); sfu?.setCameraEnabled(!isOn); ZoomRuntime.isVideoEnabled=!isOn
                }
            }
            ACTION_SWITCH_CAMERA -> { engine?.switchCamera(); mesh?.switchCamera(); sfu?.switchCamera() }
            ACTION_TOGGLE_SPEAKER -> { audio.isSpeakerphoneOn = !audio.isSpeakerphoneOn }
            ACTION_RAISE_HAND -> {
                ZoomRuntime.isHandRaised = !ZoomRuntime.isHandRaised
                signaling.send(CallSignal(callId=meetingId, type=if(ZoomRuntime.isHandRaised) "ZOOM_RAISE_HAND" else "ZOOM_LOWER_HAND", groupCallId=meetingId))
                // تحديث محلي
                updateSelfHand(ZoomRuntime.isHandRaised)
            }
            ACTION_MUTE_ALL -> {
                if (isHost) {
                    ZoomRuntime.isMuted = true; engine?.setMicrophoneEnabled(false); mesh?.setMicrophoneEnabled(false); sfu?.setMicrophoneEnabled(false)
                    signaling.send(CallSignal(callId=meetingId, type="ZOOM_MUTE_ALL", groupCallId=meetingId, mode=if(isVideo)"VIDEO" else "VOICE"))
                }
            }
            ACTION_START_SCREEN_SHARE -> {
                val data: Intent? = if (Build.VERSION.SDK_INT>=33) intent.getParcelableExtra("screen_data", Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra("screen_data")
                if (data!=null) {
                    val track = mesh?.startScreenShare(data) ?: engine?.startScreenShare(data)
                    if (track!=null){ ZoomRuntime.localVideo=track; ZoomRuntime.isScreenSharing=true; ZoomRuntime.isVideoEnabled=true }
                }
            }
            ACTION_STOP_SCREEN_SHARE -> {
                val track = mesh?.stopScreenShare() ?: engine?.stopScreenShare()
                ZoomRuntime.localVideo=track; ZoomRuntime.isScreenSharing=false; if(track==null) ZoomRuntime.isVideoEnabled=false
            }
            ACTION_START_RECORDING -> {
                // تسجيل اجتماع Zoom مشفَّر AES-GCM — كان الـoverlay يرسل هذا الأمر
                // لكن الخدمة لم تعالجه فبقي recordingManager ميتاً والزر بلا أثر.
                // يعيد استخدام نفس محرّك التسجيل الموحّد (CallRecordingManager) الذي
                // يعمل فعلاً في GroupCallService، بموافقة صريحة لا تُفترض.
                if (recordingManager == null && meetingId.isNotBlank()) {
                    recordingManager = CallRecordingManager(this, meetingId)
                }
                ZoomRuntime.isRecording = recordingManager?.start(consentGranted = true) == true
            }
            ACTION_STOP_RECORDING -> {
                scope.launch { recordingManager?.stop(); recordingManager = null }
                ZoomRuntime.isRecording = false
            }
            ACTION_ADD_PARTICIPANT -> {
                val newIds = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                val newNames = intent.getStringArrayListExtra(EXTRA_INVITEE_NAMES) ?: arrayListOf()
                if (newIds.isEmpty() || !isHost) return START_STICKY
                val cur = ZoomRuntime.state
                val existing = when(cur){ is ZoomUiState.Ringing->cur.members.map{it.userId}+myUserId; is ZoomUiState.Active->cur.members.map{it.userId}+myUserId; else->emptyList() }
                val fresh = newIds.filterIndexed{ i, id-> id.isNotBlank() && id !in existing && id!=myUserId }
                if (fresh.isEmpty()) return START_STICKY
                scope.launch(Dispatchers.Main.immediate){
                    when(val s=ZoomRuntime.state){
                        is ZoomUiState.Ringing -> ZoomRuntime.state = s.copy(members=s.members+fresh.mapIndexed{i,id-> ZoomMember(id, newNames.getOrElse(i){id}, ZoomMemberStatus.RINGING)})
                        is ZoomUiState.Active -> ZoomRuntime.state = s.copy(members=s.members+fresh.mapIndexed{i,id-> ZoomMember(id, newNames.getOrElse(i){id}, ZoomMemberStatus.RINGING)})
                        else->{}
                    }
                }
                signaling.send(CallSignal(callId=meetingId, type="ZOOM_INVITE", groupCallId=meetingId, inviteeIds=fresh, payload=mapOf("hostName" to hostDisplayName, "title" to ZoomRuntime.meetingTitle, "isVideo" to isVideo.toString())))
            }
            ACTION_TOGGLE_LOCK -> {
                if (!isHost) return START_STICKY
                ZoomRuntime.isLocked = !ZoomRuntime.isLocked
                signaling.send(CallSignal(callId=meetingId, type=if(ZoomRuntime.isLocked) "ZOOM_LOCK" else "ZOOM_UNLOCK", groupCallId=meetingId))
            }
            ACTION_TOGGLE_WAITING_ROOM -> {
                if (!isHost) return START_STICKY
                ZoomRuntime.isWaitingRoomEnabled = !ZoomRuntime.isWaitingRoomEnabled
                signaling.send(CallSignal(callId=meetingId, type=if(ZoomRuntime.isWaitingRoomEnabled) "ZOOM_WAITING_ON" else "ZOOM_WAITING_OFF", groupCallId=meetingId))
            }
            ACTION_CREATE_POLL -> {
                val q = intent.getStringExtra("poll_question").orEmpty()
                val opts = intent.getStringArrayListExtra("poll_options") ?: arrayListOf()
                if (q.isBlank() || opts.size < 2) return START_STICKY
                val poll = ZoomPoll(question=q, options=opts)
                ZoomRuntime.activePoll = poll
                signaling.send(CallSignal(callId=meetingId, type="ZOOM_POLL_CREATE", groupCallId=meetingId, payload=mapOf("pollId" to poll.id, "question" to q, "options" to opts.joinToString("|"))))
            }
            ACTION_VOTE_POLL -> {
                val pollId = intent.getStringExtra("poll_id").orEmpty()
                val idx = intent.getIntExtra("poll_option", -1)
                val cur = ZoomRuntime.activePoll
                if (cur==null || cur.id!=pollId || idx !in cur.options.indices) return START_STICKY
                val updatedVotes = cur.votes.toMutableMap()
                updatedVotes[myUserId] = idx
                ZoomRuntime.activePoll = cur.copy(votes=updatedVotes)
                signaling.send(CallSignal(callId=meetingId, type="ZOOM_POLL_VOTE", groupCallId=meetingId, payload=mapOf("pollId" to pollId, "option" to idx.toString())))
            }
            ACTION_CREATE_BREAKOUT -> {
                if (!isHost) return START_STICKY
                val count = intent.getIntExtra("breakout_count", 2).coerceIn(2,8)
                val members = when(val cur=ZoomRuntime.state){ is ZoomUiState.Active->cur.members.filter{it.status==ZoomMemberStatus.JOINED}; is ZoomUiState.Ringing->cur.members; else->emptyList() }
                val rooms = (0 until count).map { idx -> ZoomBreakoutRoom(id="br_${meetingId}_$idx", name="غرفة ${idx+1}", participantIds= members.filterIndexed{ i,_ -> i%count==idx }.map{it.userId}) }
                ZoomRuntime.breakoutRooms = rooms
                signaling.send(CallSignal(callId=meetingId, type="ZOOM_BREAKOUT_CREATE", groupCallId=meetingId, payload=mapOf("rooms" to rooms.joinToString(";"){ "${it.name}:${it.participantIds.joinToString(",")}"})))
            }
        }
        return START_STICKY
    }

    private suspend fun registerZoomRoom(meetingId: String, title: String, isVideo: Boolean) {
        runCatching {
            AuthorizedApiClient(TokenStore(this)).request("POST","/api/zoom/create", org.json.JSONObject().put("meetingId",meetingId).put("title",title).put("isVideo",isVideo).toString())
        }
    }

    override fun onConnected() {
        scope.launch {
            if (isHost) {
                val state = ZoomRuntime.state
                if (state is ZoomUiState.Ringing) {
                    signaling.send(CallSignal(callId=meetingId, type="ZOOM_INVITE", groupCallId=meetingId, inviteeIds=state.members.map{it.userId}, payload=mapOf("hostName" to hostDisplayName, "title" to ZoomRuntime.meetingTitle, "isVideo" to isVideo.toString())))
                }
            }
            var sfuFailed=false
            if (meetingId.isNotBlank() && meetingId.length in 4..128) {
                sfu = SfuMediaClient(this@ZoomGroupCallService, TokenStore(this@ZoomGroupCallService), this@ZoomGroupCallService)
                if (attachSfuWithRetry(sfu!!, meetingId)) {
                    val kind = if(isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
                    val published = sfu?.publish(kind)==true
                    ZoomRuntime.eglContext = sfu?.eglContext
                    ZoomRuntime.localVideo = sfu?.localVideo
                    val ok = published && (!isVideo || ZoomRuntime.localVideo!=null)
                    if (ok){ startRingbackForHost(); return@launch } else { sfu?.release(); sfu=null; sfuFailed=true }
                } else { sfu?.release(); sfu=null; sfuFailed=true }
            } else sfuFailed=true
            val kind = if(isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
            val meshOk = startMesh(kind)
            android.util.Log.d("ZoomService","mesh start $meshOk localVideo=${ZoomRuntime.localVideo!=null}")
            if (sfuFailed && isHost) signaling.send(CallSignal(callId=meetingId, type=CallSignal.USE_MESH, groupCallId=meetingId))
            if (!isHost && hostId.isNotBlank()){ mesh?.attachPeer(hostId); mesh?.offerTo(hostId) }
            startRingbackForHost()
        }
    }

    private suspend fun startMesh(kind: CallMediaKind): Boolean {
        mesh = MeshRtcSession(this, myUserId, this)
        val result = mesh?.start(kind)
        ZoomRuntime.eglContext = mesh?.eglContext
        if (isVideo) ZoomRuntime.localVideo = mesh?.localVideo
        val ok = result is ApiResult.Success
        if (!ok) android.util.Log.w("ZoomService","startMesh failed $result")
        return ok
    }

    private suspend fun attachSfuWithRetry(sfu: SfuMediaClient, roomId: String): Boolean {
        repeat(4){ if(sfu.attach(roomId)) return true; if(it<3) delay(350) }
        return false
    }

    private fun startRingbackForHost(){ if(!isHost) return; stopRingback(); runCatching{ ringback=ToneGenerator(AudioManager.STREAM_VOICE_CALL,70).also{ it.startTone(ToneGenerator.TONE_SUP_RINGTONE,45_000)}}}
    private fun stopRingback(){ runCatching{ ringback?.stopTone(); ringback?.release()}; ringback=null}
    private fun startRingtone(){
        stopRingtone()
        try{
            ringtone=RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply{isLooping=true; play()}
            vibrator=if(Build.VERSION.SDK_INT>=31) getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            vibrator?.let{ vib-> val pat=longArrayOf(0,800,400,800); if(Build.VERSION.SDK_INT>=26) vib.vibrate(VibrationEffect.createWaveform(pat,0)) else @Suppress("DEPRECATION") vib.vibrate(pat,0)}
        }catch(_:Exception){}
    }
    private fun stopRingtone(){ try{ringtone?.stop()}catch(_:Exception){}; ringtone=null; try{vibrator?.cancel()}catch(_:Exception){}; vibrator=null }

    override fun onSignal(signal: CallSignal) {
        when(signal.type){
            "ZOOM_ACCEPT" -> {
                val joinerId=signal.sourceUserId.orEmpty()
                if(joinerId.isNotBlank()){ mesh?.attachPeer(joinerId); mesh?.offerTo(joinerId) }
                val cur=ZoomRuntime.state
                scope.launch(Dispatchers.Main.immediate){
                    when(cur){
                        is ZoomUiState.Ringing -> {
                            val updated=cur.members.map{ if(it.userId==joinerId) it.copy(status=ZoomMemberStatus.JOINED) else it }
                            stopRingback(); ZoomRuntime.state=ZoomUiState.Active(cur.meetingId,cur.isVideo,updated,System.currentTimeMillis(), ZoomRuntime.meetingTitle)
                        }
                        is ZoomUiState.Active -> ZoomRuntime.state=cur.copy(members=cur.members.map{ if(it.userId==joinerId) it.copy(status=ZoomMemberStatus.JOINED) else it})
                        else->{}
                    }
                }
            }
            "ZOOM_DECLINE" -> updateMemberStatus(signal.sourceUserId.orEmpty(), ZoomMemberStatus.DECLINED)
            "ZOOM_END" -> stopZoom()
            "ZOOM_INVITE" -> {
                val mId = signal.callId ?: signal.groupCallId ?: ""
                val isVideo = signal.mode.equals("VIDEO", ignoreCase = true)
                val hostId = signal.sourceUserId.orEmpty()
                val hostName = signal.payload["hostName"]?.toString() ?: ""
                val title = signal.payload["title"]?.toString() ?: "اجتماع Zoom"
                val otherIds = signal.payload["inviteeIds"]?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

                if (mId.isBlank()) return

                scope.launch(Dispatchers.Main.immediate) {
                    // تحديث الحالة إلى مكالمة واردة
                    ZoomRuntime.state = ZoomUiState.Incoming(mId, hostId, hostName, isVideo, otherIds, title)
                    // بدء الرنين
                    startRingtone()
                    // إشعار محلي
                    showIncomingZoomNotification(mId, hostName, otherIds.size, isVideo, title)
                    // الاتصال بالخادم للرد
                    prepareAudio(isVideo)
                    scope.launch { signaling.connect() }
                }
            }
            "ZOOM_MUTE_ALL" -> { ZoomRuntime.isMuted=true; mesh?.setMicrophoneEnabled(false); sfu?.setMicrophoneEnabled(false); engine?.setMicrophoneEnabled(false) }
            "ZOOM_RAISE_HAND" -> updateHand(signal.sourceUserId.orEmpty(), true)
            "ZOOM_LOWER_HAND" -> updateHand(signal.sourceUserId.orEmpty(), false)
            "ZOOM_LOCK" -> ZoomRuntime.isLocked = true
            "ZOOM_UNLOCK" -> ZoomRuntime.isLocked = false
            "ZOOM_WAITING_ON" -> ZoomRuntime.isWaitingRoomEnabled = true
            "ZOOM_WAITING_OFF" -> ZoomRuntime.isWaitingRoomEnabled = false
            "ZOOM_POLL_CREATE" -> {
                val q = signal.payload["question"].orEmpty()
                val opts = signal.payload["options"]?.split("|") ?: emptyList()
                val pid = signal.payload["pollId"].orEmpty()
                if (q.isNotBlank() && opts.size>=2) ZoomRuntime.activePoll = ZoomPoll(id=pid.ifBlank{UUID.randomUUID().toString()}, question=q, options=opts)
            }
            "ZOOM_POLL_VOTE" -> {
                val pid = signal.payload["pollId"].orEmpty()
                val opt = signal.payload["option"]?.toIntOrNull() ?: -1
                val voter = signal.sourceUserId.orEmpty()
                val cur = ZoomRuntime.activePoll
                if (cur!=null && cur.id==pid && opt in cur.options.indices && voter.isNotBlank()) {
                    val updated = cur.votes.toMutableMap()
                    updated[voter]=opt
                    ZoomRuntime.activePoll = cur.copy(votes=updated)
                }
            }
            "ZOOM_BREAKOUT_CREATE" -> {
                val roomsRaw = signal.payload["rooms"].orEmpty()
                if (roomsRaw.isNotBlank()) {
                    val rooms = roomsRaw.split(";").mapIndexed { idx, entry ->
                        val parts = entry.split(":", limit = 2)
                        val name = parts.getOrNull(0) ?: "غرفة ${idx+1}"
                        val ids = parts.getOrNull(1)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                        ZoomBreakoutRoom(id = "br_${meetingId}_$idx", name = name, participantIds = ids)
                    }
                    ZoomRuntime.breakoutRooms = rooms
                }
            }
            "ZOOM_BREAKOUT_ASSIGN" -> {
                val roomId = signal.payload["roomId"]?.toString().orEmpty()
                val userId = signal.payload["userId"]?.toString().orEmpty()
                if (roomId.isNotBlank() && userId.isNotBlank()) {
                    ZoomRuntime.breakoutRooms = ZoomRuntime.breakoutRooms.map { room ->
                        if (room.id == roomId) room.copy(participantIds = room.participantIds + userId) else room
                    }
                }
            }
            "ZOOM_BREAKOUT_MOVE" -> {
                val roomId = signal.payload["roomId"]?.toString().orEmpty()
                val userId = signal.payload["userId"]?.toString().orEmpty()
                if (roomId.isNotBlank() && userId.isNotBlank()) {
                    ZoomRuntime.breakoutRooms = ZoomRuntime.breakoutRooms.map { room ->
                        if (room.id == roomId) room.copy(participantIds = room.participantIds + userId) else room
                    }
                }
            }
            "ZOOM_BREAKOUT_DELETE" -> {
                val roomId = signal.payload["roomId"]?.toString().orEmpty()
                if (roomId.isNotBlank()) {
                    ZoomRuntime.breakoutRooms = ZoomRuntime.breakoutRooms.filter { it.id != roomId }
                }
            }
            "ZOOM_BREAKOUT_CLOSE_ALL" -> {
                ZoomRuntime.breakoutRooms = emptyList()
            }
            "ZOOM_BREAKOUT_BROADCAST" -> {
                val text = signal.payload["text"]?.toString()?.orEmpty()
            }
            "ZOOM_BREAKOUT_TIMER_START" -> {
                // Timer start logic handled locally
            }
            "ZOOM_BREAKOUT_TIMER_STOP" -> {
                // Timer stop logic handled locally
            }
            "OFFER" -> signal.payload["sdp"]?.let{ sdp-> val from=signal.sourceUserId.orEmpty(); if(from.isNotBlank()) mesh?.handleOffer(from, sdp) else engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)){ engine?.answer() } }
            "ANSWER" -> signal.payload["sdp"]?.let{ sdp-> val from=signal.sourceUserId.orEmpty(); if(from.isNotBlank()) mesh?.handleAnswer(from, sdp) else engine?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp)) }
            "ICE" -> {
                val from=signal.sourceUserId.orEmpty()
                val cand=IceCandidate(signal.payload["sdpMid"], signal.payload["sdpMLineIndex"]?.toIntOrNull()?:0, signal.payload["candidate"].orEmpty())
                if(from.isNotBlank()) mesh?.handleIce(from, cand) else engine?.addIce(cand)
            }
            "PARTICIPANT_LEFT" -> { val id=signal.sourceUserId.orEmpty(); updateMemberStatus(id, ZoomMemberStatus.LEFT); if(id.isNotBlank()) mesh?.detachPeer(id) }
            CallSignal.USE_MESH -> {
                if(sfu!=null){
                    sfu?.release(); sfu=null
                    scope.launch{
                        val kind=if(isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
                        startMesh(kind)
                        val peers = when(val cur=ZoomRuntime.state){ is ZoomUiState.Ringing->cur.members.map{it.userId}; is ZoomUiState.Active->cur.members.map{it.userId}; else->emptyList()} + listOf(hostId).filter{it.isNotBlank() && it!=myUserId}
                        peers.filter{it.isNotBlank()}.forEach{ pid-> mesh?.attachPeer(pid); mesh?.offerTo(pid)}
                    }
                } else if(mesh==null){
                    scope.launch{ startMesh(if(isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE) }
                }
            }
        }
    }

    override fun onPeerLeft(peerId: String){ updateMemberStatus(peerId, ZoomMemberStatus.LEFT); scope.launch(Dispatchers.Main.immediate){ ZoomRuntime.remoteVideos = ZoomRuntime.remoteVideos - peerId } }
    override fun onLocalDescription(description: SessionDescription){ val t=if(description.type==SessionDescription.Type.ANSWER) "ANSWER" else "OFFER"; signaling.send(CallSignal(callId=meetingId, type=t, groupCallId=meetingId, payload=mapOf("sdp" to description.description))) }
    override fun onLocalDescription(peerId: String, description: SessionDescription){ val t=if(description.type==SessionDescription.Type.ANSWER) "ANSWER" else "OFFER"; signaling.send(CallSignal(callId=meetingId, targetUserId=peerId, type=t, groupCallId=meetingId, payload=mapOf("sdp" to description.description))) }
    override fun onIceCandidate(candidate: IceCandidate){ signaling.send(CallSignal(callId=meetingId, type="ICE", groupCallId=meetingId, payload=mapOf("sdpMid" to (candidate.sdpMid?:""), "sdpMLineIndex" to candidate.sdpMLineIndex.toString(), "candidate" to (candidate.sdp?:"")))) }
    override fun onIceCandidate(peerId: String, candidate: IceCandidate){ signaling.send(CallSignal(callId=meetingId, targetUserId=peerId, type="ICE", groupCallId=meetingId, payload=mapOf("sdpMid" to (candidate.sdpMid?:""), "sdpMLineIndex" to candidate.sdpMLineIndex.toString(), "candidate" to (candidate.sdp?:"")))) }
    override fun onRemoteAudio(track: AudioTrack){ track.setEnabled(true) }
    override fun onRemoteVideo(track: VideoTrack){ track.setEnabled(true); scope.launch(Dispatchers.Main.immediate){ ZoomRuntime.remoteVideos = ZoomRuntime.remoteVideos + ("remote" to track)} }
    override fun onRemoteVideo(peerId: String, track: VideoTrack){
        android.util.Log.d("ZoomService","onRemoteVideo peer=$peerId track=${track.id()} egl=${ZoomRuntime.eglContext!=null}")
        track.setEnabled(true); scope.launch(Dispatchers.Main.immediate){ ZoomRuntime.remoteVideos = ZoomRuntime.remoteVideos + (peerId to track) }
    }
    override fun onNetworkStats(stats: NetworkStats){ ZoomRuntime.networkStats=stats }
    override fun onCameraUnavailable(){ ZoomRuntime.isVideoEnabled=false; ZoomRuntime.localVideo=null }
    override fun onConnectionState(state: PeerConnection.PeerConnectionState){ if(state==PeerConnection.PeerConnectionState.FAILED) { engine?.restartIce(); mesh?.restartIce() } }
    override fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState){ if(state==PeerConnection.PeerConnectionState.DISCONNECTED||state==PeerConnection.PeerConnectionState.FAILED) scope.launch{ delay(1500); mesh?.offerTo(peerId)} }
    override fun onDisconnected(){ if(!stopping) runCatching{ signaling.reconnect() } }
    override fun onError(message: String){ if(message=="UNAUTHORIZED") stopZoom() }

    private fun updateMemberStatus(userId: String, status: ZoomMemberStatus){
        if(userId.isBlank()) return
        scope.launch(Dispatchers.Main.immediate){
            when(val cur=ZoomRuntime.state){
                is ZoomUiState.Ringing -> ZoomRuntime.state=cur.copy(members=cur.members.map{ if(it.userId==userId) it.copy(status=status) else it})
                is ZoomUiState.Active -> ZoomRuntime.state=cur.copy(members=cur.members.map{ if(it.userId==userId) it.copy(status=status) else it})
                else->{}
            }
        }
    }
    private fun updateHand(userId: String, raised: Boolean){
        scope.launch(Dispatchers.Main.immediate){
            when(val cur=ZoomRuntime.state){
                is ZoomUiState.Active -> ZoomRuntime.state=cur.copy(members=cur.members.map{ if(it.userId==userId) it.copy(isHandRaised=raised) else it})
                is ZoomUiState.Ringing -> ZoomRuntime.state=cur.copy(members=cur.members.map{ if(it.userId==userId) it.copy(isHandRaised=raised) else it})
                else->{}
            }
        }
    }
    private fun updateSelfHand(raised: Boolean){
        scope.launch(Dispatchers.Main.immediate){
            when(val cur=ZoomRuntime.state){
                is ZoomUiState.Active -> ZoomRuntime.state=cur.copy(members=cur.members.map{ if(it.userId==myUserId) it.copy(isHandRaised=raised) else it})
                else->{}
            }
        }
    }
    private fun ensureNetworkWatcher(){
        if(networkWatcher==null) networkWatcher=NetworkChangeWatcher(this){ if(!stopping && ZoomRuntime.state is ZoomUiState.Active) mesh?.restartIce() }.also{ it.start()}
    }
    private fun stopNetworkWatcher(){ networkWatcher?.stop(); networkWatcher=null }
    private var networkWatcher: NetworkChangeWatcher? = null

    private fun stopZoom(){
        if(stopping) return
        stopping=true; ringTimeout?.cancel(); stopNetworkWatcher()
        if(isHost && meetingId.isNotBlank()) runCatching{ signaling.send(CallSignal(callId=meetingId, type="ZOOM_END", groupCallId=meetingId)) }
        scope.launch(Dispatchers.Main.immediate){ finishStop() }
    }
    private fun finishStop(){
        if(cleanedUp) return
        cleanedUp=true; ringTimeout?.cancel(); incomingTimeout?.cancel(); stopRingback(); stopRingtone(); releaseAudio()
        engine?.release(); engine=null; mesh?.release(); mesh=null; sfu?.release(); sfu=null; signaling.close(); ZoomRuntime.activeSignaling=null
        ZoomRuntime.state=ZoomUiState.Ended; ZoomRuntime.localVideo=null; ZoomRuntime.remoteVideos=emptyMap(); ZoomRuntime.eglContext=null
        ZoomRuntime.isMuted=false; ZoomRuntime.isHost=false; ZoomRuntime.isVideoEnabled=false; ZoomRuntime.isScreenSharing=false; ZoomRuntime.isRecording=false; ZoomRuntime.isMinimized=false; ZoomRuntime.isHandRaised=false; ZoomRuntime.networkStats=NetworkStats()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    private fun promoteToForeground(){
        val label=if(isVideo) "اجتماع Zoom فيديو" else "اجتماع Zoom صوتي"
        val intent=PendingIntent.getActivity(this,0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val endPi=CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_END, CallNotificationActionReceiver.CALL_TYPE_ZOOM, 9200, callId = meetingId, myUserId = myUserId, hostId = hostId, isVideo = isVideo)
        val mutePi=CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_TOGGLE_MIC, CallNotificationActionReceiver.CALL_TYPE_ZOOM, 9200, callId = meetingId, myUserId = myUserId, hostId = hostId, isVideo = isVideo)
        val notif=NotificationCompat.Builder(this,"red_calls").setSmallIcon(android.R.drawable.stat_sys_phone_call).setContentTitle(label).setContentText("اجتماع Zoom جاري...").setContentIntent(intent).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setColor(0xFF2AABEE.toInt()).setOngoing(true).setSilent(true).addAction(0,"كتم",mutePi).addAction(0,"إنهاء",endPi).build()
        var type=ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE; if(isVideo) type=type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        ServiceCompat.startForeground(this, 9200, notif, type)
    }
    private fun showIncomingZoomNotification(mId: String, hostName: String, otherCount: Int, video: Boolean, title: String){
        val label=if(video) "اجتماع Zoom فيديو" else "اجتماع Zoom صوتي"
        val body=if(otherCount>0) "من $hostName ومعه $otherCount آخرون · $title" else "من $hostName · $title"
        val acceptPi=CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_ACCEPT, CallNotificationActionReceiver.CALL_TYPE_ZOOM, 9201, callId = mId, myUserId = myUserId, hostId = hostId, isVideo = video)
        val declinePi=CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_DECLINE, CallNotificationActionReceiver.CALL_TYPE_ZOOM, 9201, callId = mId, myUserId = myUserId, hostId = hostId, isVideo = video)
        val fullScreenIntent=PendingIntent.getActivity(this,32, Intent(this, MainActivity::class.java).apply{ addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP); putExtra("zoom_meeting_id",mId)}, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif=NotificationCompat.Builder(this,"red_calls").setSmallIcon(android.R.drawable.stat_sys_phone_call).setContentTitle(label).setContentText(body).setFullScreenIntent(fullScreenIntent,true).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX).setColor(0xFF2AABEE.toInt()).setOngoing(true).setAutoCancel(false).addAction(NotificationCompat.Action.Builder(0,"رفض",declinePi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).build()).addAction(NotificationCompat.Action.Builder(0,"قبول",acceptPi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL).build()).build()
        ServiceCompat.startForeground(this, 9201, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }
    override fun onDestroy(){ finishStop(); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder?=null

    companion object {
        const val ACTION_START_ZOOM = "com.red.sovereign.zoom.START"
        const val ACTION_INCOMING_ZOOM = "com.red.sovereign.zoom.INCOMING"
        const val ACTION_ACCEPT_ZOOM = "com.red.sovereign.zoom.ACCEPT"
        const val ACTION_DECLINE_ZOOM = "com.red.sovereign.zoom.DECLINE"
        const val ACTION_END_ZOOM = "com.red.sovereign.zoom.END"
        const val ACTION_TOGGLE_MIC = "com.red.sovereign.zoom.TOGGLE_MIC"
        const val ACTION_TOGGLE_VIDEO = "com.red.sovereign.zoom.TOGGLE_VIDEO"
        const val ACTION_SWITCH_CAMERA = "com.red.sovereign.zoom.SWITCH_CAMERA"
        const val ACTION_TOGGLE_SPEAKER = "com.red.sovereign.zoom.TOGGLE_SPEAKER"
        const val ACTION_RAISE_HAND = "com.red.sovereign.zoom.RAISE_HAND"
        const val ACTION_MUTE_ALL = "com.red.sovereign.zoom.MUTE_ALL"
        const val ACTION_ADD_PARTICIPANT = "com.red.sovereign.zoom.ADD_PARTICIPANT"
        const val ACTION_START_SCREEN_SHARE = "com.red.sovereign.zoom.START_SCREEN_SHARE"
        const val ACTION_STOP_SCREEN_SHARE = "com.red.sovereign.zoom.STOP_SCREEN_SHARE"
        const val ACTION_START_RECORDING = "com.red.sovereign.zoom.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.red.sovereign.zoom.STOP_RECORDING"
        const val ACTION_TOGGLE_LOCK = "com.red.sovereign.zoom.TOGGLE_LOCK"
        const val ACTION_TOGGLE_WAITING_ROOM = "com.red.sovereign.zoom.TOGGLE_WAITING"
        const val ACTION_CREATE_POLL = "com.red.sovereign.zoom.CREATE_POLL"
        const val ACTION_VOTE_POLL = "com.red.sovereign.zoom.VOTE_POLL"
        const val ACTION_CREATE_BREAKOUT = "com.red.sovereign.zoom.CREATE_BREAKOUT"
        const val EXTRA_MEETING_ID = "zoom_meeting_id"
        const val EXTRA_MY_USER_ID = "my_user_id"
        const val EXTRA_HOST_ID = "host_id"
        const val EXTRA_HOST_NAME = "host_name"
        const val EXTRA_TITLE = "zoom_title"
        const val EXTRA_INVITEE_IDS = "invitee_ids"
        const val EXTRA_INVITEE_NAMES = "invitee_names"
        const val EXTRA_IS_VIDEO = "is_video"
        private const val TAG = "ZoomService"

        fun generateMeetingId(): String {
            val chars="ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..8).map{ chars.random() }.joinToString("").chunked(4).joinToString("-")
        }

        fun startZoom(context: Context, myUserId: String, inviteeIds: List<String>, inviteeNames: List<String>, isVideo: Boolean, title: String="اجتماع Zoom", meetingId: String=generateMeetingId(), hostName: String=""){
            ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).apply{
                action=ACTION_START_ZOOM; putExtra(EXTRA_MEETING_ID,meetingId); putExtra(EXTRA_MY_USER_ID,myUserId); putExtra(EXTRA_HOST_NAME,hostName); putExtra(EXTRA_IS_VIDEO,isVideo); putExtra(EXTRA_TITLE,title); putStringArrayListExtra(EXTRA_INVITEE_IDS, ArrayList(inviteeIds)); putStringArrayListExtra(EXTRA_INVITEE_NAMES, ArrayList(inviteeNames))
            })
        }
        fun notifyIncoming(context: Context, meetingId: String, myUserId: String, hostId: String, hostName: String, isVideo: Boolean, title: String="", otherIds: List<String> = emptyList()){
            ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).apply{
                action=ACTION_INCOMING_ZOOM; putExtra(EXTRA_MEETING_ID,meetingId); putExtra(EXTRA_MY_USER_ID,myUserId); putExtra(EXTRA_HOST_ID,hostId); putExtra(EXTRA_HOST_NAME,hostName); putExtra(EXTRA_IS_VIDEO,isVideo); putExtra(EXTRA_TITLE,title); putStringArrayListExtra(EXTRA_INVITEE_IDS, ArrayList(otherIds))
            })
        }
        fun accept(context: Context, meetingId: String, myUserId: String, isVideo: Boolean, hostId: String=""){
            ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).apply{ action=ACTION_ACCEPT_ZOOM; putExtra(EXTRA_MEETING_ID,meetingId); putExtra(EXTRA_MY_USER_ID,myUserId); putExtra(EXTRA_IS_VIDEO,isVideo); if(hostId.isNotBlank()) putExtra(EXTRA_HOST_ID,hostId)})
        }
        fun decline(context: Context, meetingId: String){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_DECLINE_ZOOM).putExtra(EXTRA_MEETING_ID,meetingId)) }
        fun end(context: Context){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_END_ZOOM)) }
        fun action(context: Context, act: String){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(act)) }
        fun startScreenShare(context: Context, data: Intent){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_START_SCREEN_SHARE).putExtra("screen_data",data)) }
        fun stopScreenShare(context: Context){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_STOP_SCREEN_SHARE)) }
        fun toggleLock(context: Context){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_TOGGLE_LOCK)) }
        fun toggleWaitingRoom(context: Context){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_TOGGLE_WAITING_ROOM)) }
        fun createPoll(context: Context, question: String, options: List<String>){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_CREATE_POLL).putExtra("poll_question", question).putStringArrayListExtra("poll_options", ArrayList(options))) }
        fun votePoll(context: Context, pollId: String, option: Int){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_VOTE_POLL).putExtra("poll_id", pollId).putExtra("poll_option", option)) }
        fun createBreakout(context: Context, count: Int){ ContextCompat.startForegroundService(context, Intent(context, ZoomGroupCallService::class.java).setAction(ACTION_CREATE_BREAKOUT).putExtra("breakout_count", count)) }
    }
}
