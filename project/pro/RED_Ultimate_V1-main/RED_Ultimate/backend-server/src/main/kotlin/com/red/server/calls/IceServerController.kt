package com.red.server.calls

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/api/calls")
class IceServerController(
    @Value("\${red.turn.host}") private val host: String,
    @Value("\${red.turn.port:3478}") private val port: Int,
    @Value("\${red.turn.secret}") private val secret: String
) {
    @GetMapping("/ice-servers")
    fun iceServers(authentication: Authentication): IceConfiguration {
        require(secret.length >= 32) { "TURN secret is not configured" }
        require(host.isNotBlank() && host != "0.0.0.0") { "TURN public host is not configured" }
        val expiresAt = Instant.now().plusSeconds(3600).epochSecond
        val username = "$expiresAt:${authentication.name}"
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA1")) }
        val credential = Base64.getEncoder().encodeToString(mac.doFinal(username.toByteArray()))
        return IceConfiguration(
            expiresAt,
            listOf(
                IceServerResponse(listOf("stun:$host:$port")),
                IceServerResponse(listOf("turn:$host:$port?transport=udp", "turn:$host:$port?transport=tcp"), username, credential)
            )
        )
    }
}

data class IceConfiguration(val expiresAt: Long, val iceServers: List<IceServerResponse>)
data class IceServerResponse(val urls: List<String>, val username: String? = null, val credential: String? = null)
