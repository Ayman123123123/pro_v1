package com.red.server

import com.red.server.calls.LiveStreamRepository
import com.red.server.calls.LiveStreamService
import com.red.server.calls.RoomPasswordHasher
import com.red.server.websocket.CallWebSocketHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * اختبارات خدمة البث المباشر: بدء/إيقاف البث، عدّ المشاهدين،
 * وسلامة الخيوط عند التعامل المتوازي.
 */
class LiveStreamServiceTest {

    private val service = LiveStreamService(
        RoomPasswordHasher(),
        mock<LiveStreamRepository>(),
        mock<CallWebSocketHandler>()
    )

    @Test
    fun `starting a stream registers broadcaster and returns zero viewers`() {
        val stream = service.startStream("stream-1", "96109")

        assertEquals("stream-1", stream.streamId)
        assertEquals("96109", stream.broadcasterId)
        assertEquals(0, stream.viewerCount)
        assertNotNull(stream.startedAt)
    }

    @Test
    fun `starting the same stream twice keeps a single entry`() {
        service.startStream("stream-1", "96109")
        // إعادة البدء من نفس المذيع لا تُنشئ إدخالاً جديداً
        service.startStream("stream-1", "96109")

        val active = service.getActiveStreams()
        assertEquals(1, active.size)
        assertEquals("96109", active.first().broadcasterId)
    }

    @Test
    fun `starting an active stream from a different broadcaster is rejected`() {
        service.startStream("stream-1", "96109")
        // حماية من الاستيلاء على معرف بث حي من مذيع آخر
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.startStream("stream-1", "57477")
        }
        val active = service.getActiveStreams()
        assertEquals(1, active.size)
        assertEquals("96109", active.first().broadcasterId)
    }

    @Test
    fun `private stream requires a password`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.createStream(
                streamId = "private-1",
                broadcasterId = "96109",
                broadcasterName = "يونس",
                broadcasterRedId = "96109",
                title = "بث خاص",
                isPrivate = true,
                password = null,
            )
        }
    }

    @Test
    fun `private stream verifies only its configured password`() {
        service.createStream(
            streamId = "private-1",
            broadcasterId = "96109",
            broadcasterName = "يونس",
            broadcasterRedId = "96109",
            title = "بث خاص",
            isPrivate = true,
            password = "مفتاح-خاص-قوي",
        )

        assertFalse(service.verifyPassword("private-1", null))
        assertFalse(service.verifyPassword("private-1", "خاطئة"))
        assertTrue(service.verifyPassword("private-1", "مفتاح-خاص-قوي"))
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

    @Test
    fun `slow mode pin mute ban and words are broadcaster only`() {
        service.startStream("stream-2", "96109")
        assertEquals(5, service.setSlowMode("stream-2", "96109", 5))
        assertEquals(5, service.getSlowMode("stream-2"))
        assertTrue(service.setPinned("stream-2", "96109", "m1", "مثبت"))
        assertTrue(service.muteUser("stream-2", "96109", "viewer-x", true))
        assertTrue(service.isMuted("stream-2", "viewer-x"))
        assertTrue(service.banUser("stream-2", "96109", "viewer-y", true))
        assertTrue(service.isBanned("stream-2", "viewer-y"))
        assertEquals(listOf("spam2"), service.updateBlockedWords("stream-2", "96109", listOf("spam2")))
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            service.setSlowMode("stream-2", "intruder", 10)
        }
    }

    @Test
    fun `banned viewer cannot join and peak tracks maximum`() {
        service.startStream("stream-3", "96109")
        service.banUser("stream-3", "96109", "bad-guy", true)
        assertEquals(-1, service.addViewer("stream-3", "bad-guy"))
        service.addViewer("stream-3", "good-1")
        service.addViewer("stream-3", "good-2")
        service.removeViewer("stream-3", "good-1")
        val analytics = service.getAnalytics("stream-3")
        assertEquals(2, analytics["peakViewers"])
        assertEquals(1, analytics["viewerCount"])
    }

    @Test
    fun `cohosts capped at four and raised hands tracked`() {
        service.startStream("stream-4", "96109")
        service.addRaisedHand("stream-4", "u1", "الأول")
        service.addRaisedHand("stream-4", "u2", "الثاني")
        assertEquals(2, service.getRaisedHands("stream-4").size)
        assertTrue(service.approveCoHost("stream-4", "96109", "u1"))
        assertTrue(service.getCoHosts("stream-4").contains("u1"))
        assertEquals(1, service.getRaisedHands("stream-4").size)
        assertTrue(service.approveCoHost("stream-4", "96109", "u2"))
        assertTrue(service.approveCoHost("stream-4", "96109", "u3"))
        assertTrue(service.approveCoHost("stream-4", "96109", "u4"))
        // الخامس مرفوض (الحد 4)
        assertFalse(service.approveCoHost("stream-4", "96109", "u5"))
        assertTrue(service.removeCoHost("stream-4", "96109", "u2"))
        assertTrue(service.approveCoHost("stream-4", "96109", "u5"))
    }

    @Test
    fun `chat history capped and deletable with pin cleanup`() {
        service.startStream("stream-5", "96109")
        repeat(205) { i -> service.saveChat("stream-5", "u", "مستخدم", "رسالة $i") }
        // التخزين مقيد بـ 200 والاسترجاع بـ 100 لكل صفحة
        assertEquals(100, service.getChatHistory("stream-5", 100).size)
        val oldest = service.getChatHistory("stream-5", 100).first()
        // أقدم رسالة بعد التجاوز هي "رسالة 105" (حُذفت أول 5)
        assertTrue(oldest.text.contains("105"))
        val first = service.saveChat("stream-5", "u", "مستخدم", "مهمة")
        service.setPinned("stream-5", "96109", first.id, "مهمة")
        assertTrue(service.deleteChat("stream-5", "96109", first.id))
        assertTrue(service.getChatHistory("stream-5", 100).none { it.id == first.id })
    }

    @Test
    fun `password rotation invalidates old password`() {
        service.createStream(
            streamId = "stream-6",
            broadcasterId = "96109",
            broadcasterName = "يونس",
            broadcasterRedId = "96109",
            title = "خاص",
            isPrivate = true,
            password = "قديمة-قوية",
        )
        assertTrue(service.verifyPassword("stream-6", "قديمة-قوية"))
        assertTrue(service.rotatePassword("stream-6", "96109", "جديدة-قوية"))
        assertFalse(service.verifyPassword("stream-6", "قديمة-قوية"))
        assertTrue(service.verifyPassword("stream-6", "جديدة-قوية"))
    }
}
