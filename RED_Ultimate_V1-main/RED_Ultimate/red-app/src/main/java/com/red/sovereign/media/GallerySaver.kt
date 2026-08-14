package com.red.sovereign.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.red.sovereign.settings.SettingsRuntime
import java.io.File

/**
 * حفظ وسيط بعد فك التشفير في معرض الجهاز — فقط إن فعّل المستخدم الإعداد.
 * لا يرفع شيئاً. Android 10+ عبر MediaStore بلا صلاحية تخزين عامة.
 */
object GallerySaver {

    fun saveIfAllowed(context: Context, file: File, mimeType: String, displayName: String): Boolean {
        if (!SettingsRuntime.current.saveMediaToGallery) return false
        if (!file.isFile || file.length() <= 0L) return false
        val mime = mimeType.ifBlank { guessMime(file.name) }
        val name = displayName.ifBlank { file.name }.replace(Regex("[^A-Za-z0-9._\\-\\u0600-\\u06FF ]"), "_").take(80)
        return runCatching {
            val collection = when {
                mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    val dir = when {
                        mime.startsWith("image/") -> Environment.DIRECTORY_PICTURES
                        mime.startsWith("video/") -> Environment.DIRECTORY_MOVIES
                        mime.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
                        else -> Environment.DIRECTORY_DOWNLOADS
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Younes")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: return@runCatching false
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "m4a", "aac" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }
}
