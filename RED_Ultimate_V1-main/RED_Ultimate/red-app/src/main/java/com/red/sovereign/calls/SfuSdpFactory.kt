package com.red.sovereign.calls

/**
 * SDP bridge for mediasoup (RFC 8829 Unified Plan + ICE-lite).
 *
 * The SFU owns ICE (ice-lite). The client:
 * 1. Builds a remote offer from the transport's ice/dtls parameters.
 * 2. Answers with its own DTLS fingerprint.
 * 3. Extracts RTP parameters (payload type, SSRC, cname) to call produce/consume.
 */
object SfuSdpFactory {
    fun remoteOffer(
        ice: SfuIceParameters,
        candidates: List<SfuIceCandidate>,
        dtls: SfuDtlsParameters,
        sections: List<SfuMediaKind>
    ): String {
        val fingerprint = dtls.fingerprints.firstOrNull()
            ?: SfuDtlsFingerprint("sha-256", "00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00")
        val lines = mutableListOf(
            "v=0",
            "o=- 1000 1 IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "a=ice-lite",
            "a=msid-semantic: WMS *",
            "a=group:BUNDLE ${sections.indices.joinToString(" ")}"
        )
        sections.forEachIndexed { index, section ->
            lines += mediaSection(index, section, ice, candidates, fingerprint)
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    fun dtlsFromLocalSdp(sdp: String): SfuDtlsParameters? {
        val match = Regex("a=fingerprint:([\\w-]+) ([0-9A-Fa-f:]+)").find(sdp) ?: return null
        val role = when {
            sdp.contains("a=setup:active") -> "client"
            sdp.contains("a=setup:passive") -> "server"
            else -> "client"
        }
        return SfuDtlsParameters(
            role = role,
            fingerprints = listOf(SfuDtlsFingerprint(match.groupValues[1], match.groupValues[2]))
        )
    }

    fun iceFromLocalSdp(sdp: String): SfuIceParameters? {
        val ufrag = Regex("a=ice-ufrag:([^\\r\\n]+)").find(sdp)?.groupValues?.get(1) ?: return null
        val pwd = Regex("a=ice-pwd:([^\\r\\n]+)").find(sdp)?.groupValues?.get(1) ?: return null
        return SfuIceParameters(ufrag, pwd, iceLite = false)
    }

    fun firstSsrc(sdp: String): Long? =
        Regex("a=ssrc:(\\d+) ").find(sdp)?.groupValues?.get(1)?.toLongOrNull()

    fun cname(sdp: String): String =
        Regex("a=ssrc:\\d+ cname:([^\\r\\n]+)").find(sdp)?.groupValues?.get(1).orEmpty()

    fun payloadType(sdp: String, codec: String): Int? =
        Regex("a=rtpmap:(\\d+) ${Regex.escape(codec)}/", RegexOption.IGNORE_CASE)
            .find(sdp)?.groupValues?.get(1)?.toIntOrNull()

    fun rtpParametersFromLocal(sdp: String, kind: String): SfuRtpParameters? {
        val codec = if (kind == "audio") "opus" else preferredVideoCodec(sdp)
        val pt = payloadType(sdp, codec) ?: return null
        val ssrc = firstSsrc(sdp)
        val clock = if (kind == "audio") 48000 else 90000
        val mime = if (kind == "audio") "audio/opus" else "video/$codec"
        return SfuRtpParameters(
            codecs = listOf(
                SfuRtpCodec(
                    mimeType = mime,
                    payloadType = pt,
                    clockRate = clock,
                    channels = if (kind == "audio") 2 else null,
                    rtcpFeedback = if (kind == "audio") emptyList() else listOf(
                        SfuRtcpFeedback("nack"),
                        SfuRtcpFeedback("nack", "pli"),
                        SfuRtcpFeedback("ccm", "fir"),
                        SfuRtcpFeedback("goog-remb"),
                        SfuRtcpFeedback("transport-cc")
                    )
                )
            ),
            encodings = listOf(SfuRtpEncoding(ssrc = ssrc)),
            rtcp = SfuRtcpParameters(cname = cname(sdp).ifBlank { "younes" }, reducedSize = true)
        )
    }

    fun consumerOffer(
        ice: SfuIceParameters,
        candidates: List<SfuIceCandidate>,
        dtls: SfuDtlsParameters,
        kind: String,
        rtp: SfuRtpParameters
    ): String {
        val section = SfuMediaKind(
            kind = kind,
            direction = "sendonly",
            payloadTypes = rtp.codecs.map { it.payloadType },
            codecs = rtp.codecs,
            ssrc = rtp.encodings.firstOrNull()?.ssrc,
            cname = rtp.rtcp.cname
        )
        return remoteOffer(ice, candidates, dtls, listOf(section))
    }

    private fun preferredVideoCodec(sdp: String): String {
        val order = listOf("VP9", "H264", "VP8", "AV1")
        return order.firstOrNull { payloadType(sdp, it) != null } ?: "VP8"
    }

    private fun mediaSection(
        mid: Int,
        section: SfuMediaKind,
        ice: SfuIceParameters,
        candidates: List<SfuIceCandidate>,
        fingerprint: SfuDtlsFingerprint
    ): List<String> {
        val pts = section.payloadTypes.joinToString(" ").ifBlank { if (section.kind == "audio") "111" else "96" }
        val proto = "UDP/TLS/RTP/SAVPF"
        val lines = mutableListOf(
            "m=${section.kind} 7 $proto $pts",
            "c=IN IP4 127.0.0.1",
            "a=mid:$mid",
            "a=${section.direction}",
            "a=ice-ufrag:${ice.usernameFragment}",
            "a=ice-pwd:${ice.password}",
            "a=ice-options:renomination",
            "a=fingerprint:${fingerprint.algorithm} ${fingerprint.value}",
            "a=setup:actpass",
            "a=rtcp-mux",
            "a=rtcp-rsize"
        )
        candidates.forEach { candidate ->
            val tcp = candidate.tcpType?.let { " tcptype $it" }.orEmpty()
            lines += "a=candidate:${candidate.foundation} 1 ${candidate.protocol.uppercase()} ${candidate.priority} ${candidate.host} ${candidate.port} typ ${candidate.type}$tcp"
        }
        if (candidates.isNotEmpty()) lines += "a=end-of-candidates"
        section.codecs.forEach { codec ->
            val channels = codec.channels?.let { "/$it" }.orEmpty()
            val name = codec.mimeType.substringAfter('/')
            lines += "a=rtpmap:${codec.payloadType} $name/${codec.clockRate}$channels"
            if (codec.mimeType.contains("opus", true)) {
                lines += "a=fmtp:${codec.payloadType} minptime=10;useinbandfec=1;usedtx=1"
            }
            codec.rtcpFeedback.forEach { fb ->
                val param = fb.parameter.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                lines += "a=rtcp-fb:${codec.payloadType} ${fb.type}$param"
            }
        }
        if (section.codecs.isEmpty() && section.kind == "audio") {
            lines += "a=rtpmap:111 opus/48000/2"
            lines += "a=fmtp:111 minptime=10;useinbandfec=1;usedtx=1"
            lines += "a=rtcp-fb:111 transport-cc"
        }
        if (section.codecs.isEmpty() && section.kind == "video") {
            lines += "a=rtpmap:96 VP9/90000"
            lines += "a=rtcp-fb:96 nack"
            lines += "a=rtcp-fb:96 nack pli"
            lines += "a=rtcp-fb:96 goog-remb"
            lines += "a=rtcp-fb:96 transport-cc"
        }
        section.ssrc?.let { ssrc ->
            lines += "a=ssrc:$ssrc cname:${section.cname.ifBlank { "younes" }}"
        }
        return lines
    }
}

data class SfuMediaKind(
    val kind: String,
    val direction: String,
    val payloadTypes: List<Int> = emptyList(),
    val codecs: List<SfuRtpCodec> = emptyList(),
    val ssrc: Long? = null,
    val cname: String = "younes"
)
