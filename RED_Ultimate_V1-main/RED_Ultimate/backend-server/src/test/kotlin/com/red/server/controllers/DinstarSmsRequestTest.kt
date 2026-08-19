package com.red.server.controllers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DinstarSmsRequestTest {
    @Test
    fun `maps typed recipients to Dinstar parameters without empty user id`() {
        val request = SendSmsRequest(
            text = "رسالة اختبار",
            param = listOf(
                SmsRecipient(number = "+967771234567", user_id = 12),
                SmsRecipient(number = "777765432")
            ),
            encoding = "UCS2"
        )

        assertEquals(
            listOf(
                mapOf("number" to "+967771234567", "user_id" to 12),
                mapOf("number" to "777765432")
            ),
            request.toHardwareParams()
        )
    }
}
