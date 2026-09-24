package com.red.server.services

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.CertificatePinner
import okhttp3.FormBody
import okhttp3.HttpUrl
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.pstn.YemenNumberPlan
import com.red.server.services.DinstarFleetService
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

/** ظ…ط­ظˆظ‘ظ„ UC2000-VE (â€‘4/8G ظˆâ€‘4/8T). ظ„ط§ ظٹظƒط´ظپ ط¥ظ„ط§ ط¹ظ…ظ„ظٹط§طھ HTTP API ط§ظ„ظ…ظˆط«ظ‚ط©. */
@Service
class DinstarHardwareService(
    @Value("\${red.dinstar.ip}") private val configuredIp: String,
    @Value("\${red.dinstar.port:80}") private val configuredPort: Int,
    @Value("\${red.dinstar.scheme:http}") private val configuredScheme: String,
    @Value("\${red.dinstar.username:admin}") private val gatewayUsername: String,
    @Value("\${red.dinstar.password:admin}") private val gatewayPassword: String,
    @Value("\${red.dinstar.cert-pins:}") private val certPinsConfig: String,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val connections: DinstarConnectionFactory,
    private val fleetService: DinstarFleetService
) {
    /**
     * ط£ظ†ظ…ط§ط· آ«طھط¹ظ„ظ‘ظ… ط§ظ„ط±ظ‚ظ…آ» ظƒظ…ط§ طھظڈط±ظ‚ظ‘ظ…ظ‡ط§ طµظپط­ط© `enHBPhoneNumberAdd.htm` ط¹ظ„ظ‰
     * ط§ظ„ط¬ظ‡ط§ط². DINSTAR طھظˆط«ظ‘ظ‚ ط§ظ„ط«ظ„ط§ط«ط© ط±ط³ظ…ظٹظ‹ط§ ظپظٹ FAQ ط§ظ„ط®ط§طµ ط¨ظ€ UC2000.
     *
     * ظ…ط¹ط±ظژظ‘ظپ ط¹ظ„ظ‰ ظ…ط³طھظˆظ‰ ط§ظ„طµظ†ظپ ظ„ط§ ط¯ط§ط®ظ„ `companion object` ظƒظٹ ظٹظڈط´ط§ط± ط¥ظ„ظٹظ‡ ظ…ظ†
     * ط§ظ„ظ…طھط­ظƒظ‘ظ…ط§طھ ط¨ظ€ `DinstarHardwareService.NumberLearningMethod`.
     */
    enum class NumberLearningMethod(val wire: String) {
        SMS("0"), USSD("1"), CALL("2")
    }

    companion object {
        private val log = LoggerFactory.getLogger(DinstarHardwareService::class.java)
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * ط±ظ…ظˆط² ط§ظ„ظ‚ط¨ظˆظ„. 202 = آ«ظ‚ظڈط¨ظ„ ظˆط³ظٹظڈظ†ظپظژظ‘ط° ظ„ط§ط­ظ‚ظ‹ط§آ» طھط±ط¬ط¹ظ‡ ط§ظ„ط¹ظ…ظ„ظٹط§طھ
         * ط؛ظٹط± ط§ظ„ظ…طھط²ط§ظ…ظ†ط© (`send_sms`طŒ `send_ussd`).
         */
        private val ACCEPTED_CODES = setOf(200, 202)

        /** ط§ظ„ط­ط¯ ط§ظ„ظ…ظˆط«ظ‚ ظ„ظ„ظ…ط³طھظ„ظ…ظٹظ† ظپظٹ ط·ظ„ط¨ `send_sms` ط§ظ„ظˆط§ط­ط¯. */
        const val MAX_SMS_RECIPIENTS = 128

        /** ط§ظ„ط­ط¯ ط§ظ„ظ…ظˆط«ظ‚ ظ„ط­ط¬ظ… ظ†طµ ط§ظ„ط±ط³ط§ظ„ط©. */
        const val MAX_SMS_TEXT_BYTES = 1500

        /** ط§ط·ظ„ط¨ ط§ط´طھظ‚ط§ظ‚ ط§ظ„طھط±ظ…ظٹط² ظ…ظ† ظ…ط­طھظˆظ‰ ط§ظ„ط±ط³ط§ظ„ط© ط¨ط¯ظ„ ظپط±ط¶ظ‡. */
        const val AUTO_ENCODING = "AUTO"

        /** ط§ظ„ط±ظ…ط² ط§ظ„ظ‚طµظٹط± ظ„ط®ط¯ظ…ط© آ«ظ…ط¹ط±ظپط© ط±ظ‚ظ…ظٹآ» ظپظٹ ط³ط¨ط£ظپظˆظ†. */
        const val SABAFON_MMN_SHORTCODE = "333"

        /** ظ†طµ ط§ظ„ط·ظ„ط¨ ظ„ط®ط¯ظ…ط© آ«ظ…ط¹ط±ظپط© ط±ظ‚ظ…ظٹآ» ظپظٹ ط³ط¨ط£ظپظˆظ†. */
        const val SABAFON_MMN_KEYWORD = "MMN"

        /**
         * ط®ط±ظٹط·ط© آ«ظ…ط¹ط±ظپط© ط±ظ‚ظ…ظٹآ» ظ„ظƒظ„ ظ…ط´ط؛ظ„ ظٹظ…ظ†ظٹ â€” **ظ„ظٹط³طھ GSM ظپظ‚ط·**.
         * ظٹظ…ظ† ظ…ظˆط¨ط§ظٹظ„ CDMA2000/LTE (77/78) ظ„ظ‡ط§ ط±ظ…ظˆط²ظ‡ط§ ط§ظ„ط®ط§طµط©ط› ط¥ط±ط³ط§ظ„ 333/MMN
         * ظ…ظ† ط´ط±ظٹط­ط© ظٹظ…ظ† ظ…ظˆط¨ط§ظٹظ„ ظٹظڈط®طµظ… ظˆظ„ط§ ظٹطھط¹ظ„ظ‘ظ…. ط§ظ„ظ‚ظٹظ… ظ‡ظ†ط§ ط§ظپطھط±ط§ط¶ظٹط§طھ ظ…ظˆط«ظ‚ط©
         * طھظڈط¶ط¨ط· ظ…ظ† ط§ظ„ظ„ظˆط­ط© ظ„ظƒظ„ ظ†ط´ط±ط› ط§ظ„ظپط§ط±ط؛ ظٹط¹ظ†ظٹ آ«ظ„ط§ طھط¹ظ„ظ‘ظ… طھظ„ظ‚ط§ط¦ظٹآ».
         */
        data class NumberLearningRule(val destination: String, val text: String)

        val OPERATOR_LEARNING_RULES: Map<String, NumberLearningRule> = mapOf(
            "Sabafon" to NumberLearningRule("333", "MMN"),
            // ظٹظ…ظ† ظ…ظˆط¨ط§ظٹظ„: ظٹظڈط¶ط¨ط· ظ…ظ† ط§ظ„ظ„ظˆط­ط© ط­ط³ط¨ ط§ظ„ظ†ط´ط±ط© ط§ظ„ط­ط§ظ„ظٹط© (USSD/SMS) â€” ظ„ط§ طھط±ط³ظ„ 333
            "YemenMobile" to NumberLearningRule("", ""),
            "YOU" to NumberLearningRule("", ""),
            "YTelecom" to NumberLearningRule("", "")
        )

        /** ظ‚ط§ط¹ط¯ط© ط§ظ„طھط¹ظ„ظ‘ظ… ظ„ظ…ط´ط؛ظ„ â€” null طھط¹ظ†ظٹ ظ„ط§ ظٹظˆط¬ط¯ ط·ط±ظٹظ‚ ظ…ظˆط«ظ‘ظ‚. */
        fun learningRuleFor(operator: String?): NumberLearningRule? =
            OPERATOR_LEARNING_RULES[operator]?.takeIf { it.destination.isNotBlank() && it.text.isNotBlank() }

        /** ط±ظ‚ظ… ظ‡ط§طھظپ ط£ظˆ ط±ظ…ط² ظ‚طµظٹط± â€” ظٹط·ط§ط¨ظ‚ `ReTestNumber` ظپظٹ طµظپط­ط© ط§ظ„ط¬ظ‡ط§ط². */
        private val NUMBER_OR_SHORTCODE = Regex("^\\+?[0-9*#]{2,23}$")

        /**
         * ط§ط®طھظٹط§ط± ط§ظ„طھط±ظ…ظٹط² ظ…ظ† ظ…ط­طھظˆظ‰ ط§ظ„ط±ط³ط§ظ„ط©.
         *
         * ط§ظ„ظ‚ط§ط¹ط¯ط©: ط¥ظ† ظƒط§ظ† ظƒظ„ ط­ط±ظپ ظ…ظˆط¬ظˆط¯ظ‹ط§ ظپظٹ ط£ط¨ط¬ط¯ظٹط© GSM 03.38 ظپط§ظ„ظ†طµ ظٹظڈط±ط³ظژظ„
         * `gsm-7bit` (160 ط­ط±ظپظ‹ط§ ظ„ظ„ط¬ط²ط، ط§ظ„ظˆط§ط­ط¯)ط› ظˆط¥ظ„ط§ `unicode` (70 ط­ط±ظپظ‹ط§).
         *
         * ظ„ظ…ط§ط°ط§ ظ‡ط°ط§ ظ…ظ‡ظ… ظپظٹ ط§ظ„ظٹظ…ظ† طھط­ط¯ظٹط¯ظ‹ط§: ط§ظ„ط±ط³ط§ط¦ظ„ ظ‡ظ†ط§ ط¹ط±ط¨ظٹط© ظپظٹ ط§ظ„ط؛ط§ظ„ط¨طŒ
         * ظˆط§ظ„ط¹ط±ط¨ظٹط© **ظ„ظٹط³طھ** ظپظٹ ط£ط¨ط¬ط¯ظٹط© GSM ط£طµظ„ظ‹ط§. ط§ظ„ط§ظپطھط±ط§ط¶ظٹ ط§ظ„ط³ط§ط¨ظ‚ ظƒط§ظ†
         * `GSM7BIT` ظ„ظƒظ„ ط±ط³ط§ظ„ط©طŒ ظپظƒط§ظ†طھ ظƒظ„ ط±ط³ط§ظ„ط© ط¹ط±ط¨ظٹط© طھطµظ„ آ«?????آ».
         *
         * ظˆظٹظڈظ‚طµط¯ ط¨ط§ظ„ط§ط´طھظ‚ط§ظ‚ ظ„ط§ ط§ظ„ظپط±ط¶: ظ„ظˆ ط«ط¨ظ‘طھظ†ط§ `UCS2` ط¯ط§ط¦ظ…ظ‹ط§ ظ„ط­ظ„ظژظ‘طھ ظ…ط´ظƒظ„ط©
         * ط§ظ„ط¹ط±ط¨ظٹط© ظˆط®ظڈظ„ظ‚طھ ط£ط®ط±ظ‰ â€” ط±ط³ط§ط¦ظ„ ط§ظ„طھط­ظ‚ظ‚ ظˆط±ظ…ظˆط² OTP ط¨ط§ظ„ط¥ظ†ط¬ظ„ظٹط²ظٹط© طھظپظ‚ط¯
         * ط£ظƒط«ط± ظ…ظ† ظ†طµظپ ط³ط¹طھظ‡ط§ ظپطھظ†ظ‚ط³ظ… ط£ط¬ط²ط§ط،ظ‹ ظˆطھطھط¶ط§ط¹ظپ ظƒظ„ظپطھظ‡ط§ ط¹ظ„ظ‰ ظƒظ„ ظ…ط³طھط®ط¯ظ….
         */
        fun detectEncoding(text: String): String = GsmAlphabet.detectEncoding(text)

        /**
         * ط£ط¨ط¬ط¯ظٹط© GSM 03.38 ط§ظ„ط£ط³ط§ط³ظٹط© + ط¬ط¯ظˆظ„ ط§ظ„ظ‡ط±ظˆط¨ (Basic + Extension).
         *
         * ظƒظ„ ط­ط±ظپ ظ‡ظ†ط§ ظٹظڈظ…ط«ظژظ‘ظ„ ظپظٹ 7 ط¨طھط§طھ (ط£ظˆ 14 ظ„ط­ط±ظˆظپ ط§ظ„ظ‡ط±ظˆط¨)طŒ ظپظٹطھط³ط¹ ط§ظ„ط¬ط²ط،
         * ط§ظ„ظˆط§ط­ط¯ 160 ط­ط±ظپظ‹ط§. ط£ظٹ ط­ط±ظپ ط®ط§ط±ط¬ظ‡ط§ â€” ظˆط§ظ„ط¹ط±ط¨ظٹط© ظƒظ„ظ‡ط§ ط®ط§ط±ط¬ظ‡ط§ â€” ظٹظپط±ط¶
         * ط§ظ„طھط±ظ…ظٹط² UCS2 ط¨ط³ط¹ط© 70 ط­ط±ظپظ‹ط§.
         *
         * ط§ظ„ظ…طµط¯ط±: 3GPP TS 23.038 آ§6.2.1. ظ…ظڈط¯ط±ظژط¬ط© طµط±ط§ط­ط©ظ‹ ظ„ط£ظ† ط§ظ„ط§ط¹طھظ…ط§ط¯ ط¹ظ„ظ‰
         * ظپط­طµظچ طھظ‚ط±ظٹط¨ظٹ ظ…ط«ظ„ `isLetterOrDigit()` ط£ظˆ ظ…ط¯ظ‰ ASCII ظٹط®ط·ط¦ ظپظٹ
         * ط§ظ„ط§طھط¬ط§ظ‡ظٹظ†: ظٹظ‚ط¨ظ„ `[` ظˆ`{` ظˆظ‡ظٹ ط­ط±ظˆظپ ظ‡ط±ظˆط¨ ظ…ط²ط¯ظˆط¬ط© ط§ظ„ط¹ط±ط¶طŒ ظˆظٹط±ظپط¶
         * `أ©` ظˆ`أک` ظˆظ‡ظٹ ط£ط³ط§ط³ظٹط© ظپظٹ ط§ظ„ط£ط¨ط¬ط¯ظٹط©.
         */
        /**
         * أبجدية GSM 03.38 — المصدر الوحيد هو [GsmAlphabet.FULL] (3GPP TS 23.038 §6.2.1).
         * كانت هنا نسخة مكررة تالفة الترميز (133 حرفًا بدل 137، وهروب ناقص).
         */
        internal val GSM_03_38_ALPHABET: Set<Char> get() = GsmAlphabet.FULL

        // الأثر المهمل للنسخة المكررة التالفة — يُبقى للسجل فقط، لا يُستخدم.
        // أي تعديل مستقبلي على الأبجدية يكون في GsmAlphabet حصرًا.
        @Deprecated("Broken-encoding duplicate — use GsmAlphabet.FULL")
        private val LEGACY_GSM_ALPHABET_BROKEN_COPY = buildSet {
            // ط§ظ„ط£ط³ط§ط³ظٹط©
            addAll("@آ£\$آ¥أ¨أ©أ¹أ¬أ²أ‡\nأکأ¸\rأ…أ¥خ”_خ¦خ“خ›خ©خ خ¨خ£خکخ‍أ†أ¦أںأ‰ !\"#آ¤%&'()*+,-./".toList())
            addAll("0123456789:;<=>?".toList())
            addAll("آ،ABCDEFGHIJKLMNOPQRSTUVWXYZأ„أ–أ‘أœآ§".toList())
            addAll("آ؟abcdefghijklmnopqrstuvwxyzأ¤أ¶أ±أ¼أ ".toList())
            // ط¬ط¯ظˆظ„ ط§ظ„ظ‡ط±ظˆط¨ â€” طھظڈط±ط³ظژظ„ ط¨ط¨ط§ظٹطھظٹظ† ظ„ظƒظ†ظ‡ط§ طھط¨ظ‚ظ‰ ط¶ظ…ظ† gsm-7bit
            addAll("\u000C^{}\\[~]|â‚¬".toList())
        }
    }

    /**
     * OkHttp client configured with:
     * 1. CookieJar for web UI session (form login â†’ devckie cookie)
     * 2. Trust-all SSL for Dinstar's self-signed certificate on private management network
     */
    private val client: OkHttpClient by lazy { buildOkHttpClient() }

    /** In-memory cookie store for the web UI session (devckie). */
    private val cookieStore = ConcurrentHashMap<HttpUrl, List<Cookie>>()

    private fun buildOkHttpClient(): OkHttpClient {
        // --- CookieJar for web UI authentication ---
        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                if (cookies.isNotEmpty()) cookieStore[url] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url] ?: emptyList()
            }
        }

        // --- SSL: trust all certificates (Dinstar uses self-signed certs on private LAN) ---
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        // ظˆط§ط¬ظ‡ط© UC2000 طھط·ط§ظ„ط¨ Digest (ط§ظ„ط¥طµط¯ط§ط±ط§طھ ط§ظ„ط£ظ‚ط¯ظ… Basic). ظƒط§ظ† ط§ظ„ط¹ظ…ظٹظ„
        // ظٹظڈط¨ظ†ظ‰ ط¨ظ„ط§ ط£ظٹ ظ…ظڈطµط§ط¯ظگظ‚ ظپظٹط±طھط¯ ظƒظ„ ط§ط³طھط¯ط¹ط§ط، (USSD/SMS/CDR) ط¨ظ€ 401
        // ط±ط؛ظ… طµط­ط© ط§ظ„ط§ط¹طھظ…ط§ط¯ â€” ظٹظڈط¶ط§ظپ ظ‡ظ†ط§ ظ†ظپط³ ظƒظˆظ…ط© ط§ظ„ظ…طµط§ط¯ظ‚ط© ط§ظ„ظ†ط§ط¬ط­ط©
        // ط§ظ„ظ…ط³طھط®ط¯ظ…ط© ظپظٹ DinstarConnectionFactory.
        val credentials = Credentials(gatewayUsername, gatewayPassword)
        val dispatching = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(credentials))
            .with("basic", BasicAuthenticator(credentials))
            .build()
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()

        // SPKI pinning ط§ط®طھظٹط§ط±ظٹ: ط­طھظ‰ ظ…ط¹ trust-all (ط´ظ‡ط§ط¯ط§طھ ط°ط§طھظٹط© ط§ظ„طھظˆظ‚ظٹط¹ ط¹ظ„ظ‰
        // LAN ط¥ط¯ط§ط±ظٹ)طŒ OkHttp ظٹطھط­ظ‚ظ‚ ظ…ظ† ط§ظ„ط¯ط¨ظˆط³ ط¨ط¹ط¯ ط¨ظ†ط§ط، ط§ظ„ط³ظ„ط³ظ„ط© â€” ط£ظٹ ط´ظ‡ط§ط¯ط©
        // ظ…ط²ظˆظ‘ط±ط© ظ…ظ† ظ…ظ‡ط§ط¬ظ… ظپظٹ ط§ظ„ط´ط¨ظƒط© طھظڈط±ظپط¶ ط±ط؛ظ… ظ‚ط¨ظˆظ„ TrustManager ظ„ظ‡ط§.
        // ط§ظ„طµظٹط؛ط©: sha256/xxx,sha256/yyy
        val builder = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .authenticator(CachingAuthenticatorDecorator(dispatching, authCache))
            .addInterceptor(AuthenticationCacheInterceptor(authCache))
            .followRedirects(false)
            .followSslRedirects(false)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }  // Dinstar cert won't match IP hostname

        certPinsConfig.split(',')
            .map { it.trim() }
            .filter { it.startsWith("sha256/") }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { pins ->
                val pinner = CertificatePinner.Builder()
                pins.forEach { pinner.add("*", it) }
                builder.certificatePinner(pinner.build())
                log.info("DINSTAR HTTP client: {} SPKI pin(s) active", pins.size)
            }

        // â”€â”€ ظ…ظ‡ظ„ط§طھ ظˆظ…ظڈط±ط³ظگظ„ ظˆط§ظ‚ط¹ظٹط§ظ† ظ„ظ„ط¨ط±ظ†ط§ظ…ط¬ ط§ظ„ط«ط§ط¨طھ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // ظ…طµط§ظپط­ط© TLS ط¹ظ„ظ‰ ظ‡ط°ط§ ط§ظ„ط¬ظ‡ط§ط² طھظڈظ†ظپظژظ‘ط° ط¨ط§ظ„طھط³ظ„ط³ظ„ ظˆطھط³طھط؛ط±ظ‚ 0.9s ظ…ظ†ظپط±ط¯ط©
        // ظˆ3.7s طھط­طھ ط³طھ ظ…طµط§ظپط­ط§طھ ظ…طھظˆط§ط²ظٹط© (ظ‚ظٹط§ط³ ط­ظٹظ‘ 2026-09-06). ظˆ5s طھط´ظ…ظ„
        // ط§ظ„ظ…طµط§ظپط­ط© ظپظٹ OkHttpطŒ ظپظƒط§ظ† ظƒظ„ ط§ط³طھط¯ط¹ط§ط، ظ…طھط²ط§ظ…ظ† ظٹط³ظ‚ط·
        // آ«Connect timed outآ» ط¨ظٹظ†ظ…ط§ ط§ظ„ط¬ظ‡ط§ط² ظٹط±ط¯ 200 ظ„ط·ظ„ط¨ ظ…ظ†ظپط±ط¯ ظپظٹ ط§ظ„ظ„ط­ط¸ط©
        // ظ†ظپط³ظ‡ط§. ط·ظ„ط¨ ظˆط§ط­ط¯ ظ„ظƒظ„ ظ…ط¶ظٹظپ + ظ…ط¬ظ…ظژظ‘ط¹ ط§طھطµط§ظ„ط§طھ ط¯ط§ط¦ظ… ظٹظڈظ„ط؛ظٹط§ظ† ط§ظ„ط·ط§ط¨ظˆط±.
        builder.dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 1
        })
        builder.connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))

        return builder
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // ظٹط¬ط¨ ط£ظ† ظٹطھظ‘ط³ط¹ ظ„ط§ظ†طھط¸ط§ط± ط§ظ„ط·ط§ط¨ظˆط± ظ…ط¹ maxRequestsPerHost=1.
            .callTimeout(70, TimeUnit.SECONDS)
            .build()
    }


    @Volatile private var activeHost = configuredIp

    /**
     * ط§ظ„ط·ط±ط§ط² ط§ظ„ظ…ظƒطھط´ظژظپ ظپط¹ظ„ظٹظ‹ط§. ظƒط§ظ† ط§ظ„ظ…ظ„ظپ ظٹط«ط¨ظ‘طھ "UC2000-VE-8G" ظپظٹ ظƒظ„
     * ظ…ظƒط§ظ†طŒ ظپط§ظ„ظ…طھط؛ظٹظ‘ط±ط§طھ ط§ظ„ط±ط¨ط§ط¹ظٹط© (â€‘4G/â€‘4T) طھظڈط³ط¬ظژظ‘ظ„ ط¨ط·ط±ط§ط² ط®ط§ط·ط¦ ظˆظٹظڈط³طھط¹ظ„ظژظ…
     * ط¹ظ† ط«ظ…ط§ظ†ظٹط© ظ…ظ†ط§ظپط° ط¹ظ„ظ‰ ط¬ظ‡ط§ط² ظٹظ…ظ„ظƒ ط£ط±ط¨ط¹ط©. ظٹظڈط­ط¯ظژظ‘ط« ط¹ظ†ط¯ ط£ظˆظ„ ط§ظƒطھط´ط§ظپ
     * ظ†ط§ط¬ط­ ظ…ظ† ط¹ط¯ط¯ ط§ظ„ظ…ظ†ط§ظپط° ط§ظ„طھظٹ ط±ط¯ظ‘طھ ظپط¹ظ„ظ‹ط§.
     */
    @Volatile private var detectedModel: DinstarModelProfile = DinstarModelProfile.UC2000_VE_8G

    /** ظ…ط¯ظ‰ ط§ظ„ظ…ظ†ط§ظپط° ط§ظ„طµط§ظ„ط­ ظ„ظ„ط·ط±ط§ط² ط§ظ„ظ…ظƒطھط´ظژظپ â€” ظ„ط§ 0..7 ظ…ط«ط¨ظ‘طھط©. */
    private val portRange: IntRange get() = detectedModel.portRange

    private fun requireValidPort(port: Int) =
        require(port in portRange) {
            "ظ…ظ†ظپط° ط®ط§ط±ط¬ ط§ظ„ظ…ط¯ظ‰: ${detectedModel.modelId} ظٹط¯ط¹ظ… ${portRange.first}-${portRange.last}"
        }
    private val gatewayId: UUID get() = UUID.nameUUIDFromBytes("DINSTAR:$activeHost:$configuredPort".toByteArray())

    fun discoverGateway(): Map<String, Any> {
        // ط§ظ„ط§طھطµط§ظ„ ظٹظ‚طھطµط± ط¹ظ„ظ‰ ط§ظ„ط¨ظˆط§ط¨ط© ط§ظ„ظ…ظ‡ظٹط£ط© طµط±ط§ط­ط©ظ‹ط› ظ„ط§ طھظˆط¬ط¯ ط¹ظˆط¯ط© طµط§ظ…طھط© ط¥ظ„ظ‰ ط¹ظ†ظˆط§ظ†
        // طھط§ط±ظٹط®ظٹ ظ„ط£ظ†ظ‡ط§ ظ‚ط¯ طھط³طھط¹ظ„ظ… ط¬ظ‡ط§ط²ظ‹ط§ ط¢ط®ط± ظˆطھظƒطھط¨ ط­ط§ظ„ط© ظ…ظ†ط§ظپط°ظ‡ ظپظٹ ط§ظ„ط³ط¬ظ„ ط§ظ„ط®ط·ط£.
        val candidates = linkedSetOf(configuredIp)
        for (host in candidates) {
            if (!isPrivateAddress(host)) continue
            val result = runCatching { discoverPorts(host) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: continue
            activeHost = host
            // ط§ظ„ط·ط±ط§ط² ظٹظڈط³طھظ†طھط¬ ظ…ظ† ظ‚ط¯ط±ط§طھ ط§ظ„ظ…ظ†ط§ظپط° ط§ظ„طھظٹ ط±ط¯ظ‘طھ: ظˆط¬ظˆط¯ ط±ط§ط¯ظٹظˆ LTE
            // ظٹط¹ظ†ظٹ â€‘TطŒ ظˆط¹ط¯ط¯ ط§ظ„ظ…ظ†ط§ظپط° ظٹظپطµظ„ ط§ظ„ط±ط¨ط§ط¹ظٹ ط¹ظ† ط§ظ„ط«ظ…ط§ظ†ظٹ.
            detectedModel = inferModel(result)
            registerGateway(result.size)
            return mapOf(
                "success" to true, "gatewayIp" to host, "model" to detectedModel.modelId,
                "status" to "ONLINE", "portsDetected" to result.size,
                "capabilities" to documentedCapabilities()
            )
        }
        return mapOf(
            // ط¨ظ„ط§ ط±ط¯ ظ„ط§ ظٹظڈط¹ط±ظپ ط§ظ„ط·ط±ط§ط² â€” ظ„ط§ ظ†ط¶ط¹ null ط¯ط§ط®ظ„ Map<String, Any>
            "success" to false, "gatewayIp" to configuredIp, "model" to "UNKNOWN",
            "status" to "OFFLINE", "message" to "No authenticated UC2000 get_port_info response"
        )
    }

    fun getHardwareStatus(): List<Map<String, Any?>> {
        val info = queryPortInfo(activeHost)
        registerGateway(info.size)
        // ظٹظڈط³طھط®ط¯ظ… ط§ظ„ظ…ط¹ط±ظپ ط§ظ„ظپط¹ظ„ظٹ ظ…ظ† ط§ظ„ط¬ط¯ظˆظ„: ط§ظ„ط£ط³ط·ظˆظ„ ظٹط®ط²ظ‘ظ† ط§ظ„ط¨ظˆط§ط¨ط© ط¨ظ…ط¹ط±ظپ
        // ظ…ط´طھظ‚ ظ…ظ† ط§ظ„ط±ظ‚ظ… ط§ظ„طھط³ظ„ط³ظ„ظٹطŒ ظپط§ظ„ظ…ط¹ط±ظپ ط§ظ„ظ…ط­ط³ظˆط¨ ظ…ظ† ط§ظ„ط¹ظ†ظˆط§ظ† ظ‡ظ†ط§ ظ‚ط¯ ظ„ط§
        // ظٹظˆط¬ط¯ ظپظٹ ط§ظ„ط¬ط¯ظˆظ„ ظˆظٹظƒط³ط± ظ‚ظٹط¯ ط§ظ„ظ…ظپطھط§ط­ ط§ظ„ط®ط§ط±ط¬ظٹ ط¹ظ†ط¯ ظƒطھط§ط¨ط© ط§ظ„ظ„ظ‚ط·ط§طھ.
        return info.mapNotNull(::normalizePort).also { persistPorts(it, resolveGatewayId()) }
    }

    /**
     * ط­ط§ظ„ط© ظ…ظ†ط§ظپط° ط¨ظˆط§ط¨ط© ط¨ط¹ظٹظ†ظ‡ط§ ظ…ظ† ط§ظ„ط£ط³ط·ظˆظ„.
     *
     * ط§ظ„ط¥طµط¯ط§ط± ط¨ظ„ط§ ظˆط³ظٹط· ظٹط®ط§ط·ط¨ ط§ظ„ط¹ظ†ظˆط§ظ† ط§ظ„ظ…ط¶ط¨ظˆط· ظپظٹ ط§ظ„ط¥ط¹ط¯ط§ط¯ط§طھ ظپظ‚ط·طŒ ظˆظ‡ظˆ ظ…ط§
     * ظƒط§ظ† ظٹظ…ظ†ط¹ طھط´ط؛ظٹظ„ ط£ظƒط«ط± ظ…ظ† ط¬ظ‡ط§ط². ظ‡ظ†ط§ ظٹظڈط¨ظ†ظ‰ ط§ظ„ط§طھطµط§ظ„ ظ…ظ† ط³ط¬ظ„ ط§ظ„ط¨ظˆط§ط¨ط©طŒ
     * ظˆظٹظڈظ‚ط±ط£ ط¹ط¯ط¯ ط§ظ„ظ…ظ†ط§ظپط° ظ…ظ† ط·ط±ط§ط²ظ‡ط§ ط¨ط¯ظ„ ط§ظپطھط±ط§ط¶ ط«ظ…ط§ظ†ظٹط©.
     */
    fun getHardwareStatus(gateway: DinstarFleetService.Gateway): List<Map<String, Any?>> {
        val client = connections.clientFor(gateway.host, gateway.apiPort, gateway.scheme)
        val info = client.getPortInfo(gateway.portCount)
        return info.mapNotNull(::normalizePort).also { persistPorts(it, gateway.id) }
    }

    /** ط¥ط¹ط§ط¯ط© طھط´ط؛ظٹظ„ ط§ظ„ظˆط­ط¯ط© (ط§ظ„ظ…ظ†ظپط°). */
    fun resetPort(port: Int): Map<String, Any> {
        requireValidPort(port)
        // set_port_info ظٹطھط·ظ„ط¨ ط§ظ„ط«ظ„ط§ط«ط© ظ…ط¹ظ‹ط§: port + action + param. ط¥ط±ط³ط§ظ„ظ‡ ط¨ظ„ط§
        // `param` ظƒط§ظ† ظٹط¬ط¹ظ„ ط§ظ„ط¨ط±ظ†ط§ظ…ط¬ ط§ظ„ط«ط§ط¨طھ ظٹط±ظپط¶ ط§ظ„ط·ظ„ط¨ طµط§ظ…طھظ‹ط§.
        val response = getJson(
            DinstarApiContract.Path.SET_PORT_INFO,
            mapOf(
                "port" to port.toString(),
                "action" to DinstarApiContract.PortAction.RESET,
                "param" to DinstarApiContract.PortAction.RESET_PARAM
            )
        )
        require(apiSuccess(response)) { "طھط¹ط°ظ‘ط±طھ ط¥ط¹ط§ط¯ط© طھط´ط؛ظٹظ„ ط§ظ„ظˆط­ط¯ط©: ${apiErrorMessage(response)}" }
        return mapOf("status" to "SUCCEEDED", "port" to port)
    }

    /**
     * ط¥ظ†ط´ط§ط، ظ‚ط§ط¹ط¯ط© آ«طھط¹ظ„ظ‘ظ… ط§ظ„ط±ظ‚ظ…آ» (Phone Number Learning) ط¹ط¨ط± ظˆط§ط¬ظ‡ط© ط§ظ„ظˆظٹط¨.
     *
     * ## ظ„ظ…ط§ط°ط§ ط§ظ„ظˆظٹط¨ ظ„ط§ ط§ظ„ظ€ API
     * ظ„ط§ ظٹظˆط¬ط¯ ظ…ط³ط§ط± ظ…ظˆط«ظ‘ظ‚ ظپظٹ UC2000 HTTP API ظ„ظ‡ط°ظ‡ ط§ظ„ظ…ظٹط²ط©ط› ط§ظ„ط·ط±ظٹظ‚ ط§ظ„ظˆط­ظٹط¯ ظ‡ظˆ
     * ط§ظ„ظ†ظ…ظˆط°ط¬ `/goform/HBPhoneNumberRuleAdd`.
     *
     * ## ط§ظ„ط­ظ‚ظˆظ„ â€” ظ…ظ‚ط±ظˆط،ط© ظ…ظ† `enHBPhoneNumberAdd.htm` ط¹ظ„ظ‰ ط§ظ„ط¬ظ‡ط§ط² ظ†ظپط³ظ‡
     * | ط§ظ„ط­ظ‚ظ„        | ط§ظ„ظ…ط¹ظ†ظ‰                                            |
     * |--------------|---------------------------------------------------|
     * | `Index`      | ظپظ‡ط±ط³ ط§ظ„ظ‚ط§ط¹ط¯ط© 0..7 (ظ„ط§ ظپظ‡ط±ط³ ط§ظ„ظ…ظ†ظپط°)                |
     * | `Method`     | 0=SMSطŒ 1=USSDطŒ 2=Call                             |
     * | `Encoding`   | 0=UCS2طŒ 1=GSM 7bit â€” ظ„ظ€ SMS ظپظ‚ط·                   |
     * | `Dest`       | ط±ظ‚ظ…/ط±ظ…ط² ط§ظ„ظˆط¬ظ‡ط© â€” ظ…ط·ظ„ظˆط¨ ظ„ظ€ SMS ظˆCallطŒ ظ…ظڈط®ظپظ‰ ظ„ظ€ USSD |
     * | `Text`       | ط§ظ„ظ†طµ ط§ظ„ظ…ظڈط±ط³ظژظ„ â€” ظ…ط·ظ„ظˆط¨ ظ„ط؛ظٹط± Call                    |
     * | `Src`        | ط±ظ‚ظ… ط§ظ„ظ…ظڈط±ط³ظگظ„ ط§ظ„ظ…طھظˆظ‚ظژظ‘ط¹ ظ„ظ„ط±ط¯ (ظپظ„طھط±ط©)                 |
     * | `Key`        | ط§ظ„ظƒظ„ظ…ط§طھ ط§ظ„ظ…ظپطھط§ط­ظٹط© ظ„ط§ط³طھط®ط±ط§ط¬ ط§ظ„ط±ظ‚ظ… ظ…ظ† ط§ظ„ط±ط¯          |
     * | `IsWRSim`    | 1 = ط§ظƒطھط¨ ط§ظ„ط±ظ‚ظ… ظپظٹ ط§ظ„ط´ط±ظٹط­ط©                          |
     * | `RmFromLeft` | ط­ط°ظپ ط®ط§ظ†ط§طھ ظ…ظ† ط§ظ„ظٹط³ط§ط±                                |
     * | `AddPrefix`  | ط¨ط§ط¯ط¦ط© طھظڈط¶ط§ظپ                                        |
     * | `PortGroup`  | ظ…ط¬ظ…ظˆط¹ط© ط§ظ„ظ…ظ†ط§ظپط° (0 = ط§ظ„ط§ظپطھط±ط§ط¶ظٹط©)                    |
     *
     * ظƒط§ظ† ط§ظ„ط§ط³طھط¯ط¹ط§ط، ط§ظ„ط³ط§ط¨ظ‚ ظٹط±ط³ظ„ `Index/Method/IsWRSim/Ok` ظپظ‚ط· ط¨ظ†ظ…ط· CallطŒ
     * ظپطھظڈظ†ط´ط£ ظ‚ط§ط¹ط¯ط© ظ†ط§ظ‚طµط© ط¨ظ„ط§ ظˆط¬ظ‡ط© ظˆظ„ط§ ظƒظ„ظ…ط§طھ ظ…ظپطھط§ط­ظٹط© â€” ظ„ط§ طھط³طھط®ط±ط¬ ط±ظ‚ظ…ظ‹ط§.
     *
     * ## ط³ط¨ط£ظپظˆظ†
     * ط§ظ„ط·ط±ظٹظ‚ ط§ظ„ظ…ظˆط«ظژظ‘ظ‚: **SMS** ط¥ظ„ظ‰ `333` ط¨ط§ظ„ظ†طµ `MMN` ط«ظ… ظ…ط·ط§ط¨ظ‚ط© ط§ظ„ط±ط¯. ظ„ط°ظ„ظƒ
     * ط§ظ„ط§ظپطھط±ط§ط¶ظٹ ظ‡ظ†ط§ SMS ظ„ط§ Call.
     *
     * @param ruleIndex ظپظ‡ط±ط³ ط§ظ„ظ‚ط§ط¹ط¯ط© (0..7) â€” ط§ظ„ط§ظپطھط±ط§ط¶ظٹ ظ…ط·ط§ط¨ظ‚ ظ„ظ„ظ…ظ†ظپط°.
     * @return `true` ط¥ط°ط§ ظ‚ط¨ظگظ„ ط§ظ„ط¬ظ‡ط§ط² ط§ظ„ظ†ظ…ظˆط°ط¬ (200 ط£ظˆ 302).
     */
    fun triggerNumberLearning(
        port: Int,
        host: String? = null,
        method: NumberLearningMethod = NumberLearningMethod.SMS,
        destination: String = SABAFON_MMN_SHORTCODE,
        text: String = SABAFON_MMN_KEYWORD,
        expectedSender: String = "",
        keywords: String = "",
        writeToSim: Boolean = true,
        stripFromLeft: Int = 0,
        addPrefix: String = "",
        portGroup: Int = 0,
        ruleIndex: Int = port
    ): Boolean {
        requireValidPort(port)
        require(ruleIndex in portRange) { "ظپظ‡ط±ط³ ط§ظ„ظ‚ط§ط¹ط¯ط© ط®ط§ط±ط¬ ط§ظ„ظ…ط¯ظ‰: $ruleIndex" }
        // ط§ظ„طھط­ظ‚ظ‚ ظٹط·ط§ط¨ظ‚ `form_check` ظپظٹ طµظپط­ط© ط§ظ„ط¬ظ‡ط§ط²: Call ظˆط­ط¯ظ‡ ظٹط¹ظپظٹ ظ…ظ† ط§ظ„ظ†طµطŒ
        // ظˆUSSD ظˆط­ط¯ظ‡ ظٹط¹ظپظٹ ظ…ظ† ط§ظ„ظˆط¬ظ‡ط©.
        if (method != NumberLearningMethod.USSD) {
            require(destination.matches(NUMBER_OR_SHORTCODE)) { "ط±ظ‚ظ… ظˆط¬ظ‡ط© ط؛ظٹط± طµط§ظ„ط­: $destination" }
        }
        if (method != NumberLearningMethod.CALL) {
            require(text.isNotBlank()) { "ظ†طµ ط§ظ„ط¥ط±ط³ط§ظ„ ظ…ط·ظ„ظˆط¨ ظ„ظ†ظ…ط· ${method.name}" }
        }
        require(stripFromLeft in 0..31) { "ط¹ط¯ط¯ ط§ظ„ط®ط§ظ†ط§طھ ط§ظ„ظ…ط­ط°ظˆظپط© ظٹط¬ط¨ ط£ظ† ظٹظƒظˆظ† 0..31" }

        val target = host ?: activeHost
        require(isPrivateAddress(target)) { "ط¨ظˆط§ط¨ط© طھط¹ظ„ظ‘ظ… ط§ظ„ط£ط±ظ‚ط§ظ… ظٹط¬ط¨ ط£ظ† طھظƒظˆظ† ط¹ظ„ظ‰ ط¹ظ†ظˆط§ظ† ط®ط§طµ" }
        log.info(
            "Number Learning rule: gateway={} ruleIndex={} method={} dest={} writeToSim={}",
            target, ruleIndex, method.name, destination.ifBlank { "-" }, writeToSim
        )

        return runCatching {
            ensureWebSession(target)
            val url = "$configuredScheme://$target:$configuredPort/goform/HBPhoneNumberRuleAdd".toHttpUrl()
            val formBody = FormBody.Builder()
                .add("Index", ruleIndex.toString())
                .add("Method", method.wire)
                // ط§ظ„طھط±ظ…ظٹط² ظٹظڈظ‚ط±ط£ ظ„ظ€ SMS ظˆط­ط¯ظ‡طŒ ظ„ظƒظ† ط¥ط±ط³ط§ظ„ظ‡ ط¯ط§ط¦ظ…ظ‹ط§ ظ„ط§ ظٹط¶ط±ظ‘
                .add("Encoding", if (method == NumberLearningMethod.SMS) "1" else "0")
                .add("Dest", if (method == NumberLearningMethod.USSD) "" else destination)
                .add("Text", if (method == NumberLearningMethod.CALL) "" else text)
                .add("Src", expectedSender)
                .add("Key", keywords)
                .add("IsWRSim", if (writeToSim) "1" else "0")
                .add("RmFromLeft", stripFromLeft.toString())
                .add("AddPrefix", addPrefix)
                .add("PortGroup", portGroup.toString())
                .add("Ok", "Save")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            client.newCall(request).execute().use { response ->
                val accepted = response.isSuccessful || response.code == 302
                if (!accepted) {
                    log.warn("Number Learning rejected by {}: HTTP {}", target, response.code)
                }
                accepted
            }
        }.onFailure {
            log.warn("Number Learning failed on {} port {}: {}", target, port, it.message)
        }.getOrDefault(false)
    }

    fun sendUssd(port: Int, text: String): Map<String, Any?> {
        requireValidPort(port)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = postJson("/api/send_ussd", mapOf("port" to listOf(port), "command" to "send", "text" to text))
        require(apiSuccess(response)) { "طھط¹ط°ظ‘ط± ط¥ط±ط³ط§ظ„ USSD: ${apiErrorMessage(response)}" }
        return response
    }

    fun queryUssd(port: Int): Map<String, Any?> {
        requireValidPort(port)
        return getJson("/api/query_ussd_reply", mapOf("port" to port.toString()))
    }

    /**
     * CDR must be POSTed with a JSON body per the official Dinstar API documentation.
     * Body shape per [DinstarApiContract]: `{"port":[...], "time_after":..., "time_before":...}`.
     * `maximum` is **not** a documented field for `/api/get_cdr` and historically caused 403
     * on some firmware versions (see [DinstarApiContract.Cdr]).
     */
    fun queryCdr(): Map<String, Any?> = postJson(
        "/api/get_cdr",
        mapOf("port" to portRange.toList())
    )

    fun updateSipSettings(newSipIp: String): Nothing = unsupported(
        "Firmware-independent SIP configuration API is not documented for UC2000-VE; configure the SIP trunk in the gateway UI and Asterisk"
    )

    fun rebootDevice(): Nothing = unsupported(
        "A verified full-device reboot endpoint is not documented; use the gateway UI after active-call confirmation"
    )

    fun initiateCall(phoneNumber: String, slotIndex: Int = 0): Nothing = unsupported(
        "Voice calls must use Backend â†’ Asterisk AMI â†’ PJSIP â†’ DINSTAR, not an invented DINSTAR /api/dial endpoint"
    )

    fun capabilities() = documentedCapabilities()

    // â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
    // ًں“± SMS Operations â€” ط­ط³ط¨ ظˆط«ط§ط¦ظ‚ Dinstar API ط§ظ„ط±ط³ظ…ظٹط©
    // â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ

    /**
     * ط¥ط±ط³ط§ظ„ SMS â€” POST /api/send_sms
     *
     * ط§ظ„ظ…طµط¯ط±: آ«Dinstar GSM Gateway HTTP APIآ» آ§2 (ط§ظ„ط¥طµط¯ط§ط± 1.1طŒ 2019-10-16).
     *
     * @param text ظ…ط­طھظˆظ‰ ط§ظ„ط±ط³ط§ظ„ط©. ط§ظ„ط­ط¯ 1500 ط¨ط§ظٹطھ ظ„ظƒط§ظ…ظ„ ط§ظ„ط·ظ„ط¨.
     * @param params ظ‚ط§ط¦ظ…ط© ط§ظ„ظ…ط³طھظ„ظ…ظٹظ†: [{number: "777123456", user_id: 1}]
     * @param ports ظ…ظ†ط§ظپط° ظ…ط­ط¯ط¯ط© (ط§ط®طھظٹط§ط±ظٹطŒ null = طھط®طھط§ط± ط§ظ„ط¨ظˆط§ط¨ط©)
     * @param encoding `GSM7BIT` ط£ظˆ `UCS2` â€” طھظڈطھط±ط¬ظژظ… ط¥ظ„ظ‰ ظ‚ظٹظ… ط§ظ„ط¨ظˆط§ط¨ط©
     */
    /**
     * @param gatewayHost ط¨ظˆط§ط¨ط© ط§ظ„ط¥ط±ط³ط§ظ„. ط¹ظ†ط¯ طھط±ظƒظ‡ ظپط§ط±ط؛ظ‹ط§ طھظڈط³طھط¹ظ…ظ„ ط§ظ„ط¨ظˆط§ط¨ط©
     *   ط§ظ„ظ†ط´ط·ط© â€” ط³ظ„ظˆظƒ ط§ظ„ظ†ط´ط± ط°ظٹ ط§ظ„ط¬ظ‡ط§ط² ط§ظ„ظˆط§ط­ط¯. ظ…ط¹ ط£ط³ط·ظˆظ„ ظ…ظ† ط¹ط¯ط© ط¨ظˆط§ط¨ط§طھ
     *   ظƒط§ظ† ظƒظ„ SMS ظٹط®ط±ط¬ ظ…ظ† ط¬ظ‡ط§ط² ظˆط§ط­ط¯ ظ…ظ‡ظ…ط§ ط¨ظ„ط؛ ط¹ط¯ط¯ظ‡ط§طŒ ظپطھظڈظ‡ط¯ظژط± ط´ط±ط§ط¦ط­
     *   ط§ظ„ط¨ظ‚ظٹط© ظˆظٹظڈط­طھط³ط¨ ط§ظ„ط¥ط±ط³ط§ظ„ ظƒظ„ظ‘ظ‡ ط®ط§ط±ط¬ ط§ظ„ط´ط¨ظƒط© ط¹ظ„ظ‰ ظ…ط´ط؛ظ‘ظ„ ظˆط§ط­ط¯.
     *   ط§ظ„ط¹ظ†ظˆط§ظ† ظٹظڈطھط­ظ‚ظ‚ ظ…ظ† ظƒظˆظ†ظ‡ ط®ط§طµظ‹ط§ ظ‚ط¨ظ„ ط§ط³طھط¹ظ…ط§ظ„ظ‡: ظٹطµظ„ ظ…ظ† ط·ظ„ط¨ HTTPطŒ
     *   ظˆطھظ…ط±ظٹط±ظ‡ ط¨ظ„ط§ ظپط­طµ ظٹط¬ط¹ظ„ ط§ظ„ط®ط§ط¯ظ… ظٹط·ظ„ط¨ ط£ظٹ ط¹ظ†ظˆط§ظ† ظٹط®طھط§ط±ظ‡ ط§ظ„ظ…ط±ط³ظگظ„ (SSRF).
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
        // ط§ظ„ط­ط¯ ط§ظ„ظ…ظˆط«ظ‚ 128 ظ…ط³طھظ„ظ…ظ‹ط§ ظ„ط§ 32ط› ط§ظ„ط±ظ‚ظ… 32 ظٹط®طµ query_sms_result
        // ظپظ‚ط·. ط§ظ„ط­ط¯ ط§ظ„ط£ط¶ظٹظ‚ ظƒط§ظ† ظٹط±ظپط¶ ط¯ظپط¹ط§طھ ظ…ط´ط±ظˆط¹ط© ظ‚ط¨ظ„ ط£ظ† طھطµظ„ ظ„ظ„ط¨ظˆط§ط¨ط©.
        require(params.size <= MAX_SMS_RECIPIENTS) {
            "ط§ظ„ط­ط¯ ط§ظ„ط£ظ‚طµظ‰ $MAX_SMS_RECIPIENTS ظ…ط³طھظ„ظ…ظ‹ط§ ظپظٹ ط§ظ„ط·ظ„ط¨ ط§ظ„ظˆط§ط­ط¯"
        }
        // ط§ظ„ط­ط¯ 1500 ط¨ط§ظٹطھ ظ„ظ†طµ ط§ظ„ط·ظ„ط¨طŒ ظˆط§ظ„ط¹ط±ط¨ظٹط© ط¨ظ€ UTF-8 ط­طھظ‰ 3 ط¨ط§ظٹطھ ظ„ظ„ط­ط±ظپ
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_SMS_TEXT_BYTES) {
            "ظ†طµ ط§ظ„ط±ط³ط§ظ„ط© ظٹطھط¬ط§ظˆط² $MAX_SMS_TEXT_BYTES ط¨ط§ظٹطھ"
        }
        // ظٹظ‚ط¨ظ„ ط§ظ„طھط±ظ…ظٹط² ط¨طµظٹط؛طھظٹظ‡ ط§ظ„ط¯ط§ط®ظ„ظٹط© (GSM7BIT/UCS2) ظˆطµظٹط؛ط© ط§ظ„ط¨ظˆط§ط¨ط©
        // (gsm-7bit/unicode)طŒ ط¥ط¶ط§ظپط©ظ‹ ط¥ظ„ظ‰ AUTO ط§ظ„ط°ظٹ ظٹط´طھظ‚ ط§ظ„طھط±ظ…ظٹط² ظ…ظ† ط§ظ„ظ†طµ
        // ظ†ظپط³ظ‡ (ط¹ط±ط¨ظٹâ†’UCS2طŒ ASCIIâ†’GSM7BIT) ظ„طھظپط§ط¯ظٹ ظ‡ط¨ظˆط· ط§ظ„ط³ط¹ط© ط£ظˆ ظˆطµظˆظ„
        // ط¹ظ„ط§ظ…ط§طھ ط§ط³طھظپظ‡ط§ظ….
        val normalizedEncoding = when (encoding.uppercase()) {
            AUTO_ENCODING -> AUTO_ENCODING
            "GSM7BIT", "GSM-7BIT" -> "GSM7BIT"
            "UCS2", "UNICODE" -> "UCS2"
            else -> throw IllegalArgumentException("Encoding must be $AUTO_ENCODING, GSM7BIT, UCS2, gsm-7bit or unicode")
        }
        val effectiveEncoding = if (normalizedEncoding == AUTO_ENCODING) detectEncoding(text) else normalizedEncoding

        val body = mutableMapOf<String, Any>(
            "text" to text,
            "param" to params,
            // ط§ظ„ط¨ظˆط§ط¨ط© طھطھظˆظ‚ط¹ 'gsm-7bit' ط£ظˆ 'unicode'. ط¥ط±ط³ط§ظ„ "GSM7BIT"
            // ظ‚ظٹظ…ط© ط؛ظٹط± ظ…ط¹ط±ظˆظپط© ظپطھط±ط¬ط¹ ط§ظ„ط¨ظˆط§ط¨ط© ط¥ظ„ظ‰ ط§ظ„ط§ظپطھط±ط§ط¶ظٹ 'unicode':
            // ط±ط³ط§ظ„ط© ASCII طھظڈط±ط³ظژظ„ UCS2 ظپطھظ‡ط¨ط· ط³ط¹طھظ‡ط§ ظ…ظ† 160 ط­ط±ظپظ‹ط§ ط¥ظ„ظ‰ 70
            // ظˆطھطھط¶ط§ط¹ظپ ط£ط¬ط²ط§ط¤ظ‡ط§ ظˆطھظƒظ„ظپطھظ‡ط§.
            "encoding" to wireEncoding(effectiveEncoding),
            "request_status_report" to true
        )
        ports?.let { if (it.isNotEmpty()) body["port"] = it }

        val target = gatewayHost?.trim()?.takeIf { it.isNotEmpty() }
        require(target == null || isPrivateAddress(target)) {
            "ط¨ظˆط§ط¨ط© SMS ظٹط¬ط¨ ط£ظ† طھظƒظˆظ† ط¹ظ„ظ‰ ط¹ظ†ظˆط§ظ† ط®ط§طµ (RFC 1918)"
        }
        return postJson("/api/send_sms", body, target ?: activeHost)
    }

    /** طھط±ط¬ظ…ط© طھط±ظ…ظٹط²ظ†ط§ ط§ظ„ط¯ط§ط®ظ„ظٹ ط¥ظ„ظ‰ ط§ظ„ظ‚ظٹظ…ط© ط§ظ„طھظٹ طھظپظ‡ظ…ظ‡ط§ ط§ظ„ط¨ظˆط§ط¨ط©. */
    private fun wireEncoding(encoding: String): String =
        if (encoding == "GSM7BIT") "gsm-7bit" else "unicode"

    /** ط¬ظ„ط¨ ظ†طھط§ط¦ط¬ ط¥ط±ط³ط§ظ„ SMS â€” POST /api/query_sms_result */
    fun querySmsResult(userIds: List<Int> = emptyList(), numbers: List<String> = emptyList()): Map<String, Any?> {
        val body = mutableMapOf<String, Any>()
        if (userIds.isNotEmpty()) body["user_id"] = userIds
        if (numbers.isNotEmpty()) body["number"] = numbers
        return postJson("/api/query_sms_result", body)
    }

    /** ط¬ظ„ط¨ ط­ط§ظ„ط© طھط³ظ„ظٹظ… SMS â€” POST /api/query_sms_deliver_status */
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

    /**
     * ط¬ظ„ط¨ SMS ط§ظ„ظˆط§ط±ط¯ط© â€” POST /api/query_incoming_sms ط¨ظ…ط¤ط´ظ‘ط± طھط²ط§ظٹط¯ظٹ.
     *
     * `incoming_sms_id` ظٹط¬ط¹ظ„ ط§ظ„ط¨ظˆط§ط¨ط© طھظڈط¹ظٹط¯ ط§ظ„ط±ط³ط§ط¦ظ„ ط§ظ„ط£ط­ط¯ط« ظ…ظ† ظ‡ط°ط§ ط§ظ„ظ…ط¹ط±ظ‘ظپ ظپظ‚ط·
     * ط¨ط¯ظ„ ط§ظ„طµظ†ط¯ظˆظ‚ ظƒط§ظ…ظ„ظ‹ط§ ظپظٹ ظƒظ„ ط¯ظˆط±ط©. ط§ظ„ط§ط³طھط¬ط§ط¨ط© طھط­ظ…ظ„ `sms` ظˆ`read` ظˆ`unread`.
     *
     * `flag=all` ظ‡ظˆ ط§ظ„ط§ظپطھط±ط§ط¶ظٹ ط¹ظ† ظ‚طµط¯: `unread` ظٹط¹طھظ…ط¯ ط¹ظ„ظ‰ ط¹ظ„ط§ظ…ط© ط§ظ„ظ‚ط±ط§ط،ط© ط¯ط§ط®ظ„
     * ط§ظ„ط¬ظ‡ط§ط²طŒ ظˆظ‡ظٹ طھظڈظ‚ظ„ظژط¨ ط¨ظ…ط¬ط±ط¯ ط£ظ† ظٹظپطھط­ ط£ط­ط¯ظ‡ظ… طµظپط­ط© ط§ظ„ظˆط§ط±ط¯ ظپظٹ ظˆط§ط¬ظ‡ط© ط§ظ„ظˆظٹط¨ â€”
     * ظپطھطµظٹط± ط§ظ„ط±ط³ط§ظ„ط© آ«ظ…ظ‚ط±ظˆط،ط©آ» ظˆظ„ط§ طھط¸ظ‡ط± ظ„ظ†ط§ ط£ط¨ط¯ظ‹ط§. ط§ظ„ظ…ط¤ط´ظ‘ط± ط§ظ„طھط²ط§ظٹط¯ظٹ ظٹظƒظپظٹ ظˆط­ط¯ظ‡
     * ظ„ظ…ظ†ط¹ ط§ظ„طھظƒط±ط§ط±طŒ ظپظ„ط§ ط­ط§ط¬ط© ظ„ط¥ط³ظ†ط§ط¯ ط§ظ„ظ…ظ†ط¹ ط¥ظ„ظ‰ ط­ط§ظ„ط© ظ‚ط§ط¨ظ„ط© ظ„ظ„طھط؛ظٹظٹط± ظ…ظ† ط§ظ„ط®ط§ط±ط¬.
     */
    fun queryIncomingSms(
        sinceId: Long = 0,
        flag: String = DinstarApiContract.Sms.FLAG_ALL
    ): Map<String, Any?> =
        postJson(
            DinstarApiContract.Path.QUERY_INCOMING_SMS,
            incomingSmsBody(sinceId, flag)
        )

    /**
     * ط¬ظ„ط¨ SMS ط§ظ„ظˆط§ط±ط¯ط© ظ…ظ† ط¨ظˆط§ط¨ط© ط¨ط¹ظٹظ†ظ‡ط§.
     *
     * ط§ظ„ظ†ط³ط®ط© ط¨ظ„ط§ ظˆط³ظٹط· طھط®ط§ط·ط¨ ط§ظ„ط¹ظ†ظˆط§ظ† ط§ظ„ظ…ط¶ط¨ظˆط· ظˆط­ط¯ظ‡طŒ ظپظƒط§ظ† ظˆط§ط±ط¯ ط§ظ„ط¬ظ‡ط§ط² ط§ظ„ط«ط§ظ†ظٹ
     * ظ„ط§ ظٹظڈظ„طھظ‚ط· ط¥ط·ظ„ط§ظ‚ظ‹ط§ ظ…ظ‡ظ…ط§ ط¨ظ„ط؛ ط¹ط¯ط¯ ط§ظ„ط£ط¬ظ‡ط²ط© ط§ظ„ظ…ط³ط¬ظ‘ظ„ط©.
     */
    fun queryIncomingSms(
        gateway: DinstarFleetService.Gateway,
        sinceId: Long = 0,
        flag: String = DinstarApiContract.Sms.FLAG_ALL
    ): Map<String, Any?> =
        clientFor(gateway).postJson(
            DinstarApiContract.Path.QUERY_INCOMING_SMS,
            incomingSmsBody(sinceId, flag)
        )

    private fun incomingSmsBody(sinceId: Long, flag: String): Map<String, Any> = mapOf(
        DinstarApiContract.Sms.REQ_INCOMING_ID to sinceId,
        DinstarApiContract.Sms.REQ_FLAG to flag
    )

    /**
     * ط¹ط¯ط¯ SMS ظپظٹ ط§ظ„ط·ط§ط¨ظˆط±.
     *
     * ط§ظ„ط¨ط±ظ†ط§ظ…ط¬ ط§ظ„ط«ط§ط¨طھ 04240302 ظ„ط§ ظٹظƒط´ظپ `query_sms_queue` ظˆظ„ط§ ط§ظ„ط§ط³ظ… ط§ظ„ظ‚ط¯ظٹظ…
     * `query_sms_count` (ظƒظ„ط§ظ‡ظ…ط§ 404 ظ…ظڈط«ط¨طھ ظ…ظٹط¯ط§ظ†ظٹظ‹ط§). ظٹظڈط¬ط±ظژظ‘ط¨ ط§ظ„ظ…ظˆط«ظ‘ظ‚ ط£ظˆظ„ظ‹ط§ ط«ظ…
     * ط§ظ„ط¨ط¯ظٹظ„طŒ ظˆظٹظڈط¹ط§ط¯ `error_code=404` ظ…ظڈظˆطµظژظ‘ظپظ‹ط§ ط¨ط¯ظ„ ط±ظ…ظٹ ط§ط³طھط«ظ†ط§ط، ظٹظڈط³ظ‚ظگط· ط§ظ„ط§ط³طھط¯ط¹ط§ط،
     * ظƒظ„ظ‡ ط¹ظ„ظ‰ ط£ط¬ظ‡ط²ط© ظ„ط§ طھط¯ط¹ظ… ط§ظ„ظ…ط³ط§ط± ط£طµظ„ظ‹ط§.
     */
    fun querySmsQueueCount(): Map<String, Any?> {
        for (path in listOf(
            DinstarApiContract.Path.QUERY_SMS_QUEUE,
            DinstarApiContract.Path.QUERY_SMS_COUNT_LEGACY
        )) {
            runCatching { postJson(path, emptyMap<String, Any>()) }
                .onSuccess { return it }
                .onFailure { log.debug("DINSTAR {} ط؛ظٹط± ظ…ط¯ط¹ظˆظ…: {}", path, it.message) }
        }
        return mapOf(
            "error_code" to 404,
            "message" to "SMS queue length is not exposed by this firmware"
        )
    }

    /** ط¥ظٹظ‚ط§ظپ ظ…ظ‡ظ…ط© ط¥ط±ط³ط§ظ„ SMS â€” GET /api/stop_sms?task_id=N */
    fun stopSmsTask(taskId: Int): Map<String, Any?> {
        require(taskId >= 0) { "Invalid task_id" }
        return getJson("/api/stop_sms", mapOf("task_id" to taskId.toString()))
    }

    // â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
    // ًں“‍ Advanced Port Operations â€” ط­ط³ط¨ ظˆط«ط§ط¦ظ‚ Dinstar
    // â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ

    /** Call Forward â€” GET /api/set_port_info?action=CallForward */
    fun setCallForward(port: Int, param: String, number: String): Map<String, Any?> {
        requireValidPort(port)
        require(param in setOf("Unconditional", "NoReply", "Busy", "Not_Reachable", "CancelAll")) { "Invalid CallForward param" }
        return getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "CallForward",
            "param" to param, "number" to number
        ))
    }

    /** Power on/off port â€” GET /api/set_port_info?action=power&param=on/off */
    fun setPortPower(port: Int, on: Boolean): Map<String, Any?> {
        requireValidPort(port)
        return getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "power", "param" to if (on) "on" else "off"
        ))
    }

    /**
     * Get Device Status â€” POST /api/get_status.
     *
     * Body per [DinstarApiContract]: **a JSON array** of section names, e.g. `["performance"]`.
     * The earlier `{"maximum":10}` body was not documented and yielded 403 on `get_status`
     * (matches the diagnostic in `DINSTAR_API_DEEP_ANALYSIS.md`).
     */
    fun getDeviceStatus(): Map<String, Any?> = postJson(
        "/api/get_status",
        DinstarApiContract.Status.PERFORMANCE_BODY
    )

    fun probeHumanBehaviorEndpoints(): Map<String, Any?> {
        val candidates = listOf("/api/get_number_learning","/api/get_human_behavior","/api/get_global_params","/api/get_parameters","/api/get_config","/api/get_system_info")
        val results = candidates.associateWith { path ->
            runCatching { val url = baseUrl(activeHost).newBuilder().addPathSegments(path.removePrefix("/")).build(); client.newCall(Request.Builder().url(url).get().header("Accept", "application/json").build()).execute().use { it.code } }.getOrElse { -1 }
        }
        return mapOf("host" to activeHost, "reachable" to results.filterValues { it == 200 || (it in 400..499 && it != 404) }.keys, "details" to results)
    }

    // â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
    // ًںŒگ ط¹ظ…ظ„ظٹط§طھ ظ…ظˆط¬ظ‘ظ‡ط© ط¥ظ„ظ‰ ط¨ظˆط§ط¨ط© ط¨ط¹ظٹظ†ظ‡ط§ ظ…ظ† ط§ظ„ط£ط³ط·ظˆظ„
    //
    // ط§ظ„ظ†ط³ط® ط¨ظ„ط§ ظˆط³ظٹط· طھط®ط§ط·ط¨ ط§ظ„ط¹ظ†ظˆط§ظ† ط§ظ„ظ…ط¶ط¨ظˆط· ظپظٹ ط§ظ„ط¥ط¹ط¯ط§ط¯ط§طھ ظپظ‚ط·. ظ‡ط°ظ‡
    // ط§ظ„ظˆط³ط§ط¦ط· طھط¨ظ†ظٹ ط§ظ„ط§طھطµط§ظ„ ظ…ظ† ط³ط¬ظ„ ط§ظ„ط¨ظˆط§ط¨ط© (ظ…ط¶ظٹظپ/ظ…ظ†ظپط°/ظ…ط®ط·ط·) ط¹ط¨ط±
    // [DinstarConnectionFactory]طŒ ظپظٹط¹ظ…ظ„ ظƒظ„ ط¬ظ‡ط§ط² ظ…ط³ط¬ظ‘ظ„ ظ„ط§ ط¬ظ‡ط§ط² ظˆط§ط­ط¯.
    // â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ

    private fun clientFor(gateway: DinstarFleetService.Gateway): DinstarConnectionFactory.DinstarClient =
        connections.clientFor(gateway.host, gateway.apiPort, gateway.scheme)

    private fun requireGatewayPort(gateway: DinstarFleetService.Gateway, port: Int) =
        require(port in 0 until gateway.portCount) {
            "ظ…ظ†ظپط° ط®ط§ط±ط¬ ط§ظ„ظ…ط¯ظ‰: ط§ظ„ط¨ظˆط§ط¨ط© ${gateway.host} طھط¯ط¹ظ… 0-${gateway.portCount - 1}"
        }

    /** ط­ط§ظ„ط© ط¬ظ‡ط§ط² ط¨ظˆط§ط¨ط© ظ…ط­ط¯ط¯ط© (CPU/ط°ط§ظƒط±ط©/ظپظ„ط§ط´) â€” POST /api/get_status */
    fun getDeviceStatus(gateway: DinstarFleetService.Gateway): Map<String, Any?> =
        clientFor(gateway).getDeviceStatus()

    /**
     * ط³ط¬ظ„ ط§ظ„ظ…ظƒط§ظ„ظ…ط§طھ CDR ظ„ط¨ظˆط§ط¨ط© ظ…ط­ط¯ط¯ط© â€” POST /api/get_cdr ط¨ط¬ط³ظ… JSON.
     *
     * ط¹ظ†ط¯ ط؛ظٹط§ط¨ [port] طھظڈط³طھط¹ظ„ظ… ظƒظ„ ظ…ظ†ط§ظپط° ط§ظ„ط¨ظˆط§ط¨ط© ط­ط³ط¨ ط¹ط¯ط¯ظ‡ط§ ط§ظ„ظپط¹ظ„ظٹ.
     * ط§ظ„ط§ط³طھط¬ط§ط¨ط© طھط­ظ…ظ„ ط§ظ„ط³ط¬ظ„ط§طھ ظپظٹ ط­ظ‚ظ„ `cdr` (ظˆط¨ط¹ط¶ ط§ظ„ط¥طµط¯ط§ط±ط§طھ `info`).
     */
    fun getCdrRecords(
        gateway: DinstarFleetService.Gateway,
        port: Int? = null,
        timeAfter: String? = null,
        timeBefore: String? = null
    ): List<Map<String, Any?>> {
        port?.let { requireGatewayPort(gateway, it) }
        // ظ…ط«ط¨طھ ظ…ظٹط¯ط§ظ†ظٹظ‹ط§ 2026-09-04: UC2000-VE (02240221) ظٹط±ط¯ 400 ط¹ظ„ظ‰ ط£ظٹ body
        // ظٹط­ظ…ظ„ "port" â€” ظˆط§ظ„ط§ط³طھط¯ط¹ط§ط، ط§ظ„ظپط§ط±ط؛ {} ظٹط¹ظٹط¯ ظƒظ„ ط§ظ„ط³ط¬ظ„ط§طھ. ظ„ط°ظ„ظƒ طھط¯ط±ظ‘ط¬ ط¢ظ…ظ†:
        // ط§ظ„ط¬ط³ظ… ط§ظ„ظƒط§ظ…ظ„ â†’ ط¨ظ„ط§ ظپظ„ط§طھط± ط²ظ…ظ†ظٹط© â†’ ط¨ظ„ط§ ظپظ„طھط± ظ…ظ†ظپط° (ظ…ط¹ طھط±ط´ظٹط­ ظ…ط­ظ„ظٹ).
        val full = mutableMapOf<String, Any>(
            "port" to (port?.let { listOf(it) } ?: (0 until gateway.portCount).toList())
        )
        timeAfter?.let { full["time_after"] = it }
        timeBefore?.let { full["time_before"] = it }
        val attempts = listOf(
            full,
            full.filterKeys { it != "time_after" && it != "time_before" },
            emptyMap()
        )
        var lastError: Exception? = null
        for ((attemptIdx, body) in attempts.withIndex()) {
            try {
                val response = clientFor(gateway).postJson("/api/get_cdr", body)
                @Suppress("UNCHECKED_CAST")
                val rows = (response["cdr"] as? List<Map<String, Any?>>)
                    ?: (response["info"] as? List<Map<String, Any?>>)
                    ?: emptyList()
                if (attemptIdx > 0) log.info("get_cdr fallback {} accepted by {} ({} rows)", body.keys, gateway.host, rows.size)
                if (body.isEmpty() && port != null) {
                    return rows.filter { (it["port"] as? Number)?.toInt() == port }
                }
                return rows
            } catch (e: Exception) {
                lastError = e
                log.debug("get_cdr body {} rejected by {}: {}", body.keys, gateway.host, e.message)
            }
        }
        throw lastError ?: IllegalStateException("get_cdr failed on ${gateway.host}")
    }

    /** ط¥ط±ط³ط§ظ„ USSD ط¹ط¨ط± ط¨ظˆط§ط¨ط© ظ…ط­ط¯ط¯ط© â€” POST /api/send_ussd */
    fun sendUssd(gateway: DinstarFleetService.Gateway, port: Int, text: String): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        require(text.matches(Regex("^[*#0-9]{2,30}$"))) { "Invalid USSD code" }
        val response = clientFor(gateway).postJson(
            "/api/send_ussd",
            mapOf("port" to listOf(port), "command" to "send", "text" to text)
        )
        require(apiSuccess(response)) { "طھط¹ط°ظ‘ط± ط¥ط±ط³ط§ظ„ USSD: ${apiErrorMessage(response)}" }
        return response
    }

    fun queryUssdReply(gateway: DinstarFleetService.Gateway, port: Int): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        return clientFor(gateway).getJson(
            DinstarApiContract.Path.QUERY_USSD_REPLY, 
            mapOf("port" to port.toString())
        )
    }

    /** طھط´ط؛ظٹظ„/ط¥ظٹظ‚ط§ظپ ظ…ظ†ظپط° ظپظٹ ط¨ظˆط§ط¨ط© ظ…ط­ط¯ط¯ط© â€” GET /api/set_port_info?action=power */
    fun setPortPower(gateway: DinstarFleetService.Gateway, port: Int, on: Boolean): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        return clientFor(gateway).getJson("/api/set_port_info", mapOf(
            "port" to port.toString(), "action" to "power", "param" to if (on) "on" else "off"
        ))
    }

    /**
     * طھط­ظˆظٹظ„ ط§ظ„ظ…ظƒط§ظ„ظ…ط§طھ ظپظٹ ط¨ظˆط§ط¨ط© ظ…ط­ط¯ط¯ط© â€” GET /api/set_port_info?action=CallForward.
     *
     * [condition] ظٹظ‚ط¨ظ„ ط§ظ„طµظٹط؛ ط§ظ„ط¯ط§ط®ظ„ظٹط© (ALWAYS/NO_REPLY/BUSY/NOT_REACHABLE)
     * ظˆطھظڈطھط±ط¬ظ… ط¥ظ„ظ‰ ظ‚ظٹظ… ط§ظ„ط¨ظˆط§ط¨ط© ط§ظ„ظ…ظˆط«ظ‚ط©. ط§ظ„طھط¹ط·ظٹظ„ = CancelAll ظˆظ„ط§ ظٹط­طھط§ط¬ ط±ظ‚ظ…ظ‹ط§.
     */
    fun setCallForward(
        gateway: DinstarFleetService.Gateway,
        port: Int,
        enabled: Boolean,
        number: String? = null,
        condition: String? = null
    ): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        if (enabled) require(!number.isNullOrBlank()) { "ط±ظ‚ظ… ط§ظ„طھط­ظˆظٹظ„ ظ…ط·ظ„ظˆط¨ ط¹ظ†ط¯ ط§ظ„طھظپط¹ظٹظ„" }
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
        jdbc.update(
            "INSERT INTO gateway_operations(id,gateway_id,actor_id,operation,target_port,status,details_json,completed_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            UUID.randomUUID(), resolveGatewayId(), actorId, operation, port, status, mapper.writeValueAsString(details)
        )
    }

    private fun queryPortInfo(host: String): List<Map<String, Any?>> =
        queryPortInfo(host, portRange)

    private fun queryPortInfo(host: String, ports: IntRange): List<Map<String, Any?>> {
        // ط§ظ„ظ…ط³ط§ط± ط§ظ„ط£ط³ط§ط³ظٹ: ط¹ظ…ظٹظ„ HTTP API ط§ظ„ظ…ظˆط«ظ‘ظ‚ (Digest) ظ†ظپط³ظ‡ ط§ظ„ط°ظٹ ظٹط¹ظ…ظ„ ط¹ط¨ط±
        // ط§ظ„ط£ط³ط·ظˆظ„. ط§ظ„ظپط§طµظ„ط© ظپظٹ `info_type`/`port` طھظڈط±ظ…ظژظ‘ط² `%2C` â€” ط¨ط؛ظٹط± ط°ظ„ظƒ ظٹط±ط¯ظ‘
        // ط§ظ„ط¨ط±ظ†ط§ظ…ط¬ ط§ظ„ط«ط§ط¨طھ 04240302 ط¨ظ€401 ط±ط؛ظ… طµط­ط© ط§ظ„ط§ط¹طھظ…ط§ط¯
        // (ط§ظ†ط¸ط± [DinstarConnectionFactory.DinstarClient.encodeQueryValue]).
        val client = connections.clientFor(host, configuredPort, configuredScheme)
        return runCatching { client.queryPorts(ports.last + 1).ports }
            .recoverCatching { client.queryPorts(DinstarModelProfile.UC2000_VE_4G.portRange.last + 1).ports }
            // ط¢ط®ط± ظ…ظ„ط§ط°: ط¬ظ„ط³ط© ط§ظ„ظˆظٹط¨. ط¥طµط¯ط§ط±ط§طھ طھظڈط¹ط·ظ‘ظ„ آ«New Version APIآ» ط£ظˆ طھظپظ‚ط¯
            // ظ…ط²ط§ظ…ظ†ط© ظ‚ط§ط¹ط¯ط© Digest طھط¸ظ„ طھظڈط¬ظٹط¨ ط¹ظ„ظ‰ `WebGetPortInfoAll` ط¨ط§ظ„ظƒظˆظƒظٹطŒ
            // ظپط§ظ„ط¨ط¯ظٹظ„ ظٹظ…ظ†ط¹ ط¸ظ‡ظˆط± ط¨ظˆط§ط¨ط© ط­ظٹظ‘ط© ط¹ظ„ظ‰ ط£ظ†ظ‡ط§ ط³ط§ظ‚ط·ط©. ط§ظ„ط­ظ‚ظˆظ„ طھظڈط·ط¨ظژظ‘ط¹ ط¥ظ„ظ‰
            // ط£ط³ظ…ط§ط، get_port_info ط­طھظ‰ ظٹط¨ظ‚ظ‰ [normalizePort] ظ…طµط¯ط±ظ‹ط§ ظˆط§ط­ط¯ظ‹ط§.
            .recoverCatching { apiFailure ->
                log.warn("DINSTAR get_port_info failed on {} ({}) â€” falling back to web session", host, apiFailure.message)
                queryPortInfoViaWebSession(host, ports)
            }
            .getOrElse { throw IllegalStateException("No authenticated UC2000 port response on $host", it) }
    }

    /**
     * ظ‚ط±ط§ط،ط© ط­ط§ظ„ط© ط§ظ„ظ…ظ†ط§ظپط° ط¹ط¨ط± ط¬ظ„ط³ط© ظˆط§ط¬ظ‡ط© ط§ظ„ظˆظٹط¨ (`WebGetPortInfoAll`).
     *
     * طھظڈط³طھط®ط¯ظ… ظپظ‚ط· ط¹ظ†ط¯ ظپط´ظ„ ظˆط§ط¬ظ‡ط© HTTP API ط§ظ„ظ…ظˆط«ظ‘ظ‚ط©. ط§ظ„ط§ط³طھط¬ط§ط¨ط© ظ…طµظپظˆظپط© ط®ط§ظ…
     * (ظ„ط§ `{"info":[...]}`), ظˆط£ط³ظ…ط§ط، ط­ظ‚ظˆظ„ظ‡ط§ طھط®طھظ„ظپ: `status` ط¨ط¯ظ„ `reg`
     * ظˆ`call_status` ط¨ط¯ظ„ `callstate`طŒ ظˆط§ظ„ظ‚ظٹظ… ظ†طµظٹط©. طھظڈط·ط¨ظژظ‘ط¹ ظ‡ظ†ط§ ط¥ظ„ظ‰ ط¹ظ‚ط¯
     * `get_port_info` ظƒظٹ ظ„ط§ ظٹطھظپط±ظ‘ط¹ ظ…ظ†ط·ظ‚ ط§ظ„طھظپط³ظٹط± ظپظٹ ظ…ظˆط¶ط¹ظٹظ†.
     */
    private fun queryPortInfoViaWebSession(host: String, ports: IntRange): List<Map<String, Any?>> {
        ensureWebSession(host)
        val raw = getJsonArray("/WebGetPortInfoAll", host)
        return parsePortInfoResponse(raw, ports).map { entry ->
            entry + mapOf(
                "port" to (entry["port"]?.toString()?.toIntOrNull() ?: return@map entry),
                // reg/callstate ظ‡ظ…ط§ ظ…ط§ ظٹظ‚ط±ط¤ظ‡ normalizePortط› ظ†طµ ط§ظ„ظˆظٹط¨
                // "Mobile Registered" ظ…ظ‚ط¨ظˆظ„ ظپظٹ DinstarApiContract.PortInfo.
                "reg" to (entry["reg"] ?: entry["status"]),
                "callstate" to (entry["callstate"] ?: entry["call_status"]),
                "signal" to (entry["signal"]?.toString()?.trim()?.toIntOrNull() ?: entry["signal"])
            )
        }
    }

    /**
     * Establish web UI session by posting login form to /goform/IADIdentityAuth.
     * The gateway responds with 302 and sets a devckie cookie.
     * Subsequent requests to /WebGetPortInfoAll will include this cookie automatically.
     */
    private fun ensureWebSession(host: String) {
        val url = "$configuredScheme://$host:$configuredPort/goform/IADIdentityAuth".toHttpUrl()
        val formBody = FormBody.Builder()
            .add("username", gatewayUsername)
            .add("password", gatewayPassword)
            .build()
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 302) {
                throw IllegalStateException("DINSTAR web login failed: HTTP ${response.code} on $url")
            }
            val location = response.headers["Location"] ?: ""
            if (!location.contains("enFrame", true) && !location.contains("enMain", true)) {
                log.warn("DINSTAR web login returned {} â€” session may not be established (Location: {})", response.code, location)
            }
        }
    }

    /** GET /WebGetPortInfoAll â€” returns raw port array (not wrapped in "info"). */
    private fun getJsonArray(path: String, host: String): List<Map<String, Any?>> {
        val builder = baseUrl(host).newBuilder().addPathSegments(path.removePrefix("/"))
        val request = Request.Builder().url(builder.build()).get()
            .header("Accept", "application/json")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("DINSTAR HTTP ${response.code} on ${request.url.encodedPath}")
            }
            @Suppress("UNCHECKED_CAST")
            val body = requireNotNull(response.body) { "DINSTAR returned empty body" }
            mapper.readValue(body.bytes(), List::class.java) as List<Map<String, Any?>>
        }
    }

    private fun parsePortInfoResponse(raw: List<Map<String, Any?>>, ports: IntRange): List<Map<String, Any?>> {
        return raw.filter { it["port"] != null }
            .mapNotNull { entry ->
                val portStr = entry["port"]?.toString() ?: return@mapNotNull null
                if (portStr == "Total") return@mapNotNull null
                val portNum = portStr.toIntOrNull() ?: return@mapNotNull null
                if (portNum !in ports) return@mapNotNull null
                entry
            }
    }

    /**
     * ط§ط³طھط¹ظ„ط§ظ… ط§ظ„ظ…ظ†ط§ظپط° ط£ط«ظ†ط§ط، ط§ظ„ط§ظƒطھط´ط§ظپطŒ ط­ظٹظ† ظ„ط§ ظٹظƒظˆظ† ط§ظ„ط·ط±ط§ط² ظ…ط¹ط±ظˆظپظ‹ط§ ط¨ط¹ط¯.
     *
     * ظٹظڈط¬ط±ظژظ‘ط¨ ط§ظ„ظ…ط¯ظ‰ ط§ظ„ط£ظˆط³ط¹ (ط«ظ…ط§ظ†ظٹط© ظ…ظ†ط§ظپط°) ط£ظˆظ„ظ‹ط§. ط¨ط¹ط¶ ط§ظ„ط¥طµط¯ط§ط±ط§طھ طھط±ظپط¶
     * ط§ظ„ط·ظ„ط¨ ظƒط§ظ…ظ„ظ‹ط§ ط¥ط°ط§ طھط¶ظ…ظ‘ظ† ظ…ظ†ظپط°ظ‹ط§ ط؛ظٹط± ظ…ظˆط¬ظˆط¯ ط¨ط¯ظ„ طھط¬ط§ظ‡ظ„ظ‡طŒ ظپظ„ظˆ ط£ط®ظپظ‚
     * ظٹظڈط¹ط§ط¯ ط§ظ„ظ…ط­ط§ظˆظ„ط© ط¨ط§ظ„ظ…ط¯ظ‰ ط§ظ„ط±ط¨ط§ط¹ظٹ. ط¨ط؛ظٹط± ظ‡ط°ط§ ط§ظ„طھط±ط§ط¬ط¹ ظٹط¸ظ‡ط± ط¬ظ‡ط§ط² ط±ط¨ط§ط¹ظٹ
     * ط³ظ„ظٹظ… ط¹ظ„ظ‰ ط£ظ†ظ‡ ط؛ظٹط± ظ…طھطµظ„.
     */
    private fun discoverPorts(host: String): List<Map<String, Any?>> {
        val widest = DinstarModelProfile.UC2000_VE_8G.portRange
        runCatching { queryPortInfo(host, widest) }
            .onSuccess { if (it.isNotEmpty()) return it }
        log.debug("طھط¹ط°ظ‘ط± ط§ط³طھط¹ظ„ط§ظ… {} ظ…ظ†ظپط°ظ‹ط§ ط¹ظ„ظ‰ {}ط› ط¥ط¹ط§ط¯ط© ط§ظ„ظ…ط­ط§ظˆظ„ط© ط¨ط§ظ„ظ…ط¯ظ‰ ط§ظ„ط±ط¨ط§ط¹ظٹ", widest.count(), host)
        return queryPortInfo(host, DinstarModelProfile.UC2000_VE_4G.portRange)
    }

    /**
     * ط§ط³طھظ†طھط§ط¬ ط§ظ„ط·ط±ط§ط² ظ…ظ† ط§ظ„ظ…ظ†ط§ظپط° ط§ظ„طھظٹ ط±ط¯ظ‘طھ ظپط¹ظ„ظ‹ط§.
     *
     * ظ„ط§ طھظڈظپطµط­ `get_port_info` ط¹ظ† ط§ط³ظ… ط§ظ„ط·ط±ط§ط²طŒ ظ„ظƒظ†ظ‡ط§ طھظƒط´ظپ ط­ظ‚ظٹظ‚طھظٹظ†
     * ظƒط§ظپظٹطھظٹظ† ظ„ظ„طھظ…ظٹظٹط² ط¨ظٹظ† ط§ظ„ط·ط±ط§ط²ط§طھ ط§ظ„ط£ط±ط¨ط¹ط©:
     *
     * 1. **ط¹ط¯ط¯ ط§ظ„ظ…ظ†ط§ظپط°** â€” ظٹظپطµظ„ ط§ظ„ط±ط¨ط§ط¹ظٹ (â€‘4G/â€‘4T) ط¹ظ† ط§ظ„ط«ظ…ط§ظ†ظٹ (â€‘8G/â€‘8T).
     * 2. **ظ†ظˆط¹ ط§ظ„ط±ط§ط¯ظٹظˆ** ظ„ظƒظ„ ظ…ظ†ظپط° â€” ظˆط¬ظˆط¯ LTE ط£ظˆ WCDMA ظٹط¹ظ†ظٹ ط§ظ„ظ…طھط؛ظٹظ‘ط± â€‘Tط›
     *    ط§ظ„ط·ط±ط§ط² â€‘G ظˆط­ط¯ط§طھ GSM ط¨ط­طھط©.
     *
     * ظ„ط§ طھظڈط³طھظ†طھط¬ ط§ظ„ظ†ط·ط§ظ‚ط§طھ ط§ظ„طھط±ط¯ط¯ظٹط©: ظپظٹ ط§ظ„ط·ط±ط§ط² â€‘T طھط¹طھظ…ط¯ ط¹ظ„ظ‰ ظ…طھط؛ظٹظ‘ط±
     * ط§ظ„ط±ط§ط¯ظٹظˆ ط§ظ„ظ…ط±ظƒظ‘ط¨ (Type A/E/V/J/AU) ظˆظ„ط§ طھط¸ظ‡ط± ظپظٹ ظ‡ط°ظ‡ ط§ظ„ط§ط³طھط¬ط§ط¨ط©.
     */
    private fun inferModel(ports: List<Map<String, Any?>>): DinstarModelProfile {
        val hasLteRadio = ports.any { port ->
            val type = port["type"]?.toString()?.uppercase().orEmpty()
            "LTE" in type || "WCDMA" in type || "VOLTE" in type ||
                "CDMA" in type || "EVDO" in type || "EV-DO" in type ||
                "1X" in type || "EHRPD" in type || "4G" in type
        }
        // ط£ط±ط¨ط¹ط© ظ…ظ†ط§ظپط° ط£ظˆ ط£ظ‚ظ„ â‡’ ط§ظ„ظ…طھط؛ظٹظ‘ط± ط§ظ„ط±ط¨ط§ط¹ظٹ. ط§ظ„ط±ط¯ظ‘ ط§ظ„ظپط§ط±ط؛ ظٹط¨ظ‚ظ‰ ط¹ظ„ظ‰
        // ط§ظ„ط§ظپطھط±ط§ط¶ظٹ ط¨ط¯ظ„ طھط±ط¬ظٹط­ ط·ط±ط§ط² ط¨ظ„ط§ ط¯ظ„ظٹظ„.
        val isQuad = ports.isNotEmpty() && ports.size <= 4
        return when {
            isQuad && hasLteRadio -> DinstarModelProfile.UC2000_VE_4T
            isQuad -> DinstarModelProfile.UC2000_VE_4G
            hasLteRadio -> DinstarModelProfile.UC2000_VE_8T
            else -> DinstarModelProfile.UC2000_VE_8G
        }
    }

    /**
     * طھط­ط¯ظٹط¯ ط§ط³ظ… ط§ظ„ظ…ط´ط؛ظ‘ظ„ ظ…ظ† ط£ط¯ظ‚ظ‘ ط¯ظ„ظٹظ„ ظ…طھط§ط­.
     *
     * ## طھط±طھظٹط¨ ط§ظ„ط£ط¯ظ„ط© â€” ظ…ظ† ط§ظ„ط£ظ‚ظˆظ‰ ط¥ظ„ظ‰ ط§ظ„ط£ط¶ط¹ظپ
     *
     * 1. **ط±ظ‚ظ… ط§ظ„ط´ط±ظٹط­ط©** (`number`) â€” ظ‚ط§ط·ط¹ ط­ظٹظ† ظٹظˆط¬ط¯طŒ ظ„ظƒظ†ظ‡ ظپط§ط±ط؛ ط­طھظ‰ ظٹطھظ…
     *    آ«طھط¹ظ„ظ‘ظ… ط§ظ„ط±ظ‚ظ…آ»طŒ ظˆظ‡ظˆ ط­ط§ظ„ ظƒظ„ ط§ظ„ظ…ظ†ط§ظپط° ط§ظ„ط«ظ…ط§ظ†ظٹط© ظپظٹ ظ‡ط°ط§ ط§ظ„ظ†ط´ط±.
     * 2. **IMSI** â€” ظ…طھط§ط­ ط¯ط§ط¦ظ…ظ‹ط§ ظˆظ…ط³طھظ‚ظ„ ط¹ظ† ط§ظ„طھط¹ظ„ظ‘ظ…. ظ‡ط°ط§ ظ‡ظˆ **ط§ظ„ظ…ط³ط§ط± ط§ظ„ظپط¹ظ„ظٹ**
     *    ط¹ظ„ظ‰ ظˆط§ط¬ظ‡ط© HTTP API ظ„ط£ظ† `get_port_info` **ظ„ط§ طھظڈطµط¯ط± `operator`
     *    ط¥ط·ظ„ط§ظ‚ظ‹ط§** (ظ…ظڈط«ط¨طھ: ط·ظ„ط¨ظ‡ ط¨ط£ظٹ ط§ط³ظ… ظٹط±ط¯ظ‘ `error_code=400`).
     * 3. **ط§ط³ظ… ط§ظ„ظ…ط´ط؛ظ‘ظ„ ط§ظ„ظ†طµظٹ** â€” طھظڈطµط¯ط±ظ‡ ظˆط§ط¬ظ‡ط© ط§ظ„ظˆظٹط¨ ظˆط­ط¯ظ‡ط§طŒ ظˆط£ط­ظٹط§ظ†ظ‹ط§ ظƒط±ظ‚ظ…
     *    ط®ط§ظ… `"42103"`. ظٹظڈطµط­ظژظ‘ط­ ظ‡ظ†ط§: MTNâ†گYOU (2021)طŒ HiTelâ†گYTelecom.
     *
     * ط¬ط¯ط§ظˆظ„ ط§ظ„ط¨ط§ط¯ط¦ط§طھ ظˆMNC **ظ„ظٹط³طھ ظ‡ظ†ط§**: ظ…طµط¯ط±ظ‡ظ…ط§ ط§ظ„ظˆط­ظٹط¯
     * [com.red.server.pstn.YemenNumberPlan]. ظƒط§ظ† ظ‡ط°ط§ ط§ظ„ظ…ظ„ظپ ظٹط­ظ…ظ„ ظ†ط³ط®طھظ‡
     * ط§ظ„ط®ط§طµط© ظ…ظ† ظƒظ„ظژظٹظ‡ظ…ط§طŒ ظپط£ظٹ طھطµط­ظٹط­ ظپظٹ ط£ط­ط¯ظ‡ظ…ط§ ظٹطھط±ظƒ ط§ظ„ط¢ط®ط± ظ…ط¹ط·ظˆط¨ظ‹ط§.
     */
    private fun resolveOperatorName(apiName: String?, simNumber: String?, imsi: String? = null): String {
        // 1) ط±ظ‚ظ… ط§ظ„ط´ط±ظٹط­ط© + IMSI ظ…ط¹ ظƒط´ظپ ط§ظ„طھظ†ظ‚ظ„ (MNP): ط§ظ„ط±ظ‚ظ… ظ‚ط¯ ظٹظƒظˆظ† ظ…ظ†ظ‚ظˆظ„ط§ظ‹
        //    ظ„ظ…ط´ط؛ظ„ ط¢ط®ط± (ظ…ط«ط¨طھ: IMSI 42103 ظٹظ…ظ† ظ…ظˆط¨ط§ظٹظ„ ظ…ط¹ ط±ظ‚ظ… 71 ط³ط¨ط£ظپظˆظ†).
        //    IMSI ظ‡ظˆظٹط© ط§ظ„ط´ط±ظٹط­ط© ط§ظ„ظپظٹط²ظٹط§ط¦ظٹط© â€” ظٹظڈظ‚ط¯ظژظ‘ظ… ط¹ظ†ط¯ ط§ظ„طھط¹ط§ط±ط¶.
        val imsiOp = com.red.server.pstn.YemenNumberPlan.classifyImsi(imsi)?.apiName
        if (!simNumber.isNullOrBlank()) {
            val numOp = DinstarLoadBalancer.classifyNumber(simNumber)?.apiName
            if (numOp != null) {
                if (imsiOp != null && imsiOp != numOp && !imsiOp.startsWith("YE-MNC-")) {
                    log.warn("Possible ported number: SIM IMSI indicates {} but number {} indicates {} â€” trusting IMSI", imsiOp, simNumber, numOp)
                    return imsiOp
                }
                return numOp
            }
        }
        // 2) IMSI â€” ط§ظ„ظ…ط³ط§ط± ط§ظ„ظپط¹ظ„ظٹ ط¹ظ„ظ‰ HTTP API (ظ„ط§ ظٹظڈطµط¯ظگط± operator)
        YemenNumberPlan.classifyImsi(imsi)?.let { return it.apiName }
        // 3) ط§ظ„ط§ط³ظ… ط§ظ„ظ†طµظٹ ظ…ظ† ظˆط§ط¬ظ‡ط© ط§ظ„ظˆظٹط¨طŒ ظ…ط¹ طھطµط­ظٹط­ ط§ظ„ط£ط³ظ…ط§ط، ط§ظ„ظ‚ط¯ظٹظ…ط©
        if (!apiName.isNullOrBlank() && apiName != "UNKNOWN") {
            // طµظٹط؛ط© PLMN ط§ظ„ط®ط§ظ… "42103" â€” طھظڈظ‚ط±ط£ ظƒظ€MCC+MNC ظ„ط§ ظƒط§ط³ظ…
            apiName.filter { it.isDigit() }.takeIf { it.length == 5 }?.let { plmn ->
                if (plmn.startsWith(YemenNumberPlan.YEMEN_MCC)) {
                    val mnc = plmn.substring(3, 5)
                    return (YemenNumberPlan.OPERATORS_BY_MNC[mnc]
                        ?: YemenNumberPlan.unmappedYemeniMnc(mnc)).apiName
                }
            }
            return when {
                apiName.contains("Sabafon", ignoreCase = true) -> "Sabafon"
                apiName.contains("YOU", ignoreCase = true) || apiName.contains("Yemeni Omani", ignoreCase = true) -> "YOU"
                apiName.contains("MTN", ignoreCase = true) -> "YOU"  // MTN â†’ YOU since 2021
                apiName.contains("Yemen", ignoreCase = true) && apiName.contains("Mobile", ignoreCase = true) -> "YemenMobile"
                apiName.contains("Y Telecom", ignoreCase = true) || apiName == "Y" -> "YTelecom"
                apiName.contains("HiTel", ignoreCase = true) || apiName.contains("Hi Tel", ignoreCase = true) -> "YTelecom"  // HiTelâ†’YTelecom
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
        val imsi = raw["imsi"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val resolvedOperator = resolveOperatorName(apiOperator, simNumber, imsi)

        // طھظپط³ظٹط± ط§ظ„ط¥ط´ط§ط±ط© ط­ط³ط¨ RAT: LTE RSRP ظˆCDMA RSSI ظٹط®طھظ„ظپط§ظ† ط¹ظ† GSM CSQ.
        // ظٹظ…ظ† ظ…ظˆط¨ط§ظٹظ„ LTE/CDMA ظƒط§ظ†طھ طھظڈط±ظپظژط¶ ط¸ظ„ظ…ط§ظ‹ ط¨ظ€ OUT_OF_RANGE.
        val signal = DinstarSignal.interpretRatAware(raw)

        return mapOf(
            "index" to index,
            "radioType" to raw["type"].toString(),
            "status" to raw["reg"].toString(),
            "callState" to raw["callstate"].toString(),
            "gprs" to raw["gprs"].toString(),
            "number" to simNumber,
            "numberMasked" to mask(simNumber),
            "imsi" to raw["imsi"]?.toString(),
            "imsiMasked" to mask(raw["imsi"]?.toString()),
            "iccid" to raw["iccid"]?.toString(),
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

    /**
     * ط§ظ„ظ…ط¹ط±ظپ ط§ظ„ظپط¹ظ„ظٹ ظ„ظ„ط¨ظˆط§ط¨ط© ظپظٹ ط¬ط¯ظˆظ„ telecom_gateways.
     *
     * ط§ظ„ط£ط³ط·ظˆظ„ ظٹط­ظپط¸ ط§ظ„ط¨ظˆط§ط¨ط© ط¨ظ…ط¹ط±ظپ ظ…ط´طھظ‚ ظ…ظ† ط§ظ„ط±ظ‚ظ… ط§ظ„طھط³ظ„ط³ظ„ظٹ
     * (DINSTAR:SN:...) ط¨ظٹظ†ظ…ط§ ظ‡ط°ط§ ط§ظ„ظƒط§ط¦ظ† ظٹط­ط³ط¨ ظ…ط¹ط±ظپظ‹ط§ ظ…ظ† ط§ظ„ط¹ظ†ظˆط§ظ† â€”
     * ظپظƒط§ظ†طھ ط§ظ„ظƒطھط§ط¨ط§طھ ط¨ظ‡ طھظƒط³ط± ظ‚ظٹط¯ ط§ظ„ظ…ظپطھط§ط­ ط§ظ„ط®ط§ط±ط¬ظٹ. طھظڈظ‚ط±ط£ ط§ظ„ط¨ظˆط§ط¨ط©
     * ط¨ط§ظ„ط¹ظ†ظˆط§ظ† ط§ظ„ظ…ط·ط§ط¨ظ‚طŒ ظˆظٹظڈط¹ط§ط¯ ط§ظ„طھط³ط¬ظٹظ„ ط¥ظ† ط؛ط§ط¨طھ ط«ظ… طھظڈط¹ط§ط¯ ط§ظ„ظ‚ط±ط§ط،ط©.
     */
    private fun resolveGatewayId(): UUID {
        jdbc.queryForObject(
            "SELECT id FROM telecom_gateways WHERE host = ? AND api_port = ?",
            UUID::class.java, activeHost, configuredPort
        )?.let { return it }
        registerGateway(0)
        return jdbc.queryForObject(
            "SELECT id FROM telecom_gateways WHERE host = ? AND api_port = ?",
            UUID::class.java, activeHost, configuredPort
        ) ?: gatewayId
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
        // ط§ظ„ظپط§طµظ„ط© ط§ظ„ط®ط§ظ… ظپظٹ ط¢ط®ط± ظ…ط¹ط§ظ…ظ„ طھظƒط³ط± ظ…ط·ط§ط¨ظ‚ط© Digest URI ط¹ظ„ظ‰ ط§ظ„ط¨ط±ظ†ط§ظ…ط¬
        // ط§ظ„ط«ط§ط¨طھ 04240302 ظپطھط±ط¯ظ‘ 401 ط±ط؛ظ… طµط­ط© ط§ظ„ط§ط¹طھظ…ط§ط¯ â€” ط§ظ„طھظپطµظٹظ„ ظˆط§ظ„ط¬ط¯ظˆظ„ ظپظٹ
        // [DinstarConnectionFactory.DinstarClient.encodeQueryValue].
        query.forEach { (name, value) ->
            builder.addEncodedQueryParameter(
                name,
                DinstarConnectionFactory.DinstarClient.encodeQueryValue(value)
            )
        }
        return execute(Request.Builder().url(builder.build()).get().build())
    }

    /**
     * @param host ط§ظ„ط¨ظˆط§ط¨ط© ط§ظ„ظ…ظ‚طµظˆط¯ط©. ط§ظ„ط§ظپطھط±ط§ط¶ظٹ [activeHost] طھظˆط§ظپظ‚ظ‹ط§ ظ…ط¹
     *   ط§ظ„ظ†ط´ط± ط°ظٹ ط§ظ„ط¬ظ‡ط§ط² ط§ظ„ظˆط§ط­ط¯طŒ ظ„ظƒظ† ط§ظ„ظ…ط¹ط§ظ…ظ„ ط¶ط±ظˆط±ظٹ ظ…ط¹ ط§ظ„ط£ط³ط·ظˆظ„: ظƒط§ظ†طھ
     *   ط§ظ„ط¯ط§ظ„ط© طھط«ط¨ظ‘طھ [activeHost] ظپطھط°ظ‡ط¨ ظƒظ„ ط±ط³ط§ظ„ط© ط¥ظ„ظ‰ ط¨ظˆط§ط¨ط© ظˆط§ط­ط¯ط© ظ…ظ‡ظ…ط§
     *   ط¨ظ„ط؛ ط¹ط¯ط¯ ط§ظ„ط£ط¬ظ‡ط²ط© ط§ظ„ظ…ط³ط¬ظ‘ظ„ط© â€” ظ†ط¸ظٹط± `getJson` ط§ظ„ط°ظٹ ظƒط§ظ† ظٹظ‚ط¨ظ„ ط§ظ„ط®ظٹط§ط±.
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
                log.error("DINSTAR HTTP {} on {} â€” auth challenge: {}", response.code, unsigned.url, challenge)
                throw IllegalStateException("DINSTAR HTTP ${response.code} on ${unsigned.url.encodedPath} â€” auth challenge: $challenge")
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
     * ظ‡ظ„ ظ‚ط¨ظ„طھ ط§ظ„ط¨ظˆط§ط¨ط© ط§ظ„ط·ظ„ط¨طں
     *
     * ط§ظ„طھظˆط«ظٹظ‚ ط§ظ„ط±ط³ظ…ظٹ (آ«Dinstar GSM Gateway HTTP APIآ» آ§2.3 ظˆآ§7.3) ظٹظ…ظٹظ‘ط²:
     * - **200** ط·ظڈظ„ط¨ ظˆظ†ظڈظپظگظ‘ط°.
     * - **202** ظ‚ظڈط¨ظ„ ظˆط³ظٹظڈظ†ظپظژظ‘ط° ظ„ط§ط­ظ‚ظ‹ط§ â€” طھط±ط¬ط¹ظ‡ `send_sms` ظˆ`send_ussd`
     *   ظ„ط£ظ†ظ‡ظ…ط§ ط؛ظٹط± ظ…طھط²ط§ظ…ظ†ظٹظ† ط¨ط·ط¨ظٹط¹طھظ‡ظ…ط§ (ط§ظ„ط±ط³ط§ظ„ط© طھط¯ط®ظ„ ط·ط§ط¨ظˆط±ظ‹ط§).
     *
     * ظƒط§ظ† ط§ظ„ط´ط±ط· `== 200` ظٹط±ظپط¶ 202طŒ ظپظƒظ„ ط£ظ…ط± USSD ظ†ط§ط¬ط­ ظٹظڈط±ظ…ظ‰
     * `IllegalArgumentException` ظˆظٹظڈط³ط¬ظژظ‘ظ„ ظپط´ظ„ظ‹ط§ ط±ط؛ظ… طھظ†ظپظٹط°ظ‡ ظپط¹ظ„ظ‹ط§ ط¹ظ„ظ‰
     * ط§ظ„ط´ط¨ظƒط©. ط§ظ„ظ…ط³ط¤ظˆظ„ ظٹط±ظ‰ آ«ظپط´ظ„آ» ط«ظ… ظٹط¹ظٹط¯ ط§ظ„ظ…ط­ط§ظˆظ„ط© ظپظٹظڈط±ط³ظژظ„ ط§ظ„ط£ظ…ط± ظ…ط±طھظٹظ†.
     *
     * ط£ظ…ط§ 400 ظˆ413 ظˆ500 ظˆ550 ظپط£ط®ط·ط§ط، ط­ظ‚ظٹظ‚ظٹط© طھظڈط±ظپط¶.
     */
    private fun apiSuccess(response: Map<String, Any?>): Boolean =
        (response["error_code"] as? Number)?.toInt() in ACCEPTED_CODES

    /** ط±ط³ط§ظ„ط© ط§ظ„ط®ط·ط£ ط§ظ„ظ…ظˆط«ظ‚ط© ط§ظ„ظ…ظ‚ط§ط¨ظ„ط© ظ„ظ„ط±ظ…ط² â€” ط£ظˆط¶ط­ ظ…ظ† ط±ظ‚ظ… ظ…ط¬ط±ظ‘ط¯. */
    private fun apiErrorMessage(response: Map<String, Any?>): String {
        val code = (response["error_code"] as? Number)?.toInt()
        val meaning = when (code) {
            400 -> "طµظٹط؛ط© ط§ظ„ط·ظ„ط¨ ط؛ظٹط± طµط§ظ„ط­ط©"
            404 -> "ط§ظ„ظ…ظ‡ظ…ط© ط؛ظٹط± ظ…ظˆط¬ظˆط¯ط©"
            413 -> "ط¹ط¯ط¯ ط§ظ„ظ…ط³طھظ„ظ…ظٹظ† ط£ظˆ ط­ط¬ظ… ط§ظ„ظ†طµ ظٹطھط¬ط§ظˆط² ط§ظ„ط­ط¯"
            486 -> "ط§ظ„ظ…ظ†ظپط° ظ…ط´ط؛ظˆظ„ ط­ط§ظ„ظٹظ‹ط§"
            500 -> "ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ظپظٹ ط§ظ„ط¨ظˆط§ط¨ط©"
            503 -> "ط§ظ„ظ…ظ†ظپط° ط؛ظٹط± ظ…ط³ط¬ظ‘ظ„ ط¹ظ„ظ‰ ط§ظ„ط´ط¨ظƒط©"
            550 -> "ظ„ط§ ظٹظˆط¬ط¯ ظ…ظ†ظپط° ظ…طھط§ط­ ظ„ظ„ط¥ط±ط³ط§ظ„"
            null -> "ط§ط³طھط¬ط§ط¨ط© ط¨ظ„ط§ error_code"
            else -> "ط±ظ…ط² ط؛ظٹط± ظ…ظˆط«ظ‚"
        }
        return "ط§ظ„ط¨ظˆط§ط¨ط© ط±ط¯ظ‘طھ $code â€” $meaning"
    }
     /**
      * ط¬ظ„ط¨ configuration ط§ظ„ط¬ظ‡ط§ط² ط§ظ„ط­ط§ظ„ظٹط©.
      * GET /api/get_config
      *
      * طھظ‚ط±ط£ ط§ظ„طھظƒظˆظٹظ† ط§ظ„ظƒط§ظ…ظ„ ظ…ظ† ط§ظ„ط¬ظ‡ط§ط² ظˆطھظڈط¹ظٹط¯ظ‡ ظƒظ€ JSON blob
      * ظٹظڈظ…ظƒظ† ط§ط³طھط®ط¯ط§ظ…ظ‡ ظ„ظ„ظ†ط³ط® ط§ظ„ط§ط­طھظٹط§ط·ظٹ ط£ظˆ ط§ظ„ظ…ظ‚ط§ط±ظ†ط© ط£ظˆ ط§ظ„ط§ط³طھط¹ط§ط¯ط©.
      */
     fun getConfig(gateway: DinstarFleetService.Gateway): Map<String, Any?> {
         return try {
             val client = connections.clientFor(gateway.host, gateway.apiPort, gateway.scheme)
             val response = client.getJson("/api/get_config")
             log.info("Config fetched from {}: {}", gateway.host, response.keys)
             response
         } catch (e: Exception) {
             log.error("Error fetching config from ${gateway.host}", e)
             mapOf("error" to e.message)
         }
     }

     /**
      * ط¯ظپط¹ configuration ظ„ظ„ط¬ظ‡ط§ط².
      * POST /api/set_config
      *
      * ظٹظ‚ط¨ظ„ config blob (map) ظˆظٹظڈط±ط³ظ„ظ‡ ظ„ظ„ط¬ظ‡ط§ط².
      * ظٹظڈط³طھط®ط¯ظ… ظ„ط§ط³طھط¹ط§ط¯ط© ظ†ط³ط®ط© ط§ط­طھظٹط§ط·ظٹط© ط£ظˆ طھط­ط¯ظٹط« ط§ظ„طھظƒظˆظٹظ† ط¯ظپط¹ط© ظˆط§ط­ط¯ط©.
      */
     fun setConfig(gateway: DinstarFleetService.Gateway, config: Map<String, Any?>): Map<String, Any?> {
         return try {
             val client = connections.clientFor(gateway.host, gateway.apiPort, gateway.scheme)
             val response = client.postJson("/api/set_config", config)
             log.info("Config pushed to {}: {}", gateway.host, response)
             response
         } catch (e: Exception) {
             log.error("Error pushing config to ${gateway.host}", e)
             mapOf("error" to e.message)
         }
     }

/**
       * ط§ط³طھط¹ط§ط¯ط© configuration ظ…ظ† ظ†ط³ط®ط© ط§ط­طھظٹط§ط·ظٹط© ظ…ط­ظپظˆط¸ط© ظپظٹ DB.
       * ظٹط¹ظٹط¯ طھط³ظ…ظٹط© snapshot ط¨ظ€ 'restoring' ط«ظ… ظٹظ†ظپظ‘ط° push.
       */
      fun logConfigChange(gatewayId: UUID, actorId: UUID?, changeType: String, portIndex: Int?, reason: String?, details: Any?) {
          try {
              jdbc.update(
                  "INSERT INTO dinstar_config_changes (gateway_id, changed_by, change_type, port_index, reason) VALUES (?, ?, ?, ?, ?)",
                  gatewayId, actorId, changeType, portIndex, reason
              )
          } catch (e: Exception) {
              log.warn("Failed to log config change {}: {}", changeType, e.message)
          }
      }

      fun restoreConfig(gateway: DinstarFleetService.Gateway, snapshotId: UUID): Map<String, Any?> {
          return try {
              val snapshot = jdbc.queryForMap(
                  "SELECT config_json FROM dinstar_config_snapshots WHERE id = ? AND gateway_id = ?",
                  snapshotId, gateway.id
              )
              val config = mapper.readValue(snapshot["config_json"] as String, object : TypeReference<Map<String, Any?>>() {})
              val result = setConfig(gateway, config)
              if (!result.containsKey("error")) {
                  jdbc.update("UPDATE dinstar_config_snapshots SET is_restore = true WHERE id = ?", snapshotId)
                  logConfigChange(gateway.id, null, "CONFIG_RESTORE", null, "Restored from snapshot $snapshotId", null)
              }
              result
          } catch (e: Exception) {
              log.error("Error restoring config snapshot $snapshotId", e)
              mapOf("error" to e.message)
          }
      }

      /**
       * Backup device configuration to database.
       * Stores the complete config blob with timestamp for disaster recovery.
       */
      fun backupConfig(gateway: DinstarFleetService.Gateway, actorId: UUID? = null): Map<String, Any?> {
          val config = getConfig(gateway)
          if (config.containsKey("error")) return config

          @Suppress("UNCHECKED_CAST")
          val configBlob = config["config"] as? Map<String, Any?> ?: emptyMap()
          val json = mapper.writeValueAsString(configBlob)
          val backupId = UUID.randomUUID()

          jdbc.update(
              """INSERT INTO gateway_config_backups(id,gateway_id,config_json,actor_id,created_at)
                 VALUES (?,?,?,?,CURRENT_TIMESTAMP)""",
              backupId, gateway.id, json, actorId
          )

          return mapOf(
              "success" to true,
              "backupId" to backupId,
              "host" to gateway.host,
              "configSize" to configBlob.size,
              "timestamp" to System.currentTimeMillis()
          )
      }

      /**
       * List available configuration backups for a gateway.
       */
      fun listBackups(gateway: DinstarFleetService.Gateway): List<Map<String, Any?>> {
          return jdbc.queryForList(
              """SELECT id, created_at, actor_id, json_length(config_json) as config_size
                 FROM gateway_config_backups WHERE gateway_id = ? ORDER BY created_at DESC LIMIT 50""",
              gateway.id
          )
      }

      /**
       * Health check for a specific gateway.
       * Returns detailed status including port registration, signal quality, and availability.
       */
      fun healthCheck(gateway: DinstarFleetService.Gateway): Map<String, Any?> {
          return try {
              val status = getDeviceStatus(gateway)
              val ports = getHardwareStatus(gateway)
              val registeredPorts = ports.count { (it["status"] as? String)?.contains("Registered", ignoreCase = true) == true }
              val totalPorts = ports.size
              val healthyPorts = ports.count { (it["signalUsable"] as? Boolean) == true }

              mapOf(
                  "success" to true,
                  "host" to gateway.host,
                  "model" to gateway.model,
                  "reachable" to true,
                  "portStats" to mapOf(
                      "total" to totalPorts,
                      "registered" to registeredPorts,
                      "healthy" to healthyPorts
                  ),
                  "deviceStatus" to status,
                  "checkedAt" to System.currentTimeMillis()
              )
          } catch (e: Exception) {
              log.error("Health check failed for ${gateway.host}", e)
              mapOf(
                  "success" to false,
                  "host" to gateway.host,
                  "reachable" to false,
                  "error" to e.message,
                  "checkedAt" to System.currentTimeMillis()
              )
          }
      }

      /**
       * Fleet-wide health summary.
       */
      fun fleetHealthSummary(): Map<String, Any?> {
          val gateways = try {
              fleetService.listGateways()
          } catch (e: Exception) {
              return mapOf("success" to false, "error" to e.message)
          }

          val summaries = gateways.map { gateway: DinstarFleetService.Gateway ->
              val health = healthCheck(gateway)
              val portTotal = (health["portStats"] as? Map<*, *>)?.get("total") as? Number
              mapOf(
                  "host" to gateway.host,
                  "model" to gateway.model,
                  "reachable" to health["reachable"],
                  "ports" to (portTotal?.toInt() ?: 0)
              )
          }

          val totalPorts = summaries.sumOf { (it["ports"] as? Number)?.toInt() ?: 0 }
          val reachableGateways = summaries.count { it["reachable"] == true }

          return mapOf(
              "success" to true,
              "totalGateways" to gateways.size,
              "reachableGateways" to reachableGateways,
              "totalPorts" to totalPorts,
              "gateways" to summaries,
              "checkedAt" to System.currentTimeMillis()
          )
      }

      private fun isPrivateAddress(host: String) = runCatching { InetAddress.getByName(host).isSiteLocalAddress }.getOrDefault(false)
      private fun mask(value: String?): String? = value?.takeIf { it.isNotBlank() && it != "null" }?.let { "â€¢â€¢â€¢â€¢${it.takeLast(4)}" }
      private fun unsupported(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, message)

      private fun documentedCapabilities(): Map<String, Any> = mapOf(
          "voiceViaAsterisk" to true,
          "portInfo" to true,
          "moduleReset" to true,
          "sms" to true,
          "ussd" to true,
          "cdr" to true,
          "configBackupViaUi" to true,
          "configPush" to true,
          "configRestore" to true,
          "firmwareUpgradeViaUi" to true,
          "remoteFirmwareUpgrade" to false,
          "remoteNetworkConfig" to false,
          "factoryResetFromYounes" to false
      )
  }



