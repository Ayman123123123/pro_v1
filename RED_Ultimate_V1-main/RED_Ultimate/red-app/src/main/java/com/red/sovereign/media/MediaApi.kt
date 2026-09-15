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
        if (!(path.startsWith("/api/media/") && !path.contains(".."))) {
            android.util.Log.e("MediaApi", "download invalid path")
            return ApiResult.Error(400, "INVALID_MEDIA_PATH")
        }
        val temporary = File.createTempFile("media-download-", ".bin", context.cacheDir)
        return try {
            when (val result = client.download(path, temporary)) {
                is ApiResult.Success -> {
                    val bytes = result.value.readBytes()
                    if (bytes.size > maximumBytes) ApiResult.Error(413, "MEDIA_TOO_LARGE")
                    else ApiResult.Success(result.code, bytes)
                }
                is ApiResult.Error -> result
            }
        } finally {
            temporary.delete()
        }
    }

    private val encryptedCache by lazy { EncryptedMediaCache(context) }

    /**
     * بوابة التنزيل التلقائي: يقرأ (auto_download_wifi/mobile) من Prefs + نوع الشبكة
     * الحالية. يُستخدم من شاشة "التنزيل التلقائي للوسائط" — WiFi/Mobile/Never.
     * يعيد false في وضع Never أو عند انعدام الشبكة المسموحة.
     */
    fun isAutoDownloadAllowedNow(): Boolean {
        val prefs = context.getSharedPreferences("younes_user_preferences", Context.MODE_PRIVATE)
        val wifi = prefs.getBoolean("auto_download_wifi", true)
        val mobile = prefs.getBoolean("auto_download_mobile", false)
        if (!wifi && !mobile) return false
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return wifi
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val isWifi = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        val isCell = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        return (isWifi && wifi) || (isCell && mobile)
    }

    /**
     * تنزيل مشروط بالسياسة: يعيد null دون شبكة/دون سماح بدل إخفاء الفشل —
     * المستدعي يعرض "بانتظار WiFi" بدل زر ميت.
     */
    suspend fun downloadToPrivateCacheIfAllowed(path: String, extension: String): ApiResult<File>? {
        if (!isAutoDownloadAllowedNow()) return null
        return downloadToPrivateCache(path, extension)
    }

    suspend fun downloadToPrivateCache(path: String, extension: String): ApiResult<File> {
        if (!(path.startsWith("/api/media/") && !path.contains(".."))) {
            android.util.Log.e("MediaApi", "downloadToPrivateCache invalid path")
            return ApiResult.Error(400, "INVALID_MEDIA_PATH")
        }
        if (!extension.matches(Regex("^[a-z0-9]{2,5}$"))) {
            android.util.Log.e("MediaApi", "downloadToPrivateCache invalid extension")
            return ApiResult.Error(400, "INVALID_MEDIA_EXTENSION")
        }
        // Try encrypted cache first
        val cacheKey = "story:$path"
        encryptedCache.get(cacheKey)?.let { bytes ->
            val tmp = File.createTempFile("story-cached-", ".$extension", context.cacheDir)
            // LEGENDARY FIX: كتابة متدفقة بحد 8KB (كان writeBytes دفعة واحدة لملف 99MB)
            tmp.outputStream().use { out -> bytes.inputStream().copyTo(out, 8 * 1024) }
            if (tmp.length() in 1..100L * 1024 * 1024) return ApiResult.Success(200, tmp)
            else runCatching { tmp.delete() }
        }
        val directory = File(context.cacheDir, "story_media").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        val destination = File(directory, "$digest.$extension")
        if (destination.isFile && destination.length() in 1..100L * 1024 * 1024) {
            // Warm encrypted cache — فقط للملفات الصغيرة (<=8MB) لمنع OOM
            try {
                if (destination.length() <= 8L * 1024 * 1024) {
                    destination.inputStream().use { ins ->
                        val buf = ins.readBytes()
                        encryptedCache.put(cacheKey, buf)
                    }
                }
            } catch (_: Exception) {}
            return ApiResult.Success(200, destination)
        }
        return when (val result = client.download(path, destination)) {
            is ApiResult.Success -> {
                // LEGENDARY FIX: تخزين مؤقت للصغير فقط + قراءة متدفقة (كان readBytes() مرتين لملف 99MB = OOM)
                try {
                    if (result.value.length() <= 8L * 1024 * 1024) {
                        result.value.inputStream().use { ins ->
                            encryptedCache.put(cacheKey, ins.readBytes())
                        }
                    }
                } catch (_: Exception) {}
                result
            }
            is ApiResult.Error -> result
        }
    }

    fun clearPrivateCache() {
        File(context.cacheDir, "story_media").listFiles()?.forEach(File::delete)
    }

    suspend fun uploadEncrypted(file: File, displayName: String): ApiResult<MediaObject> {
        if (!(file.isFile && file.length() in 1..100L * 1024 * 1024)) {
            android.util.Log.e("MediaApi", "uploadEncrypted invalid file size=" + file.length())
            return ApiResult.Error(400, "FILE_TOO_LARGE")
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "$displayName.bin", file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        return decodeUpload(client.requestBody("POST", "/api/media", body))
    }

    suspend fun grant(objectKey: String, targetRedId: String): ApiResult<String> =
        client.request("POST", "/api/media/grants", json.encodeToString(MediaGrantRequest(objectKey, targetRedId)))

    suspend fun delete(path: String): ApiResult<String> {
        if (!(path.startsWith("/api/media/") && !path.contains(".."))) {
            android.util.Log.e("MediaApi", "delete invalid path")
            return ApiResult.Error(400, "INVALID_MEDIA_PATH")
        }
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
                val input = resolver.openInputStream(uri)
                if (input == null) {
                    android.util.Log.e("MediaApi", "upload openInputStream null")
                    throw java.io.IOException("Unable to open selected media")
                }
                input.use { ins -> ins.source().use { sink.writeAll(it) } }
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
