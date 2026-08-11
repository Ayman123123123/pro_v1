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
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * مصنع اتصالات بوابات DINSTAR.
 *
 * كانت `DinstarHardwareService` تبني `OkHttpClient` واحدًا مثبّتًا على
 * عنوان واحد من الإعدادات، فاستحال مخاطبة جهاز ثانٍ. هنا يُفصل بناء
 * الاتصال عن منطق العمل ليصبح لكل بوابة في الأسطول عميلها.
 *
 * ## المصادقة
 * تستخدم واجهة UC2000 مصادقة **HTTP Digest** (الإصدارات الأقدم Basic).
 * يُسجَّل المُصادِقان معًا ويختار `DispatchingAuthenticator` بينهما حسب
 * ترويسة `WWW-Authenticate`. النتائج تُخزَّن مؤقتًا لتفادي جولة تحدٍّ
 * إضافية مع كل طلب.
 *
 * ## شهادة TLS
 * تُصدِر البوابة شهادة موقّعة ذاتيًا باسم لا يطابق عنوان IP. القبول
 * مشروط بأمرين: أن يكون العنوان خاصًا (RFC 1918)، وأن يكون ذلك على
 * شبكة إدارة معزولة. لذلك يرفض المصنع أي عنوان عام رفضًا صريحًا بدل
 * أن يفتح ثقة عمياء على الإنترنت.
 */
@Component
class DinstarConnectionFactory(
    @Value("\${red.dinstar.username:admin}") private val username: String,
    @Value("\${red.dinstar.password:admin}") private val password: String,
    @Value("\${red.dinstar.connect-timeout-seconds:5}") private val connectTimeout: Long,
    @Value("\${red.dinstar.read-timeout-seconds:10}") private val readTimeout: Long,
    @Value("\${red.dinstar.probe-timeout-seconds:2}") private val probeTimeout: Long,
    private val mapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarConnectionFactory::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val clients = ConcurrentHashMap<String, DinstarClient>()

    fun clientFor(host: String, apiPort: Int, scheme: String): DinstarClient =
        clients.computeIfAbsent("$scheme://$host:$apiPort") {
            DinstarClient(host, apiPort, scheme, buildHttpClient(probe = false), mapper)
        }

    fun probeClientFor(host: String, apiPort: Int, scheme: String): DinstarClient =
        DinstarClient(host, apiPort, scheme, buildHttpClient(probe = true), mapper)

    /** يُستدعى عند حذف بوابة حتى لا يتسرب عميل معلّق. */
    fun evict(host: String, apiPort: Int, scheme: String) {
        clients.remove("$scheme://$host:$apiPort")
    }

    private fun buildHttpClient(probe: Boolean): OkHttpClient {
        val credentials = Credentials(username, password)
        val dispatching = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(credentials))
            .with("basic", BasicAuthenticator(credentials))
            .build()
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ssl = SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }

        // الفحص يستخدم مهلة أقصر: عنوان بلا جهاز يجب أن يسقط بسرعة
        // وإلا استغرق مسح ‎/24 دقائق.
        val timeout = if (probe) probeTimeout else connectTimeout
        return OkHttpClient.Builder()
            .authenticator(CachingAuthenticatorDecorator(dispatching, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .sslSocketFactory(ssl.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(if (probe) probeTimeout else readTimeout, TimeUnit.SECONDS)
            .callTimeout(if (probe) probeTimeout * 2 else readTimeout + connectTimeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(!probe)
            .build()
    }

    /**
     * عميل موجّه إلى بوابة واحدة. كل الاستدعاءات هنا موثّقة في
     * «UC2000 HTTP API»؛ أي عملية غير موثّقة تُرفض في الطبقة الأعلى
     * بدل اختراع مسار.
     */
    class DinstarClient(
        val host: String,
        val apiPort: Int,
        val scheme: String,
        private val http: OkHttpClient,
        private val mapper: ObjectMapper
    ) {
        private val base = run {
            require(scheme in setOf("http", "https")) { "DINSTAR scheme must be http or https" }
            require(isPrivate(host)) { "DINSTAR must be reached on a private management address" }
            "$scheme://$host:$apiPort".toHttpUrl()
        }

        val endpointLabel: String get() = "$scheme://$host:$apiPort"

        fun getJson(path: String, query: Map<String, String> = emptyMap()): Map<String, Any?> {
            val url = base.newBuilder().addPathSegments(path.removePrefix("/"))
            query.forEach(url::addQueryParameter)
            return execute(Request.Builder().url(url.build()).get().build())
        }

        fun postJson(path: String, value: Any): Map<String, Any?> {
            val body = mapper.writeValueAsBytes(value).toRequestBody(JSON)
            val url = base.newBuilder().addPathSegments(path.removePrefix("/")).build()
            return execute(Request.Builder().url(url).post(body).build())
        }

        /**
         * قراءة حالة المنافذ. `port` تُمرَّر كقائمة مفصولة بفواصل، وعدد
         * المنافذ يُشتق من الطراز بدل تثبيته على 8.
         */
        @Suppress("UNCHECKED_CAST")
        fun getPortInfo(portCount: Int = 8): List<Map<String, Any?>> =
            queryPorts(portCount).ports

        /**
         * استعلام المنافذ مع الاحتفاظ بالرقم التسلسلي.
         *
         * توثيق `get_port_info` (§10.3) ينص على أن **كل استجابة تحمل
         * حقل `sn`** = الرقم التسلسلي للبوابة. كان يُهمَل ويُقرأ التسلسلي
         * من `get_status` وحده، وهو أمر لا تدعمه الإصدارات الأقدم من
         * 1102 — فتفقد تلك الأجهزة هويتها الثابتة وتُعرَّف بعنوانها
         * الشبكي الذي يتبدّل مع DHCP.
         */
        fun queryPorts(portCount: Int = 8): PortQuery {
            val response = getJson(
                "/api/get_port_info",
                mapOf(
                    "port" to (0 until portCount).joinToString(","),
                    "info_type" to "type,imei,imsi,iccid,number,reg,slot,callstate,signal,gprs"
                )
            )
            require(isSuccess(response)) { "DINSTAR get_port_info failed on $endpointLabel" }
            @Suppress("UNCHECKED_CAST")
            val ports = response["info"] as? List<Map<String, Any?>> ?: emptyList()
            return PortQuery(
                ports = ports,
                serialNumber = response["sn"]?.toString()?.takeIf { it.isNotBlank() }
            )
        }

        fun getDeviceStatus(): Map<String, Any?> = postJson("/api/get_status", mapOf("maximum" to 10))

        /** نتيجة استعلام المنافذ مع هوية الجهاز المرافقة. */
        data class PortQuery(
            val ports: List<Map<String, Any?>>,
            val serialNumber: String?
        )

        private fun execute(request: Request): Map<String, Any?> {
            val withAccept = request.newBuilder().header("Accept", "application/json").build()
            return http.newCall(withAccept).execute().use { response ->
                if (!response.isSuccessful) {
                    val challenge = response.challenges().joinToString(", ") { "${it.scheme} realm=${it.realm}" }
                    log.warn("DINSTAR HTTP {} on {}{} — challenge: {}",
                        response.code, endpointLabel, request.url.encodedPath, challenge)
                    throw IllegalStateException(
                        "DINSTAR HTTP ${response.code} on ${request.url.encodedPath} ($endpointLabel)"
                    )
                }
                val body = requireNotNull(response.body) { "DINSTAR returned an empty HTTP body" }
                @Suppress("UNCHECKED_CAST")
                mapper.readValue(body.bytes(), Map::class.java) as Map<String, Any?>
            }
        }

        companion object {
            /** واجهة UC2000 تُشير إلى النجاح بـ `error_code = 200`. */
            fun isSuccess(response: Map<String, Any?>): Boolean =
                (response["error_code"] as? Number)?.toInt() == 200

            private fun isPrivate(host: String): Boolean = runCatching {
                val a = InetAddress.getByName(host)
                a.isSiteLocalAddress || a.isLoopbackAddress
            }.getOrDefault(false)
        }
    }
}
