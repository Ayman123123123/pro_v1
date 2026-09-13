package com.red.sovereign.auth

import com.red.sovereign.core.LocalServerDiscovery
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
        // بلا أي شبكة (وضع طيران/انقطاع كامل): فشل فوري واضح بدل حرق المهلات —
        // يعمل على كل الأنواع (WiFi/4G/هوائي/loopback المحلي دون إنترنت يبقى متصلاً).
        if (!isNetworkAvailable(tokens.context)) return@withContext ApiResult.Error(null, "OFFLINE")
        fun build(access: String) = Request.Builder()
            .url(ServerEndpoint.url().trimEnd('/') + path)
            .header("Authorization", "Bearer $access")
            .method(method, if (method == "GET" || method == "DELETE") null else body ?: ByteArray(0).toRequestBody(JSON))
            .build()
        executeWithRefresh(token, build(token)) { build(it) }
    }

    suspend fun download(path: String, target: File): ApiResult<File> = withContext(Dispatchers.IO) {
        val token = tokens.accessToken ?: return@withContext ApiResult.Error(401, "UNAUTHENTICATED")
        if (!isNetworkAvailable(tokens.context)) return@withContext ApiResult.Error(null, "OFFLINE")
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
                    if (!response.isSuccessful) return@withContext ApiResult.Error(response.code, response.body?.string().orEmpty())
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
                    if (response.isSuccessful) {
                        // حارس البوابة الأسيرة: صفحة login HTML بكود 200 على مسار /api/
                        // ليست رد خادم — بوابة فندق/مطار اختطفت الاتصال.
                        val ctype = response.header("Content-Type").orEmpty().lowercase()
                        if (ctype.contains("text/html")) {
                            return ApiResult.Error(null, "NETWORK_ERROR")
                        }
                        ApiResult.Success(response.code, response.body?.string().orEmpty())
                    }
                    else ApiResult.Error(response.code, response.body?.string().orEmpty())
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
        // Yemen-hardened: محاولة ثانية واحدة لطلبات القراءة عند فشل الشبكة
        // (4G متذبذب) بتراجع 600ms+jitter — الكتابة تبقى محاولة واحدة (idempotency).
        val first = runCatching { client.newCall(initial).execute() }.getOrElse {
            if (isSafeReadMethod(initial.method)) {
                kotlinx.coroutines.delay((600 + (0..300).random()).toLong())
                runCatching { client.newCall(initial).execute() }.getOrNull()
                    ?: run {
                        recoverReadAfterEndpointDiscovery(initial, originalToken, rebuild)
                            ?: return ApiResult.Error(null, "NETWORK_ERROR")
                    }
            } else {
                recoverReadAfterEndpointDiscovery(initial, originalToken, rebuild)
                    ?: return ApiResult.Error(null, "NETWORK_ERROR")
            }
        }
        // إعادة واحدة لأخطاء البوابة العابرة (502/503/504) لطلبات القراءة فقط.
        if (first.code in setOf(502, 503, 504) && isSafeReadMethod(initial.method)) {
            first.close()
            kotlinx.coroutines.delay((1000 + (0..500).random()).toLong())
            val retry = runCatching { client.newCall(rebuild(originalToken)).execute() }.getOrNull()
            if (retry != null) {
                if (retry.code != 401) return ApiResult.Success(retry.code, retry)
                retry.close()
            }
        }
        if (first.code != 401) return ApiResult.Success(first.code, first)
        first.close()

        return REFRESH_MUTEX.withLock {
            // فحص مزدوج: ربما جدّد طلب متوازٍ التوكن أثناء انتظارنا القفل
            val currentAccess = tokens.accessToken
            if (currentAccess != null && currentAccess != originalToken) {
                // التوكن تغيّر ⇒ نُعيد المحاولة بالجديد دون استدعاء refresh
                val retryRequest = rebuild(currentAccess)
                val retry = runCatching { client.newCall(retryRequest).execute() }.getOrElse {
                    recoverReadAfterEndpointDiscovery(retryRequest, currentAccess, rebuild)
                        ?: return@withLock ApiResult.Error(null, "NETWORK_ERROR")
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
            val secondRequest = rebuild(refreshed.accessToken)
            val second = runCatching { client.newCall(secondRequest).execute() }
                .getOrElse {
                    recoverReadAfterEndpointDiscovery(secondRequest, refreshed.accessToken, rebuild)
                        ?: return@withLock ApiResult.Error(null, "NETWORK_ERROR")
                }
            ApiResult.Success(second.code, second)
        }
    }

    /**
     * عند تبدل عنوان خادم LAN لا يمكن إعادة إرسال الكتابة بأمان؛ فـ POST/PATCH
     * قد يكرر رسالة أو أمرًا إداريًا. تعاد المحاولة مرة واحدة لطلبات القراءة فقط،
     * وبعد نجاح اكتشاف عنوان يحمل بصمة يونس، وبناء Request جديد بالعنوان المحدث.
     *
     * النسخة السابقة كانت تستدعي `ServerEndpoint.autoDiscover` (غير محجوزة النتيجة)
     * ثم تُرجع NETWORK_ERROR دائمًا: الاكتشاف يحدث لكن الطلب الحالي يفشل رغم أن
     * الخادم صار معروفًا — فتظهر شاشة «تعذر الاتصال» بينما الاتصال متاح.
     */
    private suspend fun recoverReadAfterEndpointDiscovery(
        failedRequest: Request,
        accessToken: String,
        rebuild: (String) -> Request
    ): okhttp3.Response? {
        if (!isSafeReadMethod(failedRequest.method)) {
            // كتابة: نكتشف العنوان للطلبات القادمة، ولا نعيد إرسال هذه أبدًا.
            ServerEndpoint.autoDiscover(tokens.context)
            return null
        }
        val discovered = LocalServerDiscovery(tokens.context).discover()
        if (discovered !is ApiResult.Success) return null
        // rebuild يقرأ ServerEndpoint.url() المحدَّث، فيصيب العنوان الجديد.
        return runCatching { client.newCall(rebuild(accessToken)).execute() }.getOrNull()
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        /** حارس تحديث التوكن — مشترك عبر كل كائنات AuthorizedApiClient لتجنب السباق. */
        private val REFRESH_MUTEX = Mutex()

        /** أي واجهة نشطة (WiFi/خلوي/إيثرنت/VPN/loopback) — بلا شبكة إطلاقاً = OFFLINE فوري. */
        fun isNetworkAvailable(context: android.content.Context): Boolean = runCatching {
            val cm = context.applicationContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) ||
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        }.getOrDefault(true)

        /**
         * إعادة المحاولة بعد إعادة اكتشاف العنوان آمنة للقراءة فقط.
         * GET/HEAD/OPTIONS عديمة الأثر (idempotent بلا تأثير جانبي)، أما
         * POST/PUT/PATCH/DELETE فإعادة إرسالها قد تُنشئ رسالة مكرّرة أو تُعيد
         * تنفيذ أمر إداري.
         */
        internal fun isSafeReadMethod(method: String): Boolean =
            method.uppercase() in setOf("GET", "HEAD", "OPTIONS")
    }
}
