package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdpMediaOptimizerTest {
    private val sample = """
        v=0
        o=- 0 0 IN IP4 127.0.0.1
        s=-
        t=0 0
        m=audio 9 UDP/TLS/RTP/SAVPF 111 0 8
        a=rtpmap:111 opus/48000/2
        a=fmtp:111 minptime=10
        a=rtpmap:0 PCMU/8000
        m=video 9 UDP/TLS/RTP/SAVPF 96 98 100
        a=rtpmap:96 VP8/90000
        a=rtpmap:98 H264/90000
        a=rtpmap:100 VP9/90000
    """.trimIndent().replace("\n", "\r\n")

    @Test fun `voice SDP enables Opus FEC and DTX at speech bitrate`() {
        val sdp = SdpMediaOptimizer.optimize(sample, CallMediaKind.VOICE)
        assertTrue(sdp.contains("useinbandfec=1"))
        assertTrue(sdp.contains("usedtx=1"))
        assertTrue(sdp.contains("maxaveragebitrate=32000"))
        assertTrue(sdp.contains("stereo=0"))
    }

    @Test fun `live SDP allows stereo and a higher Opus rate`() {
        val sdp = SdpMediaOptimizer.optimize(sample, CallMediaKind.LIVE)
        assertTrue(sdp.contains("maxaveragebitrate=64000"))
        assertTrue(sdp.contains("stereo=1"))
    }

    @Test fun `conference prefers VP9 on the video m-line`() {
        val sdp = SdpMediaOptimizer.optimize(sample, CallMediaKind.CONFERENCE)
        assertTrue(sdp.contains("m=video 9 UDP/TLS/RTP/SAVPF 96 98").not())
        assertTrue(Regex("m=video 9 UDP/TLS/RTP/SAVPF 98 96").containsMatchIn(sdp.replace("\r", "")))
    }

    @Test fun `one to one video prefers H264`() {
        val sdp = SdpMediaOptimizer.optimize(sample, CallMediaKind.VIDEO)
        assertTrue(Regex("m=video 9 UDP/TLS/RTP/SAVPF 98").containsMatchIn(sdp.replace("\r", "")))
    }

    @Test fun `MOS is excellent on a clean LAN and poor on high loss`() {
        assertTrue(SdpMediaOptimizer.mos(40, 0.2) >= 4.0)
        assertTrue(SdpMediaOptimizer.mos(500, 12.0) < 3.2)
        assertEquals(SdpMediaOptimizer.mos(80, 1.0), SdpMediaOptimizer.mos(80, 1.0), 0.0)
    }

    @Test fun `media kind flags match product types`() {
        assertEquals(false, CallMediaKind.VOICE.wantsVideo)
        assertEquals(true, CallMediaKind.VIDEO.wantsVideo)
        assertEquals(false, CallMediaKind.SPACE.wantsVideo)
        assertEquals(true, CallMediaKind.CONFERENCE.wantsSvc)
        assertEquals(true, CallMediaKind.LIVE.stereoAudio)
    }
}
