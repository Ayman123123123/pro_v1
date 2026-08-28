package com.red.server.services

import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.CertificatePinner
import okhttp3.FormBody
import okhttp3.HttpUrl
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.pstn.YemenNumberPlan
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
    @Value("\${red.dinstar.port:80}") private val configuredPort: Int,
    @Value("\${red.dinstar.scheme:http}") private val configuredScheme: String,
    @Value("\${red.dinstar.username:admin}") private val gatewayUsername: String,
    @Value("\${red.dinstar.password:admin}") private val gatewayPassword: String,
    @Value("\${red.dinstar.cert-pins:}") private val certPinsConfig: String,
    private val mapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
    private val connections: DinstarConnectionFactory
) {
    /**
     * أنماط «تعلّم الرقم» كما تُرقّمها صفحة `enHBPhoneNumberAdd.htm` على
     * الجهاز. DINSTAR توثّق الثلاثة رسميًا في FAQ الخاص بـ UC2000.
     *
     * معرَّف على مستوى الصنف لا داخل `companion object` كي يُشار إليه من
     * المتحكّمات بـ `DinstarHardwareService.NumberLearningMethod`.
     */
    enum class NumberLearningMethod(val wire: String) {
        SMS("0"), USSD("1"), CALL("2")
    }

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

        /** الرمز القصير لخدمة «معرفة رقمي» في سبأفون. */
        const val SABAFON_MMN_SHORTCODE = "333"

        /** نص الطلب لخدمة «معرفة رقمي» في سبأفون. */
        const val SABAFON_MMN_KEYWORD = "MMN"

        /** رقم هاتف أو رمز قصير — يطابق `ReTestNumber` في صفحة الجهاز. */
        private val NUMBER_OR_SHORTCODE = Regex("^\\+?[0-9*#]{2,23}$")

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

    /**
     * OkHttp client configured with:
     * 1. CookieJar for web UI session (form login → devckie cookie)
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

        // واجهة UC2000 تطالب Digest (الإصدارات الأقدم Basic). كان العميل
        // يُبنى بلا أي مُصادِق فيرتد كل استدعاء (USSD/SMS/CDR) بـ 401
        // رغم صحة الاعتماد — يُضاف هنا نفس كومة المصادقة الناجحة
        // المستخدمة في DinstarConnectionFactory.
        val credentials = Credentials(gatewayUsername, gatewayPassword)
        val dispatching = DispatchingAuthenticator.Builder()
            .with("digest", DigestAuthenticator(credentials))
            .with("basic", BasicAuthenticator(credentials))
            .build()
        val authCache = ConcurrentHashMap<String, CachingAuthenticator>()

        // SPKI pinning اختياري: حتى مع trust-all (شهادات ذاتية التوقيع على
        // LAN إداري)، OkHttp يتحقق من الدبوس بعد بناء السلسلة — أي شهادة
        // مزوّرة من مهاجم في الشبكة تُرفض رغم قبول TrustManager لها.
        // الصيغة: sha256/xxx,sha256/yyy
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

        return builder
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
        // الاتصال يقتصر على البوابة المهيأة صراحةً؛ لا توجد عودة صامتة إلى عنوان
        // تاريخي لأنها قد تستعلم جهازًا آخر وتكتب حالة منافذه في السجل الخطأ.
        val candidates = linkedSetOf(configuredIp)
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
        // يُستخدم المعرف الفعلي من الجدول: الأسطول يخزّن البوابة بمعرف
        // مشتق من الرقم التسلسلي، فالمعرف المحسوب من العنوان هنا قد لا
        // يوجد في الجدول ويكسر قيد المفتاح الخارجي عند كتابة اللقطات.
        return info.mapNotNull(::normalizePort).also { persistPorts(it, resolveGatewayId()) }
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

    /** إعادة تشغيل الوحدة (المنفذ). */
    fun resetPort(port: Int): Map<String, Any> {
        requireValidPort(port)
        // set_port_info يتطلب الثلاثة معًا: port + action + param. إرساله بلا
        // `param` كان يجعل البرنامج الثابت يرفض الطلب صامتًا.
        val response = getJson(
            DinstarApiContract.Path.SET_PORT_INFO,
            mapOf(
                "port" to port.toString(),
                "action" to DinstarApiContract.PortAction.RESET,
                "param" to DinstarApiContract.PortAction.RESET_PARAM
            )
        )
        require(apiSuccess(response)) { "تعذّرت إعادة تشغيل الوحدة: ${apiErrorMessage(response)}" }
        return mapOf("status" to "SUCCEEDED", "port" to port)
    }

    /**
     * إنشاء قاعدة «تعلّم الرقم» (Phone Number Learning) عبر واجهة الويب.
     *
     * ## لماذا الويب لا الـ API
     * لا يوجد مسار موثّق في UC2000 HTTP API لهذه الميزة؛ الطريق الوحيد هو
     * النموذج `/goform/HBPhoneNumberRuleAdd`.
     *
     * ## الحقول — مقروءة من `enHBPhoneNumberAdd.htm` على الجهاز نفسه
     * | الحقل        | المعنى                                            |
     * |--------------|---------------------------------------------------|
     * | `Index`      | فهرس القاعدة 0..7 (لا فهرس المنفذ)                |
     * | `Method`     | 0=SMS، 1=USSD، 2=Call                             |
     * | `Encoding`   | 0=UCS2، 1=GSM 7bit — لـ SMS فقط                   |
     * | `Dest`       | رقم/رمز الوجهة — مطلوب لـ SMS وCall، مُخفى لـ USSD |
     * | `Text`       | النص المُرسَل — مطلوب لغير Call                    |
     * | `Src`        | رقم المُرسِل المتوقَّع للرد (فلترة)                 |
     * | `Key`        | الكلمات المفتاحية لاستخراج الرقم من الرد          |
     * | `IsWRSim`    | 1 = اكتب الرقم في الشريحة                          |
     * | `RmFromLeft` | حذف خانات من اليسار                                |
     * | `AddPrefix`  | بادئة تُضاف                                        |
     * | `PortGroup`  | مجموعة المنافذ (0 = الافتراضية)                    |
     *
     * كان الاستدعاء السابق يرسل `Index/Method/IsWRSim/Ok` فقط بنمط Call،
     * فتُنشأ قاعدة ناقصة بلا وجهة ولا كلمات مفتاحية — لا تستخرج رقمًا.
     *
     * ## سبأفون
     * الطريق الموثَّق: **SMS** إلى `333` بالنص `MMN` ثم مطابقة الرد. لذلك
     * الافتراضي هنا SMS لا Call.
     *
     * @param ruleIndex فهرس القاعدة (0..7) — الافتراضي مطابق للمنفذ.
     * @return `true` إذا قبِل الجهاز النموذج (200 أو 302).
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
        require(ruleIndex in portRange) { "فهرس القاعدة خارج المدى: $ruleIndex" }
        // التحقق يطابق `form_check` في صفحة الجهاز: Call وحده يعفي من النص،
        // وUSSD وحده يعفي من الوجهة.
        if (method != NumberLearningMethod.USSD) {
            require(destination.matches(NUMBER_OR_SHORTCODE)) { "رقم وجهة غير صالح: $destination" }
        }
        if (method != NumberLearningMethod.CALL) {
            require(text.isNotBlank()) { "نص الإرسال مطلوب لنمط ${method.name}" }
        }
        require(stripFromLeft in 0..31) { "عدد الخانات المحذوفة يجب أن يكون 0..31" }

        val target = host ?: activeHost
        require(isPrivateAddress(target)) { "بوابة تعلّم الأرقام يجب أن تكون على عنوان خاص" }
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
                // الترميز يُقرأ لـ SMS وحده، لكن إرساله دائمًا لا يضرّ
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
        require(apiSuccess(response)) { "تعذّر إرسال USSD: ${apiErrorMessage(response)}" }
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
        // يقبل الترميز بصيغتيه الداخلية (GSM7BIT/UCS2) وصيغة البوابة
        // (gsm-7bit/unicode)، إضافةً إلى AUTO الذي يشتق الترميز من النص
        // نفسه (عربي→UCS2، ASCII→GSM7BIT) لتفادي هبوط السعة أو وصول
        // علامات استفهام.
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
            // البوابة تتوقع 'gsm-7bit' أو 'unicode'. إرسال "GSM7BIT"
            // قيمة غير معروفة فترجع البوابة إلى الافتراضي 'unicode':
            // رسالة ASCII تُرسَل UCS2 فتهبط سعتها من 160 حرفًا إلى 70
            // وتتضاعف أجزاؤها وتكلفتها.
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

    /**
     * جلب SMS الواردة — POST /api/query_incoming_sms بمؤشّر تزايدي.
     *
     * `incoming_sms_id` يجعل البوابة تُعيد الرسائل الأحدث من هذا المعرّف فقط
     * بدل الصندوق كاملًا في كل دورة. الاستجابة تحمل `sms` و`read` و`unread`.
     *
     * `flag=all` هو الافتراضي عن قصد: `unread` يعتمد على علامة القراءة داخل
     * الجهاز، وهي تُقلَب بمجرد أن يفتح أحدهم صفحة الوارد في واجهة الويب —
     * فتصير الرسالة «مقروءة» ولا تظهر لنا أبدًا. المؤشّر التزايدي يكفي وحده
     * لمنع التكرار، فلا حاجة لإسناد المنع إلى حالة قابلة للتغيير من الخارج.
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
     * جلب SMS الواردة من بوابة بعينها.
     *
     * النسخة بلا وسيط تخاطب العنوان المضبوط وحده، فكان وارد الجهاز الثاني
     * لا يُلتقط إطلاقًا مهما بلغ عدد الأجهزة المسجّلة.
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
     * عدد SMS في الطابور.
     *
     * البرنامج الثابت 04240302 لا يكشف `query_sms_queue` ولا الاسم القديم
     * `query_sms_count` (كلاهما 404 مُثبت ميدانيًا). يُجرَّب الموثّق أولًا ثم
     * البديل، ويُعاد `error_code=404` مُوصَّفًا بدل رمي استثناء يُسقِط الاستدعاء
     * كله على أجهزة لا تدعم المسار أصلًا.
     */
    fun querySmsQueueCount(): Map<String, Any?> {
        for (path in listOf(
            DinstarApiContract.Path.QUERY_SMS_QUEUE,
            DinstarApiContract.Path.QUERY_SMS_COUNT_LEGACY
        )) {
            runCatching { postJson(path, emptyMap<String, Any>()) }
                .onSuccess { return it }
                .onFailure { log.debug("DINSTAR {} غير مدعوم: {}", path, it.message) }
        }
        return mapOf(
            "error_code" to 404,
            "message" to "SMS queue length is not exposed by this firmware"
        )
    }

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

    /**
     * Get Device Status — POST /api/get_status.
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
            "port" to (port?.let { listOf(it) } ?: (0 until gateway.portCount).toList())
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

    fun queryUssdReply(gateway: DinstarFleetService.Gateway, port: Int): Map<String, Any?> {
        requireGatewayPort(gateway, port)
        return clientFor(gateway).getJson(
            DinstarApiContract.Path.QUERY_USSD_REPLY, 
            mapOf("port" to port.toString())
        )
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
        jdbc.update(
            "INSERT INTO gateway_operations(id,gateway_id,actor_id,operation,target_port,status,details_json,completed_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
            UUID.randomUUID(), resolveGatewayId(), actorId, operation, port, status, mapper.writeValueAsString(details)
        )
    }

    private fun queryPortInfo(host: String): List<Map<String, Any?>> =
        queryPortInfo(host, portRange)

    private fun queryPortInfo(host: String, ports: IntRange): List<Map<String, Any?>> {
        // المسار الأساسي: عميل HTTP API الموثّق (Digest) نفسه الذي يعمل عبر
        // الأسطول. الفاصلة في `info_type`/`port` تُرمَّز `%2C` — بغير ذلك يردّ
        // البرنامج الثابت 04240302 بـ401 رغم صحة الاعتماد
        // (انظر [DinstarConnectionFactory.DinstarClient.encodeQueryValue]).
        val client = connections.clientFor(host, configuredPort, configuredScheme)
        return runCatching { client.queryPorts(ports.last + 1).ports }
            .recoverCatching { client.queryPorts(DinstarModelProfile.UC2000_VE_4G.portRange.last + 1).ports }
            // آخر ملاذ: جلسة الويب. إصدارات تُعطّل «New Version API» أو تفقد
            // مزامنة قاعدة Digest تظل تُجيب على `WebGetPortInfoAll` بالكوكي،
            // فالبديل يمنع ظهور بوابة حيّة على أنها ساقطة. الحقول تُطبَّع إلى
            // أسماء get_port_info حتى يبقى [normalizePort] مصدرًا واحدًا.
            .recoverCatching { apiFailure ->
                log.warn("DINSTAR get_port_info failed on {} ({}) — falling back to web session", host, apiFailure.message)
                queryPortInfoViaWebSession(host, ports)
            }
            .getOrElse { throw IllegalStateException("No authenticated UC2000 port response on $host", it) }
    }

    /**
     * قراءة حالة المنافذ عبر جلسة واجهة الويب (`WebGetPortInfoAll`).
     *
     * تُستخدم فقط عند فشل واجهة HTTP API الموثّقة. الاستجابة مصفوفة خام
     * (لا `{"info":[...]}`), وأسماء حقولها تختلف: `status` بدل `reg`
     * و`call_status` بدل `callstate`، والقيم نصية. تُطبَّع هنا إلى عقد
     * `get_port_info` كي لا يتفرّع منطق التفسير في موضعين.
     */
    private fun queryPortInfoViaWebSession(host: String, ports: IntRange): List<Map<String, Any?>> {
        ensureWebSession(host)
        val raw = getJsonArray("/WebGetPortInfoAll", host)
        return parsePortInfoResponse(raw, ports).map { entry ->
            entry + mapOf(
                "port" to (entry["port"]?.toString()?.toIntOrNull() ?: return@map entry),
                // reg/callstate هما ما يقرؤه normalizePort؛ نص الويب
                // "Mobile Registered" مقبول في DinstarApiContract.PortInfo.
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
                log.warn("DINSTAR web login returned {} — session may not be established (Location: {})", response.code, location)
            }
        }
    }

    /** GET /WebGetPortInfoAll — returns raw port array (not wrapped in "info"). */
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
     * تحديد اسم المشغّل من أدقّ دليل متاح.
     *
     * ## ترتيب الأدلة — من الأقوى إلى الأضعف
     *
     * 1. **رقم الشريحة** (`number`) — قاطع حين يوجد، لكنه فارغ حتى يتم
     *    «تعلّم الرقم»، وهو حال كل المنافذ الثمانية في هذا النشر.
     * 2. **IMSI** — متاح دائمًا ومستقل عن التعلّم. هذا هو **المسار الفعلي**
     *    على واجهة HTTP API لأن `get_port_info` **لا تُصدر `operator`
     *    إطلاقًا** (مُثبت: طلبه بأي اسم يردّ `error_code=400`).
     * 3. **اسم المشغّل النصي** — تُصدره واجهة الويب وحدها، وأحيانًا كرقم
     *    خام `"42103"`. يُصحَّح هنا: MTN←YOU (2021)، HiTel←YTelecom.
     *
     * جداول البادئات وMNC **ليست هنا**: مصدرهما الوحيد
     * [com.red.server.pstn.YemenNumberPlan]. كان هذا الملف يحمل نسخته
     * الخاصة من كلَيهما، فأي تصحيح في أحدهما يترك الآخر معطوبًا.
     */
    private fun resolveOperatorName(apiName: String?, simNumber: String?, imsi: String? = null): String {
        // 1) رقم الشريحة — الأقوى، لكنه غائب قبل تعلّم الرقم
        if (!simNumber.isNullOrBlank()) {
            DinstarLoadBalancer.classifyNumber(simNumber)?.let { return it.apiName }
        }
        // 2) IMSI — المسار الفعلي على HTTP API (لا يُصدِر operator)
        YemenNumberPlan.classifyImsi(imsi)?.let { return it.apiName }
        // 3) الاسم النصي من واجهة الويب، مع تصحيح الأسماء القديمة
        if (!apiName.isNullOrBlank() && apiName != "UNKNOWN") {
            // صيغة PLMN الخام "42103" — تُقرأ كـMCC+MNC لا كاسم
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
        val imsi = raw["imsi"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val resolvedOperator = resolveOperatorName(apiOperator, simNumber, imsi)

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
     * المعرف الفعلي للبوابة في جدول telecom_gateways.
     *
     * الأسطول يحفظ البوابة بمعرف مشتق من الرقم التسلسلي
     * (DINSTAR:SN:...) بينما هذا الكائن يحسب معرفًا من العنوان —
     * فكانت الكتابات به تكسر قيد المفتاح الخارجي. تُقرأ البوابة
     * بالعنوان المطابق، ويُعاد التسجيل إن غابت ثم تُعاد القراءة.
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
        // الفاصلة الخام في آخر معامل تكسر مطابقة Digest URI على البرنامج
        // الثابت 04240302 فتردّ 401 رغم صحة الاعتماد — التفصيل والجدول في
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

