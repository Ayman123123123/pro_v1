package com.red.sovereign.calls

/**
 * SDP codec preferences for the WebRTC engine. These functions reorder offered
 * payload types and add Opus/RTCP attributes; they do not themselves provide
 * end-to-end encryption, noise suppression, standard simulcast RIDs or SVC.
 * Media quality and interoperability require tests on real devices/SFU, not
 * unsupported comparisons with other products.
 */

enum class CallMediaKind {
    VOICE,          // مكالمة صوتية خاصة - P2P، Opus 32k، RNNoise
    VIDEO,          // مكالمة فيديو خاصة - P2P، H264 للتوافق مع WebRTC وSFU
    GROUP_VOICE,    // مكالمة مجموعة دردشة صوتية - Mesh/SFU
    GROUP_VIDEO,    // مكالمة مجموعة دردشة فيديو - Mesh/SFU fallback
    CONFERENCE,     // مؤتمر فيديو/صوت - SFU، simulcast، AV1 SVC
    SPACE,          // مساحة صوتية - SFU speakers mixed listeners
    LIVE;           // بث مباشر - SFU broadcast + LL-HLS

    val wantsVideo: Boolean get() = this == VIDEO || this == GROUP_VIDEO || this == CONFERENCE || this == LIVE
    val wantsSvc: Boolean get() = this == CONFERENCE || this == LIVE || this == SPACE || this == GROUP_VIDEO
    val wantsSimulcast: Boolean get() = this == CONFERENCE || this == LIVE || this == GROUP_VIDEO
    val stereoAudio: Boolean get() = this == LIVE || this == SPACE
    val preferredVideoCodec: String get() = when (this) {
        CONFERENCE, LIVE, GROUP_VIDEO -> "AV1" // استعمله فقط إن كان معروضًا من الطرفين؛ SFU الحالي يعرض VP9/H264
        VIDEO -> "H264" // أولوية متوافقة مع الجهاز، ثم VP9/AV1/VP8 عند غيابه
        else -> "H264"
    }
    val opusBitrateBps: Int get() = when (this) {
        VOICE, GROUP_VOICE -> 32_000 // صوت نقي مع RNNoise
        VIDEO, GROUP_VIDEO -> 48_000 // فيديو مع صوت عالي الجودة
        CONFERENCE, SPACE -> 48_000 // مؤتمر/مساحة
        LIVE -> 64_000 // بث مع موسيقى
    }
    val videoBitrateKbps: Int get() = when (this) {
        VIDEO -> 1200 // 1:1 فيديو عالي
        GROUP_VIDEO -> 800 // مجموعة دردشة
        CONFERENCE -> 1500 // مؤتمر
        LIVE -> 2500 // بث مباشر
        else -> 0
    }
}

object SdpMediaOptimizer {
    fun optimize(sdp: String, kind: CallMediaKind): String {
        if (sdp.isBlank()) return sdp
        var out = preferAudioCodec(sdp, "opus")
        out = applyOpusFmtp(out, kind.opusBitrateBps, kind.stereoAudio)
        if (kind.wantsVideo) {
            out = preferVideoCodecWithFallback(out, kind.preferredVideoCodec)
            out = applyVideoFeedback(out)
            out = applySimulcastIfNeeded(out, kind)
            out = applyAv1SvcIfNeeded(out, kind)
        }
        return out
    }

    fun applyVideoFeedback(sdp: String): String {
        // RFC 4585/5104/8834: NACK+PLI+FIR+REMB+TWCC
        val videoMaps = Regex("a=rtpmap:(\\d+) (VP9|VP8|H264|AV1)/", RegexOption.IGNORE_CASE).findAll(sdp)
        var out = sdp
        videoMaps.forEach { match ->
            val pt = match.groupValues[1]
            if (!out.contains("a=rtcp-fb:$pt nack")) {
                val extras = listOf(
                    "a=rtcp-fb:$pt nack",
                    "a=rtcp-fb:$pt nack pli",
                    "a=rtcp-fb:$pt ccm fir",
                    "a=rtcp-fb:$pt goog-remb",
                    "a=rtcp-fb:$pt transport-cc"
                ).joinToString("\r\n")
                out = out.replace(match.value, match.value + "\r\n" + extras)
            }
        }
        return out
    }

    fun applySimulcastIfNeeded(sdp: String, kind: CallMediaKind): String {
        if (!kind.wantsSimulcast) return sdp
        // إضافة simulcast 3 طبقات: 180p/360p/720p للـ SFU
        // يتم عبر إضافة a=simulcast و a=rid في العرض
        // هنا نضيف تعليق توجيهي للـ SFU
        return if (sdp.contains("a=simulcast:")) sdp else {
            sdp + "\r\na=x-younes-simulcast:180p,360p,720p"
        }
    }

    fun applyAv1SvcIfNeeded(sdp: String, kind: CallMediaKind): String {
        if (!kind.wantsSvc) return sdp
        // AV1 SVC: طبقة واحدة هرمية مع تحجيم زمني ومكاني
        // أفضل من simulcast VP9 من حيث الكفاءة
        return if (sdp.contains("a=x-younes-svc:")) sdp else {
            sdp + "\r\na=x-younes-svc:AV1-L1T3"
        }
    }

    fun preferAudioCodec(sdp: String, codec: String): String = preferCodecOnMedia(sdp, "audio", codec)
    fun preferVideoCodec(sdp: String, codec: String): String = preferCodecOnMedia(sdp, "video", codec)

    fun preferVideoCodecWithFallback(sdp: String, preferredCodec: String): String {
        // Never synthesize an absent codec; private H264 and group AV1 use
        // different fallbacks according to actual endpoint support.
        val order = when (preferredCodec.uppercase()) {
            "AV1" -> listOf("AV1", "VP9", "H264", "VP8")
            "VP9" -> listOf("VP9", "AV1", "H264", "VP8")
            // The deployed SFU advertises VP9/H264/VP8, not AV1. For private
            // calls whose H264 is absent, prefer the compatible VP9 fallback.
            "H264" -> listOf("H264", "VP9", "VP8", "AV1")
            else -> listOf(preferredCodec, "AV1", "VP9", "H264", "VP8")
        }
        for (codec in order) {
            if (sdp.contains(Regex("a=rtpmap:.*${Regex.escape(codec)}/", RegexOption.IGNORE_CASE))) {
                return preferVideoCodec(sdp, codec)
            }
        }
        return sdp
    }

    fun applyOpusFmtp(sdp: String, bitrateBps: Int, stereo: Boolean): String {
        val opus = Regex("a=rtpmap:(\\d+) opus/48000(?:/\\d+)?", RegexOption.IGNORE_CASE).find(sdp)
            ?: return sdp
        val pt = opus.groupValues[1]
        val stereoFlag = if (stereo) 1 else 0
        // Opus محسن 2026: FEC + DTX + stereo + bitrate + cbr + maxplaybackrate
        val fmtp = "a=fmtp:$pt minptime=10;useinbandfec=1;usedtx=1;stereo=$stereoFlag;sprop-stereo=$stereoFlag;maxaveragebitrate=$bitrateBps;cbr=1;maxplaybackrate=48000"
        val existing = Regex("a=fmtp:$pt [^\\r\\n]*").find(sdp)
        return if (existing != null) sdp.replace(existing.value, fmtp) else {
            sdp.replace(opus.value, opus.value + "\r\n" + fmtp)
        }
    }

    fun mos(rttMs: Long, lossPercent: Double): Double {
        val clampedRtt = rttMs.coerceAtLeast(0L)
        val clampedLoss = lossPercent.coerceIn(0.0, 100.0)
        val delayMs = (clampedRtt / 2.0) + 20.0
        val id = if (delayMs > 177.3) 0.024 * delayMs + 0.11 * (delayMs - 177.3) else 0.024 * delayMs
        val ieEff = 10.0 + 40.0 * clampedLoss / (clampedLoss + 10.0)
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
