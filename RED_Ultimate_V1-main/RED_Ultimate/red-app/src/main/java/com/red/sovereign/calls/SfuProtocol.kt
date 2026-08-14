package com.red.sovereign.calls

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SfuTicketDto(
    val token: String,
    val expiresInSeconds: Long = 120,
    val roomId: String,
    val role: String = "MEMBER",
    val canProduce: Boolean = true
)

@Serializable
data class SfuIceParameters(
    val usernameFragment: String = "",
    val password: String = "",
    val iceLite: Boolean = true
)

@Serializable
data class SfuIceCandidate(
    val foundation: String = "udpcandidate",
    val priority: Long = 0,
    val ip: String = "",
    val address: String? = null,
    val protocol: String = "udp",
    val port: Int = 0,
    val type: String = "host",
    val tcpType: String? = null
) {
    val host: String get() = address?.ifBlank { ip } ?: ip
}

@Serializable
data class SfuDtlsFingerprint(
    val algorithm: String = "sha-256",
    val value: String = ""
)

@Serializable
data class SfuDtlsParameters(
    val role: String = "auto",
    val fingerprints: List<SfuDtlsFingerprint> = emptyList()
)

@Serializable
data class SfuTransportOptions(
    val id: String,
    val iceParameters: SfuIceParameters = SfuIceParameters(),
    val iceCandidates: List<SfuIceCandidate> = emptyList(),
    val dtlsParameters: SfuDtlsParameters = SfuDtlsParameters()
)

@Serializable
data class SfuRtpCodec(
    val mimeType: String,
    val payloadType: Int,
    val clockRate: Int,
    val channels: Int? = null,
    val parameters: Map<String, JsonElement> = emptyMap(),
    val rtcpFeedback: List<SfuRtcpFeedback> = emptyList()
)

@Serializable
data class SfuRtcpFeedback(
    val type: String,
    val parameter: String = ""
)

@Serializable
data class SfuHeaderExtension(
    val uri: String,
    val id: Int,
    val encrypt: Boolean = false
)

@Serializable
data class SfuRtpEncoding(
    val ssrc: Long? = null,
    val rid: String? = null,
    val dtx: Boolean? = null,
    val maxBitrate: Int? = null
)

@Serializable
data class SfuRtcpParameters(
    val cname: String = "",
    val reducedSize: Boolean = true
)

@Serializable
data class SfuRtpParameters(
    val codecs: List<SfuRtpCodec> = emptyList(),
    val headerExtensions: List<SfuHeaderExtension> = emptyList(),
    val encodings: List<SfuRtpEncoding> = emptyList(),
    val rtcp: SfuRtcpParameters = SfuRtcpParameters()
)

@Serializable
data class SfuExistingProducer(
    val peerId: String,
    val producerId: String,
    val kind: String
)
