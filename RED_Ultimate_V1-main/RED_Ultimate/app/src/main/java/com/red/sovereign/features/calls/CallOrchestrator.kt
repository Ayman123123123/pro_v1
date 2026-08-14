package com.red.sovereign.features.calls

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.red.sovereign.features.calls.data.CallRepository
import com.red.sovereign.features.calls.data.CallResult
import com.red.sovereign.features.calls.sfu.SfuClient
import com.red.sovereign.features.pstn.PstnViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ─── Conference/Stream State ───────────────────────────────────────────────

data class ConferenceState(
    val roomId: String,
    val title: String,
    val isSpace: Boolean,
    val participantCount: Int = 0,
    val isHost: Boolean = false,
    val role: String = "LISTENER",   // HOST, CO_HOST, SPEAKER, LISTENER
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val inviteLink: String = ""
)

// ─── CallOrchestrator ──────────────────────────────────────────────────────

/**
 * RED Call Orchestrator — حكم بين ثلاثة أنظمة مكالمات:
 *
 * - **System A**: WebRTC P2P عبر [RedVoipMaster] (مكالمات RED↔RED)
 * - **System B**: PSTN/GSM عبر [PstnViewModel] (بوابة Dinstar)
 * - **System C**: SFU Conference/Space عبر [SfuClient] (مؤتمرات وبث)
 *
 * يختار المسار الصحيح بناءً على نوع الطلب والصلاحيات.
 */
@Singleton
class CallOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voipMaster: RedVoipMaster,
    private val pstnViewModel: PstnViewModel,
    private val callRepository: CallRepository
) {
    companion object {
        private const val TAG = "CallOrchestrator"
        private const val PREFS = "red_sovereign_identity"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private val serverHost: String get() {
        val host = prefs.getString("SERVER_HOST", "wss://red.sovereign.local") ?: "wss://red.sovereign.local"
        return host.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _conferenceState = MutableStateFlow<ConferenceState?>(null)
    val conferenceState: StateFlow<ConferenceState?> = _conferenceState

    private var activeSfuClient: SfuClient? = null

    // ── System A: WebRTC P2P ──────────────────────────────────────────

    /**
     * بدء مكالمة WebRTC مع مستخدم RED آخر.
     */
    fun initiateCall(targetRedId: String, videoEnabled: Boolean = false, isGsm: Boolean = false) {
        if (isGsm) {
            // System B: GSM عبر Dinstar
            pstnViewModel.dialPstn(targetRedId)
        } else {
            // System A: WebRTC P2P
            voipMaster.startSecureCall(targetRedId, videoEnabled)
        }
    }

    fun endActiveCall() {
        when {
            voipMaster.isCallActive() -> voipMaster.endCall()
            pstnViewModel.hasActiveCall() -> pstnViewModel.endGsmCall()
            _conferenceState.value != null -> leaveConference()
        }
    }

    // ── System C: Conference (Video) ──────────────────────────────────

    /**
     * إنشاء وانضمام إلى مؤتمر فيديو جديد.
     */
    fun startConference(
        title: String,
        roomId: String = "",
        isPrivate: Boolean = false,
        password: String? = null
    ) {
        scope.launch {
            val result = callRepository.createConference(
                title = title,
                roomId = roomId,
                isSpace = false,
                isPrivate = isPrivate,
                password = password
            )
            when (result) {
                is CallResult.Success -> {
                    val room = result.data
                    Log.i(TAG, "Conference created: ${room.roomId}")
                    _conferenceState.value = ConferenceState(
                        roomId = room.roomId,
                        title = room.title,
                        isSpace = false,
                        isHost = true,
                        inviteLink = room.inviteLink
                    )
                    connectToSfu(room.roomId, isHost = true, videoEnabled = true)
                }
                is CallResult.Error -> Log.e(TAG, "Conference create failed: ${result.message}")
            }
        }
    }

    /**
     * الانضمام إلى مؤتمر موجود.
     */
    fun joinConference(roomId: String, password: String? = null, videoEnabled: Boolean = true) {
        scope.launch {
            val result = callRepository.joinConference(roomId, password)
            when (result) {
                is CallResult.Success -> {
                    val resp = result.data
                    if (!resp.authorized) {
                        Log.w(TAG, "Conference join rejected: ${resp.errorMessage}")
                        return@launch
                    }
                    _conferenceState.value = ConferenceState(
                        roomId = resp.roomId,
                        title = resp.title,
                        isSpace = resp.isSpace,
                        isHost = false
                    )
                    connectToSfu(roomId, isHost = false, videoEnabled = videoEnabled)
                }
                is CallResult.Error -> Log.e(TAG, "Conference join failed: ${result.message}")
            }
        }
    }

    /**
     * مغادرة المؤتمر.
     */
    fun leaveConference() {
        val state = _conferenceState.value ?: return
        scope.launch {
            activeSfuClient?.leave()
            activeSfuClient?.disconnect()
            activeSfuClient = null

            if (state.isHost) {
                callRepository.closeConference(state.roomId)
            } else {
                callRepository.leaveConference(state.roomId)
            }
            _conferenceState.value = null
        }
    }

    /**
     * دعوة أعضاء إلى المؤتمر.
     */
    fun inviteToConference(roomId: String, memberIds: List<String>) {
        scope.launch {
            callRepository.inviteToConference(roomId, memberIds)
        }
    }

    // ── System C: Audio Space ─────────────────────────────────────────

    /**
     * إنشاء مساحة صوتية (مثل Clubhouse).
     */
    fun startAudioSpace(title: String, isPrivate: Boolean = false) {
        scope.launch {
            val result = callRepository.createConference(
                title = title,
                isSpace = true,
                isPrivate = isPrivate
            )
            when (result) {
                is CallResult.Success -> {
                    val room = result.data
                    _conferenceState.value = ConferenceState(
                        roomId = room.roomId,
                        title = room.title,
                        isSpace = true,
                        isHost = true,
                        role = "HOST",
                        inviteLink = room.inviteLink
                    )
                    connectToSfu(room.roomId, isHost = true, videoEnabled = false)
                }
                is CallResult.Error -> Log.e(TAG, "Audio Space create failed: ${result.message}")
            }
        }
    }

    fun joinAudioSpace(roomId: String, password: String? = null) {
        joinConference(roomId, password, videoEnabled = false)
    }

    // ── System C: Live Stream ─────────────────────────────────────────

    /**
     * بدء بث مباشر.
     */
    fun startLiveStream(title: String, streamId: String = "", isPrivate: Boolean = false) {
        scope.launch {
            val result = callRepository.createLiveStream(title, streamId, isPrivate)
            when (result) {
                is CallResult.Success -> {
                    val stream = result.data
                    Log.i(TAG, "Live stream created: ${stream.streamId}")
                    // Connect to SFU as broadcaster
                    connectToSfuAsStream(stream.streamId, isBroadcaster = true)
                }
                is CallResult.Error -> Log.e(TAG, "Live stream create failed: ${result.message}")
            }
        }
    }

    fun stopLiveStream(streamId: String) {
        scope.launch {
            activeSfuClient?.leave()
            activeSfuClient?.disconnect()
            activeSfuClient = null
            callRepository.stopLiveStream(streamId)
        }
    }

    fun inviteToLiveStream(streamId: String, friendIds: List<String>) {
        scope.launch { callRepository.inviteToLiveStream(streamId, friendIds) }
    }

    // ── Private: SFU Connection ───────────────────────────────────────

    private suspend fun connectToSfu(roomId: String, isHost: Boolean, videoEnabled: Boolean) {
        // Fetch SFU ticket from backend
        val ticketResult = callRepository.getSfuTicketForRoom(roomId)
        val token = when (ticketResult) {
            is CallResult.Success -> ticketResult.data.token
            is CallResult.Error -> {
                Log.e(TAG, "SFU ticket fetch failed: ${ticketResult.message}")
                return
            }
        }

        val sfuUrl = "$serverHost/sfu"
        val client = SfuClient(sfuUrl, token, roomId)
        activeSfuClient = client

        client.connect(object : SfuClient.SfuEventListener {
            override fun onNewProducer(peerId: String, producerId: String, kind: String) {
                Log.d(TAG, "New SFU producer: peer=$peerId, kind=$kind")
                // Subscribe to new remote producer
                scope.launch {
                    val recvTransport = client.createRecvTransport()
                    val caps = client.rtpCapabilities ?: return@launch
                    val consume = client.consume(recvTransport.transportId, producerId, caps)
                    client.resumeConsumer(consume.consumerId)
                }
            }

            override fun onProducerClosed(consumerId: String, producerId: String) {
                Log.d(TAG, "SFU producer closed: $producerId")
            }

            override fun onPeerLeft(peerId: String) {
                _conferenceState.value = _conferenceState.value?.copy(
                    participantCount = maxOf(0, (_conferenceState.value?.participantCount ?: 1) - 1)
                )
            }

            override fun onConnected() {
                scope.launch {
                    val joinResult = client.join()
                    _conferenceState.value = _conferenceState.value?.copy(
                        isConnected = true,
                        participantCount = joinResult.existingProducers.size + 1
                    )

                    // Create send transport and produce
                    val sendTransport = client.createSendTransport()
                    Log.i(TAG, "SFU joined room=$roomId, peers=${joinResult.existingProducers.size}")

                    // Subscribe to existing producers
                    for (producer in joinResult.existingProducers) {
                        val recvTransport = client.createRecvTransport()
                        val caps = joinResult.rtpCapabilitiesJson
                        val consume = client.consume(recvTransport.transportId, producer.producerId, caps)
                        client.resumeConsumer(consume.consumerId)
                    }
                }
            }

            override fun onDisconnected() {
                _conferenceState.value = _conferenceState.value?.copy(isConnected = false)
            }

            override fun onError(message: String) {
                Log.e(TAG, "SFU error: $message")
            }
        })
    }

    private suspend fun connectToSfuAsStream(streamId: String, isBroadcaster: Boolean) {
        val ticketResult = callRepository.getSfuTicketForRoom(streamId)
        val token = when (ticketResult) {
            is CallResult.Success -> ticketResult.data.token
            is CallResult.Error -> return
        }
        val sfuUrl = "$serverHost/sfu"
        val client = SfuClient(sfuUrl, token, streamId)
        activeSfuClient = client
        Log.i(TAG, "Connected to SFU for live stream: $streamId (broadcaster=$isBroadcaster)")
    }
}
