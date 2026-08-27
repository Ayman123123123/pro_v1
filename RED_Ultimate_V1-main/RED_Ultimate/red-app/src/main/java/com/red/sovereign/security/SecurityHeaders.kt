package com.red.sovereign.security

import android.util.Log
import com.red.sovereign.BuildConfig
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor to add security-related headers to all requests.
 */
class SecurityHeadersInterceptor : Interceptor {

    /**
     * Add security headers to every request.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            // Prevent caching of sensitive data
            .header("Cache-Control", "no-store, no-cache, must-revalidate, private")
            .header("Pragma", "no-cache")
            .header("X-Requested-With", "XMLHttpRequest")
            // Security headers
            .header("X-Content-Type-Options", "nosniff")
            .header("X-Frame-Options", "DENY")
            .header("X-XSS-Protection", "1; mode=block")
            .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
            // Client info
            .header("X-Client-Version", BuildConfig.VERSION_NAME)
            .header("X-Client-Platform", "Android")
            .header("X-Client-SDK", "RED-Sovereign-Android")
            .build()

        return chain.proceed(request)
    }

    companion object {
        /**
         * Build security headers map for manual use.
         */
        fun buildSecurityHeaders(): Map<String, String> {
            return mapOf(
                "Cache-Control" to "no-store, no-cache, must-revalidate, private",
                "Pragma" to "no-cache",
                "X-Content-Type-Options" to "nosniff",
                "X-Frame-Options" to "DENY",
                "X-XSS-Protection" to "1; mode=block",
                "Strict-Transport-Security" to "max-age=31536000; includeSubDomains"
            )
        }
    }
}

/**
 * Interceptor to log requests for debugging (disabled in release).
 *
 * ## عطلان حرجان كانا هنا
 *
 * 1. **قراءة مدمرة للجسم**: `response.body?.string()` تستهلك الـ stream مرة
 *    واحدة فقط. كل استجابة تمر بهذا المعترض في وضع التطوير كانت تصل إلى طبقة
 *    العميل فارغة — أي أن تفعيل تسجيل التشخيص كان يُعطّل التطبيق نفسه.
 *    الآن: لا يُقرأ الجسم أبدًا، يُسجَّل طوله فقط.
 *
 * 2. **تسريب المحتوى والأسرار**: كان يسجّل جسم الاستجابة كاملًا (رسائل E2EE
 *    مفكوكة، مرفقات) و`request.url` كاملًا بما فيه معاملات الاستعلام التي قد
 *    تحمل رموز جلسة. الآن: الجسم محجوب، الاستعلام مُزال، والترويسات الحساسة
 *    مُقنَّعة بقائمة موسّعة (تشمل رموز الأجهزة وFCM وAPI).
 *
 * `logger` قابل للحقن حتى يتمكن الاختبار من إثبات أن الأسرار لا تُسجَّل.
 */
class LoggingInterceptor(
    private val isDebug: Boolean = false,
    private val logger: (String) -> Unit = { message -> Log.d(TAG, message) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isDebug) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // لا نسجل الجسم أو رابط الاستعلام: قد يتضمنان رسائل مشفرة، رموز جلسة أو مرفقات.
        logger("→ ${request.method} ${request.url.newBuilder().query(null).build()}")
        logger("  Headers: ${redactHeaders(request.headers)}")

        val response = chain.proceed(request)
        val duration = System.currentTimeMillis() - startTime

        // لا تستدعِ ResponseBody.string(): هي قراءة مدمرة تمنع طبقة العميل من فك الاستجابة.
        logger("← ${response.code} ${response.request.url.newBuilder().query(null).build()} (${duration}ms)")
        logger("  Headers: ${redactHeaders(response.headers)}")
        logger("  Body: [omitted; ${response.body?.contentLength() ?: -1L} bytes]")

        return response
    }

    private fun redactHeaders(headers: Headers): String = headers.names()
        .sorted()
        .joinToString(prefix = "{", postfix = "}") { name ->
            val value = if (name.lowercase() in SENSITIVE_HEADERS) "██REDACTED██" else headers[name] ?: ""
            "$name=$value"
        }

    private companion object {
        const val TAG = "LoggingInterceptor"
        val SENSITIVE_HEADERS = setOf(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-access-token", "x-device-token", "x-fcm-token", "x-api-key"
        )
    }
}
