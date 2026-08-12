package com.red.server.pstn

import jakarta.annotation.PreDestroy
import org.asteriskjava.manager.DefaultManagerConnection
import org.asteriskjava.manager.action.OriginateAction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class PstnManager(
    @Value("\${ASTERISK_AMI_HOST:red-pstn-gateway}") private val amiHost: String,
    @Value("\${ASTERISK_AMI_USER:red_admin}") private val amiUser: String,
    @Value("\${ASTERISK_AMI_PASSWORD:}") private val amiPassword: String,
    @Value("\${red.pstn.max-retries:3}") private val maxRetries: Int,
    private val dinstarEvents: ObjectProvider<DinstarEventListener>
) {
    companion object { private val log = LoggerFactory.getLogger(PstnManager::class.java) }

    private val connectionLock = ReentrantLock()
    @Volatile private var connection: DefaultManagerConnection? = null

    private fun ensureConnected(): DefaultManagerConnection {
        connection?.let { connected ->
            try {
                // Just check if connection object exists and is usable
                return connected
            } catch (e: Exception) {
                // Ignore, will reconnect
            }
        }
        return connectionLock.withLock {
            connection?.let { connected ->
                return connected
            }
            require(amiPassword.isNotBlank()) { "ASTERISK_AMI_PASSWORD must be configured" }
            log.info("Connecting to Asterisk AMI at {}...", amiHost)
            val conn = DefaultManagerConnection(amiHost, amiUser, amiPassword)
            conn.login()
            dinstarEvents.ifAvailable { listener ->
                conn.addEventListener(listener)
                log.info("Registered DinstarEventListener on AMI connection")
            }
            connection = conn
            log.info("AMI connection established")
            conn
        }
    }

    /**
     * إخراج مكالمة عبر Asterisk إلى بوابة DINSTAR.
     *
     * @param pjsipEndpoint اسم نظير PJSIP للبوابة المختارة. مع أكثر من
     *   جهاز في الأسطول لا يكفي سياق واحد ثابت: يُمرَّر النظير كمتغيّر
     *   قناة (`RED_GW`) ويقرؤه الـ dialplan في `from-red-backend`،
     *   فيخرج الاتصال من الجهاز الذي اختاره الموزّع فعلًا.
     */
    @JvmOverloads
    fun dialGsm(phoneNumber: String, pjsipEndpoint: String = "dinstar-gateway"): String {
        require(phoneNumber.matches(Regex("^\\+?[0-9]{6,15}$"))) { "Invalid phone number" }
        // اسم النظير يدخل سلسلة قناة Asterisk، فيجب ألا يحمل فواصل أو
        // محارف تحكم تسمح بحقن وجهة أخرى.
        require(pjsipEndpoint.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid PJSIP endpoint name" }
        val correlationId = UUID.randomUUID().toString()
        val action = OriginateAction().apply {
            actionId = correlationId
            channel = "Local/$phoneNumber@from-red-backend"
            application = "Wait"
            data = "1"
            callerId = "RED SOVEREIGN"
            setVariable("RED_GW", pjsipEndpoint)
            setAsync(true)
        }

        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val conn = ensureConnected()
                val response = conn.sendAction(action)
                check(response.response?.equals("Success", ignoreCase = true) == true) {
                    response.message ?: "Asterisk rejected originate action"
                }
                log.info("PSTN originate sent for {} via {} (actionId={}, attempt={})", phoneNumber, pjsipEndpoint, correlationId, attempt + 1)
                return correlationId
            } catch (e: Exception) {
                lastException = e
                log.warn("PSTN originate attempt {}/{} failed: {}", attempt + 1, maxRetries, e.message)
                // Reset connection for retry
                connectionLock.withLock { connection = null }
            }
        }
        throw IllegalStateException("Asterisk rejected PSTN call after $maxRetries attempts", lastException)
    }

    @PreDestroy
    fun close() {
        connectionLock.withLock {
            connection?.let { conn ->
                runCatching { conn.logoff() }.onSuccess { log.info("AMI connection closed") }
                connection = null
            }
        }
    }
}
