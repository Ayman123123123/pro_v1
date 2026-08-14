package com.red.sovereign.security

import android.content.Context
import android.util.Log
import com.red.sovereign.BuildConfig
import com.red.sovereign.core.SecureStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.Base64

/**
 * Keystore-backed TLS SPKI pin registry.
 *
 * Production starts with pin enforcement enabled. If no pins have been
 * provisioned for a host, normal Android platform TLS validation remains the
 * authority; fake or hard-coded development pins are never accepted.
 */
object CertificatePinner {
    private val lock = Any()
    private val hostPins: MutableMap<String, MutableSet<String>> = linkedMapOf()

    @Volatile
    var isEnabled: Boolean = !BuildConfig.DEBUG
        private set

    fun enable() { isEnabled = true; SecureOkHttpClient.reset() }
    fun disable() { isEnabled = false; SecureOkHttpClient.reset() }
    fun setEnabled(enabled: Boolean) { if (enabled) enable() else disable() }

    fun isPinned(host: String): Boolean = synchronized(lock) {
        hostPins[normalizeHost(host)]?.isNotEmpty() == true
    }

    fun allPins(): Map<String, Set<String>> = synchronized(lock) {
        hostPins.mapValues { it.value.toSet() }
    }

    fun getExpectedPins(host: String): Set<String> = synchronized(lock) {
        hostPins[normalizeHost(host)]?.toSet().orEmpty()
    }

    fun addPin(host: String, pin: String) {
        val normalizedHost = normalizeHost(host)
        require(normalizedHost.isNotBlank() && (normalizedHost.contains('.') || normalizedHost == "localhost")) {
            "Certificate pin host is invalid"
        }
        validatePin(pin)
        synchronized(lock) { hostPins.getOrPut(normalizedHost) { linkedSetOf() }.add(pin) }
        SecureOkHttpClient.reset()
    }

    fun addPins(host: String, pins: Iterable<String>) = pins.forEach { addPin(host, it) }

    fun removePins(host: String) {
        synchronized(lock) { hostPins.remove(normalizeHost(host)) }
        SecureOkHttpClient.reset()
    }

    fun clearAllPins() {
        synchronized(lock) { hostPins.clear() }
        SecureOkHttpClient.reset()
    }

    fun verify(host: String, certificates: List<Certificate>): Boolean {
        if (!isEnabled) return true
        val expectedPins = getExpectedPins(host)
        if (expectedPins.isEmpty()) return true
        return certificates.any { certificate -> generatePin(certificate) in expectedPins }
    }

    /** OkHttp-compatible sha256/SPKI pin, not a hash of the whole certificate. */
    fun generatePin(certificate: Certificate): String {
        val spki = certificate.publicKey.encoded
        val hash = MessageDigest.getInstance("SHA-256").digest(spki)
        return "sha256/${Base64.getEncoder().withoutPadding().encodeToString(hash)}"
    }

    fun pemToPin(pemCertificate: String): String {
        val cleanPem = pemCertificate
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace(Regex("\\s"), "")
        return derToPin(cleanPem)
    }

    fun derToPin(derCertificateBase64: String): String {
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(
            Base64.getMimeDecoder().decode(derCertificateBase64).inputStream()
        )
        return generatePin(certificate)
    }

    /**
     * Provisions public release pins from the signed build configuration.
     * Format: host=sha256/pin|sha256/backup;other.host=sha256/pin
     */
    fun provisionPins(context: Context, specification: String) {
        if (specification.isBlank()) return
        specification.split(';').filter(String::isNotBlank).forEach { entry ->
            val parts = entry.split('=', limit = 2)
            require(parts.size == 2) { "Invalid RED_TLS_PINS entry" }
            val host = parts[0].trim()
            val pins = parts[1].split('|').map(String::trim).filter(String::isNotBlank)
            require(pins.isNotEmpty()) { "At least one pin is required for $host" }
            removePins(host)
            addPins(host, pins)
        }
        savePins(context)
    }

    /** Loads encrypted provisioned pins before the first OkHttp client is built. */
    fun loadPins(context: Context) {
        val raw = SecureStore(context.applicationContext, STORE_NAME).get(PINS_KEY) ?: return
        val parsed = runCatching {
            val root = JSONObject(raw)
            buildMap<String, Set<String>> {
                root.keys().forEach { host ->
                    val values = root.optJSONArray(host) ?: JSONArray()
                    val pins = buildSet {
                        for (index in 0 until values.length()) {
                            val pin = values.optString(index)
                            validatePin(pin)
                            add(pin)
                        }
                    }
                    if (pins.isNotEmpty()) put(normalizeHost(host), pins)
                }
            }
        }.getOrElse {
            Log.e(TAG, "Encrypted certificate-pin configuration is invalid", it)
            return
        }
        synchronized(lock) {
            hostPins.clear()
            parsed.forEach { (host, pins) -> hostPins[host] = pins.toMutableSet() }
        }
        SecureOkHttpClient.reset()
    }

    /** Persists only public SPKI hashes; encryption prevents local policy tampering. */
    fun savePins(context: Context) {
        val root = JSONObject()
        allPins().forEach { (host, pins) ->
            root.put(host, JSONArray().apply { pins.sorted().forEach { pin -> put(pin) } })
        }
        SecureStore(context.applicationContext, STORE_NAME).put(PINS_KEY, root.toString())
    }

    fun printPins() {
        allPins().forEach { (host, pins) ->
            Log.i(TAG, "Host: $host (${pins.size} configured SPKI pins)")
        }
    }

    private fun validatePin(pin: String) {
        require(pin.startsWith("sha256/")) { "Certificate pin must start with sha256/" }
        val digest = runCatching { Base64.getDecoder().decode(pin.removePrefix("sha256/")) }.getOrNull()
        require(digest?.size == SHA256_BYTES) { "Certificate pin must contain one SHA-256 digest" }
    }

    private fun normalizeHost(host: String): String = host.trim().trimEnd('.').lowercase()

    private const val STORE_NAME = "younes_certificate_pins"
    private const val PINS_KEY = "spki_pins_v1"
    private const val SHA256_BYTES = 32
    private const val TAG = "CertificatePinner"
}
