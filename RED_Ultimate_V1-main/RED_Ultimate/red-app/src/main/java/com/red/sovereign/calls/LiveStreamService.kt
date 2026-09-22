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
import kotlinx.coroutines.isActive
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
    val replyToId: String? = null,
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

/** تعليق مثبّت من المذيع — يُبث عبر PIN_MESSAGE/UNPIN_MESSAGE ويُعرض أعلى الشات. */
data class PinnedLiveMessage(
    val messageId: String,
    val senderName: String,
    val text: String
)

/** حدود قوائم البث المباشر — الشات موسّع لـ 100 (كان 50)، التفاعلات 25 للأداء. */
const val LIVE_CHAT_MAX_MESSAGES = 100
const val LIVE_REACTIONS_MAX = 25

/** جودة البث للمشاهد — تُرسل للـ SFU عبر SET_QUALITY وتُعرض في الواجهة. */
enum class LiveQuality(val label: String) {
    AUTO("تلقائي"),
    Q360("360p"),
    Q480("480p"),
    Q720("720p"),
    Q1080("1080p"),
    AUDIO_ONLY("صوت فقط")
}

object LiveStreamRuntime {
    var state: LiveStreamUiState by mutableStateOf(LiveStreamUiState.Idle)
    var localVideo: VideoTrack? by mutableStateOf(null)
    var remoteVideo: VideoTrack? by mutableStateOf(null)
    var coHostVideo: VideoTrack? by mutableStateOf(null)
    /** مضيفون مشاركون حتى 4 — userId -> track. coHostVideo القديم = أول عنصر للتوافق. */
    var coHostVideos: Map<String, VideoTrack> by mutableStateOf(emptyMap())
    var eglContext: org.webrtc.EglBase.Context? by mutableStateOf(null)
    var isMuted by mutableStateOf(false)
    var isAudioOnly by mutableStateOf(false)
    var isRecording by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    /** جودة المشاهدة المختارة + حالة الشبكة التفصيلية للـ overlay. */
    var quality: LiveQuality by mutableStateOf(LiveQuality.AUTO)
    var showStats by mutableStateOf(false)
    var slowModeSec: Int by mutableStateOf(0)
    var streamStartTime: Long by mutableStateOf(0L)
    /** ذروة المشاهدين (محلية + STREAM_STATS) — لملخص النهاية. */
    var peakViewers: Int by mutableStateOf(0)
    /** خطأ الكاميرا القابل للعرض (null = لا خطأ) — يقود بطاقة الإصلاح بدل الشاشة السوداء. */
    var cameraError: String? by mutableStateOf(null)
    /** خطأ الصوت القابل للعرض (null = لا خطأ). */
    var audioError: String? by mutableStateOf(null)
    /** عدد المشاهدين النشطين — محدّث عبر signaling (PARTICIPANT_JOINED/LEFT) */
    var viewerCount: Int by mutableStateOf(0)
    /**
     * سجل المشاهِدين المعروفين (userId). المذيع يتلقى VIEWER_JOINED لكل
     * مشاهِد، والجميع يتلقى VIEWER_COUNT_UPDATED وPARTICIPANT_LEFT —
     * فالقائمة تقريبية والعدد هو المرجع. تُحل الأسماء في الواجهة عبر
     * جهات الاتصال ثم [viewerNames] (من رسائل CHAT) ثم مقتطع المعرف.
     */
    var viewerIds: List<String> by mutableStateOf(emptyList())
    /** آخر اسم معلن لكل مشاهِد (من حمولة CHAT) — userId -> name. */
    var viewerNames: Map<String, String> by mutableStateOf(emptyMap())
    var chatMessages: List<LiveChatMessage> by mutableStateOf(emptyList())
    var reactions: List<LiveStreamReaction> by mutableStateOf(emptyList())
    var raisedHands: List<RaisedHandUser> by mutableStateOf(emptyList())
    /** التعليق المثبّت حاليًا (null = لا يوجد) — يضبطه المذيع عبر PIN_MESSAGE. */
    var pinnedMessage: PinnedLiveMessage? by mutableStateOf(null)
    var isCoHost by mutableStateOf(false)
}

class LiveStreamService : Service(), WebRtcEngine.Events, MeshRtcSession.Events, LiveStreamSignalingClient.Listener, SfuMediaClient.Events {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectAttempt = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private lateinit var signaling: LiveStreamSignalingClient
    private var engine: WebRtcEngine? = null
    private var mesh: MeshRtcSession? = null
    private var sfu: SfuMediaClient? = null
    @Volatile private var sfuLive = false
    private var recordingManager: CallRecordingManager? = null
    private var streamId = ""
    private var userId = ""
    private var streamTitle = ""
    private var streamCategory = "عام"
    private var isPrivate = false
    private var streamPassword: String? = null
    private var isBroadcaster = false
    private var stopping = false
    private var cleanedUp = false
    /** معرف المذيع (من أول OFFER مستلم) — يحتاجه المضيف المشارك للنشر نحوه. */
    private var broadcasterUserId = ""
    /** مضيفون مشاركون اعتمدهم هذا المذيع — عروضهم مقبولة حتماً في الـ mesh. */
    private val approvedCohostIds = mutableSetOf<String>()
    private val pendingViewerOffers = java.util.concurrent.ConcurrentLinkedQueue<String>()

    /** G13 فصل الغرف: انضمام/إنشاء البث — فارغ يبقى فارغاً (لا توليد في مسار الانضمام)، legacy يُقبل، بادئة مخالفة تُطبَّع LIVE_ بلا كسر. */
    private fun resolveStreamIdForJoin(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        val k = RoomSeparationPolicy.kindOf(v)
        if (k == RoomSeparationPolicy.RoomKind.LIVE || k == RoomSeparationPolicy.RoomKind.LEGACY) {
            return RoomSeparationPolicy.normalizeStreamId(v)
        }
        val core = v.substringAfter("_").ifBlank { v }.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)
        if (core.length < 4) return RoomSeparationPolicy.normalizeStreamId(null)
        if (RoomSeparationPolicy.isValidRoomId(core)) return RoomSeparationPolicy.PREFIX_LIVE + core
        return RoomSeparationPolicy.normalizeStreamId(core)
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(NotificationChannel("red_calls", getString(com.red.sovereign.R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH))
        signaling = LiveStreamSignalingClient(this, TokenStore(this), this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INVITE -> {
                streamId = resolveStreamIdForJoin(intent.getStringExtra(EXTRA_STREAM_ID))
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                val broadcasterName = intent.getStringExtra(EXTRA_BROADCASTER_NAME).orEmpty()
                LiveStreamRuntime.state = LiveStreamUiState.Incoming(streamId, broadcasterName, userId)
                showIncomingLiveStreamNotification(streamId, userId, broadcasterName)
            }
            ACTION_START -> {
                stopping = false
                cleanedUp = false
                streamId = resolveStreamIdForJoin(intent.getStringExtra(EXTRA_STREAM_ID))
                userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
                streamTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "بث مباشر يونس" }
                streamCategory = intent.getStringExtra(EXTRA_CATEGORY)?.trim()?.take(30)?.takeIf { it.isNotBlank() } ?: "عام"
                isPrivate = intent.getBooleanExtra(EXTRA_IS_PRIVATE, false)
                streamPassword = intent.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.isNotBlank() }
                isBroadcaster = intent.getBooleanExtra(EXTRA_BROADCASTER, false)
                // فحص الصلاحيات قبل بناء الوسائط — تدهور صوتي صريح بدل شاشة سوداء
                LiveStreamRuntime.cameraError = null
                LiveStreamRuntime.audioError = null
                // LEGENDARY FIX (بث صامت): لا تسجل بثاً بلا ميكروفون — بوابة صريحة بدل Active كاذب
                if (isBroadcaster && !hasAudioPermission()) {
                    LiveStreamRuntime.state = LiveStreamUiState.Error("MIC_PERMISSION_REQUIRED")
                    promote()
                    return@onStartCommand START_STICKY
                }
                if (isBroadcaster && !hasCameraPermission()) {
                    LiveStreamRuntime.cameraError = "PERMISSION"
                    LiveStreamRuntime.isAudioOnly = true
                }
                LiveStreamRuntime.state = LiveStreamUiState.Connecting(streamId, isBroadcaster)
                promote()
                armConnectWatchdog()
                scope.launch {
                    val ready = if (isBroadcaster) registerBroadcaster() else joinAsViewer()
                    if (ready) signaling.connect(streamId) else onError("LIVE_STREAM_REGISTRATION_FAILED")
                }
            }
            ACTION_RETRY_MEDIA -> {
                // إعادة بناء الوسائط بعد منح الإذن — تُستدعى من بطاقة الإصلاح في الواجهة
                LiveStreamRuntime.cameraError = null
                LiveStreamRuntime.audioError = null
                if (isBroadcaster && !hasCameraPermission()) {
                    LiveStreamRuntime.cameraError = "PERMISSION"
                    LiveStreamRuntime.isAudioOnly = true
                } else {
                    LiveStreamRuntime.isAudioOnly = false
                }
                if (isBroadcaster && !hasAudioPermission()) {
                    LiveStreamRuntime.audioError = "PERMISSION"
                }
                scope.launch {
                    runCatching {
                        if (isBroadcaster) {
                            if (sfu == null && mesh == null) {
                                startBroadcasterMedia()
                            } else {
                                // FIX P0: مذيع SFU كان زر الإصلاح ميتاً (mesh==null دائماً).
                                // جرّب SFU أولاً ثم mesh ثم إعادة نشر كاملة.
                                var ok = false
                                if (sfu != null) {
                                    ok = runCatching { sfu?.retryCamera() == true }.getOrDefault(false)
                                    if (ok) {
                                        LiveStreamRuntime.localVideo = sfu?.localVideo
                                        LiveStreamRuntime.cameraError = null
                                        LiveStreamRuntime.isAudioOnly = false
                                        sfu?.setCameraEnabled(true)
                                    } else {
                                        // إعادة attach+publish كاملة كملاذ أخير
                                        val client = sfu
                                        if (client != null) {
                                            val republished = runCatching {
                                                attachSfuWithRetry(client) && client.publish(CallMediaKind.LIVE)
                                            }.getOrDefault(false)
                                            if (republished) {
                                                sfuLive = true
                                                LiveStreamRuntime.localVideo = client.localVideo
                                                LiveStreamRuntime.cameraError = null
                                                LiveStreamRuntime.isAudioOnly = false
                                                ok = true
                                            }
                                        }
                                    }
                                }
                                if (!ok) {
                                    ok = mesh?.retryCamera() == true
                                    if (ok) LiveStreamRuntime.localVideo = mesh?.localVideo
                                }
                                // LEGENDARY FIX: إحياء الصوت مع الكاميرا (كان لاصقاً PERMISSION للأبد)
                                if (hasAudioPermission() && LiveStreamRuntime.audioError != null) {
                                    if (mesh?.retryAudio() == true) {
                                        LiveStreamRuntime.audioError = null
                                        mesh?.setMicrophoneEnabled(!LiveStreamRuntime.isMuted)
                                    }
                                }
                            }
                            if (LiveStreamRuntime.localVideo == null && !LiveStreamRuntime.isAudioOnly) {
                                LiveStreamRuntime.cameraError = LiveStreamRuntime.cameraError ?: "UNAVAILABLE"
                                LiveStreamRuntime.isAudioOnly = true
                                engine?.setCameraEnabled(false)
                                mesh?.setCameraEnabled(false)
                            }
                        } else {
                            if (sfu == null && engine == null) {
                                engine = WebRtcEngine(this@LiveStreamService, this@LiveStreamService)
                                LiveStreamRuntime.eglContext = engine?.eglContext
                                engine?.createReceiverOnly(CallMediaKind.VIDEO)
                            } else {
                                engine?.restartIce()
                                mesh?.restartIce()
                            }
                        }
                    }
                }
            }
            ACTION_STOP -> stopStream()
            ACTION_TOGGLE_MIC -> {
                LiveStreamRuntime.isMuted = !LiveStreamRuntime.isMuted
                val micOn = !LiveStreamRuntime.isMuted
                engine?.setMicrophoneEnabled(micOn)
                mesh?.setMicrophoneEnabled(micOn)
                sfu?.setMicrophoneEnabled(micOn)
            }
            ACTION_TOGGLE_VIDEO -> {
                val isVideoOn = LiveStreamRuntime.localVideo?.enabled() == true
                if (!isVideoOn && LiveStreamRuntime.localVideo == null) {
                    // إعادة محاولة فتح الكاميرا (إذن مُنح لاحقاً أو خلل مؤقت)
                    scope.launch {
                        // FIX: مذيع SFU بلا engine/mesh — جرّب sfu أولاً
                        val ok = sfu?.retryCamera() == true ||
                            engine?.retryCamera() == true || mesh?.retryCamera() == true
                        if (ok) {
                            LiveStreamRuntime.localVideo = sfu?.localVideo
                                ?: engine?.localMedia?.videoTrack ?: mesh?.localVideo
                            LiveStreamRuntime.cameraError = null
                            LiveStreamRuntime.isAudioOnly = false
                        } else if (!hasCameraPermission()) {
                            LiveStreamRuntime.cameraError = "PERMISSION"
                        }
                    }
                } else {
                    engine?.setCameraEnabled(!isVideoOn)
                    mesh?.setCameraEnabled(!isVideoOn)
                    sfu?.setCameraEnabled(!isVideoOn)
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
                sfu?.setCameraEnabled(cameraOn)
            }
            ACTION_START_RECORDING -> {
                // موافقة صريحة من واجهة المستخدم — لا تُفترض أبداً (خصوصية الطرفين)
                val consent = intent.getBooleanExtra(YounesCallService.EXTRA_CONSENT, false)
                if (!consent) {
                    android.util.Log.w("LiveStream", "Recording refused: consent not granted")
                    LiveStreamRuntime.isRecording = false
                    return START_STICKY
                }
                if (recordingManager == null && streamId.isNotBlank()) {
                    recordingManager = CallRecordingManager(this, streamId)
                }
                // إصلاح UX: isRecording يعكس نجاح start() الفعلي لا نية consent فقط.
                LiveStreamRuntime.isRecording = recordingManager?.start(consentGranted = true) == true
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
                val replyToId = intent.getStringExtra(EXTRA_REPLY_TO_ID)?.takeIf { it.isNotBlank() }
                if (text.isNotBlank()) {
                    signaling.sendChatMessage(streamId, userId, senderName, text, replyToId)
                    val localMsg = LiveChatMessage(senderId = userId, senderName = senderName.ifBlank { userId }, text = text, replyToId = replyToId)
                    LiveStreamRuntime.chatMessages = (LiveStreamRuntime.chatMessages + localMsg).takeLast(LIVE_CHAT_MAX_MESSAGES)
                }
            }
            ACTION_SEND_REACTION -> {
                val emoji = intent.getStringExtra(EXTRA_REACTION_EMOJI) ?: "❤️"
                signaling.sendReaction(streamId, userId, emoji)
                val localReaction = LiveStreamReaction(emoji = emoji)
                LiveStreamRuntime.reactions = (LiveStreamRuntime.reactions + localReaction).takeLast(LIVE_REACTIONS_MAX)
            }
            ACTION_SEND_GIFT -> {
                // الهدايا معطلة بقرار المنتج ("بدون هدايا"): أي استدعاء قديم
                // يتحول لتفاعل مجاني — بلا حدث GIFT ولا خصم عملات. يُحفظ الـ action
                // والثوابت للتوافق (لا كسر لتوقيعات API العلنية).
                val giftEmoji = intent.getStringExtra(EXTRA_GIFT_EMOJI)?.takeIf { it.isNotBlank() } ?: "❤️"
                signaling.sendReaction(streamId, userId, giftEmoji)
                LiveStreamRuntime.reactions = (LiveStreamRuntime.reactions + LiveStreamReaction(emoji = giftEmoji)).takeLast(LIVE_REACTIONS_MAX)
            }
            ACTION_PIN_MESSAGE -> {
                val messageId = intent.getStringExtra(EXTRA_PIN_MESSAGE_ID).orEmpty()
                val senderName = intent.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
                val text = intent.getStringExtra(EXTRA_PIN_TEXT).orEmpty()
                if (messageId.isNotBlank() && text.isNotBlank()) {
                    signaling.sendPinMessage(streamId, userId, messageId, senderName, text)
                    LiveStreamRuntime.pinnedMessage = PinnedLiveMessage(messageId, senderName.ifBlank { userId }, text)
                }
            }
            ACTION_DELETE_CHAT -> {
                val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
                if (chatId.isNotBlank()) {
                    // حذف تفاؤلي محلي + طلب خادم يبث CHAT_DELETED للغرفة
                    LiveStreamRuntime.chatMessages = LiveStreamRuntime.chatMessages.filter { it.id != chatId }
                    if (LiveStreamRuntime.pinnedMessage?.messageId == chatId) LiveStreamRuntime.pinnedMessage = null
                    scope.launch {
                        runCatching {
                            val body = org.json.JSONObject().put("chatId", chatId).toString()
                            AuthorizedApiClient(TokenStore(this@LiveStreamService))
                                .request("POST", "/api/livestream/$streamId/chat/delete", body)
                        }
                    }
                }
            }
            ACTION_UNPIN_MESSAGE -> {
                signaling.sendUnpinMessage(streamId, userId)
                LiveStreamRuntime.pinnedMessage = null
            }
            ACTION_RAISE_HAND -> {
                val userName = intent.getStringExtra(EXTRA_SENDER_NAME) ?: userId
                signaling.raiseHand(streamId, userId, userName)
            }
            ACTION_LOWER_HAND -> {
                signaling.lowerHand(streamId, userId)
                LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != userId }
            }
            ACTION_APPROVE_COHOST -> {
                val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (targetUserId.isNotBlank()) {
                    signaling.approveCoHost(streamId, userId, targetUserId)
                    // نتذكر المعتمدين: عرضهم اللاحق يُقبل حتماً (بلا glare) في mesh المذيع.
                    approvedCohostIds.add(targetUserId)
                    LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != targetUserId }
                }
            }
            ACTION_REJECT_COHOST -> {
                val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (targetUserId.isNotBlank()) {
                    signaling.rejectCoHost(streamId, userId, targetUserId)
                    LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != targetUserId }
                }
            }
            ACTION_REMOVE_COHOST -> {
                val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (targetUserId.isNotBlank()) {
                    signaling.removeCoHost(streamId, userId, targetUserId)
                    LiveStreamRuntime.coHostVideos = LiveStreamRuntime.coHostVideos - targetUserId
                    if (LiveStreamRuntime.coHostVideos.isEmpty()) LiveStreamRuntime.coHostVideo = null
                }
            }
            ACTION_LEAVE_COHOST -> {
                signaling.leaveCoHost(streamId, userId)
                LiveStreamRuntime.isCoHost = false
            }
            ACTION_KICK_VIEWER -> {
                val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                if (targetUserId.isNotBlank()) {
                    signaling.kickViewer(streamId, userId, targetUserId)
                    LiveStreamRuntime.viewerIds = LiveStreamRuntime.viewerIds - targetUserId
                }
            }
            ACTION_MUTE_VIEWER -> {
                val targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID).orEmpty()
                val muted = intent.getBooleanExtra(EXTRA_MUTED, true)
                if (targetUserId.isNotBlank()) signaling.muteViewer(streamId, userId, targetUserId, muted)
            }
            ACTION_SLOW_MODE -> {
                val sec = intent.getIntExtra(EXTRA_SLOW_SECONDS, 0).coerceIn(0, 60)
                signaling.setSlowMode(streamId, userId, sec)
                LiveStreamRuntime.slowModeSec = sec
            }
            ACTION_SET_QUALITY -> {
                val q = intent.getStringExtra(EXTRA_QUALITY).orEmpty()
                val parsed = runCatching { LiveQuality.valueOf(q) }.getOrDefault(LiveQuality.AUTO)
                LiveStreamRuntime.quality = parsed
                LiveStreamRuntime.isAudioOnly = (parsed == LiveQuality.AUDIO_ONLY)
                if (parsed == LiveQuality.AUDIO_ONLY) {
                    engine?.setCameraEnabled(false)
                    mesh?.setCameraEnabled(false)
                }
                // LEGENDARY Phase 6: اختيار طبقة simulcast حقيقي على SFU (لا-أثر بلا simulcast).
                // AUTO = ترك القرار للـ SFU (تكيّف مع عرض النطاق): كان يثبّت أعلى طبقة
                // (else -> 2) فيُبطل التكيّف تماماً. الآن لا نُقيّد الطبقات في AUTO.
                if (parsed != LiveQuality.AUTO) {
                    sfu?.setAllVideoLayers(
                        when (parsed) {
                            LiveQuality.AUDIO_ONLY, LiveQuality.Q360 -> 0
                            LiveQuality.Q480 -> 1
                            else -> 2
                        },
                        when (parsed) {
                            LiveQuality.AUDIO_ONLY -> 0
                            LiveQuality.Q360, LiveQuality.Q480 -> 1
                            else -> 2
                        }
                    )
                }
                signaling.setQuality(streamId, userId, parsed.name)
            }
            ACTION_TOGGLE_STATS -> {
                LiveStreamRuntime.showStats = !LiveStreamRuntime.showStats
            }
        }
        return START_STICKY
    }

    private suspend fun registerBroadcaster(): Boolean {
        if (streamId.isBlank() || userId.isBlank()) return false
        val payload = org.json.JSONObject()
            .put("streamId", streamId)
            .put("title", streamTitle)
            .put("category", streamCategory)
            .put("isPrivate", isPrivate)
            .apply { if (isPrivate && !streamPassword.isNullOrBlank()) put("password", streamPassword) }
            .toString()
        return when (AuthorizedApiClient(TokenStore(this)).request("POST", "/api/livestream/create", payload)) {
            is ApiResult.Success -> true
            is ApiResult.Error -> false
        }
    }

    private suspend fun joinAsViewer(): Boolean {
        if (streamId.isBlank()) return false
        val joinPayload = if (!streamPassword.isNullOrBlank()) {
            org.json.JSONObject().put("password", streamPassword).toString()
        } else "{}"
        return when (AuthorizedApiClient(TokenStore(this)).request("POST", "/api/livestream/$streamId/join", joinPayload)) {
            is ApiResult.Success -> true
            is ApiResult.Error -> false
        }
    }

    private suspend fun attachSfuWithRetry(client: SfuMediaClient): Boolean {
        // LEGENDARY Phase 6: SFU-first — التذكرة تتطلب REST join مسبقاً (يتم قبل signaling.connect)،
        // والتراجع الأسّي يمتصّ أي سباق تسجيل على شبكة بطيئة.
        val ticketPath = "/api/sfu/groups/live/$streamId/ticket"
        val backoffMs = longArrayOf(400, 800, 1_600, 3_200)
        for (attempt in 0..backoffMs.size) {
            if (client.attach(streamId, ticketPath)) return true
            if (attempt < backoffMs.size) {
                android.util.Log.w("LiveStreamService", "SFU attach failed attempt ${attempt + 1}/${backoffMs.size + 1} stream=$streamId — retry in ${backoffMs[attempt]}ms")
                kotlinx.coroutines.delay(backoffMs[attempt])
            }
        }
        return false
    }

    private suspend fun startBroadcasterMedia() {
        // LEGENDARY Phase 6: المذيع يرفع مرة واحدة للـ SFU (O(1)) بدل اتصال mesh لكل مشاهد (O(N)).
        val client = SfuMediaClient(this, TokenStore(this), this)
        sfu = client
        // FIX: لا تنشر egl قبل نجاح attach (كان يسبب EGL_BAD_CONTEXT)
        if (attachSfuWithRetry(client) && client.publish(CallMediaKind.LIVE)) {
            sfuLive = true
            LiveStreamRuntime.eglContext = client.eglContext
            LiveStreamRuntime.localVideo = client.localVideo
            // FIX: فعّل الكاميرا صراحة وصفّر الخطأ بعد النجاح
            if (client.localVideo != null) {
                LiveStreamRuntime.cameraError = null
                if (!LiveStreamRuntime.isAudioOnly) client.setCameraEnabled(true)
            } else {
                LiveStreamRuntime.cameraError = "UNAVAILABLE"
                LiveStreamRuntime.isAudioOnly = true
            }
            return
        }
        // Fallback: شبكة mesh القديمة 1-ن (تخدم أيضاً عملاء قدامى بلا SFU).
        runCatching { client.release() }
        sfu = null
        mesh = MeshRtcSession(this@LiveStreamService, userId, this@LiveStreamService)
        LiveStreamRuntime.eglContext = mesh?.eglContext
        // Live streaming needs HD + simulcast for adaptive quality to viewers
        val started = mesh?.start(CallMediaKind.LIVE)
        LiveStreamRuntime.localVideo = mesh?.localVideo
        if (started is ApiResult.Success && mesh?.localVideo != null) {
            LiveStreamRuntime.cameraError = null
            flushPendingViewerOffers()
        } else {
            // FIX: فشل صامت سابقاً — سجّل خطأً صريحاً بدل Active كاذبة
            if (mesh?.localVideo == null) {
                LiveStreamRuntime.cameraError = if (!hasCameraPermission()) "PERMISSION" else "UNAVAILABLE"
                LiveStreamRuntime.isAudioOnly = true
            }
            if (started is ApiResult.Success) flushPendingViewerOffers()
        }
    }

    private suspend fun startViewerMedia() {
        // LEGENDARY Phase 6: المشاهد يستهلك من الـ SFU (صفر uplink) بدل PC كامل مع المذيع.
        val client = SfuMediaClient(this, TokenStore(this), this)
        sfu = client
        if (attachSfuWithRetry(client)) {
            sfuLive = true
            // FIX P0: المشاهد SFU كان عالقاً Connecting للأبد (SfuMediaClient يبتلع CONNECTED).
            // فعّل فور نجاح attach + ابدأ polling/watcher.
            LiveStreamRuntime.eglContext = client.eglContext
            markViewerSfuActive()
            return
        }
        // Fallback: مسار الاستقبال القديم + طلب خدمة mesh من المذيع.
        runCatching { client.release() }
        sfu = null
        engine = WebRtcEngine(this@LiveStreamService, this@LiveStreamService)
        LiveStreamRuntime.eglContext = engine?.eglContext
        // المشاهد: استقبال فقط — لا تُفتح كاميرته ولا ميكروفونه
        // (خصوصية وبطارية؛ النشر فقط عند الترقية لمضيف مشارك).
        engine?.createReceiverOnly(CallMediaKind.VIDEO)
        signaling.sendViewerNeedsMesh(streamId, userId)
    }

    private suspend fun serveViewerOverMesh(viewerId: String) {
        // مشاهد واحد بلا SFU — mesh احتياطي له وحده (صوت فقط إن كانت الكاميرا محجوزة لنشر SFU).
        var session = mesh
        if (session == null) {
            val created = MeshRtcSession(this@LiveStreamService, userId, this@LiveStreamService)
            if (created.start(CallMediaKind.LIVE) !is ApiResult.Success) return
            mesh = created
            session = created
        }
        runCatching { session.attachPeer(viewerId); session.offerTo(viewerId) }
    }

    override fun onConnected() {
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch {
            // إعادة الاتصال بالإشارة لا تعيد بناء الوسائط — الجلسات قائمة.
            if (sfu == null && mesh == null && engine == null) {
                if (isBroadcaster) startBroadcasterMedia() else startViewerMedia()
            }
            if (isBroadcaster) {
                LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, true, System.currentTimeMillis())
                if (LiveStreamRuntime.streamStartTime == 0L) LiveStreamRuntime.streamStartTime = System.currentTimeMillis()
                startStatsPolling()
            }
            signaling.join(streamId, userId, if (isBroadcaster) "broadcaster" else "viewer", password = streamPassword)
            // سجل الشات للمنضم المتأخر Legendary V2 — آخر 30 رسالة من الخادم
            launchChatHistoryFetch()
        }
    }

    private fun launchChatHistoryFetch() {
        val sid = streamId
        if (sid.isBlank()) return
        scope.launch {
            runCatching {
                when (val r = AuthorizedApiClient(TokenStore(this@LiveStreamService))
                    .request("GET", "/api/livestream/$sid/chat?limit=30")) {
                    is ApiResult.Success -> {
                        val arr = org.json.JSONArray(r.value)
                        val items = mutableListOf<LiveChatMessage>()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val text = o.optString("text").takeIf { it.isNotBlank() } ?: continue
                            items.add(
                                LiveChatMessage(
                                    id = o.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                                    senderId = o.optString("senderId"),
                                    senderName = o.optString("senderName").takeIf { it.isNotBlank() } ?: o.optString("senderId"),
                                    text = text,
                                    replyToId = o.optString("replyToId").takeIf { it.isNotBlank() },
                                    timestamp = o.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                        }
                        if (items.isNotEmpty()) {
                            val existing = LiveStreamRuntime.chatMessages.map { it.id }.toSet()
                            val fresh = items.filter { it.id !in existing }
                            if (fresh.isNotEmpty()) {
                                LiveStreamRuntime.chatMessages = (LiveStreamRuntime.chatMessages + fresh).takeLast(LIVE_CHAT_MAX_MESSAGES)
                            }
                        }
                    }
                    is ApiResult.Error -> Unit
                }
            }
        }
    }

    override fun onSignal(signal: LiveStreamSignal) {
        when (signal.type) {
            "ERROR" -> {
                val code = signal.payload["code"].orEmpty()
                if (code == "MUTED") {
                    scope.launch {
                        withContext(Dispatchers.Main.immediate) {
                            runCatching {
                                android.widget.Toast.makeText(this@LiveStreamService, "تم كتمك من المذيع 🔇", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (code == "RATE_LIMITED") {
                    scope.launch {
                        withContext(Dispatchers.Main.immediate) {
                            runCatching {
                                android.widget.Toast.makeText(this@LiveStreamService, "أنت ترسل بسرعة — انتظر قليلاً 🐢", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }                 else {
                    // AUTO-FIX (livestream UX): surface unhandled server errors (NOT_OWNER /
                    // FORBIDDEN / BANNED / STREAM_NOT_FOUND ...) instead of hanging on Connecting.
                    val msg = signal.payload["message"].orEmpty().ifBlank { code }
                    scope.launch {
                        withContext(Dispatchers.Main.immediate) {
                            LiveStreamRuntime.state = LiveStreamUiState.Error(msg)
                        }
                    }
                    scope.launch {
                        kotlinx.coroutines.delay(1200)
                        if (LiveStreamRuntime.state is LiveStreamUiState.Error) stopStream()
                    }
                }

                return
            }
            "OFFER" -> {
                // نحفظ معرف المذيع من أول عرض — يحتاجه المضيف المشارك للنشر نحوه.
                if (!isBroadcaster) {
                    signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                        .takeIf { it.isNotBlank() }?.let { broadcasterUserId = it }
                }
                signal.payload["sdp"]?.let {
                    if (isBroadcaster) {
                        val from = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                        // عرض مضيف مشارك معتمد يُقبل حتماً (تصفير glare عرض سابق).
                        if (from in approvedCohostIds) mesh?.resetOfferState(from)
                        mesh?.handleOffer(from, it)
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
            // الطرد من المذيع — كان الخادم يرسل KICKED ولا معالج هنا فيبقى المطرود يشاهد.
            "KICKED" -> {
                if (!isBroadcaster) {
                    showKickedNotice()
                    stopStream()
                    return
                }
            }
            "VIEWER_JOINED" -> {
                val viewerId = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                if (viewerId.isNotBlank() && viewerId !in LiveStreamRuntime.viewerIds) {
                    LiveStreamRuntime.viewerIds = LiveStreamRuntime.viewerIds + viewerId
                }
                LiveStreamRuntime.viewerCount = maxOf(LiveStreamRuntime.viewerCount + 1, LiveStreamRuntime.viewerIds.size)
                if (LiveStreamRuntime.viewerCount > LiveStreamRuntime.peakViewers) {
                    LiveStreamRuntime.peakViewers = LiveStreamRuntime.viewerCount
                }
                // LEGENDARY Phase 6: لا عروض mesh والمذيع على SFU — الخادم يوزّع على المشاهدين.
                if (isBroadcaster && viewerId.isNotBlank() && !sfuLive) {
                    // AUTO-FIX (livestream race): queue offers arriving before the mesh is ready
                    // (ICE config still loading) and flush them right after mesh.start succeeds.
                    val session = mesh
                    if (session == null) {
                        pendingViewerOffers.add(viewerId)
                    } else {
                        session.attachPeer(viewerId)
                        session.offerTo(viewerId)
                    }
                }
            }
            "VIEWER_NEEDS_MESH" -> {
                // مشاهد تعذّر عليه SFU — اخدمه وحده عبر mesh احتياطي.
                if (isBroadcaster) {
                    val viewerId = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                    if (viewerId.isNotBlank()) scope.launch { serveViewerOverMesh(viewerId) }
                }
            }
            "VIEWER_COUNT_UPDATED" -> {
                // المرجع الأدق للعدد (يرسله الخادم للجميع عند كل مغادرة).
                val count = signal.payload["viewerCount"]?.toIntOrNull()
                if (count != null) {
                    LiveStreamRuntime.viewerCount = maxOf(count, LiveStreamRuntime.viewerIds.size)
                }
            }
            "VIEWER_LEFT", "PARTICIPANT_LEFT" -> {
                val leftId = signal.userId.ifBlank { signal.payload["userId"].orEmpty() }
                if (isBroadcaster) {
                    if (leftId.isNotBlank()) mesh?.detachPeer(leftId)
                }
                if (leftId.isNotBlank() && leftId in LiveStreamRuntime.viewerIds) {
                    LiveStreamRuntime.viewerIds = LiveStreamRuntime.viewerIds - leftId
                }
                LiveStreamRuntime.viewerCount = (LiveStreamRuntime.viewerCount - 1).coerceAtLeast(LiveStreamRuntime.viewerIds.size)
            }
            "CHAT" -> {
                val senderId = signal.userId
                val senderName = signal.payload["senderName"].orEmpty()
                val text = signal.payload["text"].orEmpty()
                val replyToId = signal.payload["replyToId"]?.takeIf { it.isNotBlank() }
                if (senderName.isNotBlank() && senderId.isNotBlank()) {
                    LiveStreamRuntime.viewerNames = LiveStreamRuntime.viewerNames + (senderId to senderName)
                    if (senderId !in LiveStreamRuntime.viewerIds) {
                        LiveStreamRuntime.viewerIds = LiveStreamRuntime.viewerIds + senderId
                    }
                }
                if (text.isNotBlank()) {
                    val msg = LiveChatMessage(senderId = senderId, senderName = senderName, text = text, replyToId = replyToId)
                    LiveStreamRuntime.chatMessages = (LiveStreamRuntime.chatMessages + msg).takeLast(LIVE_CHAT_MAX_MESSAGES)
                }
            }
            "GIFT" -> {
                // توافق مع نسخ قديمة: حدث GIFT مدفوع لم يعد يُبث من أي عميل
                // حديث (الخادم يحوّله لـ REACTION)، ومن يصل يُعرض كتفاعل مجاني
                // بلا أي ذكر للعملات أو المحفظة.
                val giftEmoji = signal.payload["giftEmoji"]?.takeIf { it.isNotBlank() } ?: "❤️"
                LiveStreamRuntime.reactions = (LiveStreamRuntime.reactions + LiveStreamReaction(emoji = giftEmoji)).takeLast(LIVE_REACTIONS_MAX)
            }
            "REACTION" -> {
                val emoji = signal.payload["emoji"] ?: "❤️"
                val reaction = LiveStreamReaction(emoji = emoji)
                LiveStreamRuntime.reactions = (LiveStreamRuntime.reactions + reaction).takeLast(LIVE_REACTIONS_MAX)
            }
            "PIN_MESSAGE" -> {
                val messageId = signal.payload["messageId"].orEmpty()
                val senderName = signal.payload["senderName"].orEmpty().ifBlank { signal.userId }
                val text = signal.payload["text"].orEmpty()
                if (messageId.isNotBlank() && text.isNotBlank()) {
                    LiveStreamRuntime.pinnedMessage = PinnedLiveMessage(messageId, senderName, text)
                }
            }
            "UNPIN_MESSAGE" -> {
                LiveStreamRuntime.pinnedMessage = null
            }
            "RAISE_HAND" -> {
                val userName = signal.payload["userName"] ?: signal.userId
                if (!LiveStreamRuntime.raisedHands.any { it.userId == signal.userId }) {
                    LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands + RaisedHandUser(signal.userId, userName)
                }
            }
            "LOWER_HAND", "LOWER_HAND_FORCE" -> {
                LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != signal.userId && it.userId != signal.payload["targetUserId"] }
            }
            "REJECT_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands.filter { it.userId != target }
                if (target == userId) {
                    scope.launch {
                        withContext(Dispatchers.Main.immediate) {
                            runCatching {
                                android.widget.Toast.makeText(this@LiveStreamService, "اعتذر المذيع عن طلب الصعود", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            "REMOVE_COHOST", "LEAVE_COHOST" -> {
                val target = signal.payload["targetUserId"]?.takeIf { it.isNotBlank() } ?: signal.userId
                if (target.isNotBlank()) {
                    LiveStreamRuntime.coHostVideos = LiveStreamRuntime.coHostVideos - target
                    if (LiveStreamRuntime.coHostVideos.isEmpty()) LiveStreamRuntime.coHostVideo = null
                    else LiveStreamRuntime.coHostVideo = LiveStreamRuntime.coHostVideos.values.firstOrNull()
                }
                if ((signal.payload["targetUserId"] == userId || signal.userId == userId) && !isBroadcaster) {
                    LiveStreamRuntime.isCoHost = false
                    if (sfu != null) {
                        // LEGENDARY Phase 6: إسقاط النشر — عميل جديد بتذكرة مشاهد (بلا إنتاج) + استهلاك فقط.
                        scope.launch {
                            val fresh = SfuMediaClient(this@LiveStreamService, TokenStore(this@LiveStreamService), this@LiveStreamService)
                            runCatching { sfu?.release() }
                            sfu = fresh
                            LiveStreamRuntime.eglContext = fresh.eglContext
                            LiveStreamRuntime.localVideo = null
                            if (!attachSfuWithRetry(fresh)) {
                                sfu = null
                                engine = WebRtcEngine(this@LiveStreamService, this@LiveStreamService)
                                LiveStreamRuntime.eglContext = engine?.eglContext
                                engine?.createReceiverOnly(CallMediaKind.VIDEO)
                                signaling.sendViewerNeedsMesh(streamId, userId)
                            }
                        }
                    }
                }
            }
            "SLOW_MODE_SET" -> {
                val sec = signal.payload["seconds"]?.toIntOrNull() ?: signal.payload["slowModeSec"]?.toIntOrNull() ?: 0
                LiveStreamRuntime.slowModeSec = sec.coerceIn(0, 60)
            }
            "MUTED" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId || target.isBlank()) {
                    LiveStreamRuntime.isMuted = true
                    engine?.setMicrophoneEnabled(false)
                    mesh?.setMicrophoneEnabled(false)
                }
            }
            "UNMUTED" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId || target.isBlank()) {
                    LiveStreamRuntime.isMuted = false
                    engine?.setMicrophoneEnabled(true)
                    mesh?.setMicrophoneEnabled(true)
                }
            }
            "COHOST_LIST" -> {
                // مزامنة المذيع العائد: coHosts مفصولة بفاصلة + raisedHands بصيغة uid=name;uid=name
                signal.payload["raisedHands"]?.takeIf { it.isNotBlank() }?.let { raw ->
                    val hands = raw.split(";").mapNotNull { part ->
                        val idx = part.indexOf("=")
                        if (idx > 0) {
                            val uid = part.substring(0, idx).trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            val uname = part.substring(idx + 1).trim().ifBlank { uid }
                            RaisedHandUser(uid, uname)
                        } else null
                    }
                    if (hands.isNotEmpty()) {
                        val existing = LiveStreamRuntime.raisedHands.map { it.userId }.toSet()
                        LiveStreamRuntime.raisedHands = LiveStreamRuntime.raisedHands + hands.filter { it.userId !in existing }
                    }
                }
            }
            "STREAM_STATS" -> {
                // إحصائيات المذيع الدورية كل 5s — حدّث العداد والذروة والوضع البطيء
                signal.payload["viewerCount"]?.toIntOrNull()?.let { c ->
                    LiveStreamRuntime.viewerCount = maxOf(c, LiveStreamRuntime.viewerIds.size)
                    if (c > LiveStreamRuntime.peakViewers) LiveStreamRuntime.peakViewers = c
                }
                signal.payload["peakViewers"]?.toIntOrNull()?.let { p ->
                    if (p > LiveStreamRuntime.peakViewers) LiveStreamRuntime.peakViewers = p
                }
                signal.payload["slowModeSec"]?.toIntOrNull()?.let { s ->
                    LiveStreamRuntime.slowModeSec = s.coerceIn(0, 60)
                }
            }
            "RECORDING_STARTED" -> {
                LiveStreamRuntime.isRecording = true
            }
            "RECORDING_STOPPED" -> {
                LiveStreamRuntime.isRecording = false
            }
            "CHAT_DELETED" -> {
                val chatId = signal.payload["chatId"].orEmpty()
                if (chatId.isNotBlank()) {
                    LiveStreamRuntime.chatMessages = LiveStreamRuntime.chatMessages.filter { it.id != chatId }
                    if (LiveStreamRuntime.pinnedMessage?.messageId == chatId) LiveStreamRuntime.pinnedMessage = null
                }
            }
            "APPROVE_COHOST" -> {
                val target = signal.payload["targetUserId"].orEmpty()
                if (target == userId) {
                    // LEGENDARY FIX (صعود أخرس/أعمى): فحص إذن + فيديو حسب الكاميرا + معاينة ذاتية
                    if (!hasAudioPermission()) {
                        LiveStreamRuntime.audioError = "PERMISSION"
                        scope.launch(Dispatchers.Main.immediate) {
                            runCatching {
                                android.widget.Toast.makeText(
                                    this@LiveStreamService,
                                    "امنح إذن الميكروفون للصعود كمضيف",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        return
                    }
                    LiveStreamRuntime.isCoHost = true
                    // المضيف المشارك ينشر صوته وصورته للمذيع عبر إعادة تفاوض
                    // (كان علماً فقط بلا أي نشر — لا يُرى ولا يُسمع).
                    scope.launch {
                        if (sfu != null) {
                            // LEGENDARY Phase 6: عميل جديد بتذكرة COHOST (صلاحية نشر) ثم نشر للـ SFU —
                            // يراه المذيع وبقية المشاهدين معاً (كان mesh للمذيع وحده).
                            val fresh = SfuMediaClient(this@LiveStreamService, TokenStore(this@LiveStreamService), this@LiveStreamService)
                            runCatching { sfu?.release() }
                            sfu = fresh
                            LiveStreamRuntime.eglContext = fresh.eglContext
                            val published = attachSfuWithRetry(fresh) && fresh.publish(CallMediaKind.LIVE)
                            if (published) {
                                LiveStreamRuntime.localVideo = fresh.localVideo
                                LiveStreamRuntime.cameraError = null
                                LiveStreamRuntime.audioError = null
                            } else {
                                LiveStreamRuntime.isCoHost = false
                                withContext(Dispatchers.Main.immediate) {
                                    runCatching {
                                        android.widget.Toast.makeText(
                                            this@LiveStreamService,
                                            "تعذر بدء النشر كمضيف مشارك",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                            return@launch
                        }
                        val wantVideo = hasCameraPermission()
                        val published = engine?.startPublishing(video = wantVideo) == true
                        if (published) {
                            // معاينة ذاتية فورية (كانت غائبة — المضيف لا يرى نفسه)
                            LiveStreamRuntime.localVideo = engine?.localMedia?.videoTrack
                            LiveStreamRuntime.cameraError = if (!wantVideo) "PERMISSION" else null
                            LiveStreamRuntime.audioError = null
                            engine?.setMicrophoneEnabled(true)
                            engine?.offer()
                        } else {
                            LiveStreamRuntime.isCoHost = false
                            withContext(Dispatchers.Main.immediate) {
                                runCatching {
                                    android.widget.Toast.makeText(
                                        this@LiveStreamService,
                                        "تعذر بدء النشر كمضيف مشارك",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** FIX P0 viewer SFU: فعّل المشاهد فور attach ناجح دون انتظار PC CONNECTED (كان يبتلع). */
    private fun markViewerSfuActive() {
        if (isBroadcaster) return
        connectWatchdog?.cancel()
        val now = System.currentTimeMillis()
        if (LiveStreamRuntime.streamStartTime == 0L) LiveStreamRuntime.streamStartTime = now
        // لا تكتب Active إن كان already Active (إعادة اتصال)
        if (LiveStreamRuntime.state !is LiveStreamUiState.Active) {
            LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, false, LiveStreamRuntime.streamStartTime)
        }
        startStatsPolling()
        ensureNetworkWatcher()
    }

    override fun onDisconnected() {
        when (LiveStreamRuntime.state) {
            is LiveStreamUiState.Active, is LiveStreamUiState.Connecting -> {
                LiveStreamRuntime.state = LiveStreamUiState.Connecting(streamId, isBroadcaster)
                scheduleSignalingReconnect()
            }
            else -> stopStream()
        }
    }
    override fun onCameraUnavailable() {
        // FIX: تحقق هل SFU يملك فيديو فعلاً قبل التجاهل — كان يتجاهل حتى مع فشل النشر
        if (sfuLive && LiveStreamRuntime.localVideo != null) return
        // بدل الشاشة السوداء الصامتة: سجل خطأً قابلاً للعرض + تدهور صوتي + تنبيه واحد
        LiveStreamRuntime.cameraError = if (!hasCameraPermission()) "PERMISSION" else "UNAVAILABLE"
        LiveStreamRuntime.isAudioOnly = true
        runCatching { engine?.setCameraEnabled(false) }
        runCatching { mesh?.setCameraEnabled(false) }
        scope.launch {
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    val msg = if (!hasCameraPermission())
                        "إذن الكاميرا مرفوض — يمكنك المتابعة صوت فقط أو منحه من البطاقة أدناه"
                    else
                        "تعذر فتح الكاميرا (مشغولة أو غير مدعومة) — تم التحويل لبث صوتي"
                    android.widget.Toast.makeText(this@LiveStreamService, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    override fun onError(message: String) {
        if (message == "UNAUTHORIZED" || message == "LIVE_STREAM_REGISTRATION_FAILED") {
            scope.launch {
                kotlinx.coroutines.delay(1500)
                stopStream()
            }
            return
        }
        // LEGENDARY FIX (صمت صوتي): أخطاء الميكروفون/الصوت محلية — لا تعيد وصل الشبكة
        if (message.startsWith("AUDIO_RECORD") || message.startsWith("AUDIO_TRACK")) {
            LiveStreamRuntime.audioError = if (!hasAudioPermission()) "PERMISSION" else "UNAVAILABLE"
            scope.launch(Dispatchers.Main.immediate) {
                runCatching {
                    android.widget.Toast.makeText(
                        this@LiveStreamService,
                        "تعذر فتح الميكروفون — تحقق من الإذن أو أن تطبيقاً آخر يستخدمه",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            return
        }
        if (LiveStreamRuntime.state is LiveStreamUiState.Active || LiveStreamRuntime.state is LiveStreamUiState.Connecting) {
            scheduleSignalingReconnect()
            return
        }
        LiveStreamRuntime.state = LiveStreamUiState.Error(message)
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (LiveStreamRuntime.state is LiveStreamUiState.Error) stopStream()
        }
    }

    /** LEGENDARY FIX (عالق Connecting): watchdog 15s — من لا يتصل يرى خطأً صريحاً بدل الانتظار الأبدي */
    private var connectWatchdog: kotlinx.coroutines.Job? = null
    private fun armConnectWatchdog() {
        connectWatchdog?.cancel()
        connectWatchdog = scope.launch {
            kotlinx.coroutines.delay(15_000)
            if (!stopping && LiveStreamRuntime.state is LiveStreamUiState.Connecting) {
                LiveStreamRuntime.state = LiveStreamUiState.Error("LIVE_CONNECT_TIMEOUT")
            }
        }
    }

    /** إعادة اتصال بتراجع أسي مع jitter (1s→2s→…→30s + حتى 500ms عشوائية)
     * بدل القصف الفوري المتكرر — والـ jitter يمنع تزامن إعادة اتصال آلاف
     * المشاهدين بعد عودة الخادم (thundering herd). */
    private fun scheduleSignalingReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val baseMs = (1000L * (1 shl reconnectAttempt.coerceAtMost(5))).coerceAtMost(30_000L)
            reconnectAttempt++
            // AUTO-FIX (livestream UX): stop silent infinite reconnects - fail loudly after 5 tries.
            if (reconnectAttempt > 5) {
                withContext(Dispatchers.Main.immediate) {
                    LiveStreamRuntime.state = LiveStreamUiState.Error("SIGNALING_FAILED")
                }
                stopStream()
                return@launch
            }
            kotlinx.coroutines.delay(baseMs + (0..500).random().toLong())
            if (!stopping && (LiveStreamRuntime.state is LiveStreamUiState.Active || LiveStreamRuntime.state is LiveStreamUiState.Connecting)) {
                runCatching { signaling.reconnect(streamId) }
            }
        }
    }

    private fun flushPendingViewerOffers() {
        if (!isBroadcaster) return
        val session = mesh ?: return
        while (true) {
            val viewerId = pendingViewerOffers.poll() ?: break
            runCatching { session.attachPeer(viewerId); session.offerTo(viewerId) }
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
        runCatching { track.setEnabled(true) }
        if (!isBroadcaster) {
            LiveStreamRuntime.remoteVideo = track
            // FIX P0: مشاهد SFU أول remoteVideo يعني اتصال ناجح — فعّل حالا
            if (sfuLive && LiveStreamRuntime.state is LiveStreamUiState.Connecting) markViewerSfuActive()
        }
    }

    override fun onRemoteVideo(peerId: String, track: VideoTrack) {
        runCatching { track.setEnabled(true) }
        // LEGENDARY Phase 6: late EGL pickup — recv engine may appear after attach (producer joined later).
        if (LiveStreamRuntime.eglContext == null) LiveStreamRuntime.eglContext = sfu?.eglContext
        if (!isBroadcaster) {
            LiveStreamRuntime.remoteVideo = track
            if (sfuLive && LiveStreamRuntime.state is LiveStreamUiState.Connecting) markViewerSfuActive()
        } else {
            // شبكة مضيفين حتى 4 — الأحدث أولاً، الأقدم يُستبدل عند الامتلاء
            val current = LiveStreamRuntime.coHostVideos.toMutableMap()
            current[peerId] = track
            while (current.size > 4) {
                current.remove(current.keys.first())
            }
            LiveStreamRuntime.coHostVideos = current
            LiveStreamRuntime.coHostVideo = track
        }
    }

    override fun onPeerLeft(peerId: String) {
        // SFU: ناشر غادر (مذيع/مضيف) — المشاهد يعتمد على STREAM_ENDED؛ المذيع ينظّف بلاطة المضيف.
        if (isBroadcaster) {
            val current = LiveStreamRuntime.coHostVideos.toMutableMap()
            if (current.remove(peerId) != null) {
                LiveStreamRuntime.coHostVideos = current
                LiveStreamRuntime.coHostVideo = current.values.lastOrNull()
            }
        }
    }

    // موروث من WebRtcEngine.Events و MeshRtcSession.Events بنفس التوقيع — تجاوز صريح إلزامي
    override fun onRemoteAudio(track: org.webrtc.AudioTrack) {
        runCatching { track.setEnabled(true) }
    }

    override fun onNetworkStats(stats: NetworkStats) { LiveStreamRuntime.networkStats = stats }

    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            connectWatchdog?.cancel()
            val now = System.currentTimeMillis()
            if (LiveStreamRuntime.streamStartTime == 0L) LiveStreamRuntime.streamStartTime = now
            LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, isBroadcaster, LiveStreamRuntime.streamStartTime)
            startStatsPolling()
            ensureNetworkWatcher()
        }
        if (state == PeerConnection.PeerConnectionState.FAILED && !stopping) {
            // FIX: مشاهد SFU بلا engine/mesh — أعد ICE عبر SFU
            if (sfuLive) sfu?.restartSfuIce() else { engine?.restartIce(); mesh?.restartIce() }
        }
    }

    override fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            connectWatchdog?.cancel()
            val now = System.currentTimeMillis()
            if (LiveStreamRuntime.streamStartTime == 0L) LiveStreamRuntime.streamStartTime = now
            LiveStreamRuntime.state = LiveStreamUiState.Active(streamId, isBroadcaster, LiveStreamRuntime.streamStartTime)
            startStatsPolling()
            ensureNetworkWatcher()
        }
        if (state == PeerConnection.PeerConnectionState.FAILED && !stopping) {
            if (sfuLive) sfu?.restartSfuIce() else { engine?.restartIce(); mesh?.restartIce() }
        }
    }

    private var statsJob: kotlinx.coroutines.Job? = null
    private var networkWatcher: NetworkChangeWatcher? = null
    private fun ensureNetworkWatcher() {
        if (networkWatcher == null) {
            networkWatcher = NetworkChangeWatcher(this) {
                if (!stopping && LiveStreamRuntime.state is LiveStreamUiState.Active) {
                    if (sfuLive) sfu?.restartSfuIce() else { engine?.restartIce(); mesh?.restartIce() }
                    android.util.Log.d("LiveStreamService", "تبديل الشبكة — إعادة ضبط المسار stream=$streamId sfuLive=$sfuLive")
                    updateNetworkNotification("تبديل الشبكة — إعادة ضبط المسار…")
                }
            }.also { it.start() }
        }
    }
    private fun updateNetworkNotification(text: String) {
        // تعميم نموذج YounesCallService: إشعار مرئي + Log عند تبديل الشبكة — كان restartIce صامتاً.
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val title = if (isBroadcaster) "بث مباشر يونس" else "مشاهدة بث يونس"
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
            .addAction(0, "إيقاف", CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_LIVE_STOP, CallNotificationActionReceiver.CALL_TYPE_LIVESTREAM, 7403, callId = streamId, myUserId = userId, hostId = "", isVideo = isBroadcaster))
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(7403, notif) }
    }
    private fun stopNetworkWatcher() {
        networkWatcher?.stop(); networkWatcher = null
    }
    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = scope.launch {
            // إلغاء كوروتينات: while(isActive && !stopping) حتى لا يتسرب polling
            // بعد stopStream()، ولا polling قبل بدء البث أو بعد انتهائه.
            while (isActive && !stopping) {
                if (LiveStreamRuntime.state is LiveStreamUiState.Active) {
                    runCatching { engine?.pollStats() }
                    runCatching { mesh?.pollStats() }
                    runCatching { sfu?.pollStats() }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    /** إشعار الطرد للمشاهد قبل إغلاق الواجهة. */
    private fun showKickedNotice() {
        scope.launch {
            withContext(Dispatchers.Main.immediate) {
                runCatching {
                    android.widget.Toast.makeText(this@LiveStreamService, "أخرجك المذيع من البث", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopStream() {
        if (stopping) return
        stopping = true
        saveLiveStreamCallLogLocally()
        // ملخص النهاية الأسطوري (المدة + الذروة + الرسائل) — يُعرض في finishStop
        val startedAt = (LiveStreamRuntime.state as? LiveStreamUiState.Active)?.startedAt
            ?: LiveStreamRuntime.streamStartTime.takeIf { it > 0L }
        val durationSec = if (startedAt != null && startedAt > 0L) ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0) else 0
        val peak = LiveStreamRuntime.peakViewers.coerceAtLeast(LiveStreamRuntime.viewerCount)
        val msgs = LiveStreamRuntime.chatMessages.size
        val summary = if (isBroadcaster && (durationSec > 0 || peak > 0)) {
            "انتهى البث 🔴 — المدة %02d:%02d • الذروة %d • الرسائل %d".format(durationSec / 60, durationSec % 60, peak, msgs)
        } else null
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
            withContext(Dispatchers.Main.immediate) {
                summary?.let { s ->
                    runCatching {
                        android.widget.Toast.makeText(this@LiveStreamService, s, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
                finishStop()
            }
        }
    }

    /**
     * يسجّل البث محلياً في سجل المكالمات — كان السجل المحلي يقتصر على المكالمات
     * الفردية بينما فلتر "بث" في الواجهة يبقى فارغاً دائماً.
     */
    private fun saveLiveStreamCallLogLocally() {
        if (streamId.isBlank()) return
        val startedAt = (LiveStreamRuntime.state as? LiveStreamUiState.Active)?.startedAt ?: 0L
        val durationMs = if (startedAt > 0L) (System.currentTimeMillis() - startedAt).coerceAtLeast(0L) else 0L
        val cipher = CallLogCipher()
        val log = com.red.sovereign.core.database.CallLogEntity(
            id = "$streamId-${startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()}",
            peerId = cipher.encryptPeerId(streamId),
            peerLabel = cipher.encryptLabel(streamId),
            type = "LIVE",
            direction = if (isBroadcaster) "OUTGOING" else "INCOMING",
            route = "RED",
            status = if (durationMs > 0L) "ENDED" else "FAILED",
            timestamp = startedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            durationMs = durationMs,
            answeredAt = startedAt.takeIf { it > 0L && durationMs > 0L },
            endedAt = (startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()) + durationMs
        )
        scope.launch { runCatching { com.red.sovereign.core.database.LocalRepository(this@LiveStreamService).saveCallLog(log) } }
    }

    private fun finishStop(terminateService: Boolean = true) {
        if (cleanedUp) return
        cleanedUp = true
        statsJob?.cancel(); statsJob = null
        stopNetworkWatcher()
        if (streamId.isNotBlank()) signaling.leave(streamId, userId)
        signaling.close()
        engine?.release()
        engine = null
        mesh?.release()
        mesh = null
        runCatching { sfu?.release() }
        sfu = null
        sfuLive = false
        broadcasterUserId = ""
        approvedCohostIds.clear()
        LiveStreamRuntime.state = LiveStreamUiState.Idle
        LiveStreamRuntime.localVideo = null
        LiveStreamRuntime.remoteVideo = null
        LiveStreamRuntime.coHostVideo = null
        LiveStreamRuntime.coHostVideos = emptyMap()
        LiveStreamRuntime.eglContext = null
        LiveStreamRuntime.networkStats = NetworkStats()
        LiveStreamRuntime.viewerCount = 0
        LiveStreamRuntime.viewerIds = emptyList()
        LiveStreamRuntime.viewerNames = emptyMap()
        LiveStreamRuntime.chatMessages = emptyList()
        LiveStreamRuntime.reactions = emptyList()
        LiveStreamRuntime.raisedHands = emptyList()
        LiveStreamRuntime.pinnedMessage = null
        LiveStreamRuntime.isCoHost = false
        LiveStreamRuntime.isMuted = false
        LiveStreamRuntime.isAudioOnly = false
        LiveStreamRuntime.isRecording = false
        LiveStreamRuntime.quality = LiveQuality.AUTO
        LiveStreamRuntime.showStats = false
        LiveStreamRuntime.slowModeSec = 0
        LiveStreamRuntime.streamStartTime = 0L
        LiveStreamRuntime.peakViewers = 0
        LiveStreamRuntime.cameraError = null
        LiveStreamRuntime.audioError = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (terminateService) stopSelf()
    }

    private fun showIncomingLiveStreamNotification(streamId: String, userId: String, broadcasterName: String) {
        val watchPi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_LIVE_WATCH, CallNotificationActionReceiver.CALL_TYPE_LIVESTREAM, 7404, callId = streamId, myUserId = userId, hostId = "", isVideo = true)
        val dismissPi = CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_LIVE_STOP, CallNotificationActionReceiver.CALL_TYPE_LIVESTREAM, 7404, callId = streamId, myUserId = userId, hostId = "", isVideo = true)

        val mainIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_LIVESTREAM)
                putExtra(EXTRA_STREAM_ID, streamId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_BROADCASTER_NAME, broadcasterName)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching { IncomingCallActivity.launchLive(this, streamId, userId, broadcasterName) }

        val notif = NotificationCompat.Builder(this, "red_calls")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("بث مباشر يونس")
            .setContentText("بدأ ${broadcasterName.ifBlank { "المُبث" }} بثاً مباشراً")
            .setContentIntent(mainIntent)
            .setFullScreenIntent(mainIntent, true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(false)
            .setColor(0xFFE53935.toInt())
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(0, "مشاهدة", watchPi)
            .addAction(0, "تجاهل", dismissPi)
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
            .addAction(0, "إيقاف", CallNotificationActionReceiver.receiverIntent(this, CallNotificationActionReceiver.ACTION_LIVE_STOP, CallNotificationActionReceiver.CALL_TYPE_LIVESTREAM, 7403, callId = streamId, myUserId = userId, hostId = "", isVideo = isBroadcaster))
            .build()
        // FIX targetSdk 34: لا تدّعِ CAMERA/MIC بدون إذن ممنوح — وإلا SecurityException
        // المذيع: MIC (+CAMERA إن ممنوح) | المشاهد recvonly: بلا MIC إلا إن كان coHost يحتاج ميك
        var type = 0
        if (isBroadcaster) {
            if (hasAudioPermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (hasCameraPermission() && hasAudioPermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            // fallback إن بلا أذونات: سيعرض Error لكن نحتاج FGS — استخدم MIC مع catch
            if (type == 0) type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            // viewer recvonly: لا يحتاج ميك/كاميرا — استخدم mediaPlayback إن متاح، وإلا microphone مع حماية
            // manifest يملك camera|microphone|phoneCall|mediaProjection — نستخدم phoneCall كـ neutral إن بلا ميك
            // لتجنب SecurityException على 34. الأفضل: استخدم MICROPHONE فقط إن ممنوح
            if (hasAudioPermission() && LiveStreamRuntime.isCoHost) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                // viewer عادي: استخدم MICROPHONE فقط كـ fallback مع حماية كراش
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (!hasAudioPermission()) {
                    // جرّب بدون ادعاء ميك عبر phoneCall (لا يتطلب إذن runtime)
                    type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                }
            }
        }
        runCatching { ServiceCompat.startForeground(this, 7403, notif, type) }
            .onFailure {
                // fallback أخير: بدون نوع محدد (قد ينجح على بعض إصدارات 34)
                runCatching { ServiceCompat.startForeground(this, 7403, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) }
            }
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
        /** محفوظ للتوافق فقط — الهدايا معطلة وتُحوَّل لتفاعل مجاني (انظر sendReaction). */
        const val ACTION_SEND_GIFT = "com.red.sovereign.livestream.SEND_GIFT"
        const val ACTION_RAISE_HAND = "com.red.sovereign.livestream.RAISE_HAND"
        const val ACTION_LOWER_HAND = "com.red.sovereign.livestream.LOWER_HAND"
        const val ACTION_APPROVE_COHOST = "com.red.sovereign.livestream.APPROVE_COHOST"
        const val ACTION_REJECT_COHOST = "com.red.sovereign.livestream.REJECT_COHOST"
        const val ACTION_REMOVE_COHOST = "com.red.sovereign.livestream.REMOVE_COHOST"
        const val ACTION_LEAVE_COHOST = "com.red.sovereign.livestream.LEAVE_COHOST"
        const val ACTION_KICK_VIEWER = "com.red.sovereign.livestream.KICK_VIEWER"
        const val ACTION_MUTE_VIEWER = "com.red.sovereign.livestream.MUTE_VIEWER"
        const val ACTION_SLOW_MODE = "com.red.sovereign.livestream.SLOW_MODE"
        const val ACTION_SET_QUALITY = "com.red.sovereign.livestream.SET_QUALITY"
        const val ACTION_TOGGLE_STATS = "com.red.sovereign.livestream.TOGGLE_STATS"
        const val ACTION_RETRY_MEDIA = "com.red.sovereign.livestream.RETRY_MEDIA"
        const val ACTION_PIN_MESSAGE = "com.red.sovereign.livestream.PIN_MESSAGE"
        const val ACTION_UNPIN_MESSAGE = "com.red.sovereign.livestream.UNPIN_MESSAGE"
        const val ACTION_DELETE_CHAT = "com.red.sovereign.livestream.DELETE_CHAT"

        const val EXTRA_STREAM_ID = "stream_id"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_BROADCASTER = "broadcaster"
        const val EXTRA_BROADCASTER_NAME = "broadcaster_name"
        const val EXTRA_TITLE = "stream_title"
        const val EXTRA_CATEGORY = "stream_category"
        const val EXTRA_IS_PRIVATE = "is_private"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_CHAT_TEXT = "chat_text"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_REACTION_EMOJI = "reaction_emoji"
        const val EXTRA_GIFT_ID = "gift_id"
        const val EXTRA_GIFT_EMOJI = "gift_emoji"
        const val EXTRA_GIFT_NAME = "gift_name"
        const val EXTRA_GIFT_COST = "gift_cost"
        const val EXTRA_TARGET_USER_ID = "target_user_id"
        const val EXTRA_PIN_MESSAGE_ID = "pin_message_id"
        const val EXTRA_PIN_TEXT = "pin_text"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_REPLY_TO_ID = "reply_to_id"
        const val EXTRA_SLOW_SECONDS = "slow_seconds"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_MUTED = "muted"

        fun sendChat(context: Context, text: String, senderName: String, replyToId: String? = null) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_SEND_CHAT
                putExtra(EXTRA_CHAT_TEXT, text)
                putExtra(EXTRA_SENDER_NAME, senderName)
                if (!replyToId.isNullOrBlank()) putExtra(EXTRA_REPLY_TO_ID, replyToId)
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

        /** تثبيت تعليق (يستخدمها المذيع عادة) — يُبث PIN_MESSAGE للجميع. */
        fun pinMessage(context: Context, messageId: String, senderName: String, text: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_PIN_MESSAGE
                putExtra(EXTRA_PIN_MESSAGE_ID, messageId)
                putExtra(EXTRA_SENDER_NAME, senderName)
                putExtra(EXTRA_PIN_TEXT, text)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** إلغاء تثبيت التعليق (المذيع). */
        fun unpinMessage(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(ACTION_UNPIN_MESSAGE))
        }

        /** حذف رسالة (المذيع) — يبث CHAT_DELETED للغرفة عبر الخادم. */
        fun deleteChat(context: Context, chatId: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_DELETE_CHAT
                putExtra(EXTRA_CHAT_ID, chatId)
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

        fun rejectCoHost(context: Context, targetUserId: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_REJECT_COHOST
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun removeCoHost(context: Context, targetUserId: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_REMOVE_COHOST
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun lowerHand(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(ACTION_LOWER_HAND))
        }

        fun kickViewer(context: Context, targetUserId: String) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_KICK_VIEWER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun muteViewer(context: Context, targetUserId: String, muted: Boolean = true) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_MUTE_VIEWER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
                putExtra(EXTRA_MUTED, muted)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setSlowMode(context: Context, seconds: Int) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_SLOW_MODE
                putExtra(EXTRA_SLOW_SECONDS, seconds.coerceIn(0, 60))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setQuality(context: Context, quality: LiveQuality) {
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_SET_QUALITY
                putExtra(EXTRA_QUALITY, quality.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun retryMedia(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(ACTION_RETRY_MEDIA))
        }

        fun invite(context: Context, streamId: String, userId: String, broadcasterName: String) {
            val safeId = if (streamId.isBlank()) "" else RoomSeparationPolicy.normalizeStreamId(streamId)
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_INVITE
                putExtra(EXTRA_STREAM_ID, safeId)
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
            title: String = "بث مباشر يونس",
            isPrivate: Boolean = false,
            password: String? = null,
            category: String = "عام"
        ) {
            val safeId = if (streamId.isBlank()) "" else RoomSeparationPolicy.normalizeStreamId(streamId)
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STREAM_ID, safeId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_BROADCASTER, isBroadcaster)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CATEGORY, category)
                putExtra(EXTRA_IS_PRIVATE, isPrivate)
                if (!password.isNullOrBlank()) putExtra(EXTRA_PASSWORD, password)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** انضمام مشاهد مع كلمة سر (للبث الخاص) */
        fun joinWithPassword(
            context: Context,
            streamId: String,
            userId: String,
            password: String?
        ) = start(context, streamId, userId, false, password = password)
        fun stop(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(ACTION_STOP))
        }
        fun action(context: Context, act: String) {
            ContextCompat.startForegroundService(context, Intent(context, LiveStreamService::class.java).setAction(act))
        }
        fun watch(context: Context, streamId: String, userId: String, password: String? = null) {
            val safeId = if (streamId.isBlank()) "" else RoomSeparationPolicy.normalizeStreamId(streamId)
            val intent = Intent(context, LiveStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STREAM_ID, safeId)
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_BROADCASTER, false)
                if (!password.isNullOrBlank()) putExtra(EXTRA_PASSWORD, password)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
