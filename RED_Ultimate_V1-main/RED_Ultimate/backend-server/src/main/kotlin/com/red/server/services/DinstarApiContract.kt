package com.red.server.services

/**
 * عقد واجهة **UC2000 HTTP/JSON API** — المصدر الوحيد لشكل كل نداء.
 *
 * ## لماذا يوجد هذا الملف
 *
 * كان شكل كل نداء مكتوبًا في موضع الاستدعاء، فتراكمت أخطاء لا يكشفها
 * المُصرِّف ولا تظهر إلا كردٍّ فارغ أو 403 من الجهاز:
 *
 * - `get_status` كان يُرسَل بجسم `{"maximum":10}`، والموثّق `["performance"]`.
 * - `query_incoming_sms` كان `GET` بلا معاملات، والموثّق `POST` بمؤشّر
 *   تزايدي `incoming_sms_id` — فكانت كل دورة تعيد قراءة الصندوق كاملًا
 *   ويُستعاض عن المؤشّر بحيلة «تكرار خلال دقيقتين».
 * - `get_cdr` كان يحمل `maximum` وهو غير موثّق في هذا المسار.
 * - `set_port_info?action=reset` كان بلا `param` مع أن الثلاثة إلزامية.
 * - مسند النجاح كان مكرّرًا ومتناقضًا: `{200,202}` في موضع و`200` في آخر.
 *
 * لذلك يُجمَّع هنا: المسار، وطريقة HTTP، وأسماء الحقول، ومفتاح الرد،
 * ومسند النجاح. أي تغيير في الواجهة يُعدَّل في مكان واحد، وتُختبَر
 * الأشكال دون الحاجة إلى جهاز.
 *
 * ## المرجع
 *
 * «Dinstar GSM Gateway HTTP API» (v201910 / v202011). المسارات المؤكَّدة
 * ميدانيًا على UC2000-VE-8G في هذا النشر مُعلَّمة أدناه.
 *
 * ## ملاحظة على المصادقة
 *
 * أمثلة Dinstar الرسمية تستخدم `--anyauth` لا `--digest`: أي يُقرأ
 * `WWW-Authenticate` من ردّ 401 ثم يُعاد الطلب بالنوع الذي طلبه الجهاز.
 * ما يقابل ذلك في OkHttp هو `DispatchingAuthenticator` مع تسجيل
 * `digest` و`basic` معًا — وهو ما يفعله [DinstarConnectionFactory].
 * فرض Digest وحده يفشل على الإصدارات التي تُعلن Basic.
 */
object DinstarApiContract {

    /** طريقة HTTP الموثّقة لكل مسار. الخلط بينها سبب موثَّق للردّ 403. */
    enum class Method { GET, POST }

    // ═══════════════════════════════════════════════════════════
    // المسارات
    // ═══════════════════════════════════════════════════════════

    object Path {
        /** حالة المنافذ والشرائح. GET + معاملات استعلام. */
        const val GET_PORT_INFO = "/api/get_port_info"

        /** تحكّم بالمنفذ (reset/power/CallForward/slot/lock…). GET + معاملات. */
        const val SET_PORT_INFO = "/api/set_port_info"

        /** أداء الجهاز (CPU/ذاكرة/فلاش). POST بجسم مصفوفة. */
        const val GET_STATUS = "/api/get_status"

        /** سجل المكالمات. POST بجسم كائن. */
        const val GET_CDR = "/api/get_cdr"

        /** إرسال SMS. POST بجسم كائن. */
        const val SEND_SMS = "/api/send_sms"

        /** نتيجة الإرسال إلى الشبكة (تُعطي `ref_id` للمطابقة). POST. */
        const val QUERY_SMS_RESULT = "/api/query_sms_result"

        /** تقرير التسليم من الشبكة (يُطابق بـ `ref_id`). POST. */
        const val QUERY_SMS_DELIVER_STATUS = "/api/query_sms_deliver_status"

        /** الرسائل الواردة. POST بمؤشّر تزايدي. */
        const val QUERY_INCOMING_SMS = "/api/query_incoming_sms"

        /** طول طابور الإرسال — الاسم الموثّق. */
        const val QUERY_SMS_QUEUE = "/api/query_sms_queue"

        /**
         * اسم بديل لطول الطابور ظهر في بعض الإصدارات.
         * يُستخدم كسقوط فقط عند ردّ 404 على [QUERY_SMS_QUEUE].
         */
        const val QUERY_SMS_COUNT_LEGACY = "/api/query_sms_count"

        /** إيقاف مهمة إرسال. GET + `task_id`. */
        const val STOP_SMS = "/api/stop_sms"

        /** إرسال USSD. POST. النجاح 202 ثم حالة لكل منفذ. */
        const val SEND_USSD = "/api/send_ussd"

        /** قراءة ردّ USSD. GET + `port` مفصولة بفواصل. */
        const val QUERY_USSD_REPLY = "/api/query_ussd_reply"
    }

    /** الطريقة الموثّقة لكل مسار — تُستخدم في اختبارات العقد. */
    val METHODS: Map<String, Method> = mapOf(
        Path.GET_PORT_INFO to Method.GET,
        Path.SET_PORT_INFO to Method.GET,
        Path.GET_STATUS to Method.POST,
        Path.GET_CDR to Method.POST,
        Path.SEND_SMS to Method.POST,
        Path.QUERY_SMS_RESULT to Method.POST,
        Path.QUERY_SMS_DELIVER_STATUS to Method.POST,
        Path.QUERY_INCOMING_SMS to Method.POST,
        Path.QUERY_SMS_QUEUE to Method.POST,
        Path.QUERY_SMS_COUNT_LEGACY to Method.POST,
        Path.STOP_SMS to Method.GET,
        Path.SEND_USSD to Method.POST,
        Path.QUERY_USSD_REPLY to Method.GET
    )

    // ═══════════════════════════════════════════════════════════
    // رموز الحالة
    // ═══════════════════════════════════════════════════════════

    /** نجاح متزامن: العملية تمّت. */
    const val OK = 200

    /**
     * قُبل للتنفيذ لاحقًا. ترجعه العمليات غير المتزامنة (`send_sms`،
     * `send_ussd`). اعتباره فشلًا يُسقط كل إرسال ناجح، واعتباره تسليمًا
     * يكذب على المستخدم — لذلك يُترجَم إلى حالة وسطى `QUEUED`.
     */
    const val ACCEPTED = 202

    private val ACCEPTED_CODES = setOf(OK, ACCEPTED)

    /** رمز الحالة من الرد، أو `null` إن غاب الحقل. */
    fun errorCode(response: Map<String, Any?>): Int? =
        (response["error_code"] as? Number)?.toInt()

    /** نجاح صارم: 200 فقط. للعمليات المتزامنة (قراءة، تحكّم بمنفذ). */
    fun isOk(response: Map<String, Any?>): Boolean = errorCode(response) == OK

    /** نجاح متساهل: 200 أو 202. للعمليات غير المتزامنة (SMS/USSD). */
    fun isAccepted(response: Map<String, Any?>): Boolean = errorCode(response) in ACCEPTED_CODES

    /**
     * بعض إصدارات البرنامج الثابت لا تُرفق `error_code` في ردود القراءة
     * الناجحة (مثل `get_status` الذي يُعيد `{"performance":{…}}` مباشرة).
     * غياب الرمز ليس فشلًا في هذه الحالة، فيُقبل غيابه صراحةً بدل
     * إسقاط ردٍّ صحيح.
     */
    fun isOkOrAbsent(response: Map<String, Any?>): Boolean =
        errorCode(response)?.let { it == OK } ?: response.isNotEmpty()

    /** ترجمة رمز الحالة إلى سبب مفهوم — تظهر للمشرف في اللوحة والسجل. */
    fun describe(code: Int?): String = when (code) {
        OK -> "نجحت العملية"
        ACCEPTED -> "قُبل الطلب وسيُنفَّذ"
        400 -> "صيغة الطلب غير صالحة"
        403 -> "مرفوض: تحقّق من طريقة HTTP وتفعيل New Version API"
        404 -> "المسار أو المهمة غير موجودة"
        413 -> "عدد المستلمين أو حجم النص يتجاوز الحد"
        486 -> "المنفذ مشغول حاليًا"
        500 -> "خطأ داخلي في البوابة"
        503 -> "المنفذ غير مسجّل على الشبكة"
        550 -> "لا يوجد منفذ متاح للإرسال"
        null -> "استجابة بلا error_code"
        else -> "رمز غير موثق: $code"
    }

    fun errorMessage(response: Map<String, Any?>): String {
        val code = errorCode(response)
        return "البوابة ردّت $code — ${describe(code)}"
    }

    // ═══════════════════════════════════════════════════════════
    // الحدود الموثّقة
    // ═══════════════════════════════════════════════════════════

    object Limits {
        /** أقصى عدد مستلمين في طلب `send_sms` واحد. */
        const val MAX_SMS_RECIPIENTS = 128

        /** أقصى حجم لنص الرسالة، بالبايت لا بالحرف. */
        const val MAX_SMS_TEXT_BYTES = 1500

        /** أقصى عدد أرقام في استعلام واحد لـ result / deliver_status. */
        const val MAX_QUERY_NUMBERS = 32

        /** أقصى طول للرقم الواحد بالبايت. */
        const val MAX_NUMBER_BYTES = 24

        /** أقصى طول لنص USSD بالبايت. */
        const val MAX_USSD_BYTES = 60

        /** أعلى فهرس منفذ تدعمه الواجهة (0..31) على مستوى العائلة. */
        const val MAX_PORT_INDEX = 31
    }

    // ═══════════════════════════════════════════════════════════
    // حقول get_port_info
    // ═══════════════════════════════════════════════════════════

    object PortInfo {
        /**
         * `info_type` المطلوبة. لا تُطلب حقول الرصيد
         * (`remain_*`) افتراضيًا: فهي فارغة ما لم تُضبط Billing Setting
         * على الجهاز، وطلبها يُبطئ الاستجابة بلا فائدة.
         */
        val REQUESTED_FIELDS = listOf(
            "type", "imei", "imsi", "iccid", "number",
            "reg", "slot", "callstate", "signal", "gprs"
        )

        val REQUESTED_FIELDS_CSV = REQUESTED_FIELDS.joinToString(",")

        /** حقول الرصيد — تُطلب فقط عند فحص الأرصدة صراحةً. */
        val CREDIT_FIELDS = listOf(
            "remain_credit", "remain_monthly_credit", "remain_daily_credit",
            "remain_daily_call_time", "remain_hourly_call_time", "remain_daily_connected"
        )

        /** مفتاح مصفوفة المنافذ في الرد. */
        const val RESPONSE_KEY = "info"

        /** الرقم التسلسلي للجهاز — يرافق **كل** رد، وهو الهوية الثابتة. */
        const val SERIAL_KEY = "sn"

        /** القيمة التي يستخدمها الجهاز لـ«لا ينطبق» في `slot`. */
        const val SLOT_NOT_APPLICABLE = 255

        /**
         * صيغ «مسجّلة على الشبكة» عبر الإصدارات:
         * - `REGISTER_OK` — الصيغة الخام من `get_port_info`.
         * - `REGISTERED` — إصدارات أخرى من الواجهة.
         * - `Mobile Registered` — نص واجهة الويب (`WebGetPortInfoAll`).
         *
         * المقارنة تُوحَّد هنا لأنها كانت مكرّرة في خمسة ملفات، وكل
         * موضع نسي صيغة، فظهرت شرائح مسجّلة على أنها ساقطة.
         */
        private val REGISTERED_STATES = setOf(
            "register_ok", "registered", "mobile registered"
        )

        fun isRegistered(value: String?): Boolean =
            value?.trim()?.lowercase() in REGISTERED_STATES

        /** حالات غياب الشريحة — تُميَّز عن «مسجّلة لكن بلا إشارة». */
        private val NO_SIM_STATES = setOf("no_sim", "nosim", "no sim", "sim_absent")

        fun isSimAbsent(value: String?): Boolean =
            value?.trim()?.lowercase()?.replace('-', '_') in NO_SIM_STATES

        /** حالة المكالمة التي تعني «المنفذ حر». */
        private val IDLE_STATES = setOf("idle", "free", "ready")

        fun isIdle(value: String?): Boolean =
            value?.trim()?.lowercase() in IDLE_STATES
    }

    // ═══════════════════════════════════════════════════════════
    // set_port_info
    // ═══════════════════════════════════════════════════════════

    object PortAction {
        const val RESET = "reset"
        const val POWER = "power"
        const val SLOT = "slot"
        const val IMEI = "imei"
        const val NUMBER = "number"
        const val LOCK = "lock"
        const val UNLOCK = "unlock"
        const val BLOCK = "block"
        const val UNBLOCK = "unblock"
        const val CALL_FORWARD = "CallForward"
        const val CHECK_CALL_FORWARD = "CheckCallForward"

        /** قيم `param` المقبولة مع `CallForward`. */
        val CALL_FORWARD_PARAMS = setOf(
            "Unconditional", "NoReply", "Busy", "Not_Reachable", "CancelAll"
        )

        /**
         * الأفعال كلها تتطلّب `port` و`action` و`param` معًا.
         * `reset` كان يُرسَل بلا `param` فيردّ الجهاز 400/403 صامتًا؛
         * القيمة المحيَّدة الموثّقة هي `"1"`.
         */
        const val RESET_PARAM = "1"

        fun powerParam(on: Boolean): String = if (on) "on" else "off"
    }

    // ═══════════════════════════════════════════════════════════
    // SMS
    // ═══════════════════════════════════════════════════════════

    object Sms {
        // ── أسماء حقول الطلب ──
        const val REQ_TEXT = "text"
        const val REQ_PARAM = "param"
        const val REQ_PORT = "port"
        const val REQ_ENCODING = "encoding"
        const val REQ_STATUS_REPORT = "request_status_report"
        const val REQ_NUMBER = "number"
        const val REQ_USER_ID = "user_id"
        const val REQ_TEXT_PARAM = "text_param"
        const val REQ_INCOMING_ID = "incoming_sms_id"
        const val REQ_FLAG = "flag"
        const val REQ_TIME_AFTER = "time_after"
        const val REQ_TIME_BEFORE = "time_before"
        const val REQ_TASK_ID = "task_id"

        // ── أسماء حقول الرد ──
        const val RES_TASK_ID = "task_id"
        const val RES_IN_QUEUE_SEND = "sms_in_queue"
        const val RES_IN_QUEUE_QUERY = "in_queue"
        const val RES_RESULT = "result"
        const val RES_SMS = "sms"
        const val RES_READ = "read"
        const val RES_UNREAD = "unread"

        // ── قيم الترميز على السلك ──
        /** أبجدية GSM 03.38 — 160 حرفًا للجزء. */
        const val WIRE_GSM7BIT = "gsm-7bit"

        /** UCS2 — لازم للعربية، 70 حرفًا للجزء. */
        const val WIRE_UNICODE = "unicode"

        // ── قيم flag في query_incoming_sms ──
        const val FLAG_UNREAD = "unread"
        const val FLAG_READ = "read"
        const val FLAG_ALL = "all"

        /**
         * ترجمة `status_code` في تقرير التسليم.
         *
         * المصدر: 3GPP TS 23.040 §9.2.3.15 (TP-Status).
         * كان الكود يقرأ حقلًا نصيًا `status` غير موجود، فلم تنتقل رسالة
         * واحدة من «أُرسلت» إلى «وصلت» قطّ.
         */
        fun deliveryOutcome(statusCode: Int?): DeliveryOutcome = when (statusCode) {
            null -> DeliveryOutcome.UNKNOWN
            0 -> DeliveryOutcome.DELIVERED
            in 1..31 -> DeliveryOutcome.IN_PROGRESS
            in 32..63 -> DeliveryOutcome.TEMPORARY_FAILURE
            in 64..255 -> DeliveryOutcome.PERMANENT_FAILURE
            else -> DeliveryOutcome.UNKNOWN
        }

        /**
         * حالة `query_sms_result` النصية = وصول الرسالة إلى الشبكة،
         * لا إلى المستلم. الخلط بينهما كان يُظهر «تم التسليم» لرسائل
         * لم تصل.
         */
        fun isHandedToNetwork(status: String?): Boolean =
            status?.trim()?.uppercase()?.let { it == "SENT_OK" || it == "SENT" } ?: false

        fun isNetworkRejected(status: String?): Boolean =
            status?.trim()?.uppercase()?.let { it.contains("FAIL") || it == "SENT_FAIL" } ?: false
    }

    /** نتيجة تقرير التسليم بعد الترجمة. */
    enum class DeliveryOutcome {
        /** وصلت إلى هاتف المستلم. */
        DELIVERED,

        /** ما زالت في الشبكة. */
        IN_PROGRESS,

        /** فشل مؤقت — تستحق إعادة محاولة. */
        TEMPORARY_FAILURE,

        /** فشل دائم — لا تُعاد. */
        PERMANENT_FAILURE,

        /** لا معلومة بعد. */
        UNKNOWN
    }

    // ═══════════════════════════════════════════════════════════
    // USSD
    // ═══════════════════════════════════════════════════════════

    object Ussd {
        const val REQ_TEXT = "text"
        const val REQ_PORT = "port"
        const val REQ_COMMAND = "command"

        const val COMMAND_SEND = "send"
        const val COMMAND_CANCEL = "cancel"

        const val RES_RESULT = "result"
        const val RES_REPLY = "reply"

        /**
         * `send_ussd` ينجح بـ **202** ثم يحمل حالة مستقلة لكل منفذ في
         * `result[]`. كان الكود يفحص الرمز العام فقط فيُبلّغ «نجح» على
         * منفذ ردّ 503 (غير مسجّل) أو 486 (مشغول).
         */
        fun perPortStatus(response: Map<String, Any?>): Map<Int, Int> {
            @Suppress("UNCHECKED_CAST")
            val list = response[RES_RESULT] as? List<Map<String, Any?>> ?: return emptyMap()
            return list.mapNotNull { row ->
                val port = (row["port"] as? Number)?.toInt() ?: return@mapNotNull null
                val status = (row["status"] as? Number)?.toInt() ?: return@mapNotNull null
                port to status
            }.toMap()
        }

        /** شكل USSD مقبول: أرقام و`*` و`#` فقط. يمنع حقن مسار أو أمر. */
        private val PATTERN = Regex("^[*#0-9]{2,30}$")

        fun isValidCode(text: String): Boolean =
            PATTERN.matches(text) && text.toByteArray(Charsets.UTF_8).size <= Limits.MAX_USSD_BYTES
    }

    // ═══════════════════════════════════════════════════════════
    // CDR
    // ═══════════════════════════════════════════════════════════

    object Cdr {
        const val REQ_PORT = "port"
        const val REQ_TIME_AFTER = "time_after"
        const val REQ_TIME_BEFORE = "time_before"

        /** المفتاح الموثّق، و`info` بديل في إصدارات أقدم. */
        const val RES_CDR = "cdr"
        const val RES_CDR_LEGACY = "info"

        val FIELDS = listOf(
            "port", "start_date", "answer_date", "duration",
            "source_number", "destination_number", "direction",
            "ip", "codec", "hangup", "gsm_code", "bcch"
        )

        /** اتجاهات المكالمة كما يكتبها الجهاز. */
        const val DIR_IP_TO_GSM = "ip->gsm"
        const val DIR_GSM_TO_IP = "gsm->ip"
        const val DIR_CALLBACK = "callback"

        /**
         * ترجمة اتجاه الجهاز إلى مفردات العمود `dinstar_cdr.direction`.
         *
         * العمود مقيَّد بـ`CHECK (direction IN ('inbound','outbound'))`، والجهاز
         * يكتب `ip->gsm` / `gsm->ip`. إدراج القيمة الخام يُسقط الصف بخرق القيد،
         * وهو ما كان يحدث في كل دورة ابتلاع.
         *
         * `ip->gsm` = صادرة (من شبكتنا إلى الهاتف المحمول)، و`gsm->ip` = واردة.
         * `callback` يبدأ بمكالمة واردة من GSM فيُعدّ واردًا.
         */
        fun normalizeDirection(raw: String?): String? = when (raw?.trim()?.lowercase()) {
            DIR_IP_TO_GSM -> "outbound"
            DIR_GSM_TO_IP, DIR_CALLBACK -> "inbound"
            "outbound", "out" -> "outbound"
            "inbound", "in" -> "inbound"
            else -> null
        }

        /**
         * استنتاج `dinstar_cdr.status` المقيَّد
         * (`answered|no_answer|busy|failed|cancelled`).
         *
         * الجهاز لا يُصدر هذا الحقل: يُصدر `answer_date` (فارغ إن لم تُجَب)
         * و`hangup` النصي. العمود `NOT NULL` بلا افتراضي، فإدراج بلا قيمة
         * كان يخرق القيد حتى لو كانت بقية الأعمدة صحيحة.
         *
         * وجود زمن الإجابة هو الدليل القاطع على الإجابة؛ وما دونه يُصنَّف من
         * سبب الإنهاء، ومجهول السبب يُعدّ `failed` لا `no_answer` حتى لا
         * تُقرأ إخفاقات الشبكة كأنها عدم رد من المستلم.
         */
        fun callOutcome(answered: Boolean, hangup: String?): String {
            if (answered) return "answered"
            val cause = hangup?.trim()?.lowercase().orEmpty()
            return when {
                cause.isEmpty() -> "failed"
                "busy" in cause -> "busy"
                "no answer" in cause || "no_answer" in cause || "noanswer" in cause ||
                    "timeout" in cause || "no reply" in cause -> "no_answer"
                "cancel" in cause || "originator" in cause || "caller" in cause -> "cancelled"
                else -> "failed"
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun records(response: Map<String, Any?>): List<Map<String, Any?>> =
            (response[RES_CDR] as? List<Map<String, Any?>>)
                ?: (response[RES_CDR_LEGACY] as? List<Map<String, Any?>>)
                ?: emptyList()

        /**
         * جملة إدراج CDR الوحيدة — مصدر واحد للحقيقة لكل مَن يكتب في
         * `dinstar_cdr` ([DinstarApiService] و[CdrIngestScheduler]).
         *
         * كانت الجملة مكرَّرة في موضعين بعمودَي `call_type` مختلفَين وبالخطأ
         * نفسه في كلَيهما، فأيّ إصلاح في أحدهما يترك الآخر معطوبًا.
         *
         * **شرط `WHERE` بعد `ON CONFLICT` إلزامي لا تجميلي**: الحَكَم
         * `uq_dinstar_cdr_natural_key` (V40) فهرس **جزئي**
         * (`WHERE gateway_id IS NOT NULL AND port_index IS NOT NULL AND
         * start_time IS NOT NULL`)، وPostgreSQL لا يقبل فهرسًا جزئيًا حَكَمًا
         * للتعارض إلا إذا أعادت الجملة شرطَه حرفيًا. بدونه تفشل الجملة كلها
         * بـ 42P10 «there is no unique or exclusion constraint matching the
         * ON CONFLICT specification» فتسقط دورة الابتلاع بأكملها ويبقى
         * الجدول فارغًا — وهو ما كان يحدث فعلًا.
         *
         * `ring_duration_seconds` يُحسَب هنا لا يُقرأ: الجهاز لا يُصدره، لكنه
         * تعريفًا `answer_date − start_date`. كان العمود صفرًا في كل صفٍّ
         * (110/110) مع أن طرفَي الطرح موجودان، فيُقرأ كأن كل مكالمة أُجيبت
         * لحظيًا. غير المُجابة تبقى `NULL`: لا زمن إجابة ولا زمن إنهاء يُصدره
         * الجهاز، والصفر فيها كذبٌ لا نقصُ بيان.
         *
         * `end_time` مُستبعَد: حسابه يفترض أن `duration` زمن تحدُّثٍ لا زمن
         * مكالمةٍ كاملة، وهو ما لا يُثبته الرد (كل السجلات المُلتقطة مُجابة،
         * فلا عيّنة تُفرِّق). عمودٌ فارغ أصدق من عمودٍ مبنيٍّ على ظنّ.
         *
         * `call_type` مُستبعَد عن قصد: افتراضيّه في المخطَّط `'VOICE'`.
         *
         * ترتيب الوسائط: gateway_id, port_index, start_time, answer_time,
         * duration_seconds, ring_duration_seconds, direction, status,
         * caller_number, callee_number, hangup_cause, gsm_code, codec, raw_data.
         */
        const val INSERT_SQL: String =
            """INSERT INTO dinstar_cdr
                   (gateway_id, port_index, start_time, answer_time, duration_seconds,
                    ring_duration_seconds, direction, status, caller_number, callee_number,
                    hangup_cause, gsm_code, codec, raw_data)
               VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
               ON CONFLICT (gateway_id, port_index, start_time, caller_number, callee_number)
               WHERE gateway_id IS NOT NULL AND port_index IS NOT NULL AND start_time IS NOT NULL
               DO NOTHING"""

        /**
         * زمن الرنين بالثواني — `answer_date − start_date`.
         *
         * `null` عند غياب زمن الإجابة: المكالمة لم تُجَب فزمن رنينها غير
         * معروف، والصفر يُقرأ «أُجيبت فورًا». والسالب مستحيل منطقًا فيُهمَل
         * (ساعة الجهاز قد تُعدَّل بين الحقلين).
         */
        fun ringSeconds(start: java.time.Instant?, answer: java.time.Instant?): Int? {
            if (start == null || answer == null) return null
            val gap = java.time.Duration.between(start, answer).seconds
            return if (gap < 0) null else gap.toInt()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // get_status
    // ═══════════════════════════════════════════════════════════

    object Status {
        /**
         * الجسم الموثّق: **مصفوفة** بأسماء الأقسام المطلوبة، لا كائن.
         * `{"maximum":10}` الذي كان يُرسَل ليس من الواجهة أصلًا.
         */
        val PERFORMANCE_BODY: List<String> = listOf("performance")

        /** القسم الذي يحوي القياسات — القراءة من الجذر تُعيد null دائمًا. */
        const val RES_PERFORMANCE = "performance"

        val FIELDS = listOf(
            "cpu_used", "flash_total", "flash_used", "memory_total",
            "memory_cached", "memory_buffers", "memory_free", "memory_used"
        )

        /** استخراج القياسات من الرد المتداخل، مع سقوط إلى الجذر. */
        @Suppress("UNCHECKED_CAST")
        fun performance(response: Map<String, Any?>): Map<String, Any?> =
            (response[RES_PERFORMANCE] as? Map<String, Any?>)
                ?: response.filterKeys { it in FIELDS }
    }

    // ═══════════════════════════════════════════════════════════
    // الزمن
    // ═══════════════════════════════════════════════════════════

    /**
     * صيغة الوقت في كل ردود الجهاز: `yyyy-MM-dd HH:mm:ss` بتوقيت الجهاز
     * المحلي، وليست ISO-8601. الكود كان ينادي `Instant.parse` عليها
     * مباشرة فتفشل حتمًا، فتُختَم **كل** رسالة واردة بزمن اللحظة —
     * أي أن ترتيب المحادثة وتقارير التسليم كانت مبنية على وقت مُلفَّق.
     */
    const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"
}
