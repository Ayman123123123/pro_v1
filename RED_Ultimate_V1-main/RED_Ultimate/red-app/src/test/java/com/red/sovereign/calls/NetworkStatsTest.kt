package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkStatsTest {
    @Test fun `default stats are UNKNOWN`() {
        val stats = NetworkStats()
        assertEquals(NetworkStats.Quality.UNKNOWN, stats.quality)
        assertEquals(0L, stats.rttMs)
        assertEquals(0.0, stats.packetLossPercent, 0.0)
    }

    @Test fun `quality classification based on RTT and loss`() {
        // Excellent: low RTT and no loss
        assertEquals(NetworkStats.Quality.EXCELLENT, classify(rtt = 50, lossPct = 0.5))
        // Good
        assertEquals(NetworkStats.Quality.GOOD, classify(rtt = 150, lossPct = 3.0))
        // Fair
        assertEquals(NetworkStats.Quality.FAIR, classify(rtt = 250, lossPct = 6.0))
        // Poor
        assertEquals(NetworkStats.Quality.POOR, classify(rtt = 500, lossPct = 12.0))
        // Unknown when no data
        assertEquals(NetworkStats.Quality.UNKNOWN, classify(rtt = 0, lossPct = 0.0))
    }

    private fun classify(rtt: Long, lossPct: Double): NetworkStats.Quality = when {
        rtt == 0L -> NetworkStats.Quality.UNKNOWN
        rtt > 400 || lossPct > 10 -> NetworkStats.Quality.POOR
        rtt > 200 || lossPct > 5 -> NetworkStats.Quality.FAIR
        rtt > 100 || lossPct > 2 -> NetworkStats.Quality.GOOD
        else -> NetworkStats.Quality.EXCELLENT
    }
}
