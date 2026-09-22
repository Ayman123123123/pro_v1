package com.red.sovereign.core

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * عميل API موحد - يعمل على كل الشبكات المحلية وكل الشبكات
 * 
 * أفضل من واتساب وتيليجرام:
 * - يحاول كل الشبكات المحلية تلقائياً
 * - تبديل تلقائي عند فشل الشبكة
 * - قياس جودة وتكيف
 * - يعمل بدون إنترنت عبر P2P LAN
 * - إعادة محاولة ذكية
 */
class UnifiedApiClient(private val context: Context) {
    
    private val client: OkHttpClient = SecureOkHttpClient.build(
        context = context,
        connectTimeout = 5,
        readTimeout = 10,
        writeTimeout = 10
    ).newBuilder()
        .retryOnConnectionFailure(true)
        .build()
    
    private val discoveryClient = OkHttpClient.Builder()
        .connectTimeout(250, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .writeTimeout(400, TimeUnit.MILLISECONDS)
        .callTimeout(800, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()
    
    /**
     * طلب مع تبديل تلقائي للشبكات
     */
    suspend fun requestWithFallback(
        path: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        
        // 1. جرب العنوان الحالي أولاً
        val currentUrl = ServerEndpoint.url()
        val primaryResult = tryRequest(currentUrl, path, method, body, headers)
        if (primaryResult is ApiResult.Success) {
            return@withContext primaryResult
        }
        
        Log.w("UnifiedApi", "Primary failed: $currentUrl$path - ${primaryResult}")
        
        // 2. جرب الشبكات المحلية المكتشفة
        val candidates = UnifiedNetworkManager.generateCandidatesForDiscovery()
        val ports = listOf(8088, 8080, 443, 80)
        
        for (ip in candidates.take(20)) { // أول 20 مرشح
            for (port in ports) {
                val candidateUrl = "http://$ip:$port"
                val result = tryRequest(candidateUrl, path, method, body, headers, quick = true)
                if (result is ApiResult.Success) {
                    // وجدنا خادم صالح - حدث العنوان
                    ServerEndpoint.update(context, candidateUrl)
                    Log.i("UnifiedApi", "✅ Found valid server at $candidateUrl - Updated endpoint")
                    return@withContext result
                }
            }
        }
        
        // 3. جرب اكتشاف mDNS
        val discovered = UnifiedNetworkManager.discoveredServersFlow.value.values
        for (serverUrl in discovered) {
            val result = tryRequest(serverUrl, path, method, body, headers, quick = true)
            if (result is ApiResult.Success) {
                ServerEndpoint.update(context, serverUrl)
                Log.i("UnifiedApi", "✅ Found via mDNS: $serverUrl")
                return@withContext result
            }
        }
        
        // 4. فشل كل المسارات
        Log.e("UnifiedApi", "❌ All networks failed for $path")
        return@withContext ApiResult.Error(null, "ALL_NETWORKS_FAILED")
    }
    
    private fun tryRequest(
        baseUrl: String,
        path: String,
        method: String,
        body: String?,
        headers: Map<String, String>,
        quick: Boolean = false
    ): ApiResult<String> {
        return try {
            val url = "$baseUrl${if (path.startsWith("/")) path else "/$path"}"
            val requestBuilder = Request.Builder().url(url)
            
            // إضافة headers
            for ((key, value) in headers) {
                requestBuilder.addHeader(key, value)
            }
            
            // طريقة الطلب
            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> {
                    val requestBody = body?.let { okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), it) }
                    requestBuilder.post(requestBody ?: okhttp3.RequestBody.create(null, ""))
                }
                "PUT" -> {
                    val requestBody = body?.let { okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), it) }
                    requestBuilder.put(requestBody ?: okhttp3.RequestBody.create(null, ""))
                }
                "DELETE" -> requestBuilder.delete()
            }
            
            val clientToUse = if (quick) discoveryClient else client
            val response = clientToUse.newCall(requestBuilder.build()).execute()
            
            val responseBody = response.body()?.string().orEmpty()
            
            if (response.isSuccessful) {
                ApiResult.Success(response.code(), responseBody)
            } else {
                ApiResult.Error(response.code(), responseBody)
            }
        } catch (e: Exception) {
            ApiResult.Error(null, e.message ?: "NETWORK_ERROR")
        }
    }
    
    /**
     * فحص صحة الخادم مع توقيع
     */
    suspend fun healthCheck(baseUrl: String? = null): ApiResult<String> = withContext(Dispatchers.IO) {
        val url = baseUrl ?: ServerEndpoint.url()
        try {
            val request = Request.Builder()
                .url("$url/health")
                .get()
                .build()
            
            val response = discoveryClient.newCall(request).execute()
            val body = response.body()?.string().orEmpty()
            
            // تحقق من توقيع يونس
            if (YounesServerSignature.isReadyHealth(body)) {
                ApiResult.Success(response.code(), body)
            } else {
                ApiResult.Error(response.code(), "INVALID_SIGNATURE")
            }
        } catch (e: Exception) {
            ApiResult.Error(null, e.message)
        }
    }
    
    /**
     * اكتشاف خوادم RED على الشبكة
     */
    suspend fun discoverRedServers(): List<String> = withContext(Dispatchers.IO) {
        val found = mutableListOf<String>()
        val candidates = UnifiedNetworkManager.generateCandidatesForDiscovery()
        
        // فحص متوازي سريع
        val jobs = candidates.take(50).map { ip ->
            kotlinx.coroutines.async {
                val url = "http://$ip:8088"
                val health = healthCheck(url)
                if (health is ApiResult.Success) {
                    url
                } else null
            }
        }
        
        for (job in jobs) {
            val result = job.await()
            if (result != null) {
                found.add(result)
            }
        }
        
        // إضافة مكتشفات mDNS
        found.addAll(UnifiedNetworkManager.discoveredServersFlow.value.values)
        
        found.distinct()
    }
    
    companion object {
        private const val TAG = "UnifiedApiClient"
    }
}
