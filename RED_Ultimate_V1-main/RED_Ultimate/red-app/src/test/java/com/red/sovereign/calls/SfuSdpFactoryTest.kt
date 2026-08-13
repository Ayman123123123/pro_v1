package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SfuSdpFactoryTest {
    private val ice = SfuIceParameters("ufrag1", "passwordpasswordpassword", iceLite = true)
    private val candidates = listOf(SfuIceCandidate(foundation = "f1", priority = 1000, ip = "203.0.113.10", port = 40000, type = "host"))
    private val dtls = SfuDtlsParameters("auto", listOf(SfuDtlsFingerprint("sha-256", "AA:BB:CC")))

    @Test fun `remote offer is ICE-lite Unified Plan with BUNDLE`() {
        val sdp = SfuSdpFactory.remoteOffer(ice, candidates, dtls, listOf(SfuMediaKind("audio", "recvonly")))
        assertTrue(sdp.contains("a=ice-lite"))
        assertTrue(sdp.contains("a=group:BUNDLE 0"))
        assertTrue(sdp.contains("a=ice-ufrag:ufrag1"))
        assertTrue(sdp.contains("a=candidate:f1 1 UDP 1000 203.0.113.10 40000 typ host"))
        assertTrue(sdp.contains("a=fingerprint:sha-256 AA:BB:CC"))
        assertTrue(sdp.contains("a=rtpmap:111 opus/48000/2"))
        assertTrue(sdp.contains("useinbandfec=1"))
    }

    @Test fun `conference offer adds a video section for VP9`() {
        val sdp = SfuSdpFactory.remoteOffer(
            ice, candidates, dtls,
            listOf(SfuMediaKind("audio", "recvonly"), SfuMediaKind("video", "recvonly"))
        )
        assertTrue(sdp.contains("m=video 7 UDP/TLS/RTP/SAVPF 96"))
        assertTrue(sdp.contains("a=rtpmap:96 VP9/90000"))
        assertTrue(sdp.contains("a=rtcp-fb:96 nack pli"))
        assertTrue(sdp.contains("a=group:BUNDLE 0 1"))
    }

    @Test fun `extracts DTLS client role from a local answer`() {
        val local = "v=0\r\na=setup:active\r\na=fingerprint:sha-256 DE:AD:BE:EF\r\n"
        val params = SfuSdpFactory.dtlsFromLocalSdp(local)!!
        assertEquals("client", params.role)
        assertEquals("sha-256", params.fingerprints.first().algorithm)
        assertEquals("DE:AD:BE:EF", params.fingerprints.first().value)
    }

    @Test fun `builds produce parameters from a local audio SDP`() {
        val local = """
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            a=rtpmap:111 opus/48000/2
            a=ssrc:4242 cname:younes-host
        """.trimIndent().replace("\n", "\r\n")
        val rtp = SfuSdpFactory.rtpParametersFromLocal(local, "audio")!!
        assertEquals("audio/opus", rtp.codecs.first().mimeType)
        assertEquals(111, rtp.codecs.first().payloadType)
        assertEquals(4242L, rtp.encodings.first().ssrc)
        assertEquals("younes-host", rtp.rtcp.cname)
    }

    @Test fun `consumer offer uses the producer payload type and SSRC`() {
        val rtp = SfuRtpParameters(
            codecs = listOf(SfuRtpCodec("audio/opus", 111, 48000, 2)),
            encodings = listOf(SfuRtpEncoding(ssrc = 99)),
            rtcp = SfuRtcpParameters("peer")
        )
        val sdp = SfuSdpFactory.consumerOffer(ice, candidates, dtls, "audio", rtp)
        assertTrue(sdp.contains("a=ssrc:99 cname:peer"))
        assertTrue(sdp.contains("a=sendonly"))
    }
}
