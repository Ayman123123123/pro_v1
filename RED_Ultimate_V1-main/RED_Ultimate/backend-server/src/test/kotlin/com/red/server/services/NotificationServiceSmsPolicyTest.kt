package com.red.server.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationServiceSmsPolicyTest {
    @Test
    fun `sms notification accepts only canonical phone recipients`() {
        assertTrue(NotificationService.isValidSmsRecipient("+967712345678"))
        assertTrue(NotificationService.isValidSmsRecipient("712345678"))
        assertFalse(NotificationService.isValidSmsRecipient("+967 712345678"))
        assertFalse(NotificationService.isValidSmsRecipient("sms@example.com"))
        assertFalse(NotificationService.isValidSmsRecipient("12345"))
    }

    @Test
    fun `sms notification recognizes only accepted gateway queue statuses`() {
        assertTrue(NotificationService.isAcceptedGatewaySms(mapOf("error_code" to 200)))
        assertTrue(NotificationService.isAcceptedGatewaySms(mapOf("error_code" to 202)))
        assertFalse(NotificationService.isAcceptedGatewaySms(mapOf("error_code" to 413)))
        assertFalse(NotificationService.isAcceptedGatewaySms(emptyMap()))
    }

    @Test
    fun `sms notification logs only a masked recipient suffix`() {
        assertTrue(NotificationService.maskedPhoneSuffix("+967712345678") == "••••5678")
    }
}
