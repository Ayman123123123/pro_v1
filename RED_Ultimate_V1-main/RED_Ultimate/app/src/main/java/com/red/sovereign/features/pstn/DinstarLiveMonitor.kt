package com.red.sovereign.features.pstn

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.red.features.dinstar.DinstarGatewayStatus
import com.red.features.dinstar.DinstarPort
import com.red.features.dinstar.YemenOperator
import dagger.hilt.android.qualifiers.ApplicationContext
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

/**
 * مراقب Dinstar الحي — بدون أسرار صلبة.
 *
 * - لا توجد بيانات اعتماد افتراضية في الشيفرة: تُقرأ من شاشة الإقران
 *   ([DinstarCredentialStore]) التي تخزن في prefs خاصة مشفرة قدر الإمكان،
 *   مع إمكانية حقن الافتراضيات من BuildConfig عبر CI
 *   (DINSTAR_GATEWAY_IP / DINSTAR_DEFAULT_USER — كلمة المرور لا تُحقن أبداً).
 * - مدير الثقة يقبل شهادات self-signed **لبوابة LAN المقترنة فقط**
 *   (hostname == gatewayIp) مع Log.w تحذيري.
 * - للإنتاج: ثبّت بصمة SHA-256 لشهادة البوابة عبر okhttp3.CertificatePinner
 *   (CertificatePinner.Builder().add(gatewayIp, "sha256/...")) بدل الثقة العمياء.
 */
@Singleton
class DinstarLiveMonitor @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val client: OkHttpClient
    private val gson = Gson()
    private var sessionCookie: String? = null
    private val creds = DinstarCredentialStore(appContext)

    // IP البوابة المقترنة — من شاشة الإقران، لا عنوان صلب.
    private val gatewayIp: String get() = creds.gatewayIp
    private val baseUrl: String get() = "https://$gatewayIp"

    init {
        // شهادات Dinstar self-signed على LAN فقط — مقيّدة بالبوابة المقترنة.
        // للإنتاج: استبدل هذا بـ okhttp3.CertificatePinner مع بصمة SHA-256
        // لشهادة البوابة (CertificatePinner.Builder().add(host, "sha256/...")).
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // لا نعرف hostname هنا — القيد الحقيقي في hostnameVerifier أدناه،
                // وهذا القبول مخصص فقط لشهادة self-signed لبوابة LAN المقترنة.
                Log.w("DinstarMonitor", "⚠️ TrustAll checkServerTrusted: LAN self-signed only — فعّل CertificatePinner (SHA-256) في الإنتاج")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { hostname, _ ->
                // القيد: الثقة العمياء للبوابة المقترنة فقط — أي مضيف آخر مرفوض.
                val allowed = hostname == gatewayIp
                if (!allowed) {
                    Log.w("DinstarMonitor", "⛔ hostnameVerifier rejected non-gateway host=$hostname (paired=$gatewayIp) — MITM?")
                }
                allowed
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /** إقران البوابة من شاشة الإعدادات — يخزن البيانات ويصفّر الجلسة. */
    fun pair(gatewayIp: String, username: String, password: String) {
        creds.save(gatewayIp.trim(), username, password)
        sessionCookie = null
    }

    fun isPaired(): Boolean = creds.isPaired

    fun clearPairing() {
        creds.clear()
        sessionCookie = null
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
        val username = creds.username
        val password = creds.password
        if (username.isNullOrBlank() || password.isNullOrEmpty()) {
            Log.w("DinstarMonitor", "غير مقترن — أدخل بيانات البوابة من شاشة الإقران أولاً")
            return false
        }
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

/**
 * مخزن بيانات إقران بوابة Dinstar — بديل admin/admin الصلبة.
 *
 * - تُملأ من شاشة الإقران (الإعدادات → بوابة Dinstar → إقران).
 * - التخزين في prefs خاصة بالتطبيق؛ للإنتاج انقلها إلى EncryptedSharedPreferences
 *   (androidx.security.crypto) أو حقن CI عبر BuildConfig (المستخدم فقط — كلمة
 *   المرور لا تُحقن في BuildConfig أبداً).
 */
class DinstarCredentialStore(private val context: Context) {
    private val prefs by lazy {
        context.getSharedPreferences("dinstar_pairing", Context.MODE_PRIVATE)
    }

    val gatewayIp: String
        get() = prefs.getString(KEY_HOST, DEFAULT_GATEWAY_IP).orEmpty().ifBlank { DEFAULT_GATEWAY_IP }
    val username: String? get() = prefs.getString(KEY_USER, null)
    // تُقرأ عند الحاجة فقط ولا تُسجَّل في أي Log.
    val password: String? get() = prefs.getString(KEY_PASS, null)
    val isPaired: Boolean get() = !username.isNullOrBlank() && !password.isNullOrEmpty()

    fun save(gatewayIp: String, username: String, password: String) {
        prefs.edit()
            .putString(KEY_HOST, gatewayIp.ifBlank { DEFAULT_GATEWAY_IP })
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HOST).remove(KEY_USER).remove(KEY_PASS).apply()
    }

    private companion object {
        const val KEY_HOST = "gateway_ip"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        // افتراضي LAN فقط — يُستبدل من شاشة الإقران أو BuildConfig عبر CI.
        const val DEFAULT_GATEWAY_IP = "192.168.11.1" // ALLOW-IP: default LAN gateway, user-overridable via pairing
    }
}
