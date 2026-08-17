package com.red.sovereign.features.pstn

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.red.features.dinstar.DinstarGatewayStatus
import com.red.features.dinstar.DinstarPort
import com.red.features.dinstar.YemenOperator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class DinstarLiveMonitor @Inject constructor() {
    private val client: OkHttpClient
    private val gson = Gson()
    private var sessionCookie: String? = null
    
    // Gateway IP (assuming sovereign network setup)
    private val gatewayIp = "192.168.11.1"
    private val baseUrl = "https://$gatewayIp"
    private val username = "admin"
    private val password = "admin" // Default or injected from secure storage

    init {
        // Bypass SSL for local self-signed Dinstar certificates
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getLiveStatus(): DinstarGatewayStatus? = withContext(Dispatchers.IO) {
        try {
            if (sessionCookie == null) {
                if (!login()) return@withContext null
            }
            fetchPortInfo()
        } catch (e: Exception) {
            Log.e("DinstarMonitor", "Error fetching Dinstar status: ${e.message}")
            // Session might be expired, reset cookie
            sessionCookie = null
            null
        }
    }

    private fun login(): Boolean {
        try {
            val jsonBody = """
                {"username":"$username","password":"$password","language":"en"}
            """.trimIndent()
            
            val request = Request.Builder()
                .url("$baseUrl/goform/IADIdentityAuth")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val cookies = response.headers("Set-Cookie")
                    for (cookie in cookies) {
                        if (cookie.contains("iadsessionid")) {
                            sessionCookie = cookie.split(";")[0]
                            return true
                        }
                    }
                }
            }
            return false
        } catch (e: Exception) {
            Log.e("DinstarMonitor", "Login failed: ${e.message}")
            return false
        }
    }

    private fun fetchPortInfo(): DinstarGatewayStatus? {
        val cookie = sessionCookie ?: return null
        
        val request = Request.Builder()
            .url("$baseUrl/WebGetPortInfoAll")
            .header("Cookie", cookie)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                sessionCookie = null
                return null
            }
            
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            
            val portsArray = json.getAsJsonArray("portinfo")
            val parsedPorts = mutableListOf<DinstarPort>()
            
            for (i in 0 until portsArray.size()) {
                val p = portsArray.get(i).asJsonObject
                
                // Parse values based on Dinstar API documentation mapped in python script
                val portIndex = p.get("port").asInt
                val opName = if (p.has("operator")) p.get("operator").asString else "غير معروف"
                val callStateVal = p.get("callState").asInt
                val regStateVal = p.get("regState").asInt
                val signalRaw = p.get("signal").asInt
                
                val callState = when (callStateVal) {
                    1 -> "ACTIVE"
                    2 -> "RINGING"
                    else -> "IDLE"
                }
                
                val regState = when (regStateVal) {
                    1 -> "REGISTERED"
                    else -> "UNREGISTERED"
                }
                
                val signalPercent = (signalRaw * 100) / 31
                
                val operator = YemenOperator.fromApiOperatorName(opName)
                
                parsedPorts.add(
                    DinstarPort(
                        index = portIndex,
                        radioType = "GSM",
                        registrationState = regState,
                        callState = callState,
                        signalPercent = signalPercent.coerceIn(0, 100),
                        signalRaw = signalRaw,
                        operatorName = opName,
                        simType = operator,
                        isHealthy = (regState == "REGISTERED" && signalPercent > 20 && callState == "IDLE")
                    )
                )
            }
            
            return DinstarGatewayStatus(
                isOnline = true,
                gatewayIp = gatewayIp,
                ports = parsedPorts,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
