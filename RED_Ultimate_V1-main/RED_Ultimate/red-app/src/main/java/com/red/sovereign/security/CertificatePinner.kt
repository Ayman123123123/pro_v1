package com.red.sovereign.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.red.sovereign.BuildConfig
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

/**
 * App-level certificate pin registry.
 *
 * Pinning is intentionally data-driven: real production pins must be provisioned by
 * configuration/secure storage, not by fake placeholder hashes. When no pins are
 * configured for a host, OkHttp falls back to normal platform TLS validation.
 */
object CertificatePinner {
    private val hostPins: MutableMap<String, MutableSet<String>> = linkedMapOf()

    var isEnabled: Boolean = !BuildConfig.DEBUG
        private set

    fun enable() {
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isPinned(host: String): Boolean = hostPins[host]?.isNotEmpty() == true

    fun allPins(): Map<String, Set<String>> = hostPins.mapValues { it.value.toSet() }

    fun getExpectedPins(host: String): Set<String> = hostPins[host]?.toSet().orEmpty()

    fun addPin(host: String, pin: String) {
        require(pin.startsWith("sha256/")) { "Certificate pin must start with sha256/" }
        hostPins.getOrPut(host.lowercase()) { linkedSetOf() }.add(pin)
    }

    fun addPins(host: String, pins: Iterable<String>) {
        pins.forEach { addPin(host, it) }
    }

    fun removePins(host: String) {
        hostPins.remove(host.lowercase())
    }

    fun clearAllPins() {
        hostPins.clear()
    }

    fun verify(host: String, certificates: List<Certificate>): Boolean {
        if (!isEnabled) return true
        val expectedPins = getExpectedPins(host.lowercase())
        if (expectedPins.isEmpty()) return true
        return certificates.any { cert -> expectedPins.contains(generatePin(cert)) }
    }

    fun generatePin(certificate: Certificate): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return "sha256/${Base64.encodeToString(hash, Base64.NO_WRAP)}"
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
            Base64.decode(derCertificateBase64, Base64.DEFAULT).inputStream()
        )
        return generatePin(certificate)
    }

    fun loadPins(context: Context) {
        // Reserved for future encrypted configuration loading.
    }

    fun savePins(context: Context) {
        // Reserved for future encrypted configuration persistence.
    }

    fun printPins() {
        allPins().forEach { (host, pins) ->
            Log.i(TAG, "Host: $host")
            pins.forEach { Log.i(TAG, "  - $it") }
        }
    }

    private const val TAG = "CertificatePinner"
}
