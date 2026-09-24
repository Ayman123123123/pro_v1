package com.red.sovereign.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * حارس انهيار زر الاتصال (مثبت حيًا من logcat بتاريخ 2026-09-05):
 * `PstnApi.bridge` كانت تسلسل `buildMap<String, Any>` فترمي
 * `SerializationException: Serializer for class 'Any' is not found`
 * ويسقط التطبيق عند كل ضغطة اتصال.
 *
 * هذه الاختبارات تثبّت أن بناء جسم `/api/pstn/bridge` لا يرمي أبدًا
 * وينتج نفس السلك الذي يتوقعه الخادم (port تُحذف عند غيابها).
 */
class BridgePayloadTest {

    @Test
    fun `الجسم بلا منفذ يطابق سلك الخادم`() {
        assertEquals("""{"number":"780488700"}""", buildBridgePayload("780488700"))
    }

    @Test
    fun `الجسم مع منفذ يتضمنه رقمًا لا نصًا`() {
        assertEquals("""{"number":"780488700","port":7}""", buildBridgePayload("780488700", 7))
    }

    @Test
    fun `البناء لا يرمي لأي دخل`() {
        // لو عاد نمط Map<String, Any> سيرمي هنا ويفشل الاختبار قبل الجهاز.
        buildBridgePayload("+967780488700", null)
        buildBridgePayload("181", 0)
    }
}
