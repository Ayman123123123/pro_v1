package com.red.sovereign.features.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PstnEventEnvelopeCodecTest {
    @Test
    fun `unwraps incoming call payload from server envelope`() {
        val event = PstnEventEnvelopeCodec.decode(
            """{"type":"PSTN_INCOMING","data":{"callId":"call-42","caller":"777123456","port":3,"channel":"PJSIP/3-00000042"}}"""
        )

        assertEquals("PSTN_INCOMING", event.type)
        assertEquals("call-42", event.callId)
        assertEquals("777123456", event.caller)
        assertEquals(3, event.port)
        assertEquals("PJSIP/3-00000042", event.channel)
    }

    @Test
    fun `unwraps received sms payload from server envelope`() {
        val event = PstnEventEnvelopeCodec.decode(
            """{"type":"SMS_RECEIVED","data":{"id":"sms-9","number":"770123456","content":"مرحبا","time":1730000000,"port":1}}"""
        )

        assertEquals("SMS_RECEIVED", event.type)
        assertEquals("sms-9", event.id)
        assertEquals("770123456", event.number)
        assertEquals("مرحبا", event.content)
        assertEquals(1, event.port)
    }

    @Test
    fun `accepts legacy flat envelope during staged backend rollout`() {
        val event = PstnEventEnvelopeCodec.decode(
            """{"type":"PSTN_INCOMING","callId":"legacy-1","caller":"731000000","gateway":"PJSIP/1-legacy"}"""
        )

        assertEquals("legacy-1", event.callId)
        assertEquals("PJSIP/1-legacy", event.gateway)
        assertNull(event.channel)
    }
}
