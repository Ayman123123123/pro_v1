package com.red.sovereign.calls.sip

/**
 * RFC 3261 §7.1 — تمثيل رسالة SIP: سطر البداية + ترويسات + سطر فارغ + جسم.
 *
 * موجودٌ صراحةً بدل البناء بالسلاسل النصية (نمط `WebRtcSipClient` القديم) لأن
 * ثلاثة أخطاء في ذلك النمط هي سبب تعثّر التسجيل عند DINSTAR:
 *
 *  1. `Content-Length` محسوب بالأحرف لا بالبايتات، فأي جسم فيه عربي/UTF-8
 *     متعدد البايت يُرسل بطول خاطئ → الخادم يقرأ رسالة مبتورة ويصمت.
 *  2. الترويسات المتعددة (وخاصة `Via`) تُبنى وتُقرأ عشوائيًا؛ `Via` العليا
 *     وحدها هي ما يطابق الردّ على المعاملة (transaction).
 *  3. إعادة الإرسال (retransmission) كانت تعيد **توليد** الرسالة، فتتغيّر
 *     `branch`/`CSeq` بين المحاولات. وRFC 3261 §17.1.2 تشترط أن تكون
 *     إعادة الإرسال متطابقة البايت بحدّ ذاتها.
 *
 * `serialize()` يُثبت `Content-Length` بالبايت الفعلي، ويُبقي الترويسات
 * بترتيبها الأصلي، و`parse()` يقبل طيّ السطور (obs-fold، §7.3.1).
 */
data class SipMessage(
    val startLine: String,
    /** مرتّبة كما أُرسلت، مع إبقاء التكرار — `Via` تتراكم عبر المسارات. */
    val headers: List<Pair<String, String>>,
    val body: ByteArray = ByteArray(0)
) {

    val isResponse: Boolean = startLine.startsWith(SIP_VERSION, ignoreCase = true)

    /** رمز الحالة (استجابة فقط)، و0 إن لم يكن رقميًا صالحًا. */
    val statusCode: Int
        get() {
            if (!isResponse) return 0
            val rest = startLine.substring(SIP_VERSION.length).trim()
            return rest.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

    val reason: String
        get() = if (isResponse) startLine.substring(SIP_VERSION.length).trim().drop(3).trim() else ""

    /**
     * طريقة الطلب. في **الاستجابة** لا يوجد سطر طلب، فتُقرأ من `CSeq`
     * (§8.2.1.2: الاستجابة تحمل نفس رقم/طريقة `CSeq` للطلب المُجاب).
     * الاعتماد على سطر البداية فقط — كما كان يفعل `parseMethod` القديم
     * بنمطه `^(\w+)\s+sip:` — يعيد نصًا فارغًا على كل استجابة،
     * فيمرّ `401` بلا مُعالِج: صمتٌ بلا رسالة خطأ.
     */
    val method: String
        get() = if (!isResponse) startLine.substringBefore(' ').uppercase()
        else cseqMethod

    val cseqNumber: Long get() = header("CSeq")?.trim()?.substringBefore(' ')?.toLongOrNull() ?: -1L
    val cseqMethod: String get() = header("CSeq")?.trim()?.substringAfter(' ', "")?.trim()?.uppercase() ?: ""

    val callId: String get() = header("Call-ID").orEmpty()

    /**
     * `Via` العليا = التي أضافها المُرسِل الأخير. في الطلب هذه **أول** عنصر في
     * القائمة: §20.22 يرتّب `Via` تنازليًا (الأحدث أولًا)، والبروكسيات تُضيف
     * قبلها. وفي الاستجابة يُعاد نسخها بالترتيب نفسه دون إضافة، فالأولى تظل
     * ملكتنا — وهي ما نطابق به `branch` المعاملة.
     * (الاعتماد على الأخيرة يُعطي `Via` أقرب وسيط، فيفشل التطابق فور وجود
     *  B2BUA/proxy يضيف طبقة، ويُتَّهم العميل زورًا بأنه «لا يستجيب للتحدّي».)
     */
    fun topmostVia(): String? = headers.firstOrNull { it.first.equals("Via", true) || it.first.equals("v", true) }?.second

    /** فرع `Via` العليا — مطابقته شرط لرفض الردود الواردة عن معاملة منتهية. */
    fun viaBranch(): String? = topmostVia()
        ?.substringAfter(';', "")
        ?.split(';')
        ?.asSequence()
        .map { it.trim() }
        ?.firstOrNull { it.startsWith("branch=", true) }
        ?.removePrefix("branch=")

    /** §8.1.1.9 — من `To` في الاستجابة؛ وجوده يعني أن الخادم أنشأ حوارًا/حصة. */
    fun toTag(): String? = header("To")?.substringAfter(';', "")
        ?.split(';')?.map { it.trim() }
        ?.firstOrNull { it.startsWith("tag=", true) }
        ?.removePrefix("tag=")

    fun header(name: String): String? = headers
        .firstOrNull { it.first.equals(name, ignoreCase = true) || it.first.equals(COMPACT[name], ignoreCase = true) }
        ?.second

    fun headers(name: String): List<String> = headers
        .filter { it.first.equals(name, ignoreCase = true) || it.first.equals(COMPACT[name], ignoreCase = true) }
        .map { it.second }

    /** معامِل ترويسة مثل `qop="auth,auth-int"` أو `Expires: 120`. */
    fun intValue(name: String): Int? = header(name)?.trim()?.toIntOrNull()

    /**
     * بايتات السلك النهائية. **`Content-Length` يُعاد كتابته دائمًا** ليطابق
     * طول الجسم بالبايت — لا يُصدَّق ما يضعه المستدعي.
     */
    fun serialize(): ByteArray {
        val out = java.io.ByteArrayOutputStream(body.size + 128)
        fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        w(startLine); w(CRLF)
        var contentLengthWritten = false
        for ((name, value) in headers) {
            // ترويسة واحدة فقط لكل اسم من هذه العائلة في الرسالة المُولَّدة.
            if (name.equals("Content-Length", true)) {
                if (!contentLengthWritten) { w("Content-Length: ${body.size}"); w(CRLF); contentLengthWritten = true }
                continue
            }
            w(name); w(": "); w(value.trim()); w(CRLF)
        }
        if (!contentLengthWritten) { w("Content-Length: ${body.size}"); w(CRLF) }
        w(CRLF)
        if (body.isNotEmpty()) out.write(body)
        return out.toByteArray()
    }

    /** نسخة بنفس سطر البداية والجسم مع استبدال/إضافة ترويسة واحدة — لبناء `Authorization`. */
    fun withHeader(name: String, value: String): SipMessage =
        copy(headers = headers.filterNot { it.first.equals(name, true) } + (name to value))

    fun withBody(newBody: ByteArray, contentType: String? = null): SipMessage {
        val base = copy(headers = headers.filterNot { it.first.equals("Content-Length", true) }, body = newBody)
        return if (contentType == null) base else base.copy(headers = base.headers + ("Content-Type" to contentType))
    }

    override fun toString(): String = startLine + "\n" + headers.joinToString("\n") { "${it.first}: ${it.second}" }

    override fun equals(other: Any?): Boolean = other is SipMessage &&
        other.startLine == startLine && other.headers == headers && other.body.contentEquals(body)

    override fun hashCode(): Int = (startLine.hashCode() * 31 + headers.hashCode()) * 31 + body.contentHashCode()

    companion object {
        const val SIP_VERSION = "SIP/2.0"
        const val CRLF = "\r\n"
        private val COMPACT = mapOf(
            "v" to "Via", "i" to "Call-ID", "f" to "From", "t" to "To",
            "m" to "Contact", "l" to "Content-Length", "c" to "Content-Type", "e" to "Content-Encoding", "s" to "Subject"
        )

        /** الحد الأقصى لحجم حزمة SIP فوق UDP (MTN 131072) — حماية من ذاكرة مبالغ فيها. */
        const val MAX_DATAGRAM = 64 * 1024

        /**
         * تحليل صارم لكن متسامح مع الهوامش:
         *  • يرفض بلا صراخ ما ليس SIP (`startLine` لا يبدأ بـ`SIP/2.0` ولا بطريقة معروفة)،
         *    لأن DINSTAR قد يردّ بنص HTTP/400 أو بحزم STUN على نفس المنفذ.
         *  • يقبل طيّ السطور (obs-fold).
         *  • لا يعتمد على `Content-Length` لتحديد نهاية الجسم عند التناقض، بل يثق
         *    بطول الحزمة نفسها: فوق UDP الرسالة كاملة في حزمة واحدة أو لا تكون.
         */
        fun parse(bytes: ByteArray, len: Int = bytes.size): SipMessage? =
            parse(String(bytes, 0, len.coerceIn(0, bytes.size), Charsets.UTF_8))

        fun parse(text: String): SipMessage? {
            if (text.length < 16) return null
            val headerEnd = text.indexOf("\r\n\r\n")
            val headerEndAlt = text.indexOf("\n\n")
            val cut = when {
                headerEnd >= 0 -> headerEnd
                headerEndAlt >= 0 -> headerEndAlt
                else -> -1
            }
            val head = if (cut >= 0) text.substring(0, cut) else text
            val body = if (cut < 0) "" else {
                val sep = if (headerEnd >= 0) 4 else 2
                text.substring(minOf(cut + sep, text.length))
            }
            val lines = head.split("\r\n", "\n")
            val startLine = lines.firstOrNull()?.trim().orEmpty()
            if (startLine.isEmpty()) return null
            val knownMethod = listOf("REGISTER", "INVITE", "ACK", "BYE", "CANCEL", "OPTIONS", "INFO", "UPDATE", "PRACK", "NOTIFY", "MESSAGE", "SUBSCRIBE", "PUBLISH")
            val looksLikeRequest = knownMethod.any { startLine.startsWith("$it ", ignoreCase = true) }
            if (!looksLikeRequest && !startLine.startsWith(SIP_VERSION, ignoreCase = true)) return null

            val headers = ArrayList<Pair<String, String>>(lines.size)
            var i = 1
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank()) { i++; continue }
                val colon = line.indexOf(':')
                if (colon <= 0) { i++; continue }
                val name = line.substring(0, colon).trim()
                var value = line.substring(colon + 1).trim()
                // obs-fold: السطر التالي يبدأ بمسافة → امتداد للقيمة الحالية.
                while (i + 1 < lines.size && lines[i + 1].isNotEmpty() &&
                    (lines[i + 1][0] == ' ' || lines[i + 1][0] == '\t')
                ) {
                    value = "$value ${lines[++i].trim()}"
                }
                headers += name to value
                i++
            }
            return SipMessage(startLine, headers, body.toByteArray(Charsets.UTF_8))
        }

        private fun parse(chars: CharArray, len: Int): SipMessage? = parse(String(chars, 0, minOf(len, chars.size)))

        /** سطر بداية طلب. `rUri` يجب أن يكون **مطابقًا حرفيًا** لقيمة `uri=` في `Authorization`. */
        fun requestLine(method: String, rUri: String): String = "$method $rUri $SIP_VERSION"
    }
}
