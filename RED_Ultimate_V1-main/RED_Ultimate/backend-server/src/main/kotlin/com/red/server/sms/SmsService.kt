package com.red.server.sms

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

@Service
class SmsService(
    private val messages: SmsMessageRepository,
    private val markers: SmsReadMarkerRepository,
    private val users: UserAccountRepository,
    private val hardware: DinstarHardwareService,
    private val redis: StringRedisTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(SmsService::class.java)

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
        val entity = SmsMessageEntity(
            ownerId = userId,
            number = number,
            content = text,
            direction = SmsDirection.OUT,
            status = SmsStatus.PENDING,
            port = port?.firstOrNull(),
            smsParts = segments
        )
        messages.save(entity)

        try {
            val params = listOf(mapOf("number" to number, "user_id" to user.redId))
            hardware.sendSms(text, params, port, encoding)
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
     * يجلب الرسائل الواردة الجديدة من بوابة DINSTAR ويخزّنها مرة واحدة.
     * التكرار يُستنطق عبر (sender, content, created_at) لتجنّب التكرار
     * عند المعالجة المتعددة للبوابة.
     */
    @Transactional
    fun ingestIncoming(): List<SmsMessageEntity> {
        val raw = runCatching { hardware.queryIncomingSms() }
            .getOrElse {
                log.warn("DINSTAR query_incoming_sms failed: ${it.message}")
                return emptyList()
            }
        return parseIncomingResponse(raw).mapNotNull { entry ->
            val sender = normalizeYemeniNumber(entry.number)
            val text = entry.text
            if (text.isNullOrEmpty()) return@mapNotNull null

            // dedup خلال آخر 2 دقيقة
            val cutoff = Instant.now().minusSeconds(120)
            val dup = messages.findByOwnerIdIsNullAndNumberOrderByCreatedAtAsc(sender).lastOrNull()
            if (dup != null && dup.createdAt.isAfter(cutoff) && dup.content == text) return@mapNotNull null

            val msg = SmsMessageEntity(
                ownerId = null,
                number = sender,
                content = text,
                direction = SmsDirection.IN,
                status = SmsStatus.RECEIVED,
                port = entry.port,
                createdAt = entry.time,
                readAt = null
            )
            // قد تأتي مرات متعددة من منافذ مختلفة — INSERT ... ON CONFLICT لتجنب الأخطاء
            try {
                messages.save(msg)
                msg
            } catch (dupEx: DataIntegrityViolationException) {
                null
            }
        }
    }

    /** نتيجة استجابة DINSTAR: يدعم الصيغ {"messages":[...]} و{"sms":[...]} و{"result":[...]} */
    private data class IncomingEntry(val port: Int?, val number: String, val text: String, val time: Instant)

    @Suppress("UNCHECKED_CAST")
    private fun parseIncomingResponse(raw: Map<String, Any?>): List<IncomingEntry> {
        val error = (raw["error_code"] as? Number)?.toInt() ?: 0
        if (error !in setOf(200, 202, 0)) {
            log.warn("DINSTAR incoming SMS returned error_code=$error")
        }
        val list = listOf("messages", "sms", "result", "data").firstNotNullOfOrNull { key ->
            (raw[key] as? List<*>)?.takeIf { it.isNotEmpty() }
        } ?: raw["sms"] as? List<*> ?: emptyList<List<*>>()

        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val number = (m["number"] ?: m["sender"] ?: m["from"]).toString()
            val text = (m["text"] ?: m["content"] ?: m["msg"]).toString()
            val port = (m["port"] as? Number)?.toInt()
            val time = runCatching { Instant.parse((m["time"] ?: m["datetime"] ?: m["timestamp"]).toString()) }
                .getOrNull() ?: Instant.now()
            IncomingEntry(port, normalizeYemeniNumber(number), text, time)
        }
    }

    // ── حالة التسليم ──────────────────────────────────────────────────────────

    /**
     * يجرّب ترقيم تسليم الرسائل المرسلة خلال آخر 6 ساعات ويُحدّث الحالة.
     * يُعيد الرسائل التي غيّرت حالتها (SENT → DELIVERED | FAILED).
     */
    @Transactional
    fun pollDelivery(): List<SmsMessageEntity> {
        val since = Instant.now().minusSeconds(6 * 3600)
        val pending = messages.findByStatusAndCreatedAtAfter(SmsStatus.SENT, since)
        if (pending.isEmpty()) return emptyList()

        val numbers = pending.mapNotNull { it.number }.distinct()
        if (numbers.isEmpty()) return emptyList()

        val raw = runCatching { hardware.querySmsDeliveryStatus(numbers) }.getOrNull() ?: return emptyList()
        val statuses = parseDeliveryStatus(raw)
        val changed = mutableListOf<SmsMessageEntity>()

        pending.forEach { msg ->
            val newStatus = statuses[msg.number] ?: return@forEach
            if (newStatus != SmsStatus.DELIVERED && newStatus != SmsStatus.FAILED) return@forEach
            if (msg.status == newStatus) return@forEach
            msg.status = newStatus
            if (newStatus == SmsStatus.DELIVERED) msg.deliveredAt = Instant.now()
            if (newStatus == SmsStatus.FAILED) msg.errorText = msg.errorText?.ifEmpty { "DELIVERY_FAILED" } ?: "DELIVERY_FAILED"
            messages.save(msg)
            changed.add(msg)
        }
        return changed
    }

    /** يدعم صيغ DINSTAR: {"sms":[{"number":...,"status":"Delivered"}]} و{"result":[...]} */
    @Suppress("UNCHECKED_CAST")
    private fun parseDeliveryStatus(raw: Map<String, Any?>): Map<String, SmsStatus> {
        if ((raw["error_code"] as? Number)?.toInt() !in setOf(200, 202, 0)) {
            log.debug("DINSTAR deliver status error: ${raw["error_code"]}")
        }
        val list = listOf("sms", "messages", "result", "data").firstNotNullOfOrNull { key ->
            (raw[key] as? List<*>)?.takeIf { it.isNotEmpty() }
        } ?: return emptyMap()

        val out = LinkedHashMap<String, SmsStatus>()
        list.forEach { item ->
            val m = item as? Map<*, *> ?: return@forEach
            val number = normalizeYemeniNumber((m["number"] ?: "").toString())
            val status = (m["status"] ?: m["deliver_status"] ?: "").toString().trim()
            out[number] = when (status.lowercase()) {
                "delivered", "ok", "success", "sent" -> SmsStatus.DELIVERED
                "failed", "error", "none", "undelivered" -> SmsStatus.FAILED
                else -> SmsStatus.SENT
            }
        }
        return out
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