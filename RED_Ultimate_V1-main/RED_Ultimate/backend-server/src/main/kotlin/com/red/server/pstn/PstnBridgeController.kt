package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
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

@RestController
@RequestMapping("/api/pstn")
class PstnBridgeController(
    private val users: UserAccountRepository,
    private val redis: StringRedisTemplate,
    private val iceController: IceServerController,
    private val history: CallHistoryService,
    @Value("\${red.pstn.sip-secret:red-secret-token}") private val sipSecret: String,
    @Value("\${ASTERISK_WSS_URL:wss://localhost:8089/ws}") private val asteriskWssUrl: String,
    @Value("\${red.pstn.bridge-secret-ttl-minutes:60}") private val bridgeSecretTtlMinutes: Long,
    @Value("\${red.pstn.turn-url:}") private val turnUrl: String,
    @Value("\${red.pstn.turn-username:}") private val turnUsername: String,
    @Value("\${red.pstn.turn-password:}") private val turnPassword: String,
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
     *
     * The call is reserved atomically (SETNX) so a second concurrent
     * request for the same user cannot double-book a gateway port.
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

        // Atomic single-active-call reservation: SETNX wins exactly once
        val callId = UUID.randomUUID().toString()
        val activeKey = PstnActiveCallKeys.activeKey(userId)
        val reserved = redis.opsForValue().setIfAbsent(
            activeKey, PstnActiveCallKeys.format(callId, null, 0), Duration.ofMinutes(bridgeSecretTtlMinutes)
        )
        if (reserved != true) {
            redis.opsForValue().decrement(key)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "ALREADY_IN_PSTN_CALL"))
        }

        // Record the call in history so DindarEventListener and admin dashboards can track it.
        // The callId returned here becomes the correlationId used in Redis and CallHistoryDocument.
        runCatching { history.start(user.redId, number, number, CallType.AUDIO_1V1, CallRoute.DINSTAR, callId) }
            .onFailure { log.warn("PSTN bridge: failed to record call in history: {}", it.message) }
        // Store reverse mapping callId → userId for admin lookup.
        // Uses the same key prefix (red:pstn:calluser:) as PstnCallService and
        // DindarEventListener so all components share the same lookup table.
        redis.opsForValue().set("red:pstn:calluser:$callId", user.id.toString(), Duration.ofMinutes(bridgeSecretTtlMinutes))

        // Build ICE servers from the existing IceServerController logic
        val iceConfig = try {
            iceController.iceServers(authentication)
        } catch (e: Exception) {
            log.warn("Failed to generate ICE servers for bridge: {}", e.message)
            IceConfiguration(Instant.now().plusSeconds(3600).epochSecond, emptyList())
        }

        // TURN wired from configuration (optional; null when not configured)
        val turn = if (turnUrl.isNotBlank()) {
            TurnCredentials(url = turnUrl, username = turnUsername, password = turnPassword)
        } else null

        log.info("PSTN bridge: user={} number={} daily={}/{} callId={}",
            user.redId, number, used, user.pstnDailyLimit, callId)

        val expiresAt = Instant.now().plusSeconds(3600).epochSecond

        return ResponseEntity.ok(BridgeResponse(
            callId = callId,
            sipServer = asteriskWssUrl,
            sipUsername = "red-webrtc-client",
            sipPassword = sipSecret,
            sipTransport = "WSS",
            targetNumber = number,
            iceServers = iceConfig,
            expiresAt = expiresAt,
            usedToday = used.toInt(),
            dailyLimit = user.pstnDailyLimit,
            turnServerUrl = turn?.url,
            turnUsername = turn?.username,
            turnPassword = turn?.password,
        ))
    }

    /**
     * Release the user's active bridge call. Only the owner of the
     * callId can release it — a stale or foreign callId is ignored so
     * one user cannot kill another user's call.
     */
    @PostMapping("/bridge/{callId}/hangup")
    fun bridgeHangup(@PathVariable callId: String, authentication: Authentication): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val activeKey = PstnActiveCallKeys.activeKey(userId)
        val raw = redis.opsForValue().get(activeKey) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to "NO_ACTIVE_PSTN_CALL"))
        val parts = raw.split(":")
        if (parts.size >= 2 && parts[0] == callId) {
            redis.delete(activeKey)
            redis.delete("red:pstn:calluser:$callId")
            // End the call in history (the call started in bridge() above)
            runCatching { history.end(callId, userId.toString()) }
                .onFailure { log.warn("PSTN bridge hangup: failed to end history for callId={}: {}", callId, it.message) }
            log.info("PSTN bridge hangup: user={} callId={} released", userId, callId)
            return ResponseEntity.ok(mapOf("status" to "RELEASED"))
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to "CALL_ID_MISMATCH"))
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

data class TurnCredentials(val url: String, val username: String, val password: String)

data class BridgeResponse(
    val callId: String,
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
