package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class PstnRetryEvent(val callId: String, val userId: UUID, val redId: String, val phoneNumber: String, val port: Int)

@Service
class PstnCallService(
    private val users: UserAccountRepository,
    private val redis: StringRedisTemplate,
    private val pstn: PstnManager,
    private val loadBalancer: DinstarLoadBalancer,
    private val history: CallHistoryService,
    private val retryScheduler: ScheduledExecutorService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnCallService::class.java)
        /** Valid Yemeni mobile prefixes after +967 or 967 */
        private val YEMEN_MOBILE_PREFIXES = setOf("770", "771", "772", "773", "774", "775", "776", "777", "778", "779",
            "730", "731", "732", "733", "734", "735", "736", "737", "738", "739",
            "710", "711", "712", "713", "714", "715", "716", "717", "718", "719")
    }

    fun dial(userId: UUID, suppliedNumber: String, slotIndex: Int? = null): PstnCallResponse {
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

        return runCatching {
            val selection = loadBalancer.selectPort(number, slotIndex)
                ?: throw IllegalStateException("No DINSTAR port with a usable signal is available")
            log.info("PSTN dial: user={} number={} gateway={} port={}",
                user.redId, number, selection.gatewayHost, selection.portIndex)
            val portIdx = if (slotIndex != null && slotIndex >= 0) slotIndex else selection.portIndex
            val actionId = pstn.dialGsm(number, selection.pjsipEndpoint, portIdx)
            history.start(user.redId, number, number, CallType.AUDIO_1V1, CallRoute.DINSTAR, actionId)
            // ربط المنفذ الفعلي بالمستخدم لضمان أن الإنهاء اللاحق يُحرر
            // منفذ هذه المكالمة فقط، لا أي منفذ اعتباطي عبر الأسطول.
            redis.opsForValue().set(activeKey(user.id), "${actionId}:${selection.gatewayId}:${selection.portIndex}", Duration.ofMinutes(30))
            PstnCallResponse(actionId, "DIALING", number, used.toInt(), user.pstnDailyLimit, portIdx)
        }.getOrElse {
            redis.opsForValue().decrement(key)
            throw IllegalStateException("Asterisk rejected the PSTN call", it)
        }
    }

    /**
     * Fetch a user account by ID — used by the controller for pre-checks.
     */
    fun getUser(userId: UUID) = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }

    /**
     * Check whether the user currently has an active PSTN call.
     */
    fun hasActiveCall(userId: UUID): Boolean {
        val raw = redis.opsForValue().get(activeKey(userId))
        if (raw != null) return true
        return loadBalancer.hasActiveCall(userId)
    }

    /**
     * يعيد المنفذ النشط الحالي للمستخدم (المنفذ الذي رُبط بمكالمته الجارية).
     * @return Triple<callId, portIndex, gatewayId> أو null إذا لا توجد مكالمة نشطة.
     */
    fun resolveActiveCall(userId: UUID): Triple<String, Int, UUID>? {
        val raw = redis.opsForValue().get(activeKey(userId)) ?: return loadBalancer.resolveActiveCall(userId)?.let {
            Triple(it.first, it.second, it.third)
        }
        val parts = raw.split(":")
        if (parts.size != 3) return null
        return try {
            Triple(
                parts[0],
                parts[1].toInt(),
                UUID.fromString(parts[2])
            )
        } catch (_: Exception) { null }
    }

    /**
     * Active PSTN call status for the current user.
     */
    fun getPstnStatus(userId: UUID): Map<String, Any> {
        val user = getUser(userId)
        val active = resolveActiveCall(userId)
        val day = LocalDate.now(ZoneId.of("Asia/Aden"))
        val countKey = "red:pstn:daily:${user.id}:$day"
        val usedToday = redis.opsForValue().get(countKey)?.toIntOrNull() ?: 0
        return mapOf(
            "pstnEnabled" to user.pstnEnabled,
            "pstnDailyLimit" to user.pstnDailyLimit,
            "usedToday" to usedToday,
            "activeCall" to (active != null),
            "callId" to (active?.first ?: ""),
            "port" to (active?.second ?: -1),
            "gatewayId" to (active?.third?.toString() ?: ""),
            "route" to "Asterisk→PJSIP→DINSTAR"
        )
    }

    fun clearActive(userId: UUID) {
        redis.delete(activeKey(userId))
    }

    private fun activeKey(userId: UUID) = "red:pstn:active:$userId"

    private val retryAttempts = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Retry scheduling for a failed PSTN call — fires asynchronously
     * with exponential backoff, then cleans up Redis state on exhaustion.
     */
    @Async
    fun handleRetry(event: PstnRetryEvent) {
        val attempt = retryAttempts.computeIfAbsent(event.callId) { AtomicInteger(0) }.incrementAndGet()
        val backoffMs = listOf(1_000L, 2_000L, 5_000L, 10_000L).getOrElse(attempt - 1) { 10_000L }
        retryScheduler.schedule(
            {
                try {
                    val user = users.findById(event.userId).orElse(null)
                        ?: run { log.warn("Retry: user {} gone", event.userId); return@schedule }
                    if (!user.pstnEnabled || user.status != AccountStatus.APPROVED) {
                        log.warn("Retry: user {} no longer eligible", event.userId)
                        return@schedule
                    }
                    val selection = loadBalancer.selectPort(event.phoneNumber, null)
                    if (selection == null) {
                        if (attempt >= 4) {
                            log.error("PSTN retry exhausted for {} on {}", event.userId, event.phoneNumber)
                            clearActive(event.userId)
                            retryAttempts.remove(event.callId)
                        } else {
                            handleRetry(event)
                        }
                        return@schedule
                    }
                    pstn.dialGsm(event.phoneNumber, selection.pjsipEndpoint, event.port)
                    log.info("PSTN retry succeeded for user {} call {}", event.userId, event.callId)
                    retryAttempts.remove(event.callId)
                } catch (e: Exception) {
                    log.error("PSTN retry failed for {}: {}", event.callId, e.message)
                    if (attempt >= 4) {
                        clearActive(event.userId)
                        retryAttempts.remove(event.callId)
                    } else {
                        handleRetry(event)
                    }
                }
            },
            backoffMs,
            TimeUnit.MILLISECONDS
        )
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

data class PstnCallResponse(
    val callId: String,
    val status: String,
    val number: String,
    val usedToday: Int,
    val dailyLimit: Int,
    val slot: Int = -1
)
