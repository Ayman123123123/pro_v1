package com.red.sovereign.data.dinstar

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * مدير API لـ DINSTAR
 * يتعامل مع جميع عمليات HTTP API الموثقة
 */
class DinstarApiManager(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .authenticator { _, response ->
            // دعم Digest Authentication
            val challenge = response.challenges().firstOrNull()
            if (challenge?.scheme == "Digest") {
                response.request.newBuilder()
                    .header("Authorization", createDigestAuth(challenge, response.request))
                    .build()
            } else {
                null
            }
        }
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * جلب حالة الجهاز (CPU, Memory, Flash)
     */
    fun getDeviceStatus(callback: (Result<Map<String, Any>>) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/get_status")
            .post("{}".toRequestBody(JSON))
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    callback(Result.success(parseDeviceStatus(response)))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * جلب سجل المكالمات CDR
     */
    fun getCdrRecords(
        port: Int? = null,
        timeAfter: String? = null,
        timeBefore: String? = null,
        callback: (Result<List<Map<String, Any>>>) -> Unit
    ) {
        val json = JSONObject()
        port?.let { json.put("port", it) }
        timeAfter?.let { json.put("time_after", it) }
        timeBefore?.let { json.put("time_before", it) }

        val request = Request.Builder()
            .url("$baseUrl/api/get_cdr")
            .post(json.toString().toRequestBody(JSON))
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    callback(Result.success(parseCdrRecords(response)))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * إرسال USSD
     */
    fun sendUssd(
        port: Int,
        code: String,
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        val json = JSONObject().apply {
            put("port", port)
            put("code", code)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/send_ussd")
            .post(json.toString().toRequestBody(JSON))
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    callback(Result.success(parseUssdResponse(response)))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * تشغيل/إيقاف منفذ
     */
    fun setPortPower(
        port: Int,
        powerOn: Boolean,
        callback: (Result<Boolean>) -> Unit
    ) {
        val json = JSONObject().apply {
            put("port", port)
            put("power", if (powerOn) "on" else "off")
        }

        val request = Request.Builder()
            .url("$baseUrl/api/set_port_info")
            .post(json.toString().toRequestBody(JSON))
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    val success = response.optString("error_code") == "200"
                    callback(Result.success(success))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * تعيين تحويل المكالمات
     */
    fun setCallForward(
        port: Int,
        enabled: Boolean,
        number: String? = null,
        condition: String? = null,
        callback: (Result<Boolean>) -> Unit
    ) {
        val json = JSONObject().apply {
            put("port", port)
            put("call_forward", if (enabled) "enable" else "disable")
            number?.let { put("forward_number", it) }
            condition?.let { put("forward_condition", it) }
        }

        val request = Request.Builder()
            .url("$baseUrl/api/set_port_info")
            .post(json.toString().toRequestBody(JSON))
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    val success = response.optString("error_code") == "200"
                    callback(Result.success(success))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * إرسال رسالة SMS
     */
    fun sendSms(
        port: Int,
        phoneNumber: String,
        message: String,
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        val json = JSONObject().apply {
            put("port", port)
            put("number", phoneNumber)
            put("text", message)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/send_sms")
            .post(json.toString().toRequestBody(JSON))
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    callback(Result.success(parseSmsResponse(response)))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * جلب الرسائل SMS الواردة
     */
    fun getIncomingSms(
        port: Int? = null,
        callback: (Result<List<Map<String, Any>>>) -> Unit
    ) {
        val url = if (port != null) {
            "$baseUrl/api/query_incoming_sms?port=$port"
        } else {
            "$baseUrl/api/query_incoming_sms"
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    callback(Result.success(parseSmsList(response)))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    /**
     * جلب معلومات المنافذ
     */
    fun getPortInfo(
        ports: List<Int>? = null,
        infoTypes: List<String>? = null,
        callback: (Result<List<Map<String, Any>>>) -> Unit
    ) {
        val params = mutableListOf<String>()
        ports?.let { params.add("ports=${it.joinToString(",")}") }
        infoTypes?.let { params.add("info_types=${it.joinToString(",")}") }

        val url = if (params.isNotEmpty()) {
            "$baseUrl/api/get_port_info?${params.joinToString("&")}"
        } else {
            "$baseUrl/api/get_port_info"
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        executeRequest(request) { result ->
            result.fold(
                onSuccess = { response ->
                    callback(Result.success(parsePortInfo(response)))
                },
                onFailure = { error ->
                    callback(Result.failure(error))
                }
            )
        }
    }

    // ===== دوال مساعدة =====

    private fun executeRequest(request: Request, callback: (Result<JSONObject>) -> Unit) {
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        callback(Result.failure(IOException("HTTP ${it.code}: ${it.message}")))
                        return
                    }

                    val body = it.body?.string()
                    if (body.isNullOrEmpty()) {
                        callback(Result.failure(IOException("Empty response body")))
                        return
                    }

                    try {
                        val json = JSONObject(body)
                        callback(Result.success(json))
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    private fun createDigestAuth(challenge: Challenge, request: Request): String {
        // تنفيذ بسيط لـ Digest Authentication
        // في الإنتاج يجب استخدام مكتبة كاملة مثل okhttp-digest
        val credentials = okhttp3.Credentials.basic(username, password)
        return credentials
    }

    private fun parseDeviceStatus(json: JSONObject): Map<String, Any> {
        return mapOf(
            "cpu_used" to json.optString("cpu_used"),
            "memory_total" to json.optString("memory_total"),
            "memory_used" to json.optString("memory_used"),
            "memory_free" to json.optString("memory_free"),
            "flash_total" to json.optString("flash_total"),
            "flash_used" to json.optString("flash_used"),
            "flash_free" to json.optString("flash_free"),
            "temperature" to json.optString("temperature"),
            "uptime" to json.optString("uptime")
        )
    }

    private fun parseCdrRecords(json: JSONObject): List<Map<String, Any>> {
        val records = mutableListOf<Map<String, Any>>()
        val cdrArray = json.optJSONArray("cdr") ?: return records

        for (i in 0 until cdrArray.length()) {
            val cdr = cdrArray.getJSONObject(i)
            records.add(mapOf(
                "port" to cdr.optInt("port"),
                "start_time" to cdr.optString("start_time"),
                "answer_time" to cdr.optString("answer_time"),
                "end_time" to cdr.optString("end_time"),
                "duration" to cdr.optInt("duration"),
                "caller_number" to cdr.optString("caller_number"),
                "callee_number" to cdr.optString("callee_number"),
                "direction" to cdr.optString("direction"),
                "call_type" to cdr.optString("call_type"),
                "codec" to cdr.optString("codec"),
                "hangup_cause" to cdr.optString("hangup_cause"),
                "sip_call_id" to cdr.optString("sip_call_id"),
                "asterisk_channel" to cdr.optString("asterisk_channel")
            ))
        }

        return records
    }

    private fun parseUssdResponse(json: JSONObject): Map<String, Any> {
        return mapOf(
            "port" to json.optInt("port"),
            "response_text" to json.optString("response_text"),
            "status" to json.optString("status")
        )
    }

    private fun parseSmsResponse(json: JSONObject): Map<String, Any> {
        return mapOf(
            "port" to json.optInt("port"),
            "message_id" to json.optString("message_id"),
            "status" to json.optString("status")
        )
    }

    private fun parseSmsList(json: JSONObject): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        val smsArray = json.optJSONArray("sms") ?: return messages

        for (i in 0 until smsArray.length()) {
            val sms = smsArray.getJSONObject(i)
            messages.add(mapOf(
                "port" to sms.optInt("port"),
                "sender" to sms.optString("sender"),
                "text" to sms.optString("text"),
                "timestamp" to sms.optString("timestamp")
            ))
        }

        return messages
    }

    private fun parsePortInfo(json: JSONObject): List<Map<String, Any>> {
        val ports = mutableListOf<Map<String, Any>>()
        val infoArray = json.optJSONArray("info") ?: return ports

        for (i in 0 until infoArray.length()) {
            val port = infoArray.getJSONObject(i)
            ports.add(mapOf(
                "port" to port.optInt("port"),
                "status" to port.optString("status"),
                "signal" to port.optInt("signal"),
                "operator" to port.optString("operator"),
                "imsi" to port.optString("imsi"),
                "imei" to port.optString("imei"),
                "iccid" to port.optString("iccid"),
                "number" to port.optString("number")
            ))
        }

        return ports
    }
}
