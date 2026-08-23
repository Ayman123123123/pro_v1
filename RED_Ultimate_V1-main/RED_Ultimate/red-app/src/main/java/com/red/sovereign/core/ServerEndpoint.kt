package com.red.sovereign.core

import android.content.Context
import com.red.sovereign.BuildConfig
import com.red.sovereign.auth.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URI

/** Process-wide endpoint selected from a signed build default or verified local discovery. */
object ServerEndpoint {
    private const val KEY = "server_url"

    /**
     * كانت هذه العناوين افتراضات تطوير قديمة وليست خوادم RED على شبكة المستخدم.
     * لا نرحّل أي عنوان آخر حتى لا نكسر إعدادًا يدويًا مشروعًا.
     */
    private val deprecatedDefaults = setOf(
        "http://127.0.0.1:8088",
        "http://localhost:8088",
        "http://192.168.11.210:8088"
    )

    private fun buildDefaultUrl(): String = runCatching { normalize(BuildConfig.RED_SERVER_URL) }
        .getOrElse { normalize("http://192.168.11.131:8088") }

    @Volatile private var current = buildDefaultUrl()
    private var onEndpointChangedListener: ((String) -> Unit)? = null

    fun initialize(context: Context) {
        val store = SecureStore(context.applicationContext, "red_server_endpoint")
        val stored = store.get(KEY) ?: return
        val normalized = runCatching { normalize(stored) }.getOrNull() ?: return
        if (normalized in deprecatedDefaults) {
            // ترحيل محدد للعناوين القديمة فقط؛ الحساب والرموز وبقية التفضيلات لا تتغير.
            current = buildDefaultUrl()
            store.put(KEY, current)
        } else {
            current = normalized
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

    private val discoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Triggers smart background auto-discovery when connection to active IP fails.
     */
    fun autoDiscover(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        discoveryScope.launch {
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
        if (uri.scheme == "http") {
            // cleartext HTTP مسموح فقط لعناوين الشبكة المحلية (LAN/محاكي) —
            // لا لأي خادم عام على الإنترنت. هذا الحارس يكمّل قاعدة
            // network_security_config التي لا يمكنها مطابقة عناوين IP الحرفية.
            require(isLocalCleartextHost(uri.host)) { "Cleartext HTTP allowed only for local (LAN) servers" }
        }
        return URI(uri.scheme, null, uri.host, uri.port, null, null, null).toString().trimEnd('/')
    }

    /** فحص حرفي (بدون DNS) لأن cleartext محلي فقط: localhost، مضيف المحاكي، أو نطاق خاص. */
    private fun isLocalCleartextHost(host: String): Boolean {
        if (host == "localhost" || host == "10.0.2.2" || host.endsWith(".local")) return true
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 127 || octets[0] == 10 ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] in 16..31)
    }
}
