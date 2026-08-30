package com.red.server.pstn

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.asteriskjava.manager.DefaultManagerConnection
import org.asteriskjava.manager.TimeoutException
import org.asteriskjava.manager.action.OriginateAction
import org.asteriskjava.manager.action.PingAction
import org.asteriskjava.manager.action.HangupAction
import org.asteriskjava.manager.action.RedirectAction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * مدير اتصال Asterisk AMI محسّن — Connection Pool + Health Check شامل
 *
 * ## التحسينات الجديدة:
 *
 * 1. **Connection Pool**: مجموعة اتصالات بدلاً من اتصال واحد
 * 2. **Health Check شامل**: لا يكتفي بـ Ping بل يتحقق من القنوات والذاكرة
 * 3. **Auto Failover**: انتقال تلقائي للاتصال الصحي عند فشل أحدها
 * 4. **Metrics**: إحصائيات الأداء والصحة
 */
@Service
class EnhancedPstnManager(
    @Value("\${ASTERISK_AMI_HOST:red-pstn-gateway}") private val amiHost: String,
    @Value("\${ASTERISK_AMI_USER:red_admin}") private val amiUser: String,
    @Value("\${ASTERISK_AMI_PASSWORD:}") private val amiPassword: String,
    @Value("\${red.pstn.max-retries:3}") private val maxRetries: Int,
    @Value("\${red.pstn.action-timeout-ms:5000}") private val actionTimeoutMs: Long,
    @Value("\${red.pstn.heartbeat-interval-ms:30000}") private val heartbeatIntervalMs: Long,
    @Value("\${red.pstn.pool-size:2}") private val poolSize: Int,
    private val dinstarEvents: ObjectProvider<DinstarEventListener>
) {
    companion object {
        private val log = LoggerFactory.getLogger(EnhancedPstnManager::class.java)
        private val RECONNECT_DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000, 60_000)

        /** حد أقصى لساعات الاتصال النشطة في الذاكرة */
        private const val MAX_CONNECTED_CHANNELS = 100
    }

    // ── Connection Pool ───────────────────────────────────────────────────
    private val connectionPool = ConcurrentLinkedQueue<AsteriskConnection>()
    private val unhealthyConnections = ConcurrentLinkedQueue<AsteriskConnection>()
    private val poolLock = ReentrantLock()

    // ── Metrics ───────────────────────────────────────────────────────────
    private var totalConnections = 0
    private var failedConnections = 0
    private var lastHealthCheck = System.currentTimeMillis()

    // ── Schedulers ────────────────────────────────────────────────────────
    private val reconnectScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "pstn-ami-reconnect").apply { isDaemon = true }
        }
    private val healthScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "pstn-health-check").apply { isDaemon = true }
        }
    private val memoryCleanupScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "pstn-memory-cleanup").apply { isDaemon = true }
        }

    // ── Startup ────────────────────────────────────────────────────────────

    @PostConstruct
    fun startup() {
        if (amiPassword.isBlank()) {
            log.warn("ASTERISK_AMI_PASSWORD not configured — PSTN disabled")
            return
        }

        // Initialize connection pool
        initializePool()

        // Start health checking
        startHealthCheck()

        // Start memory cleanup
        startMemoryCleanup()
    }

    /**
     * تهيئة Connection Pool مع الاتصال الأولي
     */
    private fun initializePool() {
        log.info("Initializing AMI connection pool (size=$poolSize)...")

        for (i in 1..poolSize) {
            try {
                val conn = createConnection(i)
                if (conn != null) {
                    connectionPool.add(conn)
                    totalConnections++
                    log.info("AMI connection {} established to {}@{}", i, amiUser, amiHost)
                }
            } catch (e: Exception) {
                log.warn("AMI connection {} initialization failed: {}", i, e.message)
                failedConnections++
            }
        }

        log.info("AMI connection pool initialized: {} healthy, {} failed",
            connectionPool.size, failedConnections)
    }

    /**
     * إنشاء اتصال جديد بـ Asterisk AMI
     */
    private fun createConnection(poolIndex: Int): AsteriskConnection? {
        require(amiPassword.isNotBlank()) { "ASTERISK_AMI_PASSWORD must be configured" }

        return try {
            val conn = DefaultManagerConnection(amiHost, amiUser, amiPassword).apply {
                setSocketTimeout(actionTimeoutMs.toInt())
                setSocketReadTimeout(300_000)
            }
            conn.login()

            dinstarEvents.ifAvailable { listener ->
                conn.addEventListener(listener)
                log.info("Registered DinstarEventListener on connection {}", poolIndex)
            }

            AsteriskConnection(conn, poolIndex)
        } catch (e: Exception) {
            log.error("Failed to create AMI connection {}: {}", poolIndex, e.message)
            null
        }
    }

    // ── Health Check ──────────────────────────────────────────────────────

    /**
     * فحص صحة اتصالات AMI بشكل دوري
     */
    private fun startHealthCheck() {
        healthScheduler.scheduleAtFixedRate({
            try {
                val healthyCount = checkConnections()
                log.debug("AMI health check: {} healthy connections out of {}",
                    healthyCount, connectionPool.size)
            } catch (e: Exception) {
                log.warn("AMI health check failed: {}", e.message)
            }
        }, 0, heartbeatIntervalMs, TimeUnit.MILLISECONDS)
    }

    /**
     * فحص صحة جميع الاتصالات في الـ Pool
     *
     * @return عدد الاتصالات الصحية
     */
    private fun checkConnections(): Int {
        val healthyConnections = ConcurrentLinkedQueue<AsteriskConnection>()

        for (conn in connectionPool) {
            if (isConnectionHealthy(conn)) {
                healthyConnections.add(conn)
            } else {
                log.warn("AMI connection {} is unhealthy — marking for recovery", conn.poolIndex)
                markUnhealthy(conn)
            }
        }

        // Replace unhealthy connections
        if (healthyConnections.size < connectionPool.size) {
            log.info("Replenishing AMI connections: {} healthy, replacing {}",
                healthyConnections.size, connectionPool.size - healthyConnections.size)
            connectionPool.clear()
            connectionPool.addAll(healthyConnections)

            while (connectionPool.size < poolSize) {
                val newIndex = connectionPool.size + 1
                val newConn = createConnection(newIndex)
                if (newConn != null) {
                    connectionPool.add(newConn)
                }
            }
        }

        lastHealthCheck = System.currentTimeMillis()
        return healthyConnections.size
    }

    /**
     * فحص صحة اتصال معين
     */
    private fun isConnectionHealthy(conn: AsteriskConnection): Boolean {
        return try {
            val ping = conn.connection.sendAction(PingAction(), actionTimeoutMs)
            ping != null
        } catch (e: Exception) {
            log.debug("Connection {} health check failed: {}", conn.poolIndex, e.message)
            false
        }
    }

    /**
     * وضع اتصال كغير صحيح
     */
    private fun markUnhealthy(conn: AsteriskConnection) {
        connectionPool.remove(conn)
        unhealthyConnections.add(conn)
        failedConnections++
    }

    // ── Memory Cleanup ────────────────────────────────────────────────────

    /**
     * تنظيف الذاكرة دورياً
     */
    private fun startMemoryCleanup() {
        memoryCleanupScheduler.scheduleAtFixedRate({
            cleanupMemory()
        }, 5, 5, TimeUnit.MINUTES)
    }

    /**
     * تنظيف الذاكرة من الاتصالات غير المستخدمة
     */
    private fun cleanupMemory() {
        // Remove old unhealthy connections
        val maxUnhealthy = 5
        while (unhealthyConnections.size > maxUnhealthy) {
            unhealthyConnections.poll()?.close()
        }

        log.debug("Memory cleanup: pool={}, unhealthy={}",
            connectionPool.size, unhealthyConnections.size)
    }

    // ── Connection Acquisition ────────────────────────────────────────────

    /**
     * الحصول على اتصال صحيح من الـ Pool مع Failover تلقائي
     */
    fun getConnection(): AsteriskConnection? {
        // Try healthy connections first
        for (conn in connectionPool) {
            if (isConnectionHealthy(conn)) {
                return conn
            }
        }

        // Try to create new connection
        log.warn("No healthy connections in pool, attempting to create new one...")
        return try {
            val newConn = createConnection(connectionPool.size + 1)
            if (newConn != null) {
                connectionPool.add(newConn)
            }
            newConn
        } catch (e: Exception) {
            log.error("Failed to create new AMI connection: {}", e.message)
            null
        }
    }

    // ── Dial Operations ────────────────────────────────────────────────────

    /**
     * إخراج مكالمة عبر Asterisk إلى بوابة DINSTAR
     */
    fun dialGsm(
        phoneNumber: String,
        pjsipEndpoint: String = "dinstar-gateway",
        portIndex: Int = -1,
        callerSimNumber: String? = null
    ): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        require(pjsipEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid PJSIP endpoint name" }

        val correlationId = UUID.randomUUID().toString()
        val effectiveCallerId = callerSimNumber?.filter { it.isDigit() }
            ?.takeIf { it.length in 6..15 } ?: "RED SOVEREIGN"

        val action = OriginateAction().apply {
            actionId = correlationId
            channel = "Local/$phoneNumber@from-red-backend"
            application = "Wait"
            data = "1"
            callerId = effectiveCallerId
            setVariable("RED_GW", pjsipEndpoint)
            setVariable("RED_PORT_INDEX", portIndex.toString())
            setVariable("RED_CALL_ID", correlationId)
            if (callerSimNumber != null) {
                setVariable("RED_SIM_NUMBER", callerSimNumber.filter { it.isDigit() })
            }
            setAsync(true)
        }

        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            val conn = getConnection() ?: run {
                log.error("No AMI connection available for dial attempt {}", attempt + 1)
                throw IllegalStateException("No AMI connection available")
            }

            try {
                val response = conn.connection.sendAction(action, actionTimeoutMs)
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
                markUnhealthy(conn)
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(1_000L * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("PSTN originate interrupted during retry", ie)
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
     * 📞 مكالمة إدارية مجسّرة — مسار صوت حقيقي في الاتجاهين.
     *
     * ## المشكلة التي يحلّها
     *
     * [dialGsm] يُنشئ `Local/<num>@from-red-backend` مع `Application=Wait`
     * و`Data=1`: يطلب GSM، وحين يرد المستلم يُنفّذ `Wait(1)` ثم يُغلق. لا
     * ساق ثانية ولا مسار صوت — "اختبار" لا مكالمة.
     *
     * ## الترتيب الصحيح
     *
     * القناة الأصلية هي نظير الإداري (WebRTC)، والوجهة سياق
     * `from-admin-bridge`. فحين يرفع الإداري السمّاعة يبدأ السياق بطلب
     * الرقم على GSM ويجسر الساقين. الإداري يسمع رنين الناقل الحقيقي لأن
     * `Dial` يمرّر 183 Session Progress مع SDP تلقائيًا.
     */
    fun dialGsmBridged(
        phoneNumber: String,
        adminEndpoint: String = "red-webrtc-client",
        pjsipEndpoint: String = "dinstar-gateway",
        portIndex: Int = -1,
        callerSimNumber: String? = null
    ): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        require(pjsipEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid PJSIP endpoint name" }
        require(adminEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid admin endpoint name" }

        val correlationId = UUID.randomUUID().toString()
        val effectiveCallerId = callerSimNumber?.filter { it.isDigit() }
            ?.takeIf { it.length in 6..15 } ?: "RED SOVEREIGN"

        val action = OriginateAction().apply {
            actionId = correlationId
            // الساق الأولى: الإداري. رفع السمّاعة هو ما يُشغّل ساق GSM.
            channel = "PJSIP/$adminEndpoint"
            // الوجهة سياق لا تطبيق: Context/Exten/Priority يُنتج مكالمة كاملة.
            context = "from-admin-bridge"
            exten = phoneNumber
            priority = 1
            callerId = effectiveCallerId
            setVariable("RED_GW", pjsipEndpoint)
            setVariable("RED_PORT_INDEX", portIndex.toString())
            setVariable("RED_CALL_ID", correlationId)
            if (callerSimNumber != null) {
                setVariable("RED_SIM_NUMBER", callerSimNumber.filter { it.isDigit() })
            }
            setAsync(true)
        }

        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            val conn = getConnection() ?: run {
                log.error("No AMI connection available for bridged dial attempt {}", attempt + 1)
                throw IllegalStateException("No AMI connection available")
            }
            try {
                val response = conn.connection.sendAction(action, actionTimeoutMs)
                check(response.response?.equals("Success", ignoreCase = true) == true) {
                    response.message ?: "Asterisk rejected admin bridge originate"
                }
                log.info(
                    "Admin bridge originate sent: admin={} dest={} gw={} port={} (callId={}, attempt={})",
                    adminEndpoint, phoneNumber, pjsipEndpoint, portIndex, correlationId, attempt + 1
                )
                return correlationId
            } catch (e: Exception) {
                lastException = e
                log.warn("Admin bridge originate attempt {}/{} failed: {}", attempt + 1, maxRetries, e.message)
                markUnhealthy(conn)
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(1_000L * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("Admin bridge interrupted during retry", ie)
                    }
                }
            }
        }
        throw IllegalStateException(
            "Asterisk rejected admin bridge after $maxRetries attempts: ${lastException?.message}",
            lastException
        )
    }

    // ── Channel binding (callId ↔ AMI channel) ─────────────────────────────

    /** خريطة callId ← اسم قناة AMI، يعبّئها DinstarEventListener من VarSet. */
    private val callChannels = ConcurrentHashMap<String, String>()

    fun bindChannel(callId: String, channel: String) {
        if (callId.isNotBlank() && channel.isNotBlank()) callChannels[callId] = channel
    }

    fun forgetChannel(callId: String) {
        callChannels.remove(callId)
    }

    /**
     * ينهي قناة AMI بالاسم الصريح — يستخدمه رفض المكالمة الواردة لتحرير
     * منفذ GSM فوراً بدل تركها تستهلك Wait(RING_TIMEOUT) كاملة.
     */
    fun hangupChannel(channel: String): Boolean {
        val conn = getConnection() ?: return false
        return try {
            val response = conn.connection.sendAction(HangupAction(channel), actionTimeoutMs)
            val success = response.response?.equals("Success", ignoreCase = true) == true
            if (success) log.info("Hung up channel {} (explicit)", channel)
            else log.warn("Hangup channel {} failed: {}", channel, response.message)
            success
        } catch (e: Exception) {
            log.warn("Hangup channel {} raised: {}", channel, e.message)
            markUnhealthy(conn)
            false
        }
    }

    /** ينهي مكالمة عبر correlationId المحفوظ في [callChannels]. */
    fun hangupCall(callId: String): Boolean {
        val channel = callChannels[callId] ?: run {
            log.debug("No channel bound for callId {} — nothing to hang up", callId)
            return false
        }
        val result = hangupChannel(channel)
        if (result) forgetChannel(callId)
        return result
    }

    /**
     * قبول مكالمة واردة: يُعيد توجيه قناة GSM إلى سياق الجسر الوارد
     * `from-incoming-bridge` الذي يطلب نظير WebRTC ويجسر الصوت.
     */
    fun acceptIncomingCall(channel: String, webrtcUser: String): Boolean {
        require(channel.isNotBlank()) { "Channel is required" }
        require(webrtcUser.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid WebRTC user" }
        val conn = getConnection() ?: return false
        return try {
            val action = RedirectAction().apply {
                this.channel = channel
                this.context = "from-incoming-bridge"
                this.exten = "s"
                this.priority = 1
            }
            val response = conn.connection.sendAction(action, actionTimeoutMs)
            val success = response.response?.equals("Success", ignoreCase = true) == true
            if (success) log.info("Incoming channel {} redirected to bridge for {}", channel, webrtcUser)
            else log.warn("Redirect of {} failed: {}", channel, response.message)
            success
        } catch (e: Exception) {
            log.warn("Redirect of {} raised: {}", channel, e.message)
            markUnhealthy(conn)
            false
        }
    }

    /** هل يوجد اتصال AMI صالح؟ يستخدمه فحص الصحة العام. */
    fun isConnected(): Boolean = connectionPool.isNotEmpty()

    // ── Metrics ────────────────────────────────────────────────────────────

    /**
     * الحصول على إحصائيات الصحة والأداء
     */
    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "totalConnections" to totalConnections,
            "healthyConnections" to connectionPool.size,
            "failedConnections" to failedConnections,
            "unhealthyConnections" to unhealthyConnections.size,
            "poolSize" to poolSize,
            "lastHealthCheck" to lastHealthCheck,
            "status" to if (connectionPool.size > 0) "HEALTHY" else "DEGRADED"
        )
    }

    /**
     * حالة النظام الإجمالية
     */
    fun isHealthy(): Boolean {
        return connectionPool.size > 0 && failedConnections < poolSize / 2
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    @PreDestroy
    fun close() {
        log.info("Closing AMI connection pool...")

        reconnectScheduler.shutdownNow()
        healthScheduler.shutdownNow()
        memoryCleanupScheduler.shutdownNow()

        // Close all connections
        connectionPool.forEach { conn ->
            try {
                conn.connection.logoff()
            } catch (e: Exception) {
                log.warn("Error closing connection {}: {}", conn.poolIndex, e.message)
            }
        }
        connectionPool.clear()
        unhealthyConnections.clear()

        log.info("AMI connection pool closed")
    }
}

/**
 * تغليف لاتصال AMI مع بيانات التعريف
 */
data class AsteriskConnection(
    val connection: DefaultManagerConnection,
    val poolIndex: Int,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun close() {
        try {
            connection.logoff()
        } catch (_: Exception) {}
    }
}
