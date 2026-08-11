package com.red.sovereign.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream

/**
 * YOUNES Media Compressor.
 * Compresses media locally before E2EE encryption and upload to MinIO.
 */
object MediaCompressor {

    /** JPEG 85% balances readability, quality, and encrypted upload size. */
    fun compressImage(inputPath: String, outputPath: String): File {
        val bitmap = requireNotNull(BitmapFactory.decodeFile(inputPath)) {
            "IMAGE_DECODE_FAILED"
        }
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        try {
            FileOutputStream(outputFile).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                    "IMAGE_COMPRESSION_FAILED"
                }
            }
            return outputFile
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Transcodes video to H.264 and a 720px height while preserving aspect ratio.
     * Media3 1.11 exports an EditedMediaItem; passing a raw MediaItem would keep
     * the original resolution and make the old "720p" claim ineffective.
     */
    fun compressVideo(context: Context, inputUri: Uri, outputPath: String, listener: Transformer.Listener) {
        File(outputPath).parentFile?.mkdirs()
        val videoEffects: List<Effect> = listOf(Presentation.createForHeight(720))
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(listener)
            .build()
        transformer.start(editedMediaItem, outputPath)
    }
}
