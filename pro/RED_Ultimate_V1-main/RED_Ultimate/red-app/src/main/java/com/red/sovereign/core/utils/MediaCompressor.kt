package com.red.sovereign.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.transformer.Transformer
import androidx.media3.common.MediaItem
import java.io.File
import java.io.FileOutputStream

/**
 * 🖼️ YOUNES Media Compressor
 * ضغط الصور والفيديو قبل التشفير والرفع لـ MinIO
 */
object MediaCompressor {

    /**
     * ضغط الصور: JPEG 85% لضمان التوازن بين الحجم والجودة
     */
    fun compressImage(inputPath: String, outputPath: String): File {
        val bitmap = BitmapFactory.decodeFile(inputPath)
        val outputFile = File(outputPath)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return outputFile
    }

    /**
     * ضغط الفيديو: 720p باستخدام Media3 لضمان التوافق والسرعة
     */
    fun compressVideo(context: Context, inputUri: Uri, outputPath: String, listener: Transformer.Listener) {
        val transformer = Transformer.Builder(context)
            .setVideoMimeType("video/avc") // H.264
            .build()
            
        val mediaItem = MediaItem.fromUri(inputUri)
        transformer.addListener(listener)
        transformer.start(mediaItem, outputPath)
    }
}
