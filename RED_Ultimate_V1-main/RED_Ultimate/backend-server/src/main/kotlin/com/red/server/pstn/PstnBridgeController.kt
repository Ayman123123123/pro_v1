package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.IceServerController
import com.red.server.calls.IceConfiguration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * REST controller for the WebRTC-PSTN bridge.
 *
 * Flow:
 * 1. App calls POST /api/pstn/bridge with target number
 * 2. Backend validates user (approved, PSTN enabled, daily limit)
 * 3. Backend returns SIP credentials + WSS URL + ICE servers
 * 4. App connects to Asterisk via WSS, sends SIP INVITE to the number
 * 5. Asterisk routes through from-red-client-webrtc → DINSTAR → GSM
 */
@RestController
@RequestMapping("/api/pstn")
class PstnBridgeController(
    private val users: UserAccountRepository,
    private val redis: StringRedisTemplate,
    private val iceController: IceServerController,
    @Value("\${WEBRTC_SIP_SECRET:red-webrtc-secret}") private val sipSecret: String,
    @Value("\${ASTERISK_WSS_URL:wss://localhost:8089/ws}") private val asteriskWssUrl: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnBridgeController::class.java)
        private val YEMEN_MOBILE_PREFIXES = setOf(
            "770", "771", "772", "773", "774", "775", "776", "777", "778", "779",
            "780", "781", "782", "783", "784", "785", "786", "787", "788", "789",
            "730", "731", "732", "733", "734", "735", "736", "737", "738", "739",
            "710", "711", "712", "713", "714", "715", "716", "717", "718", "719"
        )
    }

    /**
     * Request SIP credentials + ICE servers for a PSTN call.
     *
     * The app uses these to:
     * 1. Create a PeerConnection with the returned ICE/TURN servers
     * 2. Register with Asterisk via WSS using the SIP credentials
     * 3. Send SIP INVITE to the target number
     * 4. Asterisk bridges to DINSTAR → GSM network
     */
    @PostMapping("/bridge")
    fun bridge(@RequestBody request: BridgeRequest, authentication: Authentication): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)

        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        if (user.status != AccountStatus.APPROVED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "ACCOUNT_NOT_APPROVED"))
        }
        if (!user.pstnEnabled || user.pstnDailyLimit <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "PSTN_NOT_ENABLED"))
        }

        val number = normalizeYemeniNumber(request.number)

        // Daily rate limit via Redis atomic counter
        val day = LocalDate.now(ZoneId.of("Asia/Aden"))
        val key = "red:pstn:daily:${user.id}:$day"
        val used = redis.opsForValue().increment(key) ?: 1L
        if (used == 1L) redis.expire(key, Duration.ofDays(2))
        if (used > user.pstnDailyLimit) {
            redis.opsForValue().decrement(key)
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                mapOf("error" to "DAILY_LIMIT_REACHED", "used" to (used - 1), "limit" to user.pstnDailyLimit)
            )
        }

        // Check if user already has an active PSTN call
        val activeKey = "red:pstn:active:$userId"
        if (redis.opsForValue().get(activeKey) != null) {
            redis.opsForValue().decrement(key)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "ALREADY_IN_PSTN_CALL"))
        }

        // Build ICE servers from the existing IceServerController logic
        val iceConfig = try {
            iceController.iceServers(authentication)
        } catch (e: Exception) {
            log.warn("Failed to generate ICE servers for bridge: {}", e.message)
            IceConfiguration(Instant.now().plusSeconds(3600).epochSecond, emptyList())
        }

        log.info("PSTN bridge: user={} number={} daily={}/{}", user.redId, number, used, user.pstnDailyLimit)

        val expiresAt = Instant.now().plusSeconds(3600).epochSecond

        return ResponseEntity.ok(BridgeResponse(
            sipServer = asteriskWssUrl,
            sipUsername = "red-webrtc-client",
            sipPassword = sipSecret,
            sipTransport = "WSS",
            targetNumber = number,
            iceServers = iceConfig,
            expiresAt = expiresAt,
            usedToday = used.toInt(),
            dailyLimit = user.pstnDailyLimit,
            turnServerUrl = null,
            turnUsername = null,
            turnPassword = null,
        ))
    }

    private fun normalizeYemeniNumber(value: String): String {
        val compact = value.filter { it.isDigit() || it == '+' }
        val local = when {
            compact.startsWith("+967") -> compact.removePrefix("+967")
            compact.startsWith("00967") -> compact.removePrefix("00967")
            compact.startsWith("967") -> compact.removePrefix("967")
            compact.startsWith("0") -> compact.removePrefix("0")
            else -> compact
        }
        require(local.matches(Regex("^[0-9]{6,12}$"))) { "Only valid Yemeni numbers are allowed" }
        require(local.substring(0, minOf(3, local.length)) in YEMEN_MOBILE_PREFIXES || local.length >= 9) {
            "Unrecognized Yemeni mobile prefix"
        }
        return local
    }
}

data class BridgeRequest(val number: String)

data class BridgeResponse(
    val sipServer: String,
    val sipUsername: String,
    val sipPassword: String,
    val sipTransport: String,
    val targetNumber: String,
    val iceServers: IceConfiguration,
    val expiresAt: Long,
    val usedToday: Int,
    val dailyLimit: Int,
    val turnServerUrl: String?,
    val turnUsername: String?,
    val turnPassword: String?,
)
