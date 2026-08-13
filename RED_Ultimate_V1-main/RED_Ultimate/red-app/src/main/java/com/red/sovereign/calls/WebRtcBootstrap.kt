package com.red.sovereign.calls

import android.content.Context
import org.webrtc.PeerConnectionFactory

/**
 * PeerConnectionFactory.initialize() must run once per process.
 * 1-1 calls, mesh conferences, and live fan-out all share this gate.
 */
object WebRtcBootstrap {
    @Volatile private var ready = false
    private val lock = Any()

    fun ensure(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .setFieldTrials(
                        "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/" +
                            "WebRTC-FlexFEC-03/Enabled/" +
                            "WebRTC-Bwe-ProbingConfiguration/Enabled/" +
                            "WebRTC-Audio-NetEqAutoReset/Enabled/"
                    )
                    .createInitializationOptions()
            )
            ready = true
        }
    }
}
