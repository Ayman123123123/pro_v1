package com.red.sovereign.features.calls

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * YOUNES VoIP Engine
 * Configures WebRTC for 1080p Video (AV1/VP9) and Hi-Fi Audio (Opus).
 * ICE servers are fetched dynamically from the backend (/api/calls/ice-servers)
 * and should NOT be hardcoded.
 */
class VoipEngine(private val context: Context) {

    companion object {
        private const val TAG = "RED.VoipEngine"
        // Default fallback STUN — production must use backend-provided ICE
        private const val FALLBACK_STUN = "stun:stun.l.google.com:19302"
    }

    private val rootEglBase: EglBase = EglBase.create()

    var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder(FALLBACK_STUN).createIceServer()
    )

    val factory: PeerConnectionFactory by lazy {
        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setSampleRate(48000)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        return factory.createPeerConnection(rtcConfig, observer)
    }

    fun getEglContext() = rootEglBase.eglBaseContext

    /**
     * Update ICE servers from backend response.
     * Call this after fetching /api/calls/ice-servers.
     */
    fun updateIceServers(servers: List<Pair<String, Triple<String?, String?, String?>>>) {
        iceServers = servers.map { (url, creds) ->
            val builder = PeerConnection.IceServer.builder(url)
            creds.first?.let { builder.setUsername(it) }
            creds.second?.let { builder.setPassword(it) }
            builder.createIceServer()
        }.ifEmpty {
            listOf(PeerConnection.IceServer.builder(FALLBACK_STUN).createIceServer())
        }
    }
}
