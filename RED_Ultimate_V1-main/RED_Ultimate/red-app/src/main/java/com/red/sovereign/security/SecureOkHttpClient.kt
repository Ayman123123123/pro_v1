package com.red.sovereign.security

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Central OkHttp factory: TLS policy, SPKI pins, security headers, and timeouts. */
object SecureOkHttpClient {
    @Volatile
    private var defaultClient: OkHttpClient? = null

    /**
     * Timeouts are **seconds**. Passing 800 here is 13 minutes, not 800ms.
     * LAN probes must build their own client with `TimeUnit.MILLISECONDS`.
     *
     * Yemen-hardened defaults: connect short for fast failover (10s),
     * read/write tolerant for slow 4G TTFB (30s), plus an overall
     * callTimeout deadline so hung chains can't pin the dispatcher.
     */
    fun build(
        context: Context,
        connectTimeout: Long = 10,
        readTimeout: Long = 30,
        writeTimeout: Long = 30,
        pingInterval: Long = 25,
        callTimeout: Long = 90
    ): OkHttpClient = baseBuilder(context, connectTimeout, readTimeout, writeTimeout)
        .callTimeout(callTimeout, TimeUnit.SECONDS)
        .pingInterval(pingInterval, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .followRedirects(false)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(5, 2, TimeUnit.MINUTES))
        .build()

    fun getDefault(context: Context): OkHttpClient {
        defaultClient?.let { return it }
        return synchronized(this) {
            defaultClient ?: build(context.applicationContext)
                .newBuilder()
                .cache(Cache(File(context.applicationContext.cacheDir, "http"), DEFAULT_CACHE_BYTES))
                .build()
                .also { defaultClient = it }
        }
    }

    fun reset() {
        synchronized(this) {
            defaultClient?.dispatcher?.cancelAll()
            defaultClient?.connectionPool?.evictAll()
            runCatching { defaultClient?.cache?.close() }
            defaultClient = null
        }
    }

    fun buildWithTimeouts(context: Context, connect: Long = 10, read: Long = 30, write: Long = 30): OkHttpClient =
        build(context, connectTimeout = connect, readTimeout = read, writeTimeout = write)

    /**
     * Fast client for ICE/TURN credential fetches on the call-setup path:
     * fails fast (10s total) instead of blocking media negotiation.
     */
    fun buildIceClient(context: Context): OkHttpClient =
        baseBuilder(context, connectTimeout = 5, readTimeout = 8, writeTimeout = 8)
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun buildWebSocketClient(context: Context): OkHttpClient =
        baseBuilder(context, connectTimeout = 15, readTimeout = 0, writeTimeout = 20)
            .pingInterval(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    fun buildMediaClient(context: Context): OkHttpClient =
        baseBuilder(context, connectTimeout = 30, readTimeout = 120, writeTimeout = 60)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    fun buildUploadClient(context: Context): OkHttpClient =
        baseBuilder(context, connectTimeout = 30, readTimeout = 120, writeTimeout = 300)
            .retryOnConnectionFailure(true)
            .build()

    private fun baseBuilder(
        context: Context,
        connectTimeout: Long,
        readTimeout: Long,
        writeTimeout: Long
    ): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
        .readTimeout(readTimeout, TimeUnit.SECONDS)
        .writeTimeout(writeTimeout, TimeUnit.SECONDS)
        .addInterceptor(SecurityHeadersInterceptor())
        .apply {
            configuredPinner()?.let(::certificatePinner)
        }

    private fun configuredPinner(): okhttp3.CertificatePinner? {
        if (!CertificatePinner.isEnabled) return null
        val configured = CertificatePinner.allPins()
        if (configured.isEmpty()) return null
        return okhttp3.CertificatePinner.Builder().apply {
            configured.forEach { (host, pins) -> add(host, *pins.toTypedArray()) }
        }.build()
    }

    private const val DEFAULT_CACHE_BYTES = 10L * 1024L * 1024L
}
