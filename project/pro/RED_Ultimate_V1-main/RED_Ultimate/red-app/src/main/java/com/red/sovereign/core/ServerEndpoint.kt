package com.red.sovereign.core

import android.content.Context
import com.red.sovereign.BuildConfig
import java.net.URI

/** Process-wide endpoint selected from a signed build default or verified local discovery. */
object ServerEndpoint {
    @Volatile private var current = normalize(BuildConfig.RED_SERVER_URL)
    private const val KEY = "server_url"

    fun initialize(context: Context) {
        SecureStore(context.applicationContext, "red_server_endpoint").get(KEY)?.let { stored ->
            runCatching { current = normalize(stored) }
        }
    }

    fun url(): String = current

    fun update(context: Context, value: String) {
        val normalized = normalize(value)
        SecureStore(context.applicationContext, "red_server_endpoint").put(KEY, normalized)
        current = normalized
    }

    private fun normalize(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "http" || uri.scheme == "https") { "Server URL must use HTTP(S)" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Invalid server URL" }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "Server URL must not contain a path" }
        return URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
    }
}
