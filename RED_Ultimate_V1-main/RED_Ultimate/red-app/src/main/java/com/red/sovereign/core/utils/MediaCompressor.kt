package com.red.sovereign.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.red.sovereign.media.VoiceQuality
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * YOUNES Media Compressor.
 * Compresses media locally before E2EE encryption and upload to MinIO.
 */
object MediaCompressor {

    /** Max image dimension after compression — larger gets downscaled by power-of-two (like WhatsApp). */
    const val DEFAULT_MAX_DIMENSION = 2048

    /** مواصفات الجودة المحفوظة في younes_user_preferences (media_quality) — يستهلكها DeviceSettings. */
    data class QualitySpec(val maxDimension: Int, val jpegQuality: Int)
    fun specFor(quality: String): QualitySpec = when (quality) {
        "BALANCED" -> QualitySpec(1280, 75)
        "SAVER" -> QualitySpec(640, 60)
        else -> QualitySpec(DEFAULT_MAX_DIMENSION, 85)
    }

    /**
     * ضغط بجودة المستخدم المحفوظة فعلياً (HIGH/BALANCED/SAVER من شاشة جودة الوسائط).
     * يقرأ Prefs مباشرة دون الاعتماد على SettingsRuntime لتفادي دور استيراد
     * core.utils ← settings (settings يستهلك core في ServerEndpoint وغيره).
     */
    fun compressImageWithUserQuality(context: Context, inputPath: String, outputPath: String): File {
        val quality = runCatching {
            context.getSharedPreferences("younes_user_preferences", Context.MODE_PRIVATE)
                .getString("media_quality", "HIGH") ?: "HIGH"
        }.getOrElse { "HIGH" }
        val spec = specFor(quality)
        return compressImage(inputPath, outputPath, spec.maxDimension, spec.jpegQuality)
    }

    /** JPEG 85% balances readability, quality, and encrypted upload size. */
    fun compressImage(inputPath: String, outputPath: String, maxDimension: Int = DEFAULT_MAX_DIMENSION, jpegQuality: Int = 85): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(inputPath, bounds)
        val width = bounds.outWidth; val height = bounds.outHeight
        if (width <= 0 || height <= 0) error("IMAGE_DECODE_FAILED")
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decodeFile = BitmapFactory.decodeFile(inputPath, options)
        val bmp = decodeFile ?: error("IMAGE_DECODE_FAILED")
        var bitmap = bmp
        // EXIF orientation — without it camera photos appear flipped/rotated after compression
        bitmap = runCatching {
            when (ExifInterface(inputPath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> bitmap.rotated(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> bitmap.rotated(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> bitmap.rotated(270f)
                else -> bitmap
            }
        }.getOrDefault(bitmap)
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        try {
            FileOutputStream(outputFile).use { out ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(40, 100), out)) {
                    "IMAGE_COMPRESSION_FAILED"
                }
            }
            return outputFile
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    /**
     * Transcodes video to H.264 and a 720px height while preserving aspect ratio.
     * Media3 1.11 exports an EditedMediaItem; passing a raw MediaItem would keep
     * the original resolution and make the old "720p" claim ineffective.
     */
    @UnstableApi
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

    /**
     * Compresses/transcodes audio to Opus or AAC at the given quality.
     * Uses Media3 Transformer for consistent cross-device encoding.
     * Returns the output file on success.
     */
    @UnstableApi
    suspend fun compressAudio(
        context: Context,
        inputPath: String,
        outputPath: String,
        quality: VoiceQuality = VoiceQuality.STANDARD
    ): File = withContext(Dispatchers.IO) {
        File(outputPath).parentFile?.mkdirs()

        val mimeType = when {
            quality == VoiceQuality.COMPACT -> MimeTypes.AUDIO_OPUS
            quality == VoiceQuality.ULTRA -> MimeTypes.AUDIO_AAC
            else -> MimeTypes.AUDIO_OPUS
        }

        val mediaItem = MediaItem.fromUri(inputPath)
            .buildUpon()
            .setMimeType(mimeType)
            .build()

        val result = CompletableDeferred<java.io.File>()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    result.complete(java.io.File(outputPath))
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    result.completeExceptionally(exportException)
                }
            })
            .build()

        transformer.start(mediaItem, outputPath)
        result.await()
    }

    /**
     * Synchronous version for backward compatibility with Transformer.Listener callback.
     */
    @UnstableApi
    fun compressAudio(
        context: Context,
        inputPath: String,
        outputPath: String,
        quality: VoiceQuality,
        listener: Transformer.Listener
    ) {
        java.io.File(outputPath).parentFile?.mkdirs()

        val mediaItem = MediaItem.fromUri(inputPath)
            .buildUpon()
            .setMimeType(when {
                quality == VoiceQuality.COMPACT -> MimeTypes.AUDIO_OPUS
                quality == VoiceQuality.ULTRA -> MimeTypes.AUDIO_AAC
                else -> MimeTypes.AUDIO_OPUS
            })
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(listener)
            .build()

        transformer.start(mediaItem, outputPath)
    }

    /**
     * Creates a default Transformer.Listener that does nothing.
     * Useful when caller doesn't need callbacks.
     */
    @UnstableApi
    fun defaultTransformerListener(): Transformer.Listener = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {}
        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {}
    }

    private fun Bitmap.rotated(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated !== this) recycle()
        return rotated
    }
}
