package com.red.server.messaging

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 📨 الناقل الدائم لرسائل 1:1 — Redis Streams.
 *
 * pub/sub (`red:messages:{redId}`) إشارة تنبيه (wake-up) فقط: تضيع إن لم يكن
 * المشترك متصلًا. المصدر الدائم هنا:
 *  - `XADD` على `red:stream:messages:{redId}` عند كل نسخة محفوظة (MessageService).
 *  - consumer-group لكل نسخة باك: `red-backend-$HOSTNAME`.
 *  - `XREADGROUP` للسحب ثم `XACK` بعد التسليم للسوكت (RedMasterHandler).
 *  - إعادة التسليم: `XPENDING` + `XCLAIM` بحد 100/دفعة — مكافئ `XAUTOCLAIM`
 *    حرفيًا (مسح المعلّق + استلام في أمر واحد)؛ واجهة Spring Data Redis 4.0.6
 *    لا تعرض `XAUTOCLAIM` مباشرة (يدعمها Lettuce الأصلي فقط)، فطُبّقت
 *    بخطوتيها المكافئتين.
 *
 * كل العمليات best-effort — عطل Redis لا يُفشل رسالة محفوظة أبدًا.
 */
@Service
class MessageStreamService(
    private val redis: RedisTemplate<String, String>
) {
    private fun ops() = redis.opsForStream<String, String>()

    /** مجموعات مضمونة الإنشاء في هذه النسخة — لتفادي أمر إضافي كل سحب. */
    private val ensuredGroups = ConcurrentHashMap.newKeySet<String>()

    fun groupName(): String = groupForHost(localHostName())

    fun consumerName(): String = "consumer-${localHostName()}"

    /**
     * نشر دائم واحد: XADD ثم ضمان المجموعة (بعد أول XADD — الدفق موجود حتمًا)
     * ثم تقليم حماية MAXLEN. يُرجع معرّف السجل أو null عند عطل Redis.
     */
    fun publish(redId: String, messageUuid: String, receiverDeviceId: Int): RecordId? = runCatching {
        require(redId.isNotBlank() && messageUuid.isNotBlank()) { "INVALID_STREAM_PUBLISH" }
        val key = streamKey(redId)
        val record = MapRecord.create(key, mapOf(FIELD_UUID to messageUuid, FIELD_DEVICE to receiverDeviceId.toString()))
        val id = ops().add(record)
        ensureGroupForKey(key)
        runCatching { ops().trim(key, STREAM_MAXLEN) }
        id
    }.onFailure { e -> log.warn("Stream publish failed for {}: {}", redId, e.message) }.getOrNull()

    /** ضمان consumer-group نسخة الباك على دفق المستلم (idempotent — BUSYGROUP تُتجاهل). */
    fun ensureGroup(redId: String) = ensureGroupForKey(streamKey(redId))

    private fun ensureGroupForKey(key: String) {
        if (!ensuredGroups.add(key)) return
        runCatching { ops().createGroup(key, groupName()) }.onFailure { e ->
            if ((e.message ?: "").contains("BUSYGROUP")) ensuredGroups.add(key)
            else {
                ensuredGroups.remove(key) // عطل عابر — تُعاد المحاولة لاحقًا
                log.debug("ensureGroup failed for {}: {}", key, e.message)
            }
        }
    }

    /**
     * سحب الجديد عبر consumer-group (`>` = لم يُسلَّم لأحد) بحد 100.
     * الـ XACK مسؤولية المتصل بعد التسليم للسوكت.
     */
    fun readNew(redId: String, count: Int = MAX_BATCH): List<MapRecord<String, String, String>> = runCatching {
        ensureGroup(redId)
        ops().read(
            Consumer.from(groupName(), consumerName()),
            StreamReadOptions.empty().count(count.coerceIn(1, MAX_BATCH).toLong()),
            StreamOffset.create(streamKey(redId), ReadOffset.lastConsumed())
        ) ?: emptyList()
    }.onFailure { e -> log.debug("Stream readNew failed for {}: {}", redId, e.message) }
        .getOrDefault(emptyList())

    /** XACK بعد التسليم للسوكت — يُخرج السجلات من قائمة المعلّق (PEL). */
    fun acknowledge(redId: String, ids: List<RecordId>): Long {
        if (ids.isEmpty()) return 0
        return runCatching { ops().acknowledge(streamKey(redId), groupName(), *ids.toTypedArray()) ?: 0 }
            .onFailure { e -> log.debug("Stream ack failed for {}: {}", redId, e.message) }
            .getOrDefault(0)
    }

    /**
     * استرداد المعلّق: XPENDING (الأقدم ع idle ≥ minIdle) + XCLAIM لمالكية
     * هذه النسخة — بحد 100/دفعة. مكافئ XAUTOCLAIM سلوكيًا.
     */
    fun reclaimIdle(
        redId: String,
        minIdle: Duration = PENDING_MIN_IDLE,
        count: Int = MAX_BATCH
    ): List<MapRecord<String, String, String>> = runCatching {
        val key = streamKey(redId)
        val capped = count.coerceIn(1, MAX_BATCH).toLong()
        val pending = ops().pending(key, groupName(), Range.unbounded<String>(), capped, minIdle) ?: return emptyList()
        if (pending.isEmpty()) return emptyList()
        val ids = pending.toList().map { it.id }.toTypedArray()
        ops().claim(key, groupName(), consumerName(), XClaimOptions.minIdle(minIdle).ids(*ids)) ?: emptyList()
    }.onFailure { e -> log.debug("Stream reclaimIdle failed for {}: {}", redId, e.message) }
        .getOrDefault(emptyList())

    companion object {
        private val log = LoggerFactory.getLogger(MessageStreamService::class.java)

        /** مفتاح الدفق لكل مستلم — `red:stream:messages:{redId}`. */
        const val STREAM_KEY_PREFIX = "red:stream:messages:"

        /** بادئة consumer-group لكل نسخة باك — `red-backend-$HOSTNAME`. */
        const val GROUP_PREFIX = "red-backend-"

        /** حقول سجل الدفق. */
        const val FIELD_UUID = "uuid"
        const val FIELD_DEVICE = "device"

        /** حد الدفعة الواحدة — سحبًا واستردادًا (100). */
        const val MAX_BATCH = 100

        /** سقف طول الدفق (حماية من النمو بلا حد — الأقدم يُقلَّم). */
        const val STREAM_MAXLEN = 5000L

        /** عتبة اعتبار المعلّق متعثرًا يستحق الاسترداد. */
        val PENDING_MIN_IDLE: Duration = Duration.ofSeconds(60)

        fun streamKey(redId: String) = "$STREAM_KEY_PREFIX$redId"

        fun groupForHost(host: String) = "$GROUP_PREFIX$host"

        fun localHostName(): String = runCatching {
            System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
                ?: InetAddress.getLocalHost().hostName?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "local"
    }
}
