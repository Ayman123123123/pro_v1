package com.red.server

import com.red.server.pstn.PstnCallProgressTracker
import com.red.server.pstn.PstnCallProgressTracker.Stage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * اختبارات سجلّ ربط مكالمات PSTN بأصحابها.
 *
 * هذا السجلّ هو المفصل الذي جعل مراحل Asterisk تصل إلى المستخدم بدل
 * أن تُكتب في اللوق وتُهمل، فأي خلل فيه يُعيد الشاشات إلى العزلة.
 */
class PstnCallProgressTrackerTest {

    private val callId = "action-1"
    private val redId = "RED-77012"
    private val number = "770123456"
    private val channel = "PJSIP/dinstar-gw-0"

    private fun tracker() = PstnCallProgressTracker()

    @Test
    fun `الحدث لا يُوجَّه قبل ربط القناة بالمكالمة`() {
        val t = tracker()
        t.register(callId, redId, number)
        // وصل حدث قناة قبل OriginateResponse: لا صاحب معروف بعد
        assertNull(t.advanceByChannel(channel, Stage.RINGING))
    }

    @Test
    fun `ربط القناة يجعل الأحداث اللاحقة قابلة للتوجيه`() {
        val t = tracker()
        t.register(callId, redId, number)
        assertNotNull(t.attachChannel(callId, channel))

        val ringing = t.advanceByChannel(channel, Stage.RINGING)
        assertNotNull(ringing)
        assertEquals(redId, ringing!!.redId)
        assertEquals(number, ringing.number)
        assertEquals(Stage.RINGING, ringing.stage)
    }

    @Test
    fun `المراحل تتقدّم بالترتيب الطبيعي`() {
        val t = tracker()
        t.register(callId, redId, number)
        t.attachChannel(callId, channel)

        assertEquals(Stage.RINGING, t.advanceByChannel(channel, Stage.RINGING)?.stage)
        assertEquals(Stage.BRIDGING, t.advanceByChannel(channel, Stage.BRIDGING)?.stage)
        assertEquals(Stage.ACTIVE, t.advanceByChannel(channel, Stage.ACTIVE)?.stage)
    }

    @Test
    fun `التراجع عن مرحلة مرفوض حتى لا ترتدّ الواجهة`() {
        val t = tracker()
        t.register(callId, redId, number)
        t.attachChannel(callId, channel)
        t.advanceByChannel(channel, Stage.ACTIVE)

        // Asterisk قد يرسل Ringing على قناة فرعية بعد الإجابة
        assertNull(t.advanceByChannel(channel, Stage.RINGING))
        assertEquals(Stage.ACTIVE, t.find(callId)?.stage)
    }

    @Test
    fun `تكرار المرحلة نفسها لا يُنتج بثاً مكرراً`() {
        val t = tracker()
        t.register(callId, redId, number)
        t.attachChannel(callId, channel)
        assertNotNull(t.advanceByChannel(channel, Stage.RINGING))
        assertNull(t.advanceByChannel(channel, Stage.RINGING))
    }

    @Test
    fun `الإنهاء بالقناة يحرّر القيدين معاً`() {
        val t = tracker()
        t.register(callId, redId, number)
        t.attachChannel(callId, channel)

        val ended = t.finishByChannel(channel)
        assertEquals(Stage.ENDED, ended?.stage)
        assertEquals(redId, ended?.redId)
        assertEquals(0, t.activeCount())
        // لا تسرّب: القناة لم تعد مرتبطة بشيء
        assertNull(t.advanceByChannel(channel, Stage.ACTIVE))
    }

    @Test
    fun `الإنهاء اليدوي بالمعرّف يحرّر القيد أيضاً`() {
        val t = tracker()
        t.register(callId, redId, number)
        t.attachChannel(callId, channel)

        assertEquals(Stage.ENDED, t.finishByCallId(callId)?.stage)
        assertEquals(0, t.activeCount())
        assertNull(t.find(callId))
    }

    @Test
    fun `مكالمات متعددة لا تتداخل`() {
        val t = tracker()
        t.register("a", "RED-1", "770000001")
        t.register("b", "RED-2", "710000002")
        t.attachChannel("a", "PJSIP/gw-0")
        t.attachChannel("b", "PJSIP/gw-1")

        assertEquals("RED-1", t.advanceByChannel("PJSIP/gw-0", Stage.ACTIVE)?.redId)
        assertEquals("RED-2", t.advanceByChannel("PJSIP/gw-1", Stage.RINGING)?.redId)

        t.finishByChannel("PJSIP/gw-0")
        // إنهاء الأولى لا يمسّ الثانية
        assertEquals(1, t.activeCount())
        assertEquals(Stage.RINGING, t.find("b")?.stage)
    }

    @Test
    fun `القناة المجهولة تُهمل بلا استثناء`() {
        val t = tracker()
        assertNull(t.advanceByChannel("PJSIP/unknown", Stage.ACTIVE))
        assertNull(t.finishByChannel("PJSIP/unknown"))
        assertNull(t.finishByCallId("no-such-call"))
    }

    @Test
    fun `ربط قناة بمكالمة غير مسجّلة يُرفض`() {
        val t = tracker()
        assertNull(t.attachChannel("ghost", channel))
        assertEquals(0, t.activeCount())
    }

    @Test
    fun `التقدّم بالمعرّف المباشر لا يتطلب ربط قناة`() {
        val t = tracker()
        t.register(callId, redId, number)
        // مسار DinstarEventListener الفعلي: callId محلول من Redis، لا حاجة لـ attachChannel
        assertEquals(Stage.RINGING, t.advanceByCallId(callId, Stage.RINGING)?.stage)
        assertEquals(Stage.ACTIVE, t.advanceByCallId(callId, Stage.ACTIVE)?.stage)
    }

    @Test
    fun `التقدّم بالمعرّف يمنع التراجع والتكرار`() {
        val t = tracker()
        t.register(callId, redId, number)
        t.advanceByCallId(callId, Stage.ACTIVE)
        assertNull(t.advanceByCallId(callId, Stage.RINGING))
        assertNull(t.advanceByCallId(callId, Stage.ACTIVE))
        assertEquals(Stage.ACTIVE, t.find(callId)?.stage)
    }

    @Test
    fun `التقدّم بمعرّف غير مسجّل يُهمل بلا استثناء`() {
        val t = tracker()
        assertNull(t.advanceByCallId("no-such-call", Stage.RINGING))
    }
}
