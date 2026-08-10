package com.red.sovereign.features.stories

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.red.sovereign.core.network.MinioUploader
import com.red.sovereign.core.utils.MediaCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class StoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storyDao: StoryDao,
    private val minioUploader: MinioUploader
) : StoryRepository {

    override fun getActiveStories() = storyDao.getActiveStories(System.currentTimeMillis())

    override suspend fun uploadStory(userId: String, type: String, mediaUri: Uri?, caption: String?, backgroundColor: String?) {
        var finalUrl = ""
        if (mediaUri != null) {
            val fileId = generateUuidV7()
            val tempFile = copyUriToTemp(mediaUri, fileId)
            
            val processedFile = when (type) {
                "IMAGE" -> MediaCompressor.compressImage(tempFile.absolutePath, File(context.cacheDir, "$fileId.jpg").absolutePath)
                "VIDEO" -> compressVideo(tempFile, File(context.cacheDir, "$fileId.mp4").absolutePath)
                else -> tempFile
            }

            finalUrl = minioUploader.uploadFileSync(processedFile, "stories/$fileId") ?: throw Exception("RED: Upload Error")
        }

        storyDao.insertStory(StoryEntity(
            id = generateUuidV7(),
            userId = userId,
            mediaUrl = finalUrl,
            type = type,
            caption = caption,
            backgroundColor = backgroundColor
        ))
    }

    private suspend fun compressVideo(input: File, outPath: String): File = suspendCancellableCoroutine { cont ->
        val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).build()
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(c: Composition, r: ExportResult) = cont.resume(File(outPath))
            override fun onError(c: Composition, r: ExportResult, e: ExportException) = cont.resumeWithException(e)
        })
        transformer.start(MediaItem.fromUri(Uri.fromFile(input)), outPath)
    }

    private fun copyUriToTemp(uri: Uri, id: String): File {
        val file = File(context.cacheDir, "raw_$id")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    private fun generateUuidV7(): String {
        val timestamp = System.currentTimeMillis()
        val random = SecureRandom()
        val msb = (timestamp shl 16) or 0x7000L or (random.nextLong() and 0x0FFFL)
        val lsb = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }
}
