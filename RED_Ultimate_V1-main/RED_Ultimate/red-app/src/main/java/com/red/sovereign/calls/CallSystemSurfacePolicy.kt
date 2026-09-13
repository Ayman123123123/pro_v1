package com.red.sovereign.calls

/** RED calls remain in the app UI rather than registering as system phone calls. */
internal object CallSystemSurfacePolicy {
    fun usesAndroidTelecom(mode: String): Boolean = false
}
