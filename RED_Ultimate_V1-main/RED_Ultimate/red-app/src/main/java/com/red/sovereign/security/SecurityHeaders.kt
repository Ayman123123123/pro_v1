package com.red.sovereign.security

import android.util.Log
import com.red.sovereign.BuildConfig
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
 */
class LoggingInterceptor(private val isDebug: Boolean = false) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isDebug) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val response = chain.proceed(request)

        val duration = System.currentTimeMillis() - startTime

        // Log request (sanitize sensitive headers)
        val sanitizedHeaders = request.headers.names()
            .filter { it.lowercase() !in setOf("authorization", "cookie", "x-access-token") }
            .associateWith { request.header(it).orEmpty() }
        Log.d(TAG, "→ ${request.method} ${request.url}")
        Log.d(TAG, "  Headers: $sanitizedHeaders")

        // Log response
        Log.d(TAG, "← ${response.code} ${response.request?.url} (${duration}ms)")
        val sanitizedRespHeaders = response.headers.names()
            .filter { it.lowercase() !in setOf("set-cookie", "authorization") }
            .associateWith { response.header(it).orEmpty() }
        Log.d(TAG, "  Headers: $sanitizedRespHeaders")

        response.body?.string()?.let { body ->
            if (body.length < 5000) {
                Log.d(TAG, "  Body: $body")
            } else {
                Log.d(TAG, "  Body: [${body.length} bytes - truncated]")
            }
        }

        return response
    }

    private companion object {
        const val TAG = "LoggingInterceptor"
    }
}
