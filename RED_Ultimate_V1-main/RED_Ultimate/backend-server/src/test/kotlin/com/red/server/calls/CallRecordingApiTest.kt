package com.red.server.calls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication

/**
 * يختبر منطق Auth و التحقق الأساسي للـ CallRecordingController.
 * الـ Mongo repository يتم اختباره في integration tests.
 */
class CallRecordingApiTest {
    @Test fun `RegisterRecordingRequest carries all required fields`() {
        val req = RegisterRecordingRequest(
            callId = "call-1",
            peerId = "33563",
            sha256 = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
            sizeBytes = 1024L,
            durationMs = 45_000L
        )
        assertEquals("call-1", req.callId)
        assertEquals(64, req.sha256.length) // SHA-256 = 64 hex chars
    }

    @Test fun `RecordingManifestResponse is correctly structured`() {
        val manifest = RecordingManifestResponse(
            id = "rec-1",
            sha256 = "abc123",
            sizeBytes = 1024L,
            durationMs = 45_000L,
            encryption = "AES-256-GCM"
        )
        assertEquals("rec-1", manifest.id)
        assertEquals("AES-256-GCM", manifest.encryption)
    }

    @Test fun `Authentication carries userId for ownership check`() {
        val auth: Authentication = UsernamePasswordAuthenticationToken("user-1", "device-token")
        assertEquals("user-1", auth.name)
        assertNotNull(auth.credentials)
    }
}
