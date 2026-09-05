package com.red.sovereign.calls.sip

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبار تكاملي حقيقي لعميل التسجيل فوق UDP — **بلا عتاد DINSTAR**.
 *
 * يُشغَّل «دجَّار وهمي» على `127.0.0.1` بمنفذ UDP مؤقت داخل نفس العملية:
 * يستقبل `REGISTER`، يردّ `401` بتحدّي Digest، ثم يتحقّق من تجاوب المصادقة
 * بصيغة RFC 2617 **المحسوبة هنا بشكل مستقل** (لا عبر `SipDigestAuth`) — فحص
 * متقاطع لا تدوير. عند التطابق يردّ `200 OK`.
 *
 * سبب وجوده: العلّات الثلاث التي كانت تُعطّل التسجيل — إهمال قيمة `send()`،
 * وعدم قراءة `401` لأن `parseMethod` لا يطابق سطر استجابة، ولصق تجاوب MD5
 * مجرّدًا في `Authorization` — لم تكن لترى في أي اختبار وحدة، لأنها لم تكن
 * تملك مسار UDP أصلًا. هذه الملفات تُثبّت العقد السلكي (wire contract).
 */
class DirectSipClientTest {

    // ── الدجَّار الوهمي ────────────────────────────────────────────────────

    private class FakePbx(
        private val realm: String,
        private val nonce: String,
        private val qop: String?,
        private val minExpires: Int? = null,
        private val password: String = PASSWORD,
        private val user: String = USER,
    ) : Closeable {
        private val socket = DatagramSocket()
        val port: Int get() = socket.localPort
        val received = ConcurrentLinkedQueue<SipMessage>()
        val rawReceived = ConcurrentLinkedQueue<String>()
        var authValidated = false; private set
        var attempts = 0; private set

        private val stop = AtomicBoolean(false)
        private val ready = CountDownLatch(1)

        init {
            Thread({
                ready.countDown()
                val buf = ByteArray(8192)
                while (!stop.get()) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        socket.soTimeout = 200
                        socket.receive(p)
                    } catch (_: Exception) { continue }
                    val msg = SipMessage.parse(String(p.data, 0, p.length, Charsets.UTF_8)) ?: continue
                    rawReceived += String(p.data, 0, p.length, Charsets.UTF_8)
                    received += msg
                    if (msg.method == "REGISTER") respond(msg, p.socketAddress)
                }
            }, "fake-pbx").apply { isDaemon = true; start() }
            ready.await(2, TimeUnit.SECONDS)
        }

        private fun respond(request: SipMessage, to: SocketAddress) {
            attempts++
            val reqExpires = request.intValue("Expires") ?: 3600
            if (minExpires != null && reqExpires > minExpires) {
                reply(request, to, "423 Interval Too Brief", "Min-Expires: $minExpires")
                return
            }
            val auth = request.header("Authorization")
            if (auth == null) {
                val challenge = buildString {
                    append("Digest realm=\"$realm\", nonce=\"$nonce\", algorithm=MD5")
                    if (qop != null) append(", qop=\"$qop\"")
                }
                reply(request, to, "401 Unauthorized", "WWW-Authenticate: $challenge")
                return
            }
            val expected = independentDigest(request, auth)
            if (expected == null || !auth.contains("response=\"$expected\"")) {
                reply(request, to, "403 Forbidden", "WWW-Authenticate: Digest realm=\"$realm\", nonce=\"$nonce\"")
                return
            }
            authValidated = true
            val expires = reqExpires
            val contact = request.header("Contact") ?: "<sip:$user@127.0.0.1;transport=udp>"
            reply(request, to, "200 OK", "Contact: $contact\r\nExpires: $expires")
        }

        /** حساب RFC 2617 يدويًا — لا يستدعي `SipDigestAuth` إطلاقًا. */
        private fun independentDigest(request: SipMessage, auth: String): String? {
            val params = Regex("""(\w+)\s*=\s*"?([^",]+)""")
                .findAll(auth)
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val n = params["nonce"] ?: return null
            val uri = params["uri"] ?: return null
            val r = params["realm"] ?: return null
            // R-URI المُستخدَم يجب أن يكون ما في سطر الطلب نفسه — نفحصه أيضًا.
            if (uri != request.startLine.substringAfter(' ').substringBefore(' ')) return null
            val ha1 = md5("$user:$r:$password")
            val ha2 = md5("REGISTER:$uri")
            val nc = params["nc"]; val cnonce = params["cnonce"]; val q = params["qop"]
            return if (nc != null && cnonce != null && q == "auth") md5("$ha1:$n:$nc:$cnonce:auth:$ha2")
            else md5("$ha1:$n:$ha2")
        }

        private fun reply(request: SipMessage, to: SocketAddress, status: String, extraHeaders: String) {
            val body = buildString {
                append("SIP/2.0 $status\r\n")
                request.topmostVia()?.let { append("Via: ").append(it).append("\r\n") }
                request.header("From")?.let { append("From: ").append(it).append("\r\n") }
                append("To: ").append(request.header("To")).append(";tag=pbx\r\n")
                append("Call-ID: ").append(request.callId).append("\r\n")
                append("CSeq: ").append(request.header("CSeq")).append("\r\n")
                append(extraHeaders).append("\r\n")
                append("Content-Length: 0\r\n\r\n")
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(bytes, bytes.size, to))
        }

        override fun close() { stop.set(true); socket.close() }
    }

    // ── تيسير ───────────────────────────────────────────────────────────────

    private class Runner(
        val client: DirectSipClient,
        private val scope: CoroutineScope,
        private val transport: UdpSipTransport,
    ) : Closeable {
        override fun close() {
            runCatching { transport.close() }
            runCatching { scope.cancel() }
        }
    }

    private fun runner(pbx: FakePbx, expires: Int = 3600, timeoutMs: Long = 1_200): Runner {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val config = DirectSipClient.Config(
            host = "127.0.0.1",
            port = pbx.port,
            username = USER,
            password = PASSWORD,
            domain = "127.0.0.1",
            expires = expires,
            localUdpPort = 0,
            contactHost = "127.0.0.1",
            t1Ms = 40, t2Ms = 80, maxRetransmits = 2,
            transactionTimeoutMs = timeoutMs,
        )
        val transport = UdpSipTransport(bindPort = 0)
        return Runner(DirectSipClient(config, transport, scope), scope, transport)
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ── 1) العقد السلكي لـ REGISTER ────────────────────────────────────────

    @Test
    fun `sends an RFC 3261 REGISTER with every mandatory header`() = runBlocking {
        FakePbx("127.0.0.1", "abc123", qop = null).use { pbx ->
            runner(pbx).use { h ->
                h.client.registerOnce()
                val first = pbx.received.first()
                val wire = pbx.rawReceived.first()

                assertTrue("سطر طلب خاطئ: ${first.startLine}", first.startLine == "REGISTER sip:127.0.0.1 SIP/2.0")
                assertTrue(
                    "Via غير مطابقة لـ RFC 3261 §16.11 + rport",
                    Regex("""^Via: SIP/2\.0/UDP 127\.0\.0\.1:\d+;branch=z9hG4bK[0-9a-f]+;rport$""", RegexOption.MULTILINE)
                        .containsMatchIn(wire),
                )
                assertTrue(wire.contains("Max-Forwards: 70\r\n"))
                assertNotNull(first.header("From"))
                assertTrue("From بلا tag", first.header("From")!!.contains("tag="))
                assertFalse("To في الطلب يجب ألّا يحمل tag", first.header("To")!!.contains("tag="))
                assertTrue(first.header("Call-ID")!!.isNotBlank())
                assertEquals(1L, first.cseqNumber)
                assertEquals("REGISTER", first.cseqMethod)
                assertEquals("3600", first.header("Expires"))
                assertTrue("Contact بلا expires", first.header("Contact")!!.contains(";expires=3600"))
                assertTrue("Contact يجب أن يعلن UDP", first.header("Contact")!!.contains(";transport=udp"))
                assertFalse("تسرّب CRLF مفرد إلى السلك", wire.contains("\r\n\n"))
                assertEquals("Content-Length يجب أن يظهر مرة واحدة", 1, Regex("Content-Length:").findAll(wire).count())
                assertTrue("Content-Length يجب أن يكون 0", wire.contains("Content-Length: 0\r\n"))
            }
        }
    }

    // ── 2) تحدٍّ بـ qop=auth ────────────────────────────────────────────────

    @Test
    fun `answers a qop-auth challenge with a full Digest header and then registers`() = runBlocking {
        FakePbx("127.0.0.1", "nonce-xyz", qop = "auth").use { pbx ->
            runner(pbx).use { h ->
                val granted = h.client.registerOnce()
                assertTrue("لم يُقبل التحدّي — المصادقة المتقاطعة فشلت", pbx.authValidated)
                assertEquals(3600, granted)

                val retried = pbx.received.last()
                val auth = retried.header("Authorization")
                assertNotNull("لا توجد ترويسة Authorization", auth)
                assertTrue("Authorization يجب أن تبدأ بـ Digest", auth!!.startsWith("Digest "))
                // الجرح القديم: كان المسار التراجعي يلصق تجاوبًا مجرّدًا وحده.
                listOf("username=", "realm=", "nonce=", "uri=", "response=").forEach {
                    assertTrue("حقل $it مفقود", auth.contains(it))
                }
                assertTrue("qop=auth مطلوب لأن الدجَّار تحدّى به", auth.contains("qop=auth"))
                assertTrue("nc بصيغة 8 خانات", Regex("""nc=00000001""").containsMatchIn(auth))
                assertEquals("uri= يجب أن يساوي R-URI حرفيًا", "sip:127.0.0.1",
                    Regex("""uri="?([^",]+)""").find(auth)!!.groupValues[1])

                // معاملة جديدة بعد التحدّي: نفس Call-ID، CSeq أكبر، فرع مختلف.
                assertEquals(pbx.received.first().callId, retried.callId)
                assertEquals(2L, retried.cseqNumber)
                assertNotEqualsIgnoringNull(pbx.received.first().viaBranch(), retried.viaBranch())
            }
        }
    }

    // ── 3) بلا qop — النمط الشائع عند UC200 ─────────────────────────────────

    @Test
    fun `registers against a realm without qop using the three-field MD5 digest`() = runBlocking {
        FakePbx("127.0.0.1", "no-qop-nonce", qop = null).use { pbx ->
            runner(pbx).use { h ->
                h.client.registerOnce()
                assertTrue(pbx.authValidated)
                val auth = pbx.received.last().header("Authorization")!!
                assertFalse("qop لا يجب أن يظهر إن لم يتحدَّ به الدجَّار", auth.contains("qop="))
                assertTrue(auth.contains("response=\""))
                assertTrue("uri= مطلوب ولو بلا qop", auth.contains("uri=\"sip:127.0.0.1\""))
            }
        }
    }

    // ── 4) 423 Interval Too Brief ───────────────────────────────────────────

    @Test
    fun `retries once with Min-Expires when the PBX rejects the interval`() = runBlocking {
        FakePbx("127.0.0.1", "n", qop = null, minExpires = 120).use { pbx ->
            runner(pbx, expires = 3600).use { h ->
                val granted = h.client.registerOnce()
                assertEquals("يجب قبول الحد الأدنى المفروض", 120, granted)
                assertTrue("كان لا بد من تجاوز 423 والمصادقة", pbx.authValidated)
                assertEquals("423 ثم 401 ثم 200", 3, pbx.received.size)
                assertEquals("120", pbx.received.last().header("Expires"))
            }
        }
    }

    // ── 5) لا صمت مطلقًا عند الفشل ──────────────────────────────────────────

    @Test
    fun `an unreachable registrar yields a diagnosable error not a silent hang`() {
        // لا مُجيب على هذا المنفذ إطلاقًا.
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val transport = UdpSipTransport(bindPort = 0)
        val client = DirectSipClient(
            DirectSipClient.Config(
                host = "127.0.0.1", port = freeUdpPort(), username = USER, password = PASSWORD,
                domain = "127.0.0.1", contactHost = "127.0.0.1",
                t1Ms = 20, t2Ms = 40, maxRetransmits = 1, transactionTimeoutMs = 200,
            ),
            transport, scope,
        )
        try {
            val thrown = runCatching { runBlocking { client.registerOnce() } }.exceptionOrNull()
            assertNotNull("كان لا بد من خطأ صريح بدل الصمت", thrown)
            assertTrue(thrown is DirectSipClient.SipRegistrationException)
            val e = thrown as DirectSipClient.SipRegistrationException
            assertEquals("NO_RESPONSE", e.code)
            assertTrue("رسالة الخطأ يجب ألّا تكون فارغة أبدًا", e.descriptiveMessage().isNotBlank())
            assertTrue("الرسالة يجب أن تشخّص: ${e.descriptiveMessage()}", e.descriptiveMessage().contains("REGISTER"))
            assertTrue(e.descriptiveMessage().contains("127.0.0.1"))
        } finally {
            runCatching { transport.close() }; runCatching { scope.cancel() }
        }
    }

    @Test
    fun `send on a dead transport reports the reason instead of returning null`() {
        // هذا هو العرَض الذي كان يُنتج `SIP_SEND_FAILED` بلا تفسير: القيمة المُرجَعة
        // من الإرسال كانت تُهمَل في `WebRtcSipClient` ثم تُستبدل بـ `ws?.send()`.
        val transport = UdpSipTransport(bindPort = 0)
        transport.close()
        val outcome = transport.send("REGISTER sip:x SIP/2.0\r\n\r\n".toByteArray(Charsets.UTF_8), InetSocketAddress("127.0.0.1", 5060))
        val failed = outcome as UdpSipTransport.SendOutcome.Failed
        assertTrue(failed.reason.isNotBlank())
        assertTrue("يجب تسمية العطب", failed.reason.contains("SOCKET_NOT_OPEN"))
    }

    @Test
    fun `bad credentials surface 403 as a fatal code and never loop forever`() = runBlocking {
        FakePbx("127.0.0.1", "n", qop = null).use { pbx ->
            val scope = CoroutineScope(Dispatchers.Default + Job())
            val transport = UdpSipTransport(bindPort = 0)
            val client = DirectSipClient(
                DirectSipClient.Config(
                    host = "127.0.0.1", port = pbx.port, username = USER, password = "wrong-password",
                    domain = "127.0.0.1", contactHost = "127.0.0.1",
                    t1Ms = 40, maxRetransmits = 1, transactionTimeoutMs = 900,
                ),
                transport, scope,
            )
            try {
                val e = runCatching { client.registerOnce() }.exceptionOrNull() as DirectSipClient.SipRegistrationException
                assertEquals("SIP_403", e.code)
                assertTrue(e.descriptiveMessage().contains("403"))
                assertEquals("401 ثم 403 فقط — بلا حلقة", 2, pbx.received.size)
            } finally {
                runCatching { transport.close() }; runCatching { scope.cancel() }
            }
        }
    }

    // ── 6) صرامة المحلِّل ───────────────────────────────────────────────────

    @Test
    fun `parser tolerates obs-fold and refuses non-SIP datagrams`() {
        val folded = SipMessage.parse(
            "SIP/2.0 401 Unauthorized\r\n" +
                "Via: SIP/2.0/UDP 127.0.0.1:9;\r\n\tbranch=z9hG4bKff\r\n" +
                "Call-ID: x@y\r\nCSeq: 1 REGISTER\r\n" +
                "WWW-Authenticate: Digest realm=\"r\",\r\n\tnonce=\"n\"\r\n\r\n"
        )
        assertNotNull(folded)
        assertEquals(401, folded!!.statusCode)
        assertEquals("REGISTER", folded.method)          // من CSeq — لا من سطر البداية
        assertEquals("z9hG4bKff", folded.viaBranch())     // طيّ السطر لم يكسر المعامِل
        assertEquals("n", SipDigestAuth.parseChallenge(folded.header("WWW-Authenticate"))?.nonce)

        // ضجيج على منفذ SIP (HTTP 400، STUN) يجب أن يُرفض لا أن يُفهم خطأً.
        assertNull(SipMessage.parse("HTTP/1.1 400 Bad Request\r\n\r\n"))
        assertNull(SipMessage.parse(""))
    }

    @Test
    fun `serialize measures Content-Length in bytes not characters`() {
        val arabic = "مرحبا".toByteArray(Charsets.UTF_8)   // 10 بايت ≠ 5 أحرف
        val msg = SipMessage("NOTIFY sip:a SIP/2.0", listOf("Event" to "keep-alive"), arabic)
        val wire = msg.serialize().toString(Charsets.UTF_8)
        assertTrue("Content-Length بالأحرف = انكسار السلك", wire.contains("Content-Length: 10\r\n"))
        assertTrue(wire.endsWith("\r\n\r\nمرحبا"))
    }

    private fun assertNotEqualsIgnoringNull(a: String?, b: String?) = assertTrue("فرع Via يجب أن يتغيّر", a != b)

    /** منفذ UDP شبه خالٍ — لا مُجيب عليه فيطرق العميل حتى انتهاء المؤقّت. */
    private fun freeUdpPort(): Int = DatagramSocket(0).use { it.localPort }

}

/** عضويات الملف خاصة بالملف: مرئية لكل أصناف هذا الملف (الخاصّ المتداخل ليس كذلك في Kotlin). */
private const val USER = "1001"
private const val PASSWORD = "s3cr3t-pass"
