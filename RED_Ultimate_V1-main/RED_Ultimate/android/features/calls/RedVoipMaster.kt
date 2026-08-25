package com.red.sovereign.features.calls

import android.util.Log
import org.webrtc.*
import com.red.sovereign.core.network.MediasoupClient
import javax.inject.Inject

class RedVoipMaster @Inject constructor(
    private val voipEngine: VoipEngine,
    private val mediasoupClient: MediasoupClient
) {
    companion object { private const val TAG = "RED.VoipMaster" }

    private var pc: PeerConnection? = null

    fun startSecureCall(targetId: String) {
        pc?.close()
        pc = null

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                mediasoupClient.sendIce(candidate)
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                Log.i(TAG, "onTrack received")
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                Log.i(TAG, "PeerConnection state: $state")
                if (state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                    state == PeerConnection.PeerConnectionState.FAILED) {
                    endCall()
                }
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
        }

        pc = voipEngine.createPeerConnection(observer)

        pc?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) {
                    Log.e(TAG, "createOffer returned null SDP")
                    endCall()
                    return
                }
                pc?.setLocalDescription(this, desc)
                mediasoupClient.sendOffer(desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(s: String?) {
                Log.e(TAG, "SDP createOffer failed: $s")
                endCall()
            }
            override fun onSetFailure(s: String?) {
                Log.e(TAG, "SDP setLocalDescription failed: $s")
                endCall()
            }
        }, MediaConstraints())
    }

    fun endCall() {
        pc?.close()
        pc = null
        Log.i(TAG, "Call ended, PeerConnection released")
    }
}
