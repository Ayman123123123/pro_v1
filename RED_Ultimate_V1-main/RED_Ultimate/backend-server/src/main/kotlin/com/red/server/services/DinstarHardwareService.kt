package com.red.server.services

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.fasterxml.jackson.databind.ObjectMapper
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
    @Value("\${red.dinstar.ip}") private val configuredIp: String,
    @Value("\${red.dinstar.port:443}") private val configuredPort: Int,
    @Value("\${red.dinstar.scheme:https}") private val configuredScheme: String,
    @Value("\${red.dinstar.username:admin}") private val gatewayUsername: String,
    @Value("\${red.dinstar.password:admin}") private val gatewayPassword: String,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val connections: DinstarConnectionFactory
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarHardwareService::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()
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
            // بلا رد لا يُعرف الطراز — ادعاؤه تخمين
            "success" to false, "gatewayIp" to configuredIp, "model" to null,
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
        require(apiSuccess(response)) { "DINSTAR rejected module reset" }
        return mapOf("status" to "SUCCEEDED", "port" to port)
    }

    fun sendUssd(port: Int, text: String): Map<String, Any?> {
        requireValidPort(port)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = postJson("/api/send_ussd", mapOf("port" to listOf(port), "command" to "send", "text" to text))
        require(apiSuccess(response)) { "DINSTAR rejected USSD request" }
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
     * @param text محتوى الرسالة (حتى 60 بايت لـ GSM7BIT)
     * @param params قائمة المستلمين: [{number: "777123456", user_id: 1}]
     * @param ports منافذ محددة (اختياري، null = جميع المنافذ)
     * @param encoding GSM7BIT أو UCS2
     */
    fun sendSms(
        text: String,
        params: List<Map<String, Any?>>,
        ports: List<Int>? = null,
        encoding: String = "GSM7BIT"
    ): Map<String, Any?> {
        require(text.isNotBlank()) { "SMS text is required" }
        require(params.isNotEmpty()) { "At least one recipient is required" }
        require(params.size <= 32) { "Maximum 32 recipients per request" }
        require(encoding in setOf("GSM7BIT", "UCS2")) { "Encoding must be GSM7BIT or UCS2" }
        
        val body = mutableMapOf<String, Any>(
            "text" to text,
            "param" to params,
            "encoding" to encoding,
            "request_status_report" to true
        )
        ports?.let { if (it.isNotEmpty()) body["port"] = it }
        
        return postJson("/api/send_sms", body)
    }

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
        require(apiSuccess(response)) { "DINSTAR get_port_info failed" }
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
     * Yemen mobile operator prefixes — CORRECTED per Wikipedia + ITU E.164
     * | Prefix | Operator                        |
     * |--------|---------------------------------|
     * | 71     | سبأفون (Sabafon)               |
     * | 73     | يو / YOU (formerly MTN Yemen)   |
     * | 77, 78 | يمن موبايل (Yemen Mobile)      |
     * | 70     | واي (Y Telecom)                |
     * | 10     | يمن 4G (Yemen 4G)              |
     */
    private val YEMEN_OPERATOR_PREFIXES: Map<String, String> = mapOf(
        "71" to "Sabafon",
        "73" to "YOU",
        "77" to "YemenMobile",
        "78" to "YemenMobile",
        "70" to "YTelecom",
        "10" to "Yemen4G"
    )

    /** Resolve operator name: maps old/wrong names (MTN→YOU, HiTel→YTelecom) to correct Yemen operator */
    private fun resolveOperatorName(apiName: String?, simNumber: String?): String {
        // First try: use SIM number prefix (most reliable)
        if (!simNumber.isNullOrBlank()) {
            val digits = simNumber.filter { it.isDigit() }
            val local = when {
                digits.startsWith("967") -> digits.removePrefix("967")
                digits.startsWith("0") -> digits.removePrefix("0")
                else -> digits
            }
            if (local.length >= 2) {
                val prefix = local.substring(0, 2)
                YEMEN_OPERATOR_PREFIXES[prefix]?.let { return it }
            }
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

    private fun postJson(path: String, value: Any): Map<String, Any?> {
        val body = mapper.writeValueAsBytes(value).toRequestBody(JSON)
        return execute(Request.Builder().url(baseUrl(activeHost).newBuilder().addPathSegments(path.removePrefix("/")).build()).post(body).build())
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

    private fun apiSuccess(response: Map<String, Any?>) = (response["error_code"] as? Number)?.toInt() == 200
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
