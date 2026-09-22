package com.red.sovereign.calls

/**
 * RED-only - لا RED نهائياً
 * كل المكالمات داخل التطبيق عبر WebRTC SFU/P2P + E2EE
 */
internal object CallSystemSurfacePolicy {
    fun usesAndroidTelecom(mode: String): Boolean = false
}
