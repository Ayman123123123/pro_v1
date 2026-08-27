package com.red.sovereign.security

import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * يثبّت العطلين الحرجين اللذين كانا في [LoggingInterceptor]:
 *
 * 1. كان يستدعي `response.body.string()` — قراءة مدمرة تُفرِغ الجسم قبل أن
 *    تصل طبقة العميل، أي أن تفعيل تسجيل التشخيص كان يُعطّل التطبيق.
 * 2. كان يسجّل الجسم كاملًا والرابط بمعاملات الاستعلام، فتُسرَّب رسائل
 *    مفكوكة ورموز جلسة إلى logcat.
 *
 * يُحقن `logger` بجامع في الذاكرة، ويُستخدم Chain مزيّف بدل MockWebServer
 * حتى يبقى الاختبار بلا تبعية شبكية جديدة ويعمل بالكامل بلا اتصال.
 */
class LoggingInterceptorTest {

    @Test
    fun `debug logging preserves response body and redacts sensitive values`() {
        val logs = mutableListOf<String>()
        val responseSecret = "encrypted-message-content-must-not-be-logged"
        val request = Request.Builder()
            .url("https://red.local/messages?access_token=query-secret")
            .header("Authorization", "Bearer header-secret")
            .header("X-Device-Token", "device-secret")
            .header("Accept", "application/json")
            .build()

        val response = LoggingInterceptor(isDebug = true, logger = logs::add)
            .intercept(FakeChain(request, responseSecret))

        // العطل الأول: الجسم يجب أن يبقى قابلًا للقراءة لطبقة العميل.
        assertEquals(responseSecret, response.body?.string())

        val combined = logs.joinToString("\n")
        // العطل الثاني: لا محتوى ولا سر يعبر إلى السجل.
        assertFalse(combined.contains(responseSecret))
        assertFalse(combined.contains("header-secret"))
        assertFalse(combined.contains("device-secret"))
        assertFalse(combined.contains("query-secret"))
        assertFalse(combined.contains("access_token"))

        // ما يجب أن يبقى مفيدًا للتشخيص: الطريقة، المسار، الترويسات غير الحساسة.
        assertTrue(combined.contains("GET"))
        assertTrue(combined.contains("https://red.local/messages"))
        assertTrue(combined.contains("Accept=application/json"))
        assertTrue(combined.contains("REDACTED"))
    }

    @Test
    fun `release mode logs nothing at all`() {
        val logs = mutableListOf<String>()
        val request = Request.Builder().url("https://red.local/messages").build()

        val response = LoggingInterceptor(isDebug = false, logger = logs::add)
            .intercept(FakeChain(request, "body"))

        assertEquals("body", response.body?.string())
        assertTrue(logs.isEmpty())
    }

    /** Chain أدنى ما يكفي لتشغيل معترض تسجيل بلا شبكة. */
    private class FakeChain(
        private val request: Request,
        private val bodyText: String
    ) : Interceptor.Chain {
        override fun request(): Request = request

        override fun proceed(request: Request): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Set-Cookie", "session=cookie-secret")
            .header("Content-Type", "application/json")
            .body(bodyText.toResponseBody("application/json".toMediaType()))
            .build()

        override fun connection(): Connection? = null
        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
