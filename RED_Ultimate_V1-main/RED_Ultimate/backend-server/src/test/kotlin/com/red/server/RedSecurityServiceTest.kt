package com.red.server

import com.red.server.services.RedSecurityService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID

/**
 * RedSecurityService هي ناشر Redis فقط: تبثّ الأمر على القناة ولا تملك
 * منطق الصلاحيات. التحقق من الأدمن ومنع مسح حسابات ADMIN يعيشان في
 * AdminUserIntelligenceService/Controller، ولهما اختباراتهما الخاصة.
 */
class RedSecurityServiceTest {
    private val redis = mock<RedisTemplate<String, String>>()
    private val values = mock<ValueOperations<String, String>>()
    private val service = RedSecurityService(redis)

    @Test
    fun `wipe signal is published on the security channel`() {
        val userId = UUID.randomUUID().toString()

        val result = service.sendWipeSignal(userId)

        assertEquals("SENT", result["status"])
        assertEquals(userId, result["userId"])
        assertEquals("WIPE", result["action"])
        verify(redis).convertAndSend("security:wipe", userId)
    }

    @Test
    fun `kill switch is published with its reason`() {
        val result = service.activateKillSwitch("LOST_DEVICE")

        assertEquals("ACTIVATED", result["status"])
        assertEquals("LOST_DEVICE", result["reason"])
        verify(redis).convertAndSend("security:kill-switch", "LOST_DEVICE")
    }

    @Test
    fun `device status reports blocked state from redis`() {
        val userId = UUID.randomUUID().toString()
        whenever(redis.opsForValue()).thenReturn(values)
        whenever(values.get("security:last-seen:$userId")).thenReturn("1700000000")
        whenever(values.get("security:blocked:$userId")).thenReturn("true")

        val result = service.getDeviceSecurityStatus(userId)

        assertEquals(userId, result["userId"])
        assertEquals(true, result["isBlocked"])
        assertEquals("1700000000", result["lastSeen"])
    }
}
