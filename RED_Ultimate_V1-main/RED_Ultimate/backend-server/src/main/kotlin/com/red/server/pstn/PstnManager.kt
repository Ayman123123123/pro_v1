package com.red.server.pstn

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.asteriskjava.manager.DefaultManagerConnection
import org.asteriskjava.manager.TimeoutException
import org.asteriskjava.manager.action.OriginateAction
import org.asteriskjava.manager.action.PingAction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * مدير اتصال Asterisk AMI.
 *
 * ## تحسينات هذه النسخة
 *
 * 1. **Heartbeat حقيقي**: `@Scheduled` كل 30 ثانية يُرسل `PingAction`
 *    للتحقق الفعلي من الاتصال — لا يكتفي بوجود الكائن (connection != null).
 *
 * 2. **إعادة اتصال تلقائية مع Exponential Backoff**:
 *    - اكتشاف انقطاع Heartbeat → إلغاء الاتصال الحالي → إعادة المحاولة
 *    - حد أقصى 60 ثانية بين المحاولات
 *
 * 3. **Timeout حقيقي للـ sendAction**: يمنع تعليق الخيط للأبد.
 *
 * 4. **@PostConstruct**: يتصل عند بدء التطبيق بدل الانتظار حتى أول مكالمة.
 */
@Service
class PstnManager(
    @Value("\${ASTERISK_AMI_HOST:red-pstn-gateway}") private val amiHost: String,
    @Value("\${ASTERISK_AMI_USER:red_admin}") private val amiUser: String,
    @Value("\${ASTERISK_AMI_PASSWORD:}") private val amiPassword: String,
    @Value("\${red.pstn.max-retries:3}") private val maxRetries: Int,
    @Value("\${red.pstn.action-timeout-ms:5000}") private val actionTimeoutMs: Long,
    @Value("\${red.pstn.heartbeat-interval-ms:15000}") private val heartbeatIntervalMs: Long,
    private val dinstarEvents: ObjectProvider<DinstarEventListener>
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnManager::class.java)
        private val RECONNECT_DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000, 60_000)
    }

    private val connectionLock = ReentrantLock()
    @Volatile private var connection: DefaultManagerConnection? = null
    @Volatile private var consecutiveHeartbeatFailures = 0
    private var reconnectAttempt = 0

    private val reconnectScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "pstn-ami-reconnect").apply { isDaemon = true }
        }
    @Volatile private var reconnectFuture: ScheduledFuture<*>? = null

    // ── Startup ────────────────────────────────────────────────────────────

    @PostConstruct
    fun startup() {
        if (amiPassword.isBlank()) {
            log.warn("ASTERISK_AMI_PASSWORD not configured — PSTN disabled")
            return
        }
        try {
            ensureConnected()
        } catch (e: Exception) {
            log.warn("Initial AMI connection failed ({}), will retry via heartbeat", e.message)
        }
    }

    // ── Heartbeat ──────────────────────────────────────────────────────────

    /**
     * Heartbeat scheduler — يُشغَّل من Spring `@EnableScheduling`.
     * يُرسل Ping إلى Asterisk AMI ويكتشف الانقطاع الفعلي.
     *
     * لن يُشغَّل إذا لم تُضَف `@EnableScheduling` على Configuration.
     */
    @Scheduled(fixedDelayString = "\${red.pstn.heartbeat-interval-ms:30000}")
    fun heartbeat() {
        val conn = connection ?: run {
            log.debug("AMI connection null — attempting reconnect")
            reconnectWithBackoff()
            return
        }
        try {
            val ping = conn.sendAction(PingAction(), actionTimeoutMs)
            if (ping == null) {
                handleHeartbeatFailure("Ping returned null")
            } else {
                consecutiveHeartbeatFailures = 0
                reconnectAttempt = 0
                log.trace("AMI heartbeat OK")
            }
        } catch (e: TimeoutException) {
            handleHeartbeatFailure("Ping timeout")
        } catch (e: Exception) {
            handleHeartbeatFailure("Ping error: ${e.message}")
        }
    }

    private fun handleHeartbeatFailure(reason: String) {
        consecutiveHeartbeatFailures++
        log.warn("AMI heartbeat failed ({}/3): {}", consecutiveHeartbeatFailures, reason)
        if (consecutiveHeartbeatFailures >= 2) {
            log.error("AMI connection appears dead — forcing reconnect")
            connectionLock.withLock {
                runCatching { connection?.logoff() }
                connection = null
            }
            reconnectWithBackoff()
        }
    }

    private fun reconnectWithBackoff() {
        val delay = RECONNECT_DELAYS.getOrElse(reconnectAttempt) { RECONNECT_DELAYS.last() }
        reconnectAttempt++
        log.info("Scheduling AMI reconnect in {}ms (attempt {})", delay, reconnectAttempt)
        reconnectFuture?.cancel(false)
        reconnectFuture = reconnectScheduler.schedule(
            {
                try {
                    ensureConnected()
                    consecutiveHeartbeatFailures = 0
                    reconnectAttempt = 0
                    log.info("AMI reconnected successfully")
                } catch (e: Exception) {
                    log.error("AMI reconnect attempt {} failed: {}", reconnectAttempt, e.message)
                }
            },
            delay,
            TimeUnit.MILLISECONDS
        )
    }

    // ── Connection ─────────────────────────────────────────────────────────

    private fun ensureConnected(): DefaultManagerConnection {
        connection?.let { existing ->
            return existing  // Fast path (no lock)
        }
        return connectionLock.withLock {
            connection?.let { return it }

            require(amiPassword.isNotBlank()) { "ASTERISK_AMI_PASSWORD must be configured" }
            log.info("Connecting to Asterisk AMI at {}...", amiHost)

            val conn = DefaultManagerConnection(amiHost, amiUser, amiPassword).apply {
                // asterisk-java يعرّف هذه كـ setter بلا getter — تُستدعى كدوال لا كخصائص في Kotlin
                setSocketTimeout(actionTimeoutMs.toInt())
                // socket read timeout must exceed heartbeat interval to prevent idle disconnects
                setSocketReadTimeout((heartbeatIntervalMs * 3).toInt())
            }
            conn.login()

            dinstarEvents.ifAvailable { listener ->
                conn.addEventListener(listener)
                log.info("Registered DinstarEventListener on AMI connection")
            }

            connection = conn
            log.info("AMI connection established to {}@{}", amiUser, amiHost)
            conn
        }
    }

    // ── Dial ───────────────────────────────────────────────────────────────

    /**
     * إخراج مكالمة عبر Asterisk إلى بوابة DINSTAR.
     *
     * @param phoneNumber رقم الوجهة — مُتحقَّق منه (أرقام فقط + علامة دولية)
     * @param pjsipEndpoint اسم نظير PJSIP — مُتحقَّق منه ضد الحقن
     * @param portIndex فهرس المنفذ داخل البوابة الاختيارية — يُستخدم لتوجيه
     *                  الاتصال إلى شريحة SIM محددة. -1 يعني التلقائي.
     * @return correlationId يُستخدَم كـ callId في قاعدة البيانات
     */
    fun dialGsm(
        phoneNumber: String,
        pjsipEndpoint: String = "dinstar-gateway",
        portIndex: Int = -1
    ): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        require(pjsipEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid PJSIP endpoint name" }

        val correlationId = UUID.randomUUID().toString()
        val action = OriginateAction().apply {
            actionId = correlationId
            channel = "Local/$phoneNumber@from-red-backend"
            application = "Wait"
            data = "1"
            callerId = "RED SOVEREIGN"
            // Pass selected gateway to dialplan
            setVariable("RED_GW", pjsipEndpoint)
            // Expose the selected SIM/port slot for gateway dialplan branching
            setVariable("RED_PORT_INDEX", portIndex.toString())
            // Expose correlationId for DinstarEventListener binding
            setVariable("RED_CALL_ID", correlationId)
            setAsync(true)
        }

        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val conn = ensureConnected()
                val response = conn.sendAction(action, actionTimeoutMs)
                check(response.response?.equals("Success", ignoreCase = true) == true) {
                    response.message ?: "Asterisk rejected originate action"
                }
                log.info(
                    "PSTN originate sent for {} via {} (callId={}, attempt={})",
                    phoneNumber, pjsipEndpoint, correlationId, attempt + 1
                )
                return correlationId
            } catch (e: Exception) {
                lastException = e
                log.warn("PSTN originate attempt {}/{} failed: {}", attempt + 1, maxRetries, e.message)
                connectionLock.withLock { connection = null }
                if (attempt < maxRetries - 1) {
                    val retryDelayMs = 1_000L * (attempt + 1)
                    try {
                        Thread.sleep(retryDelayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("PSTN originate interrupted during retry backoff", ie)
                    }
                }
            }
        }
        throw IllegalStateException(
            "Asterisk rejected PSTN call after $maxRetries attempts: ${lastException?.message}",
            lastException
        )
    }

    /**
     * هل الاتصال بـ Asterisk سليم؟ يُستخدَم في health endpoint.
     */
    fun isConnected(): Boolean = connection != null && consecutiveHeartbeatFailures < 2

    @PreDestroy
    fun close() {
        reconnectFuture?.cancel(false)
        reconnectScheduler.shutdownNow()
        connectionLock.withLock {
            connection?.let { conn ->
                runCatching { conn.logoff() }.onSuccess { log.info("AMI connection closed") }
                connection = null
            }
        }
    }
}
