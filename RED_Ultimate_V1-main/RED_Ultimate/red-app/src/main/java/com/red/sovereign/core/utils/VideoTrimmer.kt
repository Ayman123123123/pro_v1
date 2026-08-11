package com.red.sovereign.core.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import java.io.File

/** YOUNES story-video trimmer: local 30-second H.264 export before encryption. */
object VideoTrimmer {
    fun trimToStoryLimit(context: Context, uri: Uri, outputPath: String, listener: Transformer.Listener) {
        File(outputPath).parentFile?.mkdirs()
        val clippedMediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(30_000)
                    .build()
            )
            .build()
        val editedMediaItem = EditedMediaItem.Builder(clippedMediaItem).build()
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(listener)
            .build()
        transformer.start(editedMediaItem, outputPath)
    }
}
