package com.red.sovereign.features.calls

import android.content.Context
import org.webrtc.*
import com.red.sovereign.core.network.RedWebSocketClient
import org.json.JSONObject
import javax.inject.Inject

/**
 * RED Live Broadcast Manager
 * Handles 1-to-Many streaming using Mediasoup SFU.
 */
class LiveBroadcastManager @Inject constructor(
    private val voipEngine: VoipEngine,
    private val signaler: RedWebSocketClient
) {
    private var localVideoTrack: VideoTrack? = null
    private var transport: PeerConnection? = null

    /**
     * Start Broadcasting (System A Flow)
     */
    fun startBroadcasting(streamId: String) {
        val streamSignal = JSONObject().apply {
            put("type", "start_live")
            put("streamId", streamId)
        }
        signaler.send(streamSignal.toString().toByteArray())
        
        // Initialize CameraX/WebRTC Local Stream
        localVideoTrack = voipEngine.getVideoConstraints()
    }

    /**
     * Join as Viewer
     */
    fun joinStream(streamId: String) {
        val joinSignal = JSONObject().apply {
            put("type", "join_live")
            put("streamId", streamId)
        }
        signaler.send(joinSignal.toString().toByteArray())
    }

    fun sendReaction(streamId: String, type: String) {
        val reaction = JSONObject().apply {
            put("type", "live_reaction")
            put("streamId", streamId)
            put("reaction", type)
        }
        signaler.send(reaction.toString().toByteArray())
    }

    fun stop() {
        transport?.close()
        localVideoTrack?.dispose()
    }
}
