package com.red.sovereign.calls

import android.util.Log
import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import kotlin.math.max

object AdaptiveCallQuality {
    private const val TAG = "AdaptiveCallQuality"
    private const val MIN_BITRATE_BPS = 50_000
    private const val MAX_BITRATE_BPS = 2_500_000
    private const val IDEAL_VIDEO_BITRATE = 1_200_000

    /**
     * Hybrid Adaptive Bitrate (ABR) Logic
     * Adjusts the bitrate, resolution scaling, and framerate of an RtpSender based on current network stats.
     */
    fun adjustQuality(sender: RtpSender, stats: NetworkStats) {
        runCatching {
            val params: RtpParameters = sender.parameters ?: return
            if (params.encodings.isEmpty()) return

            // Adapt all encodings (e.g. simulcast layers) or primary encoding
            for (encoding in params.encodings) {
                var targetBitrate = encoding.maxBitrateBps ?: IDEAL_VIDEO_BITRATE

                val highLoss = stats.packetLossPercent > 5.0
                val highRtt = stats.rttMs > 200
                val highJitter = stats.jitterMs > 30
                val excellentNetwork = stats.packetLossPercent < 1.0 && stats.rttMs < 100 && stats.jitterMs < 15

                if (highLoss || highRtt || highJitter) {
                    // Poor network: Drop bitrate aggressively
                    targetBitrate = max(MIN_BITRATE_BPS, (targetBitrate * 0.7).toInt())
                } else if (excellentNetwork) {
                    // Good network: Increase bitrate carefully
                    if (targetBitrate < MAX_BITRATE_BPS) {
                        targetBitrate = (targetBitrate * 1.1).toInt().coerceAtMost(MAX_BITRATE_BPS)
                    }
                }

                encoding.maxBitrateBps = targetBitrate

                // Adjust resolution scaling and frame rate limits based on packet loss and RTT severity
                when {
                    stats.packetLossPercent > 10.0 || stats.rttMs > 400 -> {
                        encoding.maxFramerate = 15
                        encoding.scaleResolutionDownBy = 2.0
                    }
                    stats.packetLossPercent > 5.0 || stats.rttMs > 250 -> {
                        encoding.maxFramerate = 20
                        encoding.scaleResolutionDownBy = 1.5
                    }
                    else -> {
                        encoding.maxFramerate = 30
                        encoding.scaleResolutionDownBy = 1.0
                    }
                }
            }

            sender.parameters = params
        }.onFailure { e ->
            Log.e(TAG, "Failed to adjust RtpSender quality parameters", e)
        }
    }
}
