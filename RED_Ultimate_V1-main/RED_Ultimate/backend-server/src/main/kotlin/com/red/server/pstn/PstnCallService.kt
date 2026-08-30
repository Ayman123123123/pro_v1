package com.red.server.pstn

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.event.EventListener
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
    private val pstn: EnhancedPstnManager,  // ✅ تم التحديث لاستخدام Enhanced
    private val loadBalancer: DinstarLoadBalancer,
    private val history: CallHistoryService,
    private val progress: PstnCallProgressTracker,
    @Qualifier("pstnRetryScheduler") private val retryScheduler: ScheduledExecutorService,
    private val reservations: PersistentReservationService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnCallService::class.java)

        /**
         * الطول الوطني لرقم المحمول اليمني: بادئة من رقمين + 7 أرقام.
         *
         * الهاتف الثابت أقصر (رمز محافظة من رقم واحد + 6–7 أرقام) ولا
         * تتصل به البوابة عبر شريحة GSM، فيُرفض هنا.
         */
        private const val MOBILE_NSN_LENGTH = 9
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
            throw com.red.server.auth.RateLimitExceededException()
        }

        // حجز ذرّي قبل أي استهلاك للمنافذ: SETNX يمنع مكالمتين متزامنتين
        // لنفس المستخدم من عبور الفحص معًا وحجز المنفذ نفسه (TOCTOU).
        val reservationId = UUID.randomUUID().toString()
        val reserved = redis.opsForValue().setIfAbsent(
            activeKey(user.id), "$reservationId:reserving:0", Duration.ofMinutes(30)
        )
        if (reserved != true) {
            redis.opsForValue().decrement(key)
            throw IllegalStateException("ALREADY_IN_PSTN_CALL")
        }

        return runCatching {
            // الربط الدائم 1:1 — كل حساب يملك شريحة ثابتة (16 منفذ = 8G + 8T)
            val selection = if (user.pstnGatewayId != null && user.pstnPortIndex != null) {
                // تحقق من أن الشريحة الدائمة متاحة (إشارة + تسجيل + غير مشغولة)
                val permanent = loadBalancer.selectPermanentPort(user.pstnGatewayId!!, user.pstnPortIndex!!, number)
                    ?: throw IllegalStateException("PSTN SIM not available: gateway=${user.pstnGatewayId} port=${user.pstnPortIndex} — check signal/registration")
                log.info("PSTN dial (permanent): user={} sim={} number={} gateway={} port={}",
                    user.redId, user.pstnNumber, number, permanent.gatewayHost, permanent.portIndex)
                permanent
            } else {
                // Fallback: لم يُربط المستخدم بعد — استخدم أقرب شريحة متاحة (مؤقت)
                loadBalancer.selectPort(number, slotIndex)
                    ?: throw IllegalStateException("No DINSTAR port with a usable signal is available — assign permanent SIM first")
            }
            // الاختيار القابل للتنفيذ هو ما رجعه الموزع بعد شروط الربط الدائم
            // والإشارة والانشغال؛ لا تُعد كتابة slotIndex مختلفة دليلًا على منفذ فعلي.
            val portIdx = selection.portIndex
            // إظهار رقم الشريحة الحقيقي للمستلم الخارجي
            val callerSimNumber = user.pstnNumber ?: selection.simNumber
            val actionId = pstn.dialGsm(number, selection.pjsipEndpoint, portIdx, callerSimNumber)
            history.start(user.redId, number, number, CallType.AUDIO_1V1, CallRoute.DINSTAR, actionId)
            // تسجيل المراحل يجب أن يسبق وصول أحداث AMI وإلا تعذّر ربط
            // القناة بصاحبها وضاعت مراحل المكالمة (ميزة تتبّع المراحل من origin).
            progress.register(actionId, user.redId, number)
            // ربط المنفذ الفعلي بالمستخدم لضمان أن الإنهاء اللاحق يُحرر
            // منفذ هذه المكالمة فقط، لا أي منفذ اعتباطي عبر الأسطول.
            redis.opsForValue().set(activeKey(user.id), "${actionId}:${selection.gatewayId}:${selection.portIndex}", Duration.ofMinutes(30))
            redis.opsForValue().set(callUserKey(actionId), user.id.toString(), Duration.ofMinutes(30))
            // حجز دائم — يبقى حتى بعد فقدان Redis أو إعادة تشغيل الباكند
            try { reservations?.bindActiveCall(user.id, actionId, selection.gatewayId, selection.portIndex, number) } catch (_: Exception) {}
            try { reservations?.tryReservePort(selection.gatewayId, selection.portIndex, user.id, actionId, Duration.ofMinutes(30), number) } catch (_: Exception) {}
            PstnCallResponse(actionId, "DIALING", number, used.toInt(), user.pstnDailyLimit, portIdx)
        }.getOrElse {
            // Release both the daily counter and the pre-dial reservation.
            // Without this cleanup, a transient gateway failure leaves the
            // user blocked by ALREADY_IN_PSTN_CALL until the 30-minute TTL.
            redis.opsForValue().decrement(key)
            redis.delete(activeKey(user.id))
            throw IllegalStateException("Asterisk rejected the PSTN call", it)
        }
    }

    /**
     * مكالمة إدارية مجسّرة (Bridged) — للإدمنين فقط.
     * يتجاوز حد اليوم الواحد والربط 1:1، ويتيح اختيار أي بوابة/منفذ/رقم مُتصل.
     */
    fun dialAsAdmin(
        adminId: UUID,
        suppliedNumber: String,
        gatewayHost: String? = null,
        portIndex: Int? = null,
        callerNumber: String? = null,
        bridge: Boolean = true
    ): PstnCallResponse {
        val admin = users.findById(adminId).orElseThrow { NoSuchElementException("Admin not found") }
        require(admin.role == AccountRole.ADMIN) { "Only admins can use dialAsAdmin" }

        val number = normalizeYemeniNumber(suppliedNumber)

        // اختيار البوابة والمنفذ
        val selection = loadBalancer.selectPort(number, portIndex, gatewayHost)
            ?: throw IllegalStateException("NO_USABLE_PORT: no registered port with a usable signal matches gateway=${gatewayHost ?: "any"} port=${portIndex?.toString() ?: "any"}")

        // رقم المتصل: إما الرقم المحدد، أو رقم الشريحة المختارة، أو رقم الشريحة من البوابة
        val effectiveCaller = callerNumber?.takeIf { it.isNotBlank() }?.let { normalizeYemeniNumber(it) }
            ?: selection.simNumber

        val actionId = if (bridge) {
            // جسر حقيقي: يرنّ WebRTC أولاً ثم يُجسر إلى GSM
            pstn.dialGsmBridged(
                phoneNumber = number,
                adminEndpoint = "red-webrtc-client",
                pjsipEndpoint = selection.pjsipEndpoint,
                portIndex = selection.portIndex,
                callerSimNumber = effectiveCaller
            )
        } else {
            // وضع الاختبار: يطلب GSM ثم ينهي فوراً (Wait 1)
            pstn.dialGsm(number, selection.pjsipEndpoint, selection.portIndex, effectiveCaller)
        }

        history.start(admin.redId, number, number, CallType.AUDIO_1V1, CallRoute.DINSTAR, actionId)
        progress.register(actionId, admin.redId, number)

        val selectionGatewayId = selection.gatewayId
        val selectionPortIndex = selection.portIndex
        redis.opsForValue().set(activeKey(admin.id), "${actionId}:${selectionGatewayId}:${selectionPortIndex}", Duration.ofMinutes(30))
        redis.opsForValue().set(callUserKey(actionId), admin.id.toString(), Duration.ofMinutes(30))
        try { reservations?.bindActiveCall(admin.id, actionId, selectionGatewayId, selectionPortIndex, number) } catch (_: Exception) {}
        try { reservations?.tryReservePort(selectionGatewayId, selectionPortIndex, admin.id, actionId, Duration.ofMinutes(30), number) } catch (_: Exception) {}

        return PstnCallResponse(actionId, "DIALING", number, 0, admin.pstnDailyLimit, selectionPortIndex)
    }

    /**
     * Fetch a user account by ID — used by the controller for pre-checks.
     */
    fun getUser(userId: UUID) = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }

    /**
     * Check whether the user currently has an active PSTN call.
     * يفحص Redis أولااً (أسرع)، ثم Postgres (أدق بعد فقدان الكاش).
     */
    fun hasActiveCall(userId: UUID): Boolean {
        val raw = redis.opsForValue().get(activeKey(userId))
        if (raw != null) return true
        if (loadBalancer.hasActiveCall(userId)) return true
        return try { reservations?.hasActiveCall(userId) ?: false } catch (_: Exception) { false }
    }

    /**
     * يعيد المنفذ النشط الحالي للمستخدم (المنفذ الذي رُبط بمكالمته الجارية).
     * @return Triple<callId, portIndex, gatewayId> أو null إذا لا توجد مكالمة نشطة.
     */
    fun resolveActiveCall(userId: UUID): Triple<String, Int, UUID>? {
        val raw = redis.opsForValue().get(activeKey(userId))
        if (raw != null) return PstnActiveCallKeys.parse(raw)?.let { Triple(it.first, it.second, it.third) }
        loadBalancer.resolveActiveCall(userId)?.let { return Triple(it.first, it.second, it.third) }
        return null
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
            "route" to "Asterisk→PJSIP→DINSTAR",
            "managerHealth" to pstn.getMetrics()
        )
    }

    /**
     * إنهاء مكالمة PSTN بأمان — اتحاد نسختَي origin والمحلي:
     * 1) التحقق من الملكية عبر سِجل Redis (callUserKey)
     * 2) تحرير المنفذ في الموزّع
     * 3) إسقاط ساق GSM عبر AMI
     * 4) إنهاء قيد المتتبِّع
     * 5) تنظيف حالة Redis
     */
    fun hangup(userId: UUID, callId: String): PstnHangupResponse {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val owner = findUserByCallId(callId)
        require(owner == null || owner == userId) { "Only the call initiator can release this PSTN route" }
        history.end(callId, user.redId)
        val active = resolveActiveCall(userId)
        val ownsThisCall = active?.first == callId
        val port = if (ownsThisCall) active!!.second else -1
        if (ownsThisCall) {
            loadBalancer.releasePort(active!!.third, active.second)
        }
        runCatching { pstn.hangupCall(callId) }
        progress.finishByCallId(callId)
        clearActive(userId)
        return PstnHangupResponse(callId, port, ownsThisCall)
    }

    fun clearActive(userId: UUID) {
        val raw = redis.opsForValue().get(activeKey(userId))
        val callId = raw?.split(":")?.firstOrNull()
        redis.delete(activeKey(userId))
        callId?.let { redis.delete(callUserKey(it)) }
        if (callId != null) {
            try { reservations?.unbindActiveCall(userId, callId) } catch (_: Exception) {}
        }
    }

    private fun activeKey(userId: UUID) = "red:pstn:active:$userId"
    private fun callUserKey(callId: String) = "red:pstn:calluser:$callId"

    fun findUserByCallId(callId: String): UUID? =
        redis.opsForValue().get(callUserKey(callId))?.let { runCatching { UUID.fromString(it.trim()) }.getOrNull() }

    private val retryAttempts = ConcurrentHashMap<String, AtomicInteger>()

    @EventListener
    @Async
    fun onPstnRetryEvent(event: PstnRetryEvent) {
        log.info("Received PstnRetryEvent for callId={} user={} port={}", event.callId, event.userId, event.port)
        handleRetry(event)
    }

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
                    val selection = if (user.pstnGatewayId != null && user.pstnPortIndex != null) {
                        loadBalancer.selectPermanentPort(user.pstnGatewayId!!, user.pstnPortIndex!!, event.phoneNumber)
                    } else {
                        loadBalancer.selectPort(event.phoneNumber, null)
                    }
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
                    val callerSimNumber = user.pstnNumber ?: selection.simNumber
                    val actionId = pstn.dialGsm(event.phoneNumber, selection.pjsipEndpoint, selection.portIndex, callerSimNumber)
                    history.start(user.redId, event.phoneNumber, event.phoneNumber, CallType.AUDIO_1V1, CallRoute.DINSTAR, actionId)
                    redis.opsForValue().set(activeKey(user.id), "${actionId}:${selection.gatewayId}:${selection.portIndex}", Duration.ofMinutes(30))
                    redis.opsForValue().set(callUserKey(actionId), user.id.toString(), Duration.ofMinutes(30))
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
        val operator = DinstarLoadBalancer.classifyNumber(local)
        require(operator != null && operator.isMobile) { "Unrecognized Yemeni mobile prefix" }
        require(local.length == MOBILE_NSN_LENGTH) {
            "Yemeni mobile numbers are $MOBILE_NSN_LENGTH digits"
        }
        return local
    }
}

data class PstnHangupResponse(val callId: String, val port: Int, val released: Boolean)

data class PstnCallResponse(
    val callId: String,
    val status: String,
    val number: String,
    val usedToday: Int,
    val dailyLimit: Int,
    val slot: Int = -1
)
