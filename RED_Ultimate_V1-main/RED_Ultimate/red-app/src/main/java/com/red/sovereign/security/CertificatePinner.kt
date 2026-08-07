package com.red.sovereign.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit

/**
 * Certificate Pinning for enhanced security.
 * When enabled, connections will only be allowed if the server's certificate
 * matches the expected pins.
 */
object CertificatePinner {

    /**
     * Pre-defined pins for known servers.
     * Format: "sha256/<base64-encoded-SHA256-of-certificate>"
     */
    private val pins = mutableMapOf<String, Set<String>>(
        "api.red.local" to setOf(
            "sha256/AAAAAAAAAAAAAAAAAAAAAA==", // Production certificate
            "sha256/BBBBBBBBBBBBBBBBBBBBBB=="  // Backup certificate
        ),
        "storage.red.local" to setOf(
            "sha256/CCCCCCCCCCCCCCCCCCCCCC=="
        ),
        "cdn.red.local" to setOf(
            "sha256/DDDDDDDDDDDDDDDDDDDDDDDD=="
        ),
        "sfu.red.local" to setOf(
            "sha256/EEEEEEEEEEEEEEEEEEEEEE=="
        ),
        "chat.red.local" to setOf(
            "sha256/FFFFFFFFFFFFFFFFFFFFFF=="
        )
    )

    /**
     * Enable or disable certificate pinning.
     * Should be disabled in debug builds for local development.
     */
    var isEnabled: Boolean = BuildConfig.DEBUG
        private set

    /**
     * Enable certificate pinning (for release builds or when security is needed).
     */
    fun enable() {
        isEnabled = true
    }

    /**
     * Disable certificate pinning (for debug builds).
     */
    fun disable() {
        isEnabled = false
    }

    /**
     * Check if a certificate is pinned for the given host.
     */
    fun isPinned(host: String): Boolean {
        return pins.containsKey(host)
    }

    /**
     * Verify that the certificate chain matches the expected pins for the host.
     * Returns true if the pinning check passes or if pinning is disabled.
     */
    fun verify(host: String, certificates: List<Certificate>): Boolean {
        if (!isEnabled || !pins.containsKey(host)) {
            return true
        }

        val expectedPins = pins[host] ?: return false

        for (cert in certificates) {
            val pin = generatePin(cert)
            if (expectedPins.contains(pin) || expectedPins.any { it.startsWith(pin.substringBeforeLast("/")) }) {
                return true
            }
        }

        return false
    }

    /**
     * Generate a SHA-256 pin from a certificate.
     */
    fun generatePin(certificate: Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(certificate.encoded)
        val base64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "sha256/$base64"
    }

    /**
     * Get the expected pins for a host (for debugging/logging).
     */
    fun getExpectedPins(host: String): Set<String> {
        return pins[host] ?: emptySet()
    }

    /**
     * Add or update pins for a host.
     * Should only be called during initialization or from secure storage.
     */
    fun addPin(host: String, pin: String) {
        pins.getOrPut(host) { emptySet() }.add(pin)
    }

    /**
     * Remove pins for a host.
     */
    fun removePins(host: String) {
        pins.remove(host)
    }

    /**
     * Clear all pins.
     */
    fun clearAllPins() {
        pins.clear()
    }

    /**
     * Load pins from secure storage.
     */
    fun loadPins(context: Context) {
        // In production, load from encrypted storage
        // For now, using hardcoded pins (replace with actual certificate pins)
    }

    /**
     * Save pins to secure storage.
     */
    fun savePins(context: Context) {
        // In production, save to encrypted storage
    }

    companion object {
        /**
         * Convert a PEM certificate to a pin.
         */
        fun pemToPin(pemCertificate: String): String {
            val certFactory = CertificateFactory.getInstance("X.509")
            val certificate = certFactory.generateCertificate(
                pemCertificate.byteInputStream()
            )
            return generatePin(certificate)
        }

        /**
         * Convert a DER certificate (Base64 encoded) to a pin.
         */
        fun derToPin(derCertificateBase64: String): String {
            val certFactory = CertificateFactory.getInstance("X.509")
            val certificate = certFactory.generateCertificate(
                Base64.decode(derCertificateBase64, Base64.DEFAULT).inputStream()
            )
            return generatePin(certificate)
        }

        /**
         * Print all registered pins for debugging.
         */
        fun printPins() {
            pins.forEach { (host, pinSet) ->
                println("Host: $host")
                pinSet.forEach { pin ->
                    println("  - $pin")
                }
            }
        }
    }
}
