package com.red.server.auth.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID

/** One-time, short-lived browser WebSocket authorization tickets. */
@Service
class WebSocketTicketService(private val redis: StringRedisTemplate) {
    private val random = SecureRandom()

    // سكربت Lua ذرّي: GET + DELETE في عملية واحدة — يمنع استخدام التذكرة مرتين
    private val consumeScript: DefaultRedisScript<String> = DefaultRedisScript<String>().apply {
        setScriptText(
            """
            local val = redis.call('GET', KEYS[1])
            if val then
                redis.call('DEL', KEYS[1])
                return val
            end
            return nil
            """.trimIndent()
        )
        resultType = String::class.java
    }

    fun issue(accountId: UUID): WebSocketTicket {
        val bytes = ByteArray(32).also(random::nextBytes)
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        redis.opsForValue().set(key(ticket), accountId.toString(), TTL)
        return WebSocketTicket(ticket, TTL.seconds)
    }

    fun consume(ticket: String): UUID? {
        if (!ticket.matches(TICKET_PATTERN)) return null
        val result = redis.execute(consumeScript, listOf(key(ticket)))
        return result?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private fun key(ticket: String) = "younes:ws-ticket:$ticket"

    companion object {
        private val TTL = Duration.ofSeconds(30)
        private val TICKET_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

data class WebSocketTicket(val ticket: String, val expiresInSeconds: Long)
