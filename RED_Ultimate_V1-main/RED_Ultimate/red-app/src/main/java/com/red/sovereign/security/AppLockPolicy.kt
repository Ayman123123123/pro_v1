package com.red.sovereign.security

internal object AppLockPolicy {
    const val BACKGROUND_GRACE_PERIOD_MS = 30_000L

    fun shouldLock(
        lockEnabled: Boolean,
        backgroundedAtElapsedMs: Long?,
        nowElapsedMs: Long
    ): Boolean = lockEnabled && backgroundedAtElapsedMs != null &&
        nowElapsedMs - backgroundedAtElapsedMs >= BACKGROUND_GRACE_PERIOD_MS
}
