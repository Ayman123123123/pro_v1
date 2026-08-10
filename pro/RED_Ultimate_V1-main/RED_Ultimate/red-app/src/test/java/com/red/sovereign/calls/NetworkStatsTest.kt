package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatsTest {
    @Test fun `default stats are UNKNOWN`() {
        val stats = NetworkStats()
        assertEquals(NetworkStats.Quality.UNKNOWN, stats.quality)
        assertEquals(0L, stats.rttMs)
        assertEquals(0.0, stats.packetLossPercent, 0.0)
        assertEquals(0L, stats.bandwidthKbps)
    }

    @Test fun `quality classification based on RTT and loss`() {
        assertEquals(NetworkStats.Quality.EXCELLENT, NetworkStats.classify(50, 0.5))
        assertEquals(NetworkStats.Quality.GOOD, NetworkStats.classify(150, 3.0))
        assertEquals(NetworkStats.Quality.FAIR, NetworkStats.classify(250, 6.0))
        assertEquals(NetworkStats.Quality.POOR, NetworkStats.classify(500, 12.0))
        assertEquals(NetworkStats.Quality.UNKNOWN, NetworkStats.classify(0, 0.0))
    }

    @Test fun `quality considers available bitrate`() {
        // No RTT/loss data but high available bitrate = UNKNOWN (not enough info)
        assertEquals(NetworkStats.Quality.UNKNOWN, NetworkStats.classify(0, 0.0, 5000))
        // But high RTT with high bandwidth = still POOR
        assertEquals(NetworkStats.Quality.POOR, NetworkStats.classify(500, 0.0, 5000))
    }

    @Test fun `BitrateProfile recommendation matches quality`() {
        assertEquals(NetworkStats.BitrateProfile.STANDARD, NetworkStats.recommendBitrate(NetworkStats.Quality.UNKNOWN))
        assertEquals(NetworkStats.BitrateProfile.AUDIO_ONLY, NetworkStats.recommendBitrate(NetworkStats.Quality.POOR))
        assertEquals(NetworkStats.BitrateProfile.LOW, NetworkStats.recommendBitrate(NetworkStats.Quality.FAIR))
        assertEquals(NetworkStats.BitrateProfile.STANDARD, NetworkStats.recommendBitrate(NetworkStats.Quality.GOOD))
        assertEquals(NetworkStats.BitrateProfile.HD, NetworkStats.recommendBitrate(NetworkStats.Quality.EXCELLENT))
    }

    @Test fun `BitrateProfile HD has higher bitrate than LOW`() {
        assertTrue(NetworkStats.BitrateProfile.HD.videoMaxBitrateKbps > NetworkStats.BitrateProfile.LOW.videoMaxBitrateKbps)
        assertTrue(NetworkStats.BitrateProfile.HD.videoFramerate > NetworkStats.BitrateProfile.LOW.videoFramerate)
        assertTrue(NetworkStats.BitrateProfile.HD.videoWidth > NetworkStats.BitrateProfile.LOW.videoWidth)
    }

    @Test fun `BitrateProfile AUDIO_ONLY disables video`() {
        val profile = NetworkStats.BitrateProfile.AUDIO_ONLY
        assertEquals(0, profile.videoMaxBitrateKbps)
        assertEquals(0, profile.videoFramerate)
        assertEquals(0, profile.videoWidth)
        assertEquals(0, profile.videoHeight)
    }

    @Test fun `stats with custom values are preserved`() {
        val stats = NetworkStats(
            rttMs = 150L,
            packetLossPercent = 2.5,
            bandwidthKbps = 1500L,
            availableBitrateKbps = 2000L,
            jitterMs = 30L,
            framesPerSecond = 24,
            quality = NetworkStats.Quality.GOOD
        )
        assertEquals(150L, stats.rttMs)
        assertEquals(2.5, stats.packetLossPercent, 0.0)
        assertEquals(1500L, stats.bandwidthKbps)
        assertEquals(2000L, stats.availableBitrateKbps)
        assertEquals(30L, stats.jitterMs)
        assertEquals(24, stats.framesPerSecond)
    }
}
