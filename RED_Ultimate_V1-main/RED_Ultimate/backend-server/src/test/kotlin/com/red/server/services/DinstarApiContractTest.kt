package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * عقد واجهة UC2000 — يُثبِّت شكل كل نداء بلا حاجة إلى جهاز.
 *
 * كل تأكيد هنا يقابل عطلًا حقيقيًا وُجد في الشيفرة: الطريقة الخطأ،
 * أو الجسم الخطأ، أو القراءة من الجذر بدل الكائن المتداخل.
 */
class DinstarApiContractTest {

    // ── الطرائق ──────────────────────────────────────────────────────

    @Test
    fun `get_status must be POST with a performance array body`() {
        // كان يُرسَل GET مع `?maximum=10`، فيردّ الجهاز 403.
        assertEquals(DinstarApiContract.Method.POST, DinstarApiContract.METHODS[DinstarApiContract.Path.GET_STATUS])
        assertEquals(listOf("performance"), DinstarApiContract.Status.PERFORMANCE_BODY)
    }

    @Test
    fun `get_cdr must be POST`() {
        assertEquals(DinstarApiContract.Method.POST, DinstarApiContract.METHODS[DinstarApiContract.Path.GET_CDR])
    }

    @Test
    fun `query_incoming_sms must be POST with a cursor`() {
        // كان GET بلا معاملات، فيُعيد الصندوق كاملًا كل دورة.
        assertEquals(
            DinstarApiContract.Method.POST,
            DinstarApiContract.METHODS[DinstarApiContract.Path.QUERY_INCOMING_SMS]
        )
        assertEquals("incoming_sms_id", DinstarApiContract.Sms.REQ_INCOMING_ID)
    }

    @Test
    fun `port info endpoints are GET with query parameters`() {
        assertEquals(DinstarApiContract.Method.GET, DinstarApiContract.METHODS[DinstarApiContract.Path.GET_PORT_INFO])
        assertEquals(DinstarApiContract.Method.GET, DinstarApiContract.METHODS[DinstarApiContract.Path.SET_PORT_INFO])
    }

    @Test
    fun `queue endpoint uses the documented name`() {
        assertEquals("/api/query_sms_queue", DinstarApiContract.Path.QUERY_SMS_QUEUE)
        // الاسم القديم يبقى كسقوط لا كأساس.
        assertEquals("/api/query_sms_count", DinstarApiContract.Path.QUERY_SMS_COUNT_LEGACY)
    }

    // ── رموز الحالة ──────────────────────────────────────────────────

    @Test
    fun `strict success is 200 while async acceptance allows 202`() {
        assertTrue(DinstarApiContract.isOk(mapOf("error_code" to 200)))
        assertFalse(DinstarApiContract.isOk(mapOf("error_code" to 202)))

        assertTrue(DinstarApiContract.isAccepted(mapOf("error_code" to 200)))
        assertTrue(DinstarApiContract.isAccepted(mapOf("error_code" to 202)))
        assertFalse(DinstarApiContract.isAccepted(mapOf("error_code" to 550)))
    }

    @Test
    fun `absent error code is tolerated only for read responses`() {
        // get_status لا يُرفق error_code في بعض الإصدارات؛ فرض وجوده
        // كان يُسقط ردًّا صحيحًا.
        assertTrue(DinstarApiContract.isOkOrAbsent(mapOf("performance" to mapOf("cpu_used" to "39"))))
        assertFalse(DinstarApiContract.isOkOrAbsent(emptyMap()))
    }

    @Test
    fun `documented error codes have human readable meanings`() {
        assertTrue(DinstarApiContract.describe(550).contains("منفذ"))
        assertTrue(DinstarApiContract.describe(413).contains("الحد"))
        assertTrue(DinstarApiContract.describe(503).contains("مسجّل"))
        assertTrue(DinstarApiContract.describe(486).contains("مشغول"))
        // 403 هو الردّ الذي أوقف المشروع طويلًا: سببه الأول طريقة HTTP.
        assertTrue(DinstarApiContract.describe(403).contains("HTTP"))
    }

    // ── الحدود ───────────────────────────────────────────────────────

    @Test
    fun `recipient ceiling is 128 not 32`() {
        // 32 هو حد query_sms_result لا send_sms. الخلط كان يرفض دفعات
        // مشروعة قبل أن تصل إلى البوابة.
        assertEquals(128, DinstarApiContract.Limits.MAX_SMS_RECIPIENTS)
        assertEquals(32, DinstarApiContract.Limits.MAX_QUERY_NUMBERS)
    }

    @Test
    fun `text ceiling is measured in bytes not characters`() {
        assertEquals(1500, DinstarApiContract.Limits.MAX_SMS_TEXT_BYTES)
        // العربية في UTF-8 بايتان للحرف: 800 حرف = 1600 بايت > الحد،
        // مع أن عدد الأحرف أقل من 1500. القياس بالحرف يُمرّر ما ترفضه
        // البوابة بـ 413.
        val arabic = "س".repeat(800)
        assertEquals(1600, arabic.toByteArray(Charsets.UTF_8).size)
        assertTrue(arabic.toByteArray(Charsets.UTF_8).size > DinstarApiContract.Limits.MAX_SMS_TEXT_BYTES)
        assertTrue(arabic.length < DinstarApiContract.Limits.MAX_SMS_TEXT_BYTES)
    }

    // ── حالة المنفذ ──────────────────────────────────────────────────

    @Test
    fun `registration check accepts every documented spelling`() {
        // هذه المقارنة كانت مكرّرة في خمسة ملفات وكل موضع نسي صيغة،
        // فظهرت شرائح مسجّلة على أنها ساقطة.
        assertTrue(DinstarApiContract.PortInfo.isRegistered("REGISTER_OK"))
        assertTrue(DinstarApiContract.PortInfo.isRegistered("REGISTERED"))
        assertTrue(DinstarApiContract.PortInfo.isRegistered("Mobile Registered"))
        assertTrue(DinstarApiContract.PortInfo.isRegistered("  register_ok  "))
        assertFalse(DinstarApiContract.PortInfo.isRegistered("UNREGISTER"))
        assertFalse(DinstarApiContract.PortInfo.isRegistered("NO_SIM"))
        assertFalse(DinstarApiContract.PortInfo.isRegistered(null))
    }

    @Test
    fun `sim absence is distinguished from being unregistered`() {
        assertTrue(DinstarApiContract.PortInfo.isSimAbsent("NO_SIM"))
        assertTrue(DinstarApiContract.PortInfo.isSimAbsent("no-sim"))
        // «غير مسجّل» ليس «لا شريحة»: الأول قد يُحلّ بإعادة تشغيل الوحدة.
        assertFalse(DinstarApiContract.PortInfo.isSimAbsent("UNREGISTER"))
    }

    @Test
    fun `requested info_type covers the fields the router depends on`() {
        val fields = DinstarApiContract.PortInfo.REQUESTED_FIELDS
        // بلا `signal` لا يمكن ترتيب المنافذ، وبلا `reg` لا يُعرف
        // المسجّل، وبلا `imsi` لا يُستنتج المشغّل عند غياب الرقم.
        listOf("signal", "reg", "callstate", "imsi", "iccid", "number", "slot").forEach {
            assertTrue(fields.contains(it), "info_type يجب أن يطلب $it")
        }
    }

    // ── USSD ─────────────────────────────────────────────────────────

    @Test
    fun `ussd success is per port not global`() {
        // 202 يعني «قُبل الطلب»؛ الحكم الحقيقي في result[].status.
        val response = mapOf(
            "error_code" to 202,
            "result" to listOf(
                mapOf("port" to 0, "status" to 200),
                mapOf("port" to 1, "status" to 503),
                mapOf("port" to 2, "status" to 486)
            )
        )
        val perPort = DinstarApiContract.Ussd.perPortStatus(response)
        assertEquals(200, perPort[0])
        assertEquals(503, perPort[1]) // غير مسجّل
        assertEquals(486, perPort[2]) // مشغول
    }

    @Test
    fun `ussd codes are restricted to digits star and hash`() {
        assertTrue(DinstarApiContract.Ussd.isValidCode("*100#"))
        assertTrue(DinstarApiContract.Ussd.isValidCode("*141*1#"))
        assertFalse(DinstarApiContract.Ussd.isValidCode("*100#; rm -rf /"))
        assertFalse(DinstarApiContract.Ussd.isValidCode("../api/get_status"))
        assertFalse(DinstarApiContract.Ussd.isValidCode("*"))
    }

    // ── CDR و get_status ─────────────────────────────────────────────

    @Test
    fun `cdr records are read from either documented key`() {
        val modern = mapOf("cdr" to listOf(mapOf("port" to 0)))
        val legacy = mapOf("info" to listOf(mapOf("port" to 1)))
        assertEquals(1, DinstarApiContract.Cdr.records(modern).size)
        assertEquals(1, DinstarApiContract.Cdr.records(legacy).size)
        assertTrue(DinstarApiContract.Cdr.records(emptyMap()).isEmpty())
    }

    @Test
    fun `performance metrics live inside a nested object`() {
        // القراءة من الجذر كانت تُعيد null لكل حقل، فتُكتب
        // dinstar_device_status فارغة تمامًا.
        val response = mapOf(
            "performance" to mapOf(
                "cpu_used" to "39", "memory_total" to "109448", "memory_used" to "50520"
            )
        )
        val perf = DinstarApiContract.Status.performance(response)
        assertEquals("39", perf["cpu_used"])
        assertEquals("50520", perf["memory_used"])
    }

    @Test
    fun `flat status responses still resolve for older firmware`() {
        val flat = mapOf("cpu_used" to "12", "flash_total" to "27648", "unrelated" to "x")
        val perf = DinstarApiContract.Status.performance(flat)
        assertEquals("12", perf["cpu_used"])
        assertNull(perf["unrelated"])
    }

    // ── تحويل status_code ────────────────────────────────────────────

    @Test
    fun `delivery outcome follows 3GPP TS 23 040 ranges`() {
        assertEquals(DinstarApiContract.DeliveryOutcome.DELIVERED,
            DinstarApiContract.Sms.deliveryOutcome(0))
        assertEquals(DinstarApiContract.DeliveryOutcome.IN_PROGRESS,
            DinstarApiContract.Sms.deliveryOutcome(16))
        assertEquals(DinstarApiContract.DeliveryOutcome.TEMPORARY_FAILURE,
            DinstarApiContract.Sms.deliveryOutcome(32))
        assertEquals(DinstarApiContract.DeliveryOutcome.TEMPORARY_FAILURE,
            DinstarApiContract.Sms.deliveryOutcome(63))
        assertEquals(DinstarApiContract.DeliveryOutcome.PERMANENT_FAILURE,
            DinstarApiContract.Sms.deliveryOutcome(64))
        assertEquals(DinstarApiContract.DeliveryOutcome.PERMANENT_FAILURE,
            DinstarApiContract.Sms.deliveryOutcome(255))
        assertEquals(DinstarApiContract.DeliveryOutcome.UNKNOWN,
            DinstarApiContract.Sms.deliveryOutcome(null))
    }

    @Test
    fun `wire encoding values match the gateway vocabulary`() {
        // إرسال "GSM7BIT" قيمة غير معروفة، فترجع البوابة إلى unicode:
        // رسالة ASCII تفقد نصف سعتها وتتضاعف كلفتها.
        assertEquals("gsm-7bit", DinstarApiContract.Sms.WIRE_GSM7BIT)
        assertEquals("unicode", DinstarApiContract.Sms.WIRE_UNICODE)
    }

    @Test
    fun `reset action carries a param because all three are mandatory`() {
        assertEquals("reset", DinstarApiContract.PortAction.RESET)
        assertTrue(DinstarApiContract.PortAction.RESET_PARAM.isNotBlank())
        assertTrue(DinstarApiContract.PortAction.CALL_FORWARD_PARAMS.contains("CancelAll"))
        assertEquals("on", DinstarApiContract.PortAction.powerParam(true))
        assertEquals("off", DinstarApiContract.PortAction.powerParam(false))
    }

    @Test
    fun `device time pattern is not ISO 8601`() {
        // `Instant.parse` على هذه الصيغة يفشل حتمًا — وهو ما كان يجعل
        // كل رسالة واردة تُختَم بزمن القراءة لا بزمن وصولها.
        assertEquals("yyyy-MM-dd HH:mm:ss", DinstarApiContract.TIME_PATTERN)
    }

    // ── ترميز معاملات الاستعلام ──────────────────────────────────────

    /**
     * الفاصلة الخام في قيمة معامل تكسر مطابقة Digest URI على البرنامج
     * الثابت 04240302 فيردّ `401 Wrong Password` رغم صحة الاعتماد.
     *
     * مُثبت ميدانيًا على الجهاز الحيّ:
     * - `port=0&info_type=signal,type`   → 401
     * - `port=0&info_type=signal%2Ctype` → 200
     * - `info_type=signal&port=0,1`      → 401
     * - `info_type=signal&port=0%2C1`    → 200
     *
     * الترميز يجب أن يكون غير قابل للانعكاس المزدوج: `%` تُرمَّز أولًا
     * وإلا صارت `%2C` الناتجة `%252C` في تمريرة ثانية.
     */
    @Test
    fun `query values encode commas so digest auth matches`() {
        val enc = DinstarConnectionFactory.DinstarClient::encodeQueryValue

        assertEquals("signal%2Ctype", enc("signal,type"))
        assertEquals("0%2C1%2C2%2C3%2C4%2C5%2C6%2C7", enc("0,1,2,3,4,5,6,7"))
        // قيمة بلا فاصلة تبقى كما هي — لا ترميز زائد يغيّر المعنى
        assertEquals("signal", enc("signal"))
        assertEquals("Unconditional", enc("Unconditional"))
        // `%` تُرمَّز قبل الفاصلة، فلا يُعاد ترميز الناتج
        assertEquals("100%25", enc("100%"))
        assertEquals("a%25b%2Cc", enc("a%b,c"))
    }

    @Test
    fun `port info requested fields contain commas that must be encoded`() {
        // هذا هو النداء الذي كان يفشل: عشرة حقول مفصولة بفواصل.
        val csv = DinstarApiContract.PortInfo.REQUESTED_FIELDS_CSV
        assertTrue(csv.contains(','), "REQUESTED_FIELDS_CSV must be comma-separated")
        assertFalse(
            DinstarConnectionFactory.DinstarClient.encodeQueryValue(csv).contains(','),
            "encoded info_type must not carry a raw comma"
        )
    }
}
