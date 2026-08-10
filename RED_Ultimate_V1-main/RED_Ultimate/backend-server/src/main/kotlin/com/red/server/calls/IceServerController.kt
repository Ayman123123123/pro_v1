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

/**
 * Ice/TURN credentials endpoint.
 *
 * يدعم:
 * - STUN على port 3478 (UDP/TCP)
 * - TURN على port 3478 (UDP/TCP) — relayed traffic
 * - TURNS (TURN over TLS) على port 5349 (UDP/TCP) — firewall bypass
 * - TURN على port 443 (UDP/TCP) — للشبكات التي تمنع المنافذ الأخرى
 *
 * يستخدم REST API Time-Limited HMAC (RFC 7635) — credentials صالحة لمدة 1 ساعة.
 * الـ username = "${expiresAt}:${userId}" — يمكن لأي TURN server التحقق من التوقيع.
 */
@RestController
@RequestMapping("/api/calls")
class IceServerController(
    @Value("\${red.turn.public-host:127.0.0.1}") private val host: String,
    @Value("\${red.turn.port:3478}") private val port: Int,
    @Value("\${red.turn.tls-port:5349}") private val tlsPort: Int,
    @Value("\${red.turn.443-port:443}") private val altPort: Int,
    @Value("\${red.turn.secret}") private val secret: String,
    @Value("\${red.turn.ttl-seconds:3600}") private val ttlSeconds: Long
) {
    @GetMapping("/ice-servers")
    fun iceServers(authentication: Authentication): IceConfiguration {
        require(secret.length >= 32) { "TURN secret is not configured (must be >= 32 chars)" }
        require(host.isNotBlank() && host != "0.0.0.0") { "TURN public host is not configured" }
        val expiresAt = Instant.now().plusSeconds(ttlSeconds).epochSecond
        val username = "$expiresAt:${authentication.name}"
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA1")) }
        val credential = Base64.getEncoder().encodeToString(mac.doFinal(username.toByteArray()))
        val servers = buildList {
            // 1) STUN — connectivity check
            add(IceServerResponse(listOf("stun:$host:$port")))
            // 2) TURN over UDP/TCP (المعيار)
            add(IceServerResponse(
                urls = listOf("turn:$host:$port?transport=udp", "turn:$host:$port?transport=tcp"),
                username = username, credential = credential
            ))
            // 3) TURNS (TLS) — firewall bypass, encrypted relay
            if (tlsPort > 0) {
                add(IceServerResponse(
                    urls = listOf("turns:$host:$tlsPort?transport=tcp"),
                    username = username, credential = credential
                ))
            }
            // 4) TURN on 443 — للشبكات التي تحجب المنافذ الغريبة
            if (altPort > 0 && altPort != port) {
                add(IceServerResponse(
                    urls = listOf("turns:$host:$altPort?transport=tcp"),
                    username = username, credential = credential
                ))
            }
        }
        return IceConfiguration(expiresAt, servers)
    }
}

data class IceConfiguration(val expiresAt: Long, val iceServers: List<IceServerResponse>)
data class IceServerResponse(val urls: List<String>, val username: String? = null, val credential: String? = null)
