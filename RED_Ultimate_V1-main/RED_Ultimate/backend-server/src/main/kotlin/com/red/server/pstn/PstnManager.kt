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
 * Ù…Ø¯ÙŠØ± Ø§ØªØµØ§Ù„ Asterisk AMI.
 *
 * ## ØªØ­Ø³ÙŠÙ†Ø§Øª Ù‡Ø°Ù‡ Ø§Ù„Ù†Ø³Ø®Ø©
 *
 * 1. **Heartbeat Ø­Ù‚ÙŠÙ‚ÙŠ**: `@Scheduled` ÙƒÙ„ 30 Ø«Ø§Ù†ÙŠØ© ÙŠÙØ±Ø³Ù„ `PingAction`
 *    Ù„Ù„ØªØ­Ù‚Ù‚ Ø§Ù„ÙØ¹Ù„ÙŠ Ù…Ù† Ø§Ù„Ø§ØªØµØ§Ù„ â€” Ù„Ø§ ÙŠÙƒØªÙÙŠ Ø¨ÙˆØ¬ÙˆØ¯ Ø§Ù„ÙƒØ§Ø¦Ù† (connection != null).
 *
 * 2. **Ø¥Ø¹Ø§Ø¯Ø© Ø§ØªØµØ§Ù„ ØªÙ„Ù‚Ø§Ø¦ÙŠØ© Ù…Ø¹ Exponential Backoff**:
 *    - Ø§ÙƒØªØ´Ø§Ù Ø§Ù†Ù‚Ø·Ø§Ø¹ Heartbeat â†’ Ø¥Ù„ØºØ§Ø¡ Ø§Ù„Ø§ØªØµØ§Ù„ Ø§Ù„Ø­Ø§Ù„ÙŠ â†’ Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„Ù…Ø­Ø§ÙˆÙ„Ø©
 *    - Ø­Ø¯ Ø£Ù‚ØµÙ‰ 60 Ø«Ø§Ù†ÙŠØ© Ø¨ÙŠÙ† Ø§Ù„Ù…Ø­Ø§ÙˆÙ„Ø§Øª
 *
 * 3. **Timeout Ø­Ù‚ÙŠÙ‚ÙŠ Ù„Ù„Ù€ sendAction**: ÙŠÙ…Ù†Ø¹ ØªØ¹Ù„ÙŠÙ‚ Ø§Ù„Ø®ÙŠØ· Ù„Ù„Ø£Ø¨Ø¯.
 *
 * 4. **@PostConstruct**: ÙŠØªØµÙ„ Ø¹Ù†Ø¯ Ø¨Ø¯Ø¡ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ Ø¨Ø¯Ù„ Ø§Ù„Ø§Ù†ØªØ¸Ø§Ø± Ø­ØªÙ‰ Ø£ÙˆÙ„ Ù…ÙƒØ§Ù„Ù…Ø©.
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
    /** Ø¢Ø®Ø± Ù‚Ù†Ø§Ø© AMI ÙØ¹Ù„ÙŠØ© Ù„ÙƒÙ„ callIdØŒ ÙŠØ±Ø¨Ø·Ù‡Ø§ DinstarEventListener Ø¹Ù†Ø¯ VarSet. */
    private val callChannels = ConcurrentHashMap<String, String>()

    // â”€â”€ Startup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @PostConstruct
    fun startup() {
        if (amiPassword.isBlank()) {
            log.warn("ASTERISK_AMI_PASSWORD not configured â€” PSTN disabled")
            return
        }
        try {
            ensureConnected()
        } catch (e: Exception) {
            log.warn("Initial AMI connection failed ({}), will retry via heartbeat", e.message)
        }
    }

    // â”€â”€ Heartbeat â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Heartbeat scheduler â€” ÙŠÙØ´ØºÙŽÙ‘Ù„ Ù…Ù† Spring `@EnableScheduling`.
     * ÙŠÙØ±Ø³Ù„ Ping Ø¥Ù„Ù‰ Asterisk AMI ÙˆÙŠÙƒØªØ´Ù Ø§Ù„Ø§Ù†Ù‚Ø·Ø§Ø¹ Ø§Ù„ÙØ¹Ù„ÙŠ.
     *
     * Ù„Ù† ÙŠÙØ´ØºÙŽÙ‘Ù„ Ø¥Ø°Ø§ Ù„Ù… ØªÙØ¶ÙŽÙ `@EnableScheduling` Ø¹Ù„Ù‰ Configuration.
     */
    @Scheduled(fixedDelayString = "\${red.pstn.heartbeat-interval-ms:30000}")
    fun heartbeat() {
        val conn = connection ?: run {
            log.debug("AMI connection null â€” attempting reconnect")
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
            log.error("AMI connection appears dead â€” forcing reconnect")
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

    // â”€â”€ Connection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun ensureConnected(): DefaultManagerConnection {
        connection?.let { existing ->
            return existing  // Fast path (no lock)
        }
        return connectionLock.withLock {
            connection?.let { return it }

            require(amiPassword.isNotBlank()) { "ASTERISK_AMI_PASSWORD must be configured" }
            log.info("Connecting to Asterisk AMI at {}...", amiHost)

            val conn = DefaultManagerConnection(amiHost, amiUser, amiPassword).apply {
                // asterisk-java ÙŠØ¹Ø±Ù‘Ù Ù‡Ø°Ù‡ ÙƒÙ€ setter Ø¨Ù„Ø§ getter â€” ØªÙØ³ØªØ¯Ø¹Ù‰ ÙƒØ¯ÙˆØ§Ù„ Ù„Ø§ ÙƒØ®ØµØ§Ø¦Øµ ÙÙŠ Kotlin
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

    // â”€â”€ Dial â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Ø¥Ø®Ø±Ø§Ø¬ Ù…ÙƒØ§Ù„Ù…Ø© Ø¹Ø¨Ø± Asterisk Ø¥Ù„Ù‰ Ø¨ÙˆØ§Ø¨Ø© DINSTAR.
     *
     * @param phoneNumber Ø±Ù‚Ù… Ø§Ù„ÙˆØ¬Ù‡Ø© â€” Ù…ÙØªØ­Ù‚ÙŽÙ‘Ù‚ Ù…Ù†Ù‡ (Ø£Ø±Ù‚Ø§Ù… ÙÙ‚Ø· + Ø¹Ù„Ø§Ù…Ø© Ø¯ÙˆÙ„ÙŠØ©)
     * @param pjsipEndpoint Ø§Ø³Ù… Ù†Ø¸ÙŠØ± PJSIP â€” Ù…ÙØªØ­Ù‚ÙŽÙ‘Ù‚ Ù…Ù†Ù‡ Ø¶Ø¯ Ø§Ù„Ø­Ù‚Ù†
     * @param portIndex ÙÙ‡Ø±Ø³ Ø§Ù„Ù…Ù†ÙØ° Ø¯Ø§Ø®Ù„ Ø§Ù„Ø¨ÙˆØ§Ø¨Ø© Ø§Ù„Ø§Ø®ØªÙŠØ§Ø±ÙŠØ© â€” ÙŠÙØ³ØªØ®Ø¯Ù… Ù„ØªÙˆØ¬ÙŠÙ‡
     *                  Ø§Ù„Ø§ØªØµØ§Ù„ Ø¥Ù„Ù‰ Ø´Ø±ÙŠØ­Ø© SIM Ù…Ø­Ø¯Ø¯Ø©. -1 ÙŠØ¹Ù†ÙŠ Ø§Ù„ØªÙ„Ù‚Ø§Ø¦ÙŠ.
     * @return correlationId ÙŠÙØ³ØªØ®Ø¯ÙŽÙ… ÙƒÙ€ callId ÙÙŠ Ù‚Ø§Ø¹Ø¯Ø© Ø§Ù„Ø¨ÙŠØ§Ù†Ø§Øª
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
        // Ø¥Ø¸Ù‡Ø§Ø± Ø±Ù‚Ù… Ø§Ù„Ø´Ø±ÙŠØ­Ø© Ø§Ù„Ø­Ù‚ÙŠÙ‚ÙŠ Ù„Ù„Ù…Ø³ØªÙ„Ù… Ø§Ù„Ø®Ø§Ø±Ø¬ÙŠ â€” Ø¨Ø¯Ù„ "RED SOVEREIGN" Ø§Ù„Ø¹Ø§Ù…
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
            // Ø±Ù‚Ù… Ø§Ù„Ø´Ø±ÙŠØ­Ø© Ø§Ù„Ø­Ù‚ÙŠÙ‚ÙŠ â€” ÙŠØ³ØªØ®Ø¯Ù…Ù‡ extensions.conf Ù„Ø¶Ø¨Ø· CALLERID(num)
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

    /** ÙŠØ³Ø¬Ù„ DinstarEventListener Ø§Ù„Ù‚Ù†Ø§Ø© Ø§Ù„ÙØ¹Ù„ÙŠØ© Ø§Ù„ØªÙŠ Ø£Ù†Ø´Ø£Ù‡Ø§ Asterisk Ù„ÙƒÙ„ callId. */

    /**
     * مكالمة تعلّم رقم (Phone Number Learning — Call mode):
     * originate عبر الترنك الافتراضي ثم إنهاء تلقائي بعد [waitSeconds].
     * الغرض أن تُصدر الشريحة اتصالاً حقيقياً فيلتقط الجهاز CLIP الرقم.
     */
    @Throws(IllegalStateException::class)
    fun dialGsm(phoneNumber: String, waitSeconds: Int): String {
        val correlationId = dialGsm(phoneNumber)
        val safeWait = waitSeconds.coerceIn(5, 300)
        learningExecutor.schedule({
            runCatching { hangupCall(correlationId) }
                .onFailure { log.warn("Learning call {} auto-hangup failed: {}", correlationId, it.message) }
        }, safeWait.toLong(), TimeUnit.SECONDS)
        log.info("Learning call {} scheduled auto-hangup in {}s", correlationId, safeWait)
        return correlationId
    }
    fun bindChannel(callId: String, channel: String) {
        if (callId.isNotBlank() && channel.isNotBlank()) callChannels[callId] = channel
    }

    fun forgetChannel(callId: String) {
        callChannels.remove(callId)
    }

    /**
     * ÙŠÙ†Ù‡ÙŠ Ù‚Ù†Ø§Ø© AMI Ø¨Ø§Ù„Ø§Ø³Ù… Ø§Ù„ØµØ±ÙŠØ­ â€” ÙŠØ³ØªØ®Ø¯Ù…Ù‡ Ø±ÙØ¶ Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„ÙˆØ§Ø±Ø¯Ø© Ù„ØªØ­Ø±ÙŠØ±
     * Ù…Ù†ÙØ° GSM ÙÙˆØ±Ø§Ù‹ Ø¨Ø¯Ù„ ØªØ±ÙƒÙ‡Ø§ ØªØ³ØªÙ‡Ù„Ùƒ Wait(RING_TIMEOUT) ÙƒØ§Ù…Ù„Ø©.
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
     * ÙŠÙ†Ù‡ÙŠ Ù…ÙƒØ§Ù„Ù…Ø© PSTN Ø¨Ø§Ø³ØªØ¹Ù…Ø§Ù„ callId Ø§Ù„Ù…ÙˆØ«Ù‚ ÙÙŠ Redis/Ø§Ù„Ù…ØªØ­ÙƒÙ…. Ù„Ø§ ÙŠØ³ØªØ®Ø¯Ù…
     * CoreShowChannels Ù„Ø£Ù† Ø¥ØµØ¯Ø§Ø± Asterisk-Java Ø§Ù„Ø­Ø§Ù„ÙŠ Ù„Ø§ ÙŠØ¹ÙŠØ¯ Ù‚Ø§Ø¦Ù…Ø© Ø£Ø­Ø¯Ø§Ø«
     * Ø¹Ø¨Ø± sendActionØ› Ø§Ù„Ù‚Ù†Ø§Ø© ØªÙÙ„ØªÙ‚Ø· Ø¹Ù†Ø¯ VarSet(RED_CALL_ID).
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
     * Ù‡Ù„ Ø§Ù„Ø§ØªØµØ§Ù„ Ø¨Ù€ Asterisk Ø³Ù„ÙŠÙ…ØŸ ÙŠÙØ³ØªØ®Ø¯ÙŽÙ… ÙÙŠ health endpoint.
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
