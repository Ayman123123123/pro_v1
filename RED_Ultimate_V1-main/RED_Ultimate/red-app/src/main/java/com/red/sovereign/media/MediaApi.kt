package com.red.sovereign.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.media.EncryptedMediaCache
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.security.MessageDigest

@Serializable data class MediaObject(val objectKey: String, val mimeType: String, val size: Long, val url: String)

class MediaApi(private val context: Context, private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun download(path: String, maximumBytes: Int = 25 * 1024 * 1024): ApiResult<ByteArray> {
        require(path.startsWith("/api/media/") && !path.contains("..")) { "Invalid authenticated media path" }
        return client.requestBytes(path, maximumBytes)
    }

    private val encryptedCache by lazy { EncryptedMediaCache(context) }

    suspend fun downloadToPrivateCache(path: String, extension: String): ApiResult<File> {
        require(path.startsWith("/api/media/") && !path.contains("..")) { "Invalid authenticated media path" }
        require(extension.matches(Regex("^[a-z0-9]{2,5}$")))
        // Try encrypted cache first
        val cacheKey = "story:$path"
        encryptedCache.get(cacheKey)?.let { bytes ->
            val tmp = File.createTempFile("story-cached-", ".$extension", context.cacheDir)
            tmp.writeBytes(bytes)
            if (tmp.length() in 1..100L * 1024 * 1024) return ApiResult.Success(200, tmp)
        }
        val directory = File(context.cacheDir, "story_media").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        val destination = File(directory, "$digest.$extension")
        if (destination.isFile && destination.length() in 1..100L * 1024 * 1024) {
            // Warm encrypted cache
            try { encryptedCache.put(cacheKey, destination.readBytes()) } catch (_: Exception) {}
            return ApiResult.Success(200, destination)
        }
        return when (val result = client.requestFile(path, destination)) {
            is ApiResult.Success -> {
                try { encryptedCache.put(cacheKey, result.value.readBytes()) } catch (_: Exception) {}
                result
            }
            is ApiResult.Error -> result
        }
    }

    fun clearPrivateCache() {
        File(context.cacheDir, "story_media").listFiles()?.forEach(File::delete)
    }

    suspend fun uploadEncrypted(file: File, displayName: String): ApiResult<MediaObject> {
        require(file.isFile && file.length() in 1..100L * 1024 * 1024)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "$displayName.bin", file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        return decodeUpload(client.requestBody("POST", "/api/media", body))
    }

    suspend fun grant(objectKey: String, targetRedId: String): ApiResult<String> =
        client.request("POST", "/api/media/grants", json.encodeToString(MediaGrantRequest(objectKey, targetRedId)))

    suspend fun delete(path: String): ApiResult<String> {
        require(path.startsWith("/api/media/") && !path.contains(".."))
        return client.request("DELETE", path)
    }

    suspend fun upload(uri: Uri): ApiResult<MediaObject> {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: return ApiResult.Error(null, "UNKNOWN_MEDIA_TYPE")
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "upload.bin"
        } ?: "upload.bin"
        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        } ?: -1L
        val streamBody = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input -> input.source().use { sink.writeAll(it) } }
                    ?: error("Unable to open selected media")
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, streamBody).build()
        return decodeUpload(client.requestBody("POST", "/api/media", body))
    }

    private fun decodeUpload(result: ApiResult<String>): ApiResult<MediaObject> = when (result) {
        is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<MediaObject>(result.value)) }
            .getOrElse { ApiResult.Error(result.code, "INVALID_MEDIA_RESPONSE") }
        is ApiResult.Error -> result
    }
}

@Serializable data class MediaGrantRequest(val objectKey: String, val targetRedId: String)
