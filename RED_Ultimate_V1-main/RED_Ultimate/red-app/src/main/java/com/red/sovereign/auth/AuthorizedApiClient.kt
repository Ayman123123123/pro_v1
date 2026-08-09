package com.red.sovereign.auth

import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class AuthorizedApiClient(
    private val tokens: TokenStore,
    private val auth: AuthApi = AuthApi(tokens.context),
    private val client: OkHttpClient = SecureOkHttpClient.getDefault(tokens.context)
) {
    suspend fun request(method: String, path: String, jsonBody: String? = null): ApiResult<String> =
        requestBody(method, path, jsonBody?.toRequestBody(JSON))

    suspend fun requestBody(method: String, path: String, body: RequestBody? = null): ApiResult<String> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHENTICATED")
        fun build(access: String) = Request.Builder()
            .url(ServerEndpoint.url().trimEnd('/') + path)
            .header("Authorization", "Bearer $access")
            .method(method, if (method == "GET" || method == "DELETE") null else body ?: ByteArray(0).toRequestBody(JSON))
            .build()
        executeWithRefresh(build(token)) { build(it) }
    }

    suspend fun download(path: String, target: File): ApiResult<File> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHENTICATED")
        fun build(access: String) = Request.Builder()
            .url(ServerEndpoint.url().trimEnd('/') + path)
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        val result = executeResponseWithRefresh(build(token)) { build(it) }
        when (result) {
            is ApiResult.Error -> result
            is ApiResult.Success -> {
                result.value.use { response ->
                    if (!response.isSuccessful) return@withContext ApiResult.Error(response.code, response.body?.string())
                    target.parentFile?.mkdirs()
                    response.body?.byteStream()?.use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                    ApiResult.Success(target)
                }
            }
        }
    }

    /** تنزيل بايتات (مثل الوسائط المشفرة) مع حد أقصى للحجم. */
    suspend fun requestBytes(path: String, maximumBytes: Int = 25 * 1024 * 1024): ApiResult<ByteArray> = withContext(Dispatchers.IO) {
        require(maximumBytes in 1..100 * 1024 * 1024)
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHENTICATED")
        fun build(access: String) = Request.Builder()
            .url(ServerEndpoint.url().trimEnd('/') + path)
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        val result = executeResponseWithRefresh(build(token)) { build(it) }
        when (result) {
            is ApiResult.Error -> result
            is ApiResult.Success -> result.value.use { response ->
                if (!response.isSuccessful) ApiResult.Error(response.code, response.body?.string())
                else {
                    val bytes = response.body?.bytes()
                    if (bytes == null) ApiResult.Error(null, "EMPTY_RESPONSE")
                    else if (bytes.size > maximumBytes) ApiResult.Error(null, "MEDIA_TOO_LARGE")
                    else ApiResult.Success(bytes)
                }
            }
        }
    }

    /** تنزيل ملف كامل إلى وجهة محلية (مثل كاش الوسائط). */
    suspend fun requestFile(path: String, destination: File, maximumBytes: Long = 100L * 1024 * 1024): ApiResult<File> = withContext(Dispatchers.IO) {
        require(maximumBytes in 1..100L * 1024 * 1024)
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHENTICATED")
        fun build(access: String) = Request.Builder()
            .url(ServerEndpoint.url().trimEnd('/') + path)
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        val result = executeResponseWithRefresh(build(token)) { build(it) }
        when (result) {
            is ApiResult.Error -> result
            is ApiResult.Success -> result.value.use { response ->
                if (!response.isSuccessful) ApiResult.Error(response.code, response.body?.string())
                else {
                    val body = response.body ?: return@use ApiResult.Error(null, "EMPTY_RESPONSE")
                    if (body.contentLength() > maximumBytes) ApiResult.Error(null, "MEDIA_TOO_LARGE")
                    else {
                        destination.parentFile?.mkdirs()
                        body.byteStream().use { input -> FileOutputStream(destination).use { input.copyTo(it) } }
                        ApiResult.Success(destination)
                    }
                }
            }
        }
    }

    private fun executeWithRefresh(initial: Request, rebuild: (String) -> Request): ApiResult<String> {
        executeResponseWithRefresh(initial, rebuild).let { result ->
            return when (result) {
                is ApiResult.Error -> result
                is ApiResult.Success -> result.value.use { response ->
                    if (response.isSuccessful) ApiResult.Success(response.body?.string().orEmpty())
                    else ApiResult.Error(response.code, response.body?.string())
                }
            }
        }
    }

    private fun executeResponseWithRefresh(initial: Request, rebuild: (String) -> Request): ApiResult<okhttp3.Response> {
        val first = runCatching { client.newCall(initial).execute() }.getOrElse { return ApiResult.Error(null, "NETWORK_ERROR") }
        if (first.code != 401) return ApiResult.Success(first)
        first.close()
        val refresh = tokens.refreshToken ?: return ApiResult.Error(401, "UNAUTHENTICATED")
        val refreshed = when (val result = kotlinx.coroutines.runBlocking { auth.refresh(refresh) }) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> return ApiResult.Error(401, "UNAUTHENTICATED")
        }
        tokens.updateTokens(refreshed)
        val second = runCatching { client.newCall(rebuild(refreshed.accessToken)).execute() }
            .getOrElse { return ApiResult.Error(null, "NETWORK_ERROR") }
        return ApiResult.Success(second)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
