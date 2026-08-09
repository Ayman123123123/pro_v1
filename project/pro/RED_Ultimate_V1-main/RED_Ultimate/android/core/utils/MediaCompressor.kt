package com.red.sovereign.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.common.MediaItem
import java.io.File
import java.io.FileOutputStream

/**
 * RED Media Compressor
 * Uses Media3 Transformer for Video and Native Bitmap for Images.
 * Zero FFmpegKit dependencies.
 */
object MediaCompressor {

    /**
     * Image Compression: JPEG 85%
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
     * Video Compression: 720p using Media3 Transformer
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
