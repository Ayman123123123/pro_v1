package com.red.server

import com.red.server.auth.security.WebSocketTicketService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.UUID

class WebSocketTicketServiceTest {
    private val redis: StringRedisTemplate = mock()
    private val values: ValueOperations<String, String> = mock()
    private val service = WebSocketTicketService(redis)

    init { whenever(redis.opsForValue()).thenReturn(values) }

    @Test
    fun `issues random 256-bit tickets with a thirty second lifetime`() {
        val account = UUID.randomUUID()
        val first = service.issue(account)
        val second = service.issue(account)
        assertEquals(30, first.expiresInSeconds)
        assertEquals(43, first.ticket.length)
        assertTrue(first.ticket.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertNotEquals(first.ticket, second.ticket)
        verify(values).set(eq("younes:ws-ticket:${first.ticket}"), eq(account.toString()), eq(Duration.ofSeconds(30)))
    }

    @Test
    fun `consumes valid ticket exactly through atomic get and delete`() {
        val account = UUID.randomUUID()
        val ticket = "A".repeat(43)
        whenever(values.getAndDelete("younes:ws-ticket:$ticket")).thenReturn(account.toString())
        assertEquals(account, service.consume(ticket))
        verify(values).getAndDelete("younes:ws-ticket:$ticket")
    }

    @Test
    fun `rejects malformed tickets without touching redis`() {
        assertNull(service.consume("not-a-ticket"))
        verify(values, org.mockito.kotlin.never()).getAndDelete(any())
    }
}
