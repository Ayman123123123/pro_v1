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

/** واتساب: حد المشاركين — 32 للمجموعات (SFU يوفر التوسع، Mesh يتراجع تلقائياً). */
const val WHATSAPP_GROUP_CALL_LIMIT = 32

// ─────────────────────────────────────────────────────────────────────────────
// حالات واجهة المستخدم
// ─────────────────────────────────────────────────────────────────────────────

/** حالة كل مدعو في المكالمة الجماعية */
enum class GroupCallMemberStatus {
    RINGING,   // جاري الرنين
    JOINED,    // انضم
    DECLINED,  // رفض
    NO_ANSWER, // لم يرد
    LEFT,      // غادر
    BUSY       // مشغول بمكالمة أخرى (يكتشفها الخادم)
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
    var isRecording by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    // واتساب: المجموعة التي تنتمي لها المكالمة الحالية — للبنر داخل الدردشة
    var activeGroupId: String by mutableStateOf("")
    var activeGroupName: String by mutableStateOf("")
    // 📱 تصغير المكالمة الجماعية (نافذة عائمة أثناء التصفح مثل واتساب)
    var isMinimized by mutableStateOf(false)
}

// ─────────────────────────────────────────────────────────────────────────────
// الخدمة الرئيسية
// ─────────────────────────────────────────────────────────────────────────────

/**
 * خدمة المكالمات الجماعية — نمط واتساب النقي.
 *
 * سلوك واتساب للمجموعات (مستقل تماماً عن المؤتمرات/المساحات/Zoom):
 * • زرّان منفصلان في ترويسة المجموعة: 📞 صوت و 🎥 فيديو.
 * • كل زر يرن جميع أعضاء المجموعة (حتى 32) عبر GroupCallService.
 * • أول من يقبل → تبدأ المكالمة فعلياً (SFU أولاً ثم Mesh كاحتياط).
 * • البقية يمكنهم الانضمام حتى بعد البدء (واتساب: Join).
 * • المؤتمرات/المساحات/Mega-Zoom هي خدمات مستقلة تماماً عبر ConferenceService/LiveStream.
 */
class GroupCallService : Service(), WebRtcEngine.Events, MeshRtcSession.Events, CallSignalingClient.Listener, SfuMediaClient.Events {

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

    private var groupCallId = ""
    private var sourceGroupId = ""
    private var myUserId = ""
    private var hostId = ""
    private var hostDisplayName = ""
    private var isHost = false
    private var isVideo = false
    private var stopping = false
    private var cleanedUp = false

    // مهلة الرنين — 45 ثانية قبل اعتبار الأعضاء "لم يردوا"
    private var ringTimeout: kotlinx.coroutines.Job? = null
    // مهلة الرنين الواردة — 30 ثانية دون رد → رفض تلقائي (تظهر للمضيف "لم يرد")
    private var incomingRingTimeout: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH)
        )
        signaling = CallSignalingClient(this, TokenStore(this), this)
    }

    private fun prepareAudio(isVideo: Boolean) {
        try {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            // واتساب: مكالمات الفيديو دائماً على السماعة، الصوتية حسب التبديل
            audio.isSpeakerphoneOn = isVideo || GroupCallRuntime.isVideoEnabled
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
            audioFocus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener {}.build()
            audio.requestAudioFocus(audioFocus!!)
        } catch (_: Exception) {}
    }

    private fun releaseAudio() {
        try {
            audioFocus?.let { audio.abandonAudioFocusRequest(it) }
            audio.mode = AudioManager.MODE_NORMAL
            audio.isSpeakerphoneOn = false
        } catch (_: Exception) {}
        audioFocus = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GROUP_CALL -> {
                stopping = false; cleanedUp = false
                groupCallId = intent.getStringExtra(EXTRA_GROUP_CALL_ID) ?: UUID.randomUUID().toString()
                sourceGroupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID).orEmpty()
                hostDisplayName = intent.getStringExtra(EXTRA_HOST_NAME).orEmpty()
                isHost = true
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val invitees = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                val names = intent.getStringArrayListExtra(EXTRA_INVITEE_NAMES) ?: arrayListOf()

                GroupCallRuntime.activeGroupId = sourceGroupId
                GroupCallRuntime.activeGroupName = intent.getStringExtra(EXTRA_GROUP_NAME).orEmpty()
                GroupCallRuntime.isHost = true
                GroupCallRuntime.isVideoEnabled = isVideo
                GroupCallRuntime.isMuted = false

                val members = invitees.mapIndexed { i, id ->
                    GroupCallMember(userId = id, displayName = names.getOrElse(i) { id }, status = GroupCallMemberStatus.RINGING)
                }
                GroupCallRuntime.state = GroupCallUiState.Ringing(groupCallId, isVideo, members)

                prepareAudio(isVideo)
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
                        updated.filter { it.status == GroupCallMemberStatus.NO_ANSWER }.forEach { member ->
                            signaling.send(
                                CallSignal(
                                    callId = groupCallId,
                                    type = "GROUP_CALL_STATUS",
                                    groupCallId = groupCallId,
                                    memberStatus = "no_answer",
                                    payload = mapOf("memberId" to member.userId)
                                )
                            )
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
                sourceGroupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
                GroupCallRuntime.activeGroupId = sourceGroupId
                GroupCallRuntime.activeGroupName = intent.getStringExtra(EXTRA_GROUP_NAME).orEmpty()
                GroupCallRuntime.isHost = false
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID).orEmpty()
                isHost = false
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
                val hostId = intent.getStringExtra(EXTRA_HOST_ID).orEmpty()
                this.hostId = hostId
                val hostName = intent.getStringExtra(EXTRA_HOST_NAME).orEmpty()
                val others = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                GroupCallRuntime.state = GroupCallUiState.IncomingGroup(groupCallId, hostId, hostName, isVideo, others)
                prepareAudio(isVideo)
                showIncomingGroupCallNotification(groupCallId, hostName, others.size, isVideo)
                // رنين المكالمة الجماعية الواردة — نغمة + اهتزاز مثل واتساب
                if (com.red.sovereign.settings.SettingsRuntime.current.callNotifications) startRingtone() else prepareAudio(isVideo)
                // مهلة الرنين الواردة: 30 ثانية دون رد → رفض تلقائي يظهر للمضيف كـ "لم يرد"
                incomingRingTimeout?.cancel()
                incomingRingTimeout = scope.launch {
                    delay(30_000)
                    val current = GroupCallRuntime.state
                    if (current is GroupCallUiState.IncomingGroup && current.groupCallId == groupCallId) {
                        runCatching { signaling.sendGroupCallResponse(groupCallId, accepted = false) }
                        stopGroupCall()
                    }
                }
            }

            ACTION_ACCEPT_GROUP_CALL -> {
                ringTimeout?.cancel()
                incomingRingTimeout?.cancel()
                val gId = intent.getStringExtra(EXTRA_GROUP_CALL_ID) ?: groupCallId
                myUserId = intent.getStringExtra(EXTRA_MY_USER_ID) ?: myUserId
                isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, isVideo)
                groupCallId = gId
                GroupCallRuntime.isVideoEnabled = isVideo
                // لا تبقَ واجهة "الدعوة الواردة" معلّقة — انتقل فوراً للواجهة النشطة
                // (المضيف سيرى انضمامنا، والوسائط تبدأ عبر onConnected بعد connect)
                GroupCallRuntime.state = GroupCallUiState.Active(
                    gId, isVideo,
                    listOf(GroupCallMember(userId = myUserId, displayName = myUserId, status = GroupCallMemberStatus.JOINED)),
                    System.currentTimeMillis()
                )
                ensureNetworkWatcher()
                // أزل إشعار "الدعوة الواردة" ثم ارفع إشعار المكالمة النشطة
                stopRingtone()
                prepareAudio(isVideo)
                stopForeground(STOP_FOREGROUND_REMOVE)
                promoteToForeground()
                scope.launch {
                    signaling.connect()
                    signaling.sendGroupCallResponse(gId, accepted = true)
                }
            }

            ACTION_DECLINE_GROUP_CALL -> {
                ringTimeout?.cancel()
                incomingRingTimeout?.cancel()
                stopRingtone()
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
                sfu?.setMicrophoneEnabled(on)
            }

            ACTION_TOGGLE_VIDEO -> {
                val isOn = GroupCallRuntime.localVideo?.enabled() == true
                if (!isOn && GroupCallRuntime.localVideo == null) {
                    // إعادة محاولة فتح الكاميرا (إذن مُنح لاحقاً أو خلل مؤقت)
                    scope.launch {
                        val ok = sfu?.retryCamera() ?: run {
                            // في وضع Mesh: mesh يُرسل فعلياً، engine يُعرض محلياً — نجح أحدهما يكفي
                            val m = mesh?.retryCamera()
                            val e = engine?.retryCamera()
                            m == true || e == true
                        }
                        if (ok) {
                            GroupCallRuntime.localVideo = sfu?.localVideo ?: mesh?.localVideo ?: engine?.localMedia?.videoTrack
                            GroupCallRuntime.isVideoEnabled = true
                        }
                    }
                } else {
                    engine?.setCameraEnabled(!isOn)
                    mesh?.setCameraEnabled(!isOn)
                    sfu?.setCameraEnabled(!isOn)
                    GroupCallRuntime.isVideoEnabled = !isOn
                }
            }

            ACTION_SWITCH_CAMERA -> {
                engine?.switchCamera()
                mesh?.switchCamera()
                sfu?.switchCamera()
            }

            ACTION_START_RECORDING -> {
                // موافقة صريحة من واجهة المستخدم — لا تُفترض أبداً
                val consent = intent.getBooleanExtra(YounesCallService.EXTRA_CONSENT, false)
                if (recordingManager == null && groupCallId.isNotBlank()) {
                    recordingManager = CallRecordingManager(this, groupCallId)
                }
                GroupCallRuntime.isRecording = recordingManager?.start(consentGranted = consent) == true
            }

            ACTION_STOP_RECORDING -> {
                scope.launch { recordingManager?.stop(); recordingManager = null }
                GroupCallRuntime.isRecording = false
            }
            ACTION_MUTE_ALL -> {
                if (isHost) {
                    GroupCallRuntime.isMuted = true
                    engine?.setMicrophoneEnabled(false)
                    mesh?.setMicrophoneEnabled(false)
                    sfu?.setMicrophoneEnabled(false)
                    signaling.send(
                        CallSignal(
                            callId = groupCallId,
                            type = "GROUP_CALL_MUTE_ALL",
                            groupCallId = groupCallId,
                            mode = if (isVideo) "VIDEO" else "VOICE"
                        )
                    )
                }
            }
            ACTION_TOGGLE_SPEAKER -> {
                val nowSpeaker = !audio.isSpeakerphoneOn
                audio.isSpeakerphoneOn = nowSpeaker
            }
            ACTION_ADD_PARTICIPANT -> {
                // إضافة مشارك أثناء المكالمة — مثل واتساب: يرن فقط إن لم يكن في المكالمة
                val newIds = intent.getStringArrayListExtra(EXTRA_INVITEE_IDS) ?: arrayListOf()
                val newNames = intent.getStringArrayListExtra(EXTRA_INVITEE_NAMES) ?: arrayListOf()
                if (newIds.isEmpty() || !isHost) return START_STICKY
                val cur = GroupCallRuntime.state
                val existingIds = when (cur) {
                    is GroupCallUiState.Ringing -> cur.members.map { it.userId } + myUserId
                    is GroupCallUiState.Active -> cur.members.map { it.userId } + myUserId
                    else -> emptyList()
                }
                val fresh = newIds.filterIndexed { i, id -> id.isNotBlank() && id !in existingIds && id != myUserId }
                if (fresh.isEmpty()) return START_STICKY
                // أضفهم للحالة كـ RINGING فوراً (واجهة)
                scope.launch(Dispatchers.Main.immediate) {
                    when (val s = GroupCallRuntime.state) {
                        is GroupCallUiState.Ringing -> GroupCallRuntime.state = s.copy(
                            members = s.members + fresh.mapIndexed { i, id ->
                                GroupCallMember(id, newNames.getOrElse(i) { id }, GroupCallMemberStatus.RINGING)
                            }
                        )
                        is GroupCallUiState.Active -> GroupCallRuntime.state = s.copy(
                            members = s.members + fresh.mapIndexed { i, id ->
                                GroupCallMember(id, newNames.getOrElse(i) { id }, GroupCallMemberStatus.RINGING)
                            }
                        )
                        else -> {}
                    }
                }
                // دعوة عبر الخادم — نفس مسار INVITE الأولي
                signaling.sendGroupCallInvite(groupCallId, fresh, isVideo, hostDisplayName, sourceGroupId)
                // سجّلهم كنشطين في الخادم ليتلقوا الرنين
                scope.launch {
                    runCatching {
                        AuthorizedApiClient(TokenStore(this@GroupCallService))
                            .request("POST", "/api/calls/group/invite-extra", org.json.JSONObject()
                                .put("groupCallId", groupCallId)
                                .put("inviteeIds", org.json.JSONArray(fresh))
                                .put("hostName", hostDisplayName).toString())
                    }
                }
                ringTimeout?.cancel()
                val freshSnapshot: List<String> = fresh
                ringTimeout = scope.launch {
                    delay(45_000)
                    val st = GroupCallRuntime.state
                    if (st is GroupCallUiState.Active) {
                        val updated = st.members.map { m ->
                            if (m.status == GroupCallMemberStatus.RINGING && m.userId in freshSnapshot) m.copy(status = GroupCallMemberStatus.NO_ANSWER) else m
                        }
                        withContext(Dispatchers.Main.immediate) { GroupCallRuntime.state = st.copy(members = updated) }
                    } else if (st is GroupCallUiState.Ringing) {
                        val updated = st.members.map { m ->
                            if (m.status == GroupCallMemberStatus.RINGING && m.userId in freshSnapshot) m.copy(status = GroupCallMemberStatus.NO_ANSWER) else m
                        }
                        withContext(Dispatchers.Main.immediate) { GroupCallRuntime.state = st.copy(members = updated) }
                    }
                }
            }
        }
        return START_STICKY
    }

    // ─────────────── WebRTC Events ────────────────────────────────────────────

    override fun onConnected() {
        scope.launch {
            // الدعوة تُرسَل أولاً من المضيف: الخادم يسجّل المكالمة في ActiveCallRegistry
            // قبل طلب تذكرة SFU — التذكرة تُرفض لغرف غير مسجّلة (أمان ضد الغرف العشوائية).
            if (isHost) {
                val state = GroupCallRuntime.state
                if (state is GroupCallUiState.Ringing) {
                    signaling.sendGroupCallInvite(groupCallId, state.members.map { it.userId }, isVideo, hostDisplayName, sourceGroupId)
                }
            }
            // SFU أولاً (mediasoup): خادم وسائط مركزي بدل شبكة Mesh — أداء أفضل مع نمو الأعضاء.
            // إذا فشل التوصيل نعود تلقائياً إلى Mesh (مثل واتساب عندما لا يتوفر SFU).
            if (groupCallId.isNotBlank() && groupCallId.length in 4..128) {
                sfu = SfuMediaClient(this@GroupCallService, TokenStore(this@GroupCallService), this@GroupCallService)
                if (attachSfuWithRetry(sfu!!, groupCallId)) {
                    val kind = if (isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
                    sfu?.publish(kind)
                    GroupCallRuntime.eglContext = sfu?.eglContext
                    GroupCallRuntime.localVideo = sfu?.localVideo
                    if (isVideo && GroupCallRuntime.localVideo == null) {
                        // SFU قد يفشل في فتح الكاميرا — الميش هو المسار الاحتياطي
                        sfu?.release(); sfu = null
                    } else {
                        startRingbackForHost()
                        return@launch
                    }
                } else {
                    sfu?.release(); sfu = null
                }
            }
            engine = WebRtcEngine(this@GroupCallService, this@GroupCallService)
            GroupCallRuntime.eglContext = engine?.eglContext
            val kind = if (isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
            engine?.create(kind)
            if (isVideo) GroupCallRuntime.localVideo = engine?.localMedia?.videoTrack
            mesh = MeshRtcSession(this@GroupCallService, myUserId, this@GroupCallService)

            // المنضم: ثبّت الـ Mesh مع المضيف فوراً (المضيف يثبّت عند تلقّي GROUP_CALL_ACCEPT)
            if (!isHost && hostId.isNotBlank()) {
                mesh?.attachPeer(hostId)
                mesh?.offerTo(hostId)
            }

            startRingbackForHost()
        }
    }

    /**
     * attach مع إعادة محاولة قصيرة: تسجيل المضيف للغرفة قد يلحق بطلب التذكرة
     * عبر شبكة بطيئة — محاولات إضافية تصلح السباق دون تأخير محسوس.
     */
    private suspend fun attachSfuWithRetry(sfu: SfuMediaClient, roomId: String): Boolean {
        repeat(4) { attempt ->
            if (sfu.attach(roomId)) return true
            if (attempt < 3) kotlinx.coroutines.delay(350)
        }
        return false
    }

    /** نغمة الرنين (ringback) للمضيف أثناء انتظار الردود — مثل المكالمات الفردية. */
    private fun startRingbackForHost() {
        if (!isHost) return
        stopRingback()
        runCatching {
            ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE, 45_000)
            }
        }
    }

    private fun stopRingback() {
        runCatching { ringback?.stopTone(); ringback?.release() }
        ringback = null
    }

    /** نغمة مشغول قصيرة عند إخبار الخادم أن عضواً ما في مكالمة أخرى. */
    private fun playBusyTone() {
        runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also {
                it.startTone(ToneGenerator.TONE_SUP_BUSY, 1500)
                scope.launch { kotlinx.coroutines.delay(1700); it.release() }
            }
        }
    }

    /** رنين المكالمة الجماعية الواردة — مثل المكالمة الفردية (نغمة + اهتزاز). */
    private fun startRingtone() {
        stopRingtone()
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
                            stopRingback()
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
                    "busy"      -> GroupCallMemberStatus.BUSY
                    else -> null
                }
                val uid = signal.sourceUserId.orEmpty()
                if (status != null && uid.isNotBlank()) {
                    updateMemberStatus(uid, status)
                    // العضو مشغول — يعتبر رافضاً للدعوة ونُكمِل باقي الأعضاء
                    if (status == GroupCallMemberStatus.BUSY) {
                        playBusyTone()
                        checkIfAllDone()
                    }
                }
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

    /** SFU: عضو غادر غرفة الوسائط — نُحدّث الحالة كالمغادرة العادية. */
    override fun onPeerLeft(peerId: String) {
        updateMemberStatus(peerId, GroupCallMemberStatus.LEFT)
        GroupCallRuntime.remoteVideos = GroupCallRuntime.remoteVideos - peerId
        checkIfAllDone()
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
    override fun onCameraUnavailable() {}
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
        val terminal = setOf(GroupCallMemberStatus.DECLINED, GroupCallMemberStatus.NO_ANSWER, GroupCallMemberStatus.LEFT, GroupCallMemberStatus.BUSY)
        if (members.all { it.status in terminal }) stopGroupCall()
    }

    private var networkWatcher: NetworkChangeWatcher? = null
    private fun ensureNetworkWatcher() {
        if (networkWatcher == null) {
            networkWatcher = NetworkChangeWatcher(this) {
                if (!stopping && GroupCallRuntime.state is GroupCallUiState.Active) mesh?.restartIce()
            }.also { it.start() }
        }
    }
    private fun stopNetworkWatcher() {
        networkWatcher?.stop(); networkWatcher = null
    }

    private fun stopGroupCall() {
        if (stopping) return
        stopping = true
        ringTimeout?.cancel()
        stopNetworkWatcher()
        saveGroupCallLogLocally()
        if (isHost && groupCallId.isNotBlank()) runCatching { signaling.sendGroupCallEnd(groupCallId) }
        scope.launch(Dispatchers.Main.immediate) { finishStop() }
    }

    /**
     * يسجّل المكالمة الجماعية محلياً (مشفّراً مثل المكالمة الفردية) — كان السجل
     * يقتصر على المكالمات الفردية بينما فلاتر "جماعية" في الواجهة تبقى فارغة دائماً.
     */
    private fun saveGroupCallLogLocally() {
        if (groupCallId.isBlank()) return
        val state = GroupCallRuntime.state
        val members: List<GroupCallMember> = when (state) {
            is GroupCallUiState.Ringing -> state.members
            is GroupCallUiState.Active -> state.members
            else -> emptyList()
        }
        val startedAt = (state as? GroupCallUiState.Active)?.startedAt ?: 0L
        val durationMs = if (startedAt > 0L) (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) else 0L
        val status = when {
            durationMs > 0L || state is GroupCallUiState.Active -> "ENDED"
            state is GroupCallUiState.IncomingGroup -> "REJECTED"
            members.any { it.status == GroupCallMemberStatus.JOINED } -> "ENDED"
            isHost -> "FAILED"
            else -> "MISSED"
        }
        val peer = if (isHost) {
            members.firstOrNull { it.status == GroupCallMemberStatus.JOINED }?.userId
                ?: members.firstOrNull()?.userId
        } else hostId
        if (peer.isNullOrBlank()) return
        val cipher = CallLogCipher()
        val log = com.red.sovereign.core.database.CallLogEntity(
            id = groupCallId,
            peerId = cipher.encryptPeerId(peer),
            peerLabel = cipher.encryptLabel(peer),
            type = "GROUP",
            direction = if (isHost) "OUTGOING" else "INCOMING",
            route = "RED",
            status = status,
            timestamp = startedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            durationMs = durationMs,
            answeredAt = startedAt.takeIf { it > 0L && durationMs > 0L },
            endedAt = (startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()) + durationMs
        )
        scope.launch { runCatching { com.red.sovereign.core.database.LocalRepository(this@GroupCallService).saveCallLog(log) } }
    }

    private fun finishStop() {
        if (cleanedUp) return
        cleanedUp = true
        ringTimeout?.cancel()
        incomingRingTimeout?.cancel()
        recordingManager?.let { scope.launch { it.stop() } }
        recordingManager = null
        stopRingback()
        stopRingtone()
        releaseAudio()
        engine?.release(); engine = null
        mesh?.release(); mesh = null
        sfu?.release(); sfu = null
        signaling.close()
        GroupCallRuntime.state = GroupCallUiState.Ended
        GroupCallRuntime.localVideo = null
        GroupCallRuntime.remoteVideos = emptyMap()
        GroupCallRuntime.eglContext = null
        GroupCallRuntime.isMuted = false
        GroupCallRuntime.isHost = false
        GroupCallRuntime.isVideoEnabled = false
        GroupCallRuntime.isRecording = false
        GroupCallRuntime.activeGroupId = ""
        GroupCallRuntime.activeGroupName = ""
        GroupCallRuntime.isMinimized = false
        GroupCallRuntime.networkStats = NetworkStats()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ─────────────── Notifications ────────────────────────────────────────────

    private fun promoteToForeground() {
        val label = if (isVideo) "مكالمة فيديو جماعية" else "مكالمة صوتية جماعية"
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val endPi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_END, CallNotificationActionReceiver.CALL_TYPE_GROUP, NOTIF_ID_ACTIVE, callId = groupCallId, myUserId = myUserId, hostId = this.hostId, isVideo = isVideo)
        val mutePi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_TOGGLE_MIC, CallNotificationActionReceiver.CALL_TYPE_GROUP, NOTIF_ID_ACTIVE, callId = groupCallId, myUserId = myUserId, hostId = this.hostId, isVideo = isVideo)
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(label)
            .setContentText("مكالمة جماعية جارية...")
            .setContentIntent(intent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true).setSilent(true)
            .addAction(0, "كتم", mutePi)
            .addAction(0, "إنهاء", endPi)
            .build()
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (isVideo) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        ServiceCompat.startForeground(this, NOTIF_ID_ACTIVE, notif, type)
    }

    private fun showIncomingGroupCallNotification(gId: String, hostName: String, otherCount: Int, video: Boolean) {
        val label = if (video) "مكالمة فيديو جماعية" else "مكالمة صوتية جماعية"
        val body = if (otherCount > 0) "من $hostName ومعه $otherCount آخرون" else "من $hostName"
        val acceptPi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_ACCEPT, CallNotificationActionReceiver.CALL_TYPE_GROUP, NOTIF_ID_INCOMING, callId = gId, myUserId = myUserId, hostId = this.hostId, isVideo = video)
        val declinePi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_DECLINE, CallNotificationActionReceiver.CALL_TYPE_GROUP, NOTIF_ID_INCOMING, callId = gId, myUserId = myUserId, hostId = this.hostId, isVideo = video)
        val fullScreenIntent = PendingIntent.getActivity(
            this, 22,
            Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_GROUP)
                putExtra(EXTRA_GROUP_CALL_ID, gId)
                putExtra(EXTRA_MY_USER_ID, myUserId)
                putExtra(EXTRA_HOST_NAME, hostName)
                putExtra(EXTRA_IS_VIDEO, video)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching { IncomingCallActivity.launchGroup(this, gId, myUserId, hostName, video) }
        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(label).setContentText(body)
            .setFullScreenIntent(fullScreenIntent, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(0xFF00C98C.toInt())
            .setOngoing(true).setAutoCancel(false)
            .addAction(NotificationCompat.Action.Builder(0, "رفض", declinePi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE).build())
            .addAction(NotificationCompat.Action.Builder(0, "قبول", acceptPi).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL).build())
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
        const val ACTION_MUTE_ALL          = "com.red.sovereign.groupcall.MUTE_ALL"
        const val ACTION_TOGGLE_SPEAKER    = "com.red.sovereign.groupcall.TOGGLE_SPEAKER"
        const val ACTION_ADD_PARTICIPANT   = "com.red.sovereign.groupcall.ADD_PARTICIPANT"

        const val EXTRA_GROUP_CALL_ID = "group_call_id"
        const val EXTRA_GROUP_ID      = "group_id"
        const val EXTRA_GROUP_NAME    = "group_name"
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
            hostName: String = "", groupId: String = "", groupName: String = ""
        ) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).apply {
                    action = ACTION_START_GROUP_CALL
                    putExtra(EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(EXTRA_GROUP_ID, groupId)
                    putExtra(EXTRA_GROUP_NAME, groupName)
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
            otherMemberIds: List<String> = emptyList(),
            groupId: String = "", groupName: String = ""
        ) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).apply {
                    action = ACTION_INCOMING_GROUP_CALL
                    putExtra(EXTRA_GROUP_CALL_ID, groupCallId)
                    putExtra(EXTRA_GROUP_ID, groupId)
                    putExtra(EXTRA_GROUP_NAME, groupName)
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

        fun muteAll(context: Context) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).setAction(ACTION_MUTE_ALL))
        }

        fun addParticipant(context: Context, ids: List<String>, names: List<String>) {
            ContextCompat.startForegroundService(context,
                Intent(context, GroupCallService::class.java).setAction(ACTION_ADD_PARTICIPANT)
                    .putStringArrayListExtra(EXTRA_INVITEE_IDS, ArrayList(ids))
                    .putStringArrayListExtra(EXTRA_INVITEE_NAMES, ArrayList(names)))
        }
    }
}
