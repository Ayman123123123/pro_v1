package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import com.red.server.calls.IceServerController
import com.red.server.calls.IceConfiguration
import com.red.server.services.DinstarFleetService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
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
import jakarta.annotation.PostConstruct

@RestController
@RequestMapping("/api/pstn")
class PstnBridgeController(
    private val users: UserAccountRepository,
    private val redis: StringRedisTemplate,
    private val iceController: IceServerController,
    private val history: CallHistoryService,
    private val objectMapper: ObjectMapper,
    private val fleet: DinstarFleetService,
    private val jdbc: JdbcTemplate,
    @Value("\${red.pstn.sip-secret:}") private val sipSecret: String,
    @Value("\${ASTERISK_WSS_URL:wss://localhost:8089/ws}") private val asteriskWssUrl: String,
    @Value("\${red.pstn.bridge-secret-ttl-minutes:60}") private val bridgeSecretTtlMinutes: Long,
    @Value("\${red.pstn.turn-url:}") private val turnUrl: String,
    @Value("\${red.pstn.turn-username:}") private val turnUsername: String,
    @Value("\${red.pstn.turn-password:}") private val turnPassword: String,
) {
    /**
     * Fail-fast على الإقلاع: بلا سرّ طويل لا يُشغَّل الخادم إطلاقًا.
     * أي قيمة افتراضية = بطاقة رسوم مفتوحة: أي عميل يعرفها يسجّل على PBX ويتصل عبر شرائح SIM.
     * الحدّ 32 موافق لنمط RED_ADMIN_PASSWORD في [com.red.server.auth.AdminBootstrap].
     */
    @PostConstruct
    internal fun validateSipSecret() {
        require(sipSecret.length >= 32 && !sipSecret.startsWith("CHANGE_ME")) {
            "WEBRTC_SIP_SECRET must be set to >= 32 random characters (openssl rand -hex 32) and " +
                "must not be the CHANGE_ME placeholder from .env.example — otherwise any client " +
                "that reads this repository can register and place billed calls."
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PstnBridgeController::class.java)
        /** مفوَّضة إلى YemenNumberPlan — كانت النسخة المحلية تفتقد واي 700-709. */
        private val YEMEN_MOBILE_PREFIXES = YemenNumberPlan.MOBILE_PREFIXES_3
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

        // ── الربط 1:1 صارم في التطبيق — كل الناس سواسية ──
        // الإدمن الحر موجود فقط في لوحة الإدمن (POST /api/admin/dinstar/calls → dialAsAdmin).
        // هنا أي `port` يرسله التطبيق يُتجاهل عمداً، وإلا كسر مستخدم شريحة غيره
        // واستهلك رصيدها وظهر رقمه بدل رقم صاحب الشريحة.
        if (request.port != null) {
            log.warn("PSTN bridge: port override ignored for app user={} requested={} bound={}",
                user.redId, request.port, user.pstnPortIndex)
        }
        val effectivePort = user.pstnPortIndex
        if (effectivePort == null) {
            redis.opsForValue().decrement(key)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                mapOf("error" to "PSTN_SIM_NOT_BOUND", "message" to "No SIM bound to this account — ask the administrator")
            )
        }
        // نحسب targetGw الآن قبل حجز المكالمة — إذا فشل الحل لا نترك حجزاً وهمياً
        val gwId = user.pstnGatewayId
        val host = gwId?.let { gid ->
            runCatching { fleet.findGateway(gid)?.host
                ?: jdbc.queryForObject("SELECT host FROM telecom_gateways WHERE id = ?", String::class.java, gid) }
                .getOrNull()
        }
        val targetGw = if (host != null) {
            "dinstar-gw-${host.replace('.', '-')}-port-$effectivePort"
        } else {
            // تخمين من فهرس المنفذ فقط عند فقدان السجل — لا نعتمد عليه للفوترة
            val gwIndex = if (effectivePort >= 8) 2 else 1
            val localPort = effectivePort % 8
            "dinstar-gw-${gwIndex}-port-${localPort}"
        }

        // Atomic single-active-call reservation: SETNX wins exactly once
        // نستخدم الهوية الحقيقية (callId:gatewayId:port) بدل (callId:null:0) السابق
        // الذي كان يجعل كل تحرير يحرر المنفذ 0 ويترك المنفذ الحقيقي عالقاً 30 دقيقة.
        val callId = UUID.randomUUID().toString()
        val activeKey = PstnActiveCallKeys.activeKey(userId)
        val reserved = redis.opsForValue().setIfAbsent(
            activeKey, PstnActiveCallKeys.format(callId, gwId, effectivePort), Duration.ofMinutes(bridgeSecretTtlMinutes)
        )
        if (reserved != true) {
            redis.opsForValue().decrement(key)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "ALREADY_IN_PSTN_CALL"))
        }

        // Record the call in history so DinstarEventListener and admin dashboards can track it.
        runCatching { history.start(user.redId, number, number, CallType.AUDIO_1V1, CallRoute.DINSTAR, callId) }
            .onFailure { log.warn("PSTN bridge: failed to record call in history: {}", it.message) }
        redis.opsForValue().set("red:pstn:calluser:$callId", user.id.toString(), Duration.ofMinutes(bridgeSecretTtlMinutes))
        // callport يُخزن بالشكل gatewayId:port ليتطابق مع PstnActiveCallKeys
        redis.opsForValue().set("red:pstn:callport:$callId",
            "${gwId ?: "unknown"}:$effectivePort", Duration.ofMinutes(bridgeSecretTtlMinutes))

log.info("PSTN bridge: user={} number={} daily={}/{} callId={} gw={}",
            user.redId, number, used, user.pstnDailyLimit, callId, targetGw)

        val expiresAt = Instant.now().plusSeconds(3600).epochSecond

        val iceConfig = try {
            iceController.iceServers(authentication)
        } catch (e: Exception) {
            log.warn("Failed to generate ICE servers for bridge: {}", e.message)
            IceConfiguration(Instant.now().plusSeconds(3600).epochSecond, emptyList())
        }
        val turn = if (turnUrl.isNotBlank()) TurnCredentials(turnUrl, turnUsername, turnPassword) else null

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
            port = effectivePort,
            gateway = targetGw
        ))
    }

    /**
     * تجهيز مسار SIP/WebRTC لمستلم مكالمة DINSTAR واردة قبل أن يرسل التطبيق
     * PSTN_ACCEPT. لا يحجز رصيداً ولا ينشئ originate؛ يثبت فقط أن المستخدم
     * من المستلمين المحددين في offer قصير العمر.
     */
    @PostMapping("/incoming-bridge")
    fun incomingBridge(@RequestBody request: IncomingBridgeRequest, authentication: Authentication): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        if (user.status != AccountStatus.APPROVED || !user.pstnEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "PSTN_NOT_ENABLED"))
        }
        val raw = redis.opsForValue().get("red:pstn:incoming-call:${request.callId}")
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "INCOMING_CALL_EXPIRED"))
        @Suppress("UNCHECKED_CAST")
        val incoming = runCatching { objectMapper.readValue(raw, Map::class.java) as Map<String, Any?> }
            .getOrElse { return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "INCOMING_CALL_INVALID")) }
        val recipients = (incoming["recipientAccountIds"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        if (userId.toString() !in recipients) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "INCOMING_CALL_NOT_ASSIGNED"))
        }
        val iceConfig = try {
            iceController.iceServers(authentication)
        } catch (e: Exception) {
            log.warn("Failed to generate ICE servers for incoming bridge: {}", e.message)
            IceConfiguration(Instant.now().plusSeconds(3600).epochSecond, emptyList())
        }
val turn = if (turnUrl.isNotBlank()) TurnCredentials(turnUrl, turnUsername, turnPassword) else null
        val usedToday = redis.opsForValue().get("red:pstn:daily:${user.id}:${LocalDate.now(ZoneId.of("Asia/Aden"))}")
            ?.toIntOrNull() ?: 0
        // Determine gateway port from incoming call metadata
        val effectivePort = (incoming["port"] as? Int)?.takeIf { it >= 0 }
        val gatewayHost = incoming["gatewayHost"] as? String
        val targetGw = if (effectivePort != null) {
            if (gatewayHost != null && gatewayHost != "unknown") {
                "dinstar-gw-${gatewayHost.replace('.', '-')}-port-$effectivePort"
            } else {
                val gwIndex = if (effectivePort >= 8) 2 else 1
                val localPort = effectivePort % 8
                "dinstar-gw-${gwIndex}-port-${localPort}"
            }
        } else null
        return ResponseEntity.ok(BridgeResponse(
            callId = request.callId,
            sipServer = asteriskWssUrl,
            sipUsername = "red-webrtc-client",
            sipPassword = sipSecret,
            sipTransport = "WSS",
            targetNumber = incoming["caller"]?.toString().orEmpty(),
            iceServers = iceConfig,
            expiresAt = Instant.now().plusSeconds(3600).epochSecond,
            usedToday = usedToday,
            dailyLimit = user.pstnDailyLimit,
            turnServerUrl = turn?.url,
            turnUsername = turn?.username,
            turnPassword = turn?.password,
            port = effectivePort,
            gateway = targetGw
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

data class BridgeRequest(val number: String, val port: Int? = null)
data class IncomingBridgeRequest(val callId: String)

data class TurnCredentials(val url: String, val username: String, val password: String)

data class BridgeResponse(
    val port: Int?,
    val gateway: String?,
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




