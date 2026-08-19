package com.red.sovereign.security

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoggingInterceptorTest {
    @Test
    fun `debug logging preserves response body and redacts sensitive values`() {
        val server = MockWebServer()
        val logs = mutableListOf<String>()
        val responseSecret = "encrypted-message-content-must-not-be-logged"
        server.enqueue(MockResponse().setBody(responseSecret))
        server.start()

        try {
            val client = OkHttpClient.Builder()
                .addInterceptor(LoggingInterceptor(isDebug = true, logger = logs::add))
                .build()
            val request = Request.Builder()
                .url(server.url("/messages?access_token=query-secret"))
                .header("Authorization", "Bearer header-secret")
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(responseSecret, response.body.string())
            }

            val combinedLogs = logs.joinToString("\n")
            assertFalse(combinedLogs.contains(responseSecret))
            assertFalse(combinedLogs.contains("header-secret"))
            assertFalse(combinedLogs.contains("query-secret"))
        } finally {
            server.close()
        }
    }
}
