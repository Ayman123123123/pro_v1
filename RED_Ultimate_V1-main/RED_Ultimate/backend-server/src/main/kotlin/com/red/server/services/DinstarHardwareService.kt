package com.red.server.services

import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
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
    private val digestCredentials = Credentials(gatewayUsername, gatewayPassword)
    private val digestAuthenticator = CachingAuthenticator(Credentials(gatewayUsername, gatewayPassword))
    
    private val client: OkHttpClient = run {
        val builder = OkHttpClient.Builder()
            .authenticator(digestAuthenticator)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
        // Configure SSL for self-signed certs
        import com.red.server.dinstar.service.GatewaySsl
        GatewaySsl.configure(builder).build()
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
        val info = runCatching { queryPortInfo(activeHost) }.getOrElse {
            registerGateway(0)
            return emptyList()
        }
        registerGateway(info.size)
        return info.mapNotNull(::normalizePort).also(::persistPorts)
    }

    fun resetPort(port: Int): Map<String, Any> {
        require(port in 0..7) { "UC2000-VE-8G port must be 0-7" }
        val response = runCatching { getJson("/api/set_port_info", mapOf("action" to "reset", "port" to port.toString())) }
            .getOrElse { return mapOf("status" to "FAILED", "port" to port, "error" to networkError(it)) }
        require(apiSuccess(response)) { "DINSTAR rejected module reset" }
        return mapOf("status" to "SUCCEEDED", "port" to port)
    }

    fun sendUssd(port: Int, text: String): Map<String, Any?> {
        require(port in 0..7)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = runCatching { postJson("/api/send_ussd", mapOf("port" to listOf(port), "command" to "send", "text" to text)) }
            .getOrElse { return mapOf("status" to "FAILED", "port" to port, "error" to networkError(it)) }
        require(apiSuccess(response)) { "DINSTAR rejected USSD request" }
        return response
    }

    fun queryUssd(port: Int): Map<String, Any?> {
        require(port in 0..7)
        return runCatching { getJson("/api/query_ussd_reply", mapOf("port" to port.toString())) }
            .getOrElse { mapOf("error" to networkError(it)) }
    }

    fun queryCdr(): Map<String, Any?> =
        runCatching { postJson("/api/get_cdr", mapOf("port" to (0..7).toList())) }
            .getOrElse { mapOf("error" to networkError(it)) }

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
        val request = Request.Builder().url(builder.build()).get().build()
        return execute(request)
    }

    private fun postJson(path: String, value: Any): Map<String, Any?> {
        val body = mapper.writeValueAsBytes(value).toRequestBody(JSON)
        val request = Request.Builder().url(baseUrl(activeHost).newBuilder().addPathSegments(path.removePrefix("/")).build()).post(body).build()
        return execute(request)
    }

    private fun execute(unsigned: Request): Map<String, Any?> {
        require(gatewayUsername.isNotBlank() && gatewayPassword.isNotBlank()) { "DINSTAR credentials must be configured" }
        val response = client.newCall(unsigned).execute()
        return readJson(response)
    }

    private fun readJson(response: okhttp3.Response): Map<String, Any?> = response.use {
        require(it.isSuccessful) { gatewayHttpError(it.code) }
        val body = requireNotNull(it.body) { "DINSTAR returned an empty HTTP body" }
        mapper.readValue(body.bytes(), Map::class.java) as Map<String, Any?>
    }

    private fun gatewayHttpError(code: Int): String = when (code) {
        401 -> "DINSTAR HTTP 401 - gateway rejected the credentials (check DINSTAR_USERNAME/DINSTAR_PASSWORD)"
        404 -> "DINSTAR HTTP 404 - API endpoint not found (enable the new-version API on the gateway, or check the model)"
        408 -> "DINSTAR HTTP 408 - gateway request timeout"
        else -> "DINSTAR HTTP $code"
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

    private fun networkError(error: Throwable): String = when (error) {
        is java.net.ConnectException -> "DINSTAR_UNREACHABLE"
        is java.net.SocketTimeoutException -> "DINSTAR_TIMEOUT"
        else -> error.message ?: error.javaClass.simpleName
    }

    companion object { private val JSON = "application/json; charset=utf-8".toMediaType() }
}