package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يضمن أن BitrateProfile enum يحتوي على القيم المتوقعة والمتسقة
 * للمزامنة بين الـ sender والـ receiver.
 */
class BitrateProfileTest {
    @Test fun `HD profile has highest bitrate`() {
        val profiles = NetworkStats.BitrateProfile.values()
        val hd = profiles.first { it.videoMaxBitrateKbps == 1800 }
        assertEquals(NetworkStats.BitrateProfile.HD, hd)
        assertEquals(30, hd.videoFramerate)
        assertEquals(1280, hd.videoWidth)
        assertEquals(720, hd.videoHeight)
    }

    @Test fun `LOW profile is 240p @ 15fps`() {
        val low = NetworkStats.BitrateProfile.LOW
        assertEquals(200, low.videoMaxBitrateKbps)
        assertEquals(15, low.videoFramerate)
        assertEquals(320, low.videoWidth)
        assertEquals(240, low.videoHeight)
    }

    @Test fun `STANDARD is 480p @ 24fps`() {
        val std = NetworkStats.BitrateProfile.STANDARD
        assertEquals(800, std.videoMaxBitrateKbps)
        assertEquals(24, std.videoFramerate)
        assertEquals(640, std.videoWidth)
        assertEquals(480, std.videoHeight)
    }

    @Test fun `AUDIO_ONLY disables video entirely`() {
        val audio = NetworkStats.BitrateProfile.AUDIO_ONLY
        assertEquals(0, audio.videoMaxBitrateKbps)
        assertEquals(0, audio.videoWidth)
        assertEquals(0, audio.videoHeight)
        assertEquals(0, audio.videoFramerate)
    }

    @Test fun `recommendBitrate matches quality progression`() {
        // POOR → AUDIO_ONLY (0 kbps video)
        // FAIR → LOW (200 kbps)
        // UNKNOWN/GOOD → STANDARD (800 kbps)
        // EXCELLENT → HD (1800 kbps)
        val progression = listOf(
            NetworkStats.Quality.POOR to 0,
            NetworkStats.Quality.FAIR to 200,
            NetworkStats.Quality.GOOD to 800,
            NetworkStats.Quality.EXCELLENT to 1800,
            NetworkStats.Quality.UNKNOWN to 800
        )
        progression.forEach { (quality, expectedBitrate) ->
            val profile = NetworkStats.recommendBitrate(quality)
            assertEquals("Quality $quality should give bitrate $expectedBitrate but was ${profile.videoMaxBitrateKbps}", expectedBitrate, profile.videoMaxBitrateKbps)
        }
    }

    @Test fun `MOS driven classify stays excellent on a clean path`() {
        assertEquals(NetworkStats.Quality.EXCELLENT, NetworkStats.classify(40, 0.2, 2_000))
        assertEquals(NetworkStats.Quality.POOR, NetworkStats.classify(600, 15.0, 100))
    }

    @Test fun `all profiles have valid scaleDown resolution`() {
        NetworkStats.BitrateProfile.values().forEach { profile ->
            if (profile != NetworkStats.BitrateProfile.AUDIO_ONLY) {
                assertTrue("Width must be > 0 for $profile", profile.videoWidth > 0)
                assertTrue("Height must be > 0 for $profile", profile.videoHeight > 0)
                assertTrue("Framerate must be > 0 for $profile", profile.videoFramerate > 0)
                assertTrue("Bitrate must be > 0 for $profile", profile.videoMaxBitrateKbps > 0)
            }
        }
    }
}
