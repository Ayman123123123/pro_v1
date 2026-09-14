package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Sovereign push registration — UnifiedPush with a self-hosted distributor (ntfy).
 *
 * No Google push SDK of any kind: the distributor app holds the single
 * persistent connection and wakes this app via [com.red.sovereign.push.RedPushService]
 * even when the process is dead. The server POSTs wake payloads straight to the
 * endpoint URL the distributor issued (stored server-side per device).
 *
 * Callers ([com.red.sovereign.core.CallBootReceiver],
 * [com.red.sovereign.core.CallSystemIntegration],
 * [com.red.sovereign.core.AppStartupCoordinator]) just invoke [register]; distributor
 * choice persists via [UnifiedPush.saveDistributor] and re-registration is idempotent.
 */
object VoipPushRegistrar {
    private const val TAG = "VoipPushRegistrar"

    /** Shown by the distributor UI to identify this registration. */
    const val DISTRIBUTOR_MESSAGE = "RED calls & messages"

    /** Preferred distributors when several are installed (self-hosted ntfy first). */
    private val PREFERRED_DISTRIBUTORS = setOf("io.heckel.ntfy", "io.heckel.ntfy.debug")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Ensures a distributor is saved, (re-)registers, and re-uploads the last
     * known endpoint. Safe to call on every app start and on BOOT_COMPLETED.
     */
    fun register(context: Context) {
        scope.launch {
            val app = context.applicationContext
            try {
                if (UnifiedPush.getAckDistributor(app).isNullOrBlank()) {
                    val all = runCatching { UnifiedPush.getDistributors(app) }.getOrDefault(emptyList())
                    if (all.isEmpty()) {
                        Log.i(TAG, "no UnifiedPush distributor installed — push wake unavailable until one is")
                        return@launch
                    }
                    val pick = all.firstOrNull { it in PREFERRED_DISTRIBUTORS } ?: all.first()
                    UnifiedPush.saveDistributor(app, pick)
                    Log.i(TAG, "UnifiedPush distributor saved: $pick")
                }
                UnifiedPush.register(app, messageForDistributor = DISTRIBUTOR_MESSAGE)
                // Re-upload the last endpoint: the server may have restarted or pruned tokens.
                val tokens = TokenStore(app)
                val endpoint = tokens.pushEndpoint
                if (!endpoint.isNullOrBlank() && !tokens.accessToken.isNullOrBlank()) {
                    uploadEndpoint(tokens, endpoint)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "UnifiedPush register failed", t)
            }
        }
    }

    /** Uploads a fresh endpoint URL from [com.red.sovereign.push.RedPushService.onNewEndpoint]. */
    fun uploadEndpointNow(context: Context, endpoint: String) {
        if (endpoint.isBlank()) return
        scope.launch {
            val tokens = TokenStore(context.applicationContext)
            tokens.savePushEndpoint(endpoint)
            if (tokens.accessToken.isNullOrBlank()) return@launch
            uploadEndpoint(tokens, endpoint)
        }
    }

    /** Distributor packages installed on the device (for the settings picker). */
    fun availableDistributors(context: Context): List<String> =
        runCatching { UnifiedPush.getDistributors(context.applicationContext) }.getOrDefault(emptyList())

    /** Currently saved distributor package, or null when none. */
    fun currentDistributor(context: Context): String? =
        runCatching { UnifiedPush.getAckDistributor(context.applicationContext) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Last endpoint URL issued by the distributor, or null. */
    fun currentEndpoint(context: Context): String? =
        TokenStore(context.applicationContext).pushEndpoint?.takeIf { it.isNotBlank() }

    /**
     * Switches to [pkg] (must be installed) and re-registers.
     * @return false when [pkg] is not an installed distributor.
     */
    fun useDistributor(context: Context, pkg: String): Boolean {
        val app = context.applicationContext
        if (pkg.isBlank() || pkg !in availableDistributors(app)) return false
        try {
            UnifiedPush.saveDistributor(app, pkg)
        } catch (t: Throwable) {
            Log.w(TAG, "saveDistributor($pkg) failed", t)
            return false
        }
        register(app)
        return true
    }

    /** Unregisters from the distributor and drops the local endpoint (logout path). */
    fun unregister(context: Context) {
        scope.launch {
            val app = context.applicationContext
            runCatching { UnifiedPush.unregister(app) }
                .onFailure { Log.w(TAG, "UnifiedPush unregister failed", it) }
            TokenStore(app).clearPushEndpoint()
        }
    }

    internal fun uploadEndpoint(tokens: TokenStore, endpoint: String) {
        val body = JSONObject()
            .put("token", endpoint)
            .put("platform", "ANDROID")
            .toString()
        runCatching { AuthorizedApiClient(tokens).request("POST", "/api/devices/push-token", body) }
            .onFailure { Log.w(TAG, "push-token upload failed", it) }
    }
}
