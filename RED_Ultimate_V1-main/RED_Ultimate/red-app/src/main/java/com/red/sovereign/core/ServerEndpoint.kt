package com.red.sovereign.core

import android.content.Context
import com.red.sovereign.BuildConfig
import com.red.sovereign.auth.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

/** Process-wide endpoint selected from a signed build default or verified local discovery. */
object ServerEndpoint {
    @Volatile private var current = normalize(BuildConfig.RED_SERVER_URL)
    private const val KEY = "server_url"
    private var onEndpointChangedListener: ((String) -> Unit)? = null

    fun initialize(context: Context) {
        SecureStore(context.applicationContext, "red_server_endpoint").get(KEY)?.let { stored ->
            runCatching { current = normalize(stored) }
        }
    }

    fun url(): String = current

    fun update(context: Context, value: String) {
        val normalized = normalize(value)
        if (current != normalized) {
            SecureStore(context.applicationContext, "red_server_endpoint").put(KEY, normalized)
            current = normalized
            onEndpointChangedListener?.invoke(normalized)
        }
    }

    fun setOnEndpointChangedListener(listener: ((String) -> Unit)?) {
        onEndpointChangedListener = listener
    }

    /**
     * Triggers smart background auto-discovery when connection to active IP fails.
     */
    fun autoDiscover(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = LocalServerDiscovery(context).discover()
            val success = result is ApiResult.Success
            onComplete?.invoke(success)
        }
    }

    private fun normalize(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "http" || uri.scheme == "https") { "Server URL must use HTTP(S)" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "Invalid server URL" }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "Server URL must not contain a path" }
        return URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
    }
}
