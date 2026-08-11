package com.red.sovereign.auth

import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        executeWithRefresh(token, build(token)) { build(it) }
    }

    suspend fun download(path: String, target: File): ApiResult<File> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHENTICATED")
        fun build(access: String) = Request.Builder()
            .url(ServerEndpoint.url().trimEnd('/') + path)
            .header("Authorization", "Bearer $access")
            .get()
            .build()
        val result = executeResponseWithRefresh(token, build(token)) { build(it) }
        when (result) {
            is ApiResult.Error -> result
            is ApiResult.Success -> {
                result.value.use { response ->
                    if (!response.isSuccessful) return@withContext ApiResult.Error(response.code, response.body?.string())
                    target.parentFile?.mkdirs()
                    response.body?.byteStream()?.use { input -> FileOutputStream(target).use { input.copyTo(it) } }
                    ApiResult.Success(200, target)
                }
            }
        }
    }

    private suspend fun executeWithRefresh(originalToken: String, initial: Request, rebuild: (String) -> Request): ApiResult<String> {
        executeResponseWithRefresh(originalToken, initial, rebuild).let { result ->
            return when (result) {
                is ApiResult.Error -> result
                is ApiResult.Success -> result.value.use { response ->
                    if (response.isSuccessful) ApiResult.Success(response.code, response.body?.string().orEmpty())
                    else ApiResult.Error(response.code, response.body?.string())
                }
            }
        }
    }

    /**
     * ينفّذ الطلب مع تجديد التوكن عند 401 — مع حماية سباق التحديث.
     *
     * دون الحارس: طلبات متوازية تتلقى 401 معًا ⇒ كلٌّ يُرسل نفس توكن التحديث ⇒
     * الأول ينجح ويُبطل التوكن ⇒ الباقي يستعمل توكنًا مُبطلًا ⇒ الخادم يعتبره سرقة
     * ⇒ إبطال عائلة الجلسة كلها على كل الأجهزة (RefreshTokenService يعاقب بإعادة الاستخدام).
     *
     * الحل: Mutex مشترك (عبر كائنات العميل المختلفة) + فحص مزدوج — إن جدّد طلبٌ
     * آخر التوكن أثناء انتظارنا للقفل، نُعيد المحاولة بالتوكن الجديد دون تجديد.
     */
    private suspend fun executeResponseWithRefresh(originalToken: String, initial: Request, rebuild: (String) -> Request): ApiResult<okhttp3.Response> {
        val first = runCatching { client.newCall(initial).execute() }.getOrElse {
            ServerEndpoint.autoDiscover(tokens.context)
            return ApiResult.Error(null, "NETWORK_ERROR")
        }
        if (first.code != 401) return ApiResult.Success(first.code, first)
        first.close()

        return REFRESH_MUTEX.withLock {
            // فحص مزدوج: ربما جدّد طلب متوازٍ التوكن أثناء انتظارنا القفل
            val currentAccess = tokens.accessToken
            if (currentAccess != null && currentAccess != originalToken) {
                // التوكن تغيّر ⇒ نُعيد المحاولة بالجديد دون استدعاء refresh
                val retry = runCatching { client.newCall(rebuild(currentAccess)).execute() }.getOrElse {
                    ServerEndpoint.autoDiscover(tokens.context)
                    return@withLock ApiResult.Error(null, "NETWORK_ERROR")
                }
                if (retry.code != 401) return@withLock ApiResult.Success(retry.code, retry)
                retry.close()
                return@withLock ApiResult.Error(401, "UNAUTHENTICATED")
            }
            // لا يزال نفس التوكن ⇒ نجدّد فعليًا (استدعاء suspend مباشر — لا runBlocking)
            val refresh = tokens.refreshToken ?: return@withLock ApiResult.Error(401, "UNAUTHENTICATED")
            val refreshed = when (val result = auth.refresh(refresh)) {
                is ApiResult.Success -> result.value
                is ApiResult.Error -> return@withLock ApiResult.Error(401, "UNAUTHENTICATED")
            }
            tokens.updateTokens(refreshed)
            val second = runCatching { client.newCall(rebuild(refreshed.accessToken)).execute() }
                .getOrElse {
                    ServerEndpoint.autoDiscover(tokens.context)
                    return@withLock ApiResult.Error(null, "NETWORK_ERROR")
                }
            ApiResult.Success(second.code, second)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        /** حارس تحديث التوكن — مشترك عبر كل كائنات AuthorizedApiClient لتجنب السباق. */
        private val REFRESH_MUTEX = Mutex()
    }
}
