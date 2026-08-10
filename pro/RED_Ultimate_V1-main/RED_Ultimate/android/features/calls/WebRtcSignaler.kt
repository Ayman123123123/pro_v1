package com.red.sovereign.features.calls

import com.red.sovereign.core.network.RedWebSocketClient
import com.red.sovereign.proto.CallProtos
import org.webrtc.SessionDescription

/**
 * RED WebRTC Signaler
 * Routes SDP and ICE candidates via Binary ProtoBuf to Spring Boot.
 */
class WebRtcSignaler(private val webSocketClient: RedWebSocketClient) {

    fun sendOffer(targetUserId: String, sdp: SessionDescription) {
        val signal = CallProtos.CallSignal.newBuilder()
            .setTargetUserId(targetUserId)
            .setType(CallProtos.SignalType.OFFER)
            .setSdp(sdp.description)
            .build()
        webSocketClient.send(signal.toByteArray())
    }

    fun sendAnswer(targetUserId: String, sdp: SessionDescription) {
        val signal = CallProtos.CallSignal.newBuilder()
            .setTargetUserId(targetUserId)
            .setType(CallProtos.SignalType.ANSWER)
            .setSdp(sdp.description)
            .build()
        webSocketClient.send(signal.toByteArray())
    }

    fun sendIceCandidate(targetUserId: String, candidate: String, sdpMid: String, sdpIndex: Int) {
        val signal = CallProtos.CallSignal.newBuilder()
            .setTargetUserId(targetUserId)
            .setType(CallProtos.SignalType.ICE_CANDIDATE)
            .setCandidate(candidate)
            .setSdpMid(sdpMid)
            .setSdpMLineIndex(sdpIndex)
            .build()
        webSocketClient.send(signal.toByteArray())
    }
}
