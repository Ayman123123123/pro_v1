package com.red.server.services

/**
 * Hardware facts that are safe to encode in the server. Carrier compatibility is deliberately not
 * guessed: it depends on the installed radio variant, local coverage and the SIM/network profile.
 */
enum class DinstarModelProfile(
    val modelId: String,
    val portCount: Int,
    val radioCapability: String,
    val supportsVolte: Boolean
) {
    UC2000_VE_8G("UC2000-VE-8G", 8, "GSM 850/900/1800/1900 MHz", false),
    UC2000_VE_8T("UC2000-VE-8T", 8, "LTE/VoLTE variant; exact FDD/TDD/WCDMA bands must be read from device label", true);

    val portRange: IntRange get() = 0 until portCount

    fun metadata(): Map<String, Any> = mapOf(
        "model" to modelId,
        "portCount" to portCount,
        "radioCapability" to radioCapability,
        "supportsVolte" to supportsVolte,
        "carrierCompatibilityRequiresLiveRegistration" to true
    )

    companion object {
        fun parse(value: String): DinstarModelProfile = entries.firstOrNull { it.modelId.equals(value.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported DINSTAR model. Expected UC2000-VE-8G or UC2000-VE-8T")
    }
}
