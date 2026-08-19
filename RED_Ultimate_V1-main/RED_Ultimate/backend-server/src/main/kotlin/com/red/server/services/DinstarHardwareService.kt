package com.red.server.services

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.pstn.DinstarLoadBalancer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** محوّل UC2000-VE (‑4/8G و‑4/8T). لا يكشف إلا عمليات HTTP API الموثقة. */
@Service
class DinstarHardwareService(
    @Value("\${red.dinstar.enabled:false}") private val dinstarEnabled: Boolean,
    @Value("\${red.dinstar.ip}") private val configuredIp: String,
    @Value("\${red.dinstar.port:443}") private val configuredPort: Int,
    @Value("\${red.dinstar.scheme:https}") private val configuredScheme: String,
    @Value("\${red.dinstar.username:}") private val gatewayUsername: String,
    @Value("\${red.dinstar.password:}") private val gatewayPassword: String,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val connections: DinstarConnectionFactory
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarHardwareService::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * رموز القبول. 202 = «قُبل وسيُنفَّذ لاحقًا» ترجعه العمليات
         * غير المتزامنة (`send_sms`، `send_ussd`).
         */
        private val ACCEPTED_CODES = setOf(200, 202)

        /** الحد الموثق للمستلمين في طلب `send_sms` الواحد. */
        const val MAX_SMS_RECIPIENTS = 128

        /** الحد الموثق لحجم نص الرسالة. */
        const val MAX_SMS_TEXT_BYTES = 1500

        /** اطلب اشتقاق الترميز من محتوى الرسالة بدل فرضه. */
        const val AUTO_ENCODING = "AUTO"

        /**
         * اختيار الترميز من محتوى الرسالة.
         *
         * القاعدة: إن كان كل حرف موجودًا في أبجدية GSM 03.38 فالنص يُرسَل
         * `gsm-7bit` (160 حرفًا للجزء الواحد)؛ وإلا `unicode` (70 حرفًا).
         *
         * لماذا هذا مهم في اليمن تحديدًا: الرسائل هنا عربية في الغالب،
         * والعربية **ليست** في أبجدية GSM أصلًا. الافتراضي السابق كان
         * `GSM7BIT` لكل رسالة، فكانت كل رسالة عربية تصل «?????».
         *
         * ويُقصد بالاشتقاق لا الفرض: لو ثبّتنا `UCS2` دائمًا لحلَّت مشكلة
         * العربية وخُلقت أخرى — رسائل التحقق ورموز OTP بالإنجليزية تفقد
         * أكثر من نصف سعتها فتنقسم أجزاءً وتتضاعف كلفتها على كل مستخدم.
         */
        fun detectEncoding(text: String): String =
            if (text.all { it in GSM_03_38_ALPHABET }) "GSM7BIT" else "UCS2"

        /**
         * أبجدية GSM 03.38 الأساسية + جدول الهروب (Basic + Extension).
         *
         * كل حرف هنا يُمثَّل في 7 بتات (أو 14 لحروف الهروب)، فيتسع الجزء
         * الواحد 160 حرفًا. أي حرف خارجها — والعربية كلها خارجها — يفرض
         * الترميز UCS2 بسعة 70 حرفًا.
         *
         * المصدر: 3GPP TS 23.038 §6.2.1. مُدرَجة صراحةً لأن الاعتماد على
         * فحصٍ تقريبي مثل `isLetterOrDigit()` أو مدى ASCII يخطئ في
         * الاتجاهين: يقبل `[` و`{` وهي حروف هروب مزدوجة العرض، ويرفض
         * `é` و`Ø` وهي أساسية في الأبجدية.
         */
        internal val GSM_03_38_ALPHABET: Set<Char> = buildSet {
            // الأساسية
            addAll("@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./".toList())
            addAll("0123456789:;<=>?".toList())
            addAll("¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§".toList())
            addAll("¿abcdefghijklmnopqrstuvwxyzäöñüà".toList())
            // جدول الهروب — تُرسَل ببايتين لكنها تبقى ضمن gsm-7bit
            addAll("\u000C^{}\\[~]|€".toList())
        }
    }

    init {
        if (dinstarEnabled) {
            require(gatewayUsername.isNotBlank() && gatewayPassword.isNotBlank()) {
                "DINSTAR_ENABLED requires DINSTAR_USERNAME and DINSTAR_PASSWORD"
            }
            require(!(gatewayUsername == "admin" && gatewayPassword == "admin")) {
                "DINSTAR_ENABLED refuses the known default DINSTAR credentials"
            }
        }
    }

    /**
     * OkHttp client configured with:
     * 1. HTTP Digest + Basic auth (Dinstar New API ≥1102 uses Digest; older firmware uses Basic)
     * 2. Auth caching to avoid re-challenging on every request
     * 3. Trust-all SSL for Dinstar's self-signed certificate on private management network
     */
    private val client: OkHttpClient by lazy { buildOkHttpClient() }

    private fun buildOkHttpClient(): OkHttpClient {
        // --- Digest + Basic authenticator with caching ---
        val credentials = Credentials(gatewayUsername, gatewayPassword)
        val digestAuthenticator = DigestAuthenticator(credentials)
        val basicAuthenticator = BasicAuthenticator(credentials)

        // DispatchingAuthenticator handles both schemes; registered in lowercase
        val dispatchingAuthenticator = DispatchingAuthenticator.Builder()
            .with("digest", digestAuthenticator)
            .with("basic", basicAuthenticator)
            .build()

        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()

        // --- SSL: trust all certificates (Dinstar uses self-signed certs on private LAN) ---
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder()
            .authenticator(CachingAuthenticatorDecorator(dispatchingAuthenticator, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }  // Dinstar cert won't match IP hostname
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var activeHost = configuredIp

    /**
     * الطراز المكتشَف فعليًا. كان الملف يثبّت "UC2000-VE-8G" في كل
     * مكان، فالمتغيّرات الرباعية (‑4G/‑4T) تُسجَّل بطراز خاطئ ويُستعلَم
     * عن ثمانية منافذ على جهاز يملك أربعة. يُحدَّث عند أول اكتشاف
     * ناجح من عدد المنافذ التي ردّت فعلًا.
     */
    @Volatile private var detectedModel: DinstarModelProfile = DinstarModelProfile.UC2000_VE_8G

    /** مدى المنافذ الصالح للطراز المكتشَف — لا 0..7 مثبّتة. */
    private val portRange: IntRange get() = detectedModel.portRange

    private fun requireValidPort(port: Int) =
        require(port in portRange) {
            "منفذ خارج المدى: ${detectedModel.modelId} يدعم ${portRange.first}-${portRange.last}"
        }
    private val gatewayId: UUID get() = UUID.nameUUIDFromBytes("DINSTAR:$activeHost:$configuredPort".toByteArray())

    fun discoverGateway(): Map<String, Any> {
        val candidates = linkedSetOf(configuredIp, "192.168.11.1")
        for (host in candidates) {
            if (!isPrivateAddress(host)) continue
            val result = runCatching { discoverPorts(host) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            activeHost = host
            // الطراز يُستنتج من قدرات المنافذ التي ردّت: وجود راديو LTE
            // يعني ‑T، وعدد المنافذ يفصل الرباعي عن الثماني.
            detectedModel = inferModel(result)
            registerGateway(result.size)
            return mapOf(
                "success" to true, "gatewayIp" to host, "model" to detectedModel.modelId,
                "status" to "ONLINE", "portsDetected" to result.size,
                "capabilities" to documentedCapabilities()
            )
        }
        return mapOf(
            // بلا رد لا يُعرف الطراز — لا نضع null داخل Map<String, Any>
            "success" to false, "gatewayIp" to configuredIp, "model" to "UNKNOWN",
            "status" to "OFFLINE", "message" to "No authenticated UC2000 get_port_info response"
        )
    }

    fun getHardwareStatus(): List<Map<String, Any?>> {
        val info = queryPortInfo(activeHost)
        registerGateway(info.size)
        return info.mapNotNull(::normalizePort).also(::persistPorts)
    }

    /**
     * حالة منافذ بوابة بعينها من الأسطول.
     *
     * الإصدار بلا وسيط يخاطب العنوان المضبوط في الإعدادات فقط، وهو ما
     * كان يمنع تشغيل أكثر من جهاز. هنا يُبنى الاتصال من سجل البوابة،
     * ويُقرأ عدد المنافذ من طرازها بدل افتراض ثمانية.
     */
    fun getHardwareStatus(gateway: DinstarFleetService.Gateway): List<Map<String, Any?>> {
        val client = connections.clientFor(gateway.host, gateway.apiPort, gateway.scheme)
        val info = client.getPortInfo(gateway.portCount)
        return info.mapNotNull(::normalizePort).also { persistPorts(it, gateway.id) }
    }

    fun resetPort(port: Int): Map<String, Any> {
        requireValidPort(port)
        // set_port_info uses GET with query parameters per the official Dinstar API documentation
        val response = getJson("/api/set_port_info", mapOf("action" to "reset", "port" to port.toString()))
        require(apiSuccess(response)) { "تعذّرت إعادة تشغيل الوحدة: ${apiErrorMessage(response)}" }
        return mapOf("status" to "SUCCEEDED", "port" to port)
    }

    fun sendUssd(port: Int, text: String): Map<String, Any?> {
        requireValidPort(port)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = postJson("/api/send_ussd", mapOf("port" to listOf(port), "command" to "send", "text" to text))
        require(apiSuccess(response)) { "تعذّر إرسال USSD: ${apiErrorMessage(response)}" }
        return response
    }

    fun queryUssd(port: Int): Map<String, Any?> {
        requireValidPort(port)
        return getJson("/api/query_ussd_reply", mapOf("port" to port.toString()))
    }

    /** CDR must be POSTed with a JSON body per the official Dinstar API documentation. */
    fun queryCdr(): Map<String, Any?> = postJson("/api/get_cdr", mapOf("port" to portRange.toList(), "maximum" to 100))

    fun updateSipSettings(newSipIp: String): Nothing = unsupported(
        "Firmware-independent SIP configuration API is not documented for UC2000-VE; configure the SIP trunk in the gateway UI and Asterisk"
    )

    fun rebootDevice(): Nothing = unsupported(
        "A verified full-device reboot endpoint is not documented; use the gateway UI after active-call confirmation"
    )

    fun initiateCall(phoneNumber: String, slotIndex: Int = 0): Nothing = unsupported(
        "Voice calls must use Backend → Asterisk AMI → PJSIP → DINSTAR, not an invented DINSTAR /api/dial endpoint"
    )

    fun capabilities() = documentedCapabilities()

    // ═══════════════════════════════════════════════════════
    // 📱 SMS Operations — حسب وثائق Dinstar API الرسمية
    // ═══════════════════════════════════════════════════════

    /**
     * إرسال SMS — POST /api/send_sms
     *
     * المصدر: «Dinstar GSM Gateway HTTP API» §2 (الإصدار 1.1، 2019-10-16).
     *
     * @param text محتوى الرسالة. الحد 1500 بايت لكامل الطلب.
     * @param params قائمة المستلمين: [{number: "777123456", user_id: 1}]
     * @param ports منافذ محددة (اختياري، null = تختار البوابة)
     * @param encoding `GSM7BIT` أو `UCS2` — تُترجَم إلى قيم البوابة
     */
    /**
     * @param gatewayHost بوابة الإرسال. عند تركه فارغًا تُستعمل البوابة
     *   النشطة — سلوك النشر ذي الجهاز الواحد. مع أسطول من عدة بوابات
     *   كان كل SMS يخرج من جهاز واحد مهما بلغ عددها، فتُهدَر شرائح
     *   البقية ويُحتسب الإرسال كلّه خارج الشبكة على مشغّل واحد.
     *   العنوان يُتحقق من كونه خاصًا قبل استعماله: يصل من طلب HTTP،
     *   وتمريره بلا فحص يجعل الخادم يطلب أي عنوان يختاره المرسِل (SSRF).
     */
    fun sendSms(
        text: String,
        params: List<Map<String, Any?>>,
        ports: List<Int>? = null,
        encoding: String = AUTO_ENCODING,
        gatewayHost: String? = null
    ): Map<String, Any?> {
        require(text.isNotBlank()) { "SMS text is required" }
        require(params.isNotEmpty()) { "At least one recipient is required" }
        // الحد الموثق 128 مستلمًا لا 32؛ الرقم 32 يخص query_sms_result
        // فقط. الحد الأضيق كان يرفض دفعات مشروعة قبل أن تصل للبوابة.
        require(params.size <= MAX_SMS_RECIPIENTS) {
            "الحد الأقصى $MAX_SMS_RECIPIENTS مستلمًا في الطلب الواحد"
        }
        // الحد 1500 بايت لنص الطلب، والعربية بـ UTF-8 حتى 3 بايت للحرف
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_SMS_TEXT_BYTES) {
            "نص الرسالة يتجاوز $MAX_SMS_TEXT_BYTES بايت"
        }
        require(encoding in setOf(AUTO_ENCODING, "GSM7BIT", "UCS2")) {
            "Encoding must be $AUTO_ENCODING, GSM7BIT or UCS2"
        }

        // الترميز الفعلي: عند AUTO يُشتق من النص نفسه. الحرف العربي غير
        // موجود أصلًا في أبجدية GSM 03.38، فإرساله gsm-7bit يصل علامات
        // استفهام. والعكس مكلف: فرض unicode على نص ASCII يهبط بالسعة من
        // 160 حرفًا إلى 70 فتتضاعف الأجزاء والتكلفة. لذا يُقرَّر لكل رسالة.
        val effectiveEncoding = if (encoding == AUTO_ENCODING) detectEncoding(text) else encoding

        val body = mutableMapOf<String, Any>(
            "text" to text,
            "param" to params,
            // البوابة تتوقع 'gsm-7bit' أو 'unicode'. إرسال "GSM7BIT"
            // قيمة غير معروفة فترجع البوابة إلى الافتراضي 'unicode'.
            "encoding" to wireEncoding(effectiveEncoding),
            "request_status_report" to true
        )
        ports?.let { if (it.isNotEmpty()) body["port"] = it }

        val target = gatewayHost?.trim()?.takeIf { it.isNotEmpty() }
        require(target == null || isPrivateAddress(target)) {
            "بوابة SMS يجب أن تكون على عنوان خاص (RFC 1918)"
        }
        return postJson("/api/send_sms", body, target ?: activeHost)
    }

    /** ترجمة ترميزنا الداخلي إلى القيمة التي تفهمها البوابة. */
    private fun wireEncoding(encoding: String): String =
        if (encoding == "GSM7BIT") "gsm-7bit" else "unicode"

    /** جلب نتائج إرسال SMS — POST /api/query_sms_result */
    fun querySmsResult(userIds: List<Int> = emptyList(), numbers: List<String> = emptyList()): Map<String, Any?> {
        val body = mutableMapOf<String, Any>()
        if (userIds.isNotEmpty()) body["user_id"] = userIds
        if (numbers.isNotEmpty()) body["number"] = numbers
        return postJson("/api/query_sms_result", body)
    }

    /** جلب حالة تسليم SMS — POST /api/query_sms_deliver_status */
    fun querySmsDeliveryStatus(
        numbers: List<String> = emptyList(),
        timeAfter: String? = null,
        timeBefore: String? = null
    ): Map<String, Any?> {
        val body = mutableMapOf<String, Any>()
        if (numbers.isNotEmpty()) body["number"] = numbers
        timeAfter?.let { body["time_after"] = it }
        timeBefore?.let { body["time_before"] = it }
        return postJson("/api/query_sms_deliver_status", body)
    }

    /** جلب SMS الواردة — GET /api/query_incoming_sms */
    fun queryIncomingSms(): Map<String, Any?> = getJson("/api/query_incoming_sms", emptyMap())

    /** عدد SMS في الطابور — GET /api/query_sms_count */
    fun querySmsQueueCount(): Map<String, Any?> = getJson("/api/query_sms_count", emptyMap())

    /** إيقاف مهمة إرسال SMS — GET /api/stop_sms?task_id=N */
    fun stopSmsTask(taskId: Int): Map<String, Any?> {
        require(taskId >= 0) { "Invalid task_id" }
        return getJson("/api/stop_sms", mapOf("task_id" to taskId.toString()))
    }

    // ═══════════════════════════════════════════════════════
    // 📞 Advanced Port Operations — حسب وثائق Dinstar
    // ═══════════════════════════════════════════════════════

    /** Call Forward — GET /api/set_port_info?action=CallForward */
    fun setCallForward(port: Int, param: String, number: String): Map<String, Any?> {
        requireValidPort(port)
        require(param in setOf("Unconditional", "NoReply", "Busy", "Not_Reachable", "CancelAll")) { "Invalid CallForward param" }
        return getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "CallForward",
            "param" to param, "number" to number
        ))
    }

    /** Power on/off port — GET /api/set_port_info?action=power&param=on/off */
    fun setPortPower(port: Int, on: Boolean): Map<String, Any?> {
        requireValidPort(port)
        return getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "power", "param" to if (on) "on" else "off"
        ))
    }

    /** Get Device Status — POST /api/get_status */
    fun getDeviceStatus(): Map<String, Any?> = postJson("/api/get_status", mapOf("maximum" to 10))

    // ═══════════════════════════════════════════════════════
    // 🌐 عمليات موجّهة إلى بوابة بعينها من الأسطول
    //
    // النسخ بلا وسيط تخاطب العنوان المضبوط في الإعدادات فقط. هذه
    // الوسائط تبني الاتصال من سجل البوابة (مضيف/منفذ/مخطط) عبر
    // [DinstarConnectionFactory]، فيعمل كل جهاز مسجّل لا جهاز واحد.
    // ═══════════════════════════════════════════════════════

    private fun clientFor(gateway: DinstarFleetService.Gateway): DinstarConnectionFactory.DinstarClient =
        connections.clientFor(gateway.host, gateway.apiPort, gateway.scheme)

    private fun requireGatewayPort(gateway: DinstarFleetService.Gateway, port: Int) =
        require(port in 0 until gateway.portCount) {
            "منفذ خارج المدى: البوابة ${gateway.host} تدعم 0-${gateway.portCount - 1}"
        }

    /** حالة جهاز بوابة محددة (CPU/ذاكرة/فلاش) — POST /api/get_status */
    fun getDeviceStatus(gateway: DinstarFleetService.Gateway): Map<String, Any?> =
        clientFor(gateway).getDeviceStatus()

    /**
     * سجل المكالمات CDR لبوابة محددة — POST /api/get_cdr بجسم JSON.
     *
     * عند غياب [port] تُستعلم كل منافذ البوابة حسب عددها الفعلي.
     * الاستجابة تحمل السجلات في حقل `cdr` (وبعض الإصدارات `info`).
     */
    fun getCdrRecords(
        gateway: DinstarFleetService.Gateway,
        port: Int? = null,
        timeAfter: String? = null,
        timeBefore: String? = null
    ): List<Map<String, Any?>> {
        port?.let { requireGatewayPort(gateway, it) }
        val body = mutableMapOf<String, Any>(
            "port" to (port?.let { listOf(it) } ?: (0 until gateway.portCount).toList()),
            "maximum" to 100
        )
        timeAfter?.let { body["time_after"] = it }
        timeBefore?.let { body["time_before"] = it }
        val response = clientFor(gateway).postJson("/api/get_cdr", body)
        @Suppress("UNCHECKED_CAST")
        return (response["cdr"] as? List<Map<String, Any?>>)
            ?: (response["info"] as? List<Map<String, Any?>>)
            ?: emptyList()
    }

    /** إرسال USSD عبر بوابة محددة — POST /api/send_ussd */
    fun sendUssd(gateway: DinstarFleetService.Gateway, port: Int, text: String): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = clientFor(gateway).postJson(
            "/api/send_ussd",
            mapOf("port" to listOf(port), "command" to "send", "text" to text)
        )
        require(apiSuccess(response)) { "تعذّر إرسال USSD: ${apiErrorMessage(response)}" }
        return response
    }

    /** تشغيل/إيقاف منفذ في بوابة محددة — GET /api/set_port_info?action=power */
    fun setPortPower(gateway: DinstarFleetService.Gateway, port: Int, on: Boolean): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        return clientFor(gateway).getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "power", "param" to if (on) "on" else "off"
        ))
    }

    /**
     * تحويل المكالمات في بوابة محددة — GET /api/set_port_info?action=CallForward.
     *
     * [condition] يقبل الصيغ الداخلية (ALWAYS/NO_REPLY/BUSY/NOT_REACHABLE)
     * وتُترجم إلى قيم البوابة الموثقة. التعطيل = CancelAll ولا يحتاج رقمًا.
     */
    fun setCallForward(
        gateway: DinstarFleetService.Gateway,
        port: Int,
        enabled: Boolean,
        number: String? = null,
        condition: String? = null
    ): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        if (enabled) require(!number.isNullOrBlank()) { "رقم التحويل مطلوب عند التفعيل" }
        val param = if (!enabled) "CancelAll" else when (condition?.trim()?.uppercase()) {
            null, "", "ALWAYS", "UNCONDITIONAL" -> "Unconditional"
            "NO_REPLY", "NOREPLY" -> "NoReply"
            "BUSY" -> "Busy"
            "NOT_REACHABLE", "UNREACHABLE" -> "Not_Reachable"
            else -> throw IllegalArgumentException("Invalid CallForward condition: $condition")
        }
        return clientFor(gateway).getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "CallForward",
            "param" to param, "number" to (number ?: "")
        ))
    }

    fun recordOperation(actorId: UUID, operation: String, port: Int?, status: String, details: Map<String, Any?> = emptyMap()) {
        require(status in setOf("REQUESTED", "SUCCEEDED", "FAILED", "REJECTED"))
        registerGateway(0)
        jdbc.update(
            "INSERT INTO gateway_operations(id,gateway_id,actor_id,operation,target_port,status,details_json,completed_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            UUID.randomUUID(), gatewayId, actorId, operation, port, status, mapper.writeValueAsString(details)
        )
    }

    private fun queryPortInfo(host: String): List<Map<String, Any?>> =
        queryPortInfo(host, portRange)

    private fun queryPortInfo(host: String, ports: IntRange): List<Map<String, Any?>> {
        val response = getJson(
            "/api/get_port_info",
            mapOf("port" to ports.joinToString(","), "info_type" to "type,imei,imsi,iccid,number,reg,slot,callstate,signal,gprs"),
            host
        )
        require(apiSuccess(response)) { "تعذّر استعلام المنافذ: ${apiErrorMessage(response)}" }
        @Suppress("UNCHECKED_CAST")
        return response["info"] as? List<Map<String, Any?>> ?: emptyList()
    }

    /**
     * استعلام المنافذ أثناء الاكتشاف، حين لا يكون الطراز معروفًا بعد.
     *
     * يُجرَّب المدى الأوسع (ثمانية منافذ) أولًا. بعض الإصدارات ترفض
     * الطلب كاملًا إذا تضمّن منفذًا غير موجود بدل تجاهله، فلو أخفق
     * يُعاد المحاولة بالمدى الرباعي. بغير هذا التراجع يظهر جهاز رباعي
     * سليم على أنه غير متصل.
     */
    private fun discoverPorts(host: String): List<Map<String, Any?>> {
        val widest = DinstarModelProfile.UC2000_VE_8G.portRange
        runCatching { queryPortInfo(host, widest) }
            .onSuccess { if (it.isNotEmpty()) return it }
        log.debug("تعذّر استعلام {} منفذًا على {}؛ إعادة المحاولة بالمدى الرباعي", widest.count(), host)
        return queryPortInfo(host, DinstarModelProfile.UC2000_VE_4G.portRange)
    }

    /**
     * استنتاج الطراز من المنافذ التي ردّت فعلًا.
     *
     * لا تُفصح `get_port_info` عن اسم الطراز، لكنها تكشف حقيقتين
     * كافيتين للتمييز بين الطرازات الأربعة:
     *
     * 1. **عدد المنافذ** — يفصل الرباعي (‑4G/‑4T) عن الثماني (‑8G/‑8T).
     * 2. **نوع الراديو** لكل منفذ — وجود LTE أو WCDMA يعني المتغيّر ‑T؛
     *    الطراز ‑G وحدات GSM بحتة.
     *
     * لا تُستنتج النطاقات الترددية: في الطراز ‑T تعتمد على متغيّر
     * الراديو المركّب (Type A/E/V/J/AU) ولا تظهر في هذه الاستجابة.
     */
    private fun inferModel(ports: List<Map<String, Any?>>): DinstarModelProfile {
        val hasLteRadio = ports.any { port ->
            val type = port["type"]?.toString()?.uppercase().orEmpty()
            "LTE" in type || "WCDMA" in type || "VOLTE" in type
        }
        // أربعة منافذ أو أقل ⇒ المتغيّر الرباعي. الردّ الفارغ يبقى على
        // الافتراضي بدل ترجيح طراز بلا دليل.
        val isQuad = ports.isNotEmpty() && ports.size <= 4
        return when {
            isQuad && hasLteRadio -> DinstarModelProfile.UC2000_VE_4T
            isQuad -> DinstarModelProfile.UC2000_VE_4G
            hasLteRadio -> DinstarModelProfile.UC2000_VE_8T
            else -> DinstarModelProfile.UC2000_VE_8G
        }
    }

    /**
     * تحديد اسم المشغّل، مع تصحيح الأسماء القديمة أو الخاطئة التي تُرجعها
     * البوابة (MTN ← YOU بعد 2021، HiTel ← YTelecom).
     *
     * جدول البادئات **ليس هنا**: المصدر الوحيد للحقيقة هو
     * [DinstarLoadBalancer.classifyNumber]. كان هذا الملف يحمل نسخته
     * الخاصة `YEMEN_OPERATOR_PREFIXES` تطابق بخانتين ثابتتين، فتقرأ
     * شريحة سبأفون عدن `722…` على أنها `72` وتُسقطها في «غير معروف»؛
     * فتفشل مطابقة «داخل الشبكة» وتُوجَّه المكالمة عبر شريحة مشغّل آخر
     * بتعرفة أعلى. التفويض أدناه يمنع تكرار هذا الانحراف.
     */
    private fun resolveOperatorName(apiName: String?, simNumber: String?): String {
        // الأوثق: بادئة رقم الشريحة نفسها (تطابق الأطول أولًا: 722 قبل 72)
        if (!simNumber.isNullOrBlank()) {
            DinstarLoadBalancer.classifyNumber(simNumber)?.let { return it.apiName }
        }
        // Second try: match API operator name with corrections
        if (!apiName.isNullOrBlank() && apiName != "UNKNOWN") {
            return when {
                apiName.contains("Sabafon", ignoreCase = true) -> "Sabafon"
                apiName.contains("YOU", ignoreCase = true) || apiName.contains("Yemeni Omani", ignoreCase = true) -> "YOU"
                apiName.contains("MTN", ignoreCase = true) -> "YOU"  // MTN → YOU since 2021
                apiName.contains("Yemen", ignoreCase = true) && apiName.contains("Mobile", ignoreCase = true) -> "YemenMobile"
                apiName.contains("Y Telecom", ignoreCase = true) || apiName == "Y" -> "YTelecom"
                apiName.contains("HiTel", ignoreCase = true) || apiName.contains("Hi Tel", ignoreCase = true) -> "YTelecom"  // HiTel→YTelecom
                apiName.contains("Yemen 4G", ignoreCase = true) -> "Yemen4G"
                else -> apiName  // Return as-is if unrecognized
            }
        }
        return "UNKNOWN"
    }

    private fun normalizePort(raw: Map<String, Any?>): Map<String, Any?>? {
        val index = (raw["port"] as? Number)?.toInt() ?: return null
        val simNumber = raw["number"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val apiOperator = raw["operator"]?.toString()
        val resolvedOperator = resolveOperatorName(apiOperator, simNumber)

        // تفسير الإشارة حسب 3GPP TS 27.007 §8.5 بدل القسمة الساذجة على 31.
        // كانت `coerceIn(0,31)` تحوّل القراءة 99 — ومعناها «لا توجد شبكة» —
        // إلى 31 أي 100%، فتظهر شريحة ميتة بإشارة كاملة ويختارها الموزّع.
        val signal = DinstarSignal.interpret(raw["signal"])

        return mapOf(
            "index" to index,
            "radioType" to raw["type"].toString(),
            "status" to raw["reg"].toString(),
            "callState" to raw["callstate"].toString(),
            "gprs" to raw["gprs"].toString(),
            "numberMasked" to mask(simNumber),
            "imsiMasked" to mask(raw["imsi"]?.toString()),
            "iccidMasked" to mask(raw["iccid"]?.toString()),
            "operator" to resolvedOperator
        ) + signal.toMap()
    }

    private fun registerGateway(portCount: Int) {
        val capabilities = mapper.writeValueAsString(documentedCapabilities() + ("portsDetected" to portCount))
        jdbc.update(
            """INSERT INTO telecom_gateways(id,name,vendor,model,host,scheme,api_port,capabilities_json,last_seen_at)
               VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
               ON CONFLICT (host,api_port) DO UPDATE SET model=EXCLUDED.model,scheme=EXCLUDED.scheme,
               capabilities_json=EXCLUDED.capabilities_json,last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP""",
            gatewayId, "YOUNES DINSTAR Sanaa", "DINSTAR", detectedModel.modelId, activeHost, configuredScheme, configuredPort, capabilities
        )
    }

    private fun persistPorts(ports: List<Map<String, Any?>>, targetGatewayId: UUID = gatewayId) {
        ports.forEach { port ->
            jdbc.update(
                """INSERT INTO gateway_port_snapshots(gateway_id,port_index,radio_type,registration_state,call_state,signal_raw,signal_dbm,signal_percent,signal_usable,operator_name,gprs_state,sim_number_masked,imsi_masked,iccid_masked)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (gateway_id,port_index) DO UPDATE SET
                   radio_type=EXCLUDED.radio_type,registration_state=EXCLUDED.registration_state,call_state=EXCLUDED.call_state,
                   signal_raw=EXCLUDED.signal_raw,signal_dbm=EXCLUDED.signal_dbm,signal_percent=EXCLUDED.signal_percent,
                   signal_usable=EXCLUDED.signal_usable,operator_name=EXCLUDED.operator_name,gprs_state=EXCLUDED.gprs_state,
                   sim_number_masked=EXCLUDED.sim_number_masked,imsi_masked=EXCLUDED.imsi_masked,iccid_masked=EXCLUDED.iccid_masked,observed_at=CURRENT_TIMESTAMP""",
                targetGatewayId, port["index"], port["radioType"], port["status"], port["callState"],
                port["signalRaw"], port["signalDbm"], port["signal"], port["signalUsable"] ?: false,
                port["operator"], port["gprs"], port["numberMasked"], port["imsiMasked"], port["iccidMasked"]
            )
        }
    }

    private fun getJson(path: String, query: Map<String, String>, host: String = activeHost): Map<String, Any?> {
        val builder = baseUrl(host).newBuilder().addPathSegments(path.removePrefix("/"))
        query.forEach(builder::addQueryParameter)
        return execute(Request.Builder().url(builder.build()).get().build())
    }

    /**
     * @param host البوابة المقصودة. الافتراضي [activeHost] توافقًا مع
     *   النشر ذي الجهاز الواحد، لكن المعامل ضروري مع الأسطول: كانت
     *   الدالة تثبّت [activeHost] فتذهب كل رسالة إلى بوابة واحدة مهما
     *   بلغ عدد الأجهزة المسجّلة — نظير `getJson` الذي كان يقبل الخيار.
     */
    private fun postJson(path: String, value: Any, host: String = activeHost): Map<String, Any?> {
        val body = mapper.writeValueAsBytes(value).toRequestBody(JSON)
        return execute(Request.Builder().url(baseUrl(host).newBuilder().addPathSegments(path.removePrefix("/")).build()).post(body).build())
    }

    private fun execute(unsigned: Request): Map<String, Any?> {
        require(gatewayUsername.isNotBlank() && gatewayPassword.isNotBlank()) { "DINSTAR credentials must be configured" }

        // The DispatchingAuthenticator will handle 401 challenges automatically:
        //   - If the server sends "WWW-Authenticate: Digest ...", it uses DigestAuthenticator
        //   - If the server sends "WWW-Authenticate: Basic ...", it uses BasicAuthenticator
        //   - The AuthenticationCacheInterceptor caches successful auths to avoid re-challenge overhead
        val request = unsigned.newBuilder()
            .header("Accept", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val challenge = response.challenges().joinToString(", ") { "${it.scheme} realm=${it.realm}" }
                log.error("DINSTAR HTTP {} on {} — auth challenge: {}", response.code, unsigned.url, challenge)
                throw IllegalStateException("DINSTAR HTTP ${response.code} on ${unsigned.url.encodedPath} — auth challenge: $challenge")
            }
            @Suppress("UNCHECKED_CAST")
            val responseBody = requireNotNull(response.body) { "DINSTAR returned an empty HTTP body" }
            mapper.readValue(responseBody.bytes(), Map::class.java) as Map<String, Any?>
        }
    }

    private fun baseUrl(host: String) = "$configuredScheme://$host:$configuredPort".also {
        require(configuredScheme in setOf("http", "https") && isPrivateAddress(host)) { "DINSTAR must use HTTP(S) on a private management address" }
    }.toHttpUrl()

    /**
     * هل قبلت البوابة الطلب؟
     *
     * التوثيق الرسمي («Dinstar GSM Gateway HTTP API» §2.3 و§7.3) يميّز:
     * - **200** طُلب ونُفِّذ.
     * - **202** قُبل وسيُنفَّذ لاحقًا — ترجعه `send_sms` و`send_ussd`
     *   لأنهما غير متزامنين بطبيعتهما (الرسالة تدخل طابورًا).
     *
     * كان الشرط `== 200` يرفض 202، فكل أمر USSD ناجح يُرمى
     * `IllegalArgumentException` ويُسجَّل فشلًا رغم تنفيذه فعلًا على
     * الشبكة. المسؤول يرى «فشل» ثم يعيد المحاولة فيُرسَل الأمر مرتين.
     *
     * أما 400 و413 و500 و550 فأخطاء حقيقية تُرفض.
     */
    private fun apiSuccess(response: Map<String, Any?>): Boolean =
        (response["error_code"] as? Number)?.toInt() in ACCEPTED_CODES

    /** رسالة الخطأ الموثقة المقابلة للرمز — أوضح من رقم مجرّد. */
    private fun apiErrorMessage(response: Map<String, Any?>): String {
        val code = (response["error_code"] as? Number)?.toInt()
        val meaning = when (code) {
            400 -> "صيغة الطلب غير صالحة"
            404 -> "المهمة غير موجودة"
            413 -> "عدد المستلمين أو حجم النص يتجاوز الحد"
            486 -> "المنفذ مشغول حاليًا"
            500 -> "خطأ داخلي في البوابة"
            503 -> "المنفذ غير مسجّل على الشبكة"
            550 -> "لا يوجد منفذ متاح للإرسال"
            null -> "استجابة بلا error_code"
            else -> "رمز غير موثق"
        }
        return "البوابة ردّت $code — $meaning"
    }
    private fun isPrivateAddress(host: String) = runCatching { InetAddress.getByName(host).isSiteLocalAddress }.getOrDefault(false)
    private fun mask(value: String?): String? = value?.takeIf { it.isNotBlank() && it != "null" }?.let { "••••${it.takeLast(4)}" }
    private fun unsupported(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, message)

    private fun documentedCapabilities(): Map<String, Any> = mapOf(
        "voiceViaAsterisk" to true,
        "portInfo" to true,
        "moduleReset" to true,
        "sms" to true,
        "ussd" to true,
        "cdr" to true,
        "configBackupViaUi" to true,
        "firmwareUpgradeViaUi" to true,
        "remoteFirmwareUpgrade" to false,
        "remoteNetworkConfig" to false,
        "factoryResetFromYounes" to false
    )
}
