package com.red.sovereign.calls

import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import kotlin.math.max

object AdaptiveCallQuality {
    private const val MIN_BITRATE_BPS = 50_000
    private const val MAX_BITRATE_BPS = 2_500_000
    private const val IDEAL_VIDEO_BITRATE = 1_200_000

    /**
     * Hybrid Adaptive Bitrate (ABR) Logic
     * Adjusts the bitrate of an RtpSender based on current network stats.
     */
    fun adjustQuality(sender: RtpSender, stats: NetworkStats) {
        val params: RtpParameters = sender.parameters ?: return
        if (params.encodings.isEmpty()) return

        val encoding = params.encodings[0]
        var targetBitrate = encoding.maxBitrateBps ?: IDEAL_VIDEO_BITRATE

        if (stats.packetLossPercent > 5.0 || stats.rttMs > 200) {
            // Poor network: Drop bitrate aggressively
            targetBitrate = max(MIN_BITRATE_BPS, (targetBitrate * 0.7).toInt())
        } else if (stats.packetLossPercent < 1.0 && stats.rttMs < 100) {
            // Good network: Increase bitrate carefully
            if (targetBitrate < MAX_BITRATE_BPS) {
                targetBitrate = (targetBitrate * 1.1).toInt().coerceAtMost(MAX_BITRATE_BPS)
            }
        }

        encoding.maxBitrateBps = targetBitrate
        
        // Also adjust resolution / frame rate limits if the network is really bad
        if (stats.packetLossPercent > 10.0) {
            encoding.maxFramerate = 15
            encoding.scaleResolutionDownBy = 2.0
        } else {
            encoding.maxFramerate = 30
            encoding.scaleResolutionDownBy = 1.0
        }

        sender.parameters = params
    }
}
