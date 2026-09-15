package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.SecureStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Sovereign push registration - UnifiedPush with a self-hosted distributor (ntfy).
 *
 * No Google push SDK of any kind: the distributor app holds the single
 * persistent connection and wakes this app via [com.red.sovereign.push.RedPushService]
 * even when the process is dead. The server POSTs wake payloads straight to the
 * endpoint URL the distributor issued (stored server-side per device).
 *
 * Reconnect policy: registration and endpoint re-upload share ONE exponential
 * backoff loop (base 15s, x2, capped 30min, +jitter). Re-registration is
 * idempotent and endpoint uploads are de-duplicated by URL + freshness window,
 * so a flapping network does not spam the backend or drain the battery.
 *
 * Callers ([com.red.sovereign.calls.CallBootReceiver],
 * [com.red.sovereign.core.AppStartupCoordinator]) just invoke [register]; distributor
 * choice persists via [UnifiedPush.saveDistributor] and re-registration is idempotent.
 */
object VoipPushRegistrar {
    private const val TAG = "VoipPushRegistrar"

    /** Shown by the distributor UI to identify this registration. */
    const val DISTRIBUTOR_MESSAGE = "RED calls & messages"

    /** Preferred distributors when several are installed (self-hosted ntfy first). */
    private val PREFERRED_DISTRIBUTORS = setOf("io.heckel.ntfy", "io.heckel.ntfy.debug")

    // Exponential reconnect tuning (single retry owner).
    private const val BACKOFF_BASE_MS = 15_000L
    private const val BACKOFF_MAX_MS = 30 * 60 * 1000L
    private const val BACKOFF_JITTER_MS = 5_000L
    private const val BACKOFF_MAX_SHIFT = 6

    /** Re-upload the endpoint at most this often even when it did not change. */
    private const val UPLOAD_REFRESH_MS = 6 * 60 * 60 * 1000L

    private const val META_STORE = "red_push_meta"
    private const val META_UPLOADED_AT = "endpoint_uploaded_at"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val retryScheduled = AtomicBoolean(false)
    private var attempt = 0

    /**
     * Ensures a distributor is saved, (re-)registers, and re-uploads the last
     * known endpoint. Safe to call on every app start and on BOOT_COMPLETED.
     */
    fun register(context: Context) {
        scope.launch { attemptRegister(context.applicationContext) }
    }

    /**
     * Called by [com.red.sovereign.push.RedPushService.onRegistrationFailed].
     * Schedules one exponential-backoff reconnect (no-op when one is pending).
     */
    fun onRegistrationFailed(context: Context) {
        scheduleReconnect(context.applicationContext)
    }

    /** Uploads a fresh endpoint URL from [com.red.sovereign.push.RedPushService.onNewEndpoint]. */
    fun uploadEndpointNow(context: Context, endpoint: String) {
        if (endpoint.isBlank()) return
        scope.launch {
            val app = context.applicationContext
            val tokens = TokenStore(app)
            val previous = tokens.pushEndpoint
            tokens.savePushEndpoint(endpoint)
            if (tokens.accessToken.isNullOrBlank()) return@launch
            // Dedup: same URL and a fresh upload -> nothing to do.
            if (previous == endpoint && !isUploadStale(app)) return@launch
            uploadEndpoint(app, tokens, endpoint)
        }
    }

    /** Distributor packages installed on the device (for the settings picker). */
    fun availableDistributors(context: Context): List<String> =
        runCatching { UnifiedPush.getDistributors(context.applicationContext) }.getOrDefault(emptyList())

    /** Currently saved distributor package, or null when none. */
    fun currentDistributor(context: Context): String? =
        runCatching { UnifiedPush.getAckDistributor(context.applicationContext) }
            .getOrNull()?.takeIf { it.isNotBlank() }

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
            SecureStore(app, META_STORE).remove(META_UPLOADED_AT)
        }
    }

    // ---------------------------------------------------------------- internals

    private suspend fun attemptRegister(app: Context) {
        lock.lock()
        try {
            ensureDistributor(app)
            UnifiedPush.register(app, messageForDistributor = DISTRIBUTOR_MESSAGE)
            val tokens = TokenStore(app)
            val endpoint = tokens.pushEndpoint
            if (!endpoint.isNullOrBlank() && !tokens.accessToken.isNullOrBlank()) {
                if (isUploadStale(app) || retryScheduled.get()) uploadEndpoint(app, tokens, endpoint)
            }
            // Success: reset the backoff ladder.
            attempt = 0
        } catch (t: Throwable) {
            Log.w(TAG, "UnifiedPush register failed: ${t.message}")
            scheduleReconnect(app)
        } finally {
            lock.unlock()
        }
    }

    private fun ensureDistributor(app: Context) {
        if (!UnifiedPush.getAckDistributor(app).isNullOrBlank()) return
        val all = runCatching { UnifiedPush.getDistributors(app) }.getOrDefault(emptyList())
        if (all.isEmpty()) {
            Log.i(TAG, "no UnifiedPush distributor installed - push wake unavailable until one is")
            return
        }
        val pick = all.firstOrNull { it in PREFERRED_DISTRIBUTORS } ?: all.first()
        UnifiedPush.saveDistributor(app, pick)
        Log.i(TAG, "UnifiedPush distributor saved: $pick")
    }

    private suspend fun uploadEndpoint(app: Context, tokens: TokenStore, endpoint: String) {
        val body = JSONObject()
            .put("token", endpoint)
            .put("platform", "ANDROID")
            .toString()
        val result = runCatching { AuthorizedApiClient(tokens).request("POST", "/api/devices/push-token", body) }
            .getOrNull()
        when (result) {
            is ApiResult.Success -> {
                markUploaded(app)
                attempt = 0
                Log.i(TAG, "push-token uploaded")
            }
            is ApiResult.Error -> {
                Log.w(TAG, "push-token upload rejected code=${result.code} msg=${result.message}")
                scheduleReconnect(app)
            }
            null -> {
                Log.w(TAG, "push-token upload transport error")
                scheduleReconnect(app)
            }
        }
    }

    private fun scheduleReconnect(app: Context) {
        if (!retryScheduled.compareAndSet(false, true)) return
        scope.launch {
            val shift = attempt.coerceIn(0, BACKOFF_MAX_SHIFT)
            attempt = (attempt + 1).coerceAtMost(BACKOFF_MAX_SHIFT)
            val backoff = (BACKOFF_BASE_MS shl shift).coerceAtMost(BACKOFF_MAX_MS)
            val waitMs = (backoff + Random.nextLong(0, BACKOFF_JITTER_MS)).coerceAtMost(BACKOFF_MAX_MS)
            Log.i(TAG, "reconnect scheduled in ${waitMs}ms (attempt=$shift)")
            delay(waitMs)
            retryScheduled.set(false)
            attemptRegister(app)
        }
    }

    private fun isUploadStale(app: Context): Boolean {
        val last = SecureStore(app, META_STORE).get(META_UPLOADED_AT)?.toLongOrNull() ?: 0L
        return System.currentTimeMillis() - last > UPLOAD_REFRESH_MS
    }

    private fun markUploaded(app: Context) {
        SecureStore(app, META_STORE).put(META_UPLOADED_AT, System.currentTimeMillis().toString())
    }
}
