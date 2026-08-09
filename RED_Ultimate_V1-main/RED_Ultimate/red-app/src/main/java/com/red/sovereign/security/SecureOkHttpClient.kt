package com.red.sovereign.security

import android.content.Context
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Factory for creating OkHttp clients with certificate pinning enabled.
 * Provides a secure HTTP client for all API communication.
 */
object SecureOkHttpClient {

    private var defaultClient: OkHttpClient? = null

    /**
     * Build a secure OkHttp client with certificate pinning.
     */
    fun build(
        context: Context,
        connectTimeout: Long = 15,
        readTimeout: Long = 20,
        writeTimeout: Long = 20,
        pingInterval: Long = 25,
        cacheSize: Long = 10 * 1024 * 1024 // 10 MB
    ): OkHttpClient {
        val certificatePinner = if (CertificatePinner.isEnabled) {
            okhttp3.CertificatePinner.Builder().apply {
                CertificatePinner.allPins().forEach { (host, pinSet) ->
                    val pins = pinSet.joinToString(",") { it }
                    add(host, pins)
                }
            }.build()
        } else {
            null
        }

        return OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .pingInterval(pingInterval, TimeUnit.SECONDS)
            .certificatePinner(certificatePinner)
            .addInterceptor(SecurityHeadersInterceptor())
            .followSslRedirects(false)
            .followRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Get or create the default secure client for the app.
     */
    fun getDefault(context: Context): OkHttpClient {
        return defaultClient ?: run {
            defaultClient = build(context)
            defaultClient!!
        }
    }

    /**
     * Reset the default client (useful for configuration changes).
     */
    fun reset() {
        defaultClient = null
    }

    /**
     * Create a client with custom timeouts.
     */
    fun buildWithTimeouts(
        context: Context,
        connect: Long = 15,
        read: Long = 20,
        write: Long = 20
    ): OkHttpClient {
        return build(
            context = context,
            connectTimeout = connect,
            readTimeout = read,
            writeTimeout = write
        )
    }

    /**
     * Create a client for WebSocket connections.
     */
    fun buildWebSocketClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .certificatePinner(
                if (CertificatePinner.isEnabled) {
                    okhttp3.CertificatePinner.Builder().apply {
                        CertificatePinner.allPins().forEach { (host, pinSet) ->
                            add(host, pinSet.joinToString(","))
                        }
                    }.build()
                } else {
                    null
                }
            )
            .build()
    }

    /**
     * Create a client for media downloads (larger timeout, no pinning for CDN).
     */
    fun buildMediaClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Create a client for uploads.
     */
    fun buildUploadClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()
    }
}
