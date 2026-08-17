package com.red.sovereign.auth

import android.content.Context
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import com.red.sovereign.auth.AuthModels.UserResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AuthApi(
    private val context: Context,
    private val client: OkHttpClient = SecureOkHttpClient.getDefault(context),
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
) {
    suspend fun register(request: RegisterRequest): ApiResult<AuthResponse> =
        post("/api/auth/register", json.encodeToString(request)) { json.decodeFromString<AuthResponse>(it) }

    suspend fun login(request: LoginRequest): ApiResult<AuthResponse> =
        post("/api/auth/login", json.encodeToString(request)) { json.decodeFromString<AuthResponse>(it) }

    suspend fun refresh(token: String): ApiResult<RefreshResponse> =
        post("/api/auth/refresh", json.encodeToString(RefreshRequest(token))) { json.decodeFromString<RefreshResponse>(it) }

    suspend fun recover(request: PasswordRecoveryRequest): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val http = Request.Builder().url(ServerEndpoint.url() + "/api/auth/recover")
                .post(json.encodeToString(request).toRequestBody(JSON)).header("Accept", "application/json").build()
            client.newCall(http).execute().use { response ->
                if (response.isSuccessful) ApiResult.Success(response.code, Unit)
                else {
                    val body = response.body.string()
                    ApiResult.Error(response.code, runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull() ?: "RECOVERY_FAILED")
                }
            }
        }.getOrElse { ApiResult.Error(null, it.message ?: "NETWORK_ERROR") }
    }

    suspend fun me(): ApiResult<UserResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val http = Request.Builder().url(ServerEndpoint.url() + "/api/auth/me")
                .header("Accept", "application/json").build()
            client.newCall(http).execute().use { response ->
                val text = response.body.string()
                if (text.isBlank()) return@use ApiResult.Error(response.code, "EMPTY_RESPONSE")
                val value = runCatching { json.decodeFromString<UserResponse>(text) }.getOrElse {
                    val error = runCatching { json.decodeFromString<ErrorResponse>(text).error }.getOrNull()
                    return@use ApiResult.Error(response.code, error ?: "INVALID_SERVER_RESPONSE")
                }
                if (response.isSuccessful) ApiResult.Success(response.code, value)
                else ApiResult.Error(response.code, "HTTP_${response.code}")
            }
        }.getOrElse { ApiResult.Error(null, it.message ?: "NETWORK_ERROR") }
    }

    private suspend fun <T> post(path: String, body: String, decode: (String) -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(ServerEndpoint.url() + path)
                .post(body.toRequestBody(JSON))
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (text.isBlank()) return@use ApiResult.Error(response.code, "EMPTY_RESPONSE")
                val value = runCatching { decode(text) }.getOrElse {
                    val error = runCatching { json.decodeFromString<ErrorResponse>(text).error }.getOrNull()
                    return@use ApiResult.Error(response.code, error ?: "INVALID_SERVER_RESPONSE")
                }
                if (response.isSuccessful || response.code == 423 || response.code == 403) {
                    ApiResult.Success(response.code, value)
                } else ApiResult.Error(response.code, "HTTP_${response.code}")
            }
        }.getOrElse { ApiResult.Error(null, it.message ?: "NETWORK_ERROR") }
    }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val code: Int, val value: T) : ApiResult<T>
    data class Error(val code: Int?, val message: String) : ApiResult<Nothing>
}
