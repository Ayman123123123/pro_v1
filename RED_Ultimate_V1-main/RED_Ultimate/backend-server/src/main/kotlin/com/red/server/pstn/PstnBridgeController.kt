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
    private val loadBalancer: DinstarLoadBalancer,
    private val progress: PstnCallProgressTracker,
    @org.springframework.context.annotation.Lazy private val pstnManager: EnhancedPstnManager,
    private val reservations: PersistentReservationService? = null,
    @Value("\${red.pstn.sip-secret:red-secret-token}") private val sipSecret: String,
    @Value("\${ASTERISK_WSS_URL:wss://localhost:8089/ws}") private val asteriskWssUrl: String,
    @Value("\${red.pstn.bridge-secret-ttl-minutes:60}") private val bridgeSecretTtlMinutes: Long,
    @Value("\${red.pstn.turn-url:}") private val turnUrl: String,
    @Value("\${red.pstn.turn-username:}") private val turnUsername: String,
    @Value("\${red.pstn.turn-password:}") private val turnPassword: String,
) {
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
        // إذا تم طلب منفذ محدد وكان المالك أدمن، يُسجل للتنبيه فقط
        if (request.port != null && user.pstnPortIndex != null) {
            log.warn("PSTN bridge: port override ignored for app user={} requested={} bound={}",
                user.redId, request.port, user.pstnPortIndex)
        }

        // تحديد المنفذ والممر: استخدام الشريحة المربوطة إن وُجدت، أو التوزيع الذكي الذاتي
        val boundPort = user.pstnPortIndex
        val (effectivePort, gwId, targetGw) = if (boundPort != null) {
            val gid = user.pstnGatewayId
            val host = gid?.let { g ->
                runCatching { fleet.findGateway(g)?.host
                    ?: jdbc.queryForObject("SELECT host FROM telecom_gateways WHERE id = ?", String::class.java, g) }
                    .getOrNull()
            }
            val gwName = if (host != null) "dinstar-gw-${host.replace('.', '-')}-port-$boundPort" else "dinstar-port-$boundPort"
            Triple(boundPort, gid, gwName)
        } else {
            // اختيار تلقائي محترف باستخدام موزّع الأحمال الأفضل
            val selection = loadBalancer.selectPort(targetNumber = number)
            if (selection == null) {
                redis.opsForValue().decrement(key)
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    mapOf("error" to "NO_AVAILABLE_PSTN_PORT", "message" to "No active PSTN line/SIM available at the moment")
                )
            }
            Triple(selection.portIndex, selection.gatewayId, selection.pjsipEndpoint)
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
                // بوابة واحدة: aliases التوافق هي dinstar-port-N، لا أسماء موضعية.
                "dinstar-port-$effectivePort"
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
     *
     * ⚠️ كان يحذف مفتاحَي Redis فقط: لا تحرير للمنفذ في الموزّع، ولا حذف
     * لصفوف Postgres، ولا إسقاط لساق GSM. فإن انتهت المكالمة من التطبيق
     * بقيت ساق البوابة قائمة حتى `Wait/DIAL_TIMEOUT` والمنفذ الخلوي مشغولًا،
     * وبقي صف `pstn_active_calls` يرفض كل محاولة تالية.
     */
    @PostMapping("/bridge/{callId}/hangup")
    fun bridgeHangup(@PathVariable callId: String, authentication: Authentication): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val activeKey = PstnActiveCallKeys.activeKey(userId)
        val raw = redis.opsForValue().get(activeKey) ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(mapOf("error" to "NO_ACTIVE_PSTN_CALL"))
        val parts = raw.split(":")
        if (parts.size >= 2 && parts[0] == callId) {
            val bound = PstnActiveCallKeys.parse(raw)
            redis.delete(activeKey)
            redis.delete("red:pstn:calluser:$callId")
            redis.delete("red:pstn:callport:$callId")
            // إسقاط ساق GSM أولًا: هذا ما يُحرّر المنفذ على الجهاز نفسه،
            // ويُنتج HangupEvent الذي يُكمل التنظيف عبر DinstarEventListener.
            runCatching { pstnManager.hangupCall(callId) }
                .onFailure { log.debug("PSTN bridge hangup: AMI hangup for {} failed: {}", callId, it.message) }
            // تحرير المنفذ في الموزّع والطبقة الدائمة — مقيّد بـcallId
            // فلا يمسح حجز مكالمة أخرى على المنفذ نفسه.
            if (bound != null && bound.second in 0..63) {
                val gw = bound.third.takeIf { it != PstnActiveCallKeys.LOCAL_GATEWAY_ID }
                runCatching { loadBalancer.releasePort(gw, bound.second, callId) }
                    .onFailure { log.warn("PSTN bridge hangup: port release failed: {}", it.message) }
            }
            runCatching { reservations?.unbindActiveCall(userId, callId) }
                .onFailure { log.debug("PSTN bridge hangup: persistent unbind failed: {}", it.message) }
            runCatching { progress.finishByCallId(callId) }
            // End the call in history (the call started in bridge() above)
            runCatching { history.end(callId, userId.toString()) }
                .onFailure { log.warn("PSTN bridge hangup: failed to end history for callId={}: {}", callId, it.message) }
            log.info("PSTN bridge hangup: user={} callId={} released port={}", userId, callId, bound?.second ?: -1)
            return ResponseEntity.ok(mapOf("status" to "RELEASED", "port" to (bound?.second ?: -1)))
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to "CALL_ID_MISMATCH"))
    }

    @GetMapping("/bridge/health")
    fun health(): ResponseEntity<Any> {
        return ResponseEntity.ok(mapOf("status" to "UP", "service" to "pstn-bridge"))
    }

    /**
     * نقل مكالمة PSTN نشطة إلى جهاز آخر للمستخدم نفسه (كما في واتساب 2026).
     * الجهاز الأول يستدعي هذا المسار → يحصل على transferToken قصير العمر (دقيقتان).
     * الجهاز الثاني يستدعي claim بنفس التوكن → يحصل على بيانات الجسر ويكمل المكالمة.
     * لا يُسقط ساق GSM ولا يُحرر المنفذ — النقل تبديل أجهزة فقط.
     */
    @PostMapping("/bridge/{callId}/transfer")
    fun bridgeTransfer(
        @PathVariable callId: String,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val activeKey = PstnActiveCallKeys.activeKey(userId)
        val raw = redis.opsForValue().get(activeKey)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "NO_ACTIVE_PSTN_CALL"))
        if (raw.split(":").getOrNull(0) != callId) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "CALL_ID_MISMATCH"))
        }
        val targetDeviceId = body["targetDeviceId"]?.toString()?.trim()?.ifBlank { null }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "targetDeviceId is required"))
        val deviceUuid = runCatching { UUID.fromString(targetDeviceId) }.getOrNull()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_DEVICE_ID"))
        val owned = runCatching {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_devices WHERE id = ? AND user_id = ?",
                Long::class.java, deviceUuid, userId
            ) ?: 0L
        }.getOrDefault(0L) > 0
        if (!owned) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "DEVICE_NOT_OWNED"))
        }
        val targetNumber = body["targetNumber"]?.toString()?.trim()?.ifBlank { null }
        val token = UUID.randomUUID().toString().replace("-", "")
        redis.opsForValue().set(
            "red:pstn:transfer:$callId",
            "${userId}:${deviceUuid}:${targetNumber.orEmpty()}:$token",
            Duration.ofMinutes(2)
        )
        log.info("PSTN transfer offered: user={} callId={} device={}", userId, callId, deviceUuid)
        return ResponseEntity.ok(mapOf(
            "transferToken" to token,
            "callId" to callId,
            "expiresInSeconds" to 120
        ))
    }

    /**
     * استلام مكالمة منقولة على الجهاز الثاني.
     * يُعيد بيانات الجسر نفسها (حساب SIP مشترك) مع سياق المكالمة — لمرة واحدة.
     */
    @PostMapping("/bridge/{callId}/claim")
    fun bridgeClaim(
        @PathVariable callId: String,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val deviceId = body["deviceId"]?.toString()?.trim()?.ifBlank { null }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "deviceId is required"))
        val token = body["transferToken"]?.toString()?.trim()?.ifBlank { null }
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "transferToken is required"))
        val deviceUuid = runCatching { UUID.fromString(deviceId) }.getOrNull()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_DEVICE_ID"))
        val stored = redis.opsForValue().get("red:pstn:transfer:$callId")
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "TRANSFER_NOT_FOUND_OR_EXPIRED"))
        val parts = stored.split(":", limit = 4)
        if (parts.size != 4 || parts[0] != userId.toString() || parts[1] != deviceUuid.toString() || parts[3] != token) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "TRANSFER_TOKEN_MISMATCH"))
        }
        redis.delete("red:pstn:transfer:$callId")
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val iceConfig = try {
            iceController.iceServers(authentication)
        } catch (e: Exception) {
            log.warn("Failed to generate ICE servers for transfer claim: {}", e.message)
            IceConfiguration(Instant.now().plusSeconds(3600).epochSecond, emptyList())
        }
        val turn = if (turnUrl.isNotBlank()) TurnCredentials(turnUrl, turnUsername, turnPassword) else null
        log.info("PSTN transfer claimed: user={} callId={} device={}", userId, callId, deviceUuid)
        return ResponseEntity.ok(BridgeResponse(
            callId = callId,
            sipServer = asteriskWssUrl,
            sipUsername = "red-webrtc-client",
            sipPassword = sipSecret,
            sipTransport = "WSS",
            targetNumber = parts[2],
            iceServers = iceConfig,
            expiresAt = Instant.now().plusSeconds(3600).epochSecond,
            usedToday = 0,
            dailyLimit = user.pstnDailyLimit,
            turnServerUrl = turn?.url,
            turnUsername = turn?.username,
            turnPassword = turn?.password,
            port = null,
            gateway = null
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




