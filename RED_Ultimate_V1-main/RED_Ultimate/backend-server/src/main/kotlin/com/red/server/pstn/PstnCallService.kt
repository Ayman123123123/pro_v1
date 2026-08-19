package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class PstnCallService(
    private val users: UserAccountRepository,
    private val redis: StringRedisTemplate,
    private val jdbc: JdbcTemplate,
    private val pstn: PstnManager,
    private val loadBalancer: DinstarLoadBalancer,
    private val history: CallHistoryService,
    @Value("\${red.dinstar.enabled:false}") private val dinstarEnabled: Boolean = false
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnCallService::class.java)
        /** Valid Yemeni mobile prefixes after +967 or 967 */
        private val YEMEN_MOBILE_PREFIXES = setOf("770", "771", "772", "773", "774", "775", "776", "777", "778", "779",
            "730", "731", "732", "733", "734", "735", "736", "737", "738", "739",
            "710", "711", "712", "713", "714", "715", "716", "717", "718", "719")
    }

    fun dial(userId: UUID, suppliedNumber: String): PstnCallResponse {
        require(dinstarEnabled) { "PSTN_HARDWARE_DISABLED" }
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(user.status == AccountStatus.APPROVED) { "Account is not approved" }
        require(user.pstnEnabled && user.pstnDailyLimit > 0) { "PSTN access is not enabled for this account" }
        
        val number = normalizeYemeniNumber(suppliedNumber)
        
        // Rate limiting with Redis atomic counter
        val day = LocalDate.now(ZoneId.of("Asia/Aden"))
        val key = "red:pstn:daily:${user.id}:$day"
        val used = redis.opsForValue().increment(key) ?: 1L
        if (used == 1L) redis.expire(key, Duration.ofDays(2))
        if (used > user.pstnDailyLimit) {
            redis.opsForValue().decrement(key)
            log.warn("PSTN daily limit reached for user {} ({}/{})", user.redId, used - 1, user.pstnDailyLimit)
            throw IllegalArgumentException("Daily PSTN call limit reached ($used/${user.pstnDailyLimit})")
        }

        // الاختيار يشمل الأسطول كله ويستبعد المنافذ بلا إشارة صالحة.
        // `null` تعني عدم توفر أي مسار — نُبلّغ بذلك بدل محاولة اتصال
        // فاشلة على منفذ ميت.
        val selection = loadBalancer.selectPort(number)
            ?: run {
                redis.opsForValue().decrement(key)
                throw IllegalStateException("No DINSTAR port with a usable signal is available")
            }
        val actionId = UUID.randomUUID().toString()
        return try {
            // نكتب العلاقة قبل originate: إذا أعيد تشغيل الخادم فور قبول AMI
            // تبقى ملكية المنفذ ومسار hangup قابلة للاسترجاع.
            persistActiveCall(actionId, user.redId, selection.gatewayId, selection.portIndex)
            log.info("PSTN dial: user={} number={} gateway={} port={}",
                user.redId, number, selection.gatewayHost, selection.portIndex)
            pstn.dialGsm(number, selection.pjsipEndpoint, actionId)
            history.start(user.redId, number, number, CallType.VOICE, CallRoute.DINSTAR, actionId)
            PstnCallResponse(actionId, "DIALING", number, used.toInt(), user.pstnDailyLimit, selection.portIndex)
        } catch (error: Exception) {
            // لا يبقى منفذ أو علاقة عالقة عندما يرفض Asterisk طلب البداية.
            deleteActiveCall(actionId)
            loadBalancer.releasePort(selection.gatewayId, selection.portIndex)
            redis.opsForValue().decrement(key)
            throw IllegalStateException("Asterisk rejected the PSTN call", error)
        }
    }

    fun hangup(userId: UUID, callId: String): PstnHangupResponse {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        // نتحقق من المالك قبل أي تغيير في السجل أو الحجز؛ هذه العلاقة تبقى بعد restart.
        val active = findActiveCall(callId)
        require(active == null || active.ownerRedId == user.redId) { "Only the call initiator can release this PSTN route" }
        history.end(callId, user.redId)
        if (active != null) {
            deleteActiveCall(callId)
            loadBalancer.releasePort(active.gatewayId, active.port)
        }
        return PstnHangupResponse(callId, active?.port ?: -1, active != null)
    }

    private fun gatewayKey(gatewayId: UUID?) = gatewayId?.toString() ?: "legacy-single-gateway"

    private fun persistActiveCall(callId: String, ownerRedId: String, gatewayId: UUID?, port: Int) {
        check(jdbc.update(
            """INSERT INTO pstn_active_calls
               (call_id, owner_red_id, gateway_key, port_index, created_at, expires_at)
               VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '24 hours')""",
            callId, ownerRedId, gatewayKey(gatewayId), port
        ) == 1) { "Unable to persist PSTN call allocation" }
    }

    private fun findActiveCall(callId: String): ActivePstnCall? = jdbc.query(
        """SELECT owner_red_id, gateway_key, port_index FROM pstn_active_calls
           WHERE call_id = ? AND expires_at > CURRENT_TIMESTAMP""",
        { rs, _ ->
            ActivePstnCall(
                ownerRedId = rs.getString("owner_red_id"),
                gatewayId = rs.getString("gateway_key").takeUnless { it == "legacy-single-gateway" }?.let(UUID::fromString),
                port = rs.getInt("port_index")
            )
        },
        callId
    ).firstOrNull()

    private fun deleteActiveCall(callId: String) {
        jdbc.update("DELETE FROM pstn_active_calls WHERE call_id = ?", callId)
    }

    /**
     * Normalize and validate Yemeni phone numbers.
     * Supports: +967XXXXXXXXX, 967XXXXXXXXX, 0XXXXXXXXX, XXXXXXXXX
     * Output: local format without leading 0 (e.g., 777123456)
     */
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

private data class ActivePstnCall(val ownerRedId: String, val gatewayId: UUID?, val port: Int)
data class PstnHangupResponse(val callId: String, val port: Int, val released: Boolean)

data class PstnCallResponse(
    val callId: String,
    val status: String,
    val number: String,
    val usedToday: Int,
    val dailyLimit: Int,
    val slot: Int = -1
)
