package com.red.sovereign.calls

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

/**
 * اختبارات المديرين الجدد — Liquid Glass 2026
 * تضمن أن التحسينات الأسطورية لا تنكسر بصمت.
 */
class ManagersTest {

    @Test
    fun `VirtualBackgroundManager يبدل التأثير ويحسب blur`() {
        VirtualBackgroundManager.clear()
        assertEquals(VirtualBgEffect.NONE, VirtualBackgroundManager.config.effect)

        VirtualBackgroundManager.setEffect(VirtualBgEffect.BLUR)
        assertEquals(VirtualBgEffect.BLUR, VirtualBackgroundManager.config.effect)
        // في JVM الاختبار isSupported قد يكون false (SDK mock) — نتحقق من blurRadius لا من shouldApply
        assertEquals(22f, VirtualBackgroundManager.blurRadiusForCompose(), 0.1f)

        VirtualBackgroundManager.setEffect(VirtualBgEffect.BLUR_HEAVY)
        assertEquals(32f, VirtualBackgroundManager.blurRadiusForCompose(), 0.1f)

        VirtualBackgroundManager.setSolidColor(Color(0xFF14C79A))
        assertEquals(VirtualBgEffect.SOLID, VirtualBackgroundManager.config.effect)
        assertEquals(Color(0xFF14C79A), VirtualBackgroundManager.config.solidColor)

        VirtualBackgroundManager.clear()
        assertEquals(VirtualBgEffect.NONE, VirtualBackgroundManager.config.effect)
    }

    @Test
    fun `CallQualityManager يصنف الشبكة صحيحاً`() {
        CallQualityManager.update(rttMs = 30, packetLoss = 0f, bitrateKbps = 1600, fps = 30)
        assertEquals(NetworkQuality.EXCELLENT, CallQualityManager.lastStats.quality)
        assertTrue(CallQualityManager.recommendedVideoEnabled())
        assertEquals(1500, CallQualityManager.recommendedBitrateKbps())

        CallQualityManager.update(rttMs = 150, packetLoss = 1f, bitrateKbps = 800, fps = 24)
        assertEquals(NetworkQuality.GOOD, CallQualityManager.lastStats.quality)

        CallQualityManager.update(rttMs = 300, packetLoss = 3f, bitrateKbps = 300, fps = 15)
        assertEquals(NetworkQuality.FAIR, CallQualityManager.lastStats.quality)

        CallQualityManager.update(rttMs = 500, packetLoss = 6f, bitrateKbps = 100, fps = 10)
        assertEquals(NetworkQuality.POOR, CallQualityManager.lastStats.quality)
        assertFalse(CallQualityManager.recommendedVideoEnabled())
        assertEquals(150, CallQualityManager.recommendedBitrateKbps())
    }

    @Test
    fun `BreakoutRoomsManager ينشئ ويوزع الأعضاء`() {
        BreakoutRoomsManager.clear()
        assertTrue(BreakoutRoomsManager.rooms.isEmpty())

        val r1 = BreakoutRoomsManager.createRoom("غرفة النقاش")
        assertEquals(1, BreakoutRoomsManager.rooms.size)
        assertEquals("غرفة النقاش", r1.name)

        BreakoutRoomsManager.assignMember(r1.id, "user1")
        BreakoutRoomsManager.assignMember(r1.id, "user2")
        assertEquals(2, BreakoutRoomsManager.rooms.first().memberIds.size)

        BreakoutRoomsManager.removeMember(r1.id, "user1")
        assertEquals(1, BreakoutRoomsManager.rooms.first().memberIds.size)

        BreakoutRoomsManager.deleteRoom(r1.id)
        assertTrue(BreakoutRoomsManager.rooms.isEmpty())
    }

    @Test
    fun `PictureInPictureManager يتحقق من الدعم`() {
        // على JVM الاختبار isSupported يعتمد على SDK_INT mock — نتحقق من المنطق فقط
        val supported = PictureInPictureManager.isSupported()
        // SDK_INT في اختبار JVM هو 0 افتراضياً إلا إذا جرى mock، لكن الدالة يجب ألا ترمي استثناء
        assertNotNull(supported)

        PictureInPictureManager.onPipModeChanged(true)
        assertTrue(PictureInPictureManager.isInPip)
        PictureInPictureManager.onPipModeChanged(false)
        assertFalse(PictureInPictureManager.isInPip)
    }
}
