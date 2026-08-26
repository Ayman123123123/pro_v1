package com.red.sovereign.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.red.sovereign.auth.AuthApi
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.security.SecureOkHttpClient

/**
 * تجديد توكن في الخلفية كل 7 أيام حتى لو لم يفتح التطبيق أبداً — يمنع انتهاء 30 يوم.
 * StrongBox + WorkManager = لا يخرج أبداً.
 */
class AuthRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val tokens = TokenStore(applicationContext)
            val refresh = tokens.refreshToken ?: return Result.success()
            val api = AuthApi(applicationContext, SecureOkHttpClient.build(applicationContext, connectTimeout=8, readTimeout=10, writeTimeout=8))
            val result = api.refresh(refresh)
            if (result is com.red.sovereign.auth.ApiResult.Success) {
                tokens.updateTokens(result.value)
                Result.success()
            } else if (result is com.red.sovereign.auth.ApiResult.Error && (result.code==401 || result.code==403)) {
                // انتهاء حقيقي — لا إعادة محاولة
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class SyncPollWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val tokens = TokenStore(context)
            if (tokens.accessToken.isNullOrBlank()) return Result.success()
            // فقط إذا WebSocket غير متصل — احتياط 60s
            // نستخدم REST للتحقق من جهات الاتصال والمجموعات
            val client = com.red.sovereign.auth.AuthorizedApiClient(tokens)
            // فحص سريع — لا نعالج النتيجة هنا، ViewModel سيجمعها عند الاستيقاظ
            client.request("GET", "/api/contacts")
            client.request("GET", "/api/groups")
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }
}
