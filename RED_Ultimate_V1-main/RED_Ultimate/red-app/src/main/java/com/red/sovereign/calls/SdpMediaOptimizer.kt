package com.red.sovereign.calls

/**
 * SDP media policy for YOUNES IP calls.
 *
 * Algorithms (IETF / WebRTC production practice):
 * - RFC 7587 Opus: in-band FEC + DTX + maxaveragebitrate
 * - RFC 6716: speech is mono; stereo only for live music-like capture
 * - Codec offer order: put the preferred payload type first on the m-line
 * - ITU-T G.107 simplified E-model → MOS from RTT + loss
 */
enum class CallMediaKind {
    VOICE, VIDEO, CONFERENCE, SPACE, LIVE;

    val wantsVideo: Boolean get() = this == VIDEO || this == CONFERENCE || this == LIVE
    val wantsSvc: Boolean get() = this == CONFERENCE || this == LIVE || this == SPACE
    // فيديو 1:1 بلا simulcast — قرار معتمد 2026-08-20 (CallMediaKindPolicyTest يحميه):
    // طبقات متعددة بلا مستقبلات إضافية تضيع عرضاً وقدمةً فقط؛ simulcast للمجموعات/البث.
    val wantsSimulcast: Boolean get() = this == CONFERENCE || this == LIVE
    val stereoAudio: Boolean get() = this == LIVE
    val preferredVideoCodec: String get() = when (this) {
        CONFERENCE, LIVE -> "VP9"
        else -> "H264"
    }
    val opusBitrateBps: Int get() = when (this) {
        VOICE, SPACE -> 32_000
        VIDEO -> 40_000
        CONFERENCE -> 48_000
        LIVE -> 64_000
    }
}

object SdpMediaOptimizer {
    fun optimize(sdp: String, kind: CallMediaKind): String {
        if (sdp.isBlank()) return sdp
        var out = preferAudioCodec(sdp, "opus")
        out = applyOpusFmtp(out, kind.opusBitrateBps, kind.stereoAudio)
        if (kind.wantsVideo) {
            out = preferVideoCodec(out, kind.preferredVideoCodec)
            out = applyVideoFeedback(out)
        }
        return out
    }

    /**
     * RFC 4585 / 5104 / 8834: NACK+PLI recover lost video, FIR requests a keyframe,
     * goog-remb and transport-cc drive congestion control (GCC / TWCC).
     */
    fun applyVideoFeedback(sdp: String): String {
        if (sdp.contains("a=rtcp-fb:")) return sdp
        val videoMap = Regex("a=rtpmap:(\\d+) (VP9|VP8|H264|AV1)/", RegexOption.IGNORE_CASE).find(sdp)
            ?: return sdp
        val pt = videoMap.groupValues[1]
        val extras = listOf(
            "a=rtcp-fb:$pt nack",
            "a=rtcp-fb:$pt nack pli",
            "a=rtcp-fb:$pt ccm fir",
            "a=rtcp-fb:$pt goog-remb",
            "a=rtcp-fb:$pt transport-cc"
        ).joinToString("\r\n")
        return sdp.replace(videoMap.value, videoMap.value + "\r\n" + extras)
    }

    fun preferAudioCodec(sdp: String, codec: String): String = preferCodecOnMedia(sdp, "audio", codec)

    fun preferVideoCodec(sdp: String, codec: String): String = preferCodecOnMedia(sdp, "video", codec)

    fun applyOpusFmtp(sdp: String, bitrateBps: Int, stereo: Boolean): String {
        val opus = Regex("a=rtpmap:(\\d+) opus/48000(?:/\\d+)?", RegexOption.IGNORE_CASE).find(sdp)
            ?: return sdp
        val pt = opus.groupValues[1]
        val stereoFlag = if (stereo) 1 else 0
        val fmtp = "a=fmtp:$pt minptime=10;useinbandfec=1;usedtx=1;stereo=$stereoFlag;sprop-stereo=$stereoFlag;maxaveragebitrate=$bitrateBps"
        val existing = Regex("a=fmtp:$pt [^\\r\\n]*").find(sdp)
        return if (existing != null) sdp.replace(existing.value, fmtp) else {
            sdp.replace(opus.value, opus.value + "\r\n" + fmtp)
        }
    }

    /**
     * ITU-T G.107 E-model (simplified). R-factor from delay + loss, then MOS.
     * Typical VoIP: MOS ≥ 4.0 excellent, 3.6 good, 3.1 fair, below that poor.
     */
    fun mos(rttMs: Long, lossPercent: Double): Double {
        val delayMs = (rttMs / 2.0) + 20.0
        val id = if (delayMs > 177.3) 0.024 * delayMs + 0.11 * (delayMs - 177.3) else 0.024 * delayMs
        val ieEff = 10.0 + 40.0 * lossPercent / (lossPercent + 10.0)
        val r = (93.2 - id - ieEff).coerceIn(0.0, 100.0)
        val mos = if (r < 0) 1.0
        else if (r > 100) 4.5
        else 1.0 + 0.035 * r + r * (r - 60.0) * (100.0 - r) * 7e-6
        return (mos * 100).toInt() / 100.0
    }

    private fun preferCodecOnMedia(sdp: String, media: String, codec: String): String {
        val rtp = Regex("a=rtpmap:(\\d+) ${Regex.escape(codec)}/", RegexOption.IGNORE_CASE).find(sdp)
            ?: return sdp
        val pt = rtp.groupValues[1]
        val mLine = Regex("m=$media \\d+ [A-Z/]+ ([0-9 ]+)").find(sdp) ?: return sdp
        val payloads = mLine.groupValues[1].trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (payloads.firstOrNull() == pt) return sdp
        payloads.remove(pt)
        payloads.add(0, pt)
        return sdp.replace(mLine.value, mLine.value.replace(mLine.groupValues[1], payloads.joinToString(" ")))
    }
}
