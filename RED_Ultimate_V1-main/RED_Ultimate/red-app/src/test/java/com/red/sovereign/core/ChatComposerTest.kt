package com.red.sovereign.core

import com.red.sovereign.crypto.DecryptedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerTest {

    @Test
    fun `clamp drops off and zero`() {
        assertNull(ChatComposer.clampDisappearingMs(null))
        assertNull(ChatComposer.clampDisappearingMs(0L))
        assertNull(ChatComposer.clampDisappearingMs(-5L))
    }

    @Test
    fun `clamp keeps allowed values`() {
        assertEquals(3_600_000L, ChatComposer.clampDisappearingMs(3_600_000L))
        assertEquals(86_400_000L, ChatComposer.clampDisappearingMs(86_400_000L))
        assertEquals(604_800_000L, ChatComposer.clampDisappearingMs(604_800_000L))
    }

    @Test
    fun `clamp snaps unknown durations instead of crashing send`() {
        assertEquals(3_600_000L, ChatComposer.clampDisappearingMs(1_000L))
        assertEquals(86_400_000L, ChatComposer.clampDisappearingMs(10_000_000L))
        assertEquals(604_800_000L, ChatComposer.clampDisappearingMs(900_000_000L))
    }

    @Test
    fun `buildText rejects blank without poll`() {
        val result = ChatComposer.buildText(text = "   ")
        assertTrue(result.isFailure)
        assertEquals("EMPTY_TEXT", result.exceptionOrNull()?.message)
    }

    @Test
    fun `buildText does not throw on illegal disappearing value`() {
        val result = ChatComposer.buildText(text = "مرحبا", disappearingMs = 12345L)
        assertTrue(result.isSuccess)
        assertEquals(3_600_000L, result.getOrThrow().disappearingMs)
    }

    @Test
    fun `buildText caps mentions and hashtags`() {
        val result = ChatComposer.buildText(
            text = "hi",
            mentions = List(30) { "@16999" },
            hashtags = List(20) { "#a" },
        )
        assertTrue(result.isSuccess)
        assertEquals(20, result.getOrThrow().mentions.size)
        assertEquals(10, result.getOrThrow().hashtags.size)
    }

    @Test
    fun `humanize maps encrypt failure to arabic`() {
        val text = ChatComposer.humanizeSendError("NO_APPROVED_REMOTE_DEVICE")
        assertTrue(text.contains("جهاز معتمد"))
        assertFalse(text.contains("NO_APPROVED"))
    }

    @Test
    fun `consume failure restores text and drops pending bubble`() {
        val clientId = "local-test"
        val pending = DecryptedMessage(
            id = clientId,
            conversationId = "conv",
            senderRedId = "16999",
            plaintext = RichMessage.encode(RichMessage(text = "النص الأصلي")),
            timestamp = 1L,
            sequence = 0L,
            type = "RICH_TEXT",
            outgoing = true,
            status = "PENDING",
        )
        val messages = mutableListOf(pending)
        val update = ChatComposer.consumeOutgoingEvent(
            OutgoingSendEvent("conv", clientId, success = false, error = "NO_APPROVED_REMOTE_DEVICE"),
            messages,
        )
        assertTrue(messages.isEmpty())
        assertEquals("النص الأصلي", update.restoreText)
        assertNotNull(update.error)
        assertTrue(update.error!!.contains("جهاز معتمد"))
    }

    @Test
    fun `consume success only drops the local placeholder`() {
        val clientId = ChatComposer.newClientId()
        assertTrue(ChatComposer.isClientId(clientId))
        val messages = mutableListOf(
            DecryptedMessage(clientId, "conv", "16999", "x".toByteArray(), 1L, 0L, outgoing = true, status = "PENDING"),
        )
        val update = ChatComposer.consumeOutgoingEvent(
            OutgoingSendEvent("conv", clientId, success = true, serverId = "server-1"),
            messages,
        )
        assertTrue(messages.isEmpty())
        assertNull(update.restoreText)
        assertNull(update.error)
    }
}
