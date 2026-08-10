package com.red.sovereign.features.calls

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * RED VoIP Engine
 * Configures WebRTC for 4K Video (AV1/VP9) and Hi-Fi Audio (Opus).
 */
class VoipEngine(private val context: Context) {

    private val rootEglBase: EglBase = EglBase.create()
    
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
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:192.168.1.50:3478").createIceServer(), // Local RED STUN
            PeerConnection.IceServer.builder("turn:192.168.1.50:3478")
                .setUsername("red_user").setPassword("red_pass").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        return factory.createPeerConnection(rtcConfig, observer)
    }

    fun getEglContext() = rootEglBase.eglBaseContext
}
