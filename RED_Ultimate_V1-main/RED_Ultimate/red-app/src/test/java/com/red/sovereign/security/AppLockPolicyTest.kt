package com.red.sovereign.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun `does not lock when app lock is disabled`() {
        assertFalse(AppLockPolicy.shouldLock(false, 1_000L, 60_000L))
    }

    @Test
    fun `does not lock on initial launch or short backgrounding`() {
        assertFalse(AppLockPolicy.shouldLock(true, null, 60_000L))
        assertFalse(AppLockPolicy.shouldLock(true, 40_001L, 60_000L))
    }

    @Test
    fun `locks after the background grace period`() {
        assertTrue(AppLockPolicy.shouldLock(true, 30_000L, 60_000L))
    }
}
