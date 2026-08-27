package com.red.sovereign.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedApiClientRetryPolicyTest {
    @Test
    fun `endpoint rediscovery retries read methods only`() {
        assertTrue(AuthorizedApiClient.isSafeReadMethod("GET"))
        assertTrue(AuthorizedApiClient.isSafeReadMethod("head"))
        assertTrue(AuthorizedApiClient.isSafeReadMethod("OPTIONS"))

        assertFalse(AuthorizedApiClient.isSafeReadMethod("POST"))
        assertFalse(AuthorizedApiClient.isSafeReadMethod("PUT"))
        assertFalse(AuthorizedApiClient.isSafeReadMethod("PATCH"))
        assertFalse(AuthorizedApiClient.isSafeReadMethod("DELETE"))
    }
}
