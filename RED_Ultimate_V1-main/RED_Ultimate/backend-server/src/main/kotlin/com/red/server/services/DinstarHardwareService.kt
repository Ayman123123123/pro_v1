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
import kotlin.math.roundToInt

/** Verified UC2000-VE-8G adapter. Only documented HTTP API operations are exposed. */
@Service
class DinstarHardwareService(
    @Value("\${red.dinstar.ip}") private val configuredIp: String,
    @Value("\${red.dinstar.port:443}") private val configuredPort: Int,
    @Value("\${red.dinstar.scheme:https}") private val configuredScheme: String,
    @Value("\${red.dinstar.username:admin}") private val gatewayUsername: String,
    @Value("\${red.dinstar.password:admin}") private val gatewayPassword: String,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate
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
    private val gatewayId: UUID get() = UUID.nameUUIDFromBytes("DINSTAR:$activeHost:$configuredPort".toByteArray())

    fun discoverGateway(): Map<String, Any> {
        val candidates = linkedSetOf(configuredIp, "192.168.11.1")
        for (host in candidates) {
            if (!isPrivateAddress(host)) continue
            val result = runCatching { queryPortInfo(host) }.getOrNull() ?: continue
            activeHost = host
            registerGateway(result.size)
            return mapOf(
                "success" to true, "gatewayIp" to host, "model" to "UC2000-VE-8G",
                "status" to "ONLINE", "portsDetected" to result.size,
                "capabilities" to documentedCapabilities()
            )
        }
        return mapOf(
            "success" to false, "gatewayIp" to configuredIp, "model" to "UC2000-VE-8G",
            "status" to "OFFLINE", "message" to "No authenticated UC2000 get_port_info response"
        )
    }

    fun getHardwareStatus(): List<Map<String, Any?>> {
        val info = queryPortInfo(activeHost)
        registerGateway(info.size)
        return info.mapNotNull(::normalizePort).also(::persistPorts)
    }

    fun resetPort(port: Int): Map<String, Any> {
        require(port in 0..7) { "UC2000-VE-8G port must be 0-7" }
        // set_port_info uses GET with query parameters per the official Dinstar API documentation
        val response = getJson("/api/set_port_info", mapOf("action" to "reset", "port" to port.toString()))
        require(apiSuccess(response)) { "DINSTAR rejected module reset" }
        return mapOf("status" to "SUCCEEDED", "port" to port)
    }

    fun sendUssd(port: Int, text: String): Map<String, Any?> {
        require(port in 0..7)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = postJson("/api/send_ussd", mapOf("port" to listOf(port), "command" to "send", "text" to text))
        require(apiSuccess(response)) { "DINSTAR rejected USSD request" }
        return response
    }

    fun queryUssd(port: Int): Map<String, Any?> {
        require(port in 0..7)
        return getJson("/api/query_ussd_reply", mapOf("port" to port.toString()))
    }

    /** CDR must be POSTed with a JSON body per the official Dinstar API documentation. */
    fun queryCdr(): Map<String, Any?> = postJson("/api/get_cdr", mapOf("port" to (0..7).toList(), "maximum" to 100))

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
    fun querySmsQueueCount(): Map<String, Any> = getJson("/api/query_sms_count", emptyMap())

    /** إيقاف مهمة إرسال SMS — GET /api/stop_sms?task_id=N */
    fun stopSmsTask(taskId: Int): Map<String, Any> {
        require(taskId >= 0) { "Invalid task_id" }
        return getJson("/api/stop_sms", mapOf("task_id" to taskId.toString()))
    }

    // ═══════════════════════════════════════════════════════
    // 📞 Advanced Port Operations — حسب وثائق Dinstar
    // ═══════════════════════════════════════════════════════

    /** Call Forward — GET /api/set_port_info?action=CallForward */
    fun setCallForward(port: Int, param: String, number: String): Map<String, Any> {
        require(port in 0..7) { "Port must be 0-7" }
        require(param in setOf("Unconditional", "NoReply", "Busy", "Not_Reachable", "CancelAll")) { "Invalid CallForward param" }
        return getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "CallForward",
            "param" to param, "number" to number
        ))
    }

    /** Power on/off port — GET /api/set_port_info?action=power&param=on/off */
    fun setPortPower(port: Int, on: Boolean): Map<String, Any> {
        require(port in 0..7) { "Port must be 0-7" }
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

    private fun queryPortInfo(host: String): List<Map<String, Any?>> {
        val response = getJson(
            "/api/get_port_info",
            mapOf("port" to (0..7).joinToString(","), "info_type" to "type,imei,imsi,iccid,number,reg,slot,callstate,signal,gprs"),
            host
        )
        require(apiSuccess(response)) { "DINSTAR get_port_info failed" }
        @Suppress("UNCHECKED_CAST")
        return response["info"] as? List<Map<String, Any?>> ?: emptyList()
    }

    private fun normalizePort(raw: Map<String, Any?>): Map<String, Any?>? {
        val index = (raw["port"] as? Number)?.toInt() ?: return null
        val signalRaw = (raw["signal"] as? Number)?.toInt()?.coerceIn(0, 31) ?: 0
        return mapOf(
            "index" to index,
            "radioType" to raw["type"].toString(),
            "status" to raw["reg"].toString(),
            "callState" to raw["callstate"].toString(),
            "signalRaw" to signalRaw,
            "signal" to (signalRaw / 31.0 * 100).roundToInt(),
            "gprs" to raw["gprs"].toString(),
            "numberMasked" to mask(raw["number"]?.toString()),
            "imsiMasked" to mask(raw["imsi"]?.toString()),
            "iccidMasked" to mask(raw["iccid"]?.toString()),
            "operator" to "UNKNOWN"
        )
    }

    private fun registerGateway(portCount: Int) {
        val capabilities = mapper.writeValueAsString(documentedCapabilities() + ("portsDetected" to portCount))
        jdbc.update(
            """INSERT INTO telecom_gateways(id,name,vendor,model,host,scheme,api_port,capabilities_json,last_seen_at)
               VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
               ON CONFLICT (host,api_port) DO UPDATE SET model=EXCLUDED.model,scheme=EXCLUDED.scheme,
               capabilities_json=EXCLUDED.capabilities_json,last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP""",
            gatewayId, "YOUNES DINSTAR Sanaa", "DINSTAR", "UC2000-VE-8G", activeHost, configuredScheme, configuredPort, capabilities
        )
    }

    private fun persistPorts(ports: List<Map<String, Any?>>) {
        ports.forEach { port ->
            jdbc.update(
                """INSERT INTO gateway_port_snapshots(gateway_id,port_index,radio_type,registration_state,call_state,signal_raw,signal_percent,gprs_state,sim_number_masked,imsi_masked,iccid_masked)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (gateway_id,port_index) DO UPDATE SET
                   radio_type=EXCLUDED.radio_type,registration_state=EXCLUDED.registration_state,call_state=EXCLUDED.call_state,
                   signal_raw=EXCLUDED.signal_raw,signal_percent=EXCLUDED.signal_percent,gprs_state=EXCLUDED.gprs_state,
                   sim_number_masked=EXCLUDED.sim_number_masked,imsi_masked=EXCLUDED.imsi_masked,iccid_masked=EXCLUDED.iccid_masked,observed_at=CURRENT_TIMESTAMP""",
                gatewayId, port["index"], port["radioType"], port["status"], port["callState"], port["signalRaw"], port["signal"], port["gprs"], port["numberMasked"], port["imsiMasked"], port["iccidMasked"]
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
                val challenge = response.challenges().joinToString(", ") { "${it.scheme()} realm=${it.realm()}" }
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
