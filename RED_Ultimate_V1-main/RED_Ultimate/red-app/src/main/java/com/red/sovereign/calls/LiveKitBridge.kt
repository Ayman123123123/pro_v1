package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.TokenStore
import org.webrtc.VideoTrack

/**
 * SFU Abstraction Bridge for Mediasoup and LiveKit (P1-F).
 *
 * - [SfuProvider] unified interface for room join/leave/publish/subscribe & layer control.
 * - [MediasoupSfuProvider] production implementation wrapping [SfuMediaClient].
 * - [LiveKitSfuProvider] state machine & bridge prepared for LiveKit Android SDK.
 *
 * Usage:
 * ```
 * val provider = SfuProviderFactory.create(context, tokens, events)
 * if (provider.join(roomId)) provider.publish(CallMediaKind.CONFERENCE)
 * ```
 */
interface SfuProvider {
    /** Join SFU room (fetches ticket/token and initializes transports). */
    suspend fun join(roomId: String): Boolean

    /** Leave room and release media/transport resources. */
    suspend fun leave()

    /** Publish local audio/video media based on [CallMediaKind]. */
    suspend fun publish(kind: CallMediaKind): Boolean

    /** Subscribe to remote peer's published tracks. */
    suspend fun subscribe(peerId: String): Boolean

    fun setMicrophoneEnabled(enabled: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    fun isAttached(): Boolean
    fun restartIce()

    /** Simulcast layer selection for dynamic quality adaptation. */
    fun setConsumerPreferredLayers(consumerId: String, spatialLayer: Int, temporalLayer: Int) {}

    /** Request keyframe from SFU producer for video stream recovery or quality transition. */
    fun requestKeyFrame(consumerId: String) {}

    /** Publish secondary screen share video track. */
    suspend fun publishScreenTrack(track: VideoTrack): Boolean = false
}

/** Mediasoup production provider wrapping [SfuMediaClient]. */
class MediasoupSfuProvider(
    context: Context,
    tokens: TokenStore,
    events: SfuMediaClient.Events
) : SfuProvider {
    private var client: SfuMediaClient? = SfuMediaClient(context, tokens, events)

    override suspend fun join(roomId: String): Boolean =
        client?.attach(roomId) == true

    override suspend fun leave() {
        runCatching { client?.release() }
        client = null
    }

    override suspend fun publish(kind: CallMediaKind): Boolean =
        client?.publish(kind) == true

    override suspend fun subscribe(peerId: String): Boolean {
        if (peerId.isBlank()) return false
        return client?.isSfuAttached() == true
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        client?.setMicrophoneEnabled(enabled)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        client?.setCameraEnabled(enabled)
    }

    override fun isAttached(): Boolean = client?.isSfuAttached() == true

    override fun restartIce() {
        client?.restartSfuIce()
    }

    override fun setConsumerPreferredLayers(consumerId: String, spatialLayer: Int, temporalLayer: Int) {
        client?.setConsumerPreferredLayers(consumerId, spatialLayer, temporalLayer)
    }

    override fun requestKeyFrame(consumerId: String) {
        client?.requestKeyFrame(consumerId)
    }

    override suspend fun publishScreenTrack(track: VideoTrack): Boolean {
        return client?.publishScreenTrack(track) == true
    }
}

enum class LiveKitRoomState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

data class LiveKitRoomToken(
    val token: String,
    val wsUrl: String,
    val roomName: String
)

/**
 * LiveKit SFU Provider — State management & Bridge for LiveKit Android SDK integration.
 *
 * TODO LiveKit SDK Integration Steps:
 * 1. Add `implementation("io.livekit:livekit-android:<version>")` in red-app/build.gradle.kts.
 * 2. Connect via `LiveKit.connect(context, url, token, options, listener)`.
 * 3. Publish via `room.localParticipant.publishAudioTrack()` & `publishVideoTrack()`.
 * 4. Flip [SfuProviderFactory.USE_LIVEKIT] to true behind FeatureFlag.
 */
class LiveKitSfuProvider : SfuProvider {
    @Volatile
    var roomState: LiveKitRoomState = LiveKitRoomState.DISCONNECTED
        private set

    private var activeRoomId: String? = null

    override suspend fun join(roomId: String): Boolean {
        activeRoomId = roomId
        roomState = LiveKitRoomState.CONNECTING
        android.util.Log.d("LiveKitSfuProvider", "Connecting to LiveKit room $roomId (SDK pending)")
        // TODO: Replace with LiveKit.connect(url, token) when io.livekit:livekit-android is added.
        return false
    }

    override suspend fun leave() {
        roomState = LiveKitRoomState.DISCONNECTED
        activeRoomId = null
        android.util.Log.d("LiveKitSfuProvider", "Disconnected from LiveKit room")
    }

    override suspend fun publish(kind: CallMediaKind): Boolean {
        if (roomState != LiveKitRoomState.CONNECTED) return false
        // TODO: room.localParticipant.publishAudioTrack/publishVideoTrack
        return false
    }

    override suspend fun subscribe(peerId: String): Boolean {
        if (peerId.isBlank() || roomState != LiveKitRoomState.CONNECTED) return false
        // LiveKit auto-subscribes by default or via ParticipantTrackPermission
        return true
    }

    override fun setMicrophoneEnabled(enabled: Boolean) {
        // TODO: room.localParticipant.setMicrophoneEnabled(enabled)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        // TODO: room.localParticipant.setCameraEnabled(enabled)
    }

    override fun isAttached(): Boolean = roomState == LiveKitRoomState.CONNECTED

    override fun restartIce() {
        // TODO: room.reconnect()
    }
}

/** Provider Factory — Default is production Mediasoup, switchable to LiveKit via flag. */
object SfuProviderFactory {
    /** Feature flag for LiveKit engine transition. */
    const val USE_LIVEKIT = false

    fun create(
        context: Context,
        tokens: TokenStore,
        events: SfuMediaClient.Events
    ): SfuProvider = if (USE_LIVEKIT) LiveKitSfuProvider()
    else MediasoupSfuProvider(context, tokens, events)

    /** Active provider name for diagnostics & telemetry. */
    fun activeName(): String = if (USE_LIVEKIT) "livekit(pending-sdk)" else "mediasoup"
}

/** Alias for callers transitioning to provider pattern. */
object LiveKitBridge {
    fun create(
        context: Context,
        tokens: TokenStore,
        events: SfuMediaClient.Events
    ): SfuProvider = SfuProviderFactory.create(context, tokens, events)
}
