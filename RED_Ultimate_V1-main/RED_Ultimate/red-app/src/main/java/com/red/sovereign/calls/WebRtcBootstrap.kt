package com.red.sovereign.calls

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * PeerConnectionFactory.initialize() must run once per process.
 * 1-1 calls, mesh conferences, and live fan-out all share this gate.
 */
object WebRtcBootstrap {
    @Volatile private var ready = false
    private val lock = Any()
    @Volatile private var cachedIce: IceConfigurationDto? = null
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sharedEgl: EglBase by lazy { EglBase.create() }
    val eglContext: EglBase.Context get() = sharedEgl.eglBaseContext

    fun ensure(context: Context) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false)
                    .setFieldTrials(
                        "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/" +
                            "WebRTC-FlexFEC-03/Enabled/" +
                            "WebRTC-Bwe-ProbingConfiguration/Enabled/" +
                            "WebRTC-Audio-NetEqAutoReset/Enabled/"
                    )
                    .createInitializationOptions()
            )
            ready = true
        }
    }

    /**
     * Server returns `expiresAt` in **seconds**, fallback uses **millis**.
     * Normalize to millis so the 30s safety margin actually works —
     * previously every cache lookup compared ms>sec and always missed,
     * forcing a network fetch on every call setup.
     */
    private fun expiresAtMillis(expiresAt: Long): Long =
        if (expiresAt < 1_000_000_000_000L) expiresAt * 1000 else expiresAt

    fun getCachedIce(): IceConfigurationDto? {
        val c = cachedIce ?: return null
        if (System.currentTimeMillis() > (expiresAtMillis(c.expiresAt) - 30_000)) return null
        return c
    }

    fun setCachedIce(dto: IceConfigurationDto) {
        cachedIce = dto
    }

    /**
     * Prefetch ICE servers off the call critical path (service onCreate / LISTEN).
     * Never throws; failures keep the previous cache so loadIce() stays fast.
     */
    fun prefetchIce(context: Context) {
        if (getCachedIce() != null) return
        prefetchScope.launch {
            runCatching {
                val app = context.applicationContext
                val client = com.red.sovereign.auth.AuthorizedApiClient(com.red.sovereign.auth.TokenStore(app))
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val response = client.request("GET", "/api/calls/ice-servers")
                if (response is com.red.sovereign.auth.ApiResult.Success) {
                    runCatching { json.decodeFromString<IceConfigurationDto>(response.value) }.getOrNull()?.let { cachedIce = it }
                }
            }
        }
    }

    /** Embedded fallback so calls never hard-fail when the ICE endpoint is slow/down.
     * LAN-first: a local coturn STUN (same host as the signaling endpoint) comes
     * before any internet STUN — on isolated LAN, google DNS fails and only
     * host/local-STUN candidates work. Google stays as last resort with internet.
     */
    fun fallbackIce(localHost: String? = null): IceConfigurationDto {
        val local = localHost?.trim()?.trim('[', ']')?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
        val servers = if (local != null) {
            listOf(IceServerDto(urls = listOf("stun:$local:3478")))
        } else {
            emptyList()
        } + listOf(
            IceServerDto(urls = listOf("stun:stun1.l.google.com:19302")),
            IceServerDto(urls = listOf("stun:stun2.l.google.com:19302"))
        )
        return IceConfigurationDto(
            expiresAt = System.currentTimeMillis() + 3600_000L,
            iceServers = servers
        )
    }
}
