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
 * ─── coturn محلي (للشبكة المحلية) ───
 * - STUN على port 3478 (UDP/TCP)
 * - TURN على port 3478 (UDP/TCP) — relayed traffic
 * - TURNS (TURN over TLS) على port 5349 (UDP/TCP) — firewall bypass
 * - TURN على port 443 (UDP/TCP) — للشبكات التي تمنع المنافذ الأخرى
 *
 * ─── Open Relay عبر الإنترنت (يتجاوز CGNAT) ───
 * - TURN على port 80 (UDP/TCP) — يتجاوز أي فيروال
 * - TURNS على port 443 (TLS) — يتجاوز فيروال الشركات + CGNAT
 *
 * يستخدم REST API Time-Limited HMAC (RFC 7635) لـ coturn المحلي.
 * Open Relay يستخدم static credentials.
 */
@RestController
@RequestMapping("/api/calls")
class IceServerController(
    @Value("\${red.turn.public-host:127.0.0.1}") private val host: String,
    @Value("\${red.turn.port:3478}") private val port: Int,
    @Value("\${red.turn.tls-port:5349}") private val tlsPort: Int,
    @Value("\${red.turn.443-port:443}") private val altPort: Int,
    // default فارغ بدل الفشل عند الإقلاع — التحقق من الطول يتم في iceServers()
    @Value("\${red.turn.secret:}") private val secret: String,
    @Value("\${red.turn.ttl-seconds:3600}") private val ttlSeconds: Long,
    // Open Relay (Metered.ca)
    @Value("\${red.turn.openrelay.enabled:false}") private val openRelayEnabled: Boolean,
    @Value("\${red.turn.openrelay.username:openrelayproject}") private val openRelayUsername: String,
    @Value("\${red.turn.openrelay.password:openrelayproject}") private val openRelayPassword: String
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
            // ═══════════════════════════════════════════════════
            // coturn محلي — للشبكة المحلية (أسرع بدون إنترنت)
            // ═══════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════
            // Open Relay — عبر الإنترنت (يتجاوز CGNAT + فيروال)
            // ═══════════════════════════════════════════════════
            if (openRelayEnabled) {
                // 5) TURN on port 80 — يتجاوز أي فيروال (يبدو كـ HTTP عادي)
                add(IceServerResponse(
                    urls = listOf(
                        "turn:openrelay.metered.ca:80?transport=udp",
                        "turn:openrelay.metered.ca:80?transport=tcp",
                        "turn:openrelay.metered.ca:443?transport=tcp"
                    ),
                    username = openRelayUsername,
                    credential = openRelayPassword
                ))
                // 6) TURNS (TLS) on port 443 — أقوى تجاوز لفيروال الشركات + CGNAT
                add(IceServerResponse(
                    urls = listOf(
                        "turns:openrelay.metered.ca:443?transport=tcp"
                    ),
                    username = openRelayUsername,
                    credential = openRelayPassword
                ))
            }
        }
        return IceConfiguration(expiresAt, servers)
    }
}

data class IceConfiguration(val expiresAt: Long, val iceServers: List<IceServerResponse>)
data class IceServerResponse(val urls: List<String>, val username: String? = null, val credential: String? = null)
