package com.red.sovereign.core.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Transformer
import androidx.media3.common.MimeTypes
import java.io.File

/**
 * 🎞️ YOUNES Video Trimmer — تقليم الفيديوهات السيادية للقصص
 */
object VideoTrimmer {
    fun trimToStoryLimit(context: Context, uri: Uri, outputPath: String, listener: Transformer.Listener) {
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(30000) // 30 Seconds Limit for Stories
                    .build()
            ).build()

        transformer.addListener(listener)
        transformer.start(mediaItem, outputPath)
    }
}
