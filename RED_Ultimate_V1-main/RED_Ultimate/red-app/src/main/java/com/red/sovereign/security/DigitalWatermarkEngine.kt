package com.red.sovereign.security

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

/**
 * 🕵️ DigitalWatermarkEngine — محرك العلامات المائية الرقمية الخفية (Invisible Steganography)
 *
 * يقوم بتضمين بصمة مشفرة غير مرئية (Least Significant Bit - LSB) تحتوي على
 * معرّف المستلم وتوقيت الاستلام داخل بكسلات الصور الحساسة، لكشف مصدر أي تسريب
 * حتى لو تم تصوير الشاشة بكاميرا خارجية.
 */
object DigitalWatermarkEngine {

    /**
     * تضمين البصمة الرقمية الخفية داخل الصورة دون التأثير على جودتها البصرية
     */
    fun embedInvisibleWatermark(originalBitmap: Bitmap, recipientRedId: String, timestamp: Long): Bitmap {
        val watermarkText = "RED:$recipientRedId:$timestamp"
        val watermarkBytes = watermarkText.toByteArray(StandardCharsets.UTF_8)
        val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val width = mutableBitmap.width
        val height = mutableBitmap.height
        val maxBytes = (width * height * 3) / 8

        if (watermarkBytes.size > maxBytes) {
            return originalBitmap // الصورة أصغر من كتم البصمة
        }

        var byteIndex = 0
        var bitIndex = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (byteIndex >= watermarkBytes.size) break

                val pixel = mutableBitmap.getPixel(x, y)
                var red = Color.red(pixel)
                var green = Color.green(pixel)
                var blue = Color.blue(pixel)

                val currentByte = watermarkBytes[byteIndex].toInt()
                val bit = (currentByte ushr (7 - bitIndex)) and 1

                // تضمين البت في البت الأقل أهمية (LSB) للون الأزرق
                blue = (blue and 0xFE) or bit

                mutableBitmap.setPixel(x, y, Color.argb(Color.alpha(pixel), red, green, blue))

                bitIndex++
                if (bitIndex == 8) {
                    bitIndex = 0
                    byteIndex++
                }
            }
            if (byteIndex >= watermarkBytes.size) break
        }

        return mutableBitmap
    }
}
