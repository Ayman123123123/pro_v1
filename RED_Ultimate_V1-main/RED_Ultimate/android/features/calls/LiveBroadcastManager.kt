package com.red.sovereign.features.calls

import android.util.Log
import org.webrtc.*
import com.red.sovereign.core.network.RedWebSocketClient
import org.json.JSONObject
import javax.inject.Inject

class LiveBroadcastManager @Inject constructor(
    private val voipEngine: VoipEngine,
    private val signaler: RedWebSocketClient
) {
    companion object { private const val TAG = "RED.LiveBroadcast" }

    private var localVideoTrack: VideoTrack? = null
    private var transport: PeerConnection? = null

    fun startBroadcasting(streamId: String) {
        val streamSignal = JSONObject().apply {
            put("type", "start_live")
            put("streamId", streamId)
        }
        signaler.send(streamSignal.toString().toByteArray())

        localVideoTrack = voipEngine.getLocalVideoTrack()

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate for broadcast: ${candidate.sdp}")
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.i(TAG, "Broadcast transport state: $state")
            }
            override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(b: Boolean) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(a: Array<out IceCandidate>?) {}
            override fun onAddStream(s: MediaStream?) {}
            override fun onRemoveStream(s: MediaStream?) {}
            override fun onDataChannel(d: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver?, a: Array<out MediaStream>?) {}
            override fun onTrack(transceiver: RtpTransceiver) {}
        }

        transport = voipEngine.createPeerConnection(observer)

        localVideoTrack?.let { track ->
            val sender = transport?.addTrack(track)
            if (sender == null) {
                Log.e(TAG, "Failed to add video track to broadcast transport")
            }
        }
    }

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
        transport = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        Log.i(TAG, "Broadcast stopped and resources released")
    }
}
