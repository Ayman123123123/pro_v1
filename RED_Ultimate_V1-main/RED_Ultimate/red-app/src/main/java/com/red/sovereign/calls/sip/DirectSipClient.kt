package com.red.sovereign.calls.sip

import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * عميل تسجيل SIP خام فوق UDP/5060 موجَّه مباشرة إلى DINSTAR **UC200** IP PBX
 * (`192.168.11.3:5060`). لا Asterisk، لا WSS، لا وسطاء.
 *
 * النطاق مقصود ومحدود: **تسجيل + تجديد + إلغاء تسجيل**. لا `INVITE`/SDP هنا،
 * ويبقى المسار الصوتي حيث هو. وUC2000 (`192.168.11.2`) **لا يظهر في هذا الكود
 * إطلاقًا** — الوصول إليها قرار ترقيم عند UC200 (SIP trunk داخلي) لا عنوان في
 * التطبيق؛ فلو وضعناه هنا لانكسر المبدأ المعماري (عميل واحد ← PBX واحد).
 *
 * ── ما يُصلحه هذا الملف، بالقياس على الكود الموجود ────────────────────────
 *
 * | العرَض | السبب الجذري المُزال |
 * |---|---|
 * | `SIP_SEND_FAILED`، ولا بايت يصل الدجَّار | النقل كان `okhttp3.WebSocket.send()` = TCP + ترقية `Upgrade: websocket`. على منفذ SIP/UDP تفشل المصافحة حتمًا. و`ws?.send(msg)` قيمةُ إرجاعها مُهمَلة في 8 مواضع: `null` عند عدم الفتح، `false` عند الإغلاق/امتلاء الطابور ⇒ صمت تام. الآن [UdpSipTransport.send] يُرجع `SendOutcome` وكلُّ فشلٍ يرفع `SIP_SEND_FAILED` **مع السبب**. |
 * | «استثناء برسالة فارغة» | `Throwable.message` nullable، و`SocketException()` بلا رسالة غالبًا. كل مسار خطأ يمرّ عبر `descriptiveMessage()` ⇒ نص غير فارغ دائمًا. |
 * | `401` لا يُعالَج | `Regex("^(\\w+)\\s+sip:")` على سطر `SIP/2.0 401 Unauthorized` لا يطابق ⇒ طريقة فارغة. الطريقة الآن من `CSeq` (§8.2.1.2). |
 * | الدجَّار يردّ `400` على محاولة المصادقة | مسار التراجع القديم كان يلصق تجاوب MD5 **مجرّدًا**: `Authorization: 6f1a…`. الآن: `Digest username=…, realm=…, nonce=…, uri=…, response=…` كاملة، أو فشل صريح `UNSUPPORTED_CHALLENGE`. |
 * | تسجيل ينجح ثم لا مكالمات واردة | `Contact` لم يكن فيه عنوان IPv4 قابل للتوجيه ولا منفذ المقبس الفعلي (`InetAddress.getLocalHost()` على Android = `127.0.0.1`). |
 * | تعليق الواجهة إلى الأبد | لا مُهل. الآن إعادة إرسال أُسّية T1→T2 ثم انتهاء المؤقّت B بخطأ تشخيصي (RFC 3261 §17.1.2). |
 * | `423 Interval Too Brief` قاتل | استئناف واحد بقيمة `Min-Expires` (120 ث إعدادٌ شائع في UC200). |
 * | `Content-Length` خاطئ مع أي UTF-8 | كان بالأحرف؛ الآن بالبايت في [SipMessage.serialize]. |
 */
class DirectSipClient(
    val config: Config,
    private val transport: UdpSipTransport = UdpSipTransport(bindPort = config.localUdpPort),
    private val scope: CoroutineScope,
    /** حقن المهل: اختبارات حتمية بلا انتظار 500ms حقيقي. */
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {

    data class Config(
        /** عنوان UC200 كما يراه الهاتف على نفس الشبكة المحلية. */
        val host: String = "192.168.11.3",
        val port: Int = 5060,
        val username: String = "",
        val password: String = "",
        /** اسم المصادقة إن خالف رقم التحويلة. فارغ = [username]. */
        val authUsername: String = "",
        /** نطاق AOR/`realm`. فارغ = [host]. */
        val domain: String = "",
        val expires: Int = 3600,
        /** 0 = منفذ مؤقت. اضبط 5060 فقط إذا اشترط الدجَّار تطابق منفذ المصدر. */
        val localUdpPort: Int = 0,
        /** عنوان `Contact` مصرَّح به. null = اكتشاف IPv4 المحلي. */
        val contactHost: String? = null,
        val transportId: String = "udp",
        val userAgent: String = "RED-Ultimate/1.0 (SIP; Android)",
        val t1Ms: Long = 500L,
        val t2Ms: Long = 4_000L,
        /** سقْفات إعادة الإرسال (§17.1.2: 64×T1 لغير INVITE ≈ 32 ث). */
        val maxRetransmits: Int = 6,
        val transactionTimeoutMs: Long = 31_000L,
        /** سقف استئناف المصادقة — يمنع حلقة `401` أبدية. */
        val maxAuthAttempts: Int = 2,
        val enableRport: Boolean = true,
    ) {
        val effectiveDomain: String get() = domain.ifBlank { host }
        val effectiveAuthUser: String get() = authUsername.ifBlank { username }
        val serverAddress: String get() = "$host:$port"

        init {
            require(username.isNotBlank()) { "SIP username (extension) is required" }
            require(password.isNotBlank()) { "SIP password is required — anonymous REGISTER cannot authenticate" }
            require(host.isNotBlank()) { "UC200 host is required" }
            require(port in 1..65535) { "port must be 1..65535" }
            require(expires in 1..86_400) { "expires must be 1..86400 (0 is only valid for un-REGISTER)" }
            require(t1Ms in 100..5_000) { "t1Ms must be 100..5000" }
        }
    }

    sealed interface Event {
        /** السلك المُرسَل حرفيًا — لـ`adb logcat` وللاتصال عن بُعد مع الدجَّار. */
        data class Outbound(val raw: String) : Event
        data class Challenged(val realm: String, val algorithm: String, val qop: String?) : Event
        data class Retransmitting(val attempt: Int, val waitMs: Long) : Event
        data class Registered(val expires: Int, val contact: String, val cseq: Int) : Event
        data class Refreshing(val inMs: Long) : Event
        data object Unregistered : Event
        /** `message` غير فارغ دائمًا — قلب إصلاح الصمت. */
        data class Failure(val code: String, val message: String, val cause: Throwable?) : Event
    }

    private val events = MutableSharedFlow<Event>(replay = 0, extraBufferCapacity = 64)
    fun events(): Flow<Event> = events.asSharedFlow()

    private val cSeq = AtomicInteger(0)
    @Volatile private var started = false
    @Volatile private var active = false
    @Volatile private var registered = false
    @Volatile private var lastExpiry: Int = 0

    val isRegistered: Boolean get() = registered
    val grantedExpiry: Int get() = lastExpiry

    /** @throws UdpTransportException عند تعذّر ربط المقبس المحلي. */
    fun start() {
        if (started) return
        transport.start(scope)
        started = true
        active = true
    }

    // ── الواجهة العامة ────────────────────────────────────────────────────

    /**
     * تسجيل واحد كامل مع المصادقة. @return مدة الصلاحية الممنوحة بالثواني.
     * @throws SipRegistrationException برسالة تفصيلية عند أي فشل — بلا صمت.
     */
    suspend fun registerOnce(): Int {
        if (!active) start()
        if (config.contactHost.isNullOrBlank() && localIpv4() == null) {
            throw SipRegistrationException(
                "NO_LOCAL_ROUTE",
                "لا عنوان IPv4 محلي على واجهة فعّالة — لا يمكن إعلان Contact قابل للتوجيه. " +
                    "تحقّق من اتصال Wi-Fi ومن صلاحية ACCESS_NETWORK_STATE، أو اضبط Config.contactHost صراحةً.",
            )
        }
        val reply = runRegister(config.expires)
        val granted = reply.intValue("Expires")
            ?: reply.headers("Contact").firstOrNull()?.substringAfter("expires=", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
            ?: config.expires
        lastExpiry = granted
        registered = true
        events.emit(Event.Registered(granted, contactHeader(granted), cSeq.get()))
        return granted
    }

    /**
     * تسجيل ثم تجديد دوري حتى الإلغاء. التجديد عند 90% من الممنوح (§10.3).
     * الأخطاء غير القاتلة تُعاد بمهل هندسية بدل حلقة صرفة على الشبكة.
     */
    suspend fun registerAndKeepAlive() {
        var backoffMs = config.t1Ms * 16
        while (scope.isActive && active) {
            val granted = try {
                registerOnce().also { backoffMs = config.t1Ms * 16 }
            } catch (e: SipRegistrationException) {
                registered = false
                events.emit(Event.Failure(e.code, e.descriptiveMessage(), e.cause))
                if (e.code in FATAL_CODES) throw e
                sleeper(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
                continue
            }
            val refreshMs = (granted * 900L).coerceIn(30_000L, 600_000L)
            events.emit(Event.Refreshing(refreshMs))
            sleeper(refreshMs)
        }
    }

    private suspend fun sleepAndAccount(@Suppress("UNUSED_PARAMETER") e: SipRegistrationException, backoff: Long) {
        sleeper(backoff)
    }

    /** §10.3 إنهاء تهذيب: نفس `Contact`/`Call-ID` مع `Expires: 0`. */
    suspend fun unregister() {
        if (!registered) return
        val result = runCatching { runRegister(0) }
        registered = false
        result.exceptionOrNull()?.let { err ->
            events.emit(Event.Failure("UNREGISTER_INCOMPLETE", "لم يؤكد الدجَّار إلغاء التسجيل: ${err.descriptive()}", err))
        }
        withContext(NonCancellable) { events.emit(Event.Unregistered) }
    }

    fun close() {
        active = false
        registered = false
        transport.close()
    }

    // ── المعاملة ───────────────────────────────────────────────────────────

    private class Transaction(val request: SipMessage, val bytes: ByteArray, val branch: String, val cseq: Long)

    /** إرسال + إعادة إرسال + انتظار الردّ المطابق. */
    private suspend fun perform(request: SipMessage): SipMessage {
        val tx = Transaction(request, request.serialize(), request.viaBranch().orEmpty(), request.cseqNumber)
        events.emit(Event.Outbound(String(tx.bytes, Charsets.UTF_8)))

        var sends = 0
        while (true) {
            if (!active) throw SipRegistrationException("TRANSPORT_CLOSED", "أُغلق العميل قبل اكتمال المعاملة")
            when (val outcome = transport.send(tx.bytes, java.net.InetSocketAddress(config.host, config.port))) {
                is UdpSipTransport.SendOutcome.Failed ->
                    throw SipRegistrationException("SIP_SEND_FAILED", outcome.reason, outcome.cause)
                is UdpSipTransport.SendOutcome.Sent -> sends++
            }
            when (val reply = awaitReply(tx, System.currentTimeMillis() + config.transactionTimeoutMs)) {
                is AwaitResult.Match -> return reply.message
                AwaitResult.Timeout -> {
                    if (sends > config.maxRetransmits) {
                        throw SipRegistrationException(
                            "NO_RESPONSE",
                            "لا ردّ على REGISTER من ${config.serverAddress} بعد ${sends} إرسالًا خلال " +
                                "${config.transactionTimeoutMs}ms. افحص بالترتيب: (1) `adb shell ping -c2 ${config.host}`؛ " +
                                "(2) أن UC200 يستمع على UDP/${config.port}؛ (3) أن التحويلة مُعرَّفة بوضع SIP " +
                                "وأن «Registration» مفعَّلة لحسابها؛ (4) جدار نار يمنع UDP ${config.port}؛ " +
                                "(5) أن الهاتف ليس على VLAN معزول عن شبكة الدجَّار.",
                        )
                    }
                    val wait = (config.t1Ms shl sends.coerceAtMost(5)).coerceAtMost(config.t2Ms)
                    events.emit(Event.Retransmitting(sends + 1, wait))
                    sleeper(wait)
                }
                AwaitResult.ChannelClosed -> throw SipRegistrationException(
                    "UDP_CHANNEL_CLOSED",
                    "أُغلقت قناة الاستقبال أثناء الانتظار (منفذ محلي ${transport.localPort})",
                )
            }
        }
    }

    private sealed interface AwaitResult {
        data class Match(val message: SipMessage) : AwaitResult
        data object Timeout : AwaitResult
        data object ChannelClosed : AwaitResult
    }

    /**
     * §22.2 مطابقة الردّ بالمعاملة: `Call-ID` + رقم `CSeq`/طريقته + فرع `Via`
     * العليا. هذا ما يمنع ردودًا متأخرة من معاملة منتهية، وضجيج معاملات أخرى
     * تصل على نفس المنفذ المحلي، من قلب حالة التسجيل.
     */
    private suspend fun awaitReply(tx: Transaction, deadline: Long): AwaitResult {
        val source: Channel<UdpSipTransport.Inbound> = transport.outgoing()
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return AwaitResult.Timeout
            val result = withTimeoutOrNull(remaining) { source.receiveCatching() }
                ?: return if (source.isClosedForReceive) AwaitResult.ChannelClosed else AwaitResult.Timeout
            if (result.isFailure) return AwaitResult.ChannelClosed
            val msg = result.getOrNull()?.message ?: continue
            if (msg.statusCode == 100 || msg.statusCode == 180) continue      // مؤقتات — تُبتلع بصمتٍ مقصود
            if (msg.callId != tx.request.callId) continue
            if (msg.cseqNumber != tx.cseq) continue
            if (msg.cseqMethod.isNotEmpty() && msg.cseqMethod != "REGISTER") continue
            val branch = msg.viaBranch()
            if (branch != null && tx.branch.isNotEmpty() && branch != tx.branch) {
                events.emit(Event.Failure("VIA_BRANCH_MISMATCH", "ردّ بفرع «$branch» ≠ «${tx.branch}» — مُتجاهَل", null))
                continue
            }
            return AwaitResult.Match(msg)
        }
    }

    /**
     * REGISTER حتى 2xx، مع استئناف `401/407` (مصادقة) و`423` (تقصير الصلاحية)
     * و`429/503` (`Retry-After`). يُعاد الاستدعاء ذاتيًا بسقْف [Config.maxAuthAttempts].
     */
    private suspend fun runRegister(expires: Int, attempt: Int = 0, auth: AuthHeader? = null): SipMessage {
        val request = buildRegister(expires, auth)
        val reply = perform(request)

        when (reply.statusCode) {
            200, 202 -> return reply

            401, 407 -> {
                val headerName = if (reply.statusCode == 401) "WWW-Authenticate" else "Proxy-Authenticate"
                val rawChallenge = reply.header(headerName)
                val challenge = SipDigestAuth.parseChallenge(rawChallenge)
                if (challenge == null) {
                    throw SipRegistrationException(
                        "MALFORMED_CHALLENGE",
                        "ترويسة $headerName من ${config.serverAddress} لا تحمل realm/nonce قابلين للتحليل: " +
                            (rawChallenge?.take(200) ?: "<مفقودة>"),
                    )
                }
                events.emit(Event.Challenged(challenge.realm, challenge.algorithm, challenge.qop))
                if (attempt >= config.maxAuthAttempts) {
                    throw SipRegistrationException(
                        "AUTH_REJECTED",
                        "الدجَّار أعاد التحدّي ${attempt + 1} مرات ورفض الاعتماد. الأسباب الثلاثة الشائعة " +
                            "على UC200: (أ) اسم المستخدم ليس رقم التحويلة الحرفي؛ (ب) `uri=` في " +
                            "Authorization لا يساوي سطر الطلب «${request.startLine.substringAfter(' ').substringBefore(' ')}»؛ " +
                            "(ج) الحساب معرَّف بمصادقة معطَّلة.",
                    )
                }
                // الشرط الحاسم: `uri=` = سطر الطلب بالضبط، لا «أفضل تخمين».
                val digestUri = request.startLine.substringAfter(' ').substringBefore(' ')
                val authorization = SipDigestAuth.buildAuthorization(
                    method = "REGISTER",
                    digestUri = digestUri,
                    username = config.effectiveAuthUser,
                    password = config.password,
                    challenge = challenge,
                    nonceCount = 1,
                    cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                ) ?: throw SipRegistrationException(
                    // لا تراجع إلى تجاوب MD5 مجرّد — كان يُنتج `Authorization: 6f1a…`
                    // فيردّ الدجَّار 400. الفشل الموصوف خيرٌ من طلب مُشوَّه.
                    "UNSUPPORTED_CHALLENGE",
                    "خوارزمية التحدّي «${challenge.algorithm}» غير مدعومة. المدعوم (RFC 7616): " +
                        "MD5, MD5-sess, SHA-256(-sess), SHA-512-256(-sess). اضبط الخوارزمية على UDP/MD5 في UC200.",
                )
                val stale = rawChallenge?.contains("stale=true", ignoreCase = true) == true
                return runRegister(expires, if (stale) attempt else attempt + 1, AuthHeader(headerName, authorization.value))
            }

            423 -> {
                val min = reply.intValue("Min-Expires")
                if (min == null || min <= 0 || min == expires || attempt >= config.maxAuthAttempts) {
                    throw SipRegistrationException(
                        "INTERVAL_TOO_BRIEF",
                        "423 Interval Too Brief بلا `Min-Expires` صالح (expires=$expires). اضبط Config.expires " +
                            "على قيمة لا تقلّ عن حدّ التسجيل الأدنى في UC200 (شائع: 120 أو 300).",
                    )
                }
                return runRegister(min, attempt + 1, auth)
            }

            429, 503 -> {
                if (attempt >= config.maxAuthAttempts) {
                    throw SipRegistrationException("SERVER_BUSY", "${reply.statusCode} ${reply.reason} — استُنفد سقف الاستئناف ($attempt)")
                }
                sleeper(((reply.intValue("Retry-After") ?: 5) * 1000L).coerceIn(1_000L, 30_000L))
                return runRegister(expires, attempt + 1, auth)
            }

            in 300..699 -> throw SipRegistrationException(
                "SIP_${reply.statusCode}",
                "REGISTER رُفض: ${reply.statusCode} ${reply.reason.ifBlank { "(بلا سبب)" }} — من ${config.serverAddress}",
            )

            else -> throw SipRegistrationException("UNPARSEABLE_REPLY", "ردّ بلا رمز حالة صالح: ${reply.startLine.take(140)}")
        }
    }

    // ── البناء ─────────────────────────────────────────────────────────────

    private fun buildRegister(expires: Int, auth: AuthHeader?): SipMessage {
        val seq = cSeq.incrementAndGet()
        val branch = "z9hG4bK" + Random.nextBytes(12).joinToString("") { "%02x".format(it) }
        val viaHost = config.contactHost ?: localIpv4() ?: "127.0.0.1"
        val localPort = transport.localPort.takeIf { it > 0 } ?: config.localUdpPort

        val via = buildString {
            append("SIP/2.0/${config.transportId.uppercase()} $viaHost")
            if (localPort > 0) append(':').append(localPort)
            append(";branch=").append(branch)
            // RFC 3581: مع rport لا يثق الدجَّار بمنفذ Contact بل يردّ على منفذ
            // المصدر الحقيقي — وهو ما يُبقي التسجيل حيًا خلف NAT/تبدّل المنافذ.
            if (config.enableRport) append(";rport")
        }
        // R-URI المسجَّل عليه = ما سيُحسب عليه HA2 بالضبط.
        val registrar = "sip:${config.host}"
        val aor = "sip:${config.username}@${config.effectiveDomain}"

        var message = SipMessage(
            startLine = SipMessage.requestLine("REGISTER", registrar),
            headers = listOf(
                "Via" to via,
                "Max-Forwards" to "70",
                "From" to "<$aor>;tag=$fromTag",
                "To" to "<$aor>",
                "Call-ID" to callId,
                "CSeq" to "$seq REGISTER",
                "Contact" to contactHeader(expires),
                "Expires" to expires.toString(),
                "Allow" to "REGISTER,INVITE,ACK,BYE,CANCEL,OPTIONS,NOTIFY,MESSAGE",
                "User-Agent" to config.userAgent,
            ),
        )
        if (auth != null) message = message.withHeader(auth.headerName, auth.value)
        return message
    }

    /**
     * `Contact` هو عنوان الدجَّار **للاتجاه العكسي**. منفذ المقبس الفعلي — لا
     * 5060 مُفترض — لأن الاتصال بـ `localUdpPort = 0` يمنح Android منفذًا مؤقتًا،
     * وإعلان 5060 يعني مكالمات واردة إلى لا أحد.
     */
    private fun contactHeader(expires: Int): String {
        val host = config.contactHost ?: localIpv4() ?: "127.0.0.1"
        val port = transport.localPort.takeIf { it > 0 } ?: config.localUdpPort
        return buildString {
            append("<sip:$host")
            if (port > 0) append(':').append(port)
            append(";transport=").append(config.transportId)
            append(";expires=").append(expires)
            append('>')
        }
    }

    /**
     * `Call-ID` و`from-tag` ثابتان طوال عمر الجلسة: تغييرهما عند كل تجديد
     * يجعل الدجَّار يرى حصة جديدة فيُسقِط القديمة، فتظهر «نافذة تسجيل» يفشل
     * فيها الاتصال العكسي.RFC 3261 §8.1.1.4 يفرض تطابقهما لرسائل خارج الحوار.
     */
    private val callId: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        "${UUID.randomUUID()}@${config.contactHost ?: localIpv4() ?: "android.local"}"
    }
    private val fromTag: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        MessageDigest.getInstance("SHA-256")
            .digest((callId + '|' + config.username).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.substring(0, 12)
    }

    data class AuthHeader(val headerName: String, val value: String)

    /**
     * رسالة خطأ بنبذة غير فارغة أبدًا.
     *
     * `cause` **لا** يُعلَّم `override` هنا: في Kotlin لا يمكن تجاوز خاصية
     * `cause` المشتقة من `Throwable.getCause()` (_accessor_ جافا اصطناعي، لا
     * عضو Kotlinي قابل للتجاوز) — فيُمرَّر إلى المُنشئ الفائق وتُقرأ كخاصية
     * للقراءة فقط (`e.cause`) من المستدعي.
     */
    class SipRegistrationException(
        val code: String,
        val detail: String,
        cause: Throwable? = null,
    ) : RuntimeException("$code — $detail", cause) {
        fun descriptiveMessage(): String = message?.takeIf { it.isNotBlank() } ?: code
    }

    private fun Throwable.descriptive(): String =
        message?.takeIf { it.isNotBlank() } ?: (javaClass.simpleName + (cause?.let { " caused by ${it.javaClass.simpleName}" } ?: ""))

    /**
     * IPv4 قابل للتوجيه محليًا، مُفضِّلًا 192.168.* (شبكة DINSTAR) ثم `wlan*`.
     * **لا** `InetAddress.getLocalHost()`: على Android تعيد `127.0.0.1` غالبًا،
     * والدجَّار يسجّلك على العنوان نفسه فيموت الاتجاه العكسي.
     */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedByDescending { it.name.startsWith("wlan") }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>() }
            .filter { it.isSiteLocalAddress }
            .let { list -> (list.firstOrNull { it.hostAddress.startsWith("192.168.") } ?: list.firstOrNull())?.hostAddress }
    }.getOrNull()

    private companion object {
        val FATAL_CODES = setOf(
            "MALFORMED_CHALLENGE", "UNSUPPORTED_CHALLENGE", "AUTH_REJECTED",
            "SIP_403", "SIP_404", "SIP_405", "SIP_481", "INTERVAL_TOO_BRIEF", "NO_LOCAL_ROUTE",
        )
    }
}
