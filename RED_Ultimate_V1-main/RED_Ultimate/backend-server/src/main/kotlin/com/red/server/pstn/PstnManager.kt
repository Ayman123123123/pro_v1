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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
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
    @Value("\${red.pstn.heartbeat-interval-ms:30000}") private val heartbeatIntervalMs: Long,
    private val dinstarEvents: ObjectProvider<DinstarEventListener>
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnManager::class.java)
        private val RECONNECT_DELAYS = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000, 60_000)
    }

    private val learningExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "pstn-learning").apply { isDaemon = true } }
    private val connectionLock = ReentrantLock()
    @Volatile private var connection: DefaultManagerConnection? = null
    @Volatile private var consecutiveHeartbeatFailures = 0
    private var reconnectAttempt = 0

    private val reconnectScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "pstn-ami-reconnect").apply { isDaemon = true }
        }
        @Volatile private var reconnectFuture: ScheduledFuture<*>? = null
    /** آخر قناة AMI فعلية لكل callId، يربطها DinstarEventListener عند VarSet. */
    private val callChannels = ConcurrentHashMap<String, String>()

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
                // Read timeout must far exceed the heartbeat interval.
                // asterisk-java's ManagerReaderImpl has its own SO_TIMEOUT which causes
                // "Read timed out" disconnects. Setting this to 5 minutes gives our
                // heartbeat plenty of headroom to keep the connection alive.
                setSocketReadTimeout(300_000)
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
        portIndex: Int = -1,
        callerSimNumber: String? = null
    ): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        require(pjsipEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid PJSIP endpoint name" }

        val correlationId = UUID.randomUUID().toString()
        // إظهار رقم الشريحة الحقيقي للمستلم الخارجي — بدل "RED SOVEREIGN" العام
        val effectiveCallerId = callerSimNumber?.filter { it.isDigit() }?.takeIf { it.length in 6..15 } ?: "RED SOVEREIGN"
        val action = OriginateAction().apply {
            actionId = correlationId
            channel = "Local/$phoneNumber@from-red-backend"
            application = "Wait"
            data = "1"
            callerId = effectiveCallerId
            // Pass selected gateway to dialplan
            setVariable("RED_GW", pjsipEndpoint)
            // Expose the selected SIM/port slot for gateway dialplan branching
            setVariable("RED_PORT_INDEX", portIndex.toString())
            // Expose correlationId for DinstarEventListener binding
            setVariable("RED_CALL_ID", correlationId)
            // رقم الشريحة الحقيقي — يستخدمه extensions.conf لضبط CALLERID(num)
            if (callerSimNumber != null) {
                setVariable("RED_SIM_NUMBER", callerSimNumber.filter { it.isDigit() })
            }
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
     * مكالمة تعلّم رقم (Phone Number Learning — Call mode):
     * originate عبر الترنك الافتراضي ثم إنهاء تلقائي بعد [waitSeconds].
     * الغرض أن تُصدر الشريحة اتصالاً حقيقياً فيلتقط الجهاز CLIP الرقم.
     */
    @Throws(IllegalStateException::class)
    fun dialGsm(phoneNumber: String, waitSeconds: Int): String {
        val correlationId = dialGsm(phoneNumber)
        // السماح بمدة رنين قصيرة جداً (ثانية واحدة) لضمان عدم الرد وسحب الرصيد
        val safeWait = waitSeconds.coerceIn(1, 300)
        learningExecutor.schedule({
            runCatching { hangupCall(correlationId) }
                .onFailure { log.warn("Learning call {} auto-hangup failed: {}", correlationId, it.message) }
        }, safeWait.toLong(), TimeUnit.SECONDS)
        log.info("Learning call {} scheduled auto-hangup in {}s", correlationId, safeWait)
        return correlationId
    }

    /**
     * 📞 مكالمة إدارية مجسّرة — مسار صوت حقيقي في الاتجاهين.
     *
     * ## المشكلة التي يحلّها
     *
     * [dialGsm] يُنشئ `Local/<num>@from-red-backend` مع `Application=Wait`
     * و`Data=1`: يطلب GSM، وحين يرد المستلم يُنفّذ `Wait(1)` ثم يُغلق. لا
     * ساق ثانية ولا مسار صوت — "اختبار" لا مكالمة. لذلك كانت لوحة الإدارة
     * تُبلّغ نجاحًا بينما لا أحد يسمع أحدًا.
     *
     * ## الترتيب الصحيح
     *
     * القناة الأصلية هي نظير الإداري (WebRTC)، والوجهة هي سياق
     * `from-admin-bridge`. فحين يرفع الإداري السمّاعة يبدأ السياق بطلب
     * الرقم على GSM ويجسر الساقين. الإداري يسمع رنين الناقل الحقيقي لأن
     * `Dial` يمرّر 183 Session Progress مع SDP تلقائيًا — لا نغمة مُولَّدة.
     *
     * @param phoneNumber رقم الوجهة على شبكة GSM
     * @param adminEndpoint نظير PJSIP للإداري (WebRTC) الذي يُطلب أولًا
     * @param pjsipEndpoint بوابة DINSTAR — نفس عقد [dialGsm]
     * @param portIndex فهرس المنفذ داخل البوابة، ‎-1 يعني التلقائي
     * @param callerSimNumber رقم الشريحة الذي يظهر للمستلم الخارجي
     * @return correlationId للربط بسجل قاعدة البيانات وأحداث AMI
     */
    @Throws(IllegalStateException::class)
    fun dialGsmBridged(
        phoneNumber: String,
        adminEndpoint: String = "red-webrtc-client",
        pjsipEndpoint: String = "dinstar-gateway",
        portIndex: Int = -1,
        callerSimNumber: String? = null
    ): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        require(pjsipEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid PJSIP endpoint name" }
        // نفس حارس الحقن: اسم النظير يدخل قناة AMI مباشرةً
        require(adminEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid admin endpoint name" }

        val correlationId = UUID.randomUUID().toString()
        val effectiveCallerId = callerSimNumber?.filter { it.isDigit() }
            ?.takeIf { it.length in 6..15 } ?: "RED SOVEREIGN"

        val action = OriginateAction().apply {
            actionId = correlationId
            // الساق الأولى: الإداري. لا Local channel هنا — نطلب النظير مباشرةً
            // حتى يكون رفع السمّاعة هو ما يُشغّل ساق GSM، لا العكس.
            channel = "PJSIP/$adminEndpoint"
            // الوجهة سياق لا تطبيق: Context/Exten/Priority يُنتج مكالمة كاملة،
            // بينما Application=Wait يُنتج قناة معلّقة تُغلق نفسها.
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
            try {
                val conn = ensureConnected()
                val response = conn.sendAction(action, actionTimeoutMs)
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
                connectionLock.withLock { connection = null }
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(1_000L * (attempt + 1))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IllegalStateException("Admin bridge interrupted during retry backoff", ie)
                    }
                }
            }
        }
        throw IllegalStateException(
            "Asterisk rejected admin bridge after $maxRetries attempts: ${lastException?.message}",
            lastException
        )
    }

    fun bindChannel(callId: String, channel: String) {        if (callId.isNotBlank() && channel.isNotBlank()) callChannels[callId] = channel
    }

    fun forgetChannel(callId: String) {
        callChannels.remove(callId)
    }

    /**
     * ينهي قناة AMI بالاسم الصريح — يستخدمه رفض المكالمة الواردة لتحرير
     * منفذ GSM فوراً بدل تركها تستهلك Wait(RING_TIMEOUT) كاملة.
     */
    fun hangupChannel(channel: String): Boolean = try {
        val response = ensureConnected().sendAction(HangupAction(channel), actionTimeoutMs)
        val success = response.response?.equals("Success", ignoreCase = true) == true
        if (success) log.info("Hung up channel {} (explicit)", channel)
        else log.warn("Hangup channel {} failed: {}", channel, response.message)
        success
    } catch (e: Exception) {
        log.error("Error hanging up channel {}: {}", channel, e.message)
        false
    }

    /**
     * ينهي مكالمة PSTN باستعمال callId الموثق في Redis/المتحكم. لا يستخدم
     * CoreShowChannels لأن إصدار Asterisk-Java الحالي لا يعيد قائمة أحداث
     * عبر sendAction؛ القناة تُلتقط عند VarSet(RED_CALL_ID).
     */
    fun hangupCall(callId: String): Boolean {
        val targetChannel = callChannels[callId]
        if (targetChannel.isNullOrBlank()) {
            log.warn("No tracked AMI channel found for PSTN call {}", callId)
            return false
        }
        return try {
            val response = ensureConnected().sendAction(HangupAction(targetChannel), actionTimeoutMs)
            val success = response.response?.equals("Success", ignoreCase = true) == true
            if (success) {
                callChannels.remove(callId)
                log.info("Hung up PSTN channel {} for call {}", targetChannel, callId)
            } else {
                log.warn("Failed to hangup PSTN channel {}: {}", targetChannel, response.message)
            }
            success
        } catch (e: Exception) {
            log.error("Error hanging up PSTN call {}: {}", callId, e.message)
            false
        }
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

    // Incoming PSTN call control, invoked by PstnEventWebSocketHandler.

    /**
     * Accept an incoming PSTN call by bridging the DINSTAR channel to the WebRTC client.
     * Uses AMI RedirectAction to move the channel from from-dinstar to from-incoming-bridge
     * which dials the red-webrtc-client endpoint, sending a SIP INVITE to the app.
     */
    fun acceptIncomingCall(channel: String, webrtcUser: String): Boolean {
        return try {
            val conn = ensureConnected()
            // Redirect the incoming DINSTAR channel to the incoming-bridge context
            // which will dial the red-webrtc-client endpoint via exten 's'
            val action = RedirectAction().apply {
                setChannel(channel)
                setContext("from-incoming-bridge")
                setExten("s")
                setPriority(1)
            }
            val response = conn.sendAction(action, actionTimeoutMs)
            val success = response.response?.equals("Success", ignoreCase = true) == true
            if (success) {
                log.info("Redirected incoming channel {} to from-incoming-bridge for {}", channel, webrtcUser)
            } else {
                log.warn("Failed to redirect channel {}: {}", channel, response.message)
            }
            success
        } catch (e: Exception) {
            log.error("Error accepting incoming call on channel {}: {}", channel, e.message)
            false
        }
    }

    /**
     * Reject an incoming PSTN call by hanging up the DINSTAR channel.
     */
    fun rejectIncomingCall(channel: String): Boolean {
        return try {
            val conn = ensureConnected()
            val action = HangupAction(channel)
            val response = conn.sendAction(action, actionTimeoutMs)
            val success = response.response?.equals("Success", ignoreCase = true) == true
            if (success) {
                log.info("Hung up incoming channel {}", channel)
            } else {
                log.warn("Failed to hangup channel {}: {}", channel, response.message)
            }
            success
        } catch (e: Exception) {
            log.error("Error rejecting incoming call on channel {}: {}", channel, e.message)
            false
        }
    }
}
