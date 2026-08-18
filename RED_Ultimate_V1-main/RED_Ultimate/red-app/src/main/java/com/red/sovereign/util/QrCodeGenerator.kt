package com.red.sovereign.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Hashtable

object QrCodeGenerator {
    fun generate(content: String, size: Int = 512): Bitmap {
        val hints = Hashtable<EncodeHintType, Any>()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = 1
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }
}
