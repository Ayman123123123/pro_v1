package com.red.sovereign.features.calls

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class VoipEngine(private val context: Context) {

    companion object {
        private const val TAG = "RED.VoipEngine"
        private const val FALLBACK_STUN = "stun:stun.l.google.com:19302"
    }

    private val rootEglBase: EglBase = EglBase.create()

    @Volatile
    private var _factory: PeerConnectionFactory? = null

    var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder(FALLBACK_STUN).createIceServer()
    )

    val factory: PeerConnectionFactory
        get() = _factory ?: synchronized(this) {
            _factory ?: createFactory().also { _factory = it }
        }

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setSampleRate(48000)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()

        return PeerConnectionFactory.builder()
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

    fun getLocalVideoTrack(): VideoTrack? {
        return try {
            val videoCapturer = Camera2Enumerator(context).let { enumerator ->
                enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
                    ?.let { enumerator.createCapturer(it, null) }
                    ?: enumerator.deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }
            } ?: return null

            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext)
            val videoSource = factory.createVideoSource(videoCapturer.isScreencast)
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            videoCapturer.startCapture(1920, 1080, 30)

            val videoTrack = factory.createVideoTrack("ARDARD0", videoSource)
            videoTrack.setEnabled(true)
            videoTrack
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create local video track", e)
            null
        }
    }

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

    fun dispose() {
        try {
            _factory?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing factory", e)
        }
        _factory = null
        try {
            rootEglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing EglBase", e)
        }
        Log.i(TAG, "VoipEngine disposed")
    }
}
