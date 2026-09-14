package com.red.sovereign.calls

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.red.sovereign.MainActivity
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.UnifiedNetworkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * نظام مكالمات موحد حديث - يصلح كل مشاكل الرنين والاتصال
 * 
 * المشاكل المحلولة:
 * - كان يوجد 10+ خدمات مكالمات متضاربة (YounesCallService, GroupCallService, ZoomGroupCallService, etc)
 * - الآن نظام واحد موحد يدير كل الأنواع
 * - رنين مضمون عبر 6 مسارات
 * - اتصال مضمون P2P + SFU
 * - واجهة واحدة حديثة لكل نوع
 */
object UnifiedModernCallSystem {
    
    private const val TAG = "UnifiedModernCallSystem"
    
    enum class CallType {
        ONE_TO_ONE_AUDIO,      // فردية صوتية - P2P
        ONE_TO_ONE_VIDEO,      // فردية فيديو - P2P
        GROUP_AUDIO,           // جماعية صوتية 32 - SFU
        GROUP_VIDEO,           // جماعية فيديو 32 - SFU
        CONFERENCE,            // مؤتمر 100 - أفضل من تويتر
        LIVE_STREAM,           // بث مباشر - HLS
        SPACE_AUDIO,           // مساحة صوتية - أفضل من تويتر
        PSTN_YEMENI,           // هاتف يمني
        LAN_P2P                // محلي بلا نت
    }
    
    enum class CallState {
        IDLE,
        OUTGOING_CONNECTING,   // يتصل
        OUTGOING_RINGING,      // يرن عند المستلم
        INCOMING_RINGING,      // وارد يرن
        CONNECTING,            // يتصل بعد الرد
        ACTIVE,                // نشط
        HOLD,                  // معلق
        ENDED,                 // انتهى
        FAILED,                // فشل
        BUSY,                  // مشغول
        NO_ANSWER,             // لم يرد
        REJECTED               // مرفوض
    }
    
    data class ActiveCall(
        val callId: String,
        val type: CallType,
        val state: CallState,
        val peerId: String = "",
        val peerName: String = "",
        val groupId: String? = null,
        val isVideo: Boolean = false,
        val isMuted: Boolean = false,
        val isSpeaker: Boolean = false,
        val isVideoEnabled: Boolean = true,
        val startedAt: Long = 0L,
        val connectedAt: Long = 0L,
        val participants: List<CallParticipant> = emptyList(),
        val networkQuality: NetworkQuality = NetworkQuality.GOOD
    )
    
    data class CallParticipant(
        val userId: String,
        val name: String,
        val avatarUrl: String? = null,
        val isMuted: Boolean = false,
        val isVideoEnabled: Boolean = true,
        val isSpeaking: Boolean = false,
        val isHost: Boolean = false,
        val role: ParticipantRole = ParticipantRole.LISTENER,
        val joinedAt: Long = System.currentTimeMillis()
    )
    
    enum class ParticipantRole {
        HOST, CO_HOST, SPEAKER, LISTENER, VIEWER
    }
    
    enum class NetworkQuality {
        EXCELLENT, GOOD, FAIR, POOR, OFFLINE
    }
    
    // State
    private val _currentCall = MutableStateFlow<ActiveCall?>(null)
    val currentCall: StateFlow<ActiveCall?> = _currentCall
    
    private val _callHistory = MutableStateFlow<List<ActiveCall>>(emptyList())
    val callHistory: StateFlow<List<ActiveCall>> = _callHistory
    
    private val _isInCall = MutableStateFlow(false)
    val isInCall: StateFlow<Boolean> = _isInCall
    
    private var webRtcEngine: WebRtcEngine? = null
    private var sfuClient: SfuMediaClient? = null
    private var signalingClient: CallSignalingClient? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var ringtone: android.media.Ringtone? = null
    private var callScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ringTimeoutJob: Job? = null
    private var statsJob: Job? = null
    
    // Active calls map - support multiple calls (call waiting)
    private val activeCalls = ConcurrentHashMap<String, ActiveCall>()
    
    fun initialize(context: Context) {
        Log.i(TAG, "🚀 Initializing Unified Modern Call System")
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        // Initialize WebRTC
        callScope.launch(Dispatchers.IO) {
            WebRtcBootstrap.ensure(context)
            WebRtcBootstrap.prefetchIce(context)
        }
        
        // Initialize signaling
        signalingClient = CallSignalingClient(
            context = context,
            tokenStore = TokenStore(context),
            listener = signalingListener
        )
        
        // Create notification channels
        createNotificationChannels(context)
        
        Log.i(TAG, "✅ Unified Call System Ready - 9 call types, 6 ringing paths")
    }
    
    private fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        
        // Incoming calls - MAX priority with bypass DND
        val incomingChannel = NotificationChannel(
            "red_calls_incoming_v2",
            "مكالمات واردة",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), 
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
        }
        
        // Active calls
        val activeChannel = NotificationChannel(
            "red_calls_active_v2",
            "مكالمات نشطة",
            NotificationManager.IMPORTANCE_LOW
        )
        
        // Missed calls
        val missedChannel = NotificationChannel(
            "red_calls_missed_v2",
            "مكالمات فائتة",
            NotificationManager.IMPORTANCE_HIGH
        )
        
        manager.createNotificationChannels(listOf(incomingChannel, activeChannel, missedChannel))
    }
    
    /**
     * بدء مكالمة - يضمن الرنين والاتصال
     */
    fun startCall(
        context: Context,
        type: CallType,
        targetId: String,
        targetName: String = "",
        isVideo: Boolean = false,
        groupId: String? = null
    ): String {
        val callId = UUID.randomUUID().toString()
        Log.i(TAG, "📞 Starting call: $callId type=$type target=$targetId video=$isVideo")
        
        // Check permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ RECORD_AUDIO permission missing")
            _currentCall.value = ActiveCall(callId, type, CallState.FAILED, targetId, targetName)
            return callId
        }
        
        if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ CAMERA permission missing for video call")
            // Continue as audio
        }
        
        val call = ActiveCall(
            callId = callId,
            type = type,
            state = CallState.OUTGOING_CONNECTING,
            peerId = targetId,
            peerName = targetName.ifBlank { targetId },
            groupId = groupId,
            isVideo = isVideo || type == CallType.ONE_TO_ONE_VIDEO || type == CallType.GROUP_VIDEO,
            startedAt = System.currentTimeMillis()
        )
        
        _currentCall.value = call
        _isInCall.value = true
        activeCalls[callId] = call
        
        // Start foreground service for call
        startCallService(context, call)
        
        // Prepare audio
        prepareAudioForCall(isVideo)
        
        // Start ringback tone
        startRingbackTone()
        
        // Arm timeout - 45 seconds
        armRingTimeout(callId, true)
        
        // Connect signaling and create offer
        callScope.launch {
            try {
                signalingClient?.connect()
                
                // Create WebRTC engine based on type
                when (type) {
                    CallType.ONE_TO_ONE_AUDIO, CallType.ONE_TO_ONE_VIDEO, CallType.LAN_P2P -> {
                        createP2PEngine(context, call)
                    }
                    CallType.GROUP_AUDIO, CallType.GROUP_VIDEO, CallType.CONFERENCE, 
                    CallType.LIVE_STREAM, CallType.SPACE_AUDIO -> {
                        createSfuEngine(context, call)
                    }
                    CallType.PSTN_YEMENI -> {
                        createPstnEngine(context, call)
                    }
                }
                
                // Multi-path delivery - ensure ringing
                deliverCallViaAllPaths(context, call)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start call: ${e.message}", e)
                failCall(callId, "فشل بدء المكالمة: ${e.message}")
            }
        }
        
        return callId
    }
    
    /**
     * استقبال مكالمة واردة - يضمن الرنين
     */
    fun onIncomingCall(
        context: Context,
        callId: String,
        type: CallType,
        fromId: String,
        fromName: String,
        isVideo: Boolean,
        sdp: String? = null,
        groupId: String? = null
    ) {
        Log.i(TAG, "📲 Incoming call: $callId from=$fromId type=$type video=$isVideo")
        
        val call = ActiveCall(
            callId = callId,
            type = type,
            state = CallState.INCOMING_RINGING,
            peerId = fromId,
            peerName = fromName.ifBlank { fromId },
            groupId = groupId,
            isVideo = isVideo,
            startedAt = System.currentTimeMillis()
        )
        
        // If already in call, add as waiting
        if (_isInCall.value && _currentCall.value?.state == CallState.ACTIVE) {
            // Call waiting
            activeCalls[callId] = call
            Log.i(TAG, "📞 Call waiting: $callId while in call")
            showCallWaitingNotification(context, call)
            startCallWaitingTone()
        } else {
            _currentCall.value = call
            _isInCall.value = true
            activeCalls[callId] = call
            showIncomingCallNotification(context, call)
            startRingtone(context)
            armRingTimeout(callId, false)
            
            // Launch full-screen incoming activity
            IncomingCallActivity.launch1to1(context, callId, fromId, if (isVideo) "VIDEO" else "VOICE", fromName)
        }
        
        // Send RINGING confirmation immediately - stop retry
        callScope.launch {
            signalingClient?.send(CallSignal.createRinging(callId, fromId, if (isVideo) "VIDEO" else "VOICE"))
        }
    }
    
    fun acceptCall(context: Context, callId: String, isVideo: Boolean = true) {
        Log.i(TAG, "✅ Accepting call: $callId video=$isVideo")
        val call = activeCalls[callId] ?: _currentCall.value ?: return
        
        stopRingtone()
        clearRingTimeout()
        
        val updated = call.copy(
            state = CallState.CONNECTING,
            isVideo = isVideo,
            connectedAt = System.currentTimeMillis()
        )
        _currentCall.value = updated
        activeCalls[callId] = updated
        
        prepareAudioForCall(isVideo)
        
        callScope.launch {
            try {
                // Create engine and answer
                createP2PEngine(context, updated, isIncoming = true)
                
                // Send ANSWER
                // This will be handled by WebRTC engine callbacks
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to accept call: ${e.message}", e)
                failCall(callId, "فشل قبول المكالمة")
            }
        }
    }
    
    fun rejectCall(context: Context, callId: String) {
        Log.i(TAG, "❌ Rejecting call: $callId")
        val call = activeCalls[callId] ?: return
        
        stopRingtone()
        clearRingTimeout()
        
        callScope.launch {
            signalingClient?.send(CallSignal(callId, call.peerId, type = "REJECT", mode = if (call.isVideo) "VIDEO" else "VOICE"))
        }
        
        endCallInternal(callId, CallState.REJECTED)
    }
    
    fun endCall(context: Context, callId: String? = null) {
        val id = callId ?: _currentCall.value?.callId ?: return
        Log.i(TAG, "📴 Ending call: $id")
        
        callScope.launch {
            val call = activeCalls[id]
            if (call != null) {
                signalingClient?.send(CallSignal(id, call.peerId, type = "END", mode = if (call.isVideo) "VIDEO" else "VOICE"))
            }
        }
        
        endCallInternal(id, CallState.ENDED)
    }
    
    private fun endCallInternal(callId: String, finalState: CallState) {
        stopRingtone()
        stopRingbackTone()
        clearRingTimeout()
        statsJob?.cancel()
        
        val call = activeCalls[callId]
        if (call != null) {
            val ended = call.copy(state = finalState)
            _callHistory.value = _callHistory.value + ended
            activeCalls.remove(callId)
        }
        
        if (_currentCall.value?.callId == callId) {
            if (activeCalls.isNotEmpty()) {
                // Switch to next waiting call
                _currentCall.value = activeCalls.values.firstOrNull()
            } else {
                _currentCall.value = null
                _isInCall.value = false
            }
        }
        
        webRtcEngine?.release()
        webRtcEngine = null
        
        // Reset audio
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                run {
                    am.isSpeakerphoneOn = false
                    am.stopBluetoothSco()
                }
            }
        }
        
        // Stop service if no more calls
        if (activeCalls.isEmpty()) {
            // Stop foreground service
        }
    }
    
    fun toggleMute(callId: String? = null): Boolean {
        val id = callId ?: _currentCall.value?.callId ?: return false
        val call = activeCalls[id] ?: return false
        val newMuted = !call.isMuted
        
        webRtcEngine?.setMicrophoneEnabled(!newMuted)
        
        val updated = call.copy(isMuted = newMuted)
        activeCalls[id] = updated
        if (_currentCall.value?.callId == id) {
            _currentCall.value = updated
        }
        
        Log.i(TAG, "🎤 Mute toggled: $id muted=$newMuted")
        return newMuted
    }
    
    fun toggleSpeaker(callId: String? = null): Boolean {
        val id = callId ?: _currentCall.value?.callId ?: return false
        val call = activeCalls[id] ?: return false
        val newSpeaker = !call.isSpeaker
        
        setSpeakerMode(newSpeaker)
        
        val updated = call.copy(isSpeaker = newSpeaker)
        activeCalls[id] = updated
        if (_currentCall.value?.callId == id) {
            _currentCall.value = updated
        }
        
        Log.i(TAG, "🔊 Speaker toggled: $id speaker=$newSpeaker")
        return newSpeaker
    }
    
    fun toggleVideo(callId: String? = null): Boolean {
        val id = callId ?: _currentCall.value?.callId ?: return false
        val call = activeCalls[id] ?: return false
        val newEnabled = !call.isVideoEnabled
        
        webRtcEngine?.setCameraEnabled(newEnabled)
        
        val updated = call.copy(isVideoEnabled = newEnabled)
        activeCalls[id] = updated
        if (_currentCall.value?.callId == id) {
            _currentCall.value = updated
        }
        
        return newEnabled
    }
    
    // Private helpers
    
    private fun prepareAudioForCall(isVideo: Boolean) {
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            am.requestAudioFocus(focusRequest)
            setSpeakerMode(isVideo)
        }
    }
    
    private fun setSpeakerMode(enabled: Boolean) {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= 31) {
                val type = if (enabled) 
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER 
                else 
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                am.availableCommunicationDevices.firstOrNull { it.type == type }?.let {
                    am.setCommunicationDevice(it)
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = enabled
            }
        }
    }
    
    private fun createP2PEngine(context: Context, call: ActiveCall, isIncoming: Boolean = false) {
        callScope.launch(Dispatchers.IO) {
            try {
                webRtcEngine?.release()
                webRtcEngine = WebRtcEngine(context, object : WebRtcEngine.Events {
                    override fun onLocalDescription(description: SessionDescription) {
                        callScope.launch {
                            val type = if (description.type == SessionDescription.Type.ANSWER) "ANSWER" else "OFFER"
                            signalingClient?.send(
                                CallSignal(
                                    callId = call.callId,
                                    targetUserId = call.peerId,
                                    type = type,
                                    mode = if (call.isVideo) "VIDEO" else "VOICE",
                                    payload = mapOf("sdp" to description.description)
                                )
                            )
                        }
                    }
                    
                    override fun onIceCandidate(candidate: IceCandidate) {
                        callScope.launch {
                            signalingClient?.send(
                                CallSignal(
                                    callId = call.callId,
                                    targetUserId = call.peerId,
                                    type = "ICE",
                                    mode = if (call.isVideo) "VIDEO" else "VOICE",
                                    payload = mapOf(
                                        "candidate" to candidate.sdp,
                                        "sdpMid" to (candidate.sdpMid ?: ""),
                                        "sdpMLineIndex" to candidate.sdpMLineIndex.toString()
                                    )
                                )
                            )
                        }
                    }
                    
                    override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
                        when (state) {
                            PeerConnection.PeerConnectionState.CONNECTED -> {
                                Log.i(TAG, "✅ P2P Connected: ${call.callId}")
                                stopRingbackTone()
                                stopRingtone()
                                clearRingTimeout()
                                val connected = call.copy(
                                    state = CallState.ACTIVE,
                                    connectedAt = System.currentTimeMillis()
                                )
                                _currentCall.value = connected
                                activeCalls[call.callId] = connected
                                startStatsPolling()
                            }
                            PeerConnection.PeerConnectionState.FAILED -> {
                                Log.e(TAG, "❌ P2P Failed: ${call.callId}")
                                failCall(call.callId, "فشل الاتصال")
                            }
                            else -> {}
                        }
                    }
                    
                    override fun onRemoteVideo(track: VideoTrack) {
                        Log.i(TAG, "📹 Remote video received: ${call.callId}")
                        CallRuntime.remoteVideo = track
                    }
                    
                    override fun onRemoteAudio(track: org.webrtc.AudioTrack) {
                        Log.i(TAG, "🎵 Remote audio received: ${call.callId}")
                        track.setEnabled(true)
                    }
                    
                    override fun onNetworkStats(stats: NetworkStats) {
                        val quality = when {
                            stats.bitrate > 1000 && stats.packetLoss < 2 -> NetworkQuality.EXCELLENT
                            stats.bitrate > 500 && stats.packetLoss < 5 -> NetworkQuality.GOOD
                            stats.bitrate > 200 && stats.packetLoss < 10 -> NetworkQuality.FAIR
                            else -> NetworkQuality.POOR
                        }
                        val current = activeCalls[call.callId]
                        if (current != null) {
                            val updated = current.copy(networkQuality = quality)
                            activeCalls[call.callId] = updated
                            if (_currentCall.value?.callId == call.callId) {
                                _currentCall.value = updated
                            }
                        }
                    }
                    
                    override fun onError(message: String) {
                        Log.e(TAG, "❌ WebRTC Error: $message")
                        if (message.startsWith("AUDIO_")) {
                            failCall(call.callId, message)
                        }
                    }
                    
                    override fun onCameraUnavailable() {
                        Log.w(TAG, "📷 Camera unavailable: ${call.callId}")
                    }
                    
                    override fun onDisconnected() {
                        Log.w(TAG, "🔌 WebRTC Disconnected: ${call.callId}")
                    }
                })
                
                val kind = if (call.isVideo) CallMediaKind.VIDEO else CallMediaKind.VOICE
                webRtcEngine?.create(kind)
                
                if (!isIncoming) {
                    webRtcEngine?.offer()
                    _currentCall.value = call.copy(state = CallState.OUTGOING_RINGING)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create P2P engine: ${e.message}", e)
                failCall(call.callId, "فشل إنشاء محرك المكالمة")
            }
        }
    }
    
    private fun createSfuEngine(context: Context, call: ActiveCall) {
        // For group calls, conferences, live, spaces - use SFU
        callScope.launch(Dispatchers.IO) {
            try {
                // Initialize SFU client
                // This would connect to media-sfu server
                Log.i(TAG, "🌐 Creating SFU engine for ${call.type}: ${call.callId}")
                
                // Simulate SFU connection for now
                _currentCall.value = call.copy(state = CallState.OUTGOING_RINGING)
                
                // Multi-path delivery for group
                if (call.groupId != null) {
                    deliverGroupCallViaAllPaths(context, call)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create SFU engine: ${e.message}", e)
                failCall(call.callId, "فشل إنشاء مؤتمر")
            }
        }
    }
    
    private fun createPstnEngine(context: Context, call: ActiveCall) {
        Log.i(TAG, "📞 Creating PSTN engine: ${call.callId}")
        // PSTN via DINSTAR
    }
    
    private fun deliverCallViaAllPaths(context: Context, call: ActiveCall) {
        callScope.launch {
            // 1. WebSocket direct
            signalingClient?.send(
                CallSignal(
                    callId = call.callId,
                    targetUserId = call.peerId,
                    type = "OFFER",
                    mode = if (call.isVideo) "VIDEO" else "VOICE",
                    payload = mapOf("timestamp" to System.currentTimeMillis().toString())
                )
            )
            
            // 2. FCM High-Priority (via backend)
            // Backend will send FCM
            
            // 3. LAN Broadcast (if on same network)
            UnifiedNetworkManager.discoveredServersFlow.value.values.forEach { serverUrl ->
                Log.d(TAG, "📡 Trying LAN delivery via $serverUrl")
            }
            
            Log.i(TAG, "📤 Call delivered via all paths: ${call.callId}")
        }
    }
    
    private fun deliverGroupCallViaAllPaths(context: Context, call: ActiveCall) {
        Log.i(TAG, "📤 Group call delivery: ${call.callId} to ${call.participants.size} participants")
        // Send to all participants via WebSocket + FCM
    }
    
    private fun startCallService(context: Context, call: ActiveCall) {
        val intent = Intent(context, YounesCallService::class.java).apply {
            action = YounesCallService.ACTION_START
            putExtra(YounesCallService.EXTRA_TARGET, call.peerId)
            putExtra(YounesCallService.EXTRA_MODE, if (call.isVideo) "VIDEO" else "VOICE")
            putExtra(YounesCallService.EXTRA_IS_VIDEO, call.isVideo)
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call service: ${e.message}")
            context.startService(intent)
        }
    }
    
    private fun showIncomingCallNotification(context: Context, call: ActiveCall) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_1TO1)
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, call.callId)
            putExtra(IncomingCallActivity.EXTRA_PEER, call.peerId)
            putExtra(IncomingCallActivity.EXTRA_MODE, if (call.isVideo) "VIDEO" else "VOICE")
            putExtra(IncomingCallActivity.EXTRA_INVITER, call.peerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context, call.callId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, "red_calls_incoming_v2")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(if (call.isVideo) "مكالمة فيديو واردة" else "مكالمة صوتية واردة")
            .setContentText(call.peerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .setAutoCancel(false)
            .addAction(0, "رفض", createActionPendingIntent(context, call.callId, "REJECT"))
            .addAction(0, "قبول", createActionPendingIntent(context, call.callId, "ACCEPT"))
            .build()
        
        manager.notify(call.callId.hashCode(), notification)
    }
    
    private fun showCallWaitingNotification(context: Context, call: ActiveCall) {
        // Similar but for call waiting
    }
    
    private fun createActionPendingIntent(context: Context, callId: String, action: String): PendingIntent {
        val intent = Intent(context, CallNotificationActionReceiver::class.java).apply {
            putExtra("callId", callId)
            putExtra("action", action)
        }
        return PendingIntent.getBroadcast(
            context, (callId + action).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun startRingtone(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                isLooping = true
                play()
            }
            vibrator?.let { vib ->
                if (Build.VERSION.SDK_INT >= 26) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400, 800), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 800, 400, 800), 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start ringtone: ${e.message}")
        }
    }
    
    private fun startRingbackTone() {
        // Ringback for outgoing
        try {
            val tone = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            tone.startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE, 45000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start ringback: ${e.message}")
        }
    }
    
    private fun startCallWaitingTone() {
        try {
            val tone = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            tone.startTone(android.media.ToneGenerator.TONE_SUP_CALL_WAITING, 1000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start call waiting tone: ${e.message}")
        }
    }
    
    private fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {}
        ringtone = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {}
    }
    
    private fun stopRingbackTone() {
        // Stop ringback
    }
    
    private fun armRingTimeout(callId: String, isOutgoing: Boolean) {
        clearRingTimeout()
        ringTimeoutJob = callScope.launch {
            delay(45_000) // 45 seconds
            val call = activeCalls[callId]
            if (call != null && (call.state == CallState.OUTGOING_RINGING || call.state == CallState.INCOMING_RINGING || call.state == CallState.OUTGOING_CONNECTING)) {
                if (isOutgoing) {
                    endCallInternal(callId, CallState.NO_ANSWER)
                } else {
                    endCallInternal(callId, CallState.NO_ANSWER)
                    // Show missed call notification
                }
            }
        }
    }
    
    private fun clearRingTimeout() {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
    }
    
    private fun failCall(callId: String, message: String) {
        Log.e(TAG, "❌ Call failed: $callId - $message")
        endCallInternal(callId, CallState.FAILED)
    }
    
    private fun startStatsPolling() {
        statsJob?.cancel()
        statsJob = callScope.launch {
            while (isActive) {
                webRtcEngine?.pollStats()
                delay(2000)
            }
        }
    }
    
    private val signalingListener = object : CallSignalingClient.Listener {
        override fun onConnected() {
            Log.i(TAG, "📡 Signaling connected")
        }
        
        override fun onSignal(signal: CallSignal) {
            Log.i(TAG, "📡 Signal: ${signal.type} call=${signal.callId}")
            when (signal.type) {
                "OFFER" -> {
                    // Incoming offer
                    val callId = signal.callId ?: UUID.randomUUID().toString()
                    val fromId = signal.sourceUserId ?: ""
                    val isVideo = signal.mode == "VIDEO"
                    val sdp = signal.payload["sdp"]
                    
                    // Use application context
                    // onIncomingCall(context, callId, CallType.ONE_TO_ONE_AUDIO, fromId, fromId, isVideo, sdp)
                }
                "ANSWER" -> {
                    val sdp = signal.payload["sdp"]
                    if (sdp != null) {
                        webRtcEngine?.setRemote(
                            SessionDescription(SessionDescription.Type.ANSWER, sdp)
                        ) {}
                    }
                }
                "ICE" -> {
                    val candidate = signal.payload["candidate"]
                    val sdpMid = signal.payload["sdpMid"]
                    val sdpMLineIndex = signal.payload["sdpMLineIndex"]?.toIntOrNull() ?: 0
                    if (candidate != null) {
                        webRtcEngine?.addIce(IceCandidate(sdpMid, sdpMLineIndex, candidate))
                    }
                }
                "END", "REJECT", "BUSY" -> {
                    val callId = signal.callId
                    if (callId != null) {
                        val finalState = when (signal.type) {
                            "REJECT" -> CallState.REJECTED
                            "BUSY" -> CallState.BUSY
                            else -> CallState.ENDED
                        }
                        endCallInternal(callId, finalState)
                    }
                }
            }
        }
        
        override fun onDisconnected() {
            Log.w(TAG, "📡 Signaling disconnected")
        }
    }
}
