package com.red.server

import com.red.server.calls.LiveStreamService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * اختبارات خدمة البث المباشر: بدء/إيقاف البث، عدّ المشاهدين،
 * وسلامة الخيوط عند التعامل المتوازي.
 */
class LiveStreamServiceTest {

    private val service = LiveStreamService()

    @Test
    fun `starting a stream registers broadcaster and returns zero viewers`() {
        val stream = service.startStream("stream-1", "96109")

        assertEquals("stream-1", stream.streamId)
        assertEquals("96109", stream.broadcasterId)
        assertEquals(0, stream.viewerCount)
        assertNotNull(stream.startedAt)
    }

    @Test
    fun `a second broadcaster cannot hijack an existing stream id`() {
        service.startStream("stream-1", "96109")
        assertThrows(IllegalArgumentException::class.java) {
            service.startStream("stream-1", "57477")
        }

        val active = service.getActiveStreams()
        assertEquals(1, active.size)
        assertEquals("96109", active.first().broadcasterId)
    }

    @Test
    fun `private stream password is salted and verified`() {
        val stream = service.createStream(
            "private-stream", "96109", "Owner", "96109", "Private", true, "correct horse"
        )
        assertTrue(stream.passwordHash?.startsWith("pbkdf2-sha256-v1\$") == true)
        assertTrue(service.verifyPassword("private-stream", "correct horse"))
        assertFalse(service.verifyPassword("private-stream", "wrong password"))
    }

    @Test
    fun `viewers join leave and count is accurate`() {
        service.startStream("stream-1", "96109")
        service.addViewer("stream-1", "viewer-a")
        service.addViewer("stream-1", "viewer-b")
        service.addViewer("stream-1", "viewer-a") // نفس المشاهد لا يُعدّ مرتين

        assertEquals(2, service.getViewerCount("stream-1"))

        service.removeViewer("stream-1", "viewer-a")
        assertEquals(1, service.getViewerCount("stream-1"))
    }

    @Test
    fun `unknown stream returns zero viewers`() {
        assertEquals(0, service.getViewerCount("does-not-exist"))
    }

    @Test
    fun `stopping a stream removes it and reports success`() {
        service.startStream("stream-1", "96109")
        assertTrue(service.stopStream("stream-1"))
        assertFalse(service.stopStream("stream-1")) // إيقاف ثانٍ يفشل بأمان
        assertEquals(0, service.getActiveStreams().size)
    }

    @Test
    fun `concurrent viewer additions never lose updates`() {
        service.startStream("stream-1", "96109")
        val threads = (1..8).map { t ->
            Thread {
                repeat(250) { i -> service.addViewer("stream-1", "viewer-$t-$i") }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(8 * 250, service.getViewerCount("stream-1"))
    }
}
