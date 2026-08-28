package com.red.server.sms

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.DinstarApiContract
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarSmsContract
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min

@Service
class SmsService(
    private val messages: SmsMessageRepository,
    private val markers: SmsReadMarkerRepository,
    private val users: UserAccountRepository,
    private val hardware: DinstarHardwareService,
    private val fleet: DinstarFleetService,
    private val redis: StringRedisTemplate,
    @Value("\${red.dinstar.enabled:true}") private val dinstarEnabled: Boolean,
    private val jdbc: org.springframework.jdbc.core.JdbcTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(SmsService::class.java)
        private val lastSmsWarnAt = ConcurrentHashMap<String, Instant>()
        private val lastSmsReachable = ConcurrentHashMap<String, Boolean>()

        /** Yemeni mobile prefixes after +967 / 967 / 0 (per ITU E.164 + NTA-Yemen). */
        private val YEMEN_PREFIXES = setOf(
            "70", "71", "73", "77", "78", "10",
            "1", "2", "3", "4", "5", "6", "7", "8", "9"
        )

        /** GSM 7-bit default alphabet (3GPP TS 23.038). */
        private val GSM_7BIT = setOf(
            '@', '£', '$', '¥', 'è', 'é', 'ù', 'ì', 'ò', 'Ç', '\n', 'Ø', 'ø', '\r', 'Å', 'å',
            'Δ', 'Φ', 'Γ', 'Λ', 'Ω', 'Π', 'Ψ', 'Σ', 'Θ', 'Ξ', '^', '€', '_', 'æ', 'Æ', 'ß',
            'É', '¡', 'Ñ', 'ñ', '¿', '§', ' ', '!', '"', '#', '¤', '%', '&', '\'', '(', ')', '*',
            '+', ',', '-', '.', '/', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';',
            '<', '=', '>', '?', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
            'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b',
            'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r',
            's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'à', 'á', 'â', 'ã', 'ä', 'å', 'ç', 'è',
            'é', 'ê', 'ë', 'ì', 'í', 'î', 'ï', 'ð', 'ñ', 'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ù',
            'ú', 'û', 'ü', 'ý', 'þ', 'ÿ'
        )

        /** GSM 7-bit extension table (cost 2: each char counted as 2 in PDU length). */
        private val GSM_7BIT_EXT = setOf('|', '^', '€', '€')

        private val ADVENA_LATIN = setOf(
            'أ', 'إ', 'ئ', 'ا', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'د', 'ذ', 'ر', 'ز', 'س', 'ش',
            'ص', 'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن', 'ه', 'و', 'ي', 'ى',
            'ء', 'آ', 'ة'
        )
        val PUNCT = setOf('.', ',', '؟', '!', ':', ';', '(', ')', '«', '»', '"', '؛')
    }

    // ── إرسال ─────────────────────────────────────────────────────────────────

    @Transactional
    fun send(userId: UUID, suppliedNumber: String, text: String, port: List<Int>? = null): SmsMessageEntity {
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        require(user.status == AccountStatus.APPROVED) { "Account is not approved" }
        require(user.pstnEnabled && user.pstnDailyLimit > 0) { "SMS is not enabled for your account" }

        val number = normalizeYemeniNumber(suppliedNumber)
        require(text.isNotBlank()) { "Message text is required" }
        val sizeBytes = text.toByteArray(Charsets.UTF_8).size
        require(sizeBytes <= DinstarHardwareService.MAX_SMS_TEXT_BYTES) {
            "نص الرسالة يتجاوز الحد الأقصى بـ ${sizeBytes} بايت"
        }

        // استهلاك من سعة المكالمات اليومية — نفس السعة المشروعة للمستخدم
        if (!consumeDailyQuota(user)) {
            throw IllegalArgumentException("Daily PSTN/SMS limit reached (${user.pstnDailyLimit})")
        }

        val encoding = detectEncoding(text)
        val segments = countParts(text, encoding)
        // استخدم شريحة المستخدم الدائمة إن لم يحدد المنفذ صراحةً
        val effectivePort = port ?: listOfNotNull(user.pstnPortIndex)
        val effectiveGatewayHost = user.pstnGatewayId?.let { gid ->
            runCatching { jdbc.queryForObject("SELECT host FROM telecom_gateways WHERE id = ?", String::class.java, gid) }.getOrNull()
        }
        val entity = SmsMessageEntity(
            ownerId = userId,
            number = number,
            content = text,
            direction = SmsDirection.OUT,
            status = SmsStatus.PENDING,
            port = effectivePort.firstOrNull(),
            smsParts = segments
        )
        messages.save(entity)

        try {
            val params = listOf(mapOf("number" to number, "user_id" to user.redId))
            hardware.sendSms(text, params, effectivePort, encoding, effectiveGatewayHost)
            entity.status = SmsStatus.SENT
            entity.sentAt = Instant.now()
            entity.errorText = null
        } catch (e: Exception) {
            entity.status = SmsStatus.FAILED
            entity.errorText = (e.message ?: "SEND_FAILED").take(250)
            log.warn("SMS send failed for user {} → {}: {}", user.redId, number, e.message)
        }
        messages.save(entity)
        return entity
    }

    // ── وارد (استلام من DINSTAR → تخزين مشترك) ────────────────────────────────

    /**
     * يجلب الرسائل الواردة الجديدة من كل بوابة في الأسطول ويخزّنها مرة واحدة.
     *
     * ## ما تغيّر ولماذا
     *
     * **1. مؤشّر تزايدي بدل حيلة الدقيقتين.** كان التكرار يُستنطق بـ«نفس الرقم
     * ونفس النص خلال 120 ثانية». هذا يُسقط رسالتين متطابقتين مشروعتين (رمز
     * تحقق يُعاد إرساله)، ويُدخل مكرّرًا بعد الدقيقتين. الآن `incoming_sms_id`
     * من الجهاز هو المفتاح، ويُمرَّر كنقطة استئناف.
     *
     * **2. الاستهلاك يفرض المؤشّر.** `query_incoming_sms` **يحذف** ما يقرأه
     * (مُثبت: قراءة ثانية تُعيد `sms:[]` مع `read=8`). فسؤال الجهاز من الصفر
     * بعد إعادة تشغيل يُعيد لا شيء ويضيع ما بين اللحظتين.
     *
     * **3. كل بوابة على حدة.** كان يُستعلم العنوان المضبوط وحده، فرسائل
     * الجهاز الثاني لا تُلتقط أبدًا. ومؤشّر الوارد يُعدّ داخل كل جهاز مستقلًا،
     * فمؤشّر عام يجعل الأبطأ يتخطّى رسائله كلما تقدّم الأسرع.
     *
     * **4. زمن الجهاز يُقرأ بالصيغة الصحيحة.** كان `Instant.parse` يُنادى على
     * `yyyy-MM-dd HH:mm:ss` وهي ليست ISO-8601، فيفشل **دائمًا** ويسقط إلى
     * `Instant.now()`: كل رسالة تُختم بزمن قراءتها لا زمن وصولها، فترتيب
     * المحادثة مبنيّ على وقت مُلفَّق. `DinstarTime` يفكّها بمنطقة الجهاز.
     */
    @Transactional
    fun ingestIncoming(): List<SmsMessageEntity> {
        if (!dinstarEnabled) return emptyList()

        val gateways = runCatching { fleet.listGateways(onlyEnabled = true) }.getOrDefault(emptyList())
        // لا أسطول مسجّل: المسار الأحادي القديم على العنوان المضبوط.
        if (gateways.isEmpty()) return ingestFromGateway(null)
        return gateways.flatMap { gw -> ingestFromGateway(gw) }
    }

    private fun ingestFromGateway(gateway: DinstarFleetService.Gateway?): List<SmsMessageEntity> {
        val label = gateway?.host ?: "configured"
        val since = gateway?.let { messages.maxIncomingIdForGateway(it.id) }
            ?: messages.maxIncomingIdUnassigned()

        val raw = runCatching {
            if (gateway != null) hardware.queryIncomingSms(gateway, sinceId = since)
            else hardware.queryIncomingSms(sinceId = since)
        }.getOrElse {
            val msg = it.message ?: "unknown"
            val firstFailure = lastSmsReachable.put(label, false) != false
            val last = lastSmsWarnAt[label]
            val quiet = last != null && Duration.between(last, Instant.now()).toMinutes() < 5
            if (firstFailure || !quiet) {
                log.warn("DINSTAR query_incoming_sms failed on {}: {} — Set DINSTAR_ENABLED=false if no hardware", label, msg)
                lastSmsWarnAt[label] = Instant.now()
            } else {
                log.debug("DINSTAR still unreachable (ingest {}): {}", label, msg)
            }
            return emptyList()
        }
        lastSmsReachable[label] = true
        lastSmsWarnAt.remove(label)

        return DinstarSmsContract.parseIncoming(raw).mapNotNull { entry ->
            val sender = runCatching { normalizeYemeniNumber(entry.number) }
                .getOrElse {
                    log.warn("Ignoring incoming SMS with invalid sender from DINSTAR {}: {}", label, entry.number)
                    return@mapNotNull null
                }
            if (entry.text.isBlank()) return@mapNotNull null

            // المفتاح الطبيعي من الجهاز. غيابه (إصدار قديم) يُبقي فحص التكرار
            // النصي القديم كملاذ أخير بدل إسقاط الرسالة.
            val incomingId = entry.incomingSmsId
            if (incomingId != null) {
                if (messages.existsByGatewayIdAndIncomingSmsId(gateway?.id, incomingId)) return@mapNotNull null
            } else {
                val cutoff = Instant.now().minusSeconds(120)
                val dup = messages.findByOwnerIdIsNullAndNumberOrderByCreatedAtAsc(sender).lastOrNull()
                if (dup != null && dup.createdAt.isAfter(cutoff) && dup.content == entry.text) return@mapNotNull null
            }

            val msg = SmsMessageEntity(
                ownerId = resolveIncomingOwner(gateway, entry.port),
                number = sender,
                content = entry.text,
                direction = SmsDirection.IN,
                status = SmsStatus.RECEIVED,
                port = entry.port,
                gatewayId = gateway?.id,
                // زمن الجهاز لا زمن القراءة؛ null يعني أن الجهاز لم يُصدر وقتًا.
                createdAt = entry.time ?: Instant.now(),
                readAt = null,
                incomingSmsId = incomingId,
                senderImsi = entry.imsi
            )
            try {
                messages.save(msg)
                msg
            } catch (dupEx: DataIntegrityViolationException) {
                null
            }
        }
    }

    /**
     * مالك الرسالة الواردة عبر الربط الدائم 1:1.
     *
     * كانت المطابقة بفهرس المنفذ وحده «على أي بوابة» — ومع جهازين يصير
     * المنفذ 3 على `.2` والمنفذ 3 على `.3` شيئًا واحدًا، فتُنسَب رسالة
     * مستخدم إلى آخر. الترتيب الآن: (بوابة، منفذ) أولًا، ثم المنفذ وحده
     * كسقوط للنشر الأحادي القديم.
     */
    private fun resolveIncomingOwner(gateway: DinstarFleetService.Gateway?, port: Int?): UUID? {
        if (port == null) return null
        if (gateway != null) {
            users.findByPstnGatewayIdAndPstnPortIndex(gateway.id, port)?.let { return it.id }
            // بوابة معروفة بلا مالك مربوط: لا نُسقط إلى مطابقة المنفذ وحده،
            // فذلك هو بالضبط الخلط الذي نتجنّبه.
            return null
        }
        return users.findAllByStatusOrderByCreatedAtAsc(com.red.server.auth.model.AccountStatus.APPROVED)
            .firstOrNull { it.pstnPortIndex == port && it.pstnEnabled }?.id
    }

    /** نتيجة استجابة DINSTAR: يدعم الصيغ {"messages":[...]} و{"sms":[...]} و{"result":[...]} */
    private data class IncomingEntry(val port: Int?, val number: String, val text: String, val time: Instant)

    // ── حالة التسليم ──────────────────────────────────────────────────────────

    /**
     * يُطابق تقارير التسليم بمعرّف المرجع ويُحدّث الحالة.
     *
     * ## ما تغيّر ولماذا
     *
     * **1. المطابقة بـ`ref_id` لا بالرقم.** كان التقرير يُنسَب لأي رسالة تحمل
     * الرقم نفسه، فرسالتان متتاليتان إلى الرقم ذاته تتبادلان تقريريهما —
     * والثانية تُعلَّم `DELIVERED` بتقرير الأولى قبل أن تصل. `ref_id` هو
     * المعرّف الذي تُصدره الشبكة لكل إرسال، وهو المفتاح الصحيح الوحيد
     * (3GPP TS 23.040 §9.2.3.15).
     *
     * **2. `status_code` الرقمي لا `status` النصي.** الحقل النصي الذي كان
     * يُقرأ **غير موجود** في ردّ `query_sms_deliver_status`؛ الموثّق
     * `status_code` عددي بمدياته: 0 وصلت، 1..31 جارية، 32..63 فشل مؤقت،
     * 64..255 فشل دائم. فكانت كل رسالة تبقى `SENT` إلى الأبد: لم تنتقل رسالة
     * واحدة إلى `DELIVERED` قطّ.
     *
     * **3. الفشل المؤقت يُميَّز عن الدائم.** المؤقت يستحق إعادة محاولة، والدائم
     * لا. جمعهما في `FAILED` كان يُسقط ما يمكن إنقاذه.
     */
    @Transactional
    fun pollDelivery(): List<SmsMessageEntity> {
        val since = Instant.now().minusSeconds(6 * 3600)
        // فقط ما يملك ref_id: بدونه لا مطابقة ممكنة، والرقم وحده يُنتج نسبةً خاطئة.
        val pending = messages.findByStatusAndDinstarRefIdIsNotNullAndCreatedAtAfter(SmsStatus.SENT, since)
        if (pending.isEmpty()) return emptyList()

        val numbers = pending.mapNotNull { it.number }.distinct()
        if (numbers.isEmpty()) return emptyList()

        val raw = runCatching { hardware.querySmsDeliveryStatus(numbers) }.getOrNull() ?: return emptyList()
        val reports = DinstarSmsContract.parseDeliveryReports(raw).associateBy { it.refId }
        if (reports.isEmpty()) return emptyList()

        val changed = mutableListOf<SmsMessageEntity>()
        pending.forEach { msg ->
            val report = msg.dinstarRefId?.let { reports[it] } ?: return@forEach
            val newStatus = when (report.outcome) {
                DinstarApiContract.DeliveryOutcome.DELIVERED -> SmsStatus.DELIVERED
                DinstarApiContract.DeliveryOutcome.PERMANENT_FAILURE -> SmsStatus.FAILED
                // المؤقت والجاري يبقيان SENT كي تُعاد المحاولة في الدورة التالية.
                else -> return@forEach
            }
            // الرمز الخام يُحفظ دائمًا — يُبقي التشخيص ممكنًا لو تغيّر تصنيفنا.
            msg.deliveryStatusCode = report.statusCode
            msg.lastPolledAt = Instant.now()
            if (msg.status == newStatus) {
                messages.save(msg)
                return@forEach
            }
            msg.status = newStatus
            when (newStatus) {
                SmsStatus.DELIVERED -> msg.deliveredAt = report.time ?: Instant.now()
                SmsStatus.FAILED -> msg.errorText =
                    "DELIVERY_FAILED(code=${report.statusCode ?: "?"})"
                else -> Unit
            }
            messages.save(msg)
            changed.add(msg)
        }
        return changed
    }

    // ── محادثات وقراءة ───────────────────────────────────────────────────────

    data class ConversationDto(
        val number: String,
        val operator: String,
        val lastText: String,
        val lastTime: Long,
        val direction: String,
        val status: String,
        val unreadCount: Int
    )

    fun conversations(userId: UUID): List<ConversationDto> {
        val userOwned = messages.findByOwnerIdOrderByCreatedAtDesc(userId)
        val shared = messages.findByOwnerIdIsNullOrderByCreatedAtDesc()

        // خريطة آخر رسالة لكل رقم (ونية ومشتركة)
        val latest = LinkedHashMap<String, SmsMessageEntity>()
        userOwned.forEach { latest.putOrReplace(it.number, it) }
        shared.forEach { latest.putOrReplace(it.number, it) }

        val readMap = markers.findByUserId(userId).associate { it.number to it.lastReadAt }
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }

        return latest.map { (number, msg) ->
            // عدّاد غير مقروءة: رسائل IN بعد آخر علامة قراءة
            val lastRead = readMap[number]
            val unread = (shared.filter { it.number == number && it.direction == SmsDirection.IN })
                .count { lastRead == null || it.createdAt.isAfter(lastRead) }
            ConversationDto(
                number = number,
                operator = resolveOperator(number),
                lastText = msg.content,
                lastTime = msg.createdAt.epochSecond,
                direction = msg.direction.name,
                status = msg.status.name,
                unreadCount = unread
            )
        }.sortedByDescending { it.lastTime }
    }

    private fun LinkedHashMap<String, SmsMessageEntity>.putOrReplace(number: String, msg: SmsMessageEntity) {
        val existing = get(number)
        if (existing == null || msg.createdAt.isAfter(existing.createdAt)) put(number, msg)
    }

    data class MessageDto(
        val id: String,
        val number: String,
        val content: String,
        val direction: String,
        val status: String,
        val createdAt: Long,
        val isRead: Boolean
    )

    fun conversation(userId: UUID, suppliedNumber: String): List<MessageDto> {
        val number = normalizeYemeniNumber(suppliedNumber)
        val userOwned = messages.findByOwnerIdAndNumberOrderByCreatedAtAsc(userId, number)
        val shared = messages.findByOwnerIdIsNullAndNumberOrderByCreatedAtAsc(number)

        val lastRead = markers.findByUserIdAndNumber(userId, number)?.lastReadAt

        val combined = (userOwned + shared).sortedBy { it.createdAt }
        return combined.map { msg ->
            val isRead = when (msg.direction) {
                SmsDirection.OUT -> msg.status != SmsStatus.PENDING && msg.status != SmsStatus.FAILED
                SmsDirection.IN -> lastRead != null && !msg.createdAt.isAfter(lastRead)
            }
            MessageDto(
                id = msg.id.toString(),
                number = msg.number,
                content = msg.content,
                direction = msg.direction.name,
                status = msg.status.name,
                createdAt = msg.createdAt.epochSecond,
                isRead = isRead
            )
        }
    }

    @Transactional
    fun markRead(userId: UUID, suppliedNumber: String) {
        val number = normalizeYemeniNumber(suppliedNumber)
        val existing = markers.findByUserIdAndNumber(userId, number)
        if (existing == null) {
            markers.save(SmsReadMarker(userId, number))
        } else {
            existing.lastReadAt = Instant.now()
            markers.save(existing)
        }
        // ضع علامة read_at على الرسائل الواردة لك للعرض السريع
        messages.findByOwnerIdIsNullAndNumberOrderByCreatedAtAsc(number).forEach {
            if (it.readAt == null) { it.readAt = Instant.now(); messages.save(it) }
        }
    }

    @Transactional
    fun deleteMessage(userId: UUID, messageId: UUID) {
        val msg = messages.findById(messageId).orElseThrow { NoSuchElementException("Message not found") }
        // المالك الأصلي فقط — لا يمسح رسائل الآخرين
        require(msg.ownerId == userId || msg.ownerId == null) { "NOT_YOURS" }
        messages.delete(msg)
    }

    // ── مساعدات ─────────────────────────────────────────────────────────────

    private fun consumeDailyQuota(user: com.red.server.auth.model.UserAccount): Boolean {
        val day = LocalDate.now(ZoneId.of("Asia/Aden"))
        val key = "red:pstn:daily:${user.id}:$day"
        val used = redis.opsForValue().increment(key) ?: 1L
        if (used == 1L) redis.expire(key, java.time.Duration.ofDays(2))
        if (used > user.pstnDailyLimit) {
            redis.opsForValue().decrement(key)
            log.warn("Quota exhausted for user {}: {}/{}", user.redId, used - 1, user.pstnDailyLimit)
            return false
        }
        return true
    }

    private fun normalizeYemeniNumber(value: String): String {
        val compact = value.filter { it.isDigit() || it == '+' }
        val local = when {
            compact.startsWith("+967") -> compact.removePrefix("+967")
            compact.startsWith("00967") -> compact.removePrefix("00967")
            compact.startsWith("967") -> compact.removePrefix("967")
            compact.startsWith("0") -> compact.removePrefix("0")
            else -> compact
        }
        require(local.matches(Regex("^[0-9]{6,12}$"))) { "Only valid Yemeni numbers are allowed" }
        require(
            local.substring(0, min(3, local.length)) in YEMEN_PREFIXES ||
                local.substring(0, min(2, local.length)) in YEMEN_PREFIXES ||
                local.length >= 9
        ) { "Unrecognized Yemeni mobile prefix" }
        return local
    }

    private fun detectEncoding(text: String): String {
        val hasArabic = text.any { it.code in 0x0600..0x06FF }
        val hasExt = text.any { it in GSM_7BIT_EXT && it !in GSM_7BIT }
        return if (text.all { it in GSM_7BIT || it in GSM_7BIT_EXT } && !hasArabic && !hasExt) "GSM7BIT" else "UCS2"
    }

    private fun countParts(text: String, encoding: String): Int {
        val len = text.count { it !in GSM_7BIT_EXT || it in GSM_7BIT } // تقريبي
        return when (encoding) {
            "GSM7BIT" -> if (len <= 160) 1 else 1 + ceil((len - 160).toDouble() / 153).toInt()
            else -> if (len <= 70) 1 else 1 + ceil((len - 70).toDouble() / 67).toInt()
        }
    }

    private fun resolveOperator(number: String): String {
        val prefix = if (number.length >= 2) number.substring(0, 2) else ""
        return when {
            prefix in setOf("71") -> "سبأفون"
            prefix in setOf("73") -> "يو"
            prefix in setOf("77", "78") -> "يمن موبايل"
            prefix in setOf("70") -> "واي"
            prefix in setOf("10") -> "يمن 4G"
            else -> "غير معروف"
        }
    }
}