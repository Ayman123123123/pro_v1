package com.red.sovereign.security

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Factory for OkHttp clients shared by REST, WebSocket, media download and upload. */
object SecureOkHttpClient {
    private var defaultClient: OkHttpClient? = null

    fun build(
        context: Context,
        connectTimeout: Long = 15,
        readTimeout: Long = 20,
        writeTimeout: Long = 20,
        pingInterval: Long = 25
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .pingInterval(pingInterval, TimeUnit.SECONDS)
            .addInterceptor(SecurityHeadersInterceptor())
            .followSslRedirects(false)
            .followRedirects(false)
            .retryOnConnectionFailure(true)

        applyCertificatePins(builder)
        return builder.build()
    }

    fun getDefault(context: Context): OkHttpClient = defaultClient ?: build(context).also { defaultClient = it }

    fun reset() {
        defaultClient = null
    }

    fun buildWithTimeouts(
        context: Context,
        connect: Long = 15,
        read: Long = 20,
        write: Long = 20
    ): OkHttpClient = build(context, connectTimeout = connect, readTimeout = read, writeTimeout = write)

    fun buildWebSocketClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .addInterceptor(SecurityHeadersInterceptor())
        applyCertificatePins(builder)
        return builder.build()
    }

    fun buildMediaClient(context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun buildUploadClient(context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun applyCertificatePins(builder: OkHttpClient.Builder) {
        if (!CertificatePinner.isEnabled) return
        val configuredPins = CertificatePinner.allPins().filterValues { it.isNotEmpty() }
        if (configuredPins.isEmpty()) return

        val okHttpPinner = okhttp3.CertificatePinner.Builder().apply {
            configuredPins.forEach { (host, pins) ->
                add(host, *pins.toTypedArray())
            }
        }.build()
        builder.certificatePinner(okHttpPinner)
    }
}
