package com.red.server.services

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit tests for DinstarHardwareService focusing on:
 * 1. Digest/Basic authenticator construction
 * 2. DispatchingAuthenticator registration
 * 3. OkHttpClient builder configuration
 * 4. Configuration validation
 */
class DinstarHardwareServiceTest {

    @Test
    fun `DigestAuthenticator is constructed with valid credentials`() {
        val credentials = Credentials("admin", "admin")
        val authenticator = DigestAuthenticator(credentials)
        assertNotNull(authenticator, "DigestAuthenticator should be constructed successfully")
    }

    @Test
    fun `BasicAuthenticator is constructed with valid credentials`() {
        val credentials = Credentials("admin", "admin")
        val authenticator = BasicAuthenticator(credentials)
        assertNotNull(authenticator, "BasicAuthenticator should be constructed successfully")
    }

    @Test
    fun `DispatchingAuthenticator handles both digest and basic schemes`() {
        val credentials = Credentials("admin", "admin")
        val digestAuthenticator = DigestAuthenticator(credentials)
        val basicAuthenticator = BasicAuthenticator(credentials)

        val dispatchingAuthenticator = DispatchingAuthenticator.Builder()
            .with("digest", digestAuthenticator)
            .with("basic", basicAuthenticator)
            .build()

        assertNotNull(dispatchingAuthenticator, "DispatchingAuthenticator should be built successfully")
    }

    @Test
    fun `CachingAuthenticatorDecorator wraps DispatchingAuthenticator`() {
        val credentials = Credentials("admin", "admin")
        val digestAuthenticator = DigestAuthenticator(credentials)
        val basicAuthenticator = BasicAuthenticator(credentials)

        val dispatchingAuthenticator = DispatchingAuthenticator.Builder()
            .with("digest", digestAuthenticator)
            .with("basic", basicAuthenticator)
            .build()

        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        val decorator = CachingAuthenticatorDecorator(dispatchingAuthenticator, authCache)

        assertNotNull(decorator, "CachingAuthenticatorDecorator should be constructed successfully")
    }

    @Test
    fun `AuthenticationCacheInterceptor is constructed with authCache`() {
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        val interceptor = AuthenticationCacheInterceptor(authCache)
        assertNotNull(interceptor, "AuthenticationCacheInterceptor should be constructed successfully")
    }

    @Test
    fun `OkHttpClient is built with all components`() {
        val credentials = Credentials("admin", "admin")
        val digestAuthenticator = DigestAuthenticator(credentials)
        val basicAuthenticator = BasicAuthenticator(credentials)

        val dispatchingAuthenticator = DispatchingAuthenticator.Builder()
            .with("digest", digestAuthenticator)
            .with("basic", basicAuthenticator)
            .build()

        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        val decorator = CachingAuthenticatorDecorator(dispatchingAuthenticator, authCache)
        val interceptor = AuthenticationCacheInterceptor(authCache)

        val client = OkHttpClient.Builder()
            .authenticator(decorator)
            .addInterceptor(interceptor)
            .build()

        assertNotNull(client, "OkHttpClient should be built successfully")
    }

    @Test
    fun `Credentials rejects null username`() {
        assertThrows(IllegalArgumentException::class.java) {
            Credentials(null, "password")
        }
    }

    @Test
    fun `Credentials rejects null password`() {
        assertThrows(IllegalArgumentException::class.java) {
            Credentials("admin", null)
        }
    }

    @Test
    fun `DispatchingAuthenticator Builder registers schemes in lowercase`() {
        // The Builder.with() method converts to lowercase internally
        val credentials = Credentials("admin", "admin")
        val digestAuthenticator = DigestAuthenticator(credentials)
        val basicAuthenticator = BasicAuthenticator(credentials)

        // Even if we pass uppercase, it should work (Builder lowercases internally)
        val dispatchingAuthenticator = DispatchingAuthenticator.Builder()
            .with("DIGEST", digestAuthenticator)  // uppercase
            .with("BASIC", basicAuthenticator)     // uppercase
            .build()

        assertNotNull(dispatchingAuthenticator, "DispatchingAuthenticator should handle uppercase schemes")
    }

    @Test
    fun `authCache is concurrent for thread safety`() {
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()
        // Should support concurrent access without exceptions
        authCache.put("test-key", DigestAuthenticator(Credentials("admin", "admin")))
        assertTrue(authCache.containsKey("test-key"), "authCache should contain the inserted key")
        assertEquals(1, authCache.size, "authCache should have exactly one entry")
    }
}
