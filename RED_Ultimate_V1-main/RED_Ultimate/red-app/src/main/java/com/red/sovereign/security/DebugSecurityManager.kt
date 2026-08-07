package com.red.sovereign.security

import android.content.Context
import android.os.Build

/**
 * Manages security settings based on build configuration.
 * Enables strict security in release builds and relaxed security in debug builds.
 */
object DebugSecurityManager {

    /**
     * Returns true if the app is running in debug mode.
     */
    fun isDebugBuild(): Boolean {
        return BuildConfig.DEBUG
    }

    /**
     * Returns true if certificate pinning should be enabled.
     * Disabled in debug builds to allow local development.
     */
    fun shouldEnableCertificatePinning(): Boolean {
        return !BuildConfig.DEBUG
    }

    /**
     * Returns true if logging should be enabled.
     * Enabled only in debug builds.
     */
    fun shouldEnableLogging(): Boolean {
        return BuildConfig.DEBUG
    }

    /**
     * Returns true if strict SSL validation should be applied.
     * Always enabled, but can be overridden for testing.
     */
    fun shouldEnableStrictSsl(): Boolean {
        return true
    }

    /**
     * Initialize security settings based on build type.
     */
    fun initialize(context: Context) {
        // Enable/disable certificate pinning based on build type
        CertificatePinner.isEnabled = shouldEnableCertificatePinning()

        // Configure logging
        if (shouldEnableLogging()) {
            // Enable verbose logging
            println("[Security] Debug build detected - relaxed security")
            println("[Security] Certificate pinning: ${CertificatePinner.isEnabled}")
        } else {
            // Enable strict security
            println("[Security] Release build detected - strict security enabled")
            println("[Security] Certificate pinning: ${CertificatePinner.isEnabled}")
        }
    }

    /**
     * Validate that the build is properly configured for security.
     */
    fun validateConfiguration(): List<String> {
        val warnings = mutableListOf<String>()

        if (BuildConfig.DEBUG) {
            warnings.add("DEBUG_BUILD: Certificate pinning is disabled for local development")
            warnings.add("DEBUG_BUILD: Logging is enabled - disable in production")
        }

        if (BuildConfig.VERSION_NAME.isBlank()) {
            warnings.add("VERSION_NAME is not set")
        }

        if (BuildConfig.APPLICATION_ID.isBlank()) {
            warnings.add("APPLICATION_ID is not set")
        }

        return warnings
    }

    /**
     * Get security recommendations based on current configuration.
     */
    fun getSecurityRecommendations(): List<SecurityRecommendation> {
        val recommendations = mutableListOf<SecurityRecommendation>()

        if (BuildConfig.DEBUG) {
            recommendations.add(
                SecurityRecommendation(
                    severity = "INFO",
                    title = "Debug Build",
                    description = "This is a debug build. Certificate pinning is disabled for local testing."
                )
            )
        }

        recommendations.add(
            SecurityRecommendation(
                severity = "INFO",
                title = "Certificate Pinning",
                description = if (CertificatePinner.isEnabled) "Enabled - connections are verified against pinned certificates" 
                    else "Disabled - all certificates are accepted (debug only)"
            )
        )

        recommendations.add(
            SecurityRecommendation(
                severity = "INFO",
                title = "Security Headers",
                description = "Security headers are added to all HTTP requests"
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            recommendations.add(
                SecurityRecommendation(
                    severity = "INFO",
                    title = "StrongBox",
                    description = "Device supports StrongBox keystore for hardware-backed keys"
                )
            )
        }

        return recommendations
    }

    /**
     * Security recommendation.
     */
    data class SecurityRecommendation(
        val severity: String,
        val title: String,
        val description: String
    )
}
