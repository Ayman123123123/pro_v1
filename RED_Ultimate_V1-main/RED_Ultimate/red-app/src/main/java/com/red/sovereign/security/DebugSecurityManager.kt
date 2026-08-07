package com.red.sovereign.security

import android.content.Context
import android.os.Build
import com.red.sovereign.BuildConfig
import java.security.MessageDigest

/** Applies strict security defaults in release and developer-friendly defaults in debug. */
object DebugSecurityManager {
    fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    fun shouldEnableCertificatePinning(): Boolean = !BuildConfig.DEBUG

    fun shouldEnableLogging(): Boolean = BuildConfig.DEBUG

    fun shouldEnableStrictSsl(): Boolean = true

    fun initialize(context: Context) {
        CertificatePinner.loadPins(context)
        CertificatePinner.setEnabled(shouldEnableCertificatePinning())
        if (shouldEnableLogging()) {
            println("[Security] Debug build - certificate pinning disabled unless manually configured")
        } else {
            println("[Security] Release build - strict TLS and configured pins enabled")
        }
    }

    fun validateConfiguration(): List<String> = buildList {
        if (BuildConfig.DEBUG) add("DEBUG_BUILD: certificate pinning is disabled for local development")
        if (BuildConfig.VERSION_NAME.isBlank()) add("VERSION_NAME is not set")
        if (BuildConfig.APPLICATION_ID.isBlank()) add("APPLICATION_ID is not set")
    }

    fun getSecurityRecommendations(): List<SecurityRecommendation> = buildList {
        add(
            SecurityRecommendation(
                severity = if (BuildConfig.DEBUG) "INFO" else "OK",
                title = "Certificate Pinning",
                description = if (CertificatePinner.isEnabled) {
                    "Enabled for configured production hosts"
                } else {
                    "Disabled in debug/local development"
                }
            )
        )
        add(
            SecurityRecommendation(
                severity = "OK",
                title = "Security Headers",
                description = "Security headers are added to API requests"
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(
                SecurityRecommendation(
                    severity = "OK",
                    title = "StrongBox",
                    description = "Device may support StrongBox hardware-backed keys"
                )
            )
        }
    }

    fun isValidEmail(email: String): Boolean =
        Regex("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$").matches(email)

    fun isValidPhone(phone: String): Boolean =
        Regex("^\\+?[0-9]{10,15}$").matches(phone.replace(Regex("[^0-9+]"), ""))

    fun isStrongPassword(password: String): Boolean =
        password.length >= 12 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() }

    fun sanitizeInput(input: String?): String? = input?.trim()?.takeIf { it.isNotEmpty() }

    fun isValidUuid(uuid: String): Boolean = runCatching { java.util.UUID.fromString(uuid) }.isSuccess

    fun hashData(data: String): String = MessageDigest.getInstance("SHA-256")
        .digest(data.toByteArray())
        .joinToString("") { "%02x".format(it) }

    data class SecurityRecommendation(
        val severity: String,
        val title: String,
        val description: String
    )
}
