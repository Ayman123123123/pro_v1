package com.red.sovereign.auth

import android.content.Context
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class AuthorizedApiClient(
    private val context: Context,
    private val tokens: TokenStore,
    private val auth: AuthApi = AuthApi(context),
    private val client: OkHttpClient = SecureOkHttpClient.getDefault(context)
) {
    suspend fun request(method: String, path: String, jsonBody: String? = null): ApiResult<String> =
        requestBody(method, path, jsonBody?.toRequestBody(JSON))

    suspend fun requestBody(method: String, path: String, body: RequestBody? = null): ApiResult<String> {
        var result = execute(method, path, body)
        if (result is ApiResult.Error && result.code == 401) {
            val refresh = tokens.refreshToken ?: return result
            when (val rotated = auth.refresh(refresh)) {
                is ApiResult.Success -> { tokens.updateTokens(rotated.value); result = execute(method, path, body) }
                is ApiResult.Error -> return rotated
            }
        }
        return result
    }

    suspend fun requestBytes(path: String, maximumBytes: Int = 25 * 1024 * 1024): ApiResult<ByteArray> {
        require(maximumBytes in 1..100 * 1024 * 1024)
        var result = executeBytes(path, maximumBytes)
        if (result is ApiResult.Error && result.code == 401) {
            val refresh = tokens.refreshToken ?: return result
            when (val rotated = auth.refresh(refresh)) {
                is ApiResult.Success -> { tokens.updateTokens(rotated.value); result = executeBytes(path, maximumBytes) }
                is ApiResult.Error -> return rotated
            }
        }
        return result
    }

    suspend fun requestFile(path: String, destination: File, maximumBytes: Long = 100L * 1024 * 1024): ApiResult<File> {
        require(maximumBytes in 1..100L * 1024 * 1024)
        var result = executeFile(path, destination, maximumBytes)
        if (result is ApiResult.Error && result.code == 401) {
            val refresh = tokens.refreshToken ?: return result
            when (val rotated = auth.refresh(refresh)) {
                is ApiResult.Success -> { tokens.updateTokens(rotated.value); result = executeFile(path, destination, maximumBytes) }
                is ApiResult.Error -> return rotated
            }
        }
        return result
    }

    private suspend fun execute(method: String, path: String, body: RequestBody?): ApiResult<String> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHORIZED")
        runCatching {
            val builder = Request.Builder().url(ServerEndpoint.url() + path)
                .header("Authorization", "Bearer $token").header("Accept", "application/json")
            when (method) {
                "GET" -> builder.get()
                "POST" -> builder.post(body ?: EMPTY)
                "PUT" -> builder.put(body ?: EMPTY)
                "PATCH" -> builder.patch(body ?: EMPTY)
                "DELETE" -> builder.delete(body)
                else -> error("Unsupported HTTP method")
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) ApiResult.Success(response.code, text)
                else ApiResult.Error(response.code, text.ifBlank { "HTTP_${response.code}" })
            }
        }.getOrElse { ApiResult.Error(null, it.message ?: "NETWORK_ERROR") }
    }

    private suspend fun executeBytes(path: String, maximumBytes: Int): ApiResult<ByteArray> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHORIZED")
        runCatching {
            val request = Request.Builder()
                .url(ServerEndpoint.url() + path)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ApiResult.Error(response.code, "HTTP_${response.code}")
                val declared = response.body.contentLength()
                if (declared > maximumBytes) return@use ApiResult.Error(413, "MEDIA_TOO_LARGE")
                val output = ByteArrayOutputStream(minOf(maximumBytes, if (declared > 0) declared.toInt() else 64 * 1024))
                var tooLarge = false
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maximumBytes) { tooLarge = true; break }
                        output.write(buffer, 0, read)
                    }
                }
                if (tooLarge) ApiResult.Error(413, "MEDIA_TOO_LARGE")
                else ApiResult.Success(response.code, output.toByteArray())
            }
        }.getOrElse { ApiResult.Error(null, it.message ?: "NETWORK_ERROR") }
    }

    private suspend fun executeFile(path: String, destination: File, maximumBytes: Long): ApiResult<File> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHORIZED")
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()
        runCatching {
            val request = Request.Builder().url(ServerEndpoint.url() + path)
                .header("Authorization", "Bearer $token").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ApiResult.Error(response.code, "HTTP_${response.code}")
                val declared = response.body.contentLength()
                if (declared > maximumBytes) return@use ApiResult.Error(413, "MEDIA_TOO_LARGE")
                var total = 0L
                var tooLarge = false
                response.body.byteStream().use { input -> FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maximumBytes) { tooLarge = true; break }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                } }
                if (tooLarge) { partial.delete(); ApiResult.Error(413, "MEDIA_TOO_LARGE") }
                else {
                    destination.delete()
                    if (!partial.renameTo(destination)) { partial.delete(); ApiResult.Error(null, "MEDIA_CACHE_WRITE_FAILED") }
                    else ApiResult.Success(response.code, destination)
                }
            }
        }.getOrElse { partial.delete(); ApiResult.Error(null, it.message ?: "NETWORK_ERROR") }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val EMPTY = ByteArray(0).toRequestBody(null)
    }
}
